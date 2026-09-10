package io.mosip.commons.packet.test.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.commons.packet.audit.AuditLogEntry;
import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.commons.packet.keeper.PacketKeeper;
import io.mosip.commons.packet.util.IdSchemaUtils;
import io.mosip.commons.packet.util.PacketValidator;
import io.mosip.kernel.core.idobjectvalidator.spi.IdObjectValidator;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import io.mosip.commons.packet.dto.Packet;
import io.mosip.commons.packet.dto.packet.FieldValueArray;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PacketValidatorTest {

    @InjectMocks
    private PacketValidator packetValidator;

    @Mock
    private PacketReader reader;

    @Mock
    private Environment env;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private PacketKeeper packetKeeper;

    @Mock
    private IdObjectValidator idObjectValidator;

    @Mock
    private IdSchemaUtils idSchemaUtils;

    @Mock
    private AuditLogEntry auditLogEntry;

    private final String testId = "1234567890";
    private final String testSource = "REGISTRATION_CLIENT";
    private final String testProcess = "NEW";
    private final String packetNames = "id";

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(packetValidator, "packetNames", packetNames);
        ReflectionTestUtils.setField(packetValidator, "convertIdschemaToDouble", true);
    }

    @Test
    public void testValidateSchemaValidationFails() throws Exception {
        // Mock schema validation to fail
        when(idSchemaUtils.getIdschemaVersionFromMappingJson()).thenReturn("IDSchemaVersion");
        when(reader.getField(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("1.0");

        List<String> defaultFields = Arrays.asList("name", "dob", "IDSchemaVersion");
        when(idSchemaUtils.getDefaultFields(anyDouble())).thenReturn(defaultFields);

        Map<String, String> fieldsMap = new HashMap<>();
        fieldsMap.put("name", "John Doe");
        fieldsMap.put("dob", "1990-01-01");
        fieldsMap.put("IDSchemaVersion", "1.0");
        when(reader.getFields(anyString(), anyList(), anyString(), anyString(), anyBoolean()))
                .thenReturn(fieldsMap);

        when(env.getProperty(anyString())).thenReturn("name,dob");
        when(idSchemaUtils.getIdSchema(anyDouble())).thenReturn(new JSONObject().toString());
        when(idObjectValidator.validateIdObject(any(), any(), anyList())).thenReturn(false);

        // Execute
        boolean result = packetValidator.validate(testId, testSource, testProcess);

        // Verify
        assertFalse(result);
        verify(auditLogEntry, times(1)).addAudit(eq("Id object validation failed"),
                anyString(), anyString(), anyString(), isNull(), isNull(), eq(testId));
    }

    @Test
    public void testValidateIdObjectValidationException() throws Exception {
        // Mock to throw IdObjectValidationFailedException
        when(idSchemaUtils.getIdschemaVersionFromMappingJson()).thenReturn("IDSchemaVersion");
        when(reader.getField(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("1.0");

        List<String> defaultFields = Arrays.asList("name", "dob", "IDSchemaVersion");
        when(idSchemaUtils.getDefaultFields(anyDouble())).thenReturn(defaultFields);

        Map<String, String> fieldsMap = new HashMap<>();
        fieldsMap.put("name", "John Doe");
        fieldsMap.put("dob", "1990-01-01");
        fieldsMap.put("IDSchemaVersion", "1.0");
        when(reader.getFields(anyString(), anyList(), anyString(), anyString(), anyBoolean()))
                .thenReturn(fieldsMap);

        when(env.getProperty(anyString())).thenReturn("name,dob");
        when(idSchemaUtils.getIdSchema(anyDouble())).thenReturn(new JSONObject().toString());
        when(idObjectValidator.validateIdObject(any(), any(), anyList()))
                .thenThrow(new io.mosip.kernel.core.idobjectvalidator.exception.IdObjectValidationFailedException(
                        "validation", "errors"));

        // Execute
        boolean result = packetValidator.validate(testId, testSource, testProcess);

        // Verify
        assertFalse(result);
        verify(auditLogEntry, times(1)).addAudit(eq("Id object validation failed"),
                anyString(), anyString(), anyString(), isNull(), isNull(), eq(testId));
    }

    @Test
    public void testValidateIOException() throws Exception {
        // Mock to throw IOException - use Answer for checked exceptions
        when(idSchemaUtils.getIdschemaVersionFromMappingJson()).thenReturn("IDSchemaVersion");

        // Use Answer to throw checked exception
        when(reader.getField(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenAnswer(invocation -> {
                    throw new IOException("Test IO Exception");
                });

        // Execute & Verify
        Exception exception = assertThrows(Exception.class, () ->
                packetValidator.validate(testId, testSource, testProcess));

        // Check it's an IOException or wrapped IOException
        assertTrue(exception instanceof IOException ||
                (exception.getCause() != null && exception.getCause() instanceof IOException));
    }

    @Test
    public void testValidateJsonProcessingException() throws Exception {
        // Mock to throw JsonProcessingException
        when(idSchemaUtils.getIdschemaVersionFromMappingJson()).thenReturn("IDSchemaVersion");
        when(reader.getField(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("1.0");

        List<String> defaultFields = Arrays.asList("name", "dob", "IDSchemaVersion");
        when(idSchemaUtils.getDefaultFields(anyDouble())).thenReturn(defaultFields);

        // Use Answer for JsonProcessingException
        when(reader.getFields(anyString(), anyList(), anyString(), anyString(), anyBoolean()))
                .thenAnswer(invocation -> {
                    throw new JsonProcessingException("JSON error");
                });

        // Execute & Verify
        Exception exception = assertThrows(Exception.class, () ->
                packetValidator.validate(testId, testSource, testProcess));

        assertTrue(exception instanceof JsonProcessingException ||
                (exception.getCause() != null && exception.getCause() instanceof JsonProcessingException));
    }

    @Test
    public void testGetPacketInfo() {
        // Execute using reflection
        PacketInfo result = (PacketInfo) ReflectionTestUtils.invokeMethod(
                packetValidator, "getPacketInfo", testId, "id", testSource, testProcess);

        // Verify
        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals("id", result.getPacketName());
        assertEquals(testSource, result.getSource());
        assertEquals(testProcess, result.getProcess());
    }

    // Remove or fix the problematic test - Option 1: Remove it
    // @Test
    // public void testLoadDemographicIdentity_SimpleValues() throws Exception {
    //     // This test is causing issues with unnecessary stubbing
    // }

    // Option 2: Fix it by properly testing the method
    @Test
    public void testLoadDemographicIdentityWithSimpleStringValues() throws Exception {
        // Setup test data with simple string values (non-JSON)
        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("simpleField", "simpleValue");
        fieldMap.put("numberField", "123");
        fieldMap.put("booleanField", "true");

        // These are simple strings, not JSON, so mapper.readValue won't be called
        // No need to mock mapper.readValue since it won't be invoked

        // Execute using reflection
        LinkedHashMap result = (LinkedHashMap) ReflectionTestUtils.invokeMethod(
                packetValidator, "loadDemographicIdentity", fieldMap);

        // Verify
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("simpleValue", result.get("simpleField"));
        assertEquals("123", result.get("numberField"));
        assertEquals("true", result.get("booleanField"));
    }

    @Test
    public void testLoadDemographicIdentityEmptyMap() throws Exception {
        Map<String, Object> fieldMap = new HashMap<>();

        // Execute using reflection
        LinkedHashMap result = (LinkedHashMap) ReflectionTestUtils.invokeMethod(
                packetValidator, "loadDemographicIdentity", fieldMap);

        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testLoadDemographicIdentityWithJsonObjectAndArray() throws Exception {
        Map<String, Object> fieldMap = new HashMap<>();
        String objJson = "{\"a\":\"b\"}";
        String arrJson = "[{\"x\":\"y\"}, \"plain\"]";
        fieldMap.put("objField", objJson);
        fieldMap.put("arrField", arrJson);

        // Stub mapper to convert JSON strings to maps when called inside loadDemographicIdentity
        HashMap<String, Object> objMap = new HashMap<>();
        objMap.put("a", "b");
        HashMap<String, Object> arrElemMap = new HashMap<>();
        arrElemMap.put("x", "y");
        // Specific stubs to avoid generic thenAnswer pitfalls
        doReturn(objMap).when(mapper).readValue(eq(objJson), eq(HashMap.class));
        doReturn(arrElemMap).when(mapper).readValue(contains("\"x\""), eq(HashMap.class));

        LinkedHashMap result = (LinkedHashMap) ReflectionTestUtils.invokeMethod(packetValidator, "loadDemographicIdentity", fieldMap);

        assertNotNull(result);
        assertTrue(result.containsKey("objField"));
        assertTrue(result.get("objField") instanceof Map);
        assertTrue(result.containsKey("arrField"));
        assertTrue(result.get("arrField") instanceof List);
        List arr = (List) result.get("arrField");
        assertEquals(2, arr.size());
        // first element should be a map parsed from JSON
        assertTrue(arr.get(0) instanceof Map);
        // second element should be plain string "plain"
        assertEquals("plain", arr.get(1));
    }

    @Test
    public void testGenerateHashHandlesIOExceptionFromStream() throws Exception {
        // Create checksumMap with an InputStream that throws IOException on read
        Map<String, InputStream> checksumMap = new HashMap<>();
        String filename = "badfile";
        InputStream badStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("forced");
            }
        };
        checksumMap.put(filename, badStream);

        FieldValueArray fva = new FieldValueArray();
        fva.setValue(Arrays.asList(filename));
        List<FieldValueArray> hashSequence = Arrays.asList(fva);

        // Should not throw despite the IO error inside
        byte[] hash = (byte[]) ReflectionTestUtils.invokeMethod(packetValidator, "generateHash", hashSequence, checksumMap);
        assertNotNull(hash);
        assertTrue(hash.length > 0);
    }

    @Test
    public void testChecksumValidationWhenHashesMatch() throws Exception {
        // Prepare files and contents
        String file1 = "f1";
        byte[] content1 = "data1".getBytes();

        // Prepare FieldValueArray that references file1
        FieldValueArray fva = new FieldValueArray();
        fva.setValue(Arrays.asList(file1));
        List<FieldValueArray> hashSeq = Arrays.asList(fva);

        // Compute dataHash using fresh stream
        Map<String, InputStream> checksumMapForDataHash = new HashMap<>();
        checksumMapForDataHash.put(file1, new ByteArrayInputStream(content1));
        byte[] dataHash = (byte[]) ReflectionTestUtils.invokeMethod(packetValidator, "generateHash", hashSeq, checksumMapForDataHash);
        // Compute opsHash using fresh stream
        Map<String, InputStream> checksumMapForOpsHash = new HashMap<>();
        checksumMapForOpsHash.put(file1, new ByteArrayInputStream(content1));
        byte[] opsHash = (byte[]) ReflectionTestUtils.invokeMethod(packetValidator, "generateHash", hashSeq, checksumMapForOpsHash);

        // Create zip with file and PACKET_DATA_HASH and PACKET_OPERATIONS_HASH
        Map<String, byte[]> entries = new HashMap<>();
        entries.put(file1 + ".dat", content1);
        entries.put("PACKET_DATA_HASH", dataHash);
        entries.put("PACKET_OPERATIONS_HASH", opsHash);
        byte[] zip = createZipBytes(entries);

        Packet packet = new Packet();
        packet.setPacket(zip);

        // Prepare mapper to return FieldValueArray when reading
        doReturn(fva).when(mapper).readValue(anyString(), eq(FieldValueArray.class));

        // Prepare hashseq lists as the method expects raw objects (it will call mapper.readValue on each)
        List hashseq1List = Arrays.asList(new HashMap<String, Object>() {{ put("value", Arrays.asList(file1)); }});
        List hashseq2List = Arrays.asList(new HashMap<String, Object>() {{ put("value", Arrays.asList(file1)); }});

        // Create a fresh checksumMap for validation (streams must be unread)
        Map<String, byte[]> rawContent = Map.of(file1, content1);
        Map<String, InputStream> checksumMapForValidation = new HashMap<>() {
            @Override
            public InputStream get(Object key) {
                return new ByteArrayInputStream(rawContent.get(key));
            }
        };

        // Now call checksumValidation
        boolean result = (boolean) ReflectionTestUtils.invokeMethod(packetValidator, "checksumValidation", hashseq1List, hashseq2List, checksumMapForValidation, packet);
        assertTrue(result);
    }

    @Test
    public void testChecksumValidationDataHashMismatchReturnsFalse() throws Exception {
        String file1 = "f1";
        byte[] content1 = "dataX".getBytes();

        FieldValueArray fva = new FieldValueArray();
        fva.setValue(Arrays.asList(file1));
        List<FieldValueArray> hashSeq = Arrays.asList(fva);

        Map<String, InputStream> checksumMap = new HashMap<>();
        checksumMap.put(file1, new ByteArrayInputStream(content1));

        byte[] dataHash = (byte[]) ReflectionTestUtils.invokeMethod(packetValidator, "generateHash", hashSeq, checksumMap);
        // tamper dataHashByte so it won't match
        byte[] dataHashByte = "different".getBytes();
        byte[] opsHash = dataHash; // ops match

        Map<String, byte[]> entries = new HashMap<>();
        entries.put(file1 + ".dat", content1);
        entries.put("PACKET_DATA_HASH", dataHashByte);
        entries.put("PACKET_OPERATIONS_HASH", opsHash);

        Packet packet = new Packet();
        packet.setPacket(createZipBytes(entries));

        doReturn(fva).when(mapper).readValue(anyString(), eq(FieldValueArray.class));

        List hashseq1List = Arrays.asList(new HashMap<String, Object>() {{ put("value", Arrays.asList(file1)); }});
        List hashseq2List = Arrays.asList(new HashMap<String, Object>() {{ put("value", Arrays.asList(file1)); }});

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(packetValidator, "checksumValidation", hashseq1List, hashseq2List, checksumMap, packet);
        assertFalse(result);
    }

    @Test
    public void testChecksumValidationOperationsHashMismatchReturnsFalse() throws Exception {
        String file1 = "f1";
        byte[] content1 = "dataY".getBytes();

        FieldValueArray fva = new FieldValueArray();
        fva.setValue(Arrays.asList(file1));
        List<FieldValueArray> hashSeq = Arrays.asList(fva);

        Map<String, InputStream> checksumMap = new HashMap<>();
        checksumMap.put(file1, new ByteArrayInputStream(content1));

        byte[] dataHash = (byte[]) ReflectionTestUtils.invokeMethod(packetValidator, "generateHash", hashSeq, checksumMap);
        byte[] opsHashByte = "ops-diff".getBytes();

        Map<String, byte[]> entries = new HashMap<>();
        entries.put(file1 + ".dat", content1);
        entries.put("PACKET_DATA_HASH", dataHash);
        entries.put("PACKET_OPERATIONS_HASH", opsHashByte);

        Packet packet = new Packet();
        packet.setPacket(createZipBytes(entries));

        doReturn(fva).when(mapper).readValue(anyString(), eq(FieldValueArray.class));

        List hashseq1List = Arrays.asList(new HashMap<String, Object>() {{ put("value", Arrays.asList(file1)); }});
        List hashseq2List = Arrays.asList(new HashMap<String, Object>() {{ put("value", Arrays.asList(file1)); }});

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(packetValidator, "checksumValidation", hashseq1List, hashseq2List, checksumMap, packet);
        assertFalse(result);
    }

    @Test
    public void testValidateFileAndChecksumValidationFalseReturnsFalse() throws Exception {
        // Make validateSchema succeed
        when(idSchemaUtils.getIdschemaVersionFromMappingJson()).thenReturn("IDSchemaVersion");
        when(reader.getField(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn("1.0");
        when(idSchemaUtils.getDefaultFields(anyDouble())).thenReturn(Arrays.asList("name","IDSchemaVersion"));
        Map<String,String> fieldsMap = new HashMap<>(); fieldsMap.put("IDSchemaVersion","1.0"); fieldsMap.put("name","n");
        when(reader.getFields(anyString(), anyList(), anyString(), anyString(), anyBoolean())).thenReturn(fieldsMap);
        when(env.getProperty(anyString())).thenReturn("name");
        when(idSchemaUtils.getIdSchema(anyDouble())).thenReturn(new JSONObject().toString());
        when(idObjectValidator.validateIdObject(any(), any(), anyList())).thenReturn(true);

        PacketValidator spy = spy(packetValidator);
        doReturn(false).when(spy).fileAndChecksumValidation(anyString(), anyString(), anyString());

        boolean result = spy.validate(testId, testSource, testProcess);
        assertFalse(result);
    }

    @Test
    public void testValidateFilesBothSequencesPresent() throws Exception {
        // Create zip with two files
        String f1 = "a";
        String f2 = "b";
        Map<String, byte[]> entries = new HashMap<>();
        entries.put(f1 + ".dat", "A".getBytes());
        entries.put(f2 + ".dat", "B".getBytes());
        byte[] zip = createZipBytes(entries);

        Packet packet = new Packet();
        packet.setPacket(zip);

        // mapper should convert objects to FieldValueArray
        FieldValueArray fva1 = new FieldValueArray(); fva1.setValue(Arrays.asList(f1));
        FieldValueArray fva2 = new FieldValueArray(); fva2.setValue(Arrays.asList(f2));

        Map<String, Object> mm1 = new HashMap<>(); mm1.put("value", Arrays.asList(f1));
        List hashseq1 = Arrays.asList(mm1);
        Map<String, Object> mm2 = new HashMap<>(); mm2.put("value", Arrays.asList(f2));
        List hashseq2 = Arrays.asList(mm2);

        // Use exact JSON serialization strings for stubbing mapper.readValue
        String json1 = io.mosip.kernel.core.util.JsonUtils.javaObjectToJsonString(mm1);
        String json2 = io.mosip.kernel.core.util.JsonUtils.javaObjectToJsonString(mm2);
        doReturn(fva1).when(mapper).readValue(eq(json1), eq(FieldValueArray.class));
        doReturn(fva2).when(mapper).readValue(eq(json2), eq(FieldValueArray.class));

        Map<String, InputStream> checksumMap = new HashMap<>();

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(packetValidator, "validateFiles", hashseq1, hashseq2, checksumMap, packet);
        assertTrue(result);
        assertTrue(checksumMap.containsKey(f1));
        assertTrue(checksumMap.containsKey(f2));
    }

    @Test
    public void testGetMetaInfoJsonNoMetaInfoReturnsEmpty() throws Exception {
        // Create zip without PACKET_META_INFO
        byte[] zip = createZipBytes(new HashMap<>());
        Packet packet = new Packet(); packet.setPacket(zip);

        Map<String,String> finalMap = (Map<String,String>) ReflectionTestUtils.invokeMethod(packetValidator, "getMetaInfoJson", packet);
        assertNotNull(finalMap);
        assertTrue(finalMap.isEmpty());
    }

    @Test
    public void testValidateSuccessFlowWithSpy() throws Exception {
        // Make validateSchema succeed by stubbing dependencies
        when(idSchemaUtils.getIdschemaVersionFromMappingJson()).thenReturn("IDSchemaVersion");
        when(reader.getField(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn("1.0");
        when(idSchemaUtils.getDefaultFields(anyDouble())).thenReturn(Arrays.asList("name","IDSchemaVersion"));
        Map<String,String> fieldsMap = new HashMap<>(); fieldsMap.put("IDSchemaVersion","1.0"); fieldsMap.put("name","n");
        when(reader.getFields(anyString(), anyList(), anyString(), anyString(), anyBoolean())).thenReturn(fieldsMap);
        when(env.getProperty(anyString())).thenReturn("name");
        when(idSchemaUtils.getIdSchema(anyDouble())).thenReturn(new JSONObject().toString());
        when(idObjectValidator.validateIdObject(any(), any(), anyList())).thenReturn(true);

        // Spy packetValidator and stub fileAndChecksumValidation to true
        PacketValidator spy = spy(packetValidator);
        doReturn(true).when(spy).fileAndChecksumValidation(anyString(), anyString(), anyString());

        boolean result = spy.validate(testId, testSource, testProcess);
        assertTrue(result);
    }

    @Test
    public void testGetMetaInfoJsonWithNullValue() throws Exception {
        // Create PACKET_META_INFO where a key has null value
        String metaContent = "{\"identity\":{\"maybeNull\":null}}";
        byte[] zip = createZipBytes(new HashMap<String, byte[]>() {{ put("PACKET_META_INFO", metaContent.getBytes()); }});

        Packet packet = new Packet();
        packet.setPacket(zip);

        // Stub mapper to return LinkedHashMap where identity has key maybeNull -> null
        LinkedHashMap<String,Object> currentIdMap = new LinkedHashMap<>();
        currentIdMap.put("maybeNull", null);
        LinkedHashMap<String,Object> root = new LinkedHashMap<>();
        root.put("identity", currentIdMap);
        doReturn(root).when(mapper).readValue(anyString(), eq(LinkedHashMap.class));

        Map<String,String> finalMap = (Map<String,String>) ReflectionTestUtils.invokeMethod(packetValidator, "getMetaInfoJson", packet);
        assertNotNull(finalMap);
        assertTrue(finalMap.containsKey("maybeNull"));
        assertNull(finalMap.get("maybeNull"));
    }

    @Test
    public void testFileAndChecksumValidationSuccessIntegration() throws Exception {
        // prepare file and its bytes
        String file1 = "datafile";
        byte[] content = "file-content".getBytes();

        // Prepare FieldValueArray for sequence
        FieldValueArray fva = new FieldValueArray();
        fva.setValue(Arrays.asList(file1));
        List<FieldValueArray> hashSeq = Arrays.asList(fva);

        // Prepare a checksumMap to compute the hashes (simulate file bytes)
        Map<String, InputStream> checksumMapForHash = new HashMap<>();
        checksumMapForHash.put(file1, new ByteArrayInputStream(content));

        // Compute hashes using private generateHash
        byte[] dataHash = (byte[]) ReflectionTestUtils.invokeMethod(packetValidator, "generateHash", hashSeq, checksumMapForHash);
        byte[] opsHash = (byte[]) ReflectionTestUtils.invokeMethod(packetValidator, "generateHash", hashSeq, checksumMapForHash);

        // Create zip containing the actual file and the two hash entries
        Map<String, byte[]> entries = new HashMap<>();
        entries.put(file1 + ".bin", content);
        entries.put("PACKET_DATA_HASH", dataHash);
        entries.put("PACKET_OPERATIONS_HASH", opsHash);
        entries.put("PACKET_META_INFO", "meta".getBytes()); // placeholder, mapper is stubbed
        byte[] zip = createZipBytes(entries);

        Packet packet = new Packet();
        packet.setPacket(zip);

        // packetKeeper should return our packet
        when(packetKeeper.getPacket(any(PacketInfo.class))).thenReturn(packet);

        // Prepare mapper behavior: getMetaInfoJson will call mapper.readValue for LinkedHashMap
        // currentIdMap should contain hashSequence1 and hashSequence2 as objects (they will be serialized by JsonUtils inside getMetaInfoJson)
        LinkedHashMap<String, Object> currentIdMap = new LinkedHashMap<>();
        // use Java objects that JsonUtils will serialize; mapper.readValue when called for ArrayList should return lists of maps
        Map<String, Object> seqElem = new HashMap<>();
        seqElem.put("label", "l");
        seqElem.put("value", Arrays.asList(file1));
        List seqList = Arrays.asList(seqElem);
        currentIdMap.put("hashSequence1", seqList);
        currentIdMap.put("hashSequence2", seqList);
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("identity", currentIdMap);

        doReturn(root).when(mapper).readValue(anyString(), eq(LinkedHashMap.class));
        // When later getMetaInfoJson produces JSON strings, the code will call mapper.readValue(jsonString, ArrayList.class)
        doReturn(new ArrayList<>(seqList)).when(mapper).readValue(anyString(), eq(ArrayList.class));
        // And when validateFiles tries to parse each array element into FieldValueArray
        doReturn(fva).when(mapper).readValue(anyString(), eq(FieldValueArray.class));

        // Now call public method
        boolean result = packetValidator.fileAndChecksumValidation(testId, testSource, testProcess);
        assertTrue(result);
    }

    @Test
    public void testValidateEndToEndSuccess() throws Exception {
        // Setup schema validation mocks
        when(idSchemaUtils.getIdschemaVersionFromMappingJson()).thenReturn("IDSchemaVersion");
        when(reader.getField(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn("1.0");
        when(idSchemaUtils.getDefaultFields(anyDouble())).thenReturn(Arrays.asList("name","IDSCHEMA_VERSION","IDSchemaVersion"));
        Map<String,String> fieldsMap = new HashMap<>(); fieldsMap.put("IDSCHEMA_VERSION","1.0"); fieldsMap.put("IDSchemaVersion","1.0"); fieldsMap.put("name","n");
        when(reader.getFields(anyString(), anyList(), anyString(), anyString(), anyBoolean())).thenReturn(fieldsMap);
        when(env.getProperty(anyString())).thenReturn("name");
        when(idSchemaUtils.getIdSchema(anyDouble())).thenReturn(new JSONObject().toString());
        when(idObjectValidator.validateIdObject(any(), any(), anyList())).thenReturn(true);

        // Prepare a valid packet similar to fileAndChecksumValidation_success_integration
        String file1 = "fdata";
        byte[] content = "content-2".getBytes();
        FieldValueArray fva = new FieldValueArray(); fva.setValue(Arrays.asList(file1));
        List<FieldValueArray> hashSeq = Arrays.asList(fva);
        Map<String, InputStream> checksumMapForHash = new HashMap<>(); checksumMapForHash.put(file1, new ByteArrayInputStream(content));
        byte[] dataHash = (byte[]) ReflectionTestUtils.invokeMethod(packetValidator, "generateHash", hashSeq, checksumMapForHash);
        byte[] opsHash = (byte[]) ReflectionTestUtils.invokeMethod(packetValidator, "generateHash", hashSeq, checksumMapForHash);

        Map<String, byte[]> entries = new HashMap<>();
        entries.put(file1 + ".dat", content);
        entries.put("PACKET_DATA_HASH", dataHash);
        entries.put("PACKET_OPERATIONS_HASH", opsHash);
        entries.put("PACKET_META_INFO", "meta".getBytes());
        byte[] zip = createZipBytes(entries);

        Packet packet = new Packet(); packet.setPacket(zip);
        when(packetKeeper.getPacket(any(PacketInfo.class))).thenReturn(packet);

        // mapper stubs for meta info
        Map<String, Object> seqElem = new HashMap<>(); seqElem.put("label", "l"); seqElem.put("value", Arrays.asList(file1));
        List seqList = Arrays.asList(seqElem);
        LinkedHashMap<String, Object> currentIdMap = new LinkedHashMap<>(); currentIdMap.put("hashSequence1", seqList); currentIdMap.put("hashSequence2", seqList);
        LinkedHashMap<String, Object> root = new LinkedHashMap<>(); root.put("identity", currentIdMap);
        doReturn(root).when(mapper).readValue(anyString(), eq(LinkedHashMap.class));
        doReturn(new ArrayList<>(seqList)).when(mapper).readValue(anyString(), eq(ArrayList.class));
        doReturn(fva).when(mapper).readValue(anyString(), eq(FieldValueArray.class));

        boolean result = packetValidator.validate(testId, testSource, testProcess);
        assertTrue(result);
    }

    // Helper to create an in-memory zip containing entries
    private byte[] createZipBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }

        return baos.toByteArray();
    }
}
