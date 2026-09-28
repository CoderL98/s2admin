package com.s2admin.module.system.service;

import com.s2admin.module.common.util.ClientIpResolver;
import com.s2admin.module.common.util.IpLocations;
import com.s2admin.module.system.entity.LoginLog;
import com.s2admin.module.system.repository.LoginLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogRepository loginLogRepository;
    private final ClientIpResolver clientIpResolver;
    private final com.s2admin.module.system.repository.SysUserRepository userRepository;

    /**
     * 由调用方在事务外调用(登录日志在 AuthController 写入):
     * SQLite 单写者下 REQUIRES_NEW 会与外层事务的 SHARED 锁死锁,必须避免在事务内写日志。
     */
    @Transactional
    public void record(Long userId, String username, HttpServletRequest request, Integer status, String message) {
        try {
            LoginLog loginLog = new LoginLog();
            Long resolvedUserId = userId;
            if (resolvedUserId == null && username != null && !username.isBlank()) {
                resolvedUserId = userRepository.findByUsername(username.trim())
                        .or(() -> userRepository.findByEmail(username.trim()))
                        .or(() -> userRepository.findByPhone(username.trim()))
                        .map(com.s2admin.module.system.entity.SysUser::getId)
                        .orElse(null);
            }
            loginLog.setUserId(resolvedUserId);
            loginLog.setUsername(username);
            String ip = clientIpResolver.resolve(request);
            loginLog.setIp(ip);
            loginLog.setLocation(IpLocations.guess(ip));
            String ua = request == null ? "" : request.getHeader("User-Agent");
            loginLog.setBrowser(parseBrowser(ua));
            loginLog.setOs(parseOs(ua));
            loginLog.setStatus(status);
            loginLog.setMessage(message);
            loginLog.setLoginTime(LocalDateTime.now());
            loginLogRepository.save(loginLog);
        } catch (Exception e) {
            log.warn("记录登录日志失败: {}", e.getMessage());
        }
    }

    private String parseBrowser(String ua) {
        if (ua == null || ua.isBlank()) {
            return "未知";
        }
        String lower = ua.toLowerCase();
        if (lower.contains("edg/")) return "Edge";
        if (lower.contains("firefox/")) return "Firefox";
        if (lower.contains("chrome/")) return "Chrome";
        if (lower.contains("safari/")) return "Safari";
        return "未知";
    }

    private String parseOs(String ua) {
        if (ua == null || ua.isBlank()) {
            return "未知";
        }
        String lower = ua.toLowerCase();
        if (lower.contains("windows")) return "Windows";
        if (lower.contains("mac os")) return "macOS";
        if (lower.contains("android")) return "Android";
        if (lower.contains("iphone") || lower.contains("ios")) return "iOS";
        if (lower.contains("linux")) return "Linux";
        return "未知";
    }
}
