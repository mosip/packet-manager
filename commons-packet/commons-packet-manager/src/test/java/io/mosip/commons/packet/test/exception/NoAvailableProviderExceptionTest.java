package io.mosip.commons.packet.test.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.exception.NoAvailableProviderException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class NoAvailableProviderExceptionTest {

    @Test
    public void testNoAvailableProviderException() {
        NoAvailableProviderException exception = new NoAvailableProviderException("test-message");
        assertEquals("test-message", exception.getMessage());
    }

    @Test
    public void testNoAvailableProviderExceptionWithCause() {
        NoAvailableProviderException exception = new NoAvailableProviderException("test-message", new Throwable());
        assertEquals("test-message", exception.getMessage());
    }
}
