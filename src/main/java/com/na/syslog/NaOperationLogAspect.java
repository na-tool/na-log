package com.na.syslog;

import com.na.common.constant.NaConst;
import com.na.common.exceptions.NaBusinessException;
import com.na.common.result.enums.NaStatus;
import com.na.common.syslog.dto.NaSysLogDto;
import com.na.common.utils.*;
import com.na.syslog.annotation.NaOperationLog;
import com.na.syslog.config.NaAutoLogConfig;
import com.na.common.syslog.util.NaLogEventUtil;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
    private NaLogEventUtil naLogEventUtil;

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
        String key = naAutoLogConfig != null ? naAutoLogConfig.getKey() : null;
        if (StringUtils.isEmpty(key)) {
            throw new NaBusinessException(NaStatus.AUTHORIZATION_EXPIRED,null);
        }

        if(!LicenseValidator.isValidLicense(key)) {
            throw new NaBusinessException(NaStatus.AUTHORIZATION_EXPIRED,null);
        }

        LocalDateTime startTime = NaDateTimeUtil.getCurrentBeijingDateTime();
        Object result = point.proceed();
        NaSysLogDto sysLogDto = new NaSysLogDto();
        sysLogDto.setType(NaConst.NORMAL);
        if(naAutoLogConfig.getEnabled()){
            LogAspectContext context;
            try {
                context = handleAround(point);
                context.formatter.beforeContext(context.context);
            }
            catch (Exception e) {
                log.error("日志记录异常", e);
                context = null;
                sysLogDto.setContent(null);
                sysLogDto.setType(NaConst.EX);
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
                    LocalDateTime finishTime = NaDateTimeUtil.getCurrentBeijingDateTime();

                    sysLogDto.setId(NaIDUtil.ID(17));
                    sysLogDto.setRequestIp(NaAddressUtil.getIpAddress(context.context.getRequest()));

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
                    Long calculated = NaDateTimeUtil.calculateTimeDifference(startTime, finishTime);
                    sysLogDto.setConsumingTime(calculated);
                    sysLogDto.setModule(context.formatter.module().getCode());
                    sysLogDto.setOperate(context.formatter.operate().getCode());
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
                sysLogDto.setType(NaConst.EX);
                sysLogDto.setExDesc("日志记录异常");
                // 加入数据库日志记录
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                // 异常的详情
                String expDetail = sw.toString();
                sysLogDto.setExDetail(expDetail);
            }
            sysLogDto.setOperateTime(NaDateTimeUtil.getCurrentBeijingDateTime());
            naLogEventUtil.publishEvent(sysLogDto);
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

        HttpServletRequest request = NaCommonUtil.getCurrentHttpRequest();
        HttpServletResponse response = NaCommonUtil.getCurrentHttpResponse();

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = point.getTarget().getClass();
        if (method != null) {
            NaOperationLog naOperationLog = method.getAnnotation(NaOperationLog.class);
            if (naOperationLog != null) {
                Class<? extends INaLogFormatter> formatterClass = naOperationLog.formatter();
                INaLogFormatter formatter = NaSpringContextUtil.getBean(formatterClass);

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
