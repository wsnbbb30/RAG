package com.yizhaoqi.smartpai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 检索专用的有界线程池，避免外部 Embedding 的慢请求占用 Web/Kafka 工作线程。 */
@Configuration
public class RetrievalExecutorConfig {
    @Bean("retrievalExecutor")
    public Executor retrievalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("retrieval-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
