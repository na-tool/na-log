package com.na.syslog.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;

/**
 * Web 请求相关的内置工具，取代原先对 na-common 的依赖。
 */
@Slf4j
public final class WebUtils {

    /**
     * 代理转发头，按优先级排列。注意这些头可被客户端伪造，
     * 依赖它做安全控制时必须确保请求经过可信代理并覆盖了伪造字段。
     */
    private static final String[] IP_HEADER_NAMES = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
    };

    private WebUtils() {
    }

    /**
     * 获取当前线程绑定的请求对象。
     *
     * @return 当前请求；非 Web 上下文（定时任务、MQ 消费等）返回 null
     */
    public static HttpServletRequest getCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        // instanceof 对 null 恒为 false，无需额外判空
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest() : null;
    }

    /**
     * 获取当前线程绑定的响应对象。
     *
     * @return 当前响应；非 Web 上下文返回 null
     */
    public static HttpServletResponse getCurrentResponse() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getResponse() : null;
    }

    /**
     * 获取客户端真实 IP，支持多级代理与 IPv6 回环。
     *
     * @param request 请求对象；为 null 时尝试从当前线程上下文获取
     * @return 客户端 IP；无法识别时返回空字符串
     */
    public static String getIpAddress(HttpServletRequest request) {
        if (request == null) {
            request = getCurrentRequest();
        }
        if (request == null) {
            return "";
        }
        try {
            String ip = pickIpFromHeaders(request);
            if (ip == null) {
                ip = request.getRemoteAddr();
            }
            return normalizeIp(ip);
        } catch (Exception e) {
            log.warn("获取客户端 IP 异常: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 按优先级遍历代理头，返回第一个有效（非空且非 unknown）的值。
     *
     * @param request 请求对象
     * @return 有效头值；全部无效时返回 null
     */
    private static String pickIpFromHeaders(HttpServletRequest request) {
        for (String header : IP_HEADER_NAMES) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 规范化 IP：多级代理取第一个、IPv6 回环映射为 IPv4 回环。
     *
     * <p>注意：回环地址会被替换为 {@code InetAddress.getLocalHost()} 的结果。
     * 该调用可能触发反向 DNS 解析，在 DNS 配置异常的主机上会阻塞，仅在
     * 本机回环访问时才会走到。
     *
     * @param ip 原始 IP
     * @return 规范化后的 IP；入参无效时返回 null
     */
    private static String normalizeIp(String ip) {
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return null;
        }
        if (ip.contains(",")) {
            // 多级代理时形如 "clientIp, proxy1, proxy2"，取第一个即真实客户端 IP
            ip = ip.split(",")[0].trim();
        }
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }
        if ("127.0.0.1".equals(ip)) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                log.warn("无法解析本机 IP，使用默认回环地址 127.0.0.1");
            }
        }
        return ip;
    }
}
