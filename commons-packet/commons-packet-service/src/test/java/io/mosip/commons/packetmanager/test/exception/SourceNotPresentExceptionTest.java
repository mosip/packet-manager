package io.mosip.commons.packetmanager.test.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packetmanager.exception.SourceNotPresentException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SourceNotPresentExceptionTest {

    @Test
    public void testSourceNotPresentException() {
        SourceNotPresentException exception = new SourceNotPresentException("test-message");
        assertEquals("test-message", exception.getMessage());
    }

    @Test
    public void testSourceNotPresentExceptionWithCause() {
        SourceNotPresentException exception = new SourceNotPresentException("test-message", new Throwable());
        assertEquals("test-message", exception.getMessage());
    }
}
