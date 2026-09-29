package com.na.syslog.config;

import com.na.syslog.OperationLogAspect;
import com.na.syslog.utils.LogEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * na-log 的 Spring Boot 自动装配入口。
 *
 * <p>na-log 不依赖 na-common，所有 Bean 都由本类注册。
 *
 * <p><b>为什么注册走 BeanDefinitionRegistryPostProcessor 而不是 @Bean 方法</b>：
 * {@code @ConditionalOnMissingBean} 的让位依赖一个前提 —— 自动装配通过
 * {@code DeferredImportSelector} 在所有用户配置之后处理。但本类带
 * {@code @Configuration} 元注解，宿主的组件扫描会把它当成普通配置类拾取；
 * 一旦被拾取，它就与宿主的 {@code @Bean} 进入同一处理批次，顺序不再确定，于是：
 * <ul>
 *   <li>宿主声明同名 Bean → 启动直接抛 {@code BeanDefinitionOverrideException}；
 *       （Spring Boot 2.1+ 默认禁止 Bean 覆盖）</li>
 *   <li>宿主用异名 Bean 提供同类型实现 → 不报错，两份实现静默共存，
 *       切面会重复织入、同一次请求被记录两遍。</li>
 * </ul>
 *
 * <p>注意不能用 {@code ImportBeanDefinitionRegistrar} 代替：它在
 * {@code ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForConfigurationClass}
 * 内部**逐个配置类**调用，若本类排在宿主配置类之前，注册仍然会先发生。
 * 只有 {@code BeanDefinitionRegistryPostProcessor} 是在
 * {@code ConfigurationClassPostProcessor} 处理完**全部**配置类之后才被触发，
 * 此时宿主提供的 Bean 定义一定已经就位。
 */
@Slf4j
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(AutoLogConfig.class)
public class LogAutoConfiguration {

    /** 日志事件发布器的 Bean 名 */
    public static final String BEAN_LOG_EVENT_UTIL = "logEventUtil";
    /** 采集切面的 Bean 名 */
    public static final String BEAN_OPERATION_LOG_ASPECT = "operationLogAspect";
    /** 日志线程池的 Bean 名，宿主声明同名 Bean 即可整体替换 */
    public static final String BEAN_SYS_LOG_THREAD_POOL = "sysLogThreadPool";

    /**
     * 注册时机控制器。用 static @Bean 声明，避免配置类因此被提前实例化。
     *
     * @return Bean 注册器
     */
    @Bean
    static BeanDefinitionRegistryPostProcessor naLogBeanRegistrar() {
        return new NaLogBeanRegistrar();
    }

    /**
     * 在全部配置类处理完后注册 na-log 的 Bean，并让宿主提供的实现优先。
     */
    static class NaLogBeanRegistrar implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            registerIfAbsentByType(registry, BEAN_LOG_EVENT_UTIL, LogEventUtil.class);
            registerIfAbsentByType(registry, BEAN_OPERATION_LOG_ASPECT, OperationLogAspect.class);

            // 线程池只按 Bean 名判断：宿主通常还有其它用途的 Executor，
            // 按类型让位会因为一个无关的线程池而整个跳过注册。
            if (!registry.containsBeanDefinition(BEAN_SYS_LOG_THREAD_POOL)) {
                registry.registerBeanDefinition(BEAN_SYS_LOG_THREAD_POOL,
                        BeanDefinitionBuilder
                                .genericBeanDefinition(ThreadPoolTaskExecutor.class,
                                        LogAutoConfiguration::createThreadPool)
                                .setDestroyMethodName("destroy")
                                .getBeanDefinition());
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            // 无需在 BeanFactory 层做额外处理
        }

        /**
         * 容器里已有该类型的 Bean 时跳过注册。依赖由 Spring 按构造器解析。
         *
         * @param registry 注册表
         * @param beanName 要注册的 Bean 名
         * @param type     Bean 类型
         */
        private static void registerIfAbsentByType(BeanDefinitionRegistry registry,
                                                   String beanName,
                                                   Class<?> type) {
            if (registry instanceof ListableBeanFactory beanFactory
                    && beanFactory.getBeanNamesForType(type, false, false).length > 0) {
                return;
            }
            registry.registerBeanDefinition(beanName,
                    BeanDefinitionBuilder.genericBeanDefinition(type)
                            .setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR)
                            .getBeanDefinition());
        }
    }

    /**
     * 创建日志落库专用线程池，仅供 {@code @Async("sysLogThreadPool")} 使用。
     *
     * <p>注意这不是 Spring Boot 的默认 {@code applicationTaskExecutor}，没写限定符的
     * {@code @Async} 仍然走 Boot 自己的线程池，两者排队与拒绝行为不同。
     *
     * @return 日志专用线程池
     */
    static ThreadPoolTaskExecutor createThreadPool() {
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
