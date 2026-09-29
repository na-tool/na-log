package com.na.syslog.config;

import com.na.syslog.OperationLogAspect;
import com.na.syslog.utils.LogEventUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配的加载与注册测试。
 */
class LogAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class));

    @Test
    @DisplayName("自动装配声明指向的类必须真实存在")
    void autoConfigurationImportsResolve() throws Exception {
        Enumeration<URL> resources = getClass().getClassLoader().getResources(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        List<String> candidates = new ArrayList<>();
        while (resources.hasMoreElements()) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resources.nextElement().openStream(), StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(candidates::add);
            }
        }

        // 声明里必须包含本模块的入口类：写成已改名的旧类名时这条会失败
        assertThat(candidates).contains(LogAutoConfiguration.class.getName());

        // 只加载本模块声明的候选。classpath 上其他 jar 的自动装配可能依赖本模块
        // 没有的可选依赖（如 Cassandra 驱动），校验它们不是本测试的职责。
        for (String candidate : candidates) {
            if (!candidate.startsWith("com.na.syslog")) {
                continue;
            }
            // 类名只能从字符串解析，编译器不会检查；写错会让宿主启动直接失败
            assertThat(Class.forName(candidate))
                    .as("自动装配声明 %s 无法加载", candidate)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("默认注册切面、事件发布器、配置项与线程池")
    void registersDefaultBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(OperationLogAspect.class);
            assertThat(context).hasSingleBean(LogEventUtil.class);
            assertThat(context).hasSingleBean(AutoLogConfig.class);
            assertThat(context).hasBean("sysLogThreadPool");
        });
    }

    @Test
    @DisplayName("宿主自带同类型 Bean 时自动装配让位")
    void backsOffWhenHostProvidesOwnBeans() {
        runner.withUserConfiguration(HostOverrideConfig.class).run(context -> {
            assertThat(context).hasSingleBean(OperationLogAspect.class);
            assertThat(context.getBean(OperationLogAspect.class))
                    .isSameAs(context.getBean("hostAspect"));
        });
    }

    @Test
    @DisplayName("配置项默认值：enabled=true、cn=false")
    void configDefaults() {
        runner.run(context -> {
            AutoLogConfig config = context.getBean(AutoLogConfig.class);
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.isCn()).isFalse();
        });
    }

    @Test
    @DisplayName("宿主声明同名 Bean 即可覆盖日志线程池（走自动装配路径）")
    void hostOverridesThreadPoolByName() {
        runner.withUserConfiguration(HostThreadPoolConfig.class).run(context -> {
            assertThat(context).hasBean("sysLogThreadPool");
            // 宿主与默认实现同名，只能靠内容区分是谁的
            ThreadPoolTaskExecutor pool = context.getBean("sysLogThreadPool", ThreadPoolTaskExecutor.class);
            assertThat(pool.getThreadNamePrefix()).isEqualTo("host-log-");
            assertThat(pool.getCorePoolSize()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("宿主 Bean 名不同则不覆盖，两个 Executor 共存")
    void hostBeanWithDifferentNameCoexists() {
        runner.withUserConfiguration(HostRenamedExecutorConfig.class).run(context -> {
            assertThat(context).hasBean("sysLogThreadPool");
            assertThat(context).hasBean("myExecutor");
            assertThat(context.getBean("sysLogThreadPool", ThreadPoolTaskExecutor.class)
                    .getThreadNamePrefix()).isEqualTo("sysLogThreadPool-");
        });
    }

    @Test
    @DisplayName("LogAutoConfiguration 被组件扫描当普通配置类拾取时仍能启动")
    void startsWhenModuleConfigIsPickedUpByScanning() {
        new ApplicationContextRunner()
                .withUserConfiguration(LogAutoConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("sysLogThreadPool");
                });
    }

    @Test
    @DisplayName("被组件扫描拾取时，同名覆盖会启动失败：@ConditionalOnMissingBean 的让位保证只在自动装配阶段成立")
    void overrideFailsWhenModuleConfigIsPickedUpByScanning() {
        new ApplicationContextRunner()
                // 宿主扫描 com.na 时，LogAutoConfiguration 会被当成普通 @Configuration 拾取，
                // 与宿主的 @Bean 处于同一处理批次，顺序不确定 —— 这里固定为 na-log 先注册
                .withUserConfiguration(LogAutoConfiguration.class, HostThreadPoolConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(BeanDefinitionOverrideException.class);
                });
    }

    @Test
    @DisplayName("宿主把线程池 Bean 名换成别的，自动装配的默认实现保持不动")
    void renamedHostExecutorDoesNotAffectDefaultPool() {
        runner.withUserConfiguration(HostRenamedExecutorConfig.class).run(context -> {
            ThreadPoolTaskExecutor pool =
                    context.getBean("sysLogThreadPool", ThreadPoolTaskExecutor.class);
            assertThat(pool.getThreadNamePrefix()).isEqualTo("sysLogThreadPool-");
            assertThat(pool.getCorePoolSize()).isEqualTo(5);
            assertThat(pool.getMaxPoolSize()).isEqualTo(10);
            assertThat(pool.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(25);
        });
    }

    @Test
    @DisplayName("被扫描拾取时宿主用异名 Bean 提供同类型切面，会静默注册出两个切面——比启动失败更隐蔽")
    void duplicateAspectWhenScannedAndHostUsesDifferentName() {
        new ApplicationContextRunner()
                .withUserConfiguration(LogAutoConfiguration.class, HostNamedAspectConfig.class)
                .run(context -> {
                    // 上下文正常启动，没有任何告警：两个 @Aspect 同时生效，同一次请求会被记录两遍。
                    // 若将来修复为按注册时机判断，此处会变成 1 个 Bean，届时删除本用例。
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeanNamesForType(OperationLogAspect.class))
                            .containsExactlyInAnyOrder("operationLogAspect", "myOwnAspect");
                });
    }

    @Configuration
    static class HostOverrideConfig {
        @Bean
        OperationLogAspect hostAspect(AutoLogConfig config, LogEventUtil util,
                                      org.springframework.context.ApplicationContext ctx) {
            return new OperationLogAspect(config, util, ctx);
        }
    }

    /** 宿主用异名 Bean 提供同类型切面：没有名字冲突，@ConditionalOnMissingBean 是否让位取决于时机 */
    @Configuration
    static class HostNamedAspectConfig {
        @Bean("myOwnAspect")
        OperationLogAspect myOwnAspect(AutoLogConfig config, LogEventUtil util,
                                       org.springframework.context.ApplicationContext ctx) {
            return new OperationLogAspect(config, util, ctx);
        }
    }

    /** 宿主用自己的实现覆盖默认线程池：Bean 名必须也叫 sysLogThreadPool */
    @Configuration
    static class HostThreadPoolConfig {
        @Bean(name = "sysLogThreadPool")
        Executor hostThreadPool() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setThreadNamePrefix("host-log-");
            executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
            return executor;
        }
    }

    /** 宿主用了别的 Bean 名：不会覆盖，两个 Executor 共存 */
    @Configuration
    static class HostRenamedExecutorConfig {
        @Bean(name = "myExecutor")
        Executor myExecutor() {
            return new ThreadPoolTaskExecutor();
        }
    }
}
