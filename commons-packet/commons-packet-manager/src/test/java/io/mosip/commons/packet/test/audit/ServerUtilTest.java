package io.mosip.commons.packet.test.audit;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.audit.ServerUtil;
import io.mosip.commons.packet.constants.PacketManagerConstants;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ServerUtilTest {

    @Mock
    private Environment environment;

    @Before
    public void setup() {
        when(environment.getProperty(PacketManagerConstants.AUDIT_HOST_NAME)).thenReturn("test-host");
        when(environment.getProperty(PacketManagerConstants.AUDIT_HOST_IP)).thenReturn("127.0.0.1");
    }

    @Test
    public void testGetHostName() {
        String hostName = ServerUtil.getHostName();
        assertNotNull(hostName);
    }

    @Test
    public void testGetHostIp() {
        String hostIp = ServerUtil.getHostIp();
        assertNotNull(hostIp);
    }
}
