package com.s2admin.config;

import com.s2admin.module.system.entity.SysMenu;
import com.s2admin.module.system.entity.SysPermission;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.repository.SysMenuRepository;
import com.s2admin.module.system.repository.SysPermissionRepository;
import com.s2admin.module.system.repository.SysRoleRepository;
import com.s2admin.module.workflow.entity.SysFlow;
import com.s2admin.module.workflow.entity.SysFlowNode;
import com.s2admin.module.workflow.repository.SysFlowNodeRepository;
import com.s2admin.module.workflow.repository.SysFlowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 补齐审批流程菜单、权限,并在空库放一条默认流程。
 */
@Slf4j
@Component
@Order
@RequiredArgsConstructor
public class WorkflowSeeder implements ApplicationRunner {

    private final SysPermissionRepository permissionRepository;
    private final SysMenuRepository menuRepository;
    private final SysRoleRepository roleRepository;
    private final SysFlowRepository flowRepository;
    private final SysFlowNodeRepository nodeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensurePermission("system:flow:view", "流程查看", 91);
        ensurePermission("system:flow:add", "流程新增", 92);
        ensurePermission("system:flow:edit", "流程编辑", 93);
        ensurePermission("system:flow:delete", "流程删除", 94);
        ensurePermission("system:approval:view", "审批查看", 95);
        ensurePermission("system:approval:apply", "发起审批", 96);
        ensurePermission("system:approval:handle", "处理审批", 97);

        Long systemId = menuRepository.findFirstByPath("/system").map(SysMenu::getId).orElse(0L);
        if (systemId != 0) {
            ensureMenu("流程定义", systemId, "/system/flow", "ClipboardList", 9, "system:flow:view");
            ensureMenu("审批中心", systemId, "/system/approval", "CircleCheck", 10, "system:approval:view");
        }
        grant("ADMIN", "system:flow:view", "system:flow:add", "system:flow:edit", "system:flow:delete",
                "system:approval:view", "system:approval:apply", "system:approval:handle");
        grant("USER", "system:approval:view", "system:approval:apply");
        seedDefaultFlow();
    }

    private void seedDefaultFlow() {
        if (flowRepository.count() > 0 || flowRepository.findByCode("GENERAL").isPresent()) {
            return;
        }
        SysRole admin = roleRepository.findByCode("ADMIN").orElse(null);
        if (admin == null) {
            return;
        }
        SysFlow flow = new SysFlow();
        flow.setName("通用审批");
        flow.setCode("GENERAL");
        flow.setStatus(0);
        flow.setRemark("单节点,由系统管理员角色处理");
        flowRepository.save(flow);
        SysFlowNode node = new SysFlowNode();
        node.setFlowId(flow.getId());
        node.setName("管理员审批");
        node.setSort(1);
        node.setRoleId(admin.getId());
        node.setNodeType(1);
        node.setSignMode(1);
        nodeRepository.save(node);
        log.info("已初始化默认审批流程 GENERAL");
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

    private void grant(String roleCode, String... permissionCodes) {
        SysRole role = roleRepository.findByCode(roleCode).orElse(null);
        if (role == null) {
            return;
        }
        boolean changed = false;
        for (String code : permissionCodes) {
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
