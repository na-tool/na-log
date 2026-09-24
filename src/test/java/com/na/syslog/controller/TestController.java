package com.na.syslog.controller;

import com.na.syslog.annotation.OperationLog;

/**
 * 测试用的伪控制器。
 *
 * <p>包名刻意包含 {@code controller} 段，以便命中切点的
 * {@code within(*..*controller..*)} 分支 —— 这正是回归重点：
 * 切点里写的是注解的全限定字符串，改名后若不跟着改，切面会静默失效。
 */
public class TestController {

    /** 正常返回 */
    @OperationLog(formatter = TestFormatter.class)
    public String ok(String name) {
        return "hello " + name;
    }

    /** 抛业务异常 */
    @OperationLog(formatter = TestFormatter.class)
    public String boom() {
        throw new IllegalStateException("业务炸了");
    }

    /** 不带注解，不应被采集 */
    public String notAnnotated() {
        return "nope";
    }
}
