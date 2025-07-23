package io.mosip.commons.packet.test.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.exception.ZipParsingException;
import io.mosip.commons.packet.util.ZipUtils;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ZipUtilsTest {

    @Test
    public void testUnzip() throws IOException, ZipParsingException {
        // Create a dummy zip file
        byte[] zipData = new byte[] { 80, 75, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        Map<String, byte[]> unzippedFiles = ZipUtils.unzip(new ByteArrayInputStream(zipData));
        assertNotNull(unzippedFiles);
    }

    @Test
    public void testZip() throws IOException {
        byte[] zipData = ZipUtils.zip(null);
        assertNotNull(zipData);
        assertTrue(zipData.length > 0);
    }
}
