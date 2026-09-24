package com.na.syslog.controller;

import com.na.syslog.ILogFormatter;
import com.na.syslog.LogFormatterContext;
import com.na.syslog.enums.IModelProvider;
import com.na.syslog.enums.IOperateProvider;
import com.na.syslog.enums.ModuleEnum;
import com.na.syslog.enums.OperateEnum;

/**
 * 测试用的 Formatter，可通过 {@link #failBefore} 模拟 beforeContext 抛异常。
 */
public class TestFormatter implements ILogFormatter {

    /** 置为 true 时 beforeContext 抛异常，用于验证降级路径 */
    public boolean failBefore;

    @Override
    public void beforeContext(LogFormatterContext context) {
        if (failBefore) {
            throw new IllegalStateException("formatter 前置处理失败");
        }
        context.setContent("测试内容");
        context.setUserId("user-1");
        context.setTenantId("tenant-1");
    }

    @Override
    public void afterContext(LogFormatterContext context) {
        // 无需后置处理
    }

    @Override
    public IModelProvider module() {
        return ModuleEnum.DEF_MODEL;
    }

    @Override
    public IOperateProvider operate() {
        return OperateEnum.DEF_OPT;
    }
}
