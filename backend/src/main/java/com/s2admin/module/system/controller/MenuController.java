package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.form.MenuForm;
import com.s2admin.module.system.form.MenuQuery;
import com.s2admin.module.system.service.MenuService;
import com.s2admin.module.system.vo.MenuVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理控制器
 */
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('system:menu:view')")
    public Result<PageResult<MenuVO>> page(MenuQuery query) {
        return Result.success(menuService.page(query));
    }

    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPermission('system:menu:view')")
    public Result<List<MenuVO>> tree() {
        return Result.success(menuService.tree());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:menu:add')")
    @Log(module = "菜单管理", operation = "新增")
    public Result<MenuVO> create(@RequestBody @Valid MenuForm form) {
        return Result.success(menuService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:menu:edit')")
    @Log(module = "菜单管理", operation = "修改")
    public Result<MenuVO> update(@PathVariable Long id, @RequestBody @Valid MenuForm form) {
        return Result.success(menuService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:menu:delete')")
    @Log(module = "菜单管理", operation = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/move")
    @PreAuthorize("@ss.hasPermission('system:menu:edit')")
    @Log(module = "菜单管理", operation = "排序")
    public Result<Void> move(@PathVariable Long id, @RequestParam("direction") String direction) {
        if (!"up".equalsIgnoreCase(direction) && !"down".equalsIgnoreCase(direction)) {
            throw new com.s2admin.module.common.exception.BusinessException("排序方向只能是 up 或 down");
        }
        int delta = "up".equalsIgnoreCase(direction) ? -1 : 1;
        menuService.move(id, delta);
        return Result.success();
    }
}
