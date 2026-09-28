package com.s2admin.module.system.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.s2admin.module.common.util.ClientIpResolver;
import com.s2admin.module.common.util.IpLocations;
import com.s2admin.module.security.LoginUser;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.entity.OperationLog;
import com.s2admin.module.system.repository.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面
 * 环绕 @Log 注解方法,记录成功/失败、耗时、IP、请求信息
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final com.s2admin.module.system.service.EntitySnapshotService entitySnapshotService;

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        Throwable error = null;
        Object[] args = joinPoint.getArgs();
        Long id = extractId(args);
        String oldValue = null;
        try {
            oldValue = entitySnapshotService.capture(logAnnotation.module(), logAnnotation.operation(), id);
        } catch (Exception e) {
            log.debug("读取改前快照失败: {}", e.getMessage());
        }
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            try {
                saveLog(joinPoint, logAnnotation, error, System.currentTimeMillis() - start, oldValue, args);
            } catch (Exception e) {
                log.warn("记录操作日志失败: {}", e.getMessage());
            }
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, Log logAnnotation, Throwable error, long cost,
                         String oldValue, Object[] args) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();

        OperationLog operationLog = new OperationLog();
        operationLog.setOperation(logAnnotation.operation());
        operationLog.setModule(logAnnotation.module());
        operationLog.setUrl(request == null ? "" : request.getRequestURI());
        String ip = clientIpResolver.resolve(request);
        operationLog.setIp(ip);
        operationLog.setLocation(IpLocations.guess(ip));
        operationLog.setMethod(joinPoint.getSignature().toShortString());
        operationLog.setExecuteTime(cost);
        operationLog.setStatus(error == null ? 0 : 1);
        if (error != null) {
            operationLog.setErrorMsg(error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage());
        }
        operationLog.setOperationTime(LocalDateTime.now());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            operationLog.setUserId(loginUser.getId());
            operationLog.setUsername(loginUser.getUsername());
        }
        operationLog.setOldValue(oldValue);
        operationLog.setNewValue(serializeArgs(args));
        operationLogRepository.save(operationLog);
    }

    private Long extractId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Long id && id > 0) {
                return id;
            }
        }
        return null;
    }

    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            Object[] filtered = Arrays.stream(args)
                    .filter(a -> !(a instanceof MultipartFile))
                    .filter(a -> !(a instanceof HttpServletRequest))
                    .toArray();
            if (filtered.length == 0) {
                return null;
            }
            String json = objectMapper.writeValueAsString(filtered);
            json = maskSecrets(json);
            return json.length() > 2000 ? json.substring(0, 2000) + "..." : json;
        } catch (Exception e) {
            return null;
        }
    }

    private String maskSecrets(String json) {
        return json.replaceAll("(?i)(\"(?:password|oldPassword|newPassword|refreshToken|token|accessToken)\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
    }
}
