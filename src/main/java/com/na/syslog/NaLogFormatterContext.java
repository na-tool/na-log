package com.na.syslog;

import lombok.Data;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;


@Data
public class NaLogFormatterContext {
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final String className;
    private final Method method;
    private final Object[] parameters;
    private Object result;

    private Object contextData;
    private String content;
    private String userId;
    private String tenantId;


    public NaLogFormatterContext(HttpServletRequest request,
                                 HttpServletResponse response,
                                 String className,
                                 Method method,
                                 Object[] parameters) {
        this.request = request;
        this.response = response;
        this.className = className;
        this.method = method;
        this.parameters = parameters;
    }
}
