package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.entity.SysNotice;
import com.s2admin.module.system.form.NoticeForm;
import com.s2admin.module.system.form.NoticeQuery;
import com.s2admin.module.system.service.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('system:notice:view')")
    public Result<PageResult<SysNotice>> page(NoticeQuery query) {
        return Result.success(noticeService.page(query));
    }

    @GetMapping("/published")
    public Result<List<SysNotice>> published() {
        return Result.success(noticeService.published());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:notice:add')")
    @Log(module = "公告", operation = "新增")
    public Result<SysNotice> create(@RequestBody @Valid NoticeForm form) {
        return Result.success(noticeService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:notice:edit')")
    @Log(module = "公告", operation = "修改")
    public Result<SysNotice> update(@PathVariable Long id, @RequestBody @Valid NoticeForm form) {
        return Result.success(noticeService.update(id, form));
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('system:notice:edit')")
    @Log(module = "公告", operation = "发布")
    public Result<SysNotice> publish(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        if (body == null || !body.containsKey("published")) {
            throw new BusinessException("请指定发布状态");
        }
        boolean published = Boolean.TRUE.equals(body.get("published"));
        return Result.success(noticeService.publish(id, published));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:notice:delete')")
    @Log(module = "公告", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.success();
    }
}
