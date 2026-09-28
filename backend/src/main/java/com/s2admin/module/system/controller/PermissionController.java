package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.form.PermissionForm;
import com.s2admin.module.system.form.PermissionQuery;
import com.s2admin.module.system.service.PermissionService;
import com.s2admin.module.system.vo.PermissionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理控制器
 */
@RestController
@RequestMapping("/api/system/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('system:permission:view')")
    public Result<PageResult<PermissionVO>> page(PermissionQuery query) {
        return Result.success(permissionService.page(query));
    }

    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPermission('system:permission:view')")
    public Result<List<PermissionVO>> tree() {
        return Result.success(permissionService.tree());
    }

    @GetMapping("/all")
    @PreAuthorize("@ss.hasPermission('system:permission:view')")
    public Result<List<PermissionVO>> listAll() {
        return Result.success(permissionService.listAll());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:permission:add')")
    @Log(module = "权限管理", operation = "新增")
    public Result<PermissionVO> create(@RequestBody @Valid PermissionForm form) {
        return Result.success(permissionService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:permission:edit')")
    @Log(module = "权限管理", operation = "修改")
    public Result<PermissionVO> update(@PathVariable Long id, @RequestBody @Valid PermissionForm form) {
        return Result.success(permissionService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:permission:delete')")
    @Log(module = "权限管理", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.success();
    }
}
