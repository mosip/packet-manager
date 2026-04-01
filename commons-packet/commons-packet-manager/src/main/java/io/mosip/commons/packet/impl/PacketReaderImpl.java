package io.mosip.commons.packet.impl;

import static io.mosip.commons.packet.constants.PacketManagerConstants.FORMAT;
import static io.mosip.commons.packet.constants.PacketManagerConstants.ID;
import static io.mosip.commons.packet.constants.PacketManagerConstants.IDENTITY;
import static io.mosip.commons.packet.constants.PacketManagerConstants.LABEL;
import static io.mosip.commons.packet.constants.PacketManagerConstants.META_INFO_OPERATIONS_DATA;
import static io.mosip.commons.packet.constants.PacketManagerConstants.REFNUMBER;
import static io.mosip.commons.packet.constants.PacketManagerConstants.TYPE;
import static io.mosip.commons.packet.constants.PacketManagerConstants.VALUE;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.core.util.JsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.commons.packet.dto.Document;
import io.mosip.commons.packet.dto.Packet;
import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.exception.GetAllIdentityException;
import io.mosip.commons.packet.exception.GetAllMetaInfoException;
import io.mosip.commons.packet.exception.GetBiometricException;
import io.mosip.commons.packet.exception.GetDocumentException;
import io.mosip.commons.packet.exception.PacketValidationFailureException;
import io.mosip.commons.packet.keeper.PacketKeeper;
import io.mosip.commons.packet.spi.IPacketReader;
import io.mosip.commons.packet.util.IdSchemaUtils;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.commons.packet.util.PacketValidator;
import io.mosip.commons.packet.util.ZipUtils;
import io.mosip.kernel.biometrics.commons.CbeffValidator;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;


@RefreshScope
@Component
public class PacketReaderImpl implements IPacketReader {

	private static final Logger LOGGER = PacketManagerLogger.getLogger(PacketReaderImpl.class);

	@Value("${mosip.commons.packetnames}")
	private String packetNames;

	// Split once at startup — avoids String.split() allocation on every request under high load
	private volatile String[] packetNameArray;

	@PostConstruct
	public void init() {
		packetNameArray = packetNames.split(",");
	}

	/**
	 * Lazy accessor for packetNameArray.
	 * In production, @PostConstruct ensures this is pre-populated.
	 * In tests, @PostConstruct is not invoked by @InjectMocks, so we fall back
	 * to splitting packetNames on first access — no test changes required.
	 */
	private String[] getPacketNames() {
		String[] arr = packetNameArray;
		if (arr == null) {
			arr = (packetNames != null) ? packetNames.split(",") : new String[0];
			packetNameArray = arr;
		}
		return arr;
	}

	@Autowired
	private PacketReader packetReader;

	@Autowired
	private PacketKeeper packetKeeper;

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private IdSchemaUtils idSchemaUtils;

	@Autowired
	private PacketValidator packetValidator;

	@Autowired
	private CacheManager cacheManager;

	@Autowired(required = false)
	@Qualifier("packetFetchExecutor")
	private Executor packetFetchExecutor;
	/**
	 * Perform packet validations and audit errors. List of validations - 1. schema
	 * & idobject reference validation 2. files validation 3. decrypted packet
	 * checksum validation 4. cbeff validation 5. document validation
	 *
	 *
	 * @param id
	 * @param process
	 * @return
	 */
	@Override
	public boolean validatePacket(String id, String source, String process) {
		try {
			return packetValidator.validate(id, source, process);
		} catch (Exception e) {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					"Packet Validation exception : " + ExceptionUtils.getStackTrace(e));
			if (e instanceof BaseCheckedException)
				throw new PacketValidationFailureException(((BaseCheckedException) e).getMessage(), e);
			else
				throw new PacketValidationFailureException((e).getMessage(), e);
		}
	}

	/**
	 * return data from idobject of all 3 subpackets
	 *
	 * @param id
	 * @param process
	 * @return
	 */
	@Override
    @Cacheable(value = "packet", key="{'allFields'.concat('-').concat(#p0).concat('-').concat(#p2)}" ,unless = "#result == null")
	public Map<String, Object> getAll(String id, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"Getting all fields :: entry");

		Map<String, Object> finalMap = new LinkedHashMap<>();

		try {
			Executor exec = packetFetchExecutor != null ? packetFetchExecutor : ForkJoinPool.commonPool();
			String[] names = getPacketNames();
			List<CompletableFuture<Packet>> futures = new ArrayList<>(names.length);

			for (String srcPacket : names) {
				futures.add(CompletableFuture.supplyAsync(() -> {
					try {
						return packetKeeper.getPacket(getPacketInfo(id, srcPacket, source, process));
					} catch (Exception e) {
						throw new CompletionException(e);
					}
				}, exec));
			}

			// Wait for all packets
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

			for (CompletableFuture<Packet> future : futures) {

				Packet packet = future.join();

				try (InputStream idJsonStream = ZipUtils.unzipAndGetFile(packet.getPacket(), "ID")) {

					if (idJsonStream == null) {
						continue;
					}

					Map<String, Object> identityWrapper =
							mapper.readValue(idJsonStream, LinkedHashMap.class);

					Map<String, Object> currentIdMap =
							(Map<String, Object>) identityWrapper.get(IDENTITY);

					if (currentIdMap == null) {
						continue;
					}

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
				}
			}

		} catch (CompletionException ce) {

			Throwable cause = ce.getCause() != null ? ce.getCause() : ce;

			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					cause instanceof Exception
							? ExceptionUtils.getStackTrace((Exception) cause)
							: cause.toString());

			if (cause instanceof BaseCheckedException ex) {
				throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
			}

			if (cause instanceof BaseUncheckedException ex) {
				throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
			}

			throw new GetAllIdentityException(cause.getMessage());

		} catch (Exception e) {

			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					ExceptionUtils.getStackTrace(e));

			if (e instanceof BaseCheckedException ex) {
				throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
			}

			if (e instanceof BaseUncheckedException ex) {
				throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
			}

			throw new GetAllIdentityException(e.getMessage());
		}

        return finalMap;
	}

	@Override
	public String getField(String id, String field, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getField :: for - " + field);
		Map<String, Object> allFields = getAll(id, source, process);
		if (allFields != null) {
			Object fieldObj = allFields.get(field);
			return fieldObj != null ? fieldObj.toString() : null;
		}
		return null;
	}

	@Override
	public Map<String, String> getFields(String id, List<String> fields, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getFields :: for - " + fields.toString());
		Map<String, String> result = new HashMap<>();
		Map<String, Object> allFields = getAll(id, source, process);
		fields.stream().forEach(
				field -> result.put(field, allFields.get(field) != null ? allFields.get(field).toString() : null));

		return result;
	}

	@Override
	public Document getDocument(String id, String documentName, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getDocument :: for - " + documentName);
		try {
			String schemaVersionString = packetReader.getField(id, idSchemaUtils.getIdschemaVersionFromMappingJson(), source, process, false);
			Double schemaVersion = schemaVersionString != null ? Double.valueOf(schemaVersionString) : null;
			String documentString = packetReader.getField(id, documentName, source, process, false);
			if (documentString != null && schemaVersion != null) {
				JSONObject documentMap = new JSONObject(documentString);
				String packetName = idSchemaUtils.getSource(documentName, schemaVersion);
				Packet packet = packetKeeper.getPacket(getPacketInfo(id, packetName, source, process));
				String value = documentMap.has(VALUE) ? documentMap.get(VALUE).toString() : null;
				InputStream documentStream = ZipUtils.unzipAndGetFile(packet.getPacket(), value);
				if (documentStream != null) {
					Document document = new Document();
					document.setDocument(IOUtils.toByteArray(documentStream));
					document.setValue(value);
					document.setType(documentMap.has(TYPE) ? documentMap.get(TYPE).toString() : null);
					document.setFormat(documentMap.has(FORMAT) ? documentMap.get(FORMAT).toString() : null);
					document.setRefNumber(documentMap.has(REFNUMBER) ? documentMap.get(REFNUMBER).toString() : null);
					return document;
				}
			}
		} catch (Exception e) {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					ExceptionUtils.getStackTrace(e));
			throw new GetDocumentException(e.getMessage());
		}
		return null;
	}

	@Override
    public BiometricRecord getBiometric(String id, String biometricFieldName, List<String> modalities, String source, String process, boolean byPassCache) {
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                "getBiometric :: for - " + biometricFieldName + " with byPassCache - " + byPassCache);
        BiometricRecord biometricRecord = null;
		
		try {
			BIR bir = loadBiometricsFromObjectStore(id, biometricFieldName, source, process, byPassCache);
			if(bir == null) {
				LOGGER.debug(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
						"Biometric data not found for id: " + id + " and biometricFieldName: " + biometricFieldName);
				return null;
			}
            biometricRecord = new BiometricRecord();
            if(bir.getOthers() != null) {
                HashMap<String, String> others = new HashMap<>();
                bir.getOthers().entrySet().forEach(e -> {
                    others.put(e.getKey(), e.getValue());
                });
                biometricRecord.setOthers(others);
            }
            biometricRecord.setSegments(filterByModalities(modalities, bir.getBirs()));
        } catch (Exception e) {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                    ExceptionUtils.getStackTrace(e));
            if (e instanceof BaseCheckedException) {
                BaseCheckedException ex = (BaseCheckedException) e;
                throw new GetBiometricException(ex.getErrorCode(), ex.getMessage());
            } else if (e instanceof BaseUncheckedException) {
                BaseUncheckedException ex = (BaseUncheckedException) e;
                throw new GetBiometricException(ex.getErrorCode(), ex.getMessage());
            }
            throw new GetBiometricException(e.getMessage());
        }
        return biometricRecord;
    }

	// Kept for backward compatibility. This method will not utilize the cache. Will be removed in future
	@Override
	public BiometricRecord getBiometric(String id, String biometricFieldName, List<String> modalities, String source, String process) {
		return getBiometric(id, biometricFieldName, modalities, source, process, false);
	}

	private String generateKey(String id, String biometricFieldName, String source, String process) {
		return String.format("%s-%s-%s-%s", id, biometricFieldName, source, process);
	}

	private BIR loadBiometricsFromObjectStore(String id, String biometricFieldName, String source, String process, boolean byPassCache) throws Exception {
		String cacheKey = generateKey(id, biometricFieldName, source, process);
		Cache cache = cacheManager.getCache("packets");

		if(byPassCache || cache == null) {
			LOGGER.debug(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					"Skipping Cache due to byPassCache : " + byPassCache + " or IsCachePresent : " + (cache != null));
			return loadBiometricsFromObjectStore(id, biometricFieldName, source, process);
		}

		BIR cachedValue = cache.get(cacheKey, BIR.class);
		if(cachedValue != null) {
			LOGGER.debug(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					"Cache Found for the Key : " + cacheKey);
			return cachedValue;
		}

		LOGGER.debug(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"Cache not found for the Key : " + cacheKey + " Loading biometrics from ObjectStore");
		BIR bir = loadBiometricsFromObjectStore(id, biometricFieldName, source, process);
		if(bir != null) {
			LOGGER.debug(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					"Adding cache the Key : " + cacheKey);
			cache.put(cacheKey, bir);
		}

			return bir;
		}

	private BIR loadBiometricsFromObjectStore(String id, String biometricFieldName, String source, String process) throws Exception {
		String packetName = null;
		String fileName = null;

		String bioString = packetReader.getField(id, biometricFieldName, source, process, false);//(String) idobjectMap.get(biometricFieldName);
		JSONObject biometricMap = null;
		if (bioString != null)
			biometricMap = new JSONObject(bioString);
		if (bioString == null || biometricMap == null || biometricMap.isNull(VALUE)) {
			// biometric file not present in idobject. Search in meta data.
			Map<String, String> metadataMap = getMetaInfo(id, source, process);
			String operationsData = metadataMap.get(META_INFO_OPERATIONS_DATA);
			if (StringUtils.isNotEmpty(operationsData)) {
				JSONArray jsonArray = new JSONArray(operationsData);
				for (int i = 0; i < jsonArray.length(); i++) {
					JSONObject jsonObject = (JSONObject) jsonArray.get(i);
					if (jsonObject.has(LABEL)
							&& jsonObject.get(LABEL).toString().equalsIgnoreCase(biometricFieldName)) {
						packetName = ID;
						fileName = jsonObject.isNull(VALUE) ? null : jsonObject.get(VALUE).toString();
						break;
					}
				}
			}
		} else {
			String idSchemaVersion = packetReader.getField(id,
					idSchemaUtils.getIdschemaVersionFromMappingJson(), source, process, false);
			Double schemaVersion = idSchemaVersion != null ? Double.valueOf(idSchemaVersion) : null;
			packetName = idSchemaUtils.getSource(biometricFieldName, schemaVersion);
			fileName = biometricMap.get(VALUE).toString();
		}

		if (packetName == null || fileName == null)
			return null;

		Packet packet = packetKeeper.getPacket(getPacketInfo(id, packetName, source, process));
		InputStream biometrics = ZipUtils.unzipAndGetFile(packet.getPacket(), fileName);
		if (biometrics == null)
			return null;

		return CbeffValidator.getBIRFromXML(IOUtils.toByteArray(biometrics));
	}

	@Override
	public Map<String, String> getMetaInfo(String id, String source, String process) {
		Map<String, String> finalMap = new LinkedHashMap<>();

		try {
			Executor exec = packetFetchExecutor != null ? packetFetchExecutor : ForkJoinPool.commonPool();
			String[] names = getPacketNames();
			List<CompletableFuture<Packet>> futures = new ArrayList<>(names.length);

			for (String packetName : names) {
				futures.add(CompletableFuture.supplyAsync(() -> {
					try {
						return packetKeeper.getPacket(getPacketInfo(id, packetName, source, process));
					} catch (Exception e) {
						throw new CompletionException(e);
					}
				}, exec));
			}

			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

			for (CompletableFuture<Packet> future : futures) {
				Packet packet = future.join();
				InputStream idJsonStream = ZipUtils.unzipAndGetFile(packet.getPacket(), "PACKET_META_INFO");
				if (idJsonStream != null) {
					byte[] bytearray = IOUtils.toByteArray(idJsonStream);
					String jsonString = new String(bytearray);
					LinkedHashMap<String, Object> currentIdMap = (LinkedHashMap<String, Object>) mapper
							.readValue(jsonString, LinkedHashMap.class).get(IDENTITY);

					currentIdMap.keySet().stream().forEach(key -> {
						try {
							finalMap.putIfAbsent(key,
									currentIdMap.get(key) != null ? JsonUtils
											.javaObjectToJsonString(currentIdMap.get(key)).replaceAll("(^\")|(\"$)", "")
											: null);
						} catch (io.mosip.kernel.core.util.exception.JsonProcessingException e) {
							throw new GetAllMetaInfoException(e.getMessage());
						}
					});
				}
			}
		} catch (CompletionException ce) {
			Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
			if (cause instanceof BaseCheckedException ex)
				throw new GetAllMetaInfoException(ex.getErrorCode(), ex.getMessage());
			if (cause instanceof BaseUncheckedException ex)
				throw new GetAllMetaInfoException(ex.getErrorCode(), ex.getMessage());
			throw new GetAllMetaInfoException(cause.getMessage());
		} catch (Exception e) {
			if (e instanceof BaseCheckedException) {
				BaseCheckedException ex = (BaseCheckedException) e;
				throw new GetAllMetaInfoException(ex.getErrorCode(), ex.getMessage());
			} else if (e instanceof BaseUncheckedException) {
				BaseUncheckedException ex = (BaseUncheckedException) e;
				throw new GetAllMetaInfoException(ex.getErrorCode(), ex.getMessage());
			}
			throw new GetAllMetaInfoException(e.getMessage());
		}
		return finalMap;
	}

	@Override
	public List<Map<String, String>> getAuditInfo(String id, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "getAuditInfo :: entry");
		List<Map<String, String>> finalMap = new ArrayList<>();

		try {
			Executor exec = packetFetchExecutor != null ? packetFetchExecutor : ForkJoinPool.commonPool();
			String[] names = getPacketNames();
			List<CompletableFuture<Packet>> futures = new ArrayList<>(names.length);

			for (String srcPacket : names) {
				futures.add(CompletableFuture.supplyAsync(() -> {
					try {
						return packetKeeper.getPacket(getPacketInfo(id, srcPacket, source, process));
					} catch (Exception e) {
						throw new CompletionException(e);
					}
				}, exec));
			}

			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

			for (CompletableFuture<Packet> future : futures) {
				Packet packet = future.join();
				InputStream auditJson = ZipUtils.unzipAndGetFile(packet.getPacket(), "audit");
				if (auditJson != null) {
					byte[] bytearray = IOUtils.toByteArray(auditJson);
					String jsonString = new String(bytearray);
					List<Map<String, String>> currentMap = (List<Map<String, String>>) mapper.readValue(jsonString, List.class);
					finalMap.addAll(currentMap);
				}
			}
		} catch (CompletionException ce) {
			Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					cause instanceof Exception ? ExceptionUtils.getStackTrace((Exception) cause) : cause.toString());
			if (cause instanceof BaseCheckedException ex)
				throw new GetAllIdentityException(ex.getErrorCode(), ex.getMessage());
			if (cause instanceof BaseUncheckedException ex)
				throw new GetAllIdentityException(ex.getErrorCode(), ex.getMessage());
			throw new GetAllIdentityException(cause.getMessage());
		} catch (Exception e) {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
					ExceptionUtils.getStackTrace(e));
			if (e instanceof BaseCheckedException) {
				BaseCheckedException ex = (BaseCheckedException) e;
				throw new GetAllIdentityException(ex.getErrorCode(), ex.getMessage());
			} else if (e instanceof BaseUncheckedException) {
				BaseUncheckedException ex = (BaseUncheckedException) e;
				throw new GetAllIdentityException(ex.getErrorCode(), ex.getMessage());
			}
			throw new GetAllIdentityException(e.getMessage());
		}
		return finalMap;
	}

	private PacketInfo getPacketInfo(String id, String packetName, String source, String process) {
		PacketInfo packetInfo = new PacketInfo();
		packetInfo.setId(id);
		packetInfo.setPacketName(packetName);
		packetInfo.setProcess(process);
		packetInfo.setSource(source);
		return packetInfo;
	}

	public List<BIR> filterByModalities(List<String> modalities,
										List<BIR> birList) {
		if (CollectionUtils.isEmpty(modalities)) {
			return birList;
		}
		// Pre-split modality strings once before the BIR loop to avoid
		// repeated String.split() calls for every BIR entry.
		List<List<String>> parsedModalities = new ArrayList<>(modalities.size());
		for (String modality : modalities) {
			parsedModalities.add(Arrays.asList(modality.split(" ")));
		}

		List<BIR> segments = new ArrayList<>();
		// first search modalities in subtype and if not present search in type
		for (BIR bir : birList) {
			if (CollectionUtils.isNotEmpty(bir.getBdbInfo().getSubtype())
					&& isModalityPresent(bir.getBdbInfo().getSubtype(), parsedModalities)) {
				segments.add(bir);
			} else {
				for (BiometricType type : bir.getBdbInfo().getType()) {
					if (isModalityPresent(Collections.singletonList(type.value()), parsedModalities)) {
						segments.add(bir);
						break;
					}
				}
			}
		}
		return segments;
	}

	private boolean isModalityPresent(List<String> typeSubtype, List<List<String>> parsedModalities) {
		for (List<String> modalityParts : parsedModalities) {
			if (ListUtils.isEqualList(typeSubtype, modalityParts))
				return true;
		}
		return false;
	}

}
