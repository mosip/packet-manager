package io.mosip.commons.packetmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * The Class RegistrationStatusConfig.
 */
@Configuration
public class PacketSwaggerConfig {

	/**
	 * Registration status bean.
	 */
	@Bean
	@Primary
	public OpenAPI swaggerBean() {
		return new OpenAPI()
				.components(new Components())
				.info(new Info().title("Commons-packet Service API documentation").description(
						"Commons-packet Service API documentation").version("3.0.1"));
	}

}
