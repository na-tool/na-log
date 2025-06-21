package com.na.syslog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncSysLogThreadPoolConfig {

    @Bean(name = "sysLogThreadPool")
    public Executor sysLogThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // 核心线程数
        executor.setMaxPoolSize(10); // 最大线程数
        executor.setQueueCapacity(25); // 队列容量
        executor.setThreadNamePrefix("sysLogThreadPool-"); // 线程名称前缀
        executor.initialize();
        return executor;
    }
}

