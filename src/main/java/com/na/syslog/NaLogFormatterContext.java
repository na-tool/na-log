package com.na.syslog;

import lombok.Data;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;


@Data
public class NaLogFormatterContext {
    private final HttpServletRequest request;
    private final String className;
    private final Method method;
    private final Object[] parameters;
    private Object result;

    private Object contextData;
    private String content;
    private String userId;
    private String tenantId;


    public NaLogFormatterContext(HttpServletRequest request, String className, Method method, Object[] parameters) {
        this.request = request;
        this.className = className;
        this.method = method;
        this.parameters = parameters;
    }
}
