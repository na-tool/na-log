package com.na.syslog.formatter;

import com.na.syslog.INaLogFormatter;
import com.na.syslog.NaLogFormatterContext;
import com.na.syslog.enums.INaModelProvider;
import com.na.syslog.enums.INaOperateProvider;
import com.na.syslog.enums.NaModuleEnum;
import com.na.syslog.enums.NaOperateEnum;
import org.springframework.stereotype.Component;

@Component
public class DefFormatterNa implements INaLogFormatter {
    @Override
    public void beforeContext(NaLogFormatterContext context) {

    }

    @Override
    public void afterContext(NaLogFormatterContext context) {

    }

    @Override
    public INaModelProvider module() {
        return NaModuleEnum.DEF_MODEL;
    }

    @Override
    public INaOperateProvider operate() {
        return NaOperateEnum.DEF_OPT;
    }
}
