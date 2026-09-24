package com.na.syslog.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class WebUtilsTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("优先取 X-Forwarded-For")
    void prefersXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("Proxy-Client-IP", "5.6.7.8");
        request.setRemoteAddr("9.9.9.9");

        assertThat(WebUtils.getIpAddress(request)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("多级代理取第一个 IP")
    void takesFirstIpOfProxyChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1, 10.0.0.2");

        assertThat(WebUtils.getIpAddress(request)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("unknown 与空值被跳过，回退到下一个头")
    void skipsUnknownHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("Proxy-Client-IP", "5.6.7.8");

        assertThat(WebUtils.getIpAddress(request)).isEqualTo("5.6.7.8");
    }

    @Test
    @DisplayName("无代理头时回退到远程地址")
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        assertThat(WebUtils.getIpAddress(request)).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("request 为 null 且无请求上下文时返回空串")
    void returnsEmptyWithoutRequest() {
        assertThat(WebUtils.getIpAddress(null)).isEmpty();
    }

    @Test
    @DisplayName("可以从当前线程上下文取到请求与响应")
    void readsFromRequestContextHolder() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));

        assertThat(WebUtils.getCurrentRequest()).isSameAs(request);
        assertThat(WebUtils.getCurrentResponse()).isSameAs(response);
    }

    @Test
    @DisplayName("非 Web 上下文返回 null 而不是抛异常")
    void returnsNullOutsideWebContext() {
        assertThat(WebUtils.getCurrentRequest()).isNull();
        assertThat(WebUtils.getCurrentResponse()).isNull();
    }
}
