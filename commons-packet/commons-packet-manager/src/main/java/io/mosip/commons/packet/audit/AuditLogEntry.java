package io.mosip.commons.packet.audit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
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

	private static final Logger LOGGER =
			PacketManagerLogger.getLogger(AuditLogEntry.class);

	private static final String AUDIT_SERVICE_ID =
			"mosip.commons.packet.manager";

	private static final String APPLICATION_VERSION = "v1";

	private static final String DATETIME_PATTERN =
			"mosip.utc-datetime-pattern";

	@Autowired
	@Lazy
	@Qualifier("selfTokenRestTemplate")
	private RestTemplate restTemplate;

	@Autowired
	private Environment env;

	@Autowired
	@Qualifier("auditTaskExecutor")
	private Executor auditExecutor;

	@Value("${AUDIT_URL:null}")
	private String auditLogUrl;

	private DateTimeFormatter dateTimeFormatter;
	private String dateTimePattern;

	private String serverIp;
	private String serverName;

	@PostConstruct
	private void init() {

		dateTimePattern = env.getProperty(DATETIME_PATTERN);

		dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimePattern);

		ServerUtil serverUtil = ServerUtil.getServerUtilInstance();

		serverIp = serverUtil.getServerIp();
		serverName = serverUtil.getServerName();
	}

	public CompletableFuture<String> addAudit(String description,
											  String eventId,
											  String eventName,
											  String eventType,
											  String moduleId,
											  String moduleName,
											  String id) {

		LOGGER.debug(LoggerFileConstant.SESSIONID.toString(),
				LoggerFileConstant.ID.toString(),
				id,
				"AuditLogEntry::addAudit::async submitted");

		return CompletableFuture.supplyAsync(() -> {

			try {

				AuditRequestDto auditRequestDto = new AuditRequestDto();

				auditRequestDto.setDescription(description);
				auditRequestDto.setActionTimeStamp(
						DateUtils2.getUTCCurrentDateTimeString());

				auditRequestDto.setApplicationId(
						LoggerFileConstant.MOSIP_4.toString());

				auditRequestDto.setApplicationName(
						LoggerFileConstant.PACKET_MANAGER.toString());

				auditRequestDto.setCreatedBy(
						LoggerFileConstant.SYSTEM.toString());

				auditRequestDto.setEventId(eventId);
				auditRequestDto.setEventName(eventName);
				auditRequestDto.setEventType(eventType);

				auditRequestDto.setHostIp(serverIp);
				auditRequestDto.setHostName(serverName);

				auditRequestDto.setId(id);
				auditRequestDto.setIdType(
						LoggerFileConstant.ID.toString());

				auditRequestDto.setModuleId(moduleId);
				auditRequestDto.setModuleName(moduleName);

				auditRequestDto.setSessionUserId(
						LoggerFileConstant.SYSTEM.toString());

				RequestWrapper<AuditRequestDto> requestWrapper =
						new RequestWrapper<>();

				requestWrapper.setId(AUDIT_SERVICE_ID);
				requestWrapper.setRequest(auditRequestDto);

				String currentDateTimeStr =
						DateUtils2.getUTCCurrentDateTimeString(dateTimePattern);

				requestWrapper.setRequesttime(
						LocalDateTime.parse(currentDateTimeStr,
								dateTimeFormatter));

				requestWrapper.setVersion(APPLICATION_VERSION);

				HttpEntity<RequestWrapper<AuditRequestDto>> httpEntity =
						new HttpEntity<>(requestWrapper);

				ResponseEntity<String> response =
						restTemplate.exchange(
								auditLogUrl,
								HttpMethod.POST,
								httpEntity,
								String.class
						);

				return response.getBody();

			} catch (Exception e) {

				LOGGER.error(PacketManagerLogger.SESSIONID,
						PacketManagerLogger.REGISTRATIONID,
						id,
						ExceptionUtils.getStackTrace(e));

				return null;
			}

		}, auditExecutor);
	}
}