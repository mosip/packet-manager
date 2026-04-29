package io.mosip.commons.packetmanager.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mosip.commons.packet.constants.PacketUtilityErrorCodes;
import io.mosip.commons.packet.dto.TagDeleteResponseDto;
import io.mosip.commons.packet.dto.TagDto;
import io.mosip.commons.packet.dto.TagRequestDto;
import io.mosip.commons.packet.dto.TagResponseDto;
import io.mosip.commons.packet.exception.TagCreationException;
import io.mosip.commons.packet.exception.TagDeletionException;
import io.mosip.commons.packet.facade.PacketReader;
import io.mosip.commons.packet.facade.PacketWriter;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;

@Component
public class PacketWriterService {
    private static Logger LOGGER = PacketManagerLogger.getLogger(PacketWriterService.class);
    
    @Autowired
    private PacketReader packetReader;
    
    @Autowired
    private PacketWriter packetWriter;
    
    
    public TagResponseDto addTags(TagDto tagDto) {
    	try {
			Map<String, String> requestedTags = tagDto.getTags() != null ? tagDto.getTags() : Collections.emptyMap();
			if (requestedTags.isEmpty()) {
				TagResponseDto emptyResponse = new TagResponseDto();
				emptyResponse.setTags(Collections.emptyMap());
				return emptyResponse;
			}

			Map<String, String> existingTags = packetReader.getTags(tagDto.getId());
			for (String tagKey : requestedTags.keySet()) {
				if (existingTags.containsKey(tagKey)) {
					throw new TagCreationException(PacketUtilityErrorCodes.TAG_ALREADY_EXIST.getErrorCode(),
							PacketUtilityErrorCodes.TAG_ALREADY_EXIST.getErrorMessage());
				}
			}

			TagDto request = new TagDto();
			request.setId(tagDto.getId());
			request.setTags(new HashMap<>(requestedTags));
			Map<String, String> tags = packetWriter.addTags(request, request.getId());
			TagResponseDto tagResponseDto = new TagResponseDto();
			tagResponseDto.setTags(tags);
			return tagResponseDto;
		} catch (Exception e) {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, tagDto.getId(),
					ExceptionUtils.getStackTrace(e));
			if (e instanceof BaseCheckedException) {
				BaseCheckedException ex = (BaseCheckedException) e;
				throw new TagCreationException(ex.getErrorCode(), ex.getMessage());
			} else if (e instanceof BaseUncheckedException) {
				BaseUncheckedException ex = (BaseUncheckedException) e;
				throw new TagCreationException(ex.getErrorCode(), ex.getMessage());
			}
			throw new TagCreationException(e.getMessage());
		}
    }
    
    public TagResponseDto updateTags(TagDto tagDto) {
    	try {
			Map<String, String> requestedTags = tagDto.getTags() != null ? tagDto.getTags() : Collections.emptyMap();
			Map<String, String> newTags = new HashMap<String, String>();
			Map<String, String> existingTags = packetReader.getTags(tagDto.getId());
			if (existingTags.isEmpty()) {
				newTags.putAll(requestedTags);
			} else {
				for (Entry<String, String> entry : requestedTags.entrySet()) {
					if (existingTags.containsKey(entry.getKey())) {
						String existingValue = existingTags.get(entry.getKey());
						String newValue = entry.getValue();
						if (!equalsIgnoreCaseNullable(existingValue, newValue))
							newTags.put(entry.getKey(), newValue);
					} else {
						newTags.put(entry.getKey(), entry.getValue());
					}
				}
			}
			TagResponseDto tagResponseDto = new TagResponseDto();

			if (newTags.isEmpty()) {
				tagResponseDto.setTags(requestedTags);
			} else {
				TagDto request = new TagDto();
				request.setId(tagDto.getId());
				request.setTags(newTags);
				Map<String, String> tags = packetWriter.addTags(request, request.getId());
				tagResponseDto.setTags(tags);
			}

			return tagResponseDto;
		} catch (Exception e) {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, tagDto.getId(),
					ExceptionUtils.getStackTrace(e));
			if (e instanceof BaseCheckedException) {
				BaseCheckedException ex = (BaseCheckedException) e;
				throw new TagCreationException(ex.getErrorCode(), ex.getMessage());
			} else if (e instanceof BaseUncheckedException) {
				BaseUncheckedException ex = (BaseUncheckedException) e;
				throw new TagCreationException(ex.getErrorCode(), ex.getMessage());
			}
			throw new TagCreationException(e.getMessage());
		}
    }
    
    public TagDeleteResponseDto deleteTags(TagRequestDto tagRequestDto) {
    	try {
    		List<String> deleteTags = new ArrayList<String>();
			Map<String, String> existingTags = packetReader.getTags(tagRequestDto.getId());
			List<String> requestedTagNames = tagRequestDto.getTagNames() != null ? tagRequestDto.getTagNames()
					: Collections.emptyList();

			for (String tagName : requestedTagNames) {
				if (existingTags.containsKey(tagName)) {
					deleteTags.add(tagName);
				}
			}
			TagDeleteResponseDto tagDeleteResponseDto = new TagDeleteResponseDto();
			if (!deleteTags.isEmpty()) {
				TagRequestDto request = new TagRequestDto();
				request.setId(tagRequestDto.getId());
				request.setTagNames(deleteTags);
				packetWriter.deleteTags(request, request.getId());
			}

			tagDeleteResponseDto.setStatus("Deleted Successfully");
			return tagDeleteResponseDto;

		} catch (Exception e) {
			LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, tagRequestDto.getId(),
					ExceptionUtils.getStackTrace(e));
			if (e instanceof BaseCheckedException) {
				BaseCheckedException ex = (BaseCheckedException) e;
				throw new TagDeletionException(ex.getErrorCode(), ex.getMessage());
			} else if (e instanceof BaseUncheckedException) {
				BaseUncheckedException ex = (BaseUncheckedException) e;
				throw new TagDeletionException(ex.getErrorCode(), ex.getMessage());
			}
			throw new TagDeletionException(e.getMessage());
		}
    }

	private boolean equalsIgnoreCaseNullable(String value1, String value2) {
		if (Objects.equals(value1, value2))
			return true;
		if (value1 == null || value2 == null)
			return false;
		return value1.equalsIgnoreCase(value2);
	}
}
