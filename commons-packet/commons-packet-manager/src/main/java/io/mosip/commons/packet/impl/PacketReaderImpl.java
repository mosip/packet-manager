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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Lists;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.util.StandardCharset;

import io.mosip.commons.packet.dto.Document;
import io.mosip.commons.packet.dto.Packet;
import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.exception.GetAllIdentityException;
import io.mosip.commons.packet.exception.GetAllMetaInfoException;
import io.mosip.commons.packet.exception.GetBiometricException;
import io.mosip.commons.packet.exception.GetDocumentException;
import io.mosip.commons.packet.exception.PacketValidationFailureException;
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.commons.packet.keeper.PacketKeeper;
import io.mosip.commons.packet.spi.IPacketReader;
import io.mosip.commons.packet.util.IdSchemaUtils;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.commons.packet.util.PacketValidator;
import io.mosip.commons.packet.util.ZipUtils;
import io.mosip.kernel.biometrics.commons.CbeffValidator;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.JsonUtils;

@RefreshScope
@Component
public class PacketReaderImpl implements IPacketReader {

	private static final Logger LOGGER = PacketManagerLogger.getLogger(PacketReaderImpl.class);

	@Value("${mosip.commons.packetnames}")
	private String packetNames;

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
	@Cacheable(value = "packet", key = "{'allFields'.concat('-').concat(#p0).concat('-').concat(#p2)}")	
	public Map<String, Object> getAll(String id, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "Getting all fields");
		Map<String, Object> finalMap = new LinkedHashMap<>();
		for (String srcPacket : packetNames.split(",")) {
			try (InputStream idJsonStream = ZipUtils.unzipAndGetFile(
					packetKeeper.getPacket(getPacketInfo(id, srcPacket, source, process)).getPacket(), "ID")) {
				if (idJsonStream != null) {
					LinkedHashMap<String, Object> idMap = (LinkedHashMap<String, Object>) mapper
							.readValue(new String(IOUtils.toByteArray(idJsonStream), StandardCharset.UTF_8), LinkedHashMap.class).get(IDENTITY);
					for (Map.Entry<String, Object> entry : idMap.entrySet()) {
						finalMap.putIfAbsent(entry.getKey(), normalize(entry.getValue()));
					}
				}
			} catch (Exception e) {
				handleException(e);
			}
		}
		return finalMap;
	}

	private Object normalize(Object value) {
		if (value == null)
			return null;
		if (value instanceof Number || value instanceof String)
			return value.toString().replaceAll("(^\")|(\"$)", "");
		try {
			return JsonUtils.javaObjectToJsonString(value);
		} catch (io.mosip.kernel.core.util.exception.JsonProcessingException e) {
			throw new GetAllIdentityException(e.getMessage());
		}
	}

	@Override
	public String getField(String id, String field, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getField :: for - " + field);
		return Optional.ofNullable(getAll(id, source, process).get(field)).map(Object::toString).orElse(null);
	}

	@Override
	public Map<String, String> getFields(String id, List<String> fields, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getFields :: for - " + fields.toString());
		Map<String, Object> allFields = getAll(id, source, process);
		return fields.stream().collect(Collectors.toMap(f -> f,
				f -> Optional.ofNullable(allFields.get(f)).map(Object::toString).orElse(null)));
	}

	@Override
	public Document getDocument(String id, String documentName, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getDocument :: for - " + documentName);
		try {
			String versionStr = packetReader.getField(id, idSchemaUtils.getIdschemaVersionFromMappingJson(), source,
					process, false);
			String docStr = packetReader.getField(id, documentName, source, process, false);
			if (docStr != null && versionStr != null) {
				JSONObject docJson = new JSONObject(docStr);
				String packetName = idSchemaUtils.getSource(documentName, Double.valueOf(versionStr));
				Packet packet = packetKeeper.getPacket(getPacketInfo(id, packetName, source, process));
				InputStream docStream = ZipUtils.unzipAndGetFile(packet.getPacket(), docJson.optString(VALUE));
				if (docStream != null) {
					Document doc = new Document();
					doc.setDocument(IOUtils.toByteArray(docStream));
					doc.setValue(docJson.optString(VALUE));
					doc.setType(docJson.optString(TYPE));
					doc.setFormat(docJson.optString(FORMAT));
					doc.setRefNumber(docJson.optString(REFNUMBER));
					return doc;
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
	public BiometricRecord getBiometric(String id, String biometricFieldName, List<String> modalities, String source,
			String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getBiometric :: for - " + biometricFieldName);
		try {
			String bioStr = packetReader.getField(id, biometricFieldName, source, process, false);
			JSONObject bioJson = bioStr != null ? new JSONObject(bioStr) : null;
			String packetName = null, fileName = null;

			if (bioJson == null || bioJson.isNull(VALUE)) {
				String operationsData = getMetaInfo(id, source, process).get(META_INFO_OPERATIONS_DATA);
				if (StringUtils.isNotEmpty(operationsData)) {
					JSONArray jsonArray = new JSONArray(operationsData);
					for (int i = 0; i < jsonArray.length(); i++) {
						JSONObject obj = jsonArray.getJSONObject(i);
						if (biometricFieldName.equalsIgnoreCase(obj.optString(LABEL))) {
							packetName = ID;
							fileName = obj.optString(VALUE);
							break;
						}
					}
				}
			} else {
				String idSchemaVersion = packetReader.getField(id, idSchemaUtils.getIdschemaVersionFromMappingJson(), source,
						process, false);
				Double schemaVersion = idSchemaVersion != null ? Double.valueOf(idSchemaVersion) : null;
				packetName = idSchemaUtils.getSource(biometricFieldName, schemaVersion);
				fileName = bioJson.optString(VALUE);
			}

			if (packetName == null || fileName == null)
				return null;
			InputStream stream = ZipUtils.unzipAndGetFile(
					packetKeeper.getPacket(getPacketInfo(id, packetName, source, process)).getPacket(), fileName);
			if (stream == null)
				return null;

			BIR bir = CbeffValidator.getBIRFromXML(IOUtils.toByteArray(stream));
			BiometricRecord record = new BiometricRecord();
			if (bir.getOthers() != null) {
				record.setOthers(new HashMap<>(bir.getOthers()));
			}
			record.setSegments(filterByModalities(modalities, bir.getBirs()));
			return record;
		} catch (Exception e) {
			handleBiometricException(e);
			return null;
		}
	}

	@Override
	public Map<String, String> getMetaInfo(String id, String source, String process) {
		Map<String, String> finalMap = new LinkedHashMap<>();
		for (String packetName : packetNames.split(",")) {
			try (InputStream stream = ZipUtils.unzipAndGetFile(
					packetKeeper.getPacket(getPacketInfo(id, packetName, source, process)).getPacket(),
					"PACKET_META_INFO")) {
				if (stream != null) {
					byte[] arr = IOUtils.toByteArray(stream);
					LinkedHashMap<String, Object> idMap = (LinkedHashMap<String, Object>) mapper
							.readValue(new String(arr, StandardCharset.UTF_8), LinkedHashMap.class).get(IDENTITY);			
					for (Map.Entry<String, Object> entry : idMap.entrySet()) {
						finalMap.putIfAbsent(entry.getKey(),
								JsonUtils.javaObjectToJsonString(entry.getValue()).replaceAll("(^\")|(\"$)", ""));
					}
				}
			} catch (Exception e) {
				handleAllMetaInfoException(e);
			}
		}
		return finalMap;
	}

	@Override
	public List<Map<String, String>> getAuditInfo(String id, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "getAuditInfo :: enrtry");
		List<Map<String, String>> allAudits = new ArrayList<>();
		for (String packetName : packetNames.split(",")) {
			try (InputStream stream = ZipUtils.unzipAndGetFile(
					packetKeeper.getPacket(getPacketInfo(id, packetName, source, process)).getPacket(), "audit")) {
				if (stream != null) {
					allAudits.addAll(mapper.readValue(new String(IOUtils.toByteArray(stream), StandardCharset.UTF_8), List.class));
				}
			} catch (Exception e) {
				handleException(e);
			}
		}
		return allAudits;
	}

	private PacketInfo getPacketInfo(String id, String packetName, String source, String process) {
		PacketInfo packetInfo = new PacketInfo();
		packetInfo.setId(id);
		packetInfo.setPacketName(packetName);
		packetInfo.setProcess(process);
		packetInfo.setSource(source);
		return packetInfo;
	}

	public List<BIR> filterByModalities(List<String> modalities, List<BIR> birList) {
		if (CollectionUtils.isEmpty(modalities))
			return birList;
		return birList.stream()
				.filter(bir -> (CollectionUtils.isNotEmpty(bir.getBdbInfo().getSubtype())
						&& isModalityPresentInTypeSubtype(bir.getBdbInfo().getSubtype(), modalities))
						|| bir.getBdbInfo().getType().stream().map(BiometricType::value)
								.anyMatch(type -> isModalityPresentInTypeSubtype(Lists.newArrayList(type), modalities)))
				.collect(Collectors.toList());
	}

	private boolean isModalityPresentInTypeSubtype(List<String> typeSubtype, List<String> modalities) {
		return modalities.stream().map(mod -> mod.split(" "))
				.anyMatch(arr -> ArrayUtils.isNotEmpty(arr) && ListUtils.isEqualList(typeSubtype, Arrays.asList(arr)));
	}

	private void handleException(Exception e) {
		LOGGER.error("Exception: {}", ExceptionUtils.getStackTrace(e));
		if (e instanceof BaseCheckedException ex)
			throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
		if (e instanceof BaseUncheckedException ex)
			throw new GetAllIdentityException(ex.getErrorCode(), ex.getErrorText());
		throw new GetAllIdentityException(e.getMessage());
	}

	private void handleAllMetaInfoException(Exception e) {
		LOGGER.error("Exception: {}", ExceptionUtils.getStackTrace(e));
		if (e instanceof BaseCheckedException ex)
			throw new GetAllMetaInfoException(ex.getErrorCode(), ex.getErrorText());
		if (e instanceof BaseUncheckedException ex)
			throw new GetAllMetaInfoException(ex.getErrorCode(), ex.getErrorText());
		throw new GetAllMetaInfoException(e.getMessage());
	}
	
	private void handleBiometricException(Exception e) {
		LOGGER.error("Exception: {}", ExceptionUtils.getStackTrace(e));
		if (e instanceof BaseCheckedException ex)
			throw new GetBiometricException(ex.getErrorCode(), ex.getErrorText());
		if (e instanceof BaseUncheckedException ex)
			throw new GetBiometricException(ex.getErrorCode(), ex.getErrorText());
		throw new GetBiometricException(e.getMessage());
	}
	
}