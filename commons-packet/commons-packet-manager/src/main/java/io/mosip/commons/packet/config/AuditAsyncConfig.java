package io.mosip.commons.packet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
     * Daemon threads: audit is best-effort, losing a few entries on pod
     * shutdown is acceptable. Daemon avoids blocking JVM exit for audit work.
     */
    @Bean(name = "auditTaskExecutor")
    public ExecutorService auditTaskExecutor() {
        auditPool = Executors.newFixedThreadPool(auditPoolSize,
                Thread.ofPlatform().name("audit-", 0).factory());
        //auditPool = Executors.newFixedThreadPool(auditPoolSize,
          //      Thread.ofPlatform().name("audit-", 0).daemon(true).factory());
        return auditPool;
    }

    /**
     * Fixed platform-thread pool for parallel sub-packet S3 fetches.
     * NON-daemon threads: active packet downloads must complete before pod
     * shuts down, otherwise in-flight requests fail mid-stream.
     * Graceful shutdown (30s wait) in @PreDestroy ensures in-progress
     * S3 downloads finish before the JVM exits.
     *
     * Replaces virtual-thread-per-task executor to avoid:
     *  - Carrier thread pinning from AWS SDK v1 synchronized blocks
     *  - Carrier thread accumulation (up to maxPoolSize) over hours
     *  - ThreadLocal memory leaks per virtual thread in AWS/Jedis/Spring libs
     */
    @Bean(name = "packetFetchExecutor")
    public ExecutorService packetFetchExecutor() {
        fetchPool = Executors.newFixedThreadPool(fetchPoolSize,
                Thread.ofPlatform().name("pkt-fetch-", 0).factory());
        return fetchPool;
    }

    @PreDestroy
    public void shutdown() {
        // Audit: daemon threads die on JVM exit anyway, but explicit shutdown is clean
        if (auditPool != null) auditPool.shutdownNow();

        // Fetch: graceful — let in-progress S3 downloads complete (up to 30s),
        // then force-stop anything still running
        if (fetchPool != null) {
            fetchPool.shutdown();
            try {
                if (!fetchPool.awaitTermination(30, TimeUnit.SECONDS))
                    fetchPool.shutdownNow();
            } catch (InterruptedException e) {
                fetchPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

}
