package com.s2admin.module.common.exception;

import com.s2admin.module.common.Result;
import com.s2admin.module.common.ResultCode;
import com.s2admin.module.system.entity.ErrorLog;
import com.s2admin.module.system.repository.ErrorLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorLogRepository errorLogRepository;

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("参数校验失败");
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return Result.error(ResultCode.BAD_REQUEST, "请求体格式错误");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public Result<Void> handleMissingParam(Exception e) {
        return Result.error(ResultCode.BAD_REQUEST, "缺少必要参数");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return Result.error(ResultCode.BAD_REQUEST, "上传文件超过大小限制");
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, MultipartException.class})
    public Result<Void> handleMediaType(Exception e) {
        return Result.error(ResultCode.BAD_REQUEST, "请求类型不正确,请使用 multipart 上传文件");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.error(ResultCode.UNAUTHORIZED, "用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(ResultCode.FORBIDDEN, "没有操作权限");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("数据完整性异常", e);
        return Result.error(ResultCode.BAD_REQUEST, "数据冲突:唯一键重复或存在关联数据,操作被拒绝");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        return Result.error(ResultCode.NOT_FOUND, "资源不存在");
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public Result<Void> handleBadSort(PropertyReferenceException e) {
        return Result.error(ResultCode.BAD_REQUEST, "排序字段不合法");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常", e);
        // 记录异常日志,便于监控排查
        try {
            ErrorLog errorLog = new ErrorLog();
            errorLog.setTraceId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            errorLog.setIp(request.getRemoteAddr());
            errorLog.setUrl(request.getRequestURI());
            errorLog.setMethod(request.getMethod());
            errorLog.setException(e.getClass().getName() + ": " + e.getMessage());
            errorLog.setStackTrace(stackTraceToString(e));
            errorLog.setErrorTime(LocalDateTime.now());
            errorLogRepository.save(errorLog);
        } catch (Exception ex) {
            log.error("记录异常日志失败", ex);
        }
        return Result.error(ResultCode.INTERNAL_ERROR, "系统繁忙,请稍后重试");
    }

    private String stackTraceToString(Throwable e) {
        StackTraceElement[] trace = e.getStackTrace();
        int limit = Math.min(trace.length, 30);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            sb.append("at ").append(trace[i]).append('\n');
        }
        return sb.toString();
    }
}
