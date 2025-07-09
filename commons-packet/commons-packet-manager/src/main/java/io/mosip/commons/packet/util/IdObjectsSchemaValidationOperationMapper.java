package io.mosip.commons.packet.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.mosip.kernel.core.logger.spi.Logger;

@Component
public class IdObjectsSchemaValidationOperationMapper {
	/*
	 * @Value("${mosip.kernel.applicant.type.age.limit}") private String ageLimit;
	 */

	/** The reg proc logger. */
	private static Logger LOGGER = PacketManagerLogger.getLogger(IdObjectsSchemaValidationOperationMapper.class);
	private static final String ENTRY_LOG = "IdObjectsSchemaValidationOperationMapper::getOperation()::entry";
	private static final String EXIT_LOG_PREFIX = "IdObjectsSchemaValidationOperationMapper::getOperation()::exit-";

	enum SyncTypeDto {

		/** The new registration. */
		NEW("NEW"),

		/** The update uin. */
		UPDATE("UPDATE"),

		/** The lost uin. */
		LOST("LOST"),

		/** The activate uin. */
		ACTIVATED("ACTIVATED"),

		/** The deactivate uin. */
		DEACTIVATED("DEACTIVATED"),

		/** The res update. */
		RES_UPDATE("RES_UPDATE"),

		/** The res re-print. */
		RES_REPRINT("RES_REPRINT");

		/** The value. */
		private final String value;

		/**
		 * Instantiates a new sync type dto.
		 *
		 * @param value the value
		 */
		private SyncTypeDto(String value) {
			this.value = value;
		}

		/**
		 * Gets the value.
		 *
		 * @return the value
		 */
		public String getValue() {
			return this.value;
		}

	}

	enum IdObjectValidatorSupportedOperations {
		NEW_REGISTRATION("new-registration"),

		CHILD_REGISTRATION("child-registration"),

		OTHER("other"),

		LOST("lost");

		private String operation;

		IdObjectValidatorSupportedOperations(String operation) {
			this.operation = operation;
		}

		public String getOperation() {
			return operation;
		}
	}

	private static final Map<String, String> PROCESS_TO_OPERATION;

	static {
		Map<String, String> map = new HashMap<>();
		map.put(SyncTypeDto.NEW.getValue(), IdObjectValidatorSupportedOperations.NEW_REGISTRATION.getOperation());
		map.put(SyncTypeDto.LOST.getValue(), IdObjectValidatorSupportedOperations.LOST.getOperation());
		map.put(SyncTypeDto.UPDATE.getValue(), IdObjectValidatorSupportedOperations.OTHER.getOperation());
		map.put(SyncTypeDto.RES_UPDATE.getValue(), IdObjectValidatorSupportedOperations.OTHER.getOperation());
		map.put(SyncTypeDto.ACTIVATED.getValue(), IdObjectValidatorSupportedOperations.OTHER.getOperation());
		map.put(SyncTypeDto.DEACTIVATED.getValue(), IdObjectValidatorSupportedOperations.OTHER.getOperation());
		PROCESS_TO_OPERATION = Collections.unmodifiableMap(map);
	}

	public static String getOperation(String process) {
		LOGGER.debug(PacketManagerLogger.SESSIONID.toString(), PacketManagerLogger.REGISTRATIONID.toString(), "",
				ENTRY_LOG);

		String normalizedProcess = process.toUpperCase();
		String operation = PROCESS_TO_OPERATION.get(normalizedProcess);

		if (operation != null) {
			LOGGER.debug(PacketManagerLogger.SESSIONID.toString(), PacketManagerLogger.REGISTRATIONID.toString(), "",
					EXIT_LOG_PREFIX + normalizedProcess);
			return operation;
		}

		// fallback to legacy logic
		String fallback = PacketHelper.getProcessWithoutIteration(process).toLowerCase();
		LOGGER.debug(PacketManagerLogger.SESSIONID.toString(), PacketManagerLogger.REGISTRATIONID.toString(), "",
				EXIT_LOG_PREFIX + "FALLBACK-" + fallback);
		return fallback;
	}
}