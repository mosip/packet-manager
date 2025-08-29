package io.mosip.commons.packet.test.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.commons.packet.exception.TagDeletionException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TagDeletionExceptionTest {

    @Test
    public void testTagDeletionException() {
        TagDeletionException exception = new TagDeletionException("test-message");
        assertEquals("test-message", exception.getMessage());
    }

    @Test
    public void testTagDeletionExceptionWithCause() {
        TagDeletionException exception = new TagDeletionException("test-message", new Throwable());
        assertEquals("test-message", exception.getMessage());
    }
}
