package io.mosip.commons.packet.test.util;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.mosip.commons.packet.constants.PacketManagerConstants;
import io.mosip.commons.packet.util.PacketManagerHelper;

@RunWith(SpringRunner.class)
@SpringBootTest
public class PacketManagerHelperTest {

    @Autowired
    private PacketManagerHelper packetManagerHelper;

    @MockBean
    private Environment environment;

    @Mock
    private ObjectMapper objectMapper;

    @Before
    public void setup() {
        when(environment.getProperty(PacketManagerConstants.AUDIT_EVENT_NAME)).thenReturn("test-event");
        when(environment.getProperty(PacketManagerConstants.AUDIT_EVENT_TYPE)).thenReturn("test-type");
        when(environment.getProperty(PacketManagerConstants.AUDIT_APPLICATION_ID)).thenReturn("test-app-id");
        when(environment.getProperty(PacketManagerConstants.AUDIT_APPLICATION_NAME)).thenReturn("test-app-name");
        when(environment.getProperty(PacketManagerConstants.AUDIT_HOST_NAME)).thenReturn("test-host");
        when(environment.getProperty(PacketManagerConstants.AUDIT_HOST_IP)).thenReturn("127.0.0.1");
    }

    @Test
    public void testGetPacketInfo() throws IOException {
        Map<String, InputStream> resources = new HashMap<>();
        String json = "{\"key\":\"value\"}";
        resources.put("some-path", new ByteArrayInputStream(json.getBytes()));
        ObjectNode objectNode = packetManagerHelper.getPacketInfo(resources);
        assertNotNull(objectNode);
    }

    @Test
    public void testBuildAuditRequest() {
        ObjectNode objectNode = packetManagerHelper.buildAuditRequest(null, "test-id", "test-name");
        assertNotNull(objectNode);
    }
}
