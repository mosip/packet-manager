package io.mosip.commons.packetmanager.test.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import io.mosip.commons.packet.dto.TagDeleteResponseDto;
import io.mosip.commons.packet.dto.TagDto;
import io.mosip.commons.packet.dto.TagRequestDto;
import io.mosip.commons.packet.dto.TagResponseDto;
import io.mosip.commons.packet.dto.packet.PacketDto;
import io.mosip.commons.packet.facade.PacketWriter;
import io.mosip.commons.packetmanager.service.PacketWriterService;
import io.mosip.commons.packetmanager.test.TestBootApplication;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.util.JsonUtils;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestBootApplication.class)
@AutoConfigureMockMvc
public class PacketWriterControllerTest {

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PacketWriter packetWriter;

    @MockBean
    private PacketWriterService packetWriterService;

    private RequestWrapper<Object> request = new RequestWrapper<>();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @WithUserDetails("reg-processor")
    public void testCreatePacket() throws Exception {
        PacketDto packetDto = new PacketDto();
        packetDto.setId("id");
        packetDto.setProcess("NEW");
        packetDto.setSource("REGISTRATION");

        Mockito.when(
                packetWriter.createPacket(any())).thenReturn(new ArrayList<>());

        request.setRequest(packetDto);

        this.mockMvc.perform(put("/create").contentType(MediaType.APPLICATION_JSON).content(JsonUtils.javaObjectToJsonString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("reg-processor")
    public void testBaseUncheckedException() throws Exception {
        PacketDto packetDto = new PacketDto();
        packetDto.setId("id");
        packetDto.setProcess("NEW");
        packetDto.setSource("REGISTRATION");

        Mockito.when(
                packetWriter.createPacket(any())).thenThrow(new BaseUncheckedException("errorCode", "errorMessage"));

        request.setRequest(packetDto);

        this.mockMvc.perform(put("/create").contentType(MediaType.APPLICATION_JSON).content(JsonUtils.javaObjectToJsonString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithUserDetails("reg-processor")
    public void testAddTag() throws Exception {
	TagDto tagDto = new TagDto();
	tagDto.setId("id");

        Mockito.when(
			packetWriterService.addTags(any())).thenReturn(new TagResponseDto());

        request.setRequest(tagDto);

        this.mockMvc.perform(post("/tags").contentType(MediaType.APPLICATION_JSON).content(JsonUtils.javaObjectToJsonString(request)))
                .andExpect(status().isOk());
    }
    @Test
    @WithUserDetails("reg-processor")
    public void testUpdateTags() throws Exception {
	TagDto tagDto = new TagDto();
	tagDto.setId("id");

        Mockito.when(
			packetWriterService.updateTags(any())).thenReturn(new TagResponseDto());

        request.setRequest(tagDto);

        this.mockMvc.perform(put("/tags").contentType(MediaType.APPLICATION_JSON).content(JsonUtils.javaObjectToJsonString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("reg-processor")
    public void testDeleteTags() throws Exception {
	TagRequestDto tagRequestDto = new TagRequestDto();
	tagRequestDto.setId("id");
	  List<String> tagNames=new ArrayList<>();
          tagNames.add("osivalidation");
          tagRequestDto.setTagNames(tagNames);
          TagDeleteResponseDto tagResponse=new TagDeleteResponseDto();
		tagResponse.setStatus("Deleted Successfully");
          Mockito.when(
			  packetWriterService.deleteTags(any())).thenReturn(tagResponse);

        request.setRequest(tagRequestDto);

        this.mockMvc.perform(delete("/tags").contentType(MediaType.APPLICATION_JSON).content(JsonUtils.javaObjectToJsonString(request)))
                .andExpect(status().isOk());
    }
}
