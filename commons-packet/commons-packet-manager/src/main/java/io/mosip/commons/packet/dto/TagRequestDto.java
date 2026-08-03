package io.mosip.commons.packet.dto;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TagRequestDto implements Serializable {
	
	private String id;
	private List<String> tagNames;

	@Schema(description = "Controls which tags are returned. Accepted values: " +
			"'anonymous' – returns only tags whose name starts with 'ANONYMOUS'; " +
			"'all' – returns every tag; " +
			"omit or leave blank – returns all tags except anonymous ones (default).")
	private String type;

	public TagRequestDto(String id, List<String> tagNames) {
		this.id = id;
		this.tagNames = tagNames;
		this.type = null;
	}
}
