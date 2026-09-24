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
import com.na.common.syslog.util.LogEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.io.PrintWriter;
import java.io.StringWriter;
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
                sysLogDto.setContent(null);
                sysLogDto.setType(GlobalConst.EX);
                sysLogDto.setExDesc("日志记录异常");
                // 加入数据库日志记录
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                // 异常的详情
                String expDetail = sw.toString();
                sysLogDto.setExDetail(expDetail);
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
                    sysLogDto.setClassPath(context.context.getClassName());
                    //方法名
                    String[] classPath = context.context.getClassName().split("\\.");
                    sysLogDto.setActionMethod(classPath[classPath.length - 1]);
                    //请求api
                    sysLogDto.setRequestUri(context.context.getRequest().getRequestURI());
                    //请求方式
                    sysLogDto.setHttpMethod(context.context.getRequest().getMethod());
                    //请求参数
                    Object[] parameters = context.context.getParameters();
                    StringBuilder builder = new StringBuilder();
                    for (Object parameter : parameters) {
                        // 处理可能的 null 值，避免 NullPointerException
                        builder.append(StringUtils.isEmpty(String.valueOf(parameter)) ? "" : String.valueOf(parameter));
                    }
                    String params = builder.toString();
                    sysLogDto.setParams(params);
                    //请求结果
                    String resultStr = String.valueOf(context.context.getResult());
                    sysLogDto.setResult(resultStr);
                    //获取请求方法
                    sysLogDto.setStartTime(startTime);
                    sysLogDto.setFinishTime(finishTime);
                    Long calculated = DateTimeUtil.calculateTimeDifference(startTime, finishTime);
                    sysLogDto.setConsumingTime(calculated);
                    sysLogDto.setModule(context.formatter.module().getCode());
                    sysLogDto.setOperate(context.formatter.operate().getCode());
                    if (naAutoLogConfig.isCn()) {
                        sysLogDto.setModule(context.formatter.module().getDesc());
                        sysLogDto.setOperate(context.formatter.operate().getDesc());
                    }
                    sysLogDto.setContent(context.context.getContent());

                    String ua = context.context.getRequest().getHeader("user-agent");

                    sysLogDto.setUa(ua);

                    sysLogDto.setRequest(context.context.getRequest());
                    sysLogDto.setResponse(context.context.getResponse());

                }
            }
            catch (Exception e) {
                log.error("日志记录异常", e);
                sysLogDto.setContent(null);
                sysLogDto.setType(GlobalConst.EX);
                sysLogDto.setExDesc("日志记录异常");
                // 加入数据库日志记录
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                // 异常的详情
                String expDetail = sw.toString();
                sysLogDto.setExDetail(expDetail);
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
