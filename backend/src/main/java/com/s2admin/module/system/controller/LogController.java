package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.entity.ErrorLog;
import com.s2admin.module.system.entity.LoginLog;
import com.s2admin.module.system.entity.OperationLog;
import com.s2admin.module.system.form.LogQuery;
import com.s2admin.module.system.service.LogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 日志管理控制器
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping("/login-log")
    @PreAuthorize("@ss.hasPermission('monitor:log:view')")
    public Result<PageResult<LoginLog>> loginLogPage(LogQuery query) {
        return Result.success(logService.loginLogPage(query));
    }

    @GetMapping("/login-log/export")
    @PreAuthorize("@ss.hasPermission('monitor:log:view')")
    public void exportLoginLog(LogQuery query, HttpServletResponse response) throws IOException {
        writeCsv(response, "login-log.csv", logService.exportLoginLogCsv(query));
    }

    @DeleteMapping("/login-log/clean")
    @PreAuthorize("@ss.hasPermission('monitor:log:delete')")
    @Log(module = "登录日志", operation = "清空")
    public Result<Void> cleanLoginLog() {
        logService.cleanLoginLog();
        return Result.success();
    }

    @GetMapping("/op-log")
    @PreAuthorize("@ss.hasPermission('monitor:log:view')")
    public Result<PageResult<OperationLog>> opLogPage(LogQuery query) {
        return Result.success(logService.opLogPage(query));
    }

    @GetMapping("/op-log/export")
    @PreAuthorize("@ss.hasPermission('monitor:log:view')")
    public void exportOpLog(LogQuery query, HttpServletResponse response) throws IOException {
        writeCsv(response, "operation-log.csv", logService.exportOpLogCsv(query));
    }

    @GetMapping("/op-log/{id}")
    @PreAuthorize("@ss.hasPermission('monitor:log:view')")
    public Result<OperationLog> opLogById(@PathVariable Long id) {
        return Result.success(logService.opLogById(id));
    }

    @DeleteMapping("/op-log/clean")
    @PreAuthorize("@ss.hasPermission('monitor:log:delete')")
    @Log(module = "操作日志", operation = "清空")
    public Result<Void> cleanOpLog() {
        logService.cleanOpLog();
        return Result.success();
    }

    @GetMapping("/error-log")
    @PreAuthorize("@ss.hasPermission('monitor:log:view')")
    public Result<PageResult<ErrorLog>> errorLogPage(LogQuery query) {
        return Result.success(logService.errorLogPage(query));
    }

    @GetMapping("/error-log/{id}")
    @PreAuthorize("@ss.hasPermission('monitor:log:view')")
    public Result<ErrorLog> errorLogById(@PathVariable Long id) {
        return Result.success(logService.errorLogById(id));
    }

    @DeleteMapping("/error-log/clean")
    @PreAuthorize("@ss.hasPermission('monitor:log:delete')")
    @Log(module = "异常日志", operation = "清空")
    public Result<Void> cleanErrorLog() {
        logService.cleanErrorLog();
        return Result.success();
    }

    private void writeCsv(HttpServletResponse response, String filename, String csv) throws IOException {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        response.getOutputStream().write(bytes);
    }
}
