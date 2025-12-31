package io.mosip.commons.packet.test.util;

import io.mosip.commons.packet.util.PacketHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PacketHelperTest {

    @BeforeEach
    public void resetStatics() {
        // reset static caches and configurations before each test
        ReflectionTestUtils.setField(PacketHelper.class, "readerProvider", null);
        ReflectionTestUtils.setField(PacketHelper.class, "writerProvider", null);
        ReflectionTestUtils.setField(PacketHelper.class, "readerConfiguration", null);
        ReflectionTestUtils.setField(PacketHelper.class, "writerConfiguration", null);
    }

    @Test
    public void testGetReaderProviderAndGetWriterProviderParsesConfig() {
        Map<String, String> readerConfig = new HashMap<>();
        readerConfig.put("r1", "source:REG_CLIENT,process:NEW,classname:com.test.ReaderImpl");
        readerConfig.put("r2", "source:REG_CLIENT,process:UPDATE,classname:com.test.ReaderImpl2");

        Set<String> readers = PacketHelper.getReaderProvider(readerConfig);
        assertNotNull(readers);
        assertTrue(readers.contains("com.test.ReaderImpl"));
        assertTrue(readers.contains("com.test.ReaderImpl2"));

        Map<String, String> writerConfig = new HashMap<>();
        writerConfig.put("w1", "source:REG_CLIENT,process:NEW,classname:com.test.WriterImpl");

        Set<String> writers = PacketHelper.getWriterProvider(writerConfig);
        assertNotNull(writers);
        assertTrue(writers.contains("com.test.WriterImpl"));
    }

    @Test
    public void testIsSourceAndProcessPresentReaderTrueAndFalse() {
        Map<String, String> readerConfig = new HashMap<>();
        readerConfig.put("r1", "source:REG_CLIENT,process:NEW,classname:com.test.ReaderImpl");
        PacketHelper.getReaderProvider(readerConfig); // sets readerConfiguration and readerProvider

        // providerName contains classname
        boolean present = PacketHelper.isSourceAndProcessPresent("com.test.ReaderImpl-something", "REG_CLIENT", "NEW", PacketHelper.Provider.READER);
        assertTrue(present);

        // different process should return false
        boolean notPresent = PacketHelper.isSourceAndProcessPresent("com.test.ReaderImpl-something", "REG_CLIENT", "UPDATE", PacketHelper.Provider.READER);
        assertFalse(notPresent);
    }

    @Test
    public void testIsSourceAndProcessPresentWithIterationInProcess() {
        Map<String, String> readerConfig = new HashMap<>();
        readerConfig.put("r1", "source:REG_CLIENT,process:NEW,classname:com.test.ReaderImpl");
        PacketHelper.getReaderProvider(readerConfig);

        // provide process with iteration suffix - it should be stripped internally
        boolean present = PacketHelper.isSourceAndProcessPresent("com.test.ReaderImpl", "REG_CLIENT", "NEW-1", PacketHelper.Provider.READER);
        assertTrue(present);
    }

    @Test
    public void testIsSourceAndProcessPresentNoConfigReturnsFalse() {
        // ensure readerConfiguration is null
        ReflectionTestUtils.setField(PacketHelper.class, "readerConfiguration", null);
        ReflectionTestUtils.setField(PacketHelper.class, "readerProvider", null);

        // should not throw; should return false as no provider matches
        boolean result = PacketHelper.isSourceAndProcessPresent("any", "s", "p", PacketHelper.Provider.READER);
        assertFalse(result);
    }

    @Test
    public void testGetProcessWithoutIterationVariousCases() {
        // null should return null
        assertNull(PacketHelper.getProcessWithoutIteration(null));

        // empty string returns empty
        assertEquals("", PacketHelper.getProcessWithoutIteration(""));

        // no iteration suffix
        assertEquals("NEW", PacketHelper.getProcessWithoutIteration("NEW"));

        // numeric suffix removed
        assertEquals("PROC", PacketHelper.getProcessWithoutIteration("PROC-1"));

        // last segment numeric removed, earlier segments retained
        assertEquals("PROC-ABC", PacketHelper.getProcessWithoutIteration("PROC-ABC-123"));

        // last segment non-numeric remains
        assertEquals("PROC-ABC-XYZ", PacketHelper.getProcessWithoutIteration("PROC-ABC-XYZ"));
    }
}
