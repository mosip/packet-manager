package io.mosip.commons.packet.test.audit;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.audit.AuditLogEntry;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AuditLogEntryTest {

    @Test
    public void testAuditLogEntry() {
        AuditLogEntry auditLogEntry = new AuditLogEntry("test-event", "test-type", "test-app-id", "test-app-name");
        assertNotNull(auditLogEntry);
    }
}
