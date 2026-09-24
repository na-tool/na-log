package com.na.syslog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * na.log 配置项。
 *
 * 不再标 @Component：由 NaLogAutoConfiguration 的 @EnableConfigurationProperties
 * 注册，避免与自动装配重复注册同一个 Bean。
 */
@Data
@ConfigurationProperties(prefix = "na.log")
public class AutoLogConfig {
//    private String key;
    private boolean enabled = true;
    private boolean cn = false;
}
