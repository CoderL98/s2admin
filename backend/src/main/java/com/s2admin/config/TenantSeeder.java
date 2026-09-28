package com.s2admin.config;

import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.system.entity.SysMenu;
import com.s2admin.module.system.entity.SysPermission;
import com.s2admin.module.system.repository.SysMenuRepository;
import com.s2admin.module.system.repository.SysPermissionRepository;
import com.s2admin.module.system.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 默认租户、租户菜单,并把已有普通用户归入默认租户。超级管理员保持 tenant_id 为空。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class TenantSeeder implements ApplicationRunner {

    private final TenantService tenantService;
    private final JdbcTemplate jdbcTemplate;
    private final SysPermissionRepository permissionRepository;
    private final SysMenuRepository menuRepository;
    private final UserPermissionService permissionService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long tenantId = tenantService.defaultId();
        try {
            int moved = jdbcTemplate.update("""
                    UPDATE sys_user SET tenant_id = ? WHERE deleted = 0 AND tenant_id IS NULL AND id NOT IN (
                        SELECT ur.user_id FROM sys_user_role ur
                        JOIN sys_role r ON r.id = ur.role_id
                        WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
                    )
                    """, tenantId);
            if (moved > 0) {
                log.info("已将 {} 个用户归入默认租户", moved);
            }
            jdbcTemplate.update("""
                    UPDATE sys_file SET tenant_id = (
                        SELECT u.tenant_id FROM sys_user u WHERE u.id = sys_file.create_by
                    ) WHERE tenant_id IS NULL AND create_by IS NOT NULL
                    """);
        } catch (Exception e) {
            log.warn("归入默认租户跳过: {}", e.getMessage());
        }
        ensurePermission("system:tenant:view", "租户查看", 98);
        ensurePermission("system:tenant:add", "租户新增", 99);
        ensurePermission("system:tenant:edit", "租户编辑", 100);
        ensurePermission("system:tenant:delete", "租户删除", 101);
        Long systemId = menuRepository.findFirstByPath("/system").map(SysMenu::getId).orElse(0L);
        if (systemId != 0) {
            ensureMenu("租户管理", systemId, "/system/tenant", "Building", 11, "system:tenant:view");
        }
        permissionService.clearAll();
    }

    private void ensurePermission(String code, String name, int sort) {
        if (permissionRepository.existsByCode(code)) {
            return;
        }
        Long parentId = permissionRepository.findByCode("system").map(SysPermission::getId).orElse(0L);
        SysPermission permission = new SysPermission();
        permission.setName(name);
        permission.setCode(code);
        permission.setType(2);
        permission.setParentId(parentId);
        permission.setSort(sort);
        permission.setStatus(0);
        permissionRepository.save(permission);
    }

    private void ensureMenu(String name, Long parentId, String path, String icon, int sort, String permission) {
        if (menuRepository.existsByPath(path)) {
            return;
        }
        SysMenu menu = new SysMenu();
        menu.setName(name);
        menu.setParentId(parentId);
        menu.setPath(path);
        menu.setComponent(path + "/index");
        menu.setIcon(icon);
        menu.setSort(sort);
        menu.setType(2);
        menu.setPermission(permission);
        menu.setHidden(0);
        menu.setStatus(0);
        menuRepository.save(menu);
    }
}
