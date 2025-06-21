package com.na.syslog;


import com.na.syslog.enums.INaModelProvider;
import com.na.syslog.enums.INaOperateProvider;

public interface INaLogFormatter {
    /**
     * beforeContext
     * @param context context
     */
    void beforeContext(NaLogFormatterContext context);

    /**
     * afterContext
     * @param context context
     */
    void afterContext(NaLogFormatterContext context);

    /**
     * module
     * @return
     */
    INaModelProvider module();

    /**
     * operate
     * @return
     */
    INaOperateProvider operate();
}
