package io.mosip.commons.packet.dto;

import java.io.InputStream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileValidationDto {

    private boolean fileValidation;
    private InputStream dataHashStream;
    private InputStream operationsHashStream;
}