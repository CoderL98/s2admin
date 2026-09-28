package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.entity.SysConfig;
import com.s2admin.module.system.form.ConfigForm;
import com.s2admin.module.system.form.ConfigQuery;
import com.s2admin.module.system.service.ConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置控制器
 */
@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('system:config:view')")
    public Result<PageResult<SysConfig>> page(ConfigQuery query) {
        return Result.success(configService.page(query));
    }

    @GetMapping("/key/{key}")
    @PreAuthorize("@ss.hasPermission('system:config:view')")
    public Result<SysConfig> getByConfigKey(@PathVariable String key) {
        return Result.success(configService.getByKey(key));
    }

    @GetMapping("/groups")
    @PreAuthorize("@ss.hasPermission('system:config:view')")
    public Result<java.util.List<String>> groups() {
        return Result.success(configService.listGroups());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:config:add')")
    @Log(module = "系统配置", operation = "新增")
    public Result<SysConfig> create(@RequestBody @Valid ConfigForm form) {
        return Result.success(configService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:config:edit')")
    @Log(module = "系统配置", operation = "修改")
    public Result<SysConfig> update(@PathVariable Long id, @RequestBody @Valid ConfigForm form) {
        return Result.success(configService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:config:delete')")
    @Log(module = "系统配置", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return Result.success();
    }
}
