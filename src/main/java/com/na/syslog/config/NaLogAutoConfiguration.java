package com.na.syslog.config;

import com.na.common.syslog.util.LogEventUtil;
import com.na.syslog.NaOperationLogAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * na-log 的 Spring Boot 自动装配入口。
 *
 * <p>原先 na-log 的三个配置类都带 @Component/@Configuration，靠宿主的
 * {@code @ComponentScan("com.na")} 才能装配。宿主包名不是 com.na 开头时模块会
 * 静默失效 —— 不报错、不记日志，{@code @NaOperationLog} 形同虚设。
 *
 * <p>改为标准自动装配后不再有这个约束。同时因为 na-log 自身不再有任何可被
 * 组件扫描命中的类，"宿主保留 @ComponentScan("com.na")"与"自动装配"可以共存，
 * 不会重复注册 Bean（na-common 没有 META-INF/spring，宿主仍必须扫描它）。
 *
 * <p>两个 @Bean 都带 @ConditionalOnMissingBean，宿主可以自行提供同类型/同名的
 * Bean 覆盖默认实现。
 */
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(NaAutoLogConfig.class)
public class NaLogAutoConfiguration {

    /**
     * 注册操作日志采集切面。
     *
     * @param naAutoLogConfig na.log 配置项
     * @param logEventUtil    na-common 的事件发布工具
     * @return 采集切面，Bean 名保持为 naOperationLogAspect 以兼容既有注入点
     */
    @Bean
    @ConditionalOnMissingBean
    public NaOperationLogAspect naOperationLogAspect(NaAutoLogConfig naAutoLogConfig,
                                                     LogEventUtil logEventUtil) {
        return new NaOperationLogAspect(naAutoLogConfig, logEventUtil);
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
        // 无需手动 initialize()：作为容器管理的 Bean，afterPropertiesSet() 会被自动调用
        return executor;
    }
}
