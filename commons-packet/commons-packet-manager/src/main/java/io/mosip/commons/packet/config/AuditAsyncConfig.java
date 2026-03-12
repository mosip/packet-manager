package io.mosip.commons.packet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AuditAsyncConfig {

    /**
     * Virtual-thread executor for fire-and-forget audit HTTP calls.
     * Uses Java 21 Executors.newVirtualThreadPerTaskExecutor() directly —
     * bypasses Spring's compatibility wrapper and its spring-core version checks.
     * Each audit task gets its own virtual thread; no pool sizing needed.
     */
    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Virtual-thread executor for parallel sub-packet S3 fetches.
     * Each sub-packet fetch gets its own virtual thread and unmounts
     * from the carrier thread during blocking I/O (network + decrypt),
     * giving true parallelism without holding OS threads.
     */
    @Bean(name = "packetFetchExecutor")
    public Executor packetFetchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

}
