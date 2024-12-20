package io.mosip.commons.packet.facade;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import io.mosip.commons.khazana.dto.ObjectDto;
import io.mosip.commons.packet.dto.Document;
import io.mosip.commons.packet.exception.NoAvailableProviderException;
import io.mosip.commons.packet.keeper.PacketKeeper;
import io.mosip.commons.packet.spi.IPacketReader;
import io.mosip.commons.packet.util.PacketHelper;
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

    @Autowired(required = false)
    @Qualifier("referenceReaderProviders")
    @Lazy
    private List<IPacketReader> referenceReaderProviders;

    @Autowired
    private PacketKeeper packetKeeper;

    /**
     * Get a field from identity file
     *
     * @param id      : the registration id
     * @param field   : field name to search
     * @param source  : the source packet. If not present return default
     * @param process : the process
     * @return String field
     */
    @PreAuthorize("hasRole('DATA_READ')")
    public String getField(String id, String field, String source, String process, boolean bypassCache) {
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                "getFields for fields : " + field + " source : " + source + " process : " + process);
        String value;
        if (bypassCache)
            value = getProvider(source, process).getField(id, field, source, process);
        else {
            Optional<Object> optionalValue = getAllFields(id, source, process).entrySet().stream().filter(m-> m.getKey().equalsIgnoreCase(field) && m.getValue()!=null).map(m -> m.getValue()).findAny();
            value = optionalValue.isPresent() ? optionalValue.get().toString() : null;
        }
        return value;
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
    @PreAuthorize("hasRole('DATA_READ')")
    public Map<String, String> getFields(String id, List<String> fields, String source, String process, boolean bypassCache) {
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                "getFields for fields : " + fields.toString() + " source : " + source + " process : " + process);
        Map<String, String> values;
        if (bypassCache)
            values = getProvider(source, process).getFields(id, fields, source, process);
        else {
            values = getAllFields(id, source, process).entrySet()
                    .stream().filter(m -> fields.contains(m.getKey())).collect(Collectors.toMap(m -> m.getKey(), m -> m.getValue() != null ? m.getValue().toString() : null));
        }
        return values;
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
    @PreAuthorize("hasRole('DOCUMENT_READ')")
    @Cacheable(value = "packets",key = "'documents'.concat('-').concat(#p0).concat('-').concat(#p1).concat('-').concat(#p2).concat('-').concat(#p3)")
    public Document getDocument(String id, String documentName, String source, String process) {
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                "getDocument for documentName : " + documentName + " source : " + source + " process : " + process);
        return getProvider(source, process).getDocument(id, documentName, source, process);
    }

    /**
     * Get biometric information by registration id, document name, source and process
     *
     * @param id         : the registration id
     * @param person     : The person (ex - applicant, operator, supervisor, introducer etc)
     * @param modalities : list of biometric modalities
     * @param source     : the source packet. If not present return default
     * @param process    : the process
     * @return BiometricRecord : the biometric record
     */
    @PreAuthorize("hasRole('BIOMETRIC_READ')")
    @Cacheable(value = "packets", key = "'biometrics'.concat('-').#p0.concat('-').concat(#p1).concat('-').concat(#p2).concat('-').concat(#p3).concat('-').concat(#p4)", condition = "#p5 == false")
    public BiometricRecord getBiometric(String id, String person, List<String> modalities, String source, String process, boolean bypassCache) {

        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                "getBiometric for source : " + source + " process : " + process);
        return getProvider(source, process).getBiometric(id, person, modalities, source, process);
    }

    /**
     * Get packet meta information by registration id, source and process
     *
     * @param id      : the registration id
     * @param source  : the source packet. If not present return default
     * @param process : the process
     * @return Map fields
     */
    @PreAuthorize("hasRole('METADATA_READ')")
    @Cacheable(value = "packets", key ="{'metaInfo'.concat('-').concat(#p0).concat('-').concat(#p1).concat('-').concat(#p2)}", condition = "#p3 == false")
    public Map<String, String> getMetaInfo(String id, String source, String process, boolean bypassCache) {
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                "getMetaInfo for source : " + source + " process : " + process);
        return getProvider(source, process).getMetaInfo(id, source, process);
    }

    /**
     * Get all field names from identity object
     *
     * @param id
     * @param source
     * @param process
     * @return
     */
    @PreAuthorize("hasRole('DATA_READ')")
    public List<ObjectDto> info(String id) {
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                "info called");
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
    @PreAuthorize("hasRole('DATA_READ')")
    public Set<String> getAllKeys(String id, String source, String process) {
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                "getAllKeys for source : " + source + " process : " + process);
        return getProvider(source, process).getAll(id, source, process).keySet();
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
                "getAllFields for source : " + source + " process : " + process);
        return getProvider(source, process).getAll(id, source, process);
    }

    /**
     * Get all fields from packet by id, source and process
     *
     * @param id      : the registration id
     * @param source  : the source packet. If not present return default
     * @param process : the process
     * @return Map fields
     */
    @Cacheable(value = "packets", key = "{#p0.concat('-').concat(#p1).concat('-').concat(#p2)}", condition = "#p3 == false")
    public List<Map<String, String>> getAudits(String id, String source, String process, boolean bypassCache) {
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                "getAllFields for source : " + source + " process : " + process);
        return getProvider(source, process).getAuditInfo(id, source, process);
    }

    @Cacheable(value = "tags", key = "{#p0}")
    public  Map<String, String>  getTags(String id) {
        Map<String, String> tags = packetKeeper.getTags(id);
        return tags;
    }

    public boolean validatePacket(String id, String source, String process) {
        return getProvider(source, process).validatePacket(id, source, process);
    }

    private IPacketReader getProvider(String source, String process) {
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, null,
                "getProvider for source : " + source + " process : " + process);
        IPacketReader provider = null;
        if (referenceReaderProviders != null && !referenceReaderProviders.isEmpty()) {
            Optional<IPacketReader> refProvider = referenceReaderProviders.stream().filter(refPr ->
                    (PacketHelper.isSourceAndProcessPresent(refPr.getClass().getName(), source, process, PacketHelper.Provider.READER))).findAny();
            if (refProvider.isPresent() && refProvider.get() != null)
                provider = refProvider.get();
        }

        if (provider == null) {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, null,
                    "No available provider found for source : " + source + " process : " + process);
            throw new NoAvailableProviderException();
        }

        return provider;
    }

}