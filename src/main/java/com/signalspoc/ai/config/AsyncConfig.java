package com.signalspoc.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "aiAnalysisExecutor")
    public Executor aiAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("ai-analysis-");
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated executor for AiEnrichmentWorker.
     * Single core thread keeps Ollama calls sequential (it's one model, one GPU).
     * Queue of 100 absorbs bursts without blocking the detection loop.
     */
    @Bean(name = "aiEnrichmentExecutor")
    public Executor aiEnrichmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-enrich-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "aiTaskScheduler")
    public ThreadPoolTaskScheduler aiTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ai-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
