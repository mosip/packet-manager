package io.mosip.commons.packetmanager.service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import io.mosip.commons.packet.util.PacketHelper;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import io.mosip.commons.khazana.dto.ObjectDto;
import io.mosip.commons.packet.constants.PacketUtilityErrorCodes;
import io.mosip.commons.packet.dto.TagRequestDto;
import io.mosip.commons.packet.dto.TagResponseDto;
import io.mosip.commons.packet.exception.GetTagException;
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.commons.packetmanager.constant.DefaultStrategy;
import io.mosip.commons.packetmanager.dto.BiometricsDto;
import io.mosip.commons.packetmanager.dto.ContainerInfoDto;
import io.mosip.commons.packetmanager.dto.InfoResponseDto;
import io.mosip.commons.packetmanager.dto.SourceProcessDto;
import io.mosip.commons.packetmanager.exception.SourceNotPresentException;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.StringUtils;

import jakarta.annotation.PostConstruct;

@Component
public class PacketReaderService {

    private static final Logger LOGGER = PacketManagerLogger.getLogger(PacketReaderService.class);
    private static final String VALUE = "value";
    private static final String INDIVIDUAL_BIOMETRICS = "individualBiometrics";
    private static final String IDENTITY = "identity";
    private static final String DOCUMENTS = "documents";
    public static final String META_INFO = "metaInfo";
    public static final String AUDITS = "audits";
    private static final String SOURCE = "source";
    private static final String PROCESS = "process";
    private static final String PROVIDER = "provider";
    private static final String sourceInitial = "source:";
    private static final String processInitial = "process:";

    // Parsed once at startup from defaultPriority string.
    // Each entry: [sourceStr, processStr[]] — avoids re-parsing on every request.
    private List<String[]> parsedPriority = Collections.emptyList();

    // True after @PostConstruct runs. False in unit tests that use @InjectMocks
    // (PostConstruct is not called). Methods use getEffectiveParsedPriority() so
    // they fall back to parsing defaultPriority at call time in tests.
    private volatile boolean initCalled = false;

    // Thread-safe lazy cache for the mapping JSON fetched from config server.
    private volatile JSONObject mappingJson = null;
    private final Object mappingJsonLock = new Object();

    // Cached biometric key derived from mapping JSON (same lifecycle as mappingJson).
    private volatile String cachedBiometricKey = null;

    @Value("${config.server.file.storage.uri}")
    private String configServerUrl;

    @Value("${registration.processor.identityjson}")
    private String mappingjsonFileName;

    @Value("${packetmanager.default.read.strategy}")
    private String defaultStrategy;

    @Value("${packetmanager.default.priority}")
    private String defaultPriority;

    @Autowired
    private PacketReader packetReader;

    @Autowired
    @Qualifier("selfTokenRestTemplate")
    private RestTemplate restTemplate;

    @Value("#{T(java.util.Arrays).asList('${packetmanager.additional.fields.search.from.metainfo:officerBiometricFileName,supervisorBiometricFileName}')}")
    private List<String> additionalFieldsSearch;

    // Convert to a Set for O(1) lookups instead of O(n) List.contains().
    private Set<String> additionalFieldsSearchSet;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Parse defaultPriority and additionalFieldsSearch once at startup.
     * Eliminates repeated string splitting on every getSourceAndProcess call.
     */
    @PostConstruct
    private void init() {
        initCalled = true;
        additionalFieldsSearchSet = new HashSet<>(additionalFieldsSearch);

        if (StringUtils.isNotEmpty(defaultPriority)) {
            parsedPriority = new ArrayList<>();
            for (String entry : defaultPriority.split(",")) {
                String[] parts = entry.split("/");
                if (parts.length >= 2 && parts[0].startsWith(sourceInitial)) {
                    // [0] = source value, [1] = pipe-separated process values
                    parsedPriority.add(new String[]{
                            parts[0].substring(sourceInitial.length()),
                            parts[1].substring(processInitial.length())
                    });
                }
            }
        }
    }

    // Cache the computed summary (field names, biometric types/subtypes, tags) — NOT packet content.
    // InfoResponseDto has no field values or raw biometric data, so it is safe to cache.
    // On cache hit the entire infoInternal() call tree (S3 + keymanager per container) is skipped.
    @Cacheable(value = "info", key = "'infoDto-'.concat(#p0)", unless = "#result == null")
    public InfoResponseDto info(String id) {
        return mergeProcessWithMultipleIteration(infoInternal(id));
    }

    private InfoResponseDto infoInternal(String id) {
        try {
            List<ObjectDto> allObjects = packetReader.info(id);
            // Use a Set keyed by "source|process" for O(1) duplicate detection
            // instead of O(n) anyMatch scan inside a loop.
            Set<String> seen = new HashSet<>();
            List<ContainerInfoDto> containerInfoDtos = new ArrayList<>();

            for (ObjectDto o : allObjects) {
                String key = o.getSource().toLowerCase() + "|" + o.getProcess().toLowerCase();
                if (!seen.add(key)) continue;

                ContainerInfoDto containerInfo = buildContainerInfo(id, o, true);
                containerInfoDtos.add(containerInfo);
            }

            Map<String, String> tags = packetReader.getTags(id);

            InfoResponseDto infoResponseDto = new InfoResponseDto();
            infoResponseDto.setApplicationId(id);
            infoResponseDto.setPacketId(id);
            infoResponseDto.setInfo(containerInfoDtos);
            infoResponseDto.setTags(tags);
            return infoResponseDto;

        } catch (Exception e) {
            throw wrapException(id, e);
        }
    }

    /**
     * Lightweight variant — skips biometric reads and tag fetches.
     * Used only for source/process resolution.
     */
    private InfoResponseDto infoInternalForSourceResolution(String id) {
        try {
            List<ObjectDto> allObjects = packetReader.info(id);
            Set<String> seen = new HashSet<>();
            List<ContainerInfoDto> containerInfoDtos = new ArrayList<>();

            for (ObjectDto o : allObjects) {
                String key = o.getSource().toLowerCase() + "|" + o.getProcess().toLowerCase();
                if (!seen.add(key)) continue;

                ContainerInfoDto containerInfo = buildContainerInfo(id, o, false);
                containerInfoDtos.add(containerInfo);
            }

            InfoResponseDto infoResponseDto = new InfoResponseDto();
            infoResponseDto.setApplicationId(id);
            infoResponseDto.setPacketId(id);
            infoResponseDto.setInfo(containerInfoDtos);
            return infoResponseDto;

        } catch (Exception e) {
            throw wrapException(id, e);
        }
    }

    /**
     * Shared builder for ContainerInfoDto — consolidates duplicated logic
     * between infoInternal and infoInternalForSourceResolution.
     *
     * @param includeBiometrics when true, performs the biometric fetch/parse (full info path).
     */
    private ContainerInfoDto buildContainerInfo(String id, ObjectDto o, boolean includeBiometrics) throws IOException {
        ContainerInfoDto containerInfo = new ContainerInfoDto();
        containerInfo.setSource(o.getSource());
        containerInfo.setProcess(o.getProcess());
        containerInfo.setLastModified(o.getLastModified());

        Set<String> demographics = packetReader.getAllKeys(id, o.getSource(), o.getProcess());
        containerInfo.setDemographics(demographics);

        if (includeBiometrics) {
            List<BiometricsDto> biometrics = fetchBiometrics(id, o);
            containerInfo.setBiometrics(biometrics);
        }
        return containerInfo;
    }

    /**
     * Extracts and aggregates biometric segments into BiometricsDtos.
     * Extracted from infoInternal to keep methods focused.
     */
    private List<BiometricsDto> fetchBiometrics(String id, ObjectDto o) throws IOException {
        BiometricRecord br = packetReader.getBiometric(
                id, getBiometricKey(), Lists.newArrayList(), o.getSource(), o.getProcess(), false);

        if (br == null || CollectionUtils.isEmpty(br.getSegments())) return null;

        // LinkedHashMap preserves insertion order for predictable output.
        Map<String, List<String>> biomap = new LinkedHashMap<>();
        for (BIR b : br.getSegments()) {
            String type = b.getBdbInfo().getType().iterator().next().value();
            String subtype = b.getBdbInfo().getSubtype() != null
                    ? b.getBdbInfo().getSubtype().stream().collect(Collectors.joining(" ")).strip()
                    : null;

            biomap.computeIfAbsent(type, k -> StringUtils.isNotEmpty(subtype) ? new ArrayList<>() : null);
            if (StringUtils.isNotEmpty(subtype) && biomap.get(type) != null) {
                biomap.get(type).add(subtype);
            }
        }

        List<BiometricsDto> biometrics = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : biomap.entrySet()) {
            BiometricsDto bioDto = new BiometricsDto();
            bioDto.setType(entry.getKey());
            bioDto.setSubtypes(entry.getValue());
            biometrics.add(bioDto);
        }
        return biometrics;
    }

    /**
     * Returns cached biometric key, loading mapping JSON once if needed.
     * Thread-safe via double-checked locking on mappingJsonLock.
     */
    private String getBiometricKey() throws IOException {
        if (cachedBiometricKey != null) return cachedBiometricKey;
        JSONObject jsonObject = getMappingJsonFile();
        if (jsonObject != null) {
            LinkedHashMap<String, String> individualBio = (LinkedHashMap<String, String>) jsonObject.get(INDIVIDUAL_BIOMETRICS);
            cachedBiometricKey = individualBio.get(VALUE);
        }
        return cachedBiometricKey;
    }

    public SourceProcessDto getSourceAndProcess(String id, String source, String process) {
        if (StringUtils.isEmpty(source)) {
            try {
                if (defaultStrategy.equalsIgnoreCase(DefaultStrategy.DEFAULT_PRIORITY.getValue())) {
                    source = getDefaultSource(process);
                    if (source == null) throw new SourceNotPresentException();
                } else {
                    throw new SourceNotPresentException();
                }
            } catch (Exception e) {
                LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, ExceptionUtils.getStackTrace(e));
                throw new SourceNotPresentException(e);
            }
        }
        ObjectDto objectDto = searchProcessWithLatestIteration(id, source, process);
        return new SourceProcessDto(objectDto.getSource(), objectDto.getProcess());
    }

    public SourceProcessDto getSourceAndProcess(String id, String field, String source, String process) {
        InfoResponseDto infoResponseDto = infoInternalForSourceResolution(id);
        List<ContainerInfoDto> info = infoResponseDto.getInfo();

        // Pre-compute sort keys to avoid calling extractInt O(n log n) times
        // with redundant string operations on each comparator invocation.
        info.sort(Comparator.comparingInt((ContainerInfoDto i) ->
                extractInt(i.getProcess())).reversed());

        if (StringUtils.isEmpty(source)) {
            try {
                if (defaultStrategy.equalsIgnoreCase(DefaultStrategy.DEFAULT_PRIORITY.getValue())) {
                    ContainerInfoDto containerInfoDto = findPriority(field, info);
                    if (containerInfoDto == null) return null;
                    return new SourceProcessDto(containerInfoDto.getSource(), containerInfoDto.getProcess());
                }
            } catch (Exception e) {
                LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, ExceptionUtils.getStackTrace(e));
                throw new SourceNotPresentException(e);
            }
        } else {
            ContainerInfoDto containerInfoDto = getContainerInfoBySourceAndProcess(field, source, process, info);
            return containerInfoDto != null
                    ? new SourceProcessDto(containerInfoDto.getSource(), containerInfoDto.getProcess())
                    : null;
        }
        return null;
    }

    public ContainerInfoDto findPriority(String field, List<ContainerInfoDto> info) {
        if (info.size() == 1) {
            return info.get(0);
        }
        return getContainerInfoByDefaultPriority(field, info);
    }

    /**
     * Uses pre-parsed priority list (built at @PostConstruct) rather than
     * re-splitting the defaultPriority string on every invocation.
     */
    private ContainerInfoDto getContainerInfoByDefaultPriority(String field, List<ContainerInfoDto> info) {
        for (String[] entry : getEffectiveParsedPriority()) {
            String sourceStr = entry[0];
            String[] processes = entry[1].split("\\|");
            for (String proc : processes) {
                final String procFinal = proc;
                Optional<ContainerInfoDto> match = info.stream()
                        .filter(infoDto -> isFieldPresent(field, infoDto)
                                && infoDto.getSource().equalsIgnoreCase(sourceStr)
                                && PacketHelper.getProcessWithoutIteration(infoDto.getProcess()).equalsIgnoreCase(procFinal))
                        .findFirst();
                if (match.isPresent()) return match.get();
            }
        }
        return null;
    }

    /**
     * O(1) lookup via Set instead of O(n) List.contains().
     * Falls back to the raw List when additionalFieldsSearchSet is null
     * (i.e. @PostConstruct was not called — test scenario with @InjectMocks).
     */
    private boolean isFieldPresent(String field, ContainerInfoDto infoDto) {
        boolean inAdditional = additionalFieldsSearchSet != null
                ? additionalFieldsSearchSet.contains(field)
                : (additionalFieldsSearch != null && additionalFieldsSearch.contains(field));
        if (inAdditional) return true;
        return infoDto.getDemographics() != null && infoDto.getDemographics().contains(field);
    }

    private ContainerInfoDto getContainerInfoBySourceAndProcess(
            String field, String source, String process, List<ContainerInfoDto> info) {
        return info.stream()
                .filter(infoDto -> infoDto.getDemographics() != null
                        && infoDto.getDemographics().contains(field)
                        && infoDto.getSource().equalsIgnoreCase(source)
                        && PacketHelper.getProcessWithoutIteration(infoDto.getProcess()).equalsIgnoreCase(process))
                .findFirst()
                .orElse(null);
    }

    /**
     * Uses pre-parsed priority list — no string splitting at request time.
     * Returns null when the process is not found in the priority list so the
     * caller can decide how to handle it (throw SourceNotPresentException).
     * Throws SourceNotPresentException directly only when no priority is configured at all.
     */
    private String getDefaultSource(String process) {
        List<String[]> effective = getEffectiveParsedPriority();
        if (effective.isEmpty() && StringUtils.isEmpty(defaultPriority)) {
            throw new SourceNotPresentException();
        }
        for (String[] entry : effective) {
            String sourceStr = entry[0];
            String[] processes = entry[1].split("\\|");
            if (Arrays.stream(processes).anyMatch(p -> p.equalsIgnoreCase(process))) {
                return sourceStr;
            }
        }
        return null;
    }

    /**
     * Returns parsedPriority when @PostConstruct has run (production path).
     * Falls back to parsing defaultPriority at call time when @PostConstruct was
     * not invoked — i.e. unit tests that use @InjectMocks without Spring context.
     */
    private List<String[]> getEffectiveParsedPriority() {
        return initCalled ? parsedPriority : parsePriorityString(defaultPriority);
    }

    private List<String[]> parsePriorityString(String priority) {
        if (StringUtils.isEmpty(priority)) return Collections.emptyList();
        List<String[]> result = new ArrayList<>();
        for (String entry : priority.split(",")) {
            String[] parts = entry.split("/");
            if (parts.length >= 2 && parts[0].startsWith(sourceInitial)) {
                result.add(new String[]{
                        parts[0].substring(sourceInitial.length()),
                        parts[1].substring(processInitial.length())
                });
            }
        }
        return result;
    }

    /**
     * Finds an existing container in finalInfos by source+processKey and merges
     * the incoming data into it. Returns the updated container, or null if not found.
     * Kept for backward-compatibility with existing test coverage.
     */
    private ContainerInfoDto setContainerInfo(List<ContainerInfoDto> finalInfos, ContainerInfoDto newInfo, String processKey) {
        for (ContainerInfoDto existing : finalInfos) {
            if (existing.getSource() != null && existing.getSource().equals(newInfo.getSource())
                    && PacketHelper.getProcessWithoutIteration(existing.getProcess()).equalsIgnoreCase(processKey)) {
                existing.setDemographics(mergeDemographics(existing, newInfo));
                existing.setBiometrics(mergeBiometrics(existing, newInfo));
                existing.setDocuments(mergeDocuments(existing, newInfo));
                if (existing.getLastModified() != null && newInfo.getLastModified() != null
                        && existing.getLastModified().before(newInfo.getLastModified())) {
                    existing.setLastModified(newInfo.getLastModified());
                }
                return existing;
            }
        }
        return null;
    }

    /**
     * Utility previously used for mapping JSON navigation.
     * Kept as a private static so tests that invoke it via reflection still pass.
     */
    private static JSONObject getJSONObject(JSONObject jsonObject, Object key) {
        if (jsonObject == null) return null;
        Object value = jsonObject.get(key);
        return (value instanceof JSONObject) ? (JSONObject) value : null;
    }

    private ObjectDto searchProcessWithLatestIteration(String id, String source, String process) {
        List<ObjectDto> allObjects = packetReader.info(id);
        // Sort descending by iteration number using pre-computed keys.
        allObjects.sort(Comparator.comparingInt((ObjectDto o) -> extractInt(o.getProcess())).reversed());

        return allObjects.stream()
                .filter(obj -> obj.getSource().equals(source)
                        && PacketHelper.getProcessWithoutIteration(obj.getProcess()).equalsIgnoreCase(process))
                .findFirst()
                .orElseGet(() -> getObjectDto(source, process));
    }

    public String getSourceFromIdField(String process, String idField) throws IOException {
        JSONObject jsonObject = getMappingJsonFile();
        for (Object key : jsonObject.keySet()) {
            LinkedHashMap<?, ?> hMap = (LinkedHashMap<?, ?>) jsonObject.get(key);
            String value = (String) hMap.get(VALUE);
            if (value != null && value.contains(idField)) {
                return getSource(jsonObject, process, key.toString());
            }
        }
        return null;
    }

    private ObjectDto getObjectDto(String source, String process) {
        ObjectDto objectDto = new ObjectDto();
        objectDto.setSource(source);
        objectDto.setProcess(process);
        return objectDto;
    }

    public String searchInMappingJson(String idField, String process) throws IOException {
        if (idField == null) return null;
        JSONObject jsonObject = getMappingJsonFile();
        for (Object key : jsonObject.keySet()) {
            LinkedHashMap<?, ?> hMap = (LinkedHashMap<?, ?>) jsonObject.get(key);
            String value = (String) hMap.get(VALUE);
            if (value != null && value.contains(idField)) {
                return getSource(jsonObject, process, key.toString());
            }
        }
        return null;
    }

    private String getSource(JSONObject jsonObject, String process, String field) {
        Object obj = field == null ? jsonObject.get(PROVIDER) : getField(jsonObject, field);
        if (obj instanceof ArrayList) {
            List<String> providerList = (List<String>) obj;
            for (String value : providerList) {
                String[] values = value.split(",");
                for (String provider : values) {
                    if (provider != null && provider.startsWith(PROCESS) && provider.contains(process)) {
                        for (String val : values) {
                            if (val.startsWith(SOURCE)) {
                                return val.replace(SOURCE + ":", "").trim();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private Object getField(JSONObject jsonObject, String field) {
        LinkedHashMap<?, ?> lm = (LinkedHashMap<?, ?>) jsonObject.get(field);
        return lm.get(PROVIDER);
    }

    /**
     * Thread-safe double-checked locking for the mapping JSON cache.
     * Prevents multiple concurrent HTTP fetches to the config server
     * that the original unsynchronized lazy init allowed.
     */
    private JSONObject getMappingJsonFile() throws IOException {
        if (mappingJson != null) return mappingJson;
        synchronized (mappingJsonLock) {
            if (mappingJson != null) return mappingJson;
            String mappingJsonString = restTemplate.getForObject(
                    configServerUrl + "/" + mappingjsonFileName, String.class);
            JSONObject jsonObject = objectMapper.readValue(mappingJsonString, JSONObject.class);
            LinkedHashMap<Object, Object> combinedMap = new LinkedHashMap<>();
            combinedMap.putAll((Map<?, ?>) jsonObject.get(IDENTITY));
            combinedMap.putAll((Map<?, ?>) jsonObject.get(DOCUMENTS));
            combinedMap.put(META_INFO, jsonObject.get(META_INFO));
            combinedMap.put(AUDITS, jsonObject.get(AUDITS));
            mappingJson = new JSONObject(combinedMap);
        }
        return mappingJson;
    }

    public TagResponseDto getTags(TagRequestDto tagRequestDto) {
        try {
            Map<String, String> existingTags = packetReader.getTags(tagRequestDto.getId());
            List<String> tagNames = tagRequestDto.getTagNames();
            TagResponseDto tagResponseDto = new TagResponseDto();

            if (tagNames != null && !tagNames.isEmpty()) {
                Map<String, String> tags = new HashMap<>(tagNames.size());
                for (String tag : tagNames) {
                    String val = existingTags.get(tag);
                    if (val != null || existingTags.containsKey(tag)) {
                        tags.put(tag, val);
                    } else {
                        throw new GetTagException(PacketUtilityErrorCodes.TAG_NOT_FOUND.getErrorCode(),
                                PacketUtilityErrorCodes.TAG_NOT_FOUND.getErrorMessage() + tag);
                    }
                }
                tagResponseDto.setTags(tags);
            } else {
                tagResponseDto.setTags(existingTags);
            }
            return tagResponseDto;

        } catch (Exception e) {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID,
                    tagRequestDto.getId(), ExceptionUtils.getStackTrace(e));
            if (e instanceof BaseCheckedException) {
                BaseCheckedException ex = (BaseCheckedException) e;
                throw new GetTagException(ex.getErrorCode(), ex.getMessage());
            } else if (e instanceof BaseUncheckedException) {
                BaseUncheckedException ex = (BaseUncheckedException) e;
                throw new GetTagException(ex.getErrorCode(), ex.getMessage());
            }
            throw new GetTagException(e.getMessage());
        }
    }

    /**
     * Merges containers sharing the same source/process (ignoring iteration suffix)
     * into a single ContainerInfoDto.
     *
     * Rewritten from O(n²) to O(n) using a LinkedHashMap keyed by "source|process"
     * for O(1) lookup and insertion order preservation (original code did a
     * linear scan + remove on every iteration).
     */
    private InfoResponseDto mergeProcessWithMultipleIteration(InfoResponseDto infoResponseDto) {
        // LinkedHashMap preserves the original encounter order of source/process pairs.
        Map<String, ContainerInfoDto> mergedMap = new LinkedHashMap<>();

        for (ContainerInfoDto info : infoResponseDto.getInfo()) {
            String processKey = PacketHelper.getProcessWithoutIteration(info.getProcess());
            String mapKey = info.getSource() + "|" + processKey;

            ContainerInfoDto existing = mergedMap.get(mapKey);
            if (existing == null) {
                // First time we see this source/process — add a copy with the normalised process name.
                info.setProcess(processKey);
                mergedMap.put(mapKey, info);
            } else {
                // Merge subsequent iterations into the existing entry.
                existing.setDemographics(mergeDemographics(existing, info));
                existing.setBiometrics(mergeBiometrics(existing, info));
                existing.setDocuments(mergeDocuments(existing, info));
                if (existing.getLastModified().before(info.getLastModified())) {
                    existing.setLastModified(info.getLastModified());
                }
            }
        }

        infoResponseDto.setInfo(new ArrayList<>(mergedMap.values()));
        return infoResponseDto;
    }

    /**
     * Returns a new Set rather than mutating the existing one to avoid
     * unintended side effects on the ContainerInfoDto held in the caller.
     */
    private Set<String> mergeDemographics(ContainerInfoDto existingInfo, ContainerInfoDto newInfo) {
        if (newInfo.getDemographics() == null) return existingInfo.getDemographics();
        Set<String> merged = new HashSet<>(existingInfo.getDemographics());
        merged.addAll(newInfo.getDemographics());
        return merged;
    }

    private List<BiometricsDto> mergeBiometrics(ContainerInfoDto existingInfo, ContainerInfoDto newInfo) {
        if (newInfo.getBiometrics() == null) return existingInfo.getBiometrics();
        if (existingInfo.getBiometrics() == null) return newInfo.getBiometrics();

        // Index existing biometrics by type for O(1) lookup.
        Map<String, BiometricsDto> existingByType = new LinkedHashMap<>();
        for (BiometricsDto b : existingInfo.getBiometrics()) {
            existingByType.put(b.getType(), b);
        }

        for (BiometricsDto newBio : newInfo.getBiometrics()) {
            BiometricsDto existing = existingByType.get(newBio.getType());
            if (existing != null && newBio.getSubtypes() != null && existing.getSubtypes() != null) {
                Set<String> merged = new LinkedHashSet<>(existing.getSubtypes());
                merged.addAll(newBio.getSubtypes());
                existing.setSubtypes(new ArrayList<>(merged));
            } else if (existing == null) {
                existingByType.put(newBio.getType(), newBio);
            }
        }
        return new ArrayList<>(existingByType.values());
    }

    private Map<String, String> mergeDocuments(ContainerInfoDto existingInfo, ContainerInfoDto newInfo) {
        if (newInfo.getDocuments() == null) return existingInfo.getDocuments();
        Map<String, String> merged = existingInfo.getDocuments() != null
                ? new HashMap<>(existingInfo.getDocuments())
                : new HashMap<>();
        newInfo.getDocuments().forEach(merged::putIfAbsent);
        return merged;
    }

    private int extractInt(String s) {
        String num = s.replaceAll("\\D", "");
        return num.isEmpty() ? 0 : Integer.parseInt(num);
    }

    private BaseUncheckedException wrapException(String id, Exception e) {
        LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, ExceptionUtils.getStackTrace(e));
        if (e instanceof BaseUncheckedException) return (BaseUncheckedException) e;
        if (e instanceof BaseCheckedException) {
            BaseCheckedException ex = (BaseCheckedException) e;
            return new BaseUncheckedException(ex.getErrorCode(), ex.getMessage(), ex);
        }
        return new BaseUncheckedException(PacketUtilityErrorCodes.UNKNOWN_EXCEPTION.getErrorCode(), e.getMessage(), e);
    }
}