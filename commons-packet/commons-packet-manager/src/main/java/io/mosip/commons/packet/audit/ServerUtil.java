package io.mosip.commons.packet.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerUtil {

	/** The Constant LOGGER. */
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerUtil.class);

	/** The host not found. */
	private static final String NO_HOST = "HOST_NOT_FOUND";

	private final String serverIp;
	private final String serverName;

	/**
	 * Instantiates a new server util and caches host info to avoid repeated lookups.
	 */
	private ServerUtil() {
		super();
		String ip = "UNKNOWN-HOST";
		String name = "UNKNOWN-HOST";
		try {
			InetAddress localHost = InetAddress.getLocalHost();
			ip = localHost.getHostAddress();
			name = localHost.getHostName();
		} catch (UnknownHostException e) {
			LOGGER.error(NO_HOST, e.getMessage());
		}
		this.serverIp = ip;
		this.serverName = name;
	}

	private static class Holder {
		static final ServerUtil INSTANCE = new ServerUtil();
	}

	/**
	 * Returns the singleton instance. Thread-safe without locking after class load.
	 *
	 * @return The ServerUtil object
	 */
	public static ServerUtil getServerUtilInstance() {
		return Holder.INSTANCE;
	}

	/**
	 * This method return ServerIp.
	 *
	 * @return The ServerIp
	 */
	public String getServerIp() {
		return serverIp;
	}

	/**
	 * This method return Server Host Name.
	 *
	 * @return The ServerName
	 */
	public String getServerName() {
		return serverName;
	}

}