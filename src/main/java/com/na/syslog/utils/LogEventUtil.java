package com.na.syslog.utils;

import com.na.syslog.dto.SysLogDto;
import com.na.syslog.event.SysLogEvent;
import org.springframework.context.ApplicationContext;

/**
 * 日志事件发布器。
 *
 * <p>事件由 ApplicationContext 管控，宿主用 {@code @EventListener(SysLogEvent.class)}
 * 接收，通常再叠加 {@code @Async("sysLogThreadPool")} 异步落库。
 *
 * <p>本类不带 {@code @Component}：na-log 全部由 LogAutoConfiguration 自动装配注册，
 * 带组件注解会在宿主扫描到 {@code com.na} 时被重复注册。
 */
public class LogEventUtil {

    private final ApplicationContext applicationContext;

    public LogEventUtil(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 发布一条日志事件。
     *
     * @param sysLogDto 待落库的日志对象
     */
    public void publishEvent(SysLogDto sysLogDto) {
        applicationContext.publishEvent(new SysLogEvent(sysLogDto));
    }
}
