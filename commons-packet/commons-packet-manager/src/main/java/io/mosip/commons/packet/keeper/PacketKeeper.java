package io.mosip.commons.packet.keeper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.mosip.commons.khazana.dto.ObjectDto;
import io.mosip.commons.khazana.spi.ObjectStoreAdapter;
import io.mosip.commons.packet.constants.ErrorCode;
import io.mosip.commons.packet.constants.PacketUtilityErrorCodes;
import io.mosip.commons.packet.dto.Packet;
import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.dto.TagDto;
import io.mosip.commons.packet.dto.TagRequestDto;
import io.mosip.commons.packet.exception.CryptoException;
import io.mosip.commons.packet.exception.ObjectDoesnotExistsException;
import io.mosip.commons.packet.exception.ObjectStoreAdapterException;
import io.mosip.commons.packet.exception.PacketIntegrityFailureException;
import io.mosip.commons.packet.exception.PacketKeeperException;
import io.mosip.commons.packet.spi.IPacketCryptoService;
import io.mosip.commons.packet.util.PacketManagerHelper;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.HMACUtils2;
import jakarta.annotation.PostConstruct;

/**
 * The packet keeper is used to store & retrieve packet, creation of audit,
 * encrypt and sign packet. Packet keeper is used to get container information
 * and list of sources from a packet.
 */
@Component
public class PacketKeeper {

	/**
	 * The reg proc logger.
	 */
	private static Logger LOGGER = PacketManagerLogger.getLogger(PacketKeeper.class);

	private static final String OBJECT_DOESNOT_EXISTS = "The specified key does not exist";
	private static final String STATUS_404 = "Status Code: 404; Error Code: NoSuchKey";
	private static final String UNDERSCORE = "_";

	@Value("${packet.manager.account.name}")
	private String PACKET_MANAGER_ACCOUNT;

	@Autowired
	@Qualifier("SwiftAdapter")
	private ObjectStoreAdapter swiftAdapter;

	@Autowired
	@Qualifier("S3Adapter")
	private ObjectStoreAdapter s3Adapter;

	@Autowired
	@Qualifier("PosixAdapter")
	private ObjectStoreAdapter posixAdapter;

	@Value("${objectstore.adapter.name}")
	private String adapterName;

	@Value("${objectstore.crypto.name}")
	private String cryptoName;

	@Value("${mosip.kernel.registrationcenterid.length}")
	private int centerIdLength;

	@Value("${mosip.kernel.machineid.length}")
	private int machineIdLength;

	@Value("${packetmanager.packet.signature.disable-verification:false}")
	private boolean disablePacketSignatureVerification;

	@Autowired
	@Qualifier("OnlinePacketCryptoServiceImpl")
	private IPacketCryptoService onlineCrypto;

	@Autowired
	@Qualifier("OfflinePacketCryptoServiceImpl")
	private IPacketCryptoService offlineCrypto;

	@Autowired
	private PacketManagerHelper helper;

	private Map<String, ObjectStoreAdapter> adapterRegistry = new ConcurrentHashMap<>();
	private Map<String, IPacketCryptoService> cryptoRegistry = new ConcurrentHashMap<>();

	@PostConstruct
	private void initializeRegistries() {
		adapterRegistry.put("SwiftAdapter", swiftAdapter);
		adapterRegistry.put("S3Adapter", s3Adapter);
		adapterRegistry.put("PosixAdapter", posixAdapter);

		cryptoRegistry.put("OnlinePacketCryptoServiceImpl", onlineCrypto);
		cryptoRegistry.put("OfflinePacketCryptoServiceImpl", offlineCrypto);

		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, "PacketKeeper",
				"Adapter and CryptoService registries initialized");
	}

	/**
	 * Check packet integrity using HMAC-SHA256 and compare with stored hash.
	 *
	 * @param packetInfo         the packet information metadata
	 * @param encryptedSubPacket the encrypted byte content of the packet
	 * @return true if integrity verified, false otherwise
	 */
	public boolean checkIntegrity(PacketInfo packetInfo, byte[] encryptedSubPacket) throws NoSuchAlgorithmException {
		long start = System.nanoTime();
		String computedHash = CryptoUtil.encodeToURLSafeBase64(HMACUtils2.generateHash(encryptedSubPacket));
		boolean result = computedHash.equals(packetInfo.getEncryptedHash());

		// Keep original log structure
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID,
				getName(packetInfo.getId(), packetInfo.getPacketName()), "Integrity check : " + result);

		return result;
	}

	/**
	 * Verify both digital signature and HMAC-based packet integrity.
	 *
	 * @param packet             the Packet object
	 * @param encryptedSubPacket the encrypted byte content of the packet
	 * @return true if both signature and integrity are valid
	 */
	public boolean checkSignature(Packet packet, byte[] encryptedSubPacket) throws NoSuchAlgorithmException {
		boolean result;

		if (disablePacketSignatureVerification) {
			result = true;
		} else {
			result = getCryptoService().verify(
					helper.getRefId(packet.getPacketInfo().getId(), packet.getPacketInfo().getRefId()),
					packet.getPacket(), CryptoUtil.decodeURLSafeBase64(packet.getPacketInfo().getSignature()));
		}

		if (result) {
			result = checkIntegrity(packet.getPacketInfo(), encryptedSubPacket);
		}

		// Keep original log structure
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID,
				getName(packet.getPacketInfo().getId(), packet.getPacketInfo().getPacketName()),
				"Integrity and signature check : " + result);

		return result;
	}

	/**
	 * Get packet from object store and validate its integrity and signature.
	 *
	 * @param packetInfo PacketInfo metadata to locate and verify packet
	 * @return Packet object containing decrypted packet and metadata
	 * @throws PacketKeeperException in case of errors or failed validation
	 */
	public Packet getPacket(PacketInfo packetInfo) throws PacketKeeperException {
		try {
			String objectName = getName(packetInfo.getId(), packetInfo.getPacketName());

			// Step 1: Retrieve encrypted packet
			InputStream inputStream = getAdapter().getObject(PACKET_MANAGER_ACCOUNT, packetInfo.getId(),
					packetInfo.getSource(), packetInfo.getProcess(), objectName);

			if (inputStream == null) {
				LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, objectName,
						packetInfo.getProcess() + " Packet is not present in packet store.");
				throw new PacketKeeperException(ErrorCode.PACKET_NOT_FOUND.getErrorCode(),
						ErrorCode.PACKET_NOT_FOUND.getErrorMessage());
			}

			byte[] encryptedSubPacket = IOUtils.toByteArray(inputStream);

			// Step 2: Initialize Packet and load metadata
			Packet packet = new Packet();

			Map<String, Object> metaInfo = getAdapter().getMetaData(PACKET_MANAGER_ACCOUNT, packetInfo.getId(),
					packetInfo.getSource(), packetInfo.getProcess(), objectName);

			if (metaInfo != null && !metaInfo.isEmpty()) {
				packet.setPacketInfo(PacketManagerHelper.getPacketInfo(metaInfo));
			} else {
				LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, objectName,
						"metainfo not found for this packet");
				packet.setPacketInfo(packetInfo);
			}

			// Step 3: Decrypt packet
			byte[] decrypted = getCryptoService().decrypt(
					helper.getRefId(packet.getPacketInfo().getId(), packet.getPacketInfo().getRefId()),
					encryptedSubPacket);
			packet.setPacket(decrypted);

			// Step 4: Verify signature and integrity
			if (!checkSignature(packet, encryptedSubPacket)) {
				LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID,
						getName(packet.getPacketInfo().getId(), packetInfo.getPacketName()),
						"Packet Integrity and Signature check failed");
				throw new PacketIntegrityFailureException();
			}

			LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, objectName,
					"Packet successfully retrieved and verified");

			return packet;

		} catch (Exception e) {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, packetInfo.getId(),
					ExceptionUtils.getStackTrace(e));

			if (e.getMessage() != null && e.getMessage().contains(OBJECT_DOESNOT_EXISTS)
					&& e.getMessage().contains(STATUS_404)) {
				throw new ObjectDoesnotExistsException();
			} else if (e instanceof BaseCheckedException baseChecked) {
				throw new PacketKeeperException(baseChecked.getErrorCode(), baseChecked.getMessage());
			} else if (e instanceof BaseUncheckedException baseUnchecked) {
				throw new PacketKeeperException(baseUnchecked.getErrorCode(), baseUnchecked.getMessage());
			}

			throw new PacketKeeperException(PacketUtilityErrorCodes.PACKET_KEEPER_GET_ERROR.getErrorCode(),
					"Exception occured reading packet : " + e.getMessage(), e);
		}
	}

	/**
	 * Put packet into storage/cache
	 *
	 * @param packet : the Packet
	 * @return PacketInfo
	 */
	public PacketInfo putPacket(Packet packet) throws PacketKeeperException {
		try {
			// encrypt packet
			byte[] encryptedSubPacket = getCryptoService().encrypt(packet.getPacketInfo().getRefId(),
					packet.getPacket());

			// put packet in object store
			boolean uploaded = getAdapter().putObject(PACKET_MANAGER_ACCOUNT, packet.getPacketInfo().getId(),
					packet.getPacketInfo().getSource(), packet.getPacketInfo().getProcess(),
					packet.getPacketInfo().getPacketName(), new ByteArrayInputStream(encryptedSubPacket));

			if (!uploaded) {
				throw new PacketKeeperException(PacketUtilityErrorCodes.PACKET_KEEPER_PUT_ERROR.getErrorCode(),
						"Unable to store packet in object store");
			}

			PacketInfo packetInfo = packet.getPacketInfo();
			// sign encrypted packet
			packetInfo.setSignature(CryptoUtil.encodeToURLSafeBase64(getCryptoService().sign(packet.getPacket())));
			// generate encrypted packet hash
			packetInfo.setEncryptedHash(CryptoUtil.encodeToURLSafeBase64(HMACUtils2.generateHash(encryptedSubPacket)));

			Map<String, Object> metaMap = PacketManagerHelper.getMetaMap(packetInfo);
			metaMap = getAdapter().addObjectMetaData(PACKET_MANAGER_ACCOUNT, packet.getPacketInfo().getId(),
					packet.getPacketInfo().getSource(), packet.getPacketInfo().getProcess(),
					packet.getPacketInfo().getPacketName(), metaMap);

			return PacketManagerHelper.getPacketInfo(metaMap);
		} catch (Exception e) {
			String packetId = packet.getPacketInfo().getId();

			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, packetId,
					ExceptionUtils.getStackTrace(e));
			if (e instanceof BaseCheckedException ex) {
				throw new PacketKeeperException(ex.getErrorCode(), ex.getMessage());
			} else if (e instanceof BaseUncheckedException ex) {
				throw new PacketKeeperException(ex.getErrorCode(), ex.getMessage());
			}

			throw new PacketKeeperException(PacketUtilityErrorCodes.PACKET_KEEPER_PUT_ERROR.getErrorCode(),
					"Failed to persist packet in object store : " + e.getMessage(), e);
		}
	}

	private ObjectStoreAdapter getAdapter() {
		ObjectStoreAdapter adapter = adapterRegistry.get(adapterName);
		if (adapter == null) {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, "PacketKeeper",
					"Invalid adapter name: " + adapterName);
			throw new ObjectStoreAdapterException("Invalid adapter name configured: " + adapterName);
		}
		return adapter;
	}

	private IPacketCryptoService getCryptoService() {
		IPacketCryptoService cryptoService = cryptoRegistry.get(cryptoName);
		if (cryptoService == null) {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, "PacketKeeper",
					"Invalid crypto name: " + cryptoName);
			throw new CryptoException("Invalid crypto name configured: " + cryptoName);
		}
		return cryptoService;
	}

	private static String getName(String id, String name) {
		return id + UNDERSCORE + name;
	}

	public boolean deletePacket(String id, String source, String process) {
		return getAdapter().removeContainer(PACKET_MANAGER_ACCOUNT, id, source, process);
	}

	public boolean pack(String id, String source, String process, String refId) {
		return getAdapter().pack(PACKET_MANAGER_ACCOUNT, id, source, process, refId);
	}

	public Map<String, String> addTags(TagDto tagDto) {
		Map<String, String> tags = getAdapter().addTags(PACKET_MANAGER_ACCOUNT, tagDto.getId(), tagDto.getTags());
		return tags;
	}

	public Map<String, String> addorUpdate(TagDto tagDto) {
		Map<String, String> tags = getAdapter().addTags(PACKET_MANAGER_ACCOUNT, tagDto.getId(), tagDto.getTags());
		return tags;
	}

	public Map<String, String> getTags(String id) {
		Map<String, String> existingTags = getAdapter().getTags(PACKET_MANAGER_ACCOUNT, id);
		return existingTags;
	}

	public List<ObjectDto> getAll(String id) {
		List<ObjectDto> allObjects = getAdapter().getAllObjects(PACKET_MANAGER_ACCOUNT, id);
		return allObjects;
	}

	public void deleteTags(TagRequestDto tagRequestDto) {
		getAdapter().deleteTags(PACKET_MANAGER_ACCOUNT, tagRequestDto.getId(), tagRequestDto.getTagNames());

	}
}