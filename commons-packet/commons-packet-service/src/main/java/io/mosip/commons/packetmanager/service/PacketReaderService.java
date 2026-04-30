package io.mosip.commons.packetmanager.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import io.mosip.commons.packet.util.PacketHelper;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

@Component
public class PacketReaderService {

    private static Logger LOGGER = PacketManagerLogger.getLogger(PacketReaderService.class);
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
    private volatile JSONObject mappingJson = null;
    private volatile String parsedPriorityRaw;
    private volatile List<PriorityRule> parsedPriorityRules = Collections.emptyList();

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

    @Autowired
    private ObjectMapper objectMapper;

    public InfoResponseDto info(String id) {
        return mergeProcessWithMultipleIteration(infoInternal(id));
    }

    private InfoResponseDto infoInternal(String id) {
        try {
            List<ObjectDto> allObjects = packetReader.info(id);
            List<ContainerInfoDto> containerInfoDtos = new ArrayList<>();
            Map<String, ContainerInfoDto> uniqueContainers = new LinkedHashMap<>();
            String biometricKey = getKey();
            for (ObjectDto o : allObjects) {
                String sourceProcessKey = (o.getSource() + "#" + o.getProcess()).toLowerCase();
                if (uniqueContainers.containsKey(sourceProcessKey))
                    continue;

                ContainerInfoDto containerInfo = new ContainerInfoDto();
                containerInfo.setSource(o.getSource());
                containerInfo.setProcess(o.getProcess());
                containerInfo.setLastModified(o.getLastModified());

                // get demographic fields
                Set<String> demographics = packetReader.getAllKeys(id, containerInfo.getSource(), containerInfo.getProcess());
                // get biometrics
                List<BiometricsDto> biometrics = null;
                BiometricRecord br = packetReader.getBiometric(id, biometricKey, Lists.newArrayList(), o.getSource(),
                        o.getProcess(), false);
                if (br != null && !CollectionUtils.isEmpty(br.getSegments())) {
                    Map<String, List<String>> biomap = new HashMap<>();
                    for (BIR b : br.getSegments()) {
                        String key = b.getBdbInfo().getType().iterator().next().value();
                        String subtype = null;
                        if (b.getBdbInfo().getSubtype() != null) {
                            subtype = b.getBdbInfo().getSubtype().stream().collect(Collectors.joining(" ")).strip();
                        }

                        if (biomap.get(key) == null)
                            biomap.put(key, StringUtils.isNotEmpty(subtype) ? Lists.newArrayList(subtype) : null);
                        else {
                            List<String> finalVal = biomap.get(key);
                            finalVal.add(subtype);
                            biomap.put(key, finalVal);
                        }
                    }
                    biometrics = new ArrayList<>();
                    for (Map.Entry<String, List<String>> b : biomap.entrySet()) {
                        BiometricsDto bioDto = new BiometricsDto();
                        bioDto.setType(b.getKey());
                        bioDto.setSubtypes(b.getValue());
                        biometrics.add(bioDto);
                    }
                }

                containerInfo.setDemographics(demographics);
                containerInfo.setBiometrics(biometrics);
                uniqueContainers.put(sourceProcessKey, containerInfo);
            }
            containerInfoDtos.addAll(uniqueContainers.values());
            // get tags
            Map<String, String> tags = packetReader.getTags(id);

            InfoResponseDto infoResponseDto = new InfoResponseDto();
            infoResponseDto.setApplicationId(id);
            infoResponseDto.setPacketId(id);
            infoResponseDto.setInfo(containerInfoDtos);
            infoResponseDto.setTags(tags);
            return infoResponseDto;
        } catch (Exception e) {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                    ExceptionUtils.getStackTrace(e));

            if (e instanceof BaseUncheckedException) {
                BaseUncheckedException ex = (BaseUncheckedException) e;
                throw ex;
            } else if (e instanceof BaseCheckedException) {
                BaseCheckedException ex = (BaseCheckedException) e;
                throw new BaseUncheckedException(ex.getErrorCode(), ex.getMessage(), ex);
            } else
                throw new BaseUncheckedException(PacketUtilityErrorCodes.UNKNOWN_EXCEPTION.getErrorCode(),
                        e.getMessage(), e);
        }
    }

    private String getKey() throws IOException {

        JSONObject jsonObject = getMappingJsonFile();
        if (jsonObject != null) {
            LinkedHashMap<String, String> individualBio = (LinkedHashMap) jsonObject.get(INDIVIDUAL_BIOMETRICS);
            return individualBio.get(VALUE);
        }
        return null;
    }

    public SourceProcessDto getSourceAndProcess(String id, String source, String process) {
        if (StringUtils.isEmpty(source)) {
            try {
                if (defaultStrategy.equalsIgnoreCase(DefaultStrategy.DEFAULT_PRIORITY.getValue())) {
                    source = getDefaultSource(process);
                } else {
                    throw new SourceNotPresentException();
                }
            } catch (Exception e) {
                LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                        ExceptionUtils.getStackTrace(e));
                throw new SourceNotPresentException(e);
            }
        }
        ObjectDto objectDto = searchProcessWithLatestIteration(id, source, process);
        return new SourceProcessDto(objectDto.getSource(), objectDto.getProcess());
    }

    public SourceProcessDto getSourceAndProcess(String id, String field, String source, String process) {
        SourceProcessDto sourceProcessDto = null;
        List<ContainerInfoDto> info = getLightweightContainerInfo(id, field);
        Map<String, ContainerInfoDto> latestContainerIndex = buildLatestContainerIndex(info, field);
        if (StringUtils.isEmpty(source)) {
            try {
                if (defaultStrategy.equalsIgnoreCase(DefaultStrategy.DEFAULT_PRIORITY.getValue())) {
                    ContainerInfoDto containerInfoDto = findPriority(field, latestContainerIndex);
                    if (containerInfoDto == null)
                        return null;
                    sourceProcessDto = new SourceProcessDto(containerInfoDto.getSource(),
                            containerInfoDto.getProcess());
                }
            } catch (Exception e) {
                LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id,
                        ExceptionUtils.getStackTrace(e));
                throw new SourceNotPresentException(e);
            }

        } else {
            ContainerInfoDto containerInfoDto = getContainerInfoBySourceAndProcess(source, process, latestContainerIndex);
            sourceProcessDto = containerInfoDto != null
                    ? new SourceProcessDto(containerInfoDto.getSource(), containerInfoDto.getProcess())
                    : null;
        }
        return sourceProcessDto;
    }

    /**
     * Lightweight source/process view for high-throughput flows where biometrics
     * and tags are not required.
     */
    private List<ContainerInfoDto> getLightweightContainerInfo(String id, String field) {
        List<ObjectDto> allObjects = packetReader.info(id);
        Map<String, ContainerInfoDto> uniqueContainers = new LinkedHashMap<>();
        boolean needDemographics = field != null && (additionalFieldsSearch == null || !additionalFieldsSearch.contains(field));
        for (ObjectDto objectDto : allObjects) {
            String sourceProcessKey = (objectDto.getSource() + "#" + objectDto.getProcess()).toLowerCase();
            if (uniqueContainers.containsKey(sourceProcessKey))
                continue;

            ContainerInfoDto containerInfoDto = new ContainerInfoDto();
            containerInfoDto.setSource(objectDto.getSource());
            containerInfoDto.setProcess(objectDto.getProcess());
            containerInfoDto.setLastModified(objectDto.getLastModified());
            if (needDemographics) {
                containerInfoDto.setDemographics(
                        packetReader.getAllKeys(id, objectDto.getSource(), objectDto.getProcess()));
            }
            uniqueContainers.put(sourceProcessKey, containerInfoDto);
        }
        return new ArrayList<>(uniqueContainers.values());
    }

    public ContainerInfoDto findPriority(String field, List<ContainerInfoDto> info) {
        if (info.size() == 1)
            return info.iterator().next();
        else
            return getContainerInfoByDefaultPriority(field, info);
    }

    public ContainerInfoDto findPriority(String field, Map<String, ContainerInfoDto> latestContainerIndex) {
        if (latestContainerIndex.size() == 1)
            return latestContainerIndex.values().iterator().next();
        else
            return getContainerInfoByDefaultPriority(latestContainerIndex);
    }

    private ContainerInfoDto getContainerInfoByDefaultPriority(String field, List<ContainerInfoDto> info) {
        for (PriorityRule rule : getDefaultPriorityRules()) {
            for (String ruleProcess : rule.processes) {
                ContainerInfoDto match = getLatestContainer(field, info, rule.source, ruleProcess, true);
                if (match != null)
                    return match;
            }
        }
        return null;
    }

    private ContainerInfoDto getContainerInfoByDefaultPriority(Map<String, ContainerInfoDto> latestContainerIndex) {
        for (PriorityRule rule : getDefaultPriorityRules()) {
            for (String ruleProcess : rule.processes) {
                ContainerInfoDto match = latestContainerIndex.get(buildSourceProcessKey(rule.source, ruleProcess));
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private boolean isFieldPresent(String field, ContainerInfoDto infoDto) {
        if (additionalFieldsSearch != null && additionalFieldsSearch.contains(field))
            return true;
        else
            return infoDto.getDemographics() != null && infoDto.getDemographics().contains(field);
    }

    private ContainerInfoDto getContainerInfoBySourceAndProcess(String field, String source, String process,
                                                                List<ContainerInfoDto> info) {
        boolean additionalField = field != null && additionalFieldsSearch != null && additionalFieldsSearch.contains(field);
        if (additionalField) {
            return getLatestContainer(field, info, source, process, false);
        }
        return getLatestContainer(field, info, source, process, true);
    }

    private ContainerInfoDto getContainerInfoBySourceAndProcess(String source, String process,
                                                                Map<String, ContainerInfoDto> latestContainerIndex) {
        return latestContainerIndex.get(buildSourceProcessKey(source, process));
    }

    private String getDefaultSource(String process) {
        if (StringUtils.isEmpty(defaultPriority))
            throw new SourceNotPresentException();
        for (PriorityRule rule : getDefaultPriorityRules()) {
            for (String configuredProcess : rule.processes) {
                if (configuredProcess.equalsIgnoreCase(process)) {
                    return rule.source;
                }
            }
        }
        return null;
    }

    private ObjectDto searchProcessWithLatestIteration(String id, String source, String process) {
        List<ObjectDto> allObjects = packetReader.info(id);
        ObjectDto latest = null;
        int latestIteration = Integer.MIN_VALUE;
        for (ObjectDto obj : allObjects) {
            if (!obj.getSource().equals(source))
                continue;
            if (!PacketHelper.getProcessWithoutIteration(obj.getProcess()).equalsIgnoreCase(process))
                continue;
            int iteration = extractInt(obj.getProcess());
            if (latest == null || iteration > latestIteration) {
                latest = obj;
                latestIteration = iteration;
            }
        }
        return latest != null ? latest : getObjectDto(source, process);
    }

    public String getSourceFromIdField(String process, String idField) throws IOException {
        JSONObject jsonObject = getMappingJsonFile();
        for (Object key : jsonObject.keySet()) {
            LinkedHashMap hMap = (LinkedHashMap) jsonObject.get(key);
            String value = (String) hMap.get(VALUE);
            if (value != null && value.contains(idField)) {
                return getSource(jsonObject, process, key.toString());
            }
        }
        return null;
    }

    private ObjectDto getObjectDto(String source, String process) {
        ObjectDto objectDto1 = new ObjectDto();
        objectDto1.setSource(source);
        objectDto1.setProcess(process);
        return objectDto1;
    }

    public String searchInMappingJson(String idField, String process) throws IOException {
        if (idField != null) {
            JSONObject jsonObject = getMappingJsonFile();
            for (Object key : jsonObject.keySet()) {
                LinkedHashMap hMap = (LinkedHashMap) jsonObject.get(key);
                String value = (String) hMap.get(VALUE);
                if (value != null && value.contains(idField)) {
                    return getSource(jsonObject, process, key.toString());
                }
            }
        }
        return null;
    }

    private String getSource(JSONObject jsonObject, String process, String field) {
        String source = null;
        Object obj = field == null ? jsonObject.get(PROVIDER) : getField(jsonObject, field);
        if (obj != null && obj instanceof ArrayList) {
            List<String> providerList = (List) obj;
            for (String value : providerList) {
                String[] values = value.split(",");
                for (String provider : values) {
                    if (provider != null) {
                        if (provider.startsWith(PROCESS) && provider.contains(process)) {
                            for (String val : values) {
                                if (val.startsWith(SOURCE)) {
                                    return val.replace(SOURCE + ":", "").trim();
                                }
                            }
                        }
                    }
                }
            }
        }

        return source;
    }

    private Object getField(JSONObject jsonObject, String field) {
        LinkedHashMap lm = (LinkedHashMap) jsonObject.get(field);
        return lm.get(PROVIDER);
    }

    private static JSONObject getJSONObject(JSONObject jsonObject, Object key) {
        if (jsonObject == null)
            return null;
        LinkedHashMap identity = (LinkedHashMap) jsonObject.get(key);
        return identity != null ? new JSONObject(identity) : null;
    }

    private synchronized JSONObject getMappingJsonFile() throws IOException {
        if (mappingJson != null)
            return mappingJson;

        String mappingJsonString = restTemplate.getForObject(configServerUrl + "/" + mappingjsonFileName, String.class);
        JSONObject jsonObject = objectMapper.readValue(mappingJsonString, JSONObject.class);
        LinkedHashMap combinedMap = new LinkedHashMap();
        combinedMap.putAll((Map) jsonObject.get(IDENTITY));
        combinedMap.putAll((Map) jsonObject.get(DOCUMENTS));
        combinedMap.put(META_INFO, jsonObject.get(META_INFO));
        combinedMap.put(AUDITS, jsonObject.get(AUDITS));
        mappingJson = new JSONObject(combinedMap);
        return mappingJson;
    }

    public TagResponseDto getTags(TagRequestDto tagRequestDto) {
        try {
            Map<String, String> tags = new HashMap<String, String>();
            Map<String, String> existingTags = packetReader.getTags(tagRequestDto.getId());
            List<String> tagNames = tagRequestDto.getTagNames();
            TagResponseDto tagResponseDto = new TagResponseDto();
            if (tagNames != null && !tagNames.isEmpty()) {
                for (String tag : tagNames) {
                    if (existingTags.containsKey(tag)) {
                        tags.put(tag, existingTags.get(tag));
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
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, tagRequestDto.getId(),
                    ExceptionUtils.getStackTrace(e));
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
     * If there are processes with multiple iteration then this method will merge
     * these processes into one. (Ex - if there are containers with processes -
     * CORRECTION-1,CORRECTION-2,CORRECTION-3 then this method will merge 3
     * container into 1 container with process name - 'CORRECTION')
     *
     * @param infoResponseDto
     * @return InfoResponseDto
     */
    private InfoResponseDto mergeProcessWithMultipleIteration(InfoResponseDto infoResponseDto) {
        // Merge containers with same source + process (without iteration suffix).
        // Use a map to avoid O(n^2) scans.
        Map<String, ContainerInfoDto> merged = new LinkedHashMap<>();

        for (ContainerInfoDto info : infoResponseDto.getInfo()) {
            String baseProcess = PacketHelper.getProcessWithoutIteration(info.getProcess());
            String key = (info.getSource() + "#" + baseProcess).toLowerCase();

            if (merged.containsKey(key)) {
                ContainerInfoDto existing = merged.get(key);
                existing.setDemographics(mergeDemographics(existing, info));
                existing.setBiometrics(mergeBiometrics(existing, info));
                existing.setDocuments(mergeDocuments(existing, info));
                if (existing.getLastModified() == null || (info.getLastModified() != null
                        && existing.getLastModified().before(info.getLastModified()))) {
                    existing.setLastModified(info.getLastModified());
                }
            } else {
                info.setProcess(baseProcess);
                merged.put(key, info);
            }
        }

        infoResponseDto.setInfo(new ArrayList<>(merged.values()));
        return infoResponseDto;
    }

    private ContainerInfoDto setContainerInfo(List<ContainerInfoDto> finalInfos, ContainerInfoDto info,
                                              String process) {

        Optional<ContainerInfoDto> optionalInfo = finalInfos.stream()
                .filter(i -> i.getSource().equals(info.getSource()) && i.getProcess().equals(process)).findAny();

        if (optionalInfo.isPresent()) {

            ContainerInfoDto optionalInfovalue = optionalInfo.get();
            finalInfos.remove(optionalInfovalue);
            optionalInfovalue.setDemographics(mergeDemographics(optionalInfovalue, info));
            optionalInfovalue.setBiometrics(mergeBiometrics(optionalInfovalue, info));
            optionalInfovalue.setDocuments(mergeDocuments(optionalInfovalue, info));
            optionalInfovalue.setLastModified(
                    optionalInfovalue.getLastModified().before(info.getLastModified()) ? info.getLastModified()
                            : optionalInfovalue.getLastModified());
            return optionalInfovalue;
        }
        return null;

    }

    private Set<String> mergeDemographics(ContainerInfoDto existingInfo, ContainerInfoDto newInfo) {
        Set<String> newDemographics = newInfo.getDemographics();
        if (newDemographics == null || newDemographics.isEmpty())
            return existingInfo.getDemographics();

        Set<String> merged = existingInfo.getDemographics() != null ? new HashSet<>(existingInfo.getDemographics())
                : new HashSet<>();
        merged.addAll(newDemographics);
        return merged;
    }

    private List<BiometricsDto> mergeBiometrics(ContainerInfoDto existingInfo, ContainerInfoDto newInfo) {
        List<BiometricsDto> newBiometrics = newInfo.getBiometrics();
        if (newBiometrics == null || newBiometrics.isEmpty())
            return existingInfo.getBiometrics();

        List<BiometricsDto> existingBiometrics = existingInfo.getBiometrics();
        if (existingBiometrics == null || existingBiometrics.isEmpty())
            return newBiometrics;

        // Merge by biometric type.
        Map<String, BiometricsDto> byType = new LinkedHashMap<>();
        for (BiometricsDto b : existingBiometrics) {
            if (b != null && b.getType() != null) {
                byType.put(b.getType(), b);
            }
        }

        for (BiometricsDto incoming : newBiometrics) {
            if (incoming == null || incoming.getType() == null) {
                continue;
            }
            BiometricsDto existing = byType.get(incoming.getType());
            if (existing == null) {
                byType.put(incoming.getType(), incoming);
                continue;
            }

            // Merge subtypes (unique, preserve order best-effort).
            List<String> incomingSubtypes = incoming.getSubtypes();
            if (incomingSubtypes == null || incomingSubtypes.isEmpty())
                continue;

            List<String> existingSubtypes = existing.getSubtypes();
            if (existingSubtypes == null) {
                existing.setSubtypes(new ArrayList<>(incomingSubtypes));
            } else {
                Set<String> mergedSubtypes = new LinkedHashSet<>(existingSubtypes);
                mergedSubtypes.addAll(incomingSubtypes);
                existing.setSubtypes(new ArrayList<>(mergedSubtypes));
            }
        }

        return new ArrayList<>(byType.values());
    }

    private Map<String, String> mergeDocuments(ContainerInfoDto existingInfo, ContainerInfoDto newInfo) {
        if (newInfo.getDocuments() == null)
            return existingInfo.getDocuments();

        // merged documents is initialized with existing documents
        Map<String, String> mergedDocuments = existingInfo.getDocuments() != null ? existingInfo.getDocuments()
                : Maps.newHashMap();

        for (String key : newInfo.getDocuments().keySet()) {
            if (!mergedDocuments.containsKey(key))
                mergedDocuments.put(key, newInfo.getDocuments().get(key));
        }

        return mergedDocuments;
    }

    private int extractInt(String s) {
        if (s == null || s.isEmpty()) return 0;

        int result = 0;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) {
                int digit = ch - '0';
                if (result > (Integer.MAX_VALUE - digit) / 10) {
                    return Integer.MAX_VALUE; // or throw, based on your preference
                }
                result = result * 10 + digit;
            }
        }
        return result;
    }

    private ContainerInfoDto getLatestContainer(String field, List<ContainerInfoDto> info, String source, String process,
                                                boolean requireFieldPresence) {
        ContainerInfoDto latest = null;
        int latestIteration = Integer.MIN_VALUE;
        for (ContainerInfoDto infoDto : info) {
            if (!infoDto.getSource().equalsIgnoreCase(source))
                continue;
            if (!PacketHelper.getProcessWithoutIteration(infoDto.getProcess()).equalsIgnoreCase(process))
                continue;
            if (requireFieldPresence && !isFieldPresent(field, infoDto))
                continue;
            int iteration = extractInt(infoDto.getProcess());
            if (latest == null || iteration > latestIteration) {
                latest = infoDto;
                latestIteration = iteration;
            }
        }
        return latest;
    }

    private List<PriorityRule> getDefaultPriorityRules() {
        if (StringUtils.isEmpty(defaultPriority)) {
            return Collections.emptyList();
        }
        if (defaultPriority.equals(parsedPriorityRaw) && parsedPriorityRules != null) {
            return parsedPriorityRules;
        }
        synchronized (this) {
            if (defaultPriority.equals(parsedPriorityRaw) && parsedPriorityRules != null) {
                return parsedPriorityRules;
            }
            parsedPriorityRules = parsePriorityRules(defaultPriority);
            parsedPriorityRaw = defaultPriority;
            return parsedPriorityRules;
        }
    }

    private List<PriorityRule> parsePriorityRules(String priorityConfig) {
        List<PriorityRule> rules = new ArrayList<>();
        String[] values = priorityConfig.split(",");
        for (String value : values) {
            String[] parts = value.split("/");
            if (parts.length < 2 || !parts[0].startsWith(sourceInitial) || !parts[1].startsWith(processInitial))
                continue;
            String source = parts[0].substring(sourceInitial.length());
            String[] processes = parts[1].substring(processInitial.length()).split("\\|");
            List<String> normalized = Arrays.stream(processes).map(String::trim).filter(StringUtils::isNotEmpty)
                    .collect(Collectors.toList());
            if (!normalized.isEmpty()) {
                rules.add(new PriorityRule(source, normalized));
            }
        }
        return rules;
    }

    private Map<String, ContainerInfoDto> buildLatestContainerIndex(List<ContainerInfoDto> info, String field) {
        Map<String, ContainerInfoDto> index = new HashMap<>();
        boolean additionalField = field != null && additionalFieldsSearch != null && additionalFieldsSearch.contains(field);

        for (ContainerInfoDto infoDto : info) {
            if (!additionalField && !isFieldPresent(field, infoDto)) {
                continue;
            }
            String baseProcess = PacketHelper.getProcessWithoutIteration(infoDto.getProcess());
            String key = buildSourceProcessKey(infoDto.getSource(), baseProcess);

            ContainerInfoDto existing = index.get(key);
            if (existing == null || extractInt(infoDto.getProcess()) > extractInt(existing.getProcess())) {
                index.put(key, infoDto);
            }
        }
        return index;
    }

    private String buildSourceProcessKey(String source, String process) {
        return source.toLowerCase() + "#" + process.toLowerCase();
    }

    private static class PriorityRule {
        private final String source;
        private final List<String> processes;

        private PriorityRule(String source, List<String> processes) {
            this.source = source;
            this.processes = processes;
        }
    }
}