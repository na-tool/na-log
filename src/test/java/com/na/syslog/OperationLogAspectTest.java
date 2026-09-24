package com.na.syslog;

import com.na.syslog.config.AutoLogConfig;
import com.na.syslog.config.LogAutoConfiguration;
import com.na.syslog.controller.TestController;
import com.na.syslog.controller.TestFormatter;
import com.na.syslog.dto.SysLogDto;
import com.na.syslog.event.SysLogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.event.EventListener;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 采集切面的集成测试。
 *
 * <p>这里刻意用真实 Spring 上下文 + 真实切面走代理调用，而不是单测私有方法：
 * 切点表达式里的注解名是字符串字面量，编译器不检查，只有真正走一遍织入才能
 * 发现写错（历史上就因此让切面静默失效过）。
 */
@SpringJUnitConfig(OperationLogAspectTest.TestConfig.class)
class OperationLogAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    @org.springframework.context.annotation.Import(LogAutoConfiguration.class)
    static class TestConfig {

        @Bean
        TestController testController() {
            return new TestController();
        }

        @Bean
        TestFormatter testFormatter() {
            return new TestFormatter();
        }

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    /** 同步监听，保证断言是确定的 */
    static class RecordingListener {
        final List<SysLogDto> received = new CopyOnWriteArrayList<>();

        @EventListener
        public void onSysLog(SysLogEvent event) {
            received.add((SysLogDto) event.getSource());
        }
    }

    @Autowired
    private TestController controller;

    @Autowired
    private TestFormatter formatter;

    @Autowired
    private RecordingListener listener;

    @Autowired
    private AutoLogConfig autoLogConfig;

    @BeforeEach
    void setUp() {
        listener.received.clear();
        formatter.failBefore = false;
        autoLogConfig.setEnabled(true);
        autoLogConfig.setCn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/demo/hello");
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("user-agent", "junit-agent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("带注解的 controller 方法被切面命中并记录")
    void recordsAnnotatedControllerMethod() {
        String returned = controller.ok("world");

        assertThat(returned).isEqualTo("hello world");
        assertThat(listener.received).hasSize(1);

        SysLogDto dto = listener.received.get(0);
        assertThat(dto.getType()).isEqualTo(SysLogDto.TYPE_NORMAL);
        assertThat(dto.getActionMethod()).isEqualTo("ok");
        assertThat(dto.getClassPath()).isEqualTo(TestController.class.getName());
        assertThat(dto.getRequestUri()).isEqualTo("/demo/hello");
        assertThat(dto.getHttpMethod()).isEqualTo("POST");
        assertThat(dto.getRequestIp()).isEqualTo("10.0.0.9");
        assertThat(dto.getUa()).isEqualTo("junit-agent");
        assertThat(dto.getUserId()).isEqualTo("user-1");
        assertThat(dto.getTenantId()).isEqualTo("tenant-1");
        assertThat(dto.getContent()).isEqualTo("测试内容");
        assertThat(dto.getParams()).contains("world");
        assertThat(dto.getResult()).contains("hello world");
        assertThat(dto.getStartTime()).isNotNull();
        assertThat(dto.getFinishTime()).isNotNull();
        assertThat(dto.getConsumingTime()).isNotNull();
    }

    @Test
    @DisplayName("业务方法抛异常时先落一条 ex 记录，再原样抛出")
    void recordsBusinessErrorAndRethrows() {
        assertThatThrownBy(() -> controller.boom())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("业务炸了");

        assertThat(listener.received).hasSize(1);
        SysLogDto dto = listener.received.get(0);
        assertThat(dto.getType()).isEqualTo(SysLogDto.TYPE_EX);
        assertThat(dto.getExDesc()).contains("IllegalStateException").contains("业务炸了");
        assertThat(dto.getExDetail()).contains("IllegalStateException");
        assertThat(dto.getContent()).isNull();
    }

    @Test
    @DisplayName("switch 关闭时不发布任何事件")
    void skipsWhenDisabled() {
        autoLogConfig.setEnabled(false);

        assertThat(controller.ok("world")).isEqualTo("hello world");
        assertThat(listener.received).isEmpty();
    }

    @Test
    @DisplayName("非 Web 请求上下文下静默跳过，且不抛异常")
    void skipsWithoutRequestContext() {
        RequestContextHolder.resetRequestAttributes();

        assertThat(controller.ok("world")).isEqualTo("hello world");
        assertThat(listener.received).isEmpty();
    }

    @Test
    @DisplayName("Formatter 抛异常时降级为 ex 记录，业务方法不受影响")
    void degradesWhenFormatterFails() {
        formatter.failBefore = true;

        assertThat(controller.ok("world")).isEqualTo("hello world");
        assertThat(listener.received).hasSize(1);

        SysLogDto dto = listener.received.get(0);
        assertThat(dto.getType()).isEqualTo(SysLogDto.TYPE_EX);
        assertThat(dto.getExDesc()).isEqualTo("日志记录异常");
        assertThat(dto.getExDetail()).contains("formatter 前置处理失败");
    }

    @Test
    @DisplayName("不带注解的方法不产生记录")
    void ignoresUnannotatedMethod() {
        assertThat(controller.notAnnotated()).isEqualTo("nope");
        assertThat(listener.received).isEmpty();
    }

    @Test
    @DisplayName("cn=true 时模块与操作写中文描述")
    void writesDescriptionWhenCnEnabled() {
        autoLogConfig.setCn(true);

        controller.ok("world");

        SysLogDto dto = listener.received.get(0);
        assertThat(dto.getModule()).isEqualTo("默认模块");
        assertThat(dto.getOperate()).isEqualTo("默认操作");
    }
}
