package com.s2admin.module.workflow.controller;

import com.s2admin.module.common.PageQuery;
import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.workflow.form.ApprovalActionForm;
import com.s2admin.module.workflow.form.ApprovalSubmitForm;
import com.s2admin.module.workflow.service.ApprovalService;
import com.s2admin.module.workflow.vo.ApprovalVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/mine")
    @PreAuthorize("@ss.hasPermission('system:approval:apply') or @ss.hasPermission('system:approval:view')")
    public Result<PageResult<ApprovalVO>> mine(PageQuery query) {
        return Result.success(approvalService.mine(query));
    }

    @GetMapping("/pending")
    @PreAuthorize("@ss.hasPermission('system:approval:handle') or @ss.hasPermission('system:approval:view')")
    public Result<PageResult<ApprovalVO>> pending(PageQuery query) {
        return Result.success(approvalService.pending(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:approval:view') or @ss.hasPermission('system:approval:apply') or @ss.hasPermission('system:approval:handle')")
    public Result<ApprovalVO> detail(@PathVariable Long id) {
        return Result.success(approvalService.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:approval:apply')")
    @Log(module = "审批中心", operation = "提交")
    public Result<ApprovalVO> submit(@RequestBody @Valid ApprovalSubmitForm form) {
        return Result.success(approvalService.submit(form));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPermission('system:approval:handle')")
    @Log(module = "审批中心", operation = "通过")
    public Result<ApprovalVO> approve(@PathVariable Long id, @RequestBody(required = false) ApprovalActionForm form) {
        return Result.success(approvalService.approve(id, form));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPermission('system:approval:handle')")
    @Log(module = "审批中心", operation = "驳回")
    public Result<ApprovalVO> reject(@PathVariable Long id, @RequestBody(required = false) ApprovalActionForm form) {
        return Result.success(approvalService.reject(id, form));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPermission('system:approval:apply')")
    @Log(module = "审批中心", operation = "撤回")
    public Result<ApprovalVO> cancel(@PathVariable Long id, @RequestBody(required = false) ApprovalActionForm form) {
        return Result.success(approvalService.cancel(id, form));
    }
}
