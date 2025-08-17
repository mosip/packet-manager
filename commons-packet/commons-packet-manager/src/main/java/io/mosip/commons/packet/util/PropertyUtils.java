package io.mosip.commons.packet.util;

import io.mosip.kernel.core.logger.spi.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PropertyUtils {

    private static final Logger LOGGER = PacketManagerLogger.getLogger(PropertyUtils.class);

    @Value("${packetmanager.cache.info.enabled:false}")
    private boolean isInfoCacheEnabled;

    public boolean isInfoCacheEnabled() {
        LOGGER.info("isInfoCacheEnabled : {}", isInfoCacheEnabled);
        return isInfoCacheEnabled;
    }
}
