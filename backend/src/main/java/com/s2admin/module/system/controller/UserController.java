package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.form.UserForm;
import com.s2admin.module.system.form.UserQuery;
import com.s2admin.module.system.service.UserService;
import com.s2admin.module.system.vo.ImportResultVO;
import com.s2admin.module.system.vo.UserVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('system:user:view')")
    public Result<PageResult<UserVO>> page(UserQuery query) {
        return Result.success(userService.page(query));
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermission('system:user:view')")
    public void export(UserQuery query, HttpServletResponse response) throws IOException {
        String csv = userService.exportCsv(query);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users.csv");
        response.getOutputStream().write(bytes);
    }

    @GetMapping("/import-template")
    @PreAuthorize("@ss.hasPermission('system:user:add')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        byte[] bytes = userService.importTemplate().getBytes(StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=user-import-template.csv");
        response.getOutputStream().write(bytes);
    }

    @PostMapping("/import")
    @PreAuthorize("@ss.hasPermission('system:user:add')")
    @Log(module = "用户管理", operation = "导入")
    public Result<ImportResultVO> importCsv(@RequestParam("file") MultipartFile file) {
        return Result.success(userService.importCsv(file));
    }

    @GetMapping("/export-xlsx")
    @PreAuthorize("@ss.hasPermission('system:user:view')")
    public void exportExcel(UserQuery query, HttpServletResponse response) throws IOException {
        byte[] bytes = userService.exportExcel(query);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users.xlsx");
        response.getOutputStream().write(bytes);
    }

    @PostMapping("/import-xlsx")
    @PreAuthorize("@ss.hasPermission('system:user:add')")
    @Log(module = "用户管理", operation = "导入Excel")
    public Result<ImportResultVO> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.success(userService.importExcel(file));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:user:view')")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:user:add')")
    @Log(module = "用户管理", operation = "新增")
    public Result<UserVO> create(@RequestBody @Valid UserForm form) {
        return Result.success(userService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:user:edit')")
    @Log(module = "用户管理", operation = "修改")
    public Result<UserVO> update(@PathVariable Long id, @RequestBody @Valid UserForm form) {
        return Result.success(userService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    @Log(module = "用户管理", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @DeleteMapping
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    @Log(module = "用户管理", operation = "批量删除")
    public Result<Void> batchDelete(@RequestParam("ids") List<Long> ids) {
        userService.batchDelete(ids);
        return Result.success();
    }

    @PutMapping("/status")
    @PreAuthorize("@ss.hasPermission('system:user:edit')")
    @Log(module = "用户管理", operation = "批量改状态")
    public Result<Void> batchUpdateStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) body.get("ids");
        Integer status = body.get("status") instanceof Number n ? n.intValue() : null;
        if (rawIds == null || rawIds.isEmpty()) {
            return Result.success();
        }
        List<Long> ids = rawIds.stream().map(Number::longValue).toList();
        userService.batchUpdateStatus(ids, status);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermission('system:user:edit')")
    @Log(module = "用户管理", operation = "修改状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.updateStatus(id, body.get("status"));
        return Result.success();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("@ss.hasPermission('system:user:edit')")
    @Log(module = "用户管理", operation = "重置密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("password"));
        return Result.success();
    }
}
