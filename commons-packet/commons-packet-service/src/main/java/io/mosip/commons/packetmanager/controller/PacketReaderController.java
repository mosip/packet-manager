package io.mosip.commons.packetmanager.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
import io.mosip.commons.packetmanager.dto.SourceProcessDto;
import io.mosip.commons.packetmanager.dto.ValidatePacketResponse;
import io.mosip.commons.packetmanager.service.PacketReaderService;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseFilter;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.util.DateUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "packet-reader-controller", description = "Packet Reader Controller")
public class PacketReaderController {

	@Autowired
	private PacketReader packetReader;

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
	public ResponseWrapper<FieldResponseDto> searchField(
			@RequestBody(required = true) RequestWrapper<FieldDto> fieldDto) {
		FieldDto req = fieldDto.getRequest();
		String value = getSourceProcess(req.getId(), req.getField(), req.getSource(), req.getProcess())
				.map(sp -> packetReader.getField(req.getId(), req.getField(), sp.getSource(), sp.getProcess(),
						req.getBypassCache()))
				.orElse(null);
		return wrapResponse(new FieldResponseDto(Map.of(req.getField(), value)));
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
	public ResponseWrapper<FieldResponseDto> searchFields(
			@RequestBody(required = true) RequestWrapper<FieldDtos> request) {
		FieldDtos dto = request.getRequest();
		Map<String, String> resultFields = new HashMap<>();
        if ((dto.getSource()) == null) {
            for (String field : dto.getFields()) {
                SourceProcessDto sourceProcessDto = packetReaderService.getSourceAndProcess(dto.getId(),
                        field, dto.getSource(), dto.getProcess());
                String value = sourceProcessDto == null ? null :
                        packetReader.getField(dto.getId(), field, sourceProcessDto.getSource(),
                        sourceProcessDto.getProcess(), dto.getBypassCache());
                resultFields.put(field, value);
            }
        } else {
            resultFields = packetReader.getFields(dto.getId(), dto.getFields(), dto.getSource(), dto.getProcess(),
					dto.getBypassCache());
		}

		return wrapResponse(new FieldResponseDto(resultFields));
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
		DocumentDto dto = request.getRequest();
		Document doc = getSourceProcess(dto.getId(), dto.getDocumentName(), dto.getSource(), dto.getProcess()).map(
				sp -> packetReader.getDocument(dto.getId(), dto.getDocumentName(), sp.getSource(), sp.getProcess()))
				.orElse(null);
		return wrapResponse(doc);
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
	public ResponseWrapper<BiometricRecord> getBiometrics(
			@RequestBody(required = true) RequestWrapper<BiometricRequestDto> request) {
		BiometricRequestDto dto = request.getRequest();
		List<String> modalities = Optional.ofNullable(dto.getModalities()).orElseGet(ArrayList::new);

		BiometricRecord bio = getSourceProcess(dto.getId(), dto.getPerson(), dto.getSource(), dto.getProcess())
				.map(sp -> packetReader.getBiometric(dto.getId(), dto.getPerson(), modalities, sp.getSource(),
						sp.getProcess(), dto.isBypassCache()))
				.orElse(null);
		return wrapResponse(bio);
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
	public ResponseWrapper<FieldResponseDto> getMetaInfo(
			@RequestBody(required = true) RequestWrapper<InfoDto> request) {
		InfoDto metaDto = request.getRequest();
		Optional<SourceProcessDto> sourceProcessOpt = getSourceProcess(metaDto.getId(), metaDto.getSource(),
				metaDto.getProcess());

		if (sourceProcessOpt.isEmpty()) {
			return wrapResponse(new FieldResponseDto(Map.of()));
		}

		Map<String, String> fields = packetReader.getMetaInfo(metaDto.getId(), sourceProcessOpt.get().getSource(),
				sourceProcessOpt.get().getProcess(), metaDto.getBypassCache());

		return wrapResponse(new FieldResponseDto(fields));
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
	public ResponseWrapper<List<FieldResponseDto>> getAudits(
			@RequestBody(required = true) RequestWrapper<InfoDto> request) {
		InfoDto metaDto = request.getRequest();
		Optional<SourceProcessDto> sourceProcessOpt = getSourceProcess(metaDto.getId(), metaDto.getSource(),
				metaDto.getProcess());

		if (sourceProcessOpt.isEmpty()) {
			return wrapResponse(List.of());
		}

		List<Map<String, String>> audits = packetReader.getAudits(metaDto.getId(), sourceProcessOpt.get().getSource(),
				sourceProcessOpt.get().getProcess(), metaDto.getBypassCache());

		List<FieldResponseDto> auditList = Optional.ofNullable(audits).orElseGet(List::of).stream()
				.map(FieldResponseDto::new).collect(Collectors.toList());

		return wrapResponse(auditList);
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
	public ResponseWrapper<ValidatePacketResponse> validatePacket(
			@RequestBody(required = true) RequestWrapper<InfoDto> request) {
		InfoDto dto = request.getRequest();

		Optional<SourceProcessDto> sourceProcessOpt = getSourceProcess(dto.getId(), dto.getSource(), dto.getProcess());

		boolean isValid = sourceProcessOpt
				.map(sp -> packetReader.validatePacket(dto.getId(), sp.getSource(), sp.getProcess())).orElse(false);

		return wrapResponse(new ValidatePacketResponse(isValid));
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
		TagResponseDto tags = packetReaderService.getTags(request.getRequest());
		return wrapResponse(tags);
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
		InfoRequestDto dto = request.getRequest();
		InfoResponseDto info = (dto.getId() != null && !dto.getId().isEmpty()) ? packetReaderService.info(dto.getId())
				: null;
		return wrapResponse(info);
	}

	private <T> ResponseWrapper<T> wrapResponse(T data) {
		ResponseWrapper<T> response = new ResponseWrapper<>();
		response.setId("mosip.registration.packet.reader");
		response.setVersion("v1");
		response.setResponsetime(DateUtils.getUTCCurrentDateTime());
		response.setResponse(data);
		return response;
	}

	private Optional<SourceProcessDto> getSourceProcess(String id, String field, String source, String process) {
		return Optional.ofNullable(packetReaderService.getSourceAndProcess(id, field, source, process));
	}

	private Optional<SourceProcessDto> getSourceProcess(String id, String source, String process) {
		return Optional.ofNullable(packetReaderService.getSourceAndProcess(id, source, process));
	}
}