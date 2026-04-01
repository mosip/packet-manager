package io.mosip.commons.packet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

import jakarta.annotation.PreDestroy;

@Configuration
public class AuditAsyncConfig {

    @Value("${packetmanager.audit.thread.pool.size:5}")
    private int auditPoolSize;

    @Value("${packetmanager.fetch.thread.pool.size:40}")
    private int fetchPoolSize;

    @Value("${packetmanager.fetch.queue.capacity:200}")
    private int fetchQueueCapacity;

    @Value("${packetmanager.audit.thread.queue.capacity:50}")
    private int auditQueueCapacity;

    @Value("${packetmanager.validate.thread.pool.size:30}")
    private int validatePoolSize;

    @Value("${packetmanager.validate.queue.capacity:150}")
    private int validateQueueCapacity;

    private ExecutorService auditPool;
    private ExecutorService fetchPool;
    private ExecutorService validatePool;

    /**
     * Fixed platform-thread pool for fire-and-forget audit HTTP calls.
     * Daemon threads: audit is best-effort, losing a few entries on pod
     * shutdown is acceptable. Daemon avoids blocking JVM exit for audit work.
     */
    @Bean(name = "auditTaskExecutor")
    public ExecutorService auditTaskExecutor() {
        auditPool = new ThreadPoolExecutor(
                auditPoolSize,
                fetchPoolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(auditQueueCapacity),
                Thread.ofPlatform().name("pkt-audit-", 0).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return auditPool;
    }

    /**
     * Bounded platform-thread pool for parallel sub-packet S3 fetches (reads).
     * CallerRunsPolicy: when the queue is full the HTTP thread executes the task itself,
     * providing natural backpressure — no Tomcat thread blocks waiting for a permit,
     * and the queue never grows unbounded.
     *
     * Tune via:
     *   packetmanager.fetch.thread.pool.size    (default 40)  — concurrent S3 fetches
     *   packetmanager.fetch.queue.capacity      (default 200) — max queued tasks before CallerRuns kicks in
     */
    @Bean(name = "packetFetchExecutor")
    public ExecutorService packetFetchExecutor() {
        fetchPool = new ThreadPoolExecutor(
                fetchPoolSize,
                fetchPoolSize,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(fetchQueueCapacity),
                Thread.ofPlatform().name("pkt-fetch-", 0).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return fetchPool;
    }

    /**
     * Bounded platform-thread pool for parallel sub-packet S3 fetches during validation.
     * Separate from packetFetchExecutor so validate concurrency can be tuned independently.
     * CallerRunsPolicy provides backpressure when the queue is full.
     *
     * Tune via:
     *   packetmanager.validate.thread.pool.size  (default 30)  — concurrent validate fetches
     *   packetmanager.validate.queue.capacity    (default 150) — max queued tasks before CallerRuns kicks in
     */
    @Bean(name = "packetValidateExecutor")
    public ExecutorService packetValidateExecutor() {
        validatePool = new ThreadPoolExecutor(
                validatePoolSize,
                validatePoolSize,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(validateQueueCapacity),
                Thread.ofPlatform().name("pkt-validate-", 0).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return validatePool;
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

        if (validatePool != null) {
            validatePool.shutdown();
            try {
                if (!validatePool.awaitTermination(30, TimeUnit.SECONDS))
                    validatePool.shutdownNow();
            } catch (InterruptedException e) {
                validatePool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

}
