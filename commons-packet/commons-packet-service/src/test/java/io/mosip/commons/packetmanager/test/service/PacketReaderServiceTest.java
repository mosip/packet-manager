package io.mosip.commons.packetmanager.test.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Maps;
import io.mosip.commons.packet.impl.PacketReaderImpl;
import io.mosip.commons.packetmanager.dto.BiometricsDto;
import io.mosip.commons.packetmanager.dto.ContainerInfoDto;
import io.mosip.commons.packetmanager.dto.SourceProcessDto;
import io.mosip.commons.packetmanager.exception.SourceNotPresentException;
import org.assertj.core.util.Lists;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import io.mosip.commons.khazana.dto.ObjectDto;
import io.mosip.commons.packet.dto.TagRequestDto;
import io.mosip.commons.packet.dto.TagResponseDto;
import io.mosip.commons.packet.exception.GetTagException;
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.commons.packetmanager.dto.InfoResponseDto;
import io.mosip.commons.packetmanager.service.PacketReaderService;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.constant.QualityType;
import io.mosip.kernel.biometrics.entities.BDBInfo;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.biometrics.entities.RegistryIDType;
import io.mosip.kernel.core.exception.BaseUncheckedException;

@RunWith(SpringRunner.class)
public class PacketReaderServiceTest {

    private static final String id = "10001100770000320200720092256";

    @InjectMocks
    private PacketReaderService packetReaderService;

    @Mock
    private PacketReader packetReader;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RestTemplate restTemplate;

    @Before
    public void setup() throws IOException {
        ReflectionTestUtils.setField(packetReaderService, "configServerUrl", "localhost");
        ReflectionTestUtils.setField(packetReaderService, "mappingjsonFileName", "reg-proc.json");

        List<BIR> birTypeList = new ArrayList<>();
        BIR birType1 = new BIR.BIRBuilder().build();
        BDBInfo bdbInfoType1 = new BDBInfo.BDBInfoBuilder().build();
        RegistryIDType registryIDType = new RegistryIDType();
        registryIDType.setOrganization("Mosip");
        registryIDType.setType("257");
        QualityType quality = new QualityType();
        quality.setAlgorithm(registryIDType);
        quality.setScore(90l);
        bdbInfoType1.setQuality(quality);
        BiometricType singleType1 = BiometricType.FINGER;
        List<BiometricType> singleTypeList1 = new ArrayList<>();
        singleTypeList1.add(singleType1);
        List<String> subtype1 = new ArrayList<>(Arrays.asList("Left", "RingFinger"));
        bdbInfoType1.setSubtype(subtype1);
        bdbInfoType1.setType(singleTypeList1);
        birType1.setBdbInfo(bdbInfoType1);
        birTypeList.add(birType1);

        BiometricRecord biometricRecord = new BiometricRecord();
        biometricRecord.setSegments(birTypeList);
        when(packetReader.getBiometric(any(),any(),any(),any(),any(), anyBoolean())).thenReturn(biometricRecord);

        when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn("jsonobject");
        LinkedHashMap tempMap = new LinkedHashMap();
        JSONObject jsonObject = new JSONObject();
        LinkedHashMap<String, String> val = new LinkedHashMap<>();
        val.put("value", "individualBiometrics");
        tempMap.put("individualBiometrics", val);
        jsonObject.put("identity", tempMap);
        jsonObject.put("documents", tempMap);
        jsonObject.put("metaInfo", tempMap);
        jsonObject.put("audits", tempMap);
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(jsonObject);

        ObjectDto objectDto = new ObjectDto("REGISTRATION_CLIENT", "NEW", id + "_id", new Date());
        List<ObjectDto> allObjects = Lists.newArrayList(objectDto);
        when(packetReader.info(id)).thenReturn(allObjects);

        Set<String> demographics = Sets.newHashSet("name", "email", "phone", "individualBiometrics");
        when(packetReader.getAllKeys(id, objectDto.getSource(), objectDto.getProcess())).thenReturn(demographics);

    }

    @Test
    public void testInfoSuccess() {
        InfoResponseDto infoResponseDto = packetReaderService.info(id);

        assertTrue("Id should be equal.", infoResponseDto.getApplicationId().equals(id));
        assertTrue("Id should be equal.", infoResponseDto.getPacketId().equals(id));
        assertTrue("Size should be equal.", infoResponseDto.getInfo().size() == 1);
    }

    @Test(expected = BaseUncheckedException.class)
    public void testException() throws IOException {
        when(objectMapper.readValue(anyString(), any(Class.class))).thenThrow(new JsonMappingException("Mapping Exception"));

        packetReaderService.info(id);
    }
    @Test
    public void testGetTagsSuccess() {
        Map<String, String> tags = new HashMap<>();
        tags.put("test", "testValue");
        when(packetReader.getTags(anyString())).thenReturn(tags);
        TagRequestDto tagRequestDto=new TagRequestDto();
        tagRequestDto.setId("id");
        List<String> tagNames=new ArrayList<String>();
        tagNames.add("test");
        tagRequestDto.setTagNames(tagNames);
        TagResponseDto tagResponseDto=packetReaderService.getTags(tagRequestDto);
        assertEquals(tagResponseDto.getTags(), tags);
    }
    @Test(expected = GetTagException.class)
    public void testGetTagNotFound() {
        Map<String, String> tags = new HashMap<>();
        tags.put("test", "testValue");
        when(packetReader.getTags(anyString())).thenReturn(tags);
        TagRequestDto tagRequestDto=new TagRequestDto();
        tagRequestDto.setId("id");
        List<String> tagNames=new ArrayList<String>();
        tagNames.add("testtag");
        tagRequestDto.setTagNames(tagNames);
        packetReaderService.getTags(tagRequestDto);

    }
    @Test(expected = GetTagException.class)
    public void testGetTagsException() {
        when(packetReader.getTags(anyString())).thenThrow(new BaseUncheckedException("code","message"));
        TagRequestDto tagRequestDto=new TagRequestDto();
        tagRequestDto.setId("id");
        List<String> tagNames=new ArrayList<String>();
        tagNames.add("testtag");
        tagRequestDto.setTagNames(tagNames);
        packetReaderService.getTags(tagRequestDto);
    }

    /**
     * Tests getSourceAndProcess when source is empty - should return info response
     */
    @Test
    public void testGetSourceAndProcess_WhenSourceIsEmpty_ReturnsInfoResponse() {
        when(packetReader.info(anyString())).thenReturn(Lists.newArrayList(new ObjectDto("source1", "process1", "id", new Date())));

        InfoResponseDto result = packetReaderService.info("id");

        assertNotNull(result);
    }

    /**
     * Tests getSourceAndProcess when exception occurs - should throw BaseUncheckedException
     */
    @Test(expected = BaseUncheckedException.class)
    public void testGetSourceAndProcess_WhenExceptionOccurs_ThrowsBaseUncheckedException() {
        when(packetReader.info(anyString())).thenThrow(new RuntimeException("error"));

        packetReaderService.info("id");
    }

    /**
     * Tests info with multiple containers - should return multiple container info
     */
    @Test
    public void testInfo_WithMultipleContainers_ReturnsMultipleContainerInfo() {
        ObjectDto objectDto1 = new ObjectDto("source1", "process1", "id", new Date());
        ObjectDto objectDto2 = new ObjectDto("source2", "process2", "id", new Date());
        when(packetReader.info(anyString())).thenReturn(Lists.newArrayList(objectDto1, objectDto2));

        Set<String> demographics = Sets.newHashSet("name", "email");
        when(packetReader.getAllKeys("id", "source1", "process1")).thenReturn(demographics);
        when(packetReader.getAllKeys("id", "source2", "process2")).thenReturn(demographics);

        InfoResponseDto result = packetReaderService.info("id");

        assertEquals(2, result.getInfo().size());
    }

    /**
     * Tests info with empty demographics - should return empty demographics
     */
    @Test
    public void testInfo_WithEmptyDemographics_ReturnsEmptyDemographics() {
        ObjectDto objectDto = new ObjectDto("source1", "process1", "id", new Date());
        when(packetReader.info(anyString())).thenReturn(Lists.newArrayList(objectDto));
        when(packetReader.getAllKeys("id", "source1", "process1")).thenReturn(Sets.newHashSet());

        InfoResponseDto result = packetReaderService.info("id");

        assertEquals(1, result.getInfo().size());
        assertTrue(result.getInfo().get(0).getDemographics().isEmpty());
    }

    /**
     * Tests getSourceAndProcess with non-default strategy - should throw SourceNotPresentException
     */
    @Test
    public void testGetSourceAndProcess_WithNonDefaultStrategy_ThrowsSourceNotPresentException() {
        ReflectionTestUtils.setField(packetReaderService, "defaultStrategy", "OTHER_STRATEGY");

        try {
            packetReaderService.getSourceAndProcess("id", null, "process");
            fail("Should throw SourceNotPresentException");
        } catch (SourceNotPresentException e) {
            // Expected
        }
    }

    /**
     * Tests getSourceAndProcess with field null container - should return null
     */
    @Test
    public void testGetSourceAndProcess_WithFieldNullContainer_ReturnsNull() {
        InfoResponseDto infoResponse = new InfoResponseDto();
        infoResponse.setInfo(new ArrayList<>());

        when(packetReader.info(anyString())).thenReturn(new ArrayList<>());
        ReflectionTestUtils.setField(packetReaderService, "defaultStrategy", "DEFAULT_PRIORITY");

        SourceProcessDto result = packetReaderService.getSourceAndProcess("id", "field1", null, "process");

        assertNull(result);
    }

    /**
     * Tests getContainerInfoBySourceAndProcess when not found - should return null
     */
    @Test
    public void testGetContainerInfoBySourceAndProcess_WhenNotFound_ReturnsNull() {
        InfoResponseDto infoResponse = new InfoResponseDto();
        ContainerInfoDto containerInfo = new ContainerInfoDto();
        containerInfo.setSource("different");
        containerInfo.setProcess("different");
        containerInfo.setDemographics(Sets.newHashSet("otherField"));
        infoResponse.setInfo(Lists.newArrayList(containerInfo));

        when(packetReader.info(anyString())).thenReturn(new ArrayList<>());

        SourceProcessDto result = packetReaderService.getSourceAndProcess("id", "field1", "source1", "process");

        assertNull(result);
    }

    /**
     * Tests getDefaultSource with empty priority - should throw SourceNotPresentException
     */
    @Test
    public void testGetDefaultSource_WithEmptyPriority_ThrowsSourceNotPresentException() {
        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "");

        try {
            packetReaderService.getSourceAndProcess("id", null, "process");
            fail("Should throw SourceNotPresentException");
        } catch (SourceNotPresentException e) {
            // Expected
        }
    }

    /**
     * Tests getDefaultSource when returns null - should throw SourceNotPresentException
     */
    @Test
    public void testGetDefaultSource_WhenReturnsNull_ThrowsSourceNotPresentException() {
        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "source:source1/process:differentProcess");
        ReflectionTestUtils.setField(packetReaderService, "defaultStrategy", "DEFAULT_PRIORITY");

        try {
            packetReaderService.getSourceAndProcess("id", null, "process");
            fail("Should throw SourceNotPresentException");
        } catch (SourceNotPresentException e) {
            // Expected
        }
    }

    /**
     * Tests searchProcess with latest iteration not found - should return default source and process
     */
    @Test
    public void testSearchProcess_WithLatestIterationNotFound_ReturnsDefaultSourceAndProcess() {
        when(packetReader.info(anyString())).thenReturn(Lists.newArrayList(new ObjectDto("different", "different", "id", new Date())));
        ReflectionTestUtils.setField(packetReaderService, "defaultStrategy", "DEFAULT_PRIORITY");
        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "source:source1/process:process1");

        SourceProcessDto result = packetReaderService.getSourceAndProcess("id", "source1", "process1");

        assertEquals("source1", result.getSource());
        assertEquals("process1", result.getProcess());
    }

    /**
     * Tests getSourceFromIdField when field not found - should return null
     */
    @Test
    public void testGetSourceFromIdField_WhenFieldNotFound_ReturnsNull() throws IOException {
        JSONObject jsonObject = new JSONObject();

        LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
        LinkedHashMap<String, String> fieldMap = new LinkedHashMap<>();
        fieldMap.put("value", "differentField");
        identity.put("testField", fieldMap);

        LinkedHashMap<String, Object> documents = new LinkedHashMap<>();
        LinkedHashMap<String, Object> metaInfo = new LinkedHashMap<>();
        LinkedHashMap<String, Object> audits = new LinkedHashMap<>();

        jsonObject.put("identity", identity);
        jsonObject.put("documents", documents);
        jsonObject.put("metaInfo", metaInfo);
        jsonObject.put("audits", audits);

        when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn("jsonobject");
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(jsonObject);

        String result = packetReaderService.getSourceFromIdField("process1", "field1");

        assertNull(result);
    }

    /**
     * Tests searchInMappingJson with null field - should return null
     */
    @Test
    public void testSearchInMappingJson_WithNullField_ReturnsNull() throws IOException {
        String result = packetReaderService.searchInMappingJson(null, "process1");

        assertNull(result);
    }

    /**
     * Tests searchInMappingJson with non-ArrayList provider - should return null
     */
    @Test
    public void testSearchInMappingJson_WithNonArrayListProvider_ReturnsNull() throws IOException {
        JSONObject jsonObject = new JSONObject();

        LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
        LinkedHashMap<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("value", "field1");
        fieldMap.put("provider", "notAnArrayList");
        identity.put("testField", fieldMap);

        LinkedHashMap<String, Object> documents = new LinkedHashMap<>();
        LinkedHashMap<String, Object> metaInfo = new LinkedHashMap<>();
        LinkedHashMap<String, Object> audits = new LinkedHashMap<>();

        jsonObject.put("identity", identity);
        jsonObject.put("documents", documents);
        jsonObject.put("metaInfo", metaInfo);
        jsonObject.put("audits", audits);

        when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn("jsonobject");
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(jsonObject);

        String result = packetReaderService.searchInMappingJson("field1", "process1");

        assertNull(result);
    }

    /**
     * Tests getJSONObject with null input - should return null
     */
    @Test
    public void testGetJSONObject_WithNullInput_ReturnsNull() throws Exception {
        Method method = PacketReaderService.class.getDeclaredMethod("getJSONObject", JSONObject.class, Object.class);
        method.setAccessible(true);

        JSONObject result = (JSONObject) method.invoke(null, null, "key");

        assertNull(result);
    }

    /**
     * Tests getJSONObject with null identity - should return null
     */
    @Test
    public void testGetJSONObject_WithNullIdentity_ReturnsNull() throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", null);

        Method method = PacketReaderService.class.getDeclaredMethod("getJSONObject", JSONObject.class, Object.class);
        method.setAccessible(true);

        JSONObject result = (JSONObject) method.invoke(null, jsonObject, "key");

        assertNull(result);
    }

    /**
     * Tests setContainerInfo with existing container - should merge demographics
     */
    @Test
    public void testSetContainerInfo_WithExistingContainer_MergesDemographics() {
        List<ContainerInfoDto> finalInfos = new ArrayList<>();
        ContainerInfoDto existing = new ContainerInfoDto();
        existing.setSource("source1");
        existing.setProcess("process1");
        existing.setLastModified(new Date(1000));
        existing.setDemographics(Sets.newHashSet("name"));
        finalInfos.add(existing);

        ContainerInfoDto newInfo = new ContainerInfoDto();
        newInfo.setSource("source1");
        newInfo.setLastModified(new Date(2000));
        newInfo.setDemographics(Sets.newHashSet("email"));

        ContainerInfoDto result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "setContainerInfo", finalInfos, newInfo, "process1");

        assertNotNull(result);
        assertEquals("source1", result.getSource());
        assertTrue(result.getDemographics().contains("name"));
        assertTrue(result.getDemographics().contains("email"));
    }

    /**
     * Tests setContainerInfo when not found - should return null
     */
    @Test
    public void testSetContainerInfo_WhenNotFound_ReturnsNull() {
        List<ContainerInfoDto> finalInfos = new ArrayList<>();
        ContainerInfoDto newInfo = new ContainerInfoDto();
        newInfo.setSource("source1");

        ContainerInfoDto result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "setContainerInfo", finalInfos, newInfo, "process1");

        assertNull(result);
    }

    /**
     * Tests mergeDemographics with null - should return existing demographics
     */
    @Test
    public void testMergeDemographics_WithNull_ReturnsExistingDemographics() {
        ContainerInfoDto existing = new ContainerInfoDto();
        existing.setDemographics(Sets.newHashSet("name"));
        ContainerInfoDto newInfo = new ContainerInfoDto();

        Set<String> result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "mergeDemographics", existing, newInfo);

        assertEquals(existing.getDemographics(), result);
    }

    /**
     * Tests mergeBiometrics with null - should return existing biometrics
     */
    @Test
    public void testMergeBiometrics_WithNull_ReturnsExistingBiometrics() {
        ContainerInfoDto existing = new ContainerInfoDto();
        existing.setBiometrics(new ArrayList<>());
        ContainerInfoDto newInfo = new ContainerInfoDto();

        List<BiometricsDto> result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "mergeBiometrics", existing, newInfo);

        assertEquals(existing.getBiometrics(), result);
    }

    /**
     * Tests mergeBiometrics with same type - should merge subtypes
     */
    @Test
    public void testMergeBiometrics_WithSameType_MergesSubtypes() {
        BiometricsDto existingBio = new BiometricsDto();
        existingBio.setType("FINGER");
        existingBio.setSubtypes(Lists.newArrayList("LEFT"));

        BiometricsDto newBio = new BiometricsDto();
        newBio.setType("FINGER");
        newBio.setSubtypes(Lists.newArrayList("RIGHT"));

        ContainerInfoDto existing = new ContainerInfoDto();
        existing.setBiometrics(Lists.newArrayList(existingBio));
        ContainerInfoDto newInfo = new ContainerInfoDto();
        newInfo.setBiometrics(Lists.newArrayList(newBio));

        List<BiometricsDto> result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "mergeBiometrics", existing, newInfo);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getSubtypes().contains("LEFT"));
        assertTrue(result.get(0).getSubtypes().contains("RIGHT"));
    }

    /**
     * Tests mergeDocuments with null - should return existing documents
     */
    @Test
    public void testMergeDocuments_WithNull_ReturnsExistingDocuments() {
        ContainerInfoDto existing = new ContainerInfoDto();
        existing.setDocuments(Maps.newHashMap());
        ContainerInfoDto newInfo = new ContainerInfoDto();

        Map<String, String> result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "mergeDocuments", existing, newInfo);

        assertEquals(existing.getDocuments(), result);
    }

    /**
     * Tests extractInt with various inputs - should extract integer values
     */
    @Test
    public void testExtractInt_WithVariousInputs_ExtractsIntegerValues() {
        assertEquals(123, (int) ReflectionTestUtils.invokeMethod(packetReaderService, "extractInt", "abc123def"));
        assertEquals(0, (int) ReflectionTestUtils.invokeMethod(packetReaderService, "extractInt", "abcdef"));
        assertEquals(456, (int) ReflectionTestUtils.invokeMethod(packetReaderService, "extractInt", "456"));
    }

    /**
     * Tests findPriority with single item - should return that item
     */
    @Test
    public void testFindPriority_WithSingleItem_ReturnsThatItem() {
        ContainerInfoDto container = new ContainerInfoDto();
        container.setSource("source1");
        List<ContainerInfoDto> info = Lists.newArrayList(container);

        ContainerInfoDto result = packetReaderService.findPriority("field1", info);

        assertEquals(container, result);
    }

    /**
     * Tests findPriority with multiple items - should return priority item
     */
    @Test
    public void testFindPriority_WithMultipleItems_ReturnsPriorityItem() {
        ContainerInfoDto container1 = new ContainerInfoDto();
        container1.setSource("source1");
        container1.setProcess("process1");
        container1.setDemographics(Sets.newHashSet("field1"));

        ContainerInfoDto container2 = new ContainerInfoDto();
        container2.setSource("source2");

        List<ContainerInfoDto> info = Lists.newArrayList(container1, container2);

        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "source:source1/process:process1");
        ReflectionTestUtils.setField(packetReaderService, "additionalFieldsSearch", Lists.newArrayList());

        ContainerInfoDto result = packetReaderService.findPriority("field1", info);

        assertEquals(container1, result);
    }

    /**
     * Tests getContainerInfoByDefaultPriority when found - should return container
     */
    @Test
    public void testGetContainerInfoByDefaultPriority_WhenFound_ReturnsContainer() {
        ContainerInfoDto container = new ContainerInfoDto();
        container.setSource("source1");
        container.setProcess("process1");
        container.setDemographics(Sets.newHashSet("field1"));

        List<ContainerInfoDto> info = Lists.newArrayList(container);

        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "source:source1/process:process1");
        ReflectionTestUtils.setField(packetReaderService, "additionalFieldsSearch", Lists.newArrayList());

        ContainerInfoDto result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getContainerInfoByDefaultPriority", "field1", info);

        assertEquals(container, result);
    }

    /**
     * Tests getContainerInfoByDefaultPriority when not found - should return null
     */
    @Test
    public void testGetContainerInfoByDefaultPriority_WhenNotFound_ReturnsNull() {
        List<ContainerInfoDto> info = new ArrayList<>();

        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "source:source1/process:process1");

        ContainerInfoDto result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getContainerInfoByDefaultPriority", "field1", info);

        assertNull(result);
    }

    /**
     * Tests getContainerInfoByDefaultPriority with empty priority - should return null
     */
    @Test
    public void testGetContainerInfoByDefaultPriority_WithEmptyPriority_ReturnsNull() {
        List<ContainerInfoDto> info = new ArrayList<>();

        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "");

        ContainerInfoDto result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getContainerInfoByDefaultPriority", "field1", info);

        assertNull(result);
    }

    /**
     * Tests isFieldPresent in additional fields - should return true
     */
    @Test
    public void testIsFieldPresent_InAdditionalFields_ReturnsTrue() {
        ReflectionTestUtils.setField(packetReaderService, "additionalFieldsSearch", Lists.newArrayList("field1"));

        ContainerInfoDto container = new ContainerInfoDto();

        boolean result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "isFieldPresent", "field1", container);

        assertTrue(result);
    }

    /**
     * Tests isFieldPresent in demographics - should return true
     */
    @Test
    public void testIsFieldPresent_InDemographics_ReturnsTrue() {
        ReflectionTestUtils.setField(packetReaderService, "additionalFieldsSearch", Lists.newArrayList());

        ContainerInfoDto container = new ContainerInfoDto();
        container.setDemographics(Sets.newHashSet("field1"));

        boolean result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "isFieldPresent", "field1", container);

        assertTrue(result);
    }

    /**
     * Tests isFieldPresent when not found - should return false
     */
    @Test
    public void testIsFieldPresent_WhenNotFound_ReturnsFalse() {
        ReflectionTestUtils.setField(packetReaderService, "additionalFieldsSearch", Lists.newArrayList());

        ContainerInfoDto container = new ContainerInfoDto();
        container.setDemographics(Sets.newHashSet("otherField"));

        boolean result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "isFieldPresent", "field1", container);

        assertFalse(result);
    }

    /**
     * Tests getDefaultSource when found - should return source
     */
    @Test
    public void testGetDefaultSource_WhenFound_ReturnsSource() {
        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "source:source1/process:process1");

        String result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getDefaultSource", "process1");

        assertEquals("source1", result);
    }

    /**
     * Tests getDefaultSource with multiple processes - should return matching source
     */
    @Test
    public void testGetDefaultSource_WithMultipleProcesses_ReturnsMatchingSource() {
        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "source:source1/process:process1|process2");

        String result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getDefaultSource", "process2");

        assertEquals("source1", result);
    }

    /**
     * Tests getDefaultSource when process not found - should return null
     */
    @Test
    public void testGetDefaultSource_WhenProcessNotFound_ReturnsNull() {
        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "source:source1/process:process1");

        String result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getDefaultSource", "differentProcess");

        assertNull(result);
    }

    /**
     * Tests getDefaultSource with empty priority - should throw SourceNotPresentException
     */
    @Test(expected = SourceNotPresentException.class)
    public void testGetDefaultSource_WithEmptyPriorityString_ThrowsSourceNotPresentException() {
        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", "");

        ReflectionTestUtils.invokeMethod(packetReaderService, "getDefaultSource", "process1");
    }

    /**
     * Tests getDefaultSource with null priority - should throw SourceNotPresentException
     */
    @Test(expected = SourceNotPresentException.class)
    public void testGetDefaultSource_WithNullPriority_ThrowsSourceNotPresentException() {
        ReflectionTestUtils.setField(packetReaderService, "defaultPriority", null);

        ReflectionTestUtils.invokeMethod(packetReaderService, "getDefaultSource", "process1");
    }

    /**
     * Tests getSource when found - should return source
     */
    @Test
    public void testGetSource_WhenFound_ReturnsSource() throws IOException {
        JSONObject jsonObject = new JSONObject();
        LinkedHashMap<String, Object> fieldMap = new LinkedHashMap<>();
        List<String> providerList = Lists.newArrayList("process:process1,source:source1");
        fieldMap.put("provider", providerList);
        jsonObject.put("testField", fieldMap);

        String result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getSource", jsonObject, "process1", "testField");

        assertEquals("source1", result);
    }

    /**
     * Tests getSource with null field - should return source from provider
     */
    @Test
    public void testGetSource_WithNullField_ReturnsSourceFromProvider() throws IOException {
        JSONObject jsonObject = new JSONObject();
        List<String> providerList = Lists.newArrayList("process:process1,source:source1");
        jsonObject.put("provider", providerList);

        String result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getSource", jsonObject, "process1", null);

        assertEquals("source1", result);
    }

    /**
     * Tests getSource when process not found - should return null
     */
    @Test
    public void testGetSource_WhenProcessNotFound_ReturnsNull() throws IOException {
        JSONObject jsonObject = new JSONObject();
        LinkedHashMap<String, Object> fieldMap = new LinkedHashMap<>();
        List<String> providerList = Lists.newArrayList("process:differentProcess,source:source1");
        fieldMap.put("provider", providerList);
        jsonObject.put("testField", fieldMap);

        String result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getSource", jsonObject, "process1", "testField");

        assertNull(result);
    }

    /**
     * Tests getSource with non-ArrayList provider - should return null
     */
    @Test
    public void testGetSource_WithNonArrayListProvider_ReturnsNull() throws IOException {
        JSONObject jsonObject = new JSONObject();
        LinkedHashMap<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("provider", "notAnArrayList");
        jsonObject.put("testField", fieldMap);

        String result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getSource", jsonObject, "process1", "testField");

        assertNull(result);
    }

    /**
     * Tests getSource with null provider - should return null
     */
    @Test
    public void testGetSource_WithNullProvider_ReturnsNull() throws IOException {
        JSONObject jsonObject = new JSONObject();
        LinkedHashMap<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("provider", null);
        jsonObject.put("testField", fieldMap);

        String result = ReflectionTestUtils.invokeMethod(packetReaderService,
                "getSource", jsonObject, "process1", "testField");

        assertNull(result);
    }
}