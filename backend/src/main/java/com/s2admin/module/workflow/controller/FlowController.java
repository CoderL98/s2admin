package com.s2admin.module.workflow.controller;

import com.s2admin.module.common.PageQuery;
import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.workflow.form.FlowSaveForm;
import com.s2admin.module.workflow.service.FlowService;
import com.s2admin.module.workflow.vo.FlowVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflow/flow")
@RequiredArgsConstructor
public class FlowController {

    private final FlowService flowService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('system:flow:view')")
    public Result<PageResult<FlowVO>> page(PageQuery query) {
        return Result.success(flowService.page(query));
    }

    @GetMapping("/options")
    @PreAuthorize("@ss.hasPermission('system:approval:apply')")
    public Result<List<FlowVO>> options() {
        return Result.success(flowService.options());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:flow:view')")
    public Result<FlowVO> get(@PathVariable Long id) {
        return Result.success(flowService.get(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:flow:add')")
    @Log(module = "审批流程", operation = "新增")
    public Result<FlowVO> create(@RequestBody @Valid FlowSaveForm form) {
        return Result.success(flowService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:flow:edit')")
    @Log(module = "审批流程", operation = "修改")
    public Result<FlowVO> update(@PathVariable Long id, @RequestBody @Valid FlowSaveForm form) {
        return Result.success(flowService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:flow:delete')")
    @Log(module = "审批流程", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        flowService.delete(id);
        return Result.success();
    }
}
