package io.mosip.commons.packet.facade;

import io.mosip.commons.packet.exception.NoAvailableProviderException;
import io.mosip.commons.packet.spi.IPacketWriter;
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
public class PacketWriterProviderRegistry {
    private static final Logger LOGGER = PacketManagerLogger.getLogger(PacketWriterProviderRegistry.class);

    private final List<IPacketWriter> referenceWriterProviders;
    private final Map<String, IPacketWriter> providerCache = new ConcurrentHashMap<>();
    private static final String UNDERSCORE = "_";

    public PacketWriterProviderRegistry(@Qualifier("referenceWriterProviders") @Lazy List<IPacketWriter> referenceWriterProviders) {
        this.referenceWriterProviders = referenceWriterProviders;
    }

    public IPacketWriter getWriterProvider(String source, String process) {
        String cacheKey = source + UNDERSCORE + process;

        return providerCache.computeIfAbsent(cacheKey, key -> {
            Optional<IPacketWriter> provider = referenceWriterProviders.stream()
                    .filter(pr -> PacketHelper.isSourceAndProcessPresent(
                            pr.getClass().getName(), source, process, PacketHelper.Provider.WRITER))
                    .findFirst();

            if (provider.isEmpty()) {
                LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, null,
                        "No available IPacketWriter provider for source={} and process={}", source, process);
                throw new NoAvailableProviderException();
            }

            LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, null,
                    "Cached IPacketWriter for source={} and process={}", source, process);
            return provider.get();
        });
    }

    public void clearCache() {
        providerCache.clear();
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, null,
                "Writer provider cache cleared");
    }
}