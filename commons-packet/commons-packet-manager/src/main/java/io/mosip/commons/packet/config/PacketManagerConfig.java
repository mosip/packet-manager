package io.mosip.commons.packet.config;

import io.mosip.commons.packet.constants.LoggerFileConstant;
import io.mosip.commons.packet.spi.IPacketReader;
import io.mosip.commons.packet.spi.IPacketWriter;
import io.mosip.commons.packet.util.PacketHelper;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.kernel.core.logger.spi.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.CollectionUtils;


import jakarta.annotation.PostConstruct;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableCaching
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = {
		"io.mosip.kernel.cbeffutil.impl.CbeffImpl"}), basePackages = {"io.mosip.commons.packet.*", "io.mosip.commons.khazana.*",
        "io.mosip.kernel.cbeffutil.*", "io.mosip.kernel.auth.*","io.mosip.kernel.idobjectvalidator.*"})
@Import({ OfflineConfig.class })
public class PacketManagerConfig {

    private static final Logger logger = PacketManagerLogger.getLogger(PacketManagerConfig.class);

    @Autowired
    private ApplicationContext applicationContext;

	@Autowired
	@Qualifier("readerConfiguration")
	private Map<String, String> packetReaderConfig;

	@Autowired
	@Qualifier("writerConfiguration")
	private Map<String, String> packetWriterConfig;
    /**
     * Validate the reference provider.
     *
     * @throws ClassNotFoundException the class not found exception
     */
    @PostConstruct
    public void validateReferenceReaderProvider() throws ClassNotFoundException {
    	validateProviders(PacketHelper.getReaderProvider(packetReaderConfig), "reader");
    }

    /**
     * Validate the reference provider.
     *
     * @throws ClassNotFoundException the class not found exception
     */
    @PostConstruct
    public void validateReferenceWriterProvider() throws ClassNotFoundException {
    	validateProviders(PacketHelper.getWriterProvider(packetWriterConfig), "writer");
    }

    /**
     * Instantiate the reference provider bean
     *
     * @return the id object validator
     * @throws ClassNotFoundException the class not found exception
     * @throws InstantiationException the instantiation exception
     * @throws IllegalAccessException the illegal access exception
     */
    @Bean
    @Lazy
    public List<IPacketReader> referenceReaderProviders() throws ClassNotFoundException {
        return loadProviders(PacketHelper.getReaderProvider(packetReaderConfig), IPacketReader.class);
    }

    /**
     * Instantiate the reference provider bean
     *
     * @return the id object validator
     * @throws ClassNotFoundException the class not found exception
     * @throws InstantiationException the instantiation exception
     * @throws IllegalAccessException the illegal access exception
     */
    @Bean
    @Lazy
    public List<IPacketWriter> referenceWriterProviders() throws ClassNotFoundException {
        return loadProviders(PacketHelper.getWriterProvider(packetWriterConfig), IPacketWriter.class);
    }

    private void validateProviders(Set<String> classNames, String type) {
        if (CollectionUtils.isEmpty(classNames)) {
            logger.warn(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), null,
                    String.format("No reference provider %s classes provided.", type));
            return;
        }

        for (String className : classNames) {
            try {
                logger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), null,
                        String.format("Validating reference provider %s class: %s", type, className));
                getBean(className);
            } catch (RuntimeException e) {
                logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), null,
                        String.format("Invalid %s class %s: %s", type, className, e.getMessage()));
                throw e;
            }
        }
    }
    
    private <T> List<T> loadProviders(Set<String> classNames, Class<T> expectedType) {
        List<T> providers = new ArrayList<>();
        if (!CollectionUtils.isEmpty(classNames)) {
            for (String className : classNames) {
                Object bean = getBean(className);
                if (expectedType.isInstance(bean)) {
                    providers.add(expectedType.cast(bean));
                } else {
                    throw new IllegalArgumentException(String.format("Bean %s is not of expected type %s", className, expectedType.getSimpleName()));
                }
            }
        }
        return providers;
    }
    
    private Object getBean(String className) {
        try {
            if (className == null || className.trim().isEmpty()) {
                throw new ClassNotFoundException("Invalid class name: " + className);
            }
            className = className.replace(":", "").trim();
            Class<?> clazz = Class.forName(className.trim());
            return applicationContext.getBean(clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + className, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load bean: " + className, e);
        }
    }
}