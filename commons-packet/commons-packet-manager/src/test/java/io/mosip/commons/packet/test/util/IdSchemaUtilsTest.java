package io.mosip.commons.packet.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.commons.packet.dto.packet.SimpleDto;
import io.mosip.commons.packet.exception.IdObjectIOException;
import io.mosip.commons.packet.exception.IdObjectValidationException;
import io.mosip.commons.packet.util.IdSchemaUtils;

@RunWith(SpringRunner.class)
@SpringBootTest
public class IdSchemaUtilsTest {

    @Mock
    private RestTemplate restTemplate;

    @MockBean
    private Environment environment;

    @Autowired
    private IdSchemaUtils idSchemaUtils;

    @Before
    public void setup() {
        when(environment.getProperty(Mockito.anyString())).thenReturn("some-value");
    }

    @Test
    public void testGetAllField() throws IOException, IdObjectIOException {
        String schema = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"title\":\"Test\",\"description\":\"Test\",\"type\":\"object\",\"properties\":{\"identity\":{\"type\":\"object\",\"properties\":{\"test\":{\"type\":\"string\"}}}}}";
        List<String> fieldNames = idSchemaUtils.getAllField(schema.getBytes());
        assertEquals(1, fieldNames.size());
        assertEquals("test", fieldNames.get(0));
    }

    @Test
    public void testGetIdSchema() throws IOException {
        String schema = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"title\":\"Test\",\"description\":\"Test\",\"type\":\"object\",\"properties\":{\"identity\":{\"type\":\"object\",\"properties\":{\"test\":{\"type\":\"string\"}}}}}";
        when(restTemplate.getForObject(Mockito.anyString(), Mockito.any())).thenReturn(schema);
        Map<String, Object> idSchema = idSchemaUtils.getIdSchema("some-url");
        assertNotNull(idSchema);
    }

    @Test
    public void testValidate() throws IOException, IdObjectValidationException {
        String schema = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"title\":\"Test\",\"description\":\"Test\",\"type\":\"object\",\"properties\":{\"identity\":{\"type\":\"object\",\"properties\":{\"test\":{\"type\":\"string\"}}}}}";
        List<SimpleDto> simpleDtoList = new ArrayList<>();
        SimpleDto simpleDto = new SimpleDto();
        simpleDto.setLanguage("eng");
        simpleDto.setValue("testValue");
        simpleDtoList.add(simpleDto);
        ObjectMapper objectMapper = new ObjectMapper();
        String identity = objectMapper.writeValueAsString(simpleDtoList);
        idSchemaUtils.validate(schema.getBytes(), identity);
    }

    @Test
    public void testIsIdSchemaValid() throws IOException {
        String schema = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"title\":\"Test\",\"description\":\"Test\",\"type\":\"object\",\"properties\":{\"identity\":{\"type\":\"object\",\"properties\":{\"test\":{\"type\":\"string\"}}}}}";
        assertTrue(idSchemaUtils.isIdSchemaValid(schema.getBytes()));
    }
}
