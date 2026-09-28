package com.s2admin.module.system.controller;

import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.form.DeptForm;
import com.s2admin.module.system.service.DeptService;
import com.s2admin.module.system.vo.DeptVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/dept")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPermission('system:dept:view')")
    public Result<List<DeptVO>> tree() {
        return Result.success(deptService.tree());
    }

    @GetMapping("/all")
    @PreAuthorize("@ss.hasPermission('system:dept:view')")
    public Result<List<DeptVO>> listAll() {
        return Result.success(deptService.listAll());
    }

    /** 登录用户可选部门(用户表单/筛选) */
    @GetMapping("/options")
    public Result<List<DeptVO>> options() {
        return Result.success(deptService.listAll());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:dept:add')")
    @Log(module = "部门管理", operation = "新增")
    public Result<DeptVO> create(@RequestBody @Valid DeptForm form) {
        return Result.success(deptService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:dept:edit')")
    @Log(module = "部门管理", operation = "修改")
    public Result<DeptVO> update(@PathVariable Long id, @RequestBody @Valid DeptForm form) {
        return Result.success(deptService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:dept:delete')")
    @Log(module = "部门管理", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.success();
    }
}
