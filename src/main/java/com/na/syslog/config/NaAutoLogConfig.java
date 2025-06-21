package com.na.syslog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Data
@Component
@ConfigurationProperties(prefix = "na.log")
public class NaAutoLogConfig {
    private String key;
    private Boolean enabled = true;
}
