package com.na.syslog.config;

import com.na.syslog.OperationLogAspect;
import com.na.syslog.utils.LogEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * na-log 的 Spring Boot 自动装配入口。
 *
 * <p>na-log 不依赖 na-common，所有 Bean 都由本类注册：原先三个配置类都带
 * @Component/@Configuration，靠宿主的 {@code @ComponentScan("com.na")} 才能装配，
 * 宿主包名不同时模块会静默失效。改走自动装配后不再有这个约束。
 *
 * <p>同理，na-log 自身不再有任何可被组件扫描命中的类，因此"宿主保留
 * {@code @ComponentScan("com.na")}"与自动装配可以共存，不会重复注册 Bean。
 *
 * <p>各 @Bean 都带 @ConditionalOnMissingBean，宿主可以自行提供同类型/同名的
 * Bean 覆盖默认实现。
 */
@Slf4j
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(AutoLogConfig.class)
public class LogAutoConfiguration {

    /**
     * 注册日志事件发布器。
     *
     * @param applicationContext Spring 上下文
     * @return 事件发布器
     */
    @Bean
    @ConditionalOnMissingBean
    public LogEventUtil logEventUtil(ApplicationContext applicationContext) {
        return new LogEventUtil(applicationContext);
    }

    /**
     * 注册操作日志采集切面。
     *
     * @param autoLogConfig      na.log 配置项
     * @param logEventUtil       日志事件发布器
     * @param applicationContext Spring 上下文，用于按注解指定的类型查找 Formatter
     * @return 采集切面
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationLogAspect operationLogAspect(AutoLogConfig autoLogConfig,
                                                 LogEventUtil logEventUtil,
                                                 ApplicationContext applicationContext) {
        return new OperationLogAspect(autoLogConfig, logEventUtil, applicationContext);
    }

    /**
     * 日志落库专用线程池，仅供 {@code @Async("sysLogThreadPool")} 使用。
     *
     * <p>注意这不是 Spring Boot 的默认 {@code applicationTaskExecutor}，没写限定符的
     * {@code @Async} 仍然走 Boot 自己的线程池，两者排队与拒绝行为不同。
     *
     * @return 名为 sysLogThreadPool 的线程池
     */
    @Bean(name = "sysLogThreadPool")
    @ConditionalOnMissingBean(name = "sysLogThreadPool")
    public Executor sysLogThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("sysLogThreadPool-");
        // 显式声明拒绝策略：默认的 AbortPolicy 会抛异常，而日志系统在过载时
        // 应该丢日志而不是给业务添乱，同时丢了多少必须可见。
        executor.setRejectedExecutionHandler(new LogDiscardPolicy());
        // 无需手动 initialize()：作为容器管理的 Bean，afterPropertiesSet() 会被自动调用
        return executor;
    }

    /**
     * 线程池饱和时的拒绝策略：丢弃本次日志事件并告警，绝不阻塞业务线程。
     *
     * <p>告警按秒节流 —— 过载时若每条丢弃都打一行日志，告警本身会变成新的负载。
     * 累计丢弃数会随告警一起输出，便于判断过载规模。
     */
    private static class LogDiscardPolicy implements RejectedExecutionHandler {

        private final AtomicLong discarded = new AtomicLong();
        private final AtomicLong lastWarnAt = new AtomicLong();

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            long total = discarded.incrementAndGet();
            long now = System.currentTimeMillis();
            long last = lastWarnAt.get();
            if (now - last >= 1000L && lastWarnAt.compareAndSet(last, now)) {
                log.warn("操作日志线程池已满，丢弃日志事件（累计丢弃 {} 条）", total);
            }
        }
    }
}
