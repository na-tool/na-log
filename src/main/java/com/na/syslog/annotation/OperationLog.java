package com.na.syslog.annotation;

import com.na.syslog.ILogFormatter;
import com.na.syslog.formatter.DefFormatter;

import java.lang.annotation.*;

@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    Class<? extends ILogFormatter> formatter() default DefFormatter.class;
}
