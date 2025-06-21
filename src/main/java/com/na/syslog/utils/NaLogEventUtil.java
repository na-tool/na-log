package com.na.syslog.utils;

import com.na.syslog.dto.NaSysLogDto;
import com.na.syslog.event.NaSysLogEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class NaLogEventUtil {
    /**
     * 事件发布是由ApplicationContext对象管控的，我们发布事件前需要注入ApplicationContext对象调用publishEvent方法完成事件发布
     **/
    @Autowired
    private ApplicationContext applicationContext;

    public void publishEvent(NaSysLogDto sysLogDto) {
        applicationContext.publishEvent(new NaSysLogEvent(sysLogDto));
    }
}
