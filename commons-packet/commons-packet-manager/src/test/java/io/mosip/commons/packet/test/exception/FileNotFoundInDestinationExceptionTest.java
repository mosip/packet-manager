package io.mosip.commons.packet.test.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.exception.FileNotFoundInDestinationException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class FileNotFoundInDestinationExceptionTest {

    @Test
    public void testFileNotFoundInDestinationException() {
        FileNotFoundInDestinationException exception = new FileNotFoundInDestinationException("test-message");
        assertEquals("test-message", exception.getMessage());
    }

    @Test
    public void testFileNotFoundInDestinationExceptionWithCause() {
        FileNotFoundInDestinationException exception = new FileNotFoundInDestinationException("test-message", new Throwable());
        assertEquals("test-message", exception.getMessage());
    }
}
