package com.na.syslog;

import com.na.common.constant.GlobalConst;
import com.na.common.syslog.dto.SysLogDto;
import com.na.common.utils.AddressUtil;
import com.na.common.utils.CommonUtil;
import com.na.common.utils.DateTimeUtil;
import com.na.common.utils.IDUtil;
import com.na.common.utils.SpringContextUtil;
import com.na.syslog.annotation.NaOperationLog;
import com.na.syslog.config.NaAutoLogConfig;
import com.na.syslog.enums.INaModelProvider;
import com.na.syslog.enums.INaOperateProvider;
import com.na.common.syslog.util.LogEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Configuration
@Order(2)
@Slf4j
public class NaOperationLogAspect {
    @Autowired
    private NaAutoLogConfig naAutoLogConfig;

    @Autowired
    private LogEventUtil logEventUtil;

    /**
     * 定义一个切入点.
     * 第一个 * 代表任意修饰符及任意返回值.
     * 第二个 * 任意包名
     * 第三个 * 代表任意方法.
     * 第四个 * 定义在web包或者子包
     * 第五个 * 任意方法
     * .. 匹配任意数量的参数.
     */
    @Pointcut("(execution(public * com.na.controller..*.*(..)) || execution(public * *(..)) && within(*..*controller..*)) && @annotation(com.na.syslog.annotation.NaOperationLog)")
    public void entryPoint() {
        // do nothing
    }

    /**
     * 环绕通知处理
     * @param point point
     * @return Object
     * @throws Throwable t
     */
    @Around("entryPoint()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
//        String key = naAutoLogConfig != null ? naAutoLogConfig.getKey() : null;
//        if (StringUtils.isEmpty(key)) {
//            throw new NaBusinessException(NaStatus.AUTHORIZATION_EXPIRED,null);
//        }
//
//        if(!LicenseValidator.isValidLicense(key)) {
//            throw new NaBusinessException(NaStatus.AUTHORIZATION_EXPIRED,null);
//        }

        LocalDateTime startTime = DateTimeUtil.getCurrentBeijingDateTime();
        Object result = point.proceed();
        SysLogDto sysLogDto = new SysLogDto();
        sysLogDto.setType(GlobalConst.NORMAL);
        if (naAutoLogConfig.isEnabled()) {
            LogAspectContext context;
            try {
                context = handleAround(point);
                if (context == null) {
                    // 解析不到 @NaOperationLog，或当前不在 Web 请求上下文中。
                    // 这不是应用异常：既不伪造一条 EX 记录，也不发布只有时间戳的空记录。
                    log.warn("操作日志跳过，无法建立 Formatter 上下文，method={}",
                            point.getSignature().toShortString());
                    return result;
                }
                context.formatter.beforeContext(context.context);
            }
            catch (Exception e) {
                log.error("日志记录异常", e);
                context = null;
                markAsException(sysLogDto, e);
            }

            try {
                if (context != null) {
                    context.context.setResult(result);
                    context.formatter.afterContext(context.context);
                    LocalDateTime finishTime = DateTimeUtil.getCurrentBeijingDateTime();

                    sysLogDto.setId(IDUtil.ID(17));
                    sysLogDto.setRequestIp(AddressUtil.getIpAddress(context.context.getRequest()));

                    sysLogDto.setUserId(context.context.getUserId());
                    sysLogDto.setTenantId((context.context.getTenantId()));
                    //api描述信息
                    //类名
                    String className = context.context.getClassName();
                    sysLogDto.setClassPath(className);
                    //方法名：只取最后一个点之后的部分。原先用 split("\\.") 会为每个
                    //请求编译一次正则并分配 Pattern/Matcher/数组，这里换成纯下标运算。
                    int lastDot = className == null ? -1 : className.lastIndexOf('.');
                    sysLogDto.setActionMethod(lastDot < 0 ? className : className.substring(lastDot + 1));
                    //请求api
                    sysLogDto.setRequestUri(context.context.getRequest().getRequestURI());
                    //请求方式
                    sysLogDto.setHttpMethod(context.context.getRequest().getMethod());
                    //请求参数
                    sysLogDto.setParams(renderParams(context.context.getParameters()));
                    //请求结果
                    sysLogDto.setResult(renderResult(context.context.getResult()));
                    //获取请求方法
                    sysLogDto.setStartTime(startTime);
                    sysLogDto.setFinishTime(finishTime);
                    Long calculated = DateTimeUtil.calculateTimeDifference(startTime, finishTime);
                    sysLogDto.setConsumingTime(calculated);
                    // module()/operate() 是宿主可自定义的虚方法，提取一次避免重复调用
                    INaModelProvider module = context.formatter.module();
                    INaOperateProvider operate = context.formatter.operate();
                    if (naAutoLogConfig.isCn()) {
                        sysLogDto.setModule(module.getDesc());
                        sysLogDto.setOperate(operate.getDesc());
                    } else {
                        sysLogDto.setModule(module.getCode());
                        sysLogDto.setOperate(operate.getCode());
                    }
                    sysLogDto.setContent(context.context.getContent());

                    String ua = context.context.getRequest().getHeader("user-agent");

                    sysLogDto.setUa(ua);

                    // 注意：不要把活的 servlet request/response 放进 SysLogDto。
                    // 宿主监听器是 @Async("sysLogThreadPool")，回调时请求线程早已返回，
                    // request 可能已被 Tomcat 回收（读取会抛 IllegalStateException 或
                    // 读到下一个请求的数据）；且任务在队列中排队期间，整个 request
                    // 对象图（headers/attributes/session/已解析的 body）无法回收。
                    // 请求侧信息已在本方法内抽成标量字段（requestIp/requestUri/
                    // httpMethod/ua），Formatter 若需更多可在请求线程内用
                    // context.getRequest() 自行提取。

                }
            }
            catch (Exception e) {
                log.error("日志记录异常", e);
                markAsException(sysLogDto, e);
            }
            sysLogDto.setOperateTime(DateTimeUtil.getCurrentBeijingDateTime());
            try {
                logEventUtil.publishEvent(sysLogDto);
            } catch (Throwable t) {
                // 日志落库失败绝不能影响业务请求。宿主用 @Async("sysLogThreadPool")
                // 消费事件，线程池提交发生在当前请求线程内：队列满时 AbortPolicy 抛
                // TaskRejectedException，宿主类路径不全时可能抛 NoClassDefFoundError，
                // 用 Throwable 一并兜住，避免从 @Around 冒出去把业务请求打成 500。
                log.error("操作日志事件发布失败，已忽略", t);
            }
        }
        return result;
    }


    /** 与宿主 SysLogListener 对 params 的截断约定保持一致 */
    private static final int MAX_PARAMS_LENGTH = 1000;
    /** 与宿主 SysLogListener 的 STR_MAX_LENGTH 保持一致 */
    private static final int MAX_RESULT_LENGTH = 16383;

    /**
     * 把方法入参渲染成单行文本。
     * ArrayUtils.toString 对数组递归渲染为 {1,2,3}，对其它对象走 toString()，
     * 对 null 返回兜底值；集合不递归，用其自身的 toString。
     * 原实现用 String.valueOf 拼接且无分隔符，["ab","c"] 与 ["a","bc"] 会拼出
     * 同一串、无法区分，且长度无上限。
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
     * 未重写 toString 的 POJO 仍只得到对象哈希 —— 这是已知限制：彻底解决需要
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
     * 把异常写入日志记录。
     * ExceptionUtils.getStackTrace 的内部实现与原先手写的
     * StringWriter + PrintWriter(sw, true) + printStackTrace 逐字符等价，
     * 落库内容不变，但少分配两个对象且不再重复渲染堆栈。
     *
     * @param sysLogDto 待填充的日志对象
     * @param e         捕获到的异常
     */
    private static void markAsException(SysLogDto sysLogDto, Exception e) {
        sysLogDto.setContent(null);
        sysLogDto.setType(GlobalConst.EX);
        sysLogDto.setExDesc("日志记录异常");
        sysLogDto.setExDetail(ExceptionUtils.getStackTrace(e));
    }

    /**
     * around日志记录
     * @param point point
     * @return LogAspectContext
     * @throws Exception ex
     */
    private LogAspectContext handleAround(ProceedingJoinPoint point) throws Exception {
        LogAspectContext context = null;

        HttpServletRequest request = CommonUtil.getCurrentHttpRequest();
        HttpServletResponse response = CommonUtil.getCurrentHttpResponse();
        if (request == null) {
            // 非 Web 请求上下文（如定时任务调用带注解的方法），后续取 URI/Method/UA
            // 都会 NPE，这里统一收敛到调用方的显式跳过分支。
            return null;
        }

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = point.getTarget().getClass();
        if (method != null) {
            NaOperationLog naOperationLog = method.getAnnotation(NaOperationLog.class);
            if (naOperationLog != null) {
                Class<? extends INaLogFormatter> formatterClass = naOperationLog.formatter();
                INaLogFormatter formatter = SpringContextUtil.getBean(formatterClass);

                Object[] pointArgs = point.getArgs();

                context = new LogAspectContext();
                context.formatter = formatter;
                context.context = new NaLogFormatterContext(request,response, targetClass.getName(), method, pointArgs);
            }
        }

        return context;
    }

    private static class LogAspectContext {
        public INaLogFormatter formatter;
        public NaLogFormatterContext context;
    }

}
