package io.mosip.commons.packet.test.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.exception.GetTagException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GetTagExceptionTest {

    @Test
    public void testGetTagException() {
        GetTagException exception = new GetTagException("test-message");
        assertEquals("test-message", exception.getMessage());
    }

    @Test
    public void testGetTagExceptionWithCause() {
        GetTagException exception = new GetTagException("test-message", new Throwable());
        assertEquals("test-message", exception.getMessage());
    }
}
