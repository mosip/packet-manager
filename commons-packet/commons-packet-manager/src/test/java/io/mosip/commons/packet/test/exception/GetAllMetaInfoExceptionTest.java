package io.mosip.commons.packet.test.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.exception.GetAllMetaInfoException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GetAllMetaInfoExceptionTest {

    @Test
    public void testGetAllMetaInfoException() {
        GetAllMetaInfoException exception = new GetAllMetaInfoException("test-message");
        assertEquals("test-message", exception.getMessage());
    }

    @Test
    public void testGetAllMetaInfoExceptionWithCause() {
        GetAllMetaInfoException exception = new GetAllMetaInfoException("test-message", new Throwable());
        assertEquals("test-message", exception.getMessage());
    }
}
