package io.mosip.commons.packet.dto;

import java.io.Serializable;
import java.util.List;

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
	private String type;

	public TagRequestDto(String id, List<String> tagNames) {
		this.id = id;
		this.tagNames = tagNames;
		this.type = null;
	}
}
