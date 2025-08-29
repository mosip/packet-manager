package io.mosip.commons.packet.test.util;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.exception.PacketValidationFailureException;
import io.mosip.commons.packet.util.PacketValidator;

@RunWith(SpringRunner.class)
@SpringBootTest
public class PacketValidatorTest {

    @Autowired
    private PacketValidator packetValidator;

    @Test
    public void testValidate() throws PacketValidationFailureException {
        Set<String> tags = new HashSet<>();
        tags.add("test-tag");
        packetValidator.validate(tags, "test-tag-to-validate");
        assertTrue(true);
    }

    @Test(expected = PacketValidationFailureException.class)
    public void testValidateFailure() throws PacketValidationFailureException {
        Set<String> tags = new HashSet<>();
        tags.add("test-tag");
        packetValidator.validate(tags, "invalid-tag");
    }
}
