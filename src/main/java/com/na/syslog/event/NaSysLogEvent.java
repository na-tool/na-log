package com.na.syslog.event;


import com.na.syslog.dto.NaSysLogDto;
import org.springframework.context.ApplicationEvent;

/**
 * 系统日志事件
 *
 */
public class NaSysLogEvent extends ApplicationEvent {

    public NaSysLogEvent(NaSysLogDto sysLogDto) {
        super(sysLogDto);
    }
}
