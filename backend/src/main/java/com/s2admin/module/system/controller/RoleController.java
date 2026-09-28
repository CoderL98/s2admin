package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.form.RoleForm;
import com.s2admin.module.system.form.RoleQuery;
import com.s2admin.module.system.service.RoleService;
import com.s2admin.module.system.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('system:role:view')")
    public Result<PageResult<RoleVO>> page(RoleQuery query) {
        return Result.success(roleService.page(query));
    }

    @GetMapping("/all")
    @PreAuthorize("@ss.hasPermission('system:role:view')")
    public Result<List<RoleVO>> listAll() {
        return Result.success(roleService.listAll());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:role:add')")
    @Log(module = "角色管理", operation = "新增")
    public Result<RoleVO> create(@RequestBody @Valid RoleForm form) {
        return Result.success(roleService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:role:edit')")
    @Log(module = "角色管理", operation = "修改")
    public Result<RoleVO> update(@PathVariable Long id, @RequestBody @Valid RoleForm form) {
        return Result.success(roleService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:role:delete')")
    @Log(module = "角色管理", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("@ss.hasPermission('system:role:view')")
    public Result<List<Long>> getPermissionIds(@PathVariable Long id) {
        return Result.success(roleService.getPermissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@ss.hasPermission('system:role:assign')")
    @Log(module = "角色管理", operation = "授权")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.assignPermissions(id, body.get("permissionIds"));
        return Result.success();
    }
}
