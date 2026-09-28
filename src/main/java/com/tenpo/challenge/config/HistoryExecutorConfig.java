package com.tenpo.challenge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

@Configuration
@EnableAsync
@EnableConfigurationProperties(HistoryProperties.class)
public class HistoryExecutorConfig {

    public static final String HISTORY_EXECUTOR = "historyExecutor";

    private static final Logger log = LoggerFactory.getLogger(HistoryExecutorConfig.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Dedicated pool for the call history inserts, with a bounded queue. When the queue is full
     * the record is dropped with a WARN instead of blocking or failing the caller. On shutdown it
     * waits for the pending records.
     */
    @Bean(HISTORY_EXECUTOR)
    public ThreadPoolTaskExecutor historyExecutor(HistoryProperties properties) {
        HistoryProperties.Executor settings = properties.executor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("history-");
        executor.setCorePoolSize(settings.corePoolSize());
        executor.setMaxPoolSize(settings.maxPoolSize());
        executor.setQueueCapacity(settings.queueCapacity());
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Call history queue is full ({} pending); dropping the record", pool.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(SHUTDOWN_TIMEOUT.toMillis());
        return executor;
    }
}
