package io.mosip.commons.packet.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class PacketReaderAndWriterConfig {

	@Bean(name = "readerConfiguration")
	@ConfigurationProperties(prefix = "provider.packetreader")
	public Map<String, String> readerConfiguration() {
		return new HashMap<>();
	}

	@Bean(name = "writerConfiguration")
	@ConfigurationProperties(prefix = "provider.packetwriter")
	public Map<String, String> writerConfiguration() {
		return new HashMap<>();
	}
}
