package io.mosip.commons.packetmanager.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.http.ResponseWrapper;

@RestControllerAdvice
public class PacketServiceControllerAdvice {

	@ExceptionHandler(BaseCheckedException.class)
	public ResponseEntity<ResponseWrapper<ServiceError>> handleBaseCheckedException(BaseCheckedException e) {
		return buildResponse(e.getErrorCode(), e.getErrorText());
	}

	@ExceptionHandler(BaseUncheckedException.class)
	public ResponseEntity<ResponseWrapper<ServiceError>> handleBaseUncheckedException(BaseUncheckedException e) {
		return buildResponse(e.getErrorCode(), e.getErrorText());
	}

	private ResponseEntity<ResponseWrapper<ServiceError>> buildResponse(String errorCode, String message) {
		ServiceError serviceError = new ServiceError(errorCode, message);
		ResponseWrapper<ServiceError> response = new ResponseWrapper<>();
		response.setErrors(List.of(serviceError));
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
	}
}