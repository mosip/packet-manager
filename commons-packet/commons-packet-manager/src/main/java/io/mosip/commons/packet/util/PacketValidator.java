package io.mosip.commons.packet.util;

import static io.mosip.commons.packet.constants.PacketManagerConstants.IDENTITY;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
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
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.commons.packet.keeper.PacketKeeper;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.idobjectvalidator.exception.IdObjectIOException;
import io.mosip.kernel.core.idobjectvalidator.exception.IdObjectValidationFailedException;
import io.mosip.kernel.core.idobjectvalidator.exception.InvalidIdSchemaException;
import io.mosip.kernel.core.idobjectvalidator.spi.IdObjectValidator;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.HMACUtils2;
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

	public boolean validate(String id, String source, String process)
			throws IdObjectIOException, InvalidIdSchemaException, IOException, JsonProcessingException,
			PacketKeeperException, NoSuchAlgorithmException, JSONException {
		boolean result = validateSchema(id, source, process);
		auditLogEntry.addAudit(result ? "Id object validation successful" : "Id object validation failed",
                eventId, eventName, eventType, null, null, id);
		
		if (result) {
			LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					"Id object validation successful for process name : " + process);
			result = fileAndChecksumValidation(id, source, process);
		} else {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					"Id object validation failed for process name : " + process);
		}

		return result;
	}

	private boolean validateSchema(String id, String source, String process)
	        throws IOException, InvalidIdSchemaException, IdObjectIOException, JSONException {
	    Map<String, Object> objectMap = new HashMap<>();
	    try {
	        String idschemaKey = idSchemaUtils.getIdschemaVersionFromMappingJson();
	        String idschemaVersion = reader.getField(id, idschemaKey, source, process, false);
	        List<String> allFields = idSchemaUtils.getDefaultFields(Double.valueOf(idschemaVersion));
	        Map<String, String> fieldsMap = reader.getFields(id, allFields, source, process, false);
	        objectMap.putAll(fieldsMap);

	        if (convertIdschemaToDouble && fieldsMap.get(idschemaKey) != null) {
	            objectMap.put(idschemaKey, Double.valueOf(fieldsMap.get(idschemaKey)));
	        }

	        String mandatoryFields = env.getProperty(
	                String.format(FIELD_LIST, IdObjectsSchemaValidationOperationMapper.getOperation(process))
	        );

	        if (mandatoryFields != null) {
	            LinkedHashMap<String, Object> finalMap = new LinkedHashMap<>();
	            finalMap.put(IDENTITY, loadDemographicIdentity(objectMap));
	            JSONObject finalIdObject = new JSONObject(finalMap);

	            boolean result = idObjectValidator.validateIdObject(
	                    idSchemaUtils.getIdSchema(Double.valueOf(objectMap.get(PacketManagerConstants.IDSCHEMA_VERSION).toString())),
	                    finalIdObject,
	                    Arrays.asList(mandatoryFields.split(","))
	            );

	            LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
	                    "Schema validation result for process {}: {}", process, result);
	            return result;
	        } else {
	            LOGGER.warn(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
	                    "No mandatory field configuration found for process: {}", process);
	            return false;
	        }
	    } catch (IdObjectValidationFailedException e) {
	        LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
	                "Id object masterdata validation failed with errors: {}", e.getErrorTexts());
	        return false;
	    }
	}

	/**
	 * Files validation.
	 *
	 * @param id the registration id
	 * @return true, if successful
	 * @throws IOException
	 */
	public boolean fileAndChecksumValidation(String id, String source, String process)
			throws IOException, JsonProcessingException, PacketKeeperException, NoSuchAlgorithmException {
		boolean isValid = false;
        for (String packetName : packetNames.split(",")) {
            PacketInfo info = new PacketInfo();
            info.setId(id);
            info.setPacketName(packetName);
            info.setSource(source);
            info.setProcess(process);

            Packet packet = packetKeeper.getPacket(info);
            Map<String, String> metaInfo = getMetaInfoJson(packet);
            if (!metaInfo.isEmpty()) {
                List hashseq1List = metaInfo.get("hashSequence1") != null ? mapper.readValue(metaInfo.get("hashSequence1"), ArrayList.class) : null;
                List hashseq2List = metaInfo.get("hashSequence2") != null ? mapper.readValue(metaInfo.get("hashSequence2"), ArrayList.class) : null;

                Map<String, InputStream> checksumMap = new HashMap<>();

                boolean fileValid = validateFiles(hashseq1List, hashseq2List, checksumMap, packet);
                if (fileValid) {
                    LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "File validation successful for packet name : {}", packetName);
                    auditLogEntry.addAudit("File validation successful", eventId, eventName, eventType, null, null, id);
                } else {
                    LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "File validation failed for packet name : {}", packetName);
                    auditLogEntry.addAudit("File validation failed", eventId, eventName, eventType, null, null, id);
                    return false;
                }

                boolean checksumValid = checksumValidation(hashseq1List, hashseq2List, checksumMap, packet);
                if (checksumValid) {
                    LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "Checksum validation successful for packet name : {}", packetName);
                    auditLogEntry.addAudit("Checksum validation successful", eventId, eventName, eventType, null, null, id);
                } else {
                    LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "Checksum validation failed for packet name : {}", packetName);
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

	private byte[] generateHash(List<FieldValueArray> hashSequence, Map<String, InputStream> checksumMap)
			throws NoSuchAlgorithmException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (FieldValueArray fva : hashSequence) {
            for (String file : fva.getValue()) {
                try (InputStream input = checksumMap.get(file)) {
                    output.write(IOUtils.toByteArray(input));
                } catch (IOException e) {
                    LOGGER.error("Error in hash generation: {}", ExceptionUtils.getStackTrace(e));
                }
            }
        }
        return HMACUtils2.digestAsPlainText(output.toByteArray()).getBytes();
	}

	private Map<String, String> getMetaInfoJson(Packet packet) throws PacketKeeperException, IOException {
		Map<String, String> finalMap = new HashMap<>();
        InputStream metaInfoJson = ZipUtils.unzipAndGetFile(packet.getPacket(), "PACKET_META_INFO");
        if (metaInfoJson != null) {
            byte[] bytearray = IOUtils.toByteArray(metaInfoJson);
            String jsonString = new String(bytearray);
            LinkedHashMap<String, Object> currentIdMap = (LinkedHashMap<String, Object>) mapper.readValue(jsonString, LinkedHashMap.class).get(IDENTITY);

            currentIdMap.keySet().forEach(key -> {
                try {
                    finalMap.putIfAbsent(key, currentIdMap.get(key) != null ? JsonUtils.javaObjectToJsonString(currentIdMap.get(key)) : null);
                } catch (JsonProcessingException e) {
                    throw new GetAllMetaInfoException(e.getMessage());
                }
            });
        }
        return finalMap;
	}

	private boolean validateFiles(List hashseq1List, List hashseq2List, Map<String, InputStream> checksumMap,
			Packet packet) throws JsonProcessingException, IOException {
		Set<String> fileNames = new HashSet<>();
        if (hashseq1List != null) {
            for (Object o : hashseq1List) {
                FieldValueArray fva = mapper.readValue(JsonUtils.javaObjectToJsonString(o), FieldValueArray.class);
                fileNames.addAll(fva.getValue());
            }
        }
        if (hashseq2List != null) {
            for (Object o : hashseq2List) {
                FieldValueArray fva = mapper.readValue(JsonUtils.javaObjectToJsonString(o), FieldValueArray.class);
                fileNames.addAll(fva.getValue());
            }
        }

        Set<String> notFound = new HashSet<>(fileNames);
        for (String file : fileNames) {
            InputStream input = ZipUtils.unzipAndGetFile(packet.getPacket(), file);
            if (input != null && input.available() > 0) {
                checksumMap.put(file, input);
                notFound.remove(file);
            }
        }
        return notFound.isEmpty();
	}

	private boolean checksumValidation(List hashseq1List, List hashseq2List, Map<String, InputStream> checksumMap,
			Packet packet) throws JsonProcessingException, IOException, NoSuchAlgorithmException {
		List<FieldValueArray> hash1 = new ArrayList<>();
        List<FieldValueArray> hash2 = new ArrayList<>();

        if (hashseq1List != null) {
            for (Object o : hashseq1List) {
                hash1.add(mapper.readValue(JsonUtils.javaObjectToJsonString(o), FieldValueArray.class));
            }
        }
        if (hashseq2List != null) {
            for (Object o : hashseq2List) {
                hash2.add(mapper.readValue(JsonUtils.javaObjectToJsonString(o), FieldValueArray.class));
            }
        }

        InputStream dataHashStream = ZipUtils.unzipAndGetFile(packet.getPacket(), "PACKET_DATA_HASH");
        InputStream operationsHashStream = ZipUtils.unzipAndGetFile(packet.getPacket(), "PACKET_OPERATIONS_HASH");

        boolean dataEqual = dataHashStream == null || MessageDigest.isEqual(IOUtils.toByteArray(dataHashStream), generateHash(hash1, checksumMap));
        boolean opsEqual = operationsHashStream == null || MessageDigest.isEqual(IOUtils.toByteArray(operationsHashStream), generateHash(hash2, checksumMap));

        return dataEqual && opsEqual;
	}
	
	private LinkedHashMap<String, Object> loadDemographicIdentity(Map<String, Object> fieldMap) throws IOException, JSONException {
		LinkedHashMap<String, Object> demographicIdentity = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
            Object val = entry.getValue();
            if (val != null) {
                String value = val.toString();
                Object json = new JSONTokener(value).nextValue();
                if (json instanceof org.json.JSONObject) {
                    demographicIdentity.putIfAbsent(entry.getKey(), mapper.readValue(value, HashMap.class));
                } else if (json instanceof JSONArray) {
                    JSONArray array = new JSONArray(value);
                    List<Object> jsonList = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        Object obj = array.get(i);
                        jsonList.add(obj instanceof org.json.JSONObject ? mapper.readValue(obj.toString(), HashMap.class) : obj);
                    }
                    demographicIdentity.putIfAbsent(entry.getKey(), jsonList);
                } else {
                    demographicIdentity.putIfAbsent(entry.getKey(), value);
                }
            }
        }
        return demographicIdentity;
	}

}
