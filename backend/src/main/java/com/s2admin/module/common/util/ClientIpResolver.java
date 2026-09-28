package com.s2admin.module.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 解析客户端 IP。仅在显式配置可信反向代理时读取 X-Forwarded-For。
 */
@Component
public class ClientIpResolver {

    @Value("${s2admin.trusted-proxy:false}")
    private boolean trustedProxy;

    public String resolve(HttpServletRequest request) {
        return IpUtils.getClientIp(request, trustedProxy);
    }
}
