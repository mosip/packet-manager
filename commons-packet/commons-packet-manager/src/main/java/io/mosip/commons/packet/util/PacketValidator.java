package io.mosip.commons.packet.util;

import static io.mosip.commons.packet.constants.PacketManagerConstants.IDENTITY;
import static io.mosip.commons.packet.constants.PacketManagerConstants.IDSCHEMA_VERSION;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import io.mosip.commons.packet.exception.GetAllIdentityException;
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.util.HMACUtils2;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.commons.packet.audit.AuditLogEntry;
import io.mosip.commons.packet.constants.PacketManagerConstants;
import io.mosip.commons.packet.dto.Packet;
import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.dto.packet.FieldValueArray;
import io.mosip.commons.packet.exception.GetAllMetaInfoException;
import io.mosip.commons.packet.exception.PacketKeeperException;
import io.mosip.commons.packet.keeper.PacketKeeper;
import io.mosip.kernel.core.idobjectvalidator.exception.IdObjectIOException;
import io.mosip.kernel.core.idobjectvalidator.exception.IdObjectValidationFailedException;
import io.mosip.kernel.core.idobjectvalidator.exception.InvalidIdSchemaException;
import io.mosip.kernel.core.idobjectvalidator.spi.IdObjectValidator;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;

@Component
public class PacketValidator {

    @Value("${mosip.commons.packetnames:id}")
    private String packetNames;

    @Value("${mosip.commons.packet.manager.schema.validator.convertIdSchemaToDouble:true}")
    private boolean convertIdschemaToDouble;

    private static final Logger LOGGER = PacketManagerLogger.getLogger(PacketValidator.class);
    private static final String FIELD_LIST = "mosip.kernel.idobjectvalidator.mandatory-attributes.reg-processor.%s";

    private static final String eventId = "PACKET_MANAGER";
    private static final String eventName = "PACKET MANAGER";
    private static final String eventType = "SYSTEM";

    @Autowired
    private PacketReader reader;

    @Autowired
    private Environment env;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private PacketKeeper packetKeeper;

    @Autowired
    private IdObjectValidator idObjectValidator;

    @Autowired
    private IdSchemaUtils idSchemaUtils;

    @Autowired
    private AuditLogEntry auditLogEntry;

    @Autowired
    @Qualifier("packetValidateExecutor")
    private Executor packetValidateExecutor;


    public boolean validate(String id, String source, String process) throws IdObjectIOException, InvalidIdSchemaException, IOException, JsonProcessingException, PacketKeeperException, NoSuchAlgorithmException, JSONException {
        // Fetch all sub-packets ONCE and reuse for both schema validation and checksum
        // validation — avoids a second round of S3 GET + decrypt calls.
        Map<String, Packet> packetsMap = fetchAllPacketsInParallel(id, source, process);
        Map<String, Object> identityFields = extractIdentityFields(packetsMap);

        boolean result = validateSchema(id, process, identityFields);
        if (result) {
            LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "Id object validation successful for process name : " + process);
            auditLogEntry.addAudit("Id object validation successful", eventId, eventName, eventType, null, null, id);
            result = fileAndChecksumValidation(id, source, process, packetsMap);
        } else {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "Id object validation failed for process name : " + process);
            auditLogEntry.addAudit("Id object validation failed", eventId, eventName, eventType, null, null, id);
        }
        return result;
    }

    /**
     * Extract identity fields from pre-fetched packets, iterating in packetNames order
     * (same merge behaviour as PacketReaderImpl.getAll — later packets override earlier ones).
     */
    private Map<String, Object> extractIdentityFields(Map<String, Packet> packetsMap) {
        Map<String, Object> finalMap = new LinkedHashMap<>();
        for (String packetName : packetNames.split(",")) {
            Packet packet = packetsMap.get(packetName.trim());
            if (packet == null || packet.getPacket() == null) continue;
            try (InputStream idJsonStream = ZipUtils.unzipAndGetFile(packet.getPacket(), "ID")) {
                if (idJsonStream == null) continue;
                Map<String, Object> identityWrapper = mapper.readValue(idJsonStream, LinkedHashMap.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> currentIdMap = (Map<String, Object>) identityWrapper.get(IDENTITY);
                for (Map.Entry<String, Object> entry : currentIdMap.entrySet()) {

                    String key = entry.getKey();

                    if (finalMap.containsKey(key)) {
                        continue;
                    }

                    Object value = entry.getValue();

                    if (value == null) {
                        finalMap.put(key, null);
                        continue;
                    }

                    if (value instanceof Number) {
                        finalMap.put(key, value);
                        continue;
                    }

                    if (value instanceof String str) {

                        if (!str.isEmpty() && str.charAt(0) == '"')
                            str = str.substring(1);
                        if (!str.isEmpty() && str.charAt(str.length() - 1) == '"')
                            str = str.substring(0, str.length() - 1);
                        finalMap.put(key, str);
                        continue;
                    }

                    try {
                        finalMap.put(key, JsonUtils.javaObjectToJsonString(value));
                    } catch (io.mosip.kernel.core.util.exception.JsonProcessingException e) {
                        LOGGER.error(ExceptionUtils.getStackTrace(e));
                        throw new GetAllIdentityException(e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID,
                        packetName, "Failed to extract identity fields: " + ExceptionUtils.getStackTrace(e));
                if (e instanceof BaseCheckedException) {
                    BaseCheckedException ex = (BaseCheckedException) e;
                    throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
                } else if (e instanceof BaseUncheckedException) {
                    BaseUncheckedException ex = (BaseUncheckedException) e;
                    throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
                }
                throw new GetAllIdentityException(e.getMessage());
            }
        }
        return finalMap;
    }

    /**
     * Fetch all packets in parallel to minimize S3/object store round-trip time.
     * Each packet is uniquely identified by id, source, process and packetName.
     */
    private Map<String, Packet> fetchAllPacketsInParallel(String id, String source, String process) throws PacketKeeperException {
        List<CompletableFuture<Map.Entry<String, Packet>>> futures = Arrays.stream(packetNames.split(","))
                .map(packetName -> packetName.trim())
                .map(packetName -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Packet packet = packetKeeper.getPacket(getPacketInfo(id, packetName, source, process));
                        return Map.entry(packetName, packet);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, packetValidateExecutor))
                .collect(Collectors.toList());
        try {
            Map<String, Packet> packetsMap = new HashMap<>();
            for (CompletableFuture<Map.Entry<String, Packet>> future : futures) {
                Map.Entry<String, Packet> entry = future.join();
                packetsMap.put(entry.getKey(), entry.getValue());
            }
            return packetsMap;
        } catch (CompletionException ce) {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                    ExceptionUtils.getStackTrace(ce));
            System.out.println("inside completion exception");
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof BaseCheckedException ex) {
                System.out.println("inside base checked exception");
                throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
            }
            if (cause instanceof BaseUncheckedException ex) {
                System.out.println("inside base unchecked exception");
                throw new GetAllIdentityException(ex.getErrorCode());
            }
            throw new GetAllIdentityException(cause.getMessage());

        } catch (Exception e) {

            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                    ExceptionUtils.getStackTrace(e));

            if (e instanceof BaseUncheckedException ex) {
                throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
            }

            throw new GetAllIdentityException(e.getMessage());
        }
    }

    private boolean validateSchema(String id, String process, Map<String, Object> identityFields) throws IOException, InvalidIdSchemaException, IdObjectIOException, JSONException {
        try {
            String idschemaValueFromMappingJson = idSchemaUtils.getIdschemaVersionFromMappingJson();
            Object versionObj = identityFields.get(idschemaValueFromMappingJson);
            if (versionObj == null) {
                LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                        "ID schema version field not found in packet identity");
                return false;
            }
            double idschemaVersion = Double.parseDouble(versionObj.toString());
            List<String> allFields = idSchemaUtils.getDefaultFields(idschemaVersion);
            Map<String, Object> objectMap = new HashMap<>();
            allFields.forEach(field ->
                    objectMap.put(field, identityFields.get(field))
            );

            if (convertIdschemaToDouble)
                objectMap.put(idschemaValueFromMappingJson, idschemaVersion);

            String fields = env.getProperty(String.format(FIELD_LIST, IdObjectsSchemaValidationOperationMapper.getOperation(process)));
            if (fields != null) {
                LinkedHashMap finalMap = new LinkedHashMap();
                finalMap.put(IDENTITY, loadDemographicIdentity(objectMap));
                JSONObject finalIdObject = new JSONObject(finalMap);

                return idObjectValidator.validateIdObject(
                        idSchemaUtils.getIdSchema(idschemaVersion),
                        finalIdObject, Arrays.asList(fields.split(",")));

            }

			return false;
        } catch (IdObjectValidationFailedException e) {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                    "Id object masterdata validation failed with errors:  " + e.getErrorTexts());
            return false;
        }

    }

    /**
     * Files validation.
     *
     * @param id
     *            the registration id
     * @param packetsMap pre-fetched packets (avoids duplicate S3 fetches)
     * @return true, if successful
     * @throws IOException
     */
    public boolean fileAndChecksumValidation(String id, String source, String process, Map<String, Packet> packetsMap) throws IOException, JsonProcessingException, PacketKeeperException, NoSuchAlgorithmException {
        boolean isValid = false;
        // perform file and checksum validation for each source
        for (String packetName : packetNames.split(",")) {
            packetName = packetName.trim();
            Packet packet = packetsMap.get(packetName);
            if (packet == null) continue;

            // Extract all ZIP entries in a single pass to avoid repeated traversal.
            Map<String, byte[]> zipEntries = ZipUtils.unzipAll(packet.getPacket());

            Map<String, String> finalMap = getMetaInfoJson(zipEntries);
            if (!finalMap.isEmpty()) {

                // Parse raw JSON lists to typed FieldValueArray lists once here;
                // avoids repeated serialize+deserialize in validateFiles and checksumValidation.
                List<FieldValueArray> hashSeq1 = toFieldValueArrayList(
                        finalMap.get("hashSequence1") != null ? mapper.readValue(finalMap.get("hashSequence1"), ArrayList.class) : null);
                List<FieldValueArray> hashSeq2 = toFieldValueArrayList(
                        finalMap.get("hashSequence2") != null ? mapper.readValue(finalMap.get("hashSequence2"), ArrayList.class) : null);
                Map<String, InputStream> checksumMap = new HashMap<>();

                boolean fileValidation = validateFiles(hashSeq1, hashSeq2, checksumMap, zipEntries);

                if (fileValidation) {
                    LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "File validation successful for packet name : " + packetName);
                    auditLogEntry.addAudit("File validation successful", eventId, eventName, eventType, null, null, id);
                } else {
                    LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "File validation failed for packet name : " + packetName);
                    auditLogEntry.addAudit("File validation failed", eventId, eventName, eventType, null, null, id);
                    return false;
                }

                boolean checksumValidation = checksumValidation(hashSeq1, hashSeq2, checksumMap, zipEntries);

                if (checksumValidation) {
                    LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "Checksum validation successful for packet name : " + packetName);
                    auditLogEntry.addAudit("Checksum validation successful", eventId, eventName, eventType, null, null, id);
                } else {
                    LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "Checksum validation failed for packet name : " + packetName);
                    auditLogEntry.addAudit("Checksum validation failed", eventId, eventName, eventType, null, null, id);
                    return false;
                }

                isValid = true;
            }

        }
        return isValid;
    }

    private PacketInfo getPacketInfo(String id, String packetName, String source, String process) {
        PacketInfo packetInfo = new PacketInfo();
        packetInfo.setId(id);
        packetInfo.setPacketName(packetName);
        packetInfo.setProcess(process);
        packetInfo.setSource(source);
        return packetInfo;
    }

    private byte[] generateHash(List<FieldValueArray> hashSequence, Map<String, InputStream> checksumMap) throws NoSuchAlgorithmException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (FieldValueArray fieldValueArray : hashSequence) {
            List<String> hashValues = fieldValueArray.getValue();
            hashValues.forEach(value -> {
                byte[] valuebyte = null;
                try {
                    InputStream fileStream = checksumMap.get(value);
                    valuebyte = IOUtils.toByteArray(fileStream);
                    outputStream.write(valuebyte);
                } catch (IOException e) {
                    LOGGER.error("Exception while generating hash " + ExceptionUtils.getStackTrace(e));
                }
            });
        }

        return HMACUtils2.digestAsPlainText(outputStream.toByteArray()).getBytes();

    }

    private Map<String, String> getMetaInfoJson(Map<String, byte[]> zipEntries) throws IOException {
        Map<String, String> finalMap = new HashMap<>();
        byte[] metaBytes = zipEntries.get("PACKET_META_INFO");
        if (metaBytes != null) {
            String jsonString = new String(metaBytes);
            LinkedHashMap<String, Object> currentIdMap = (LinkedHashMap<String, Object>) mapper.readValue(jsonString, LinkedHashMap.class).get(IDENTITY);
            if (currentIdMap != null) {
                currentIdMap.keySet().stream().forEach(key -> {
                    try {
                        finalMap.putIfAbsent(key, currentIdMap.get(key) != null ? JsonUtils.javaObjectToJsonString(currentIdMap.get(key)) : null);
                    } catch (io.mosip.kernel.core.util.exception.JsonProcessingException e) {
                        throw new GetAllMetaInfoException(e.getMessage());
                    }
                });
            }
        }
        return finalMap;
    }

    /**
     * Converts a raw Jackson-deserialized list (list of LinkedHashMaps) into a
     * typed List<FieldValueArray> using convertValue — avoids an unnecessary
     * serialize-then-deserialize round-trip through a JSON string.
     */
    private List<FieldValueArray> toFieldValueArrayList(List rawList) {
        List<FieldValueArray> result = new ArrayList<>();
        if (rawList != null) {
            for (Object o : rawList) {
                result.add(mapper.convertValue(o, FieldValueArray.class));
            }
        }
        return result;
    }

    private boolean validateFiles(List<FieldValueArray> hashSeq1, List<FieldValueArray> hashSeq2, Map<String, InputStream> checksumMap, Map<String, byte[]> zipEntries) {
        List<String> allFileNames = new ArrayList<>();
        for (FieldValueArray fva : hashSeq1) {
            allFileNames.addAll(fva.getValue());
        }
        for (FieldValueArray fva : hashSeq2) {
            allFileNames.addAll(fva.getValue());
        }

        List<String> notFoundFiles = new ArrayList<>(allFileNames);
        for (String fileName : allFileNames) {
            byte[] fileBytes = zipEntries.get(fileName.toUpperCase());
            if (fileBytes != null && fileBytes.length > 0)
                checksumMap.put(fileName, new ByteArrayInputStream(fileBytes));
            notFoundFiles.remove(fileName);
        }

        return notFoundFiles.isEmpty();
    }

    private boolean checksumValidation(List<FieldValueArray> hashSeq1, List<FieldValueArray> hashSeq2, Map<String, InputStream> checksumMap, Map<String, byte[]> zipEntries) throws IOException, NoSuchAlgorithmException {
        // Map lookups replace two separate ZIP traversals.
        byte[] dataHashBytes = zipEntries.get("PACKET_DATA_HASH");
        byte[] operationsHashBytes = zipEntries.get("PACKET_OPERATIONS_HASH");

        boolean isdataCheckSumEqual;
        boolean isoperationsCheckSumEqual;

        if (dataHashBytes != null) {
            byte[] dataHash = generateHash(hashSeq1, checksumMap);
            isdataCheckSumEqual = MessageDigest.isEqual(dataHash, dataHashBytes);
        } else
            isdataCheckSumEqual = true;

        if (operationsHashBytes != null) {
            byte[] operationsHash = generateHash(hashSeq2, checksumMap);
            isoperationsCheckSumEqual = MessageDigest.isEqual(operationsHash, operationsHashBytes);
        } else
            isoperationsCheckSumEqual = true;

        return (isdataCheckSumEqual && isoperationsCheckSumEqual);

    }

    private LinkedHashMap loadDemographicIdentity(Map<String, Object> fieldMap) throws IOException, JSONException {
        LinkedHashMap demographicIdentity = new LinkedHashMap();
        for (Map.Entry e : fieldMap.entrySet()) {
            if (e.getValue() != null) {
                String value = e.getValue().toString();
                if (value != null) {
                    Object json = new JSONTokener(value).nextValue();
                    if (json instanceof org.json.JSONObject) {
                        HashMap<String, Object> hashMap = mapper.readValue(value, HashMap.class);
                        demographicIdentity.putIfAbsent(e.getKey(), hashMap);
                    }

                    else if (json instanceof JSONArray) {
                        List jsonList = new ArrayList<>();
                        JSONArray jsonArray = new JSONArray(value);

                        for (int i = 0; i < jsonArray.length(); i++) {
                            Object obj = jsonArray.get(i);
                            jsonList.add(obj instanceof org.json.JSONObject ? mapper.readValue(obj.toString(), HashMap.class):obj);
                        }
                        demographicIdentity.putIfAbsent(e.getKey(), jsonList);
                    } else
                        demographicIdentity.putIfAbsent(e.getKey(), e.getValue());
                } else
                    demographicIdentity.putIfAbsent(e.getKey(), value);
            }
        }
        return demographicIdentity;
    }


}
