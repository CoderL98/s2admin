package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.entity.SysDictData;
import com.s2admin.module.system.entity.SysDictType;
import com.s2admin.module.system.form.DictDataForm;
import com.s2admin.module.system.form.DictDataQuery;
import com.s2admin.module.system.form.DictTypeForm;
import com.s2admin.module.system.form.DictTypeQuery;
import com.s2admin.module.system.service.DictService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典管理控制器(类型 + 数据)
 */
@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    // ========== 字典类型 ==========

    @GetMapping("/type")
    @PreAuthorize("@ss.hasPermission('tools:dict:view')")
    public Result<PageResult<SysDictType>> typePage(DictTypeQuery query) {
        return Result.success(dictService.typePage(query));
    }

    @PostMapping("/type")
    @PreAuthorize("@ss.hasPermission('tools:dict:edit')")
    @Log(module = "字典管理", operation = "新增类型")
    public Result<SysDictType> createType(@RequestBody @Valid DictTypeForm form) {
        return Result.success(dictService.createType(form));
    }

    @PutMapping("/type/{id}")
    @PreAuthorize("@ss.hasPermission('tools:dict:edit')")
    @Log(module = "字典管理", operation = "修改类型")
    public Result<SysDictType> updateType(@PathVariable Long id, @RequestBody @Valid DictTypeForm form) {
        return Result.success(dictService.updateType(id, form));
    }

    @DeleteMapping("/type/{id}")
    @PreAuthorize("@ss.hasPermission('tools:dict:edit')")
    @Log(module = "字典管理", operation = "删除类型")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.success();
    }

    // ========== 字典数据 ==========

    @GetMapping("/data")
    @PreAuthorize("@ss.hasPermission('tools:dict:view')")
    public Result<PageResult<SysDictData>> dataPage(DictDataQuery query) {
        return Result.success(dictService.dataPage(query));
    }

    @GetMapping("/data/type/{typeCode}")
    @PreAuthorize("@ss.hasPermission('tools:dict:view')")
    public Result<List<SysDictData>> getDataByTypeCode(@PathVariable String typeCode) {
        return Result.success(dictService.getDataByTypeCode(typeCode));
    }

    @PostMapping("/data")
    @PreAuthorize("@ss.hasPermission('tools:dict:edit')")
    @Log(module = "字典管理", operation = "新增数据")
    public Result<SysDictData> createData(@RequestBody @Valid DictDataForm form) {
        return Result.success(dictService.createData(form));
    }

    @PutMapping("/data/{id}")
    @PreAuthorize("@ss.hasPermission('tools:dict:edit')")
    @Log(module = "字典管理", operation = "修改数据")
    public Result<SysDictData> updateData(@PathVariable Long id, @RequestBody @Valid DictDataForm form) {
        return Result.success(dictService.updateData(id, form));
    }

    @DeleteMapping("/data/{id}")
    @PreAuthorize("@ss.hasPermission('tools:dict:edit')")
    @Log(module = "字典管理", operation = "删除数据")
    public Result<Void> deleteData(@PathVariable Long id) {
        dictService.deleteData(id);
        return Result.success();
    }
}
