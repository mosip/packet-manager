package io.mosip.commons.packetmanager.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private String key = null;
    private static final String sourceInitial = "source:";
    private static final String processInitial = "process:";
    private JSONObject mappingJson = null;

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
            for (ObjectDto o : allObjects) {
                if (!containerInfoDtos.stream().anyMatch(info -> info.getSource().equalsIgnoreCase(o.getSource()) && info.getProcess().equalsIgnoreCase(o.getProcess()))) {
                    ContainerInfoDto containerInfo = new ContainerInfoDto();
                    containerInfo.setSource(o.getSource());
                    containerInfo.setProcess(o.getProcess());
                    containerInfo.setLastModified(o.getLastModified());

                    //get demographic fields
                    Set<String> demographics = packetReader.getAllKeys(id, containerInfo.getSource(), containerInfo.getProcess());
                    // get biometrics
                    List<BiometricsDto> biometrics = null;
                    BiometricRecord br = packetReader.getBiometric(id, getKey(), Lists.newArrayList(), o.getSource(), o.getProcess(), false);
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
                                List<String> finalVal =  biomap.get(key);
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
                    containerInfoDtos.add(containerInfo);
                }
            }
            // get tags
            Map<String, String> tags = packetReader.getTags(id);

            InfoResponseDto infoResponseDto = new InfoResponseDto();
            infoResponseDto.setApplicationId(id);
            infoResponseDto.setPacketId(id);
            infoResponseDto.setInfo(containerInfoDtos);
			infoResponseDto.setTags(tags);
            return infoResponseDto;
        } catch (Exception e) {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, id, ExceptionUtils.getStackTrace(e));

            if (e instanceof BaseUncheckedException) {
                BaseUncheckedException ex = (BaseUncheckedException) e;
                throw ex;
            }
            else if (e instanceof BaseCheckedException) {
                BaseCheckedException ex = (BaseCheckedException) e;
                throw new BaseUncheckedException(ex.getErrorCode(), ex.getMessage(), ex);
            }
            else
                throw new BaseUncheckedException(PacketUtilityErrorCodes.UNKNOWN_EXCEPTION.getErrorCode(), e.getMessage(), e);
        }
    }

    private String getKey() throws IOException {
        if (key != null)
            return key;
        JSONObject jsonObject = getMappingJsonFile();
        if(jsonObject == null)
            return null;
        LinkedHashMap<String, String> individualBio = (LinkedHashMap) jsonObject.get(INDIVIDUAL_BIOMETRICS);
        key = individualBio.get(VALUE);
        return key;
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
                throw new SourceNotPresentException(e);
            }
        }
        ObjectDto objectDto = searchProcessWithLatestIteration(id, source, process);
        return new SourceProcessDto(objectDto.getSource(), objectDto.getProcess());
    }

    public SourceProcessDto getSourceAndProcess(String id, String field, String source, String process) {
        SourceProcessDto sourceProcessDto = null;
        InfoResponseDto infoResponseDto = infoInternal(id);
        List<ContainerInfoDto> info = infoResponseDto.getInfo();
        // sorting in reverse order by process name to search from latest iteration first.
        Collections.sort(info, (i1, i2) -> extractInt(i2.getProcess()) - (extractInt(i1.getProcess())));
        if (StringUtils.isEmpty(source)) {
            try {
                if (defaultStrategy.equalsIgnoreCase(DefaultStrategy.DEFAULT_PRIORITY.getValue())) {
                    ContainerInfoDto containerInfoDto = findPriority(field, info);
                    if (containerInfoDto == null)
                        return null;
                    sourceProcessDto = new SourceProcessDto(containerInfoDto.getSource(), containerInfoDto.getProcess());
                }
            } catch (Exception e) {
                throw new SourceNotPresentException(e);
            }

        } else {
            ContainerInfoDto containerInfoDto = getContainerInfoBySourceAndProcess(field, source, process, info);
            sourceProcessDto = containerInfoDto != null ?
                    new SourceProcessDto(containerInfoDto.getSource(), containerInfoDto.getProcess()) : null;
        }
        return sourceProcessDto;
    }

    public ContainerInfoDto findPriority(String field, List<ContainerInfoDto> info) {
        if (info.size() == 1)
            return info.iterator().next();
        else
            return getContainerInfoByDefaultPriority(field, info);
    }

    private ContainerInfoDto getContainerInfoByDefaultPriority(String field, List<ContainerInfoDto> info) {
        if (StringUtils.isNotEmpty(defaultPriority)) {
            String[] val = defaultPriority.split(",");
            if (val != null && val.length > 0) {
                for (String value : val) {
                    String[] str = value.split("/");
                    if (str != null && str.length > 0 && str[0].startsWith(sourceInitial)) {
                        String sourceStr = str[0].substring(sourceInitial.length());
                        String processStr = str[1].substring(processInitial.length());
                        for (String process : processStr.split("\\|")) {
                            Optional<ContainerInfoDto> containerDto = info.stream().filter(infoDto ->
                                    isFieldPresent(field, infoDto) && infoDto.getSource().equalsIgnoreCase(sourceStr)
                            && PacketHelper.getProcessWithoutIteration(infoDto.getProcess()).equalsIgnoreCase(process)).findAny();
                            // if container is not present then continue searching
                            if (containerDto.isPresent()) {
                                return containerDto.get();
                            } else
                                continue;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isFieldPresent(String field, ContainerInfoDto infoDto) {
        if (additionalFieldsSearch.contains(field))
            return true;
        else
            return infoDto.getDemographics() != null && infoDto.getDemographics().contains(field);
    }

    private ContainerInfoDto getContainerInfoBySourceAndProcess(String field, String source, String process, List<ContainerInfoDto> info) {
        Optional<ContainerInfoDto> containerDto = info.stream().filter(infoDto ->
                infoDto.getDemographics() != null && infoDto.getDemographics().contains(field) && infoDto.getSource().equalsIgnoreCase(source)
                        && PacketHelper.getProcessWithoutIteration(infoDto.getProcess()).equalsIgnoreCase(process)).findAny();

        return containerDto.isPresent() ? containerDto.get() : null;
    }

    private String getDefaultSource(String process) {
        if (StringUtils.isNotEmpty(defaultPriority)) {
            String[] val = defaultPriority.split(",");
            if (val != null && val.length > 0) {
                for (String value : val) {
                    String[] str = value.split("/");
                    if (str != null && str.length > 0 && str[0].startsWith(sourceInitial)) {
                        String sourceStr = str[0].substring(sourceInitial.length());
                        String processStr = str[1].substring(processInitial.length());
                        String[] processes = processStr.split("\\|");
                        if (Arrays.stream(processes).filter(p -> p.equalsIgnoreCase(process)).findAny().isPresent())
                            return sourceStr;
                    }
                }
            }
        } else
            throw new SourceNotPresentException();
        return null;
    }

    private ObjectDto searchProcessWithLatestIteration(String id, String source, String process) {
        List<ObjectDto> allObjects = packetReader.info(id);
        Collections.sort(allObjects, (i1, i2) -> extractInt(i2.getProcess()) - (extractInt(i1.getProcess())));

        Optional<ObjectDto> objectDto = allObjects.stream().filter(obj ->
                obj.getSource().equals(source)
                        && PacketHelper.getProcessWithoutIteration(obj.getProcess()).equalsIgnoreCase(process)).findAny();

        return objectDto.isPresent() ? objectDto.get() : getObjectDto(source, process);
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
        if(jsonObject == null)
            return null;
        LinkedHashMap identity = (LinkedHashMap) jsonObject.get(key);
        return identity != null ? new JSONObject(identity) : null;
    }

    private JSONObject getMappingJsonFile() throws IOException {
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
			List<String> tagNames=tagRequestDto.getTagNames();
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
     * If there are processes with multiple iteration then this method will merge these processes into one.
     * (Ex - if there are containers with processes - CORRECTION-1,CORRECTION-2,CORRECTION-3
     * then this method will merge 3 container into 1 container with process name - 'CORRECTION')
     *
     * @param infoResponseDto
     * @return InfoResponseDto
     */
    private InfoResponseDto mergeProcessWithMultipleIteration(InfoResponseDto infoResponseDto) {
        List<ContainerInfoDto> finalInfos = new ArrayList<>();
        // map contains unique source process without iteration.
        Map<String, List<String>> sourceProcessMap = new HashMap<>();

        for (ContainerInfoDto info : infoResponseDto.getInfo()) {
            String process = PacketHelper.getProcessWithoutIteration(info.getProcess());
            if (sourceProcessMap.containsKey(info.getSource()) && sourceProcessMap.get(info.getSource()).contains(process)) {
                // merge container info for same source process with multiple iteration
                ContainerInfoDto finalInfo = setContainerInfo(finalInfos, info, process);

                finalInfo.setDemographics(mergeDemographics(finalInfo, info));
                finalInfo.setBiometrics(mergeBiometrics(finalInfo, info));
                finalInfo.setDocuments(mergeDocuments(finalInfo, info));

                finalInfos.add(finalInfo);

            } else {
                // add unique source process in the sourceProcessMap
                List<String> processes = sourceProcessMap.get(info.getSource()) == null ?
                        Lists.newArrayList() : sourceProcessMap.get(info.getSource());
                processes.add(process);
                sourceProcessMap.put(info.getSource(), processes);
                // add container to info response for unique source process
                info.setProcess(process);
                finalInfos.add(info);
            }
        }
        infoResponseDto.setInfo(finalInfos);
        return infoResponseDto;
    }

    private ContainerInfoDto setContainerInfo(List<ContainerInfoDto> finalInfos, ContainerInfoDto info, String process) {
        ContainerInfoDto finalInfo = finalInfos.stream().filter(
                i -> i.getSource().equals(info.getSource()) && i.getProcess().equals(process)).findAny().get();
        finalInfos.remove(finalInfo);
        finalInfo.setDemographics(mergeDemographics(finalInfo, info));
        finalInfo.setBiometrics(mergeBiometrics(finalInfo, info));
        finalInfo.setDocuments(mergeDocuments(finalInfo, info));
        finalInfo.setLastModified(finalInfo.getLastModified().before(info.getLastModified()) ?
                info.getLastModified() : finalInfo.getLastModified());
        return finalInfo;
    }

    private Set<String> mergeDemographics(ContainerInfoDto existingInfo, ContainerInfoDto newInfo) {
        if (newInfo.getDemographics() == null)
            return existingInfo.getDemographics();

        Set<String> existingDemographics = existingInfo.getDemographics();
        for (String demoKey : newInfo.getDemographics())
            if (!existingDemographics.contains(demoKey))
                existingDemographics.add(demoKey);

        return existingDemographics;
    }

    private List<BiometricsDto> mergeBiometrics(ContainerInfoDto existingInfo, ContainerInfoDto newInfo) {
        if (newInfo.getBiometrics() == null)
            return existingInfo.getBiometrics();

        List<BiometricsDto> mergedBiometrics = new ArrayList<>();
        List<BiometricsDto> newInfoBiometrics = newInfo.getBiometrics();
        List<BiometricsDto> existingBiometrics = existingInfo.getBiometrics();
        for (BiometricsDto biometric : newInfoBiometrics) {
            Optional<BiometricsDto> existingBio = existingBiometrics.stream().filter(b -> b.getType().equals(biometric.getType())).findAny();
            // if type is already present in existing biometrics then merge all new subtypes
            if (existingBio.isPresent() && biometric.getSubtypes() != null && existingBio.get().getSubtypes() != null
                    && !existingBio.get().getSubtypes().containsAll(biometric.getSubtypes())) {
                BiometricsDto mergedBio = existingBio.get();
                mergedBio.getSubtypes().addAll(biometric.getSubtypes());
                mergedBiometrics.add(mergedBio);
            } else
                // else just add new biometrics
                mergedBiometrics.add(biometric);
        }
        return mergedBiometrics;
    }

    private Map<String, String> mergeDocuments(ContainerInfoDto existingInfo, ContainerInfoDto newInfo) {
        if (newInfo.getDocuments() == null)
            return existingInfo.getDocuments();

        // merged documents is initialized with existing documents
        Map<String, String> mergedDocuments = existingInfo.getDocuments() != null ? existingInfo.getDocuments() : Maps.newHashMap();

        for (String key : newInfo.getDocuments().keySet()) {
            if (!existingInfo.getDocuments().containsKey(key))
                mergedDocuments.put(key, newInfo.getDocuments().get(key));
        }

        return mergedDocuments;
    }

    private int extractInt(String s) {
        String num = s.replaceAll("\\D", "");
        // return 0 if no digits found
        return num.isEmpty() ? 0 : Integer.parseInt(num);
    }
}
