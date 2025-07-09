package io.mosip.commons.packet.facade;

import io.mosip.commons.packet.exception.NoAvailableProviderException;
import io.mosip.commons.packet.spi.IPacketReader;
import io.mosip.commons.packet.util.PacketHelper;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.kernel.core.logger.spi.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PacketReaderProviderRegistry {
    private static final Logger LOGGER = PacketManagerLogger.getLogger(PacketReaderProviderRegistry.class);

    private final List<IPacketReader> referenceReaderProviders;
    private final Map<String, IPacketReader> providerCache = new ConcurrentHashMap<>();
    private static final String UNDERSCORE = "_";
    
    public PacketReaderProviderRegistry(@Qualifier("referenceReaderProviders") @Lazy List<IPacketReader> referenceReaderProviders) {
        this.referenceReaderProviders = referenceReaderProviders;
    }

    public IPacketReader getReaderProvider(String source, String process) {
        String cacheKey = source + UNDERSCORE + process;

        return providerCache.computeIfAbsent(cacheKey, key -> {
            Optional<IPacketReader> provider = referenceReaderProviders.stream()
                    .filter(pr -> PacketHelper.isSourceAndProcessPresent(
                            pr.getClass().getName(), source, process, PacketHelper.Provider.READER))
                    .findFirst();

            if (provider.isEmpty()) {
                LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, null,
                        "No available IPacketReader provider for source={} and process={}", source, process);
                throw new NoAvailableProviderException();
            }

            LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, null,
                    "Cached IPacketReader for source={} and process={}", source, process);
            return provider.get();
        });
    }

    public void clearCache() {
        providerCache.clear();
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, null,
                "Provider cache cleared");
    }
}