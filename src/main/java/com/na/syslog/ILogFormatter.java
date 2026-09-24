package com.na.syslog;


import com.na.syslog.enums.IModelProvider;
import com.na.syslog.enums.IOperateProvider;

public interface ILogFormatter {
    /**
     * beforeContext
     * @param context context
     */
    void beforeContext(LogFormatterContext context);

    /**
     * afterContext
     * @param context context
     */
    void afterContext(LogFormatterContext context);

    /**
     * module
     * @return
     */
    IModelProvider module();

    /**
     * operate
     * @return
     */
    IOperateProvider operate();
}
