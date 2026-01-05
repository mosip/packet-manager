package io.mosip.commons.packet.test.util;

import io.mosip.commons.packet.audit.AuditLogEntry;
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
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private PacketValidator spyValidator;

    @Before
    public void setup() {
        spyValidator = Mockito.spy(packetValidator);
    }

    @Test
    public void validateReturnsFalseWhenSchemaValidationFails() throws Exception {
        // Arrange
        doReturn(false)
                .when(spyValidator)
                .validateSchema(anyString(), anyString(), anyString());

        // Act
        boolean result = spyValidator.validate("123", "source", "process");

        // Assert
        assertFalse(result);
        verify(auditLogEntry)
                .addAudit(contains("failed"), any(), any(), any(), any(), any(), eq("123"));
        verify(spyValidator, never())
                .fileAndChecksumValidation(any(), any(), any());
    }

    @Test
    public void validateReturnsTrueWhenSchemaAndFileValidationPass() throws Exception {
        // Arrange
        doReturn(true)
                .when(spyValidator)
                .validateSchema(anyString(), anyString(), anyString());

        doReturn(true)
                .when(spyValidator)
                .fileAndChecksumValidation(anyString(), anyString(), anyString());

        // Act
        boolean result = spyValidator.validate("123", "source", "process");

        // Assert
        assertTrue(result);
        verify(auditLogEntry)
                .addAudit(contains("successful"), any(), any(), any(), any(), any(), eq("123"));
    }

    @Test
    public void validateReturnsFalseWhenFileValidationFails() throws Exception {
        // Arrange
        doReturn(true)
                .when(spyValidator)
                .validateSchema(anyString(), anyString(), anyString());

        doReturn(false)
                .when(spyValidator)
                .fileAndChecksumValidation(anyString(), anyString(), anyString());

        // Act
        boolean result = spyValidator.validate("123", "source", "process");

        // Assert
        assertFalse(result);
        verify(auditLogEntry)
                .addAudit(contains("successful"), any(), any(), any(), any(), any(), eq("123"));
    }
}
