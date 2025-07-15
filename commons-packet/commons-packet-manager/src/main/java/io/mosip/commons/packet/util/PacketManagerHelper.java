package io.mosip.commons.packet.util;

import static io.mosip.commons.packet.constants.PacketManagerConstants.CREATION_DATE;
import static io.mosip.commons.packet.constants.PacketManagerConstants.ENCRYPTED_HASH;
import static io.mosip.commons.packet.constants.PacketManagerConstants.ID;
import static io.mosip.commons.packet.constants.PacketManagerConstants.PACKET_NAME;
import static io.mosip.commons.packet.constants.PacketManagerConstants.PROCESS;
import static io.mosip.commons.packet.constants.PacketManagerConstants.PROVIDER_NAME;
import static io.mosip.commons.packet.constants.PacketManagerConstants.PROVIDER_VERSION;
import static io.mosip.commons.packet.constants.PacketManagerConstants.REFID;
import static io.mosip.commons.packet.constants.PacketManagerConstants.SCHEMA_VERSION;
import static io.mosip.commons.packet.constants.PacketManagerConstants.SIGNATURE;
import static io.mosip.commons.packet.constants.PacketManagerConstants.SOURCE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.mosip.commons.packet.constants.PacketManagerConstants;
import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.kernel.biometrics.commons.CbeffValidator;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.cbeffutil.container.impl.CbeffContainerImpl;
import io.mosip.kernel.core.util.HMACUtils2;

@Component
public class PacketManagerHelper {

	private static final String UNDERSCORE = "_";

	/**
	 * The config server file storage URL.
	 */
	@Value("${mosip.kernel.xsdstorage-uri}")
	private String configServerFileStorageURL;

	@Value("${mosip.kernel.registrationcenterid.length:5}")
	private int centerIdLength;

	@Value("${mosip.kernel.machineid.length:5}")
	private int machineIdLength;

	/**
	 * The schema name.
	 */
	@Value("${mosip.kernel.xsdfile}")
	private String schemaName;
	
	public byte[] getXMLData(BiometricRecord biometricRecord, boolean offlineMode) throws Exception {
		try (InputStream xsd = getXsdStream(offlineMode)) {
			CbeffContainerImpl cbeffContainer = new CbeffContainerImpl();
			BIR bir = cbeffContainer.createBIRType(biometricRecord.getSegments());
			HashMap<String, String> entries = new HashMap<>();
            entries.putAll(biometricRecord.getOthers());
            bir.setOthers(entries);
			return CbeffValidator.createXMLBytes(bir, IOUtils.toByteArray(xsd));
		}
	}

	private InputStream getXsdStream(boolean offlineMode) throws IOException {
        InputStream resourceStream = null;
        if (offlineMode) {
            resourceStream = getClass().getClassLoader().getResourceAsStream(PacketManagerConstants.CBEFF_SCHEMA_FILE_PATH);
        }
        return resourceStream != null ? resourceStream : new URL(configServerFileStorageURL + schemaName).openStream();
    }
	
	public static byte[] generateHash(List<String> order, Map<String, byte[]> data)
			throws IOException, NoSuchAlgorithmException {
		if (order == null || order.isEmpty()) return null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String name : order) {
                byte[] value = data.get(name);
                if (value != null) {
                    outputStream.write(value);
                }
            }
            return HMACUtils2.digestAsPlainText(outputStream.toByteArray()).getBytes();
        }
	}

	public static Map<String, Object> getMetaMap(PacketInfo packetInfo) {
		Map<String, Object> metaMap = new HashMap<>();
		metaMap.put(ID, packetInfo.getId());
		metaMap.put(PACKET_NAME, packetInfo.getPacketName());
		metaMap.put(SOURCE, packetInfo.getSource());
		metaMap.put(PROCESS, packetInfo.getProcess());
		metaMap.put(SCHEMA_VERSION, packetInfo.getSchemaVersion());
		metaMap.put(SIGNATURE, packetInfo.getSignature());
		metaMap.put(ENCRYPTED_HASH, packetInfo.getEncryptedHash());
		metaMap.put(PROVIDER_NAME, packetInfo.getProviderName());
		metaMap.put(PROVIDER_VERSION, packetInfo.getProviderVersion());
		metaMap.put(CREATION_DATE, packetInfo.getCreationDate());
		metaMap.put(REFID, packetInfo.getRefId());
		return metaMap;
	}

	public static PacketInfo getPacketInfo(Map<String, Object> metaMap) {
		PacketInfo packetInfo = new PacketInfo();
		packetInfo.setId((String) metaMap.get(ID));
		packetInfo.setPacketName((String) metaMap.get(PACKET_NAME));
		packetInfo.setSource((String) metaMap.get(SOURCE));
		packetInfo.setProcess((String) metaMap.get(PROCESS));
		packetInfo.setSchemaVersion((String) metaMap.get(SCHEMA_VERSION));
		packetInfo.setSignature((String) metaMap.get(SIGNATURE));
		packetInfo.setEncryptedHash((String) metaMap.get(ENCRYPTED_HASH));
		packetInfo.setProviderName((String) metaMap.get(PROVIDER_NAME));
		packetInfo.setProviderVersion((String) metaMap.get(PROVIDER_VERSION));
		packetInfo.setCreationDate((String) metaMap.get(CREATION_DATE));
		packetInfo.setRefId((String) metaMap.get(REFID));
		return packetInfo;
	}

	/**
	 * This code is added for backward compatibility
	 *
	 * @param id
	 * @param refId
	 * @return
	 */
	public String getRefId(String id, String refId) {
		if (refId != null && !refId.isEmpty()) return refId;

		String centerId = id.substring(0, centerIdLength);
		String machineId = id.substring(centerIdLength, centerIdLength + machineIdLength);
		return centerId + UNDERSCORE + machineId;
	}
}