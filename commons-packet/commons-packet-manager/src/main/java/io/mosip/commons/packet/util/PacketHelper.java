package io.mosip.commons.packet.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.mosip.commons.packet.dto.packet.ProviderDto;
import io.mosip.commons.packet.exception.NoAvailableProviderException;

public class PacketHelper {

	private static final String SOURCE = "source";
	private static final String PROCESS = "process";
	private static final String CLASSNAME = "classname";
	private static final String DASH = "-";

	private static List<ProviderDto> readerProvider = null;
	private static List<ProviderDto> writerProvider = null;

	public enum Provider {
		READER, WRITER;
	}

	/**
	 * The providerConfig.
	 */
	private static Map<String, String> readerConfiguration;
	/**
	 * The providerConfig.
	 */
	private static Map<String, String> writerConfiguration;

	public static Set<String> getReaderProvider(Map<String, String> config) {
		readerConfiguration = config;
		return getProviderClassNames(getReader(config));
	}

	public static Set<String> getWriterProvider(Map<String, String> config) {
		writerConfiguration = config;
		return getProviderClassNames(getWriter(config));
	}

	public static boolean isSourceAndProcessPresent(String providerName, String providerSource, String providerProcess,
			Provider type) {
		List<ProviderDto> configurations = switch (type) {
		case READER -> getReader(readerConfiguration);
		case WRITER -> getWriter(writerConfiguration);
		};

		if (configurations == null || configurations.isEmpty()) {
			System.out.println("No provider configurations found for type: " + type);
			throw new NoAvailableProviderException();
		}

		String process = getProcessWithoutIteration(providerProcess);
		String baseProviderName = getBaseClassName(providerName);
		boolean matchFound = configurations.stream()
				.anyMatch(dto -> StringUtils.containsIgnoreCase(dto.getSource(), providerSource)
						&& StringUtils.containsIgnoreCase(dto.getProcess(), process)
						&& StringUtils.containsIgnoreCase(baseProviderName, dto.getClassName()));

		return matchFound;
	}

	private static String getBaseClassName(String providerName) {
		int idx = providerName.indexOf("$$");
		return idx > 0 ? providerName.substring(0, idx) : providerName;
	}

	private static List<ProviderDto> getReader(Map<String, String> config) {
		if (readerProvider == null) {
			readerProvider = parseConfiguration(config);
		}
		return readerProvider;
	}

	private static List<ProviderDto> getWriter(Map<String, String> config) {
		if (writerProvider == null) {
			writerProvider = parseConfiguration(config);
		}
		return writerProvider;
	}

	private static List<ProviderDto> parseConfiguration(Map<String, String> config) {
	    List<ProviderDto> providers = new ArrayList<>();
	    if (config != null && !config.isEmpty()) {
	        for (String value : config.values()) {
	            ProviderDto dto = new ProviderDto();
	            for (String token : value.split(",")) {
	                token = token.trim(); // Trim whitespace
	                if (token.startsWith(SOURCE + ":")) {
	                    dto.setSource(token.substring((SOURCE + ":").length()).trim());
	                } else if (token.startsWith(PROCESS + ":")) {
	                    dto.setProcess(token.substring((PROCESS + ":").length()).trim());
	                } else if (token.startsWith(CLASSNAME + ":")) {
	                    dto.setClassName(token.substring((CLASSNAME + ":").length()).trim());
	                }
	            }
	            providers.add(dto);
	        }
	    }
	    return providers;
	}

	private static Set<String> getProviderClassNames(List<ProviderDto> providers) {
		return providers == null ? Collections.emptySet()
				: providers.stream().map(ProviderDto::getClassName).collect(Collectors.toSet());
	}

	/**
	 * This method returns process without iteration. It search iteration pattern at
	 * the end of the process string. If found then this method removes iteration
	 * and only returns process.
	 *
	 * @param process
	 * @return
	 */
	public static String getProcessWithoutIteration(String process) {
		if (StringUtils.isNotEmpty(process)) {
			// if number is present at the end preceded by '-' (DASH)
			String[] processArr = process.split(DASH);
			String lastElement = processArr[processArr.length - 1];
			if (StringUtils.isNumeric(lastElement)) {
				StringBuffer sb = new StringBuffer();
				sb.append(DASH);
				sb.append(lastElement);
				process = process.replace(sb.toString(), "");
			}
		}
		return process;
	}
}