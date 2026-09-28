package com.s2admin.module.monitor.service;

import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.system.repository.ErrorLogRepository;
import com.s2admin.module.system.repository.LoginLogRepository;
import com.s2admin.module.system.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JobHandlerRegistry {

    public static final Set<String> HANDLERS = Set.of("sample.ping", "log.cleanup");

    private final LoginLogRepository loginLogRepository;
    private final OperationLogRepository operationLogRepository;
    private final ErrorLogRepository errorLogRepository;

    public String run(String handler, String params) {
        if (!HANDLERS.contains(handler)) {
            throw new BusinessException("未知任务处理器: " + handler);
        }
        return switch (handler) {
            case "sample.ping" -> "pong " + (params == null ? "" : params);
            case "log.cleanup" -> cleanup(params);
            default -> throw new BusinessException("未知任务处理器: " + handler);
        };
    }

    private String cleanup(String params) {
        int days = 90;
        if (StringUtils.hasText(params)) {
            try {
                days = Integer.parseInt(params.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException("保留天数必须是整数");
            }
        }
        if (days < 1 || days > 3650) {
            throw new BusinessException("保留天数需在 1 到 3650 之间");
        }
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        loginLogRepository.deleteByLoginTimeBefore(before);
        operationLogRepository.deleteByOperationTimeBefore(before);
        errorLogRepository.deleteByErrorTimeBefore(before);
        return "已清理 " + days + " 天前的登录、操作、异常日志";
    }
}
