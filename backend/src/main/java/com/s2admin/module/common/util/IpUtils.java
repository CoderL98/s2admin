package com.s2admin.module.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * IP 工具类
 */
public final class IpUtils {

    private IpUtils() {
    }

    /**
     * 获取客户端 IP。默认不信任转发头,避免未配代理时伪造登录 IP。
     */
    public static String getClientIp(HttpServletRequest request) {
        return getClientIp(request, false);
    }

    /**
     * @param trustProxy true 时才读取 X-Forwarded-For / X-Real-IP
     */
    public static String getClientIp(HttpServletRequest request, boolean trustProxy) {
        if (request == null) {
            return "";
        }
        if (trustProxy) {
            String ip = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                int index = ip.indexOf(',');
                return index > 0 ? ip.substring(0, index).trim() : ip.trim();
            }
            ip = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取 User-Agent(浏览器/操作系统信息由前端或后续解析库处理)
     */
    public static String getUserAgent(HttpServletRequest request) {
        return request == null ? "" : request.getHeader("User-Agent");
    }
}
