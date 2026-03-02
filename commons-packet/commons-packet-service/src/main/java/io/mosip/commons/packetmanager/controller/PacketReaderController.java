package io.mosip.commons.packetmanager.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.commons.packetmanager.dto.SourceProcessDto;
import io.mosip.kernel.core.logger.spi.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.Lists;

import io.mosip.commons.packet.dto.Document;
import io.mosip.commons.packet.dto.TagRequestDto;
import io.mosip.commons.packet.dto.TagResponseDto;
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.commons.packetmanager.dto.BiometricRequestDto;
import io.mosip.commons.packetmanager.dto.DocumentDto;
import io.mosip.commons.packetmanager.dto.FieldDto;
import io.mosip.commons.packetmanager.dto.FieldDtos;
import io.mosip.commons.packetmanager.dto.FieldResponseDto;
import io.mosip.commons.packetmanager.dto.InfoDto;
import io.mosip.commons.packetmanager.dto.InfoRequestDto;
import io.mosip.commons.packetmanager.dto.InfoResponseDto;
import io.mosip.commons.packetmanager.dto.ValidatePacketResponse;
import io.mosip.commons.packetmanager.service.PacketReaderService;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseFilter;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.util.DateUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@Tag(name = "packet-reader-controller", description = "Packet Reader Controller")
public class PacketReaderController {

    @Autowired
    private PacketReader packetReader;

    private static Logger LOGGER = PacketManagerLogger.getLogger(PacketReaderController.class);

    @Autowired
    private PacketReaderService packetReaderService;
    @PreAuthorize("hasAnyRole(@authorizedRoles.getPostsearchfield())")
    @ResponseFilter
    @PostMapping(path = "/searchField", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "searchField", description = "searchField", tags = { "packet-reader-controller" })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
    public ResponseWrapper<FieldResponseDto> searchField(@RequestBody(required = true) RequestWrapper<FieldDto> fieldDto) {
        long startMs = System.currentTimeMillis();
        String rid = fieldDto.getRequest() != null ? fieldDto.getRequest().getId() : null;
        LOGGER.info(   "searchField entered | RID=" + rid);
        try {
            SourceProcessDto sourceProcessDto = packetReaderService.getSourceAndProcess(fieldDto.getRequest().getId(),
                    fieldDto.getRequest().getField(), fieldDto.getRequest().getSource(), fieldDto.getRequest().getProcess());
            String resultField = sourceProcessDto == null ? null :
                    packetReader.getField(fieldDto.getRequest().getId(),
                            fieldDto.getRequest().getField(), sourceProcessDto.getSource(), sourceProcessDto.getProcess(), fieldDto.getRequest().getBypassCache());
            ResponseWrapper<FieldResponseDto> response = new ResponseWrapper<FieldResponseDto>();
            Map<String, String> responseMap = new HashMap<>();
            responseMap.put(fieldDto.getRequest().getField(), resultField);
            FieldResponseDto fieldResponseDto = new FieldResponseDto(responseMap);

            response.setResponse(fieldResponseDto);
            return response;
        } finally {
            long timeMs = System.currentTimeMillis() - startMs;
            LOGGER.info( "searchField completed | RID=" + rid + " timeMs=" + timeMs);
        }
    }

    @PreAuthorize("hasAnyRole(@authorizedRoles.getPostsearchfields())")
    @ResponseFilter
    @PostMapping(path = "/searchFields", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "searchFields", description = "searchFields", tags = { "packet-reader-controller" })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
    public ResponseWrapper<FieldResponseDto> searchFields(@RequestBody(required = true) RequestWrapper<FieldDtos> request)  {
        long startMs = System.currentTimeMillis();
        FieldDtos fieldDtos = request.getRequest();
        String rid = fieldDtos != null ? fieldDtos.getId() : null;
        LOGGER.info(  "searchFields entered | RID=" + rid);
        try {
            Map<String, String> resultFields = new HashMap<>();
            if ((fieldDtos.getSource()) == null) {
                for (String field : fieldDtos.getFields()) {
                    SourceProcessDto sourceProcessDto = packetReaderService.getSourceAndProcess(fieldDtos.getId(),
                            field, fieldDtos.getSource(), fieldDtos.getProcess());
                    String value = sourceProcessDto == null ? null :
                            packetReader.getField(fieldDtos.getId(), field, sourceProcessDto.getSource(),
                                    sourceProcessDto.getProcess(), fieldDtos.getBypassCache());
                    resultFields.put(field, value);
                }
            } else
                resultFields = packetReader.getFields(fieldDtos.getId(), fieldDtos.getFields(), fieldDtos.getSource(), fieldDtos.getProcess(), fieldDtos.getBypassCache());
            FieldResponseDto resultField = new FieldResponseDto(resultFields);
            ResponseWrapper<FieldResponseDto> response = new ResponseWrapper<FieldResponseDto>();
            response.setResponse(resultField);
            return response;
        } finally {
            long timeMs = System.currentTimeMillis() - startMs;
            LOGGER.info("searchFields completed | RID=" + rid + " timeMs=" + timeMs);
        }
    }

    @ResponseFilter
    @PreAuthorize("hasAnyRole(@authorizedRoles.getPostdocument())")
    @PostMapping(path = "/document", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "getDocument", description = "getDocument", tags = { "packet-reader-controller" })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
    public ResponseWrapper<Document> getDocument(@RequestBody(required = true) RequestWrapper<DocumentDto> request) {
        long startMs = System.currentTimeMillis();
        DocumentDto documentDto = request.getRequest();
        String rid = documentDto != null ? documentDto.getId() : null;
        LOGGER.info("getDocument entered | RID=" + rid);
        try {
            SourceProcessDto sourceProcessDto = packetReaderService.getSourceAndProcess(documentDto.getId(),
                    documentDto.getDocumentName(), documentDto.getSource(), documentDto.getProcess());
            Document document = sourceProcessDto == null ? null :
                    packetReader.getDocument(documentDto.getId(), documentDto.getDocumentName(),
                            sourceProcessDto.getSource(), sourceProcessDto.getProcess());
            ResponseWrapper<Document> response = new ResponseWrapper<Document>();
            response.setResponse(document);
            return response;
        } finally {
            long timeMs = System.currentTimeMillis() - startMs;
            LOGGER.info(    "getDocument completed | RID=" + rid + " timeMs=" + timeMs);
        }
    }

    @ResponseFilter
    @PreAuthorize("hasAnyRole(@authorizedRoles.getPostbiometrics())")
    @PostMapping(path = "/biometrics", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "getBiometrics", description = "getBiometrics", tags = { "packet-reader-controller" })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
    public ResponseWrapper<BiometricRecord> getBiometrics(@RequestBody(required = true) RequestWrapper<BiometricRequestDto> request) {
        long startMs = System.currentTimeMillis();
        BiometricRequestDto bioRequest = request.getRequest();
        String rid = bioRequest != null ? bioRequest.getId() : null;
        LOGGER.info(   "getBiometrics entered | RID=" + rid);
        try {
            SourceProcessDto sourceProcessDto = packetReaderService.getSourceAndProcess(bioRequest.getId(),
                    bioRequest.getPerson(), bioRequest.getSource(), bioRequest.getProcess());
            List<String> modalities = bioRequest.getModalities() == null ? Lists.newArrayList() : bioRequest.getModalities();
            BiometricRecord responseDto = sourceProcessDto == null ? null :
                    packetReader.getBiometric(bioRequest.getId(), bioRequest.getPerson(), modalities,
                            sourceProcessDto.getSource(), sourceProcessDto.getProcess(), bioRequest.isBypassCache());
            ResponseWrapper<BiometricRecord> response = getResponseWrapper();
            response.setResponse(responseDto);
            return response;
        } finally {
            long timeMs = System.currentTimeMillis() - startMs;
            LOGGER.info(  "getBiometrics completed | RID=" + rid + " timeMs=" + timeMs);
        }
    }

    @ResponseFilter
    @PreAuthorize("hasAnyRole(@authorizedRoles.getPostmetainfo())")
    @PostMapping(path = "/metaInfo", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "getMetaInfo", description = "getMetaInfo", tags = { "packet-reader-controller" })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
    public ResponseWrapper<FieldResponseDto> getMetaInfo(@RequestBody(required = true) RequestWrapper<InfoDto> request) {
        long startMs = System.currentTimeMillis();
        InfoDto metaDto = request.getRequest();
        String rid = metaDto != null ? metaDto.getId() : null;
        LOGGER.info("getMetaInfo entered | RID=" + rid);
        try {
            SourceProcessDto sourceProcessDto = packetReaderService.getSourceAndProcess(metaDto.getId(), metaDto.getSource(), metaDto.getProcess());
            Map<String, String> resultFields = packetReader.getMetaInfo(metaDto.getId(),
                    sourceProcessDto.getSource(), sourceProcessDto.getProcess(), metaDto.getBypassCache());
            FieldResponseDto resultField = new FieldResponseDto(resultFields);
            ResponseWrapper<FieldResponseDto> response = getResponseWrapper();
            response.setResponse(resultField);
            return response;
        } finally {
            long timeMs = System.currentTimeMillis() - startMs;
            LOGGER.info("getMetaInfo completed | RID=" + rid + " timeMs=" + timeMs);
        }
    }

    @ResponseFilter
    @PreAuthorize("hasAnyRole(@authorizedRoles.getPostaudits())")
    @PostMapping(path = "/audits", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "getAudits", description = "getAudits", tags = { "packet-reader-controller" })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
    public ResponseWrapper<List<FieldResponseDto>> getAudits(@RequestBody(required = true) RequestWrapper<InfoDto> request) {
        long startMs = System.currentTimeMillis();
        InfoDto metaDto = request.getRequest();
        String rid = metaDto != null ? metaDto.getId() : null;
        LOGGER.info("getAudits entered | RID=" + rid);
        try {
            SourceProcessDto sourceProcessDto = packetReaderService.getSourceAndProcess(metaDto.getId(), metaDto.getSource(), metaDto.getProcess());
            List<Map<String, String>> resultFields = packetReader.getAudits(metaDto.getId(),
                    sourceProcessDto.getSource(), sourceProcessDto.getProcess(), metaDto.getBypassCache());
            List<FieldResponseDto> resultField = new ArrayList<>();
            if (resultFields != null && !resultFields.isEmpty()) {
                resultFields.stream().forEach(e -> {
                    FieldResponseDto fieldResponseDto = new FieldResponseDto(e);
                    resultField.add(fieldResponseDto);
                });
            }
            ResponseWrapper<List<FieldResponseDto>> response = getResponseWrapper();
            response.setResponse(resultField);
            return response;
        } finally {
            long timeMs = System.currentTimeMillis() - startMs;
            LOGGER.info("getAudits completed | RID=" + rid + " timeMs=" + timeMs);
        }
    }

    @ResponseFilter
    @PreAuthorize("hasAnyRole(@authorizedRoles.getPostvalidatepacket())")
    @PostMapping(path = "/validatePacket", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "validatePacket", description = "validatePacket", tags = { "packet-reader-controller" })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
    public ResponseWrapper<ValidatePacketResponse> validatePacket(@RequestBody(required = true) RequestWrapper<InfoDto> request) {
        long startMs = System.currentTimeMillis();
        InfoDto metaDto = request.getRequest();
        String rid = metaDto != null ? metaDto.getId() : null;
        LOGGER.info("validatePacket entered | RID=" + rid);
        try {
            SourceProcessDto sourceProcessDto = packetReaderService.getSourceAndProcess(metaDto.getId(), metaDto.getSource(), metaDto.getProcess());
            boolean resultFields = packetReader.validatePacket(metaDto.getId(), sourceProcessDto.getSource(), sourceProcessDto.getProcess());
            ResponseWrapper<ValidatePacketResponse> response = getResponseWrapper();
            response.setResponse(new ValidatePacketResponse(resultFields));
            return response;
        } finally {
            long timeMs = System.currentTimeMillis() - startMs;
            LOGGER.info("validatePacket completed | RID=" + rid + " timeMs=" + timeMs);
        }
    }

    @PreAuthorize("hasAnyRole(@authorizedRoles.getPostgettags())")
    @ResponseFilter
    @PostMapping(path = "/getTags", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "getTags", description = "getTags", tags = { "packet-reader-controller" })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
    public ResponseWrapper<TagResponseDto> getTags(
            @RequestBody(required = true) RequestWrapper<TagRequestDto> request) {
        long startMs = System.currentTimeMillis();
        String rid = request.getRequest() != null ? request.getRequest().getId() : null;
        LOGGER.info( "getTags entered | RID=" + rid);
        try {
            TagResponseDto tagResponseDto = packetReaderService.getTags(request.getRequest());
            ResponseWrapper<TagResponseDto> response = getResponseWrapper();
            response.setResponse(tagResponseDto);
            return response;
        } finally {
            long timeMs = System.currentTimeMillis() - startMs;
            LOGGER.info("getTags completed | RID=" + rid + " timeMs=" + timeMs);
        }
    }

    @ResponseFilter
    @PreAuthorize("hasAnyRole(@authorizedRoles.getPostinfo())")
    @PostMapping(path = "/info", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "info", description = "info", tags = { "packet-reader-controller" })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true))) })
    public ResponseWrapper<InfoResponseDto> info(@RequestBody(required = true) RequestWrapper<InfoRequestDto> request) {
        long startMs = System.currentTimeMillis();
        String rid = request.getRequest() != null ? request.getRequest().getId() : null;
        LOGGER.info("info entered | RID=" + rid);
        try {
            InfoResponseDto resultFields = null;
            if (rid != null && !rid.isEmpty())
                resultFields = packetReaderService.info(rid);
            ResponseWrapper<InfoResponseDto> response = getResponseWrapper();
            response.setResponse(resultFields);
            return response;
        } finally {
            long timeMs = System.currentTimeMillis() - startMs;
            LOGGER.info("info completed | RID=" + rid + " timeMs=" + timeMs);
        }
    }

    private ResponseWrapper getResponseWrapper() {
        ResponseWrapper<Object> response = new ResponseWrapper<>();
        response.setId("mosip.registration.packet.reader");
        response.setVersion("v1");
        response.setResponsetime(DateUtils.getUTCCurrentDateTime());
        return response;
    }
}