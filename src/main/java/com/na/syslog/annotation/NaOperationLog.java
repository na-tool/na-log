package com.na.syslog.annotation;

import com.na.syslog.INaLogFormatter;
import com.na.syslog.formatter.DefFormatterNa;

import java.lang.annotation.*;

@Documented
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface NaOperationLog {
    Class<? extends INaLogFormatter> formatter() default DefFormatterNa.class;
}
