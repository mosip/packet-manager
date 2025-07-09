package io.mosip.commons.packet.facade;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import io.micrometer.core.annotation.Timed;
import io.mosip.commons.khazana.dto.ObjectDto;
import io.mosip.commons.packet.dto.Document;
import io.mosip.commons.packet.keeper.PacketKeeper;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.logger.spi.Logger;

/**
 * The packet Reader facade
 */
@RefreshScope
@Component
public class PacketReader {
	private static final Logger LOGGER = PacketManagerLogger.getLogger(PacketReader.class);

	@Autowired
	private PacketKeeper packetKeeper;

	@Autowired
	private PacketReaderProviderRegistry providerRegistry;

	/**
	 * Get a field from identity file
	 *
	 * @param id      : the registration id
	 * @param field   : field name to search
	 * @param source  : the source packet. If not present return default
	 * @param process : the process
	 * @return String field
	 */
	@Timed("packet.reader.getField")
	@PreAuthorize("hasRole('DATA_READ')")
	public String getField(String id, String field, String source, String process, boolean bypassCache) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getField - field={}, source={}, process={}", field, source, process);

		if (bypassCache) {
			return providerRegistry.getReaderProvider(source, process).getField(id, field, source, process);
		}

		return getAllFields(id, source, process).entrySet().stream()
				.filter(e -> field.equalsIgnoreCase(e.getKey()) && e.getValue() != null)
				.map(e -> e.getValue().toString()).findFirst().orElse(null);
	}

	/**
	 * Get fields from identity file
	 *
	 * @param id      : the registration id
	 * @param fields  : fields to search
	 * @param source  : the source packet. If not present return default
	 * @param process : the process
	 * @return Map fields
	 */
	@Timed("packet.reader.getFields")
	@PreAuthorize("hasRole('DATA_READ')")
	public Map<String, String> getFields(String id, List<String> fields, String source, String process,
			boolean bypassCache) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getFields - fields={}, source={}, process={}", fields, source, process);

		if (bypassCache) {
			return providerRegistry.getReaderProvider(source, process).getFields(id, fields, source, process);
		}

		return getAllFields(id, source, process).entrySet().stream().filter(entry -> fields.contains(entry.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey,
						e -> e.getValue() != null ? e.getValue().toString() : null));
	}

	/**
	 * Get document by registration id, document name, source and process
	 *
	 * @param id           : the registration id
	 * @param documentName : the document name
	 * @param source       : the source packet. If not present return default
	 * @param process      : the process
	 * @return Document : document information
	 */
	@Timed("packet.reader.getDocument")
	@PreAuthorize("hasRole('DOCUMENT_READ')")
	@Cacheable(value = "packets", key = "'documents'.concat('-').concat(#p0).concat('-').concat(#p1).concat('-').concat(#p2).concat('-').concat(#p3)")
	public Document getDocument(String id, String documentName, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getDocument - documentName={}, source={}, process={}", documentName, source, process);
		return providerRegistry.getReaderProvider(source, process).getDocument(id, documentName, source, process);
	}

	/**
	 * Get biometric information by registration id, document name, source and
	 * process
	 *
	 * @param id         : the registration id
	 * @param person     : The person (ex - applicant, operator, supervisor,
	 *                   introducer etc)
	 * @param modalities : list of biometric modalities
	 * @param source     : the source packet. If not present return default
	 * @param process    : the process
	 * @return BiometricRecord : the biometric record
	 */
	@Timed("packet.reader.getBiometric")
	@PreAuthorize("hasRole('BIOMETRIC_READ')")
	@Cacheable(value = "packets", key = "'biometrics'.concat('-').#p0.concat('-').concat(#p1).concat('-').concat(#p2).concat('-').concat(#p3).concat('-').concat(#p4)", condition = "#p5 == false")
	public BiometricRecord getBiometric(String id, String person, List<String> modalities, String source,
			String process, boolean bypassCache) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getBiometric - source={}, process={}", source, process);

		return providerRegistry.getReaderProvider(source, process).getBiometric(id, person, modalities, source,
				process);
	}

	/**
	 * Get packet meta information by registration id, source and process
	 *
	 * @param id      : the registration id
	 * @param source  : the source packet. If not present return default
	 * @param process : the process
	 * @return Map fields
	 */
	@Timed("packet.reader.getMetaInfo")
	@PreAuthorize("hasRole('METADATA_READ')")
	@Cacheable(value = "packets", key = "{'metaInfo'.concat('-').concat(#p0).concat('-').concat(#p1).concat('-').concat(#p2)}", condition = "#p3 == false")
	public Map<String, String> getMetaInfo(String id, String source, String process, boolean bypassCache) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getMetaInfo - source={}, process={}", source, process);

		return providerRegistry.getReaderProvider(source, process).getMetaInfo(id, source, process);
	}

	/**
	 * Get all field names from identity object
	 *
	 * @param id
	 * @param source
	 * @param process
	 * @return
	 */
	@Timed("packet.reader.info")
	@PreAuthorize("hasRole('DATA_READ')")
	public List<ObjectDto> info(String id) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, "info called");
		return packetKeeper.getAll(id);
	}

	/**
	 * Get all field names from identity object
	 *
	 * @param id
	 * @param source
	 * @param process
	 * @return
	 */
	@Timed("packet.reader.getAllKeys")
	@PreAuthorize("hasRole('DATA_READ')")
	public Set<String> getAllKeys(String id, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getAllKeys - source={}, process={}", source, process);
		return providerRegistry.getReaderProvider(source, process).getAll(id, source, process).keySet();
	}

	/**
	 * Get all fields from packet by id, source and process
	 *
	 * @param id      : the registration id
	 * @param source  : the source packet. If not present return default
	 * @param process : the process
	 * @return Map fields
	 */
	private Map<String, Object> getAllFields(String id, String source, String process) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getAllFields - source={}, process={}", source, process);
		return providerRegistry.getReaderProvider(source, process).getAll(id, source, process);
	}

	/**
	 * Get all fields from packet by id, source and process
	 *
	 * @param id      : the registration id
	 * @param source  : the source packet. If not present return default
	 * @param process : the process
	 * @return Map fields
	 */
	@Timed("packet.reader.getAudits")
	@Cacheable(value = "packets", key = "{#p0.concat('-').concat(#p1).concat('-').concat(#p2)}", condition = "#p3 == false")
	public List<Map<String, String>> getAudits(String id, String source, String process, boolean bypassCache) {
		LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
				"getAudits - source={}, process={}", source, process);

		return providerRegistry.getReaderProvider(source, process).getAuditInfo(id, source, process);
	}

	@Cacheable(value = "tags", key = "{#p0}")
	public Map<String, String> getTags(String id) {
		Map<String, String> tags = packetKeeper.getTags(id);
		return tags;
	}

	public boolean validatePacket(String id, String source, String process) {
		return providerRegistry.getReaderProvider(source, process).validatePacket(id, source, process);
	}
}