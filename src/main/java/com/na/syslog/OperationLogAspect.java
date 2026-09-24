package com.na.syslog;

import com.na.syslog.annotation.OperationLog;
import com.na.syslog.config.AutoLogConfig;
import com.na.syslog.dto.SysLogDto;
import com.na.syslog.enums.IModelProvider;
import com.na.syslog.enums.IOperateProvider;
import com.na.syslog.utils.DateTimeUtils;
import com.na.syslog.utils.LogEventUtil;
import com.na.syslog.utils.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 操作日志采集切面。
 *
 * <p>由 {@link com.na.syslog.config.LogAutoConfiguration} 以 @Bean 方式注册，类上不挂
 * @Configuration：那个注解原本只是为了被组件扫描命中，而扫描要求宿主把自己的包名与
 * com.na 对齐，宿主包名不同时模块会静默失效。改走自动装配后不再有这个约束。
 *
 * <p><b>日志永远不会影响业务</b>，这是本类最重要的约束，由两点结构性保证：
 * <ol>
 *   <li>业务方法先执行，其结果或异常被捕获后，日志组装在独立的 try 中完成，
 *       任何失败只记一条 error 日志；</li>
 *   <li>事件发布单独兜底，线程池拒绝、监听器缺类等都不会冒泡出去。</li>
 * </ol>
 */
@Aspect
@Order(2)
@Slf4j
public class OperationLogAspect {

    /** 与宿主监听器对 params 的截断约定保持一致 */
    private static final int MAX_PARAMS_LENGTH = 1000;
    /** 与宿主监听器的 STR_MAX_LENGTH 保持一致 */
    private static final int MAX_RESULT_LENGTH = 16383;

    private final AutoLogConfig autoLogConfig;
    private final LogEventUtil logEventUtil;
    private final ApplicationContext applicationContext;

    public OperationLogAspect(AutoLogConfig autoLogConfig,
                              LogEventUtil logEventUtil,
                              ApplicationContext applicationContext) {
        this.autoLogConfig = autoLogConfig;
        this.logEventUtil = logEventUtil;
        this.applicationContext = applicationContext;
    }

    /**
     * 切入点：com.na.controller 包下的公有方法，或任意 *controller* 包内带
     * {@link OperationLog} 注解的公有方法。
     */
    @Pointcut("(execution(public * com.na.controller..*.*(..)) || execution(public * *(..)) && within(*..*controller..*)) && @annotation(com.na.syslog.annotation.OperationLog)")
    public void entryPoint() {
        // do nothing
    }

    /**
     * 环绕通知。
     *
     * @param point 连接点
     * @return 业务方法返回值
     * @throws Throwable 业务方法抛出的异常原样透传
     */
    @Around("entryPoint()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        LocalDateTime startTime = DateTimeUtils.getCurrentBeijingDateTime();

        Object result = null;
        Exception businessError = null;
        try {
            result = point.proceed();
        } catch (Exception e) {
            // 业务异常先捕获用于记录，随后原样抛出。
            // 这里只捕获 Exception：Error（如 OOM）直接冒泡，不再做额外分配。
            businessError = e;
        }
        LocalDateTime finishTime = DateTimeUtils.getCurrentBeijingDateTime();

        if (autoLogConfig.isEnabled()) {
            try {
                record(point, startTime, finishTime, result, businessError);
            } catch (Throwable t) {
                // 日志链路自身的任何失败都不得影响业务请求
                log.error("操作日志记录失败，已忽略", t);
            }
        }

        if (businessError != null) {
            throw businessError;
        }
        return result;
    }

    /**
     * 组装并发布一条操作日志。
     *
     * @param point         连接点
     * @param startTime     业务方法开始时间
     * @param finishTime    业务方法结束时间
     * @param result        业务方法返回值，异常时为 null
     * @param businessError 业务方法抛出的异常，正常时为 null
     */
    private void record(ProceedingJoinPoint point,
                        LocalDateTime startTime,
                        LocalDateTime finishTime,
                        Object result,
                        Exception businessError) {
        SysLogDto dto = newLogDto(startTime, finishTime);
        boolean hasRecord = false;

        LogAspectContext context = null;
        try {
            context = buildContext(point);
            if (context != null) {
                context.context.setResult(result);
                context.formatter.beforeContext(context.context);
                context.formatter.afterContext(context.context);
                fill(dto, context);
                hasRecord = true;
            }
        } catch (Exception e) {
            // Formatter 或上下文构建失败：记成日志链路自身的异常，不掩盖业务异常
            log.error("操作日志格式化异常", e);
            markAspectError(dto, e);
            hasRecord = true;
        }

        if (businessError != null) {
            // 业务异常优先级最高，覆盖上面可能写入的日志链路异常信息
            markBusinessError(dto, businessError);
            hasRecord = true;
        }

        if (!hasRecord) {
            // 解析不到 @OperationLog，或当前不在 Web 请求上下文中。
            // 这不是应用异常：既不伪造一条 EX 记录，也不发布只有时间戳的空记录。
            log.warn("操作日志跳过，无法建立 Formatter 上下文，method={}",
                    point.getSignature().toShortString());
            return;
        }

        try {
            logEventUtil.publishEvent(dto);
        } catch (Throwable t) {
            // 宿主用 @Async("sysLogThreadPool") 消费事件，线程池提交发生在当前请求线程内：
            // 队列满时拒绝策略会抛 RejectedExecutionException，宿主类路径不全时可能抛
            // NoClassDefFoundError，用 Throwable 一并兜住。
            log.error("操作日志事件发布失败，已忽略", t);
        }
    }

    /**
     * 构建一次采集所需的上下文。
     *
     * @param point 连接点
     * @return 上下文；解析不到注解或不在 Web 请求上下文中时返回 null
     */
    private LogAspectContext buildContext(ProceedingJoinPoint point) {
        HttpServletRequest request = WebUtils.getCurrentRequest();
        if (request == null) {
            // 非 Web 请求上下文（如定时任务调用带注解的方法），后续取 URI/Method/UA 都会 NPE
            return null;
        }

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        if (method == null) {
            return null;
        }
        OperationLog annotation = method.getAnnotation(OperationLog.class);
        if (annotation == null) {
            return null;
        }

        LogAspectContext context = new LogAspectContext();
        // 切面本身是 Spring Bean，直接按类型取 Formatter，无需静态上下文持有者
        context.formatter = applicationContext.getBean(annotation.formatter());
        context.context = new LogFormatterContext(
                request,
                WebUtils.getCurrentResponse(),
                point.getTarget().getClass().getName(),
                method,
                point.getArgs());
        return context;
    }

    /**
     * 初始化日志对象的时间字段与 ID。
     *
     * @param startTime  业务方法开始时间
     * @param finishTime 业务方法结束时间
     * @return 已填充基础字段的日志对象
     */
    private static SysLogDto newLogDto(LocalDateTime startTime, LocalDateTime finishTime) {
        SysLogDto dto = new SysLogDto();
        dto.setId(UUID.randomUUID().toString().replace("-", ""));
        dto.setType(SysLogDto.TYPE_NORMAL);
        dto.setStartTime(startTime);
        dto.setFinishTime(finishTime);
        // 采集动作发生在业务方法返回之后，以 finishTime 作为落库时间
        dto.setOperateTime(finishTime);
        dto.setConsumingTime(DateTimeUtils.calculateTimeDifference(startTime, finishTime));
        return dto;
    }

    /**
     * 从上下文与请求中填充日志的业务字段。
     *
     * @param dto     待填充的日志对象
     * @param context 采集上下文
     */
    private void fill(SysLogDto dto, LogAspectContext context) {
        HttpServletRequest request = context.context.getRequest();

        dto.setClassPath(context.context.getClassName());
        // 方法名直接取反射 Method 的名字。
        // 注意不要退回去截取 classPath 的尾段：classPath 是类全名，那样取到的是
        // 类名而不是方法名（字段注释写的是"方法名"，历史上一直落错了值）。
        dto.setActionMethod(context.context.getMethod().getName());

        dto.setRequestIp(WebUtils.getIpAddress(request));
        dto.setRequestUri(request.getRequestURI());
        dto.setHttpMethod(request.getMethod());
        dto.setUa(request.getHeader("user-agent"));

        dto.setUserId(context.context.getUserId());
        dto.setTenantId(context.context.getTenantId());
        dto.setParams(renderParams(context.context.getParameters()));
        dto.setResult(renderResult(context.context.getResult()));
        dto.setContent(context.context.getContent());

        // module()/operate() 是宿主可自定义的虚方法，提取一次避免重复调用
        IModelProvider module = context.formatter.module();
        IOperateProvider operate = context.formatter.operate();
        if (autoLogConfig.isCn()) {
            dto.setModule(module.getDesc());
            dto.setOperate(operate.getDesc());
        } else {
            dto.setModule(module.getCode());
            dto.setOperate(operate.getCode());
        }
    }

    /**
     * 把方法入参渲染成单行文本。
     *
     * <p>ArrayUtils.toString 对数组递归渲染为 {1,2,3}，对其它对象走 toString()，
     * 对 null 返回兜底值；集合不递归，用其自身的 toString。
     *
     * @param parameters 目标方法入参
     * @return 逗号分隔的参数文本；无有效参数时返回 null
     */
    private static String renderParams(Object[] parameters) {
        if (parameters == null || parameters.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder(64);
        for (Object parameter : parameters) {
            String text = ArrayUtils.toString(parameter, "");
            if (text.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(text);
        }
        return builder.length() == 0 ? null : truncate(builder.toString(), MAX_PARAMS_LENGTH);
    }

    /**
     * 把方法返回值渲染成文本。
     *
     * <p>未重写 toString 的 POJO 仍只得到对象哈希 —— 这是已知限制：彻底解决需要
     * JSON 序列化，而 Jackson 只是 spring-boot-starter-web 的传递依赖（provided），
     * 硬用会让模块在无 Jackson 的宿主里抛 NoClassDefFoundError。
     *
     * @param result 目标方法返回值
     * @return 返回值文本；result 为 null 时返回 null
     */
    private static String renderResult(Object result) {
        if (result == null) {
            return null;
        }
        return truncate(ArrayUtils.toString(result, ""), MAX_RESULT_LENGTH);
    }

    /**
     * 超长截断，与宿主消费端的既有约定保持一致。
     *
     * @param text 原始文本
     * @param max  最大保留长度
     * @return 截断后的文本
     */
    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...(truncated)";
    }

    /**
     * 把业务方法抛出的异常写入日志对象。
     *
     * @param dto            待填充的日志对象
     * @param businessError  业务方法抛出的异常
     */
    private static void markBusinessError(SysLogDto dto, Exception businessError) {
        dto.setContent(null);
        dto.setType(SysLogDto.TYPE_EX);
        dto.setExDesc(truncate(describe(businessError), MAX_PARAMS_LENGTH));
        dto.setExDetail(ExceptionUtils.getStackTrace(businessError));
    }

    /**
     * 把日志链路自身的异常写入日志对象。
     *
     * @param dto 待填充的日志对象
     * @param e   采集过程中捕获的异常
     */
    private static void markAspectError(SysLogDto dto, Exception e) {
        dto.setContent(null);
        dto.setType(SysLogDto.TYPE_EX);
        dto.setExDesc("日志记录异常");
        dto.setExDetail(ExceptionUtils.getStackTrace(e));
    }

    /**
     * 生成异常的简短描述：类名加消息。
     *
     * @param e 异常
     * @return 形如 {@code NullPointerException: xxx} 的描述
     */
    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty()
                ? e.getClass().getName()
                : e.getClass().getName() + ": " + message;
    }

    /** 单次采集的上下文：格式化器与其入参上下文 */
    private static class LogAspectContext {
        private ILogFormatter formatter;
        private LogFormatterContext context;
    }
}
