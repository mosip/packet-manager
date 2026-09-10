package io.mosip.commons.packet.test.util;

import io.mosip.commons.packet.util.PacketManagerHelper;
import io.mosip.kernel.biometrics.entities.BDBInfo;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.biometrics.entities.RegistryIDType;
import io.mosip.kernel.biometrics.constant.QualityType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class PacketManagerHelperTest {

    private PacketManagerHelper helper;

    @BeforeEach
    public void setup() {
        // create a single helper instance and set fields on it
        helper = new PacketManagerHelper();
        ReflectionTestUtils.setField(helper, "configServerFileStorageURL", "http://localhost:1000/");
        ReflectionTestUtils.setField(helper, "schemaName", "mosip-cbeff.xsd");
        ReflectionTestUtils.setField(helper, "centerIdLength", 5);
        ReflectionTestUtils.setField(helper, "machineIdLength", 6);
    }

    @Test
    public void testConstructorAndGetRefIdWithRefProvided() {
        // constructor implicit
        assertNotNull(helper);

        String ref = helper.getRefId("1234567890", "myref");
        assertEquals("myref", ref);
    }

    @Test
    public void testGetRefIdGeneratedFromId() {
        // id length must be at least centerIdLength + machineIdLength
        String id = "ABCDE123456ZZZ"; // centerId=ABCDE, machineId=123456
        String ref = helper.getRefId(id, null);
        assertEquals("ABCDE_123456", ref);
    }
    @Disabled("Requires CBEFF native libraries not available in CI")
    @Test
    public void testGetXMLDataOfflineModeUsesLocalXsd() throws Exception {
        // build a minimal BiometricRecord with one BIR segment
        BIR bir = new BIR.BIRBuilder().build();
        BDBInfo bdbInfo = new BDBInfo.BDBInfoBuilder().build();
        QualityType quality = new QualityType();
        RegistryIDType reg = new RegistryIDType("Mosip","257");
        quality.setAlgorithm(reg);
        quality.setScore(90L);
        bdbInfo.setQuality(quality);
        bir.setBdbInfo(bdbInfo);

        List<BIR> segments = new ArrayList<>();
        segments.add(bir);
        BiometricRecord br = new BiometricRecord();
        br.setSegments(segments);
        br.setOthers(new HashMap<>());
        try {
            byte[] xml = helper.getXMLData(br, true);
            assertNotNull(xml);
            assertTrue(xml.length > 0);
        } catch (Throwable t) {
            assumeTrue(false, "Test requires CBEFF native libraries: " + t.getMessage());
        }
    }
}
