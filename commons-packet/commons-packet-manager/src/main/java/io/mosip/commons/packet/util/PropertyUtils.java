package io.mosip.commons.packet.util;

import io.mosip.kernel.core.logger.spi.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PropertyUtils {

    private static final Logger LOGGER = PacketManagerLogger.getLogger(PropertyUtils.class);
    /*
     * Note on enabling info cache:
     *
     * Previously, issues were observed when the info cache was not cleared
     * while processing a BIOMETRIC_CORRECTION packet for a given RID, since
     * the correction packet is uploaded with the same RID.
     *
     * In production, we can enable the info cache for performance improvements,
     * provided the following assumptions hold true:
     *
     * 1. By the time a BIOMETRIC_CORRECTION packet is uploaded, the info cache
     *    for that RID will already be cleared. This is because, in production,
     *    BIOMETRIC_CORRECTION packets are typically uploaded after some time,
     *    and the cache duration is relatively short — reducing the risk of stale data.
     *
     * 2. Packet data is not modified "in-flight" (i.e., during processing),
     *    ensuring that caching will not introduce inconsistencies.
     */
    @Value("${packetmanager.cache.info.enabled:false}")
    private boolean isInfoCacheEnabled;

    public boolean isInfoCacheEnabled() {
        LOGGER.info("isInfoCacheEnabled : {}", isInfoCacheEnabled);
        return isInfoCacheEnabled;
    }
}
