package io.mosip.commons.packet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AuditAsyncConfig {

    /**
     * Virtual-thread executor for fire-and-forget audit HTTP calls.
     * Each task gets its own virtual thread — no pool sizing needed.
     * Virtual threads unmount from the carrier thread during blocking I/O,
     * so hundreds of concurrent audit calls cost almost nothing.
     */
    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("audit-exec-");
        executor.setVirtualThreads(true);
        return executor;
    }

    /**
     * Virtual-thread executor for parallel sub-packet S3 fetches.
     * With virtual threads, all sub-packets are fetched truly in parallel
     * without holding OS threads during network/decrypt wait time.
     * No pool size or queue capacity to tune.
     */
    @Bean(name = "packetFetchExecutor")
    public Executor packetFetchExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("packet-fetch-");
        executor.setVirtualThreads(true);
        return executor;
    }

}
