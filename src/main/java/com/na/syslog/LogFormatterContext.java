package com.na.syslog;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;


/**
 * 格式化回调的上下文载体。
 *
 * 注意不要退回 @Data：它生成的 toString()/hashCode() 对 Object[] parameters 使用
 * Arrays.deepToString/deepHashCode，会递归处理每一个控制器入参；contextData 又允许
 * 宿主 Formatter 塞入任意对象（JPA 实体、懒加载集合、MultipartFile 等）。一次
 * log.debug("{}", context) 或 IDE 里查看对象，就可能触发懒加载 SQL 或大对象哈希。
 */
@Getter
@Setter
@ToString(exclude = {"request", "response", "method", "parameters", "result", "contextData"})
public class LogFormatterContext {
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


    public LogFormatterContext(HttpServletRequest request,
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
