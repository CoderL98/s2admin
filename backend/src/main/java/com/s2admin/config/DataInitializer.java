package com.s2admin.config;

import com.s2admin.module.system.entity.*;
import com.s2admin.module.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 开发环境数据初始化
 * 仅当 sys_user 为空时执行,解决 init.sql 未执行或种子数据缺失的问题
 * admin / admin123(动态 BCrypt 生成,不依赖 init.sql 中的哈希)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysMenuRepository menuRepository;
    private final SysConfigRepository configRepository;
    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictDataRepository dictDataRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        log.info("检测到空数据库,开始初始化种子数据(admin/admin123) ...");

        // ========== 角色 ==========
        SysRole superAdmin = role("超级管理员", "SUPER_ADMIN", 1, 1, "系统最高权限,可管理所有功能");
        SysRole admin = role("系统管理员", "ADMIN", 2, 1, "系统管理功能,不可删除超级管理员");
        SysRole user = role("普通用户", "USER", 3, 4, "基本功能使用权限");
        SysRole auditor = role("审计员", "AUDITOR", 4, 1, "仅查看日志,无操作权限");
        SysRole guest = role("访客", "GUEST", 5, 4, "只读权限");
        roleRepository.saveAll(List.of(superAdmin, admin, user, auditor, guest));

        // ========== 权限(先落父节点再挂子节点,避免写死自增 ID) ==========
        SysPermission systemPerm = permissionRepository.save(perm("系统管理", "system", 1, 0L, 1));
        SysPermission monitorPerm = permissionRepository.save(perm("监控中心", "monitor", 1, 0L, 2));
        SysPermission toolsPerm = permissionRepository.save(perm("工具中心", "tools", 1, 0L, 3));
        List<SysPermission> permissions = new ArrayList<>();
        permissions.add(systemPerm);
        permissions.add(monitorPerm);
        permissions.add(toolsPerm);
        permissions.addAll(permissionRepository.saveAll(List.of(
                perm("用户查看", "system:user:view", 2, systemPerm.getId(), 1),
                perm("用户新增", "system:user:add", 2, systemPerm.getId(), 2),
                perm("用户编辑", "system:user:edit", 2, systemPerm.getId(), 3),
                perm("用户删除", "system:user:delete", 2, systemPerm.getId(), 4),
                perm("角色查看", "system:role:view", 2, systemPerm.getId(), 11),
                perm("角色新增", "system:role:add", 2, systemPerm.getId(), 12),
                perm("角色编辑", "system:role:edit", 2, systemPerm.getId(), 13),
                perm("角色删除", "system:role:delete", 2, systemPerm.getId(), 14),
                perm("角色授权", "system:role:assign", 2, systemPerm.getId(), 15),
                perm("菜单查看", "system:menu:view", 2, systemPerm.getId(), 21),
                perm("菜单新增", "system:menu:add", 2, systemPerm.getId(), 22),
                perm("菜单编辑", "system:menu:edit", 2, systemPerm.getId(), 23),
                perm("菜单删除", "system:menu:delete", 2, systemPerm.getId(), 24),
                perm("权限查看", "system:permission:view", 2, systemPerm.getId(), 31),
                perm("权限新增", "system:permission:add", 2, systemPerm.getId(), 32),
                perm("权限编辑", "system:permission:edit", 2, systemPerm.getId(), 33),
                perm("权限删除", "system:permission:delete", 2, systemPerm.getId(), 34),
                perm("配置查看", "system:config:view", 2, systemPerm.getId(), 41),
                perm("配置新增", "system:config:add", 2, systemPerm.getId(), 42),
                perm("配置编辑", "system:config:edit", 2, systemPerm.getId(), 43),
                perm("配置删除", "system:config:delete", 2, systemPerm.getId(), 44),
                perm("日志查看", "monitor:log:view", 2, monitorPerm.getId(), 1),
                perm("日志清空", "monitor:log:delete", 2, monitorPerm.getId(), 2),
                perm("字典查看", "tools:dict:view", 2, toolsPerm.getId(), 1),
                perm("字典编辑", "tools:dict:edit", 2, toolsPerm.getId(), 2)
        )));

        // ========== 菜单 ==========
        SysMenu systemMenu = menuRepository.save(menu("系统管理", 0L, "/system", "Layout", "Setting", 1, 1, null));
        SysMenu monitorMenu = menuRepository.save(menu("系统监控", 0L, "/monitor", "Layout", "Monitor", 2, 1, null));
        SysMenu toolsMenu = menuRepository.save(menu("系统工具", 0L, "/tools", "Layout", "Wrench", 3, 1, null));
        menuRepository.saveAll(List.of(
                menu("用户管理", systemMenu.getId(), "/system/user", "/system/user/index", "User", 1, 2, "system:user:view"),
                menu("角色管理", systemMenu.getId(), "/system/role", "/system/role/index", "Role", 2, 2, "system:role:view"),
                menu("菜单管理", systemMenu.getId(), "/system/menu", "/system/menu/index", "Menu", 3, 2, "system:menu:view"),
                menu("权限管理", systemMenu.getId(), "/system/permission", "/system/permission/index", "Shield", 4, 2, "system:permission:view"),
                menu("系统配置", systemMenu.getId(), "/system/config", "/system/config/index", "Tool", 5, 2, "system:config:view"),
                menu("登录日志", monitorMenu.getId(), "/monitor/login-log", "/monitor/login-log/index", "Log", 1, 2, "monitor:log:view"),
                menu("操作日志", monitorMenu.getId(), "/monitor/op-log", "/monitor/op-log/index", "FileText", 2, 2, "monitor:log:view"),
                menu("异常日志", monitorMenu.getId(), "/monitor/error-log", "/monitor/error-log/index", "AlertTriangle", 3, 2, "monitor:log:view"),
                menu("字典管理", toolsMenu.getId(), "/tools/dict", "/tools/dict/index", "Book", 1, 2, "tools:dict:view"),
                menu("代码生成", toolsMenu.getId(), "/tools/build", "/tools/build/index", "Code", 2, 2, null)
        ));

        // ========== 超级管理员用户 ==========
        SysUser adminUser = new SysUser();
        adminUser.setUsername("admin");
        adminUser.setPassword(passwordEncoder.encode("admin123"));
        adminUser.setNickname("超级管理员");
        adminUser.setEmail("admin@example.com");
        adminUser.setPhone("13800138000");
        adminUser.setStatus(0);
        adminUser.setRoles(Set.of(superAdmin));
        userRepository.save(adminUser);

        // 超级管理员拥有全部权限
        // 同一事务内直接修改托管实体,提交时自动 flush;避免二次 save 触发 merge,
        // merge 会对未初始化懒集合执行 clear(),Hibernate 6.6 下会抛 UnsupportedOperationException
        superAdmin.setPermissions(new HashSet<>(permissions));

        // ========== 系统配置 ==========
        configRepository.saveAll(List.of(
                config("sys.user.initPassword", "123456", "string", "system", "用户初始密码"),
                config("sys.account.captchaEnabled", "true", "boolean", "system", "是否启用验证码"),
                config("sys.account.captchaExpiration", "5", "number", "system", "验证码有效期(分钟)"),
                config("sys.account.lockThreshold", "5", "number", "system", "登录失败锁定阈值"),
                config("sys.account.lockDuration", "30", "number", "system", "账号锁定时长(分钟)")
        ));

        // ========== 字典 ==========
        SysDictType userStatus = dictType("用户状态", "user_status", "用户账号状态");
        SysDictType roleStatus = dictType("角色状态", "role_status", "角色启用状态");
        SysDictType menuType = dictType("菜单类型", "menu_type", "菜单节点类型");
        SysDictType permissionType = dictType("权限类型", "permission_type", "权限节点类型");
        SysDictType loginStatus = dictType("登录状态", "login_status", "登录成功/失败");
        dictTypeRepository.saveAll(List.of(userStatus, roleStatus, menuType, permissionType, loginStatus));

        dictDataRepository.saveAll(List.of(
                data(userStatus.getId(), "正常", "0", 1),
                data(userStatus.getId(), "禁用", "1", 2),
                data(userStatus.getId(), "锁定", "2", 3),
                data(userStatus.getId(), "过期", "3", 4),
                data(roleStatus.getId(), "正常", "0", 1),
                data(roleStatus.getId(), "停用", "1", 2),
                data(menuType.getId(), "目录", "1", 1),
                data(menuType.getId(), "菜单", "2", 2),
                data(menuType.getId(), "按钮", "3", 3),
                data(permissionType.getId(), "菜单权限", "1", 1),
                data(permissionType.getId(), "按钮权限", "2", 2),
                data(permissionType.getId(), "API权限", "3", 3),
                data(loginStatus.getId(), "成功", "0", 1),
                data(loginStatus.getId(), "失败", "1", 2)
        ));

        log.info("种子数据初始化完成: admin / admin123");
    }

    private SysRole role(String name, String code, int sort, int dataScope, String remark) {
        SysRole r = new SysRole();
        r.setName(name);
        r.setCode(code);
        r.setSort(sort);
        r.setDataScope(dataScope);
        r.setStatus(0);
        r.setRemark(remark);
        return r;
    }

    private SysPermission perm(String name, String code, int type, Long parentId, int sort) {
        SysPermission p = new SysPermission();
        p.setName(name);
        p.setCode(code);
        p.setType(type);
        p.setParentId(parentId);
        p.setSort(sort);
        p.setStatus(0);
        return p;
    }

    private SysMenu menu(String name, Long parentId, String path, String component, String icon, int sort, int type, String permission) {
        SysMenu m = new SysMenu();
        m.setName(name);
        m.setParentId(parentId);
        m.setPath(path);
        m.setComponent(component);
        m.setIcon(icon);
        m.setSort(sort);
        m.setType(type);
        m.setPermission(permission);
        m.setHidden(0);
        m.setStatus(0);
        return m;
    }

    private SysConfig config(String key, String value, String type, String group, String remark) {
        SysConfig c = new SysConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setConfigType(type);
        c.setGroupCode(group);
        c.setRemark(remark);
        return c;
    }

    private SysDictType dictType(String name, String code, String remark) {
        SysDictType t = new SysDictType();
        t.setName(name);
        t.setCode(code);
        t.setStatus(0);
        t.setRemark(remark);
        return t;
    }

    private SysDictData data(Long typeId, String label, String value, int sort) {
        SysDictData d = new SysDictData();
        d.setDictTypeId(typeId);
        d.setLabel(label);
        d.setValue(value);
        d.setSort(sort);
        d.setStatus(0);
        return d;
    }
}
