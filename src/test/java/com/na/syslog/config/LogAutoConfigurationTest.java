package com.na.syslog.config;

import com.na.syslog.OperationLogAspect;
import com.na.syslog.utils.LogEventUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

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

    @Configuration
    static class HostOverrideConfig {
        @Bean
        OperationLogAspect hostAspect(AutoLogConfig config, LogEventUtil util,
                                      org.springframework.context.ApplicationContext ctx) {
            return new OperationLogAspect(config, util, ctx);
        }
    }
}
