package com.s2admin.config;

import com.s2admin.module.monitor.entity.SysJob;
import com.s2admin.module.monitor.repository.SysJobRepository;
import com.s2admin.module.system.entity.SysMenu;
import com.s2admin.module.system.entity.SysPermission;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.repository.SysMenuRepository;
import com.s2admin.module.system.repository.SysPermissionRepository;
import com.s2admin.module.system.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 补齐在线用户、服务监控、定时任务的菜单和权限。
 */
@Component
@Order
@RequiredArgsConstructor
public class MonitorSeeder implements ApplicationRunner {

    private final SysPermissionRepository permissionRepository;
    private final SysMenuRepository menuRepository;
    private final SysRoleRepository roleRepository;
    private final SysJobRepository jobRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensurePermission("monitor:online:view", "在线用户查看", 31);
        ensurePermission("monitor:online:kick", "在线用户强退", 32);
        ensurePermission("monitor:server:view", "服务监控", 33);
        ensurePermission("monitor:job:view", "定时任务查看", 34);
        ensurePermission("monitor:job:edit", "定时任务编辑", 35);
        Long monitorId = menuRepository.findFirstByPath("/monitor").map(SysMenu::getId).orElse(0L);
        if (monitorId != 0) {
            ensureMenu("在线用户", monitorId, "/monitor/online", "Devices", 4, "monitor:online:view");
            ensureMenu("服务监控", monitorId, "/monitor/server", "Activity", 5, "monitor:server:view");
            ensureMenu("定时任务", monitorId, "/monitor/job", "Clock", 6, "monitor:job:view");
        }
        grant("ADMIN", "monitor:online:view", "monitor:online:kick", "monitor:server:view",
                "monitor:job:view", "monitor:job:edit");
        grant("AUDITOR", "monitor:online:view", "monitor:server:view", "monitor:job:view");
        seedJob("LOG_CLEANUP", "清理过期日志", "0 0 3 * * *", "log.cleanup", "90",
                "每天 3 点清理 90 天前的日志，默认停用");
        seedJob("PING", "心跳示例", "0 */10 * * * *", "sample.ping", "", "每 10 分钟写一条成功日志，默认停用");
    }

    private void seedJob(String code, String name, String cron, String handler, String params, String remark) {
        if (jobRepository.findByCode(code).isPresent()) {
            return;
        }
        SysJob job = new SysJob();
        job.setName(name);
        job.setCode(code);
        job.setCron(cron);
        job.setHandler(handler);
        job.setParams(params);
        job.setStatus(1);
        job.setRemark(remark);
        job.setNextFireTime(CronExpression.parse(cron).next(LocalDateTime.now()));
        jobRepository.save(job);
    }

    private void ensurePermission(String code, String name, int sort) {
        if (permissionRepository.existsByCode(code)) {
            return;
        }
        Long parentId = permissionRepository.findByCode("monitor").map(SysPermission::getId).orElse(0L);
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

    private void grant(String roleCode, String... codes) {
        SysRole role = roleRepository.findByCode(roleCode).orElse(null);
        if (role == null) {
            return;
        }
        boolean changed = false;
        for (String code : codes) {
            SysPermission permission = permissionRepository.findByCode(code).orElse(null);
            if (permission == null) {
                continue;
            }
            boolean held = role.getPermissions().stream().anyMatch(item -> permission.getId().equals(item.getId()));
            if (!held) {
                role.getPermissions().add(permission);
                changed = true;
            }
        }
        if (changed) {
            roleRepository.save(role);
        }
    }
}
