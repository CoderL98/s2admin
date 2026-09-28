package com.s2admin.config;

import com.s2admin.module.system.entity.SysDept;
import com.s2admin.module.system.entity.SysMenu;
import com.s2admin.module.system.entity.SysPermission;
import com.s2admin.module.system.repository.SysDeptRepository;
import com.s2admin.module.system.repository.SysMenuRepository;
import com.s2admin.module.system.repository.SysPermissionRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 为已有库补齐 P1 菜单/权限/部门,不覆盖已有数据。
 */
@Slf4j
@Component
@Order
@RequiredArgsConstructor
public class P1ModuleSeeder implements ApplicationRunner {

    private final SysPermissionRepository permissionRepository;
    private final SysMenuRepository menuRepository;
    private final SysDeptRepository deptRepository;
    private final SysUserRepository userRepository;
    private final com.s2admin.module.system.repository.SysConfigRepository configRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensurePermission("system:dept:view", "部门查看", "system", 51);
        ensurePermission("system:dept:add", "部门新增", "system", 52);
        ensurePermission("system:dept:edit", "部门编辑", "system", 53);
        ensurePermission("system:dept:delete", "部门删除", "system", 54);
        ensurePermission("system:file:view", "文件查看", "system", 61);
        ensurePermission("system:file:delete", "文件删除", "system", 62);
        ensurePermission("system:notice:view", "公告查看", "system", 71);
        ensurePermission("system:notice:add", "公告新增", "system", 72);
        ensurePermission("system:notice:edit", "公告编辑", "system", 73);
        ensurePermission("system:notice:delete", "公告删除", "system", 74);
        ensurePermission("system:message:send", "站内信发送", "system", 81);
        ensurePermission("tools:codegen:generate", "代码生成", "tools", 11);
        ensureConfig("sys.account.maxSessions", "0", "number", "system", "同时在线端数:0不限制,1单端,3最多三端");

        Long systemId = menuRepository.findFirstByPath("/system").map(SysMenu::getId).orElse(0L);
        Long toolsId = menuRepository.findFirstByPath("/tools").map(SysMenu::getId).orElse(0L);
        Long monitorId = menuRepository.findFirstByPath("/monitor").map(SysMenu::getId).orElse(0L);
        if (systemId != 0) {
            ensureMenu("部门管理", systemId, "/system/dept", "Building", 6, "system:dept:view");
            ensureMenu("文件管理", systemId, "/system/file", "Folder", 7, "system:file:view");
            ensureMenu("公告管理", systemId, "/system/notice", "Bell", 8, "system:notice:view");
        }
        if (toolsId != 0) {
            menuRepository.findFirstByPath("/tools/build").ifPresent(m -> {
                if (m.getPermission() == null) {
                    m.setPermission("tools:codegen:generate");
                    menuRepository.save(m);
                }
            });
            reparent("/tools/dict", toolsId);
            reparent("/tools/build", toolsId);
        }
        if (monitorId != 0) {
            reparent("/monitor/login-log", monitorId);
            reparent("/monitor/op-log", monitorId);
            reparent("/monitor/error-log", monitorId);
        }
        if (deptRepository.count() == 0) {
            SysDept root = dept("总经办", 0L, "0", 1, "管理员");
            deptRepository.save(root);
            SysDept rd = dept("研发部", root.getId(), "0," + root.getId(), 2, "张经理");
            SysDept qa = dept("测试部", root.getId(), "0," + root.getId(), 3, "赵测试");
            deptRepository.save(rd);
            deptRepository.save(qa);
            log.info("已初始化默认部门");
        }
        userRepository.findByUsername("admin").ifPresent(admin -> {
            if (admin.getDeptId() == null) {
                deptRepository.findByStatusOrderBySortAsc(0).stream().findFirst().ifPresent(d -> {
                    admin.setDeptId(d.getId());
                    admin.setDeptName(d.getName());
                    userRepository.save(admin);
                });
            }
        });
    }

    private void ensurePermission(String code, String name, String parentCode, int sort) {
        if (permissionRepository.existsByCode(code)) {
            return;
        }
        Long parentId = permissionRepository.findByStatusOrderBySortAsc(0).stream()
                .filter(p -> parentCode.equals(p.getCode()))
                .map(SysPermission::getId)
                .findFirst()
                .orElse(0L);
        SysPermission p = new SysPermission();
        p.setName(name);
        p.setCode(code);
        p.setType(2);
        p.setParentId(parentId);
        p.setSort(sort);
        p.setStatus(0);
        permissionRepository.save(p);
    }

    private void reparent(String path, Long parentId) {
        menuRepository.findFirstByPath(path).ifPresent(m -> {
            if (m.getParentId() == null || !parentId.equals(m.getParentId())) {
                m.setParentId(parentId);
                menuRepository.save(m);
                log.info("已修正菜单父级 {} -> {}", path, parentId);
            }
        });
    }

    private void ensureMenu(String name, Long parentId, String path, String icon, int sort, String permission) {
        if (menuRepository.existsByPath(path)) {
            return;
        }
        SysMenu m = new SysMenu();
        m.setName(name);
        m.setParentId(parentId);
        m.setPath(path);
        m.setComponent(path + "/index");
        m.setIcon(icon);
        m.setSort(sort);
        m.setType(2);
        m.setPermission(permission);
        m.setHidden(0);
        m.setStatus(0);
        menuRepository.save(m);
    }

    private SysDept dept(String name, Long parentId, String ancestors, int sort, String leader) {
        SysDept d = new SysDept();
        d.setName(name);
        d.setParentId(parentId);
        d.setAncestors(ancestors);
        d.setSort(sort);
        d.setLeader(leader);
        d.setStatus(0);
        return d;
    }

    private void ensureConfig(String key, String value, String type, String group, String remark) {
        if (configRepository.existsByConfigKey(key)) {
            return;
        }
        com.s2admin.module.system.entity.SysConfig c = new com.s2admin.module.system.entity.SysConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setConfigType(type);
        c.setGroupCode(group);
        c.setRemark(remark);
        configRepository.save(c);
    }
}
