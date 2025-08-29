package io.mosip.commons.packet.test.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.exception.GetBiometricException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GetBiometricExceptionTest {

    @Test
    public void testGetBiometricException() {
        GetBiometricException exception = new GetBiometricException("test-message");
        assertEquals("test-message", exception.getMessage());
    }

    @Test
    public void testGetBiometricExceptionWithCause() {
        GetBiometricException exception = new GetBiometricException("test-message", new Throwable());
        assertEquals("test-message", exception.getMessage());
    }
}
