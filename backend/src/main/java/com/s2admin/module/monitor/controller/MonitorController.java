package com.s2admin.module.monitor.controller;

import com.s2admin.module.common.PageQuery;
import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.monitor.entity.SysJob;
import com.s2admin.module.monitor.entity.SysJobLog;
import com.s2admin.module.monitor.form.JobForm;
import com.s2admin.module.monitor.service.JobService;
import com.s2admin.module.monitor.service.OnlineUserService;
import com.s2admin.module.monitor.service.ServerMonitorService;
import com.s2admin.module.system.annotation.Log;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final OnlineUserService onlineUserService;
    private final ServerMonitorService serverMonitorService;
    private final JobService jobService;

    @GetMapping("/online")
    @PreAuthorize("@ss.hasPermission('monitor:online:view')")
    public Result<List<Map<String, Object>>> online() {
        return Result.success(onlineUserService.list());
    }

    @DeleteMapping("/online/{userId}")
    @PreAuthorize("@ss.hasPermission('monitor:online:kick')")
    @Log(module = "在线用户", operation = "强退")
    public Result<Void> kickUser(@PathVariable Long userId) {
        onlineUserService.kickUser(userId);
        return Result.success();
    }

    @DeleteMapping("/online/{userId}/{sid}")
    @PreAuthorize("@ss.hasPermission('monitor:online:kick')")
    @Log(module = "在线用户", operation = "强退设备")
    public Result<Void> kickDevice(@PathVariable Long userId, @PathVariable String sid) {
        onlineUserService.kickDevice(userId, sid);
        return Result.success();
    }

    @GetMapping("/server")
    @PreAuthorize("@ss.hasPermission('monitor:server:view')")
    public Result<Map<String, Object>> server() {
        return Result.success(serverMonitorService.snapshot());
    }

    @GetMapping("/job")
    @PreAuthorize("@ss.hasPermission('monitor:job:view')")
    public Result<PageResult<SysJob>> jobs(PageQuery query) {
        return Result.success(jobService.page(query));
    }

    @GetMapping("/job/log")
    @PreAuthorize("@ss.hasPermission('monitor:job:view')")
    public Result<PageResult<SysJobLog>> logs(@RequestParam(required = false) Long jobId, PageQuery query) {
        return Result.success(jobService.logs(jobId, query));
    }

    @PostMapping("/job")
    @PreAuthorize("@ss.hasPermission('monitor:job:edit')")
    @Log(module = "定时任务", operation = "新增")
    public Result<SysJob> create(@RequestBody @Valid JobForm form) {
        return Result.success(jobService.create(form));
    }

    @PutMapping("/job/{id}")
    @PreAuthorize("@ss.hasPermission('monitor:job:edit')")
    @Log(module = "定时任务", operation = "修改")
    public Result<SysJob> update(@PathVariable Long id, @RequestBody @Valid JobForm form) {
        return Result.success(jobService.update(id, form));
    }

    @DeleteMapping("/job/{id}")
    @PreAuthorize("@ss.hasPermission('monitor:job:edit')")
    @Log(module = "定时任务", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return Result.success();
    }

    @PostMapping("/job/{id}/run")
    @PreAuthorize("@ss.hasPermission('monitor:job:edit')")
    @Log(module = "定时任务", operation = "执行")
    public Result<Void> run(@PathVariable Long id) {
        jobService.runNow(id);
        return Result.success();
    }
}
