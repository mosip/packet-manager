package io.mosip.commons.packet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

@Configuration
public class AuditAsyncConfig {

    @Value("${packetmanager.audit.thread.pool.size:5}")
    private int auditPoolSize;

    @Value("${packetmanager.fetch.thread.pool.size:60}")
    private int fetchPoolSize;

    private ExecutorService auditPool;
    private ExecutorService fetchPool;

    /**
     * Fixed platform-thread pool for fire-and-forget audit HTTP calls.
     * Threads are REUSED across requests — no per-task thread creation.
     * Daemon threads so they don't block JVM shutdown.
     */
    @Bean(name = "auditTaskExecutor")
    public ExecutorService auditTaskExecutor() {
        auditPool = Executors.newFixedThreadPool(auditPoolSize,
                Thread.ofPlatform().name("audit-", 0).daemon(true).factory());
        return auditPool;
    }

    /**
     * Fixed platform-thread pool for parallel sub-packet S3 fetches.
     * Replaces virtual-thread-per-task executor to avoid:
     *  - Carrier thread pinning from AWS SDK v1 synchronized blocks
     *  - Carrier thread accumulation (up to maxPoolSize) over hours
     *  - ThreadLocal memory leaks per virtual thread in AWS/Jedis/Spring libs
     *
     * Platform threads are reused: stable memory, no per-task overhead.
     * Pool size 30 supports 10 concurrent getAll() calls × 3 sub-packets each.
     * Actual concurrency is further controlled by fetchSemaphore in PacketReaderImpl.
     */
    @Bean(name = "packetFetchExecutor")
    public ExecutorService packetFetchExecutor() {
        fetchPool = Executors.newFixedThreadPool(fetchPoolSize,
                Thread.ofPlatform().name("pkt-fetch-", 0).daemon(true).factory());
        return fetchPool;
    }

    @PreDestroy
    public void shutdown() {
        if (auditPool != null) auditPool.shutdownNow();
        if (fetchPool != null) fetchPool.shutdownNow();
    }

}
