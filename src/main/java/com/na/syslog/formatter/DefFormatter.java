package com.na.syslog.formatter;

import com.na.syslog.ILogFormatter;
import com.na.syslog.LogFormatterContext;

import com.na.syslog.enums.IModelProvider;
import com.na.syslog.enums.IOperateProvider;
import com.na.syslog.enums.ModuleEnum;
import com.na.syslog.enums.OperateEnum;
import org.springframework.stereotype.Component;

@Component
public class DefFormatter implements ILogFormatter {
    @Override
    public void beforeContext(LogFormatterContext context) {

    }

    @Override
    public void afterContext(LogFormatterContext context) {

    }

    @Override
    public IModelProvider module() {
        return ModuleEnum.DEF_MODEL;
    }

    @Override
    public IOperateProvider operate() {
        return OperateEnum.DEF_OPT;
    }
}
