package com.ai.recruitmentai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);
    public static final String CV_PARSING_EXECUTOR_BEAN_NAME = "cvParsingExecutor";
    @Bean(name = CV_PARSING_EXECUTOR_BEAN_NAME)
    public Executor cvParsingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = 4;
        log.info("configuring CV Parsing Executor with Core Pool Size: {}", corePoolSize);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(corePoolSize);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("CVParsing-");
        executor.initialize();
        return executor;
    }
}