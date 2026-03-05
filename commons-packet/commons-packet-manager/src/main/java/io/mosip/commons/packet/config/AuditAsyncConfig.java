package io.mosip.commons.packet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AuditAsyncConfig {

    /**
     * Tune these via application.properties — no code changes needed.
     *
     * audit.executor.core-pool-size=4
     * audit.executor.max-pool-size=16
     * audit.executor.queue-capacity=500
     * audit.executor.keep-alive-seconds=60
     */
    @Value("${audit.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${audit.executor.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${audit.executor.queue-capacity:300}")
    private int queueCapacity;

    @Value("${audit.executor.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("audit-exec-");

        // CallerRunsPolicy: if the queue is full, the submitting thread
        // executes the task itself rather than dropping it silently.
        // This applies back-pressure instead of losing audit events.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Wait up to 30 s on shutdown for in-flight audit calls to finish.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

    @Bean(name = "packetFetchExecutor")
    public Executor packetFetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("audit-exec-");

        // CallerRunsPolicy: if the queue is full, the submitting thread
        // executes the task itself rather than dropping it silently.
        // This applies back-pressure instead of losing audit events.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Wait up to 30 s on shutdown for in-flight audit calls to finish.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

}