package io.mosip.commons.packet.test.util;

import io.mosip.commons.packet.util.IdObjectsSchemaValidationOperationMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IdObjectsSchemaValidationOperationMapperTest {

    @Test
    public void testConstructor() {
        // cover default constructor
        IdObjectsSchemaValidationOperationMapper mapper = new IdObjectsSchemaValidationOperationMapper();
        assertNotNull(mapper);
    }

    @Test
    public void testGetOperationNew() {
        String op = IdObjectsSchemaValidationOperationMapper.getOperation("NEW");
        assertEquals("new-registration", op);

        // case-insensitive
        op = IdObjectsSchemaValidationOperationMapper.getOperation("new");
        assertEquals("new-registration", op);
    }

    @Test
    public void testGetOperationLost() {
        String op = IdObjectsSchemaValidationOperationMapper.getOperation("LOST");
        assertEquals("lost", op);
    }

    @Test
    public void testGetOperationUpdateVariants() {
        String op = IdObjectsSchemaValidationOperationMapper.getOperation("UPDATE");
        assertEquals("other", op);

        op = IdObjectsSchemaValidationOperationMapper.getOperation("RES_UPDATE");
        assertEquals("other", op);

        op = IdObjectsSchemaValidationOperationMapper.getOperation("ACTIVATED");
        assertEquals("other", op);

        op = IdObjectsSchemaValidationOperationMapper.getOperation("DEACTIVATED");
        assertEquals("other", op);
    }

    @Test
    public void testGetOperationDefaultWithIteration() {
        // should strip numeric suffix after '-'
        String op = IdObjectsSchemaValidationOperationMapper.getOperation("SOME-1");
        assertEquals("some", op);

        op = IdObjectsSchemaValidationOperationMapper.getOperation("PROC-ABC-123");
        assertEquals("proc-abc", op);
    }

    @Test
    public void testGetOperationDefaultWithoutIteration() {
        String op = IdObjectsSchemaValidationOperationMapper.getOperation("CUSTOMOP");
        assertEquals("customop", op);

        op = IdObjectsSchemaValidationOperationMapper.getOperation("Proc-XYZ");
        assertEquals("proc-xyz", op);
    }
}

