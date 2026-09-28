package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageQuery;
import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.form.TenantForm;
import com.s2admin.module.system.service.TenantService;
import com.s2admin.module.system.vo.TenantVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('system:tenant:view')")
    public Result<PageResult<TenantVO>> page(PageQuery query) {
        return Result.success(tenantService.page(query));
    }

    @GetMapping("/options")
    @PreAuthorize("@ss.hasPermission('system:tenant:view')")
    public Result<List<TenantVO>> options() {
        return Result.success(tenantService.options());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:tenant:add')")
    @Log(module = "租户", operation = "新增")
    public Result<TenantVO> create(@RequestBody @Valid TenantForm form) {
        return Result.success(tenantService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:tenant:edit')")
    @Log(module = "租户", operation = "修改")
    public Result<TenantVO> update(@PathVariable Long id, @RequestBody @Valid TenantForm form) {
        return Result.success(tenantService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:tenant:delete')")
    @Log(module = "租户", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return Result.success();
    }
}
