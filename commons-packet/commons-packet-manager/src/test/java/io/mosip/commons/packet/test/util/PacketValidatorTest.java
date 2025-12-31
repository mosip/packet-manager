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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public void testValidate_SchemaValidationFails() throws Exception {
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
    public void testValidate_IdObjectValidationException() throws Exception {
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
    public void testValidate_IOException() throws Exception {
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
    public void testValidate_JsonProcessingException() throws Exception {
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
    public void testLoadDemographicIdentity_WithSimpleStringValues() throws Exception {
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
    public void testLoadDemographicIdentity_EmptyMap() throws Exception {
        Map<String, Object> fieldMap = new HashMap<>();

        // Execute using reflection
        LinkedHashMap result = (LinkedHashMap) ReflectionTestUtils.invokeMethod(
                packetValidator, "loadDemographicIdentity", fieldMap);

        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testValidateSchema_Success() throws Exception {
        // Mock dependencies
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
        when(idObjectValidator.validateIdObject(any(), any(), anyList())).thenReturn(true);

        // Execute private method
        boolean result = (boolean) ReflectionTestUtils.invokeMethod(
                packetValidator, "validateSchema", testId, testSource, testProcess);

        assertTrue(result);
    }

    @Test
    public void testValidateSchema_FieldsNull() throws Exception {
        // Mock dependencies
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

        when(env.getProperty(anyString())).thenReturn(null); // fields is null

        // Execute private method
        boolean result = (boolean) ReflectionTestUtils.invokeMethod(
                packetValidator, "validateSchema", testId, testSource, testProcess);

        assertFalse(result); // Should return false when fields is null
    }

    @Test
    public void testValidate_ReturnsFalseWhenFieldsNull() throws Exception {
        // Setup minimal mocks to test the early return path
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

        // Mock idSchema.getIdSchema but it won't be called because fields is null
        lenient().when(idSchemaUtils.getIdSchema(anyDouble())).thenReturn(new JSONObject().toString());
        // Mock idObjectValidator but it won't be called
        lenient().when(idObjectValidator.validateIdObject(any(), any(), anyList())).thenReturn(true);

        // Return null for fields property
        when(env.getProperty(anyString())).thenReturn(null);

        // Execute
        boolean result = packetValidator.validate(testId, testSource, testProcess);

        // Verify - should return false early
        assertFalse(result);
    }
}