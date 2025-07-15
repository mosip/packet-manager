package io.mosip.commons.packet.util;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.logger.logback.factory.Logfactory;

/**
 * Utility class to provide pre-defined log keys and logger instance for Packet Manager.
 */
public final class PacketManagerLogger {

    public static final String SESSIONID = "SESSION_ID";
    public static final String REGISTRATIONID = "REGISTRATION_ID";
    public static final String REFERENCEID = "REFERENCE_ID";

    /**
     * Private constructor to prevent instantiation.
     */
    private PacketManagerLogger() {
        // Prevent instantiation
    }

    /**
     * Returns a logger for the given class using SLF4J implementation.
     *
     * @param clazz the class for which the logger is to be created
     * @return the logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        return Logfactory.getSlf4jLogger(clazz);
    }
}