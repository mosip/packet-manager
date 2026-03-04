package io.mosip.commons.packet.audit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import io.mosip.commons.packet.constants.LoggerFileConstant;
import io.mosip.commons.packet.dto.packet.AuditRequestDto;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils2;

@Component
public class AuditLogEntry {

	private static final Logger LOGGER = PacketManagerLogger.getLogger(AuditLogEntry.class);

	private static final String AUDIT_SERVICE_ID = "mosip.commons.packet.manager";
	private static final String APPLICATION_VERSION = "v1";
	private static final String DATETIME_PATTERN = "mosip.utc-datetime-pattern";

	@Autowired
	@Lazy
	@Qualifier("selfTokenRestTemplate")
	private RestTemplate restTemplate;

	@Autowired
	private Environment env;

	/**
	 * Dedicated executor for fire-and-forget audit HTTP calls.
	 * Injected by name — defined in AuditAsyncConfig.
	 */
	@Autowired
	@Qualifier("auditTaskExecutor")
	private Executor auditExecutor;

	@Value("${AUDIT_URL:null}")
	private String auditLogUrl;

	// Cached once at startup — DateTimeFormatter is thread-safe.
	private DateTimeFormatter dateTimeFormatter;

	// Resolved once at startup — avoids repeated static method calls per audit.
	private String serverIp;
	private String serverName;

	@PostConstruct
	private void init() {
		String pattern = env.getProperty(DATETIME_PATTERN);
		dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);

		ServerUtil serverUtil = ServerUtil.getServerUtilInstance();
		serverIp = serverUtil.getServerIp();
		serverName = serverUtil.getServerName();
	}

	/**
	 * Submits the audit HTTP call to the dedicated executor and returns
	 * immediately — the calling thread is never blocked by network I/O.
	 *
	 * Returns a CompletableFuture<String> so callers that genuinely need
	 * the audit response body can still await it; callers that don't can
	 * simply ignore the returned future.
	 */
	public CompletableFuture<String> addAudit(String description, String eventId,
											  String eventName, String eventType,
											  String moduleId, String moduleName, String id) {

		LOGGER.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(),
				id, "AuditLogEntry::addAudit::submitted to async executor");

		// Capture values used inside the lambda to avoid capturing 'this'
		// fields that could change between submission and execution.
		final String capturedIp = serverIp;
		final String capturedName = serverName;
		final DateTimeFormatter capturedFormatter = dateTimeFormatter;

		return CompletableFuture.supplyAsync(() -> {
			try {
				AuditRequestDto auditRequestDto = new AuditRequestDto();
				auditRequestDto.setDescription(description);
				auditRequestDto.setActionTimeStamp(DateUtils2.getUTCCurrentDateTimeString());
				auditRequestDto.setApplicationId(LoggerFileConstant.MOSIP_4.toString());
				auditRequestDto.setApplicationName(LoggerFileConstant.PACKET_MANAGER.toString());
				auditRequestDto.setCreatedBy(LoggerFileConstant.SYSTEM.toString());
				auditRequestDto.setEventId(eventId);
				auditRequestDto.setEventName(eventName);
				auditRequestDto.setEventType(eventType);
				auditRequestDto.setHostIp(capturedIp);
				auditRequestDto.setHostName(capturedName);
				auditRequestDto.setId(id);
				auditRequestDto.setIdType(LoggerFileConstant.ID.toString());
				auditRequestDto.setModuleId(moduleId);
				auditRequestDto.setModuleName(moduleName);
				auditRequestDto.setSessionUserId(LoggerFileConstant.SYSTEM.toString());
				auditRequestDto.setSessionUserName(null);

				RequestWrapper<AuditRequestDto> requestWrapper = new RequestWrapper<>();
				requestWrapper.setId(AUDIT_SERVICE_ID);
				requestWrapper.setMetadata(null);
				requestWrapper.setRequest(auditRequestDto);

				String currentDateTimeStr = DateUtils2.getUTCCurrentDateTimeString(
						env.getProperty(DATETIME_PATTERN));
				requestWrapper.setRequesttime(
						LocalDateTime.parse(currentDateTimeStr, capturedFormatter));
				requestWrapper.setVersion(APPLICATION_VERSION);

				HttpEntity<RequestWrapper<AuditRequestDto>> httpEntity = new HttpEntity<>(requestWrapper);
				ResponseEntity<String> response = restTemplate.exchange(
						auditLogUrl, HttpMethod.POST, httpEntity, String.class);

				LOGGER.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(),
						id, "AuditLogEntry::addAudit::exit");

				return response.getBody();

			} catch (Exception e) {
				LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID,
						id, ExceptionUtils.getStackTrace(e));
				return null;
			}
		}, auditExecutor);
	}
}