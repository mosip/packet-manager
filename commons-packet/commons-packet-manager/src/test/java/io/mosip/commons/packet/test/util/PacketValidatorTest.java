package io.mosip.commons.packet.test.util;

import io.mosip.commons.packet.audit.AuditLogEntry;
import io.mosip.commons.packet.constants.PacketManagerConstants;
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.commons.packet.keeper.PacketKeeper;
import io.mosip.commons.packet.util.IdSchemaUtils;
import io.mosip.commons.packet.util.PacketValidator;
import io.mosip.kernel.core.idobjectvalidator.spi.IdObjectValidator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
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

    @Before
    public void setup() {
        // env.getProperty(...) returns null
        // → validateSchema() returns false
        when(env.getProperty(anyString())).thenReturn(null);
    }

    @Test
    public void validateReturnsFalseWhenSchemaValidationFails() throws Exception {
        // Arrange: prevent NPE
        when(idSchemaUtils.getIdschemaVersionFromMappingJson()).thenReturn("schemaVersion");
        when(reader.getField(any(), any(), any(), any(), anyBoolean())).thenReturn("1.0");

        // env property null -> schema validation returns false
        when(env.getProperty(anyString())).thenReturn(null);

        // Act
        boolean result = packetValidator.validate("123", "source", "process");

        // Assert
        assertFalse(result);

        verify(auditLogEntry)
                .addAudit(contains("failed"), any(), any(), any(), any(), any(), eq("123"));

        verify(packetKeeper, never()).getPacket(any());
    }

    @Test
    public void validateReturnsTrueWhenSchemaPassesAndFileValidationPasses() throws Exception {
        // ---- prevent earlier NPEs ----
        when(idSchemaUtils.getIdschemaVersionFromMappingJson()).thenReturn("schemaVersion");
        when(reader.getField(any(), any(), any(), any(), anyBoolean())).thenReturn("1.0");

        Map<String, String> fieldsMap = new HashMap<>();
        fieldsMap.put(PacketManagerConstants.IDSCHEMA_VERSION, "1.0");

        when(idSchemaUtils.getDefaultFields(anyDouble())).thenReturn(new ArrayList<>());
        when(reader.getFields(any(), any(), any(), any(), anyBoolean())).thenReturn(fieldsMap);

        when(env.getProperty(anyString())).thenReturn("field1,field2");
        when(idObjectValidator.validateIdObject(any(), any(), any())).thenReturn(true);

        // ---- spy only public method ----
        PacketValidator spyValidator = spy(packetValidator);
        doReturn(true)
                .when(spyValidator)
                .fileAndChecksumValidation(any(), any(), any());

        // ---- act ----
        boolean result = spyValidator.validate("123", "source", "process");

        // ---- assert ----
        assertTrue(result);
    }

    @Test
    public void validateReturnsFalseWhenFileValidationFails() throws Exception {

        // ---- Schema validation MUST pass ----
        when(idSchemaUtils.getIdschemaVersionFromMappingJson())
                .thenReturn(PacketManagerConstants.IDSCHEMA_VERSION);

        when(reader.getField(any(), any(), any(), any(), anyBoolean()))
                .thenReturn("1.0");

        Map<String, String> fieldsMap = new HashMap<>();
        fieldsMap.put(PacketManagerConstants.IDSCHEMA_VERSION, "1.0");

        when(idSchemaUtils.getDefaultFields(anyDouble()))
                .thenReturn(new ArrayList<>());

        when(reader.getFields(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(fieldsMap);

        when(env.getProperty(anyString()))
                .thenReturn("field1,field2");

        when(idObjectValidator.validateIdObject(any(), any(), any()))
                .thenReturn(true);

        // ---- Force file validation to fail ----
        PacketValidator spyValidator = spy(packetValidator);
        doReturn(false)
                .when(spyValidator)
                .fileAndChecksumValidation(any(), any(), any());

        // ---- Act ----
        boolean result = spyValidator.validate("123", "source", "process");

        // ---- Assert ----
        assertFalse(result);
    }

}
