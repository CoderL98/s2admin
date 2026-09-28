package com.s2admin.module.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.s2admin.module.system.entity.SysConfig;
import com.s2admin.module.system.entity.SysDept;
import com.s2admin.module.system.entity.SysDictData;
import com.s2admin.module.system.entity.SysDictType;
import com.s2admin.module.system.entity.SysFile;
import com.s2admin.module.system.entity.SysMenu;
import com.s2admin.module.system.entity.SysNotice;
import com.s2admin.module.system.entity.SysPermission;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.entity.SysUser;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志改前快照:按模块+ID 取出实体标量字段,避免懒加载集合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntitySnapshotService {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public String capture(String module, String operation, Long id) {
        if (id == null || id <= 0 || module == null) {
            return null;
        }
        Class<?> type = resolveType(module, operation);
        if (type == null) {
            return "id=" + id;
        }
        try {
            Object entity = entityManager.find(type, id);
            if (entity == null) {
                return "id=" + id;
            }
            Map<String, Object> snap = toMap(entity);
            snap.put("id", id);
            String json = objectMapper.writeValueAsString(snap);
            return json.length() > 2000 ? json.substring(0, 2000) + "..." : json;
        } catch (Exception e) {
            log.debug("读取操作前快照失败: {}", e.getMessage());
            return "id=" + id;
        }
    }

    private Class<?> resolveType(String module, String operation) {
        String op = operation == null ? "" : operation;
        return switch (module) {
            case "用户管理" -> SysUser.class;
            case "角色管理" -> SysRole.class;
            case "菜单管理" -> SysMenu.class;
            case "权限管理" -> SysPermission.class;
            case "部门管理" -> SysDept.class;
            case "系统配置" -> SysConfig.class;
            case "公告" -> SysNotice.class;
            case "文件" -> SysFile.class;
            case "字典管理" -> op.contains("数据") ? SysDictData.class : SysDictType.class;
            default -> null;
        };
    }

    private Map<String, Object> toMap(Object entity) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (entity instanceof SysUser u) {
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("email", u.getEmail());
            m.put("phone", u.getPhone());
            m.put("status", u.getStatus());
            m.put("deptId", u.getDeptId());
            m.put("deptName", u.getDeptName());
            m.put("pwdReset", u.getPwdReset());
        } else if (entity instanceof SysRole r) {
            m.put("name", r.getName());
            m.put("code", r.getCode());
            m.put("status", r.getStatus());
            m.put("dataScope", r.getDataScope());
        } else if (entity instanceof SysMenu menu) {
            m.put("name", menu.getName());
            m.put("parentId", menu.getParentId());
            m.put("path", menu.getPath());
            m.put("icon", menu.getIcon());
            m.put("sort", menu.getSort());
            m.put("type", menu.getType());
            m.put("hidden", menu.getHidden());
            m.put("status", menu.getStatus());
        } else if (entity instanceof SysPermission p) {
            m.put("name", p.getName());
            m.put("code", p.getCode());
            m.put("type", p.getType());
            m.put("parentId", p.getParentId());
            m.put("status", p.getStatus());
        } else if (entity instanceof SysDept d) {
            m.put("name", d.getName());
            m.put("parentId", d.getParentId());
            m.put("status", d.getStatus());
            m.put("leader", d.getLeader());
        } else if (entity instanceof SysConfig c) {
            m.put("configKey", c.getConfigKey());
            m.put("configValue", c.getConfigValue());
            m.put("groupCode", c.getGroupCode());
        } else if (entity instanceof SysNotice n) {
            m.put("title", n.getTitle());
            m.put("status", n.getStatus());
            m.put("pinned", n.getPinned());
            m.put("type", n.getType());
        } else if (entity instanceof SysFile f) {
            m.put("originalName", f.getOriginalName());
            m.put("storedName", f.getStoredName());
            m.put("category", f.getCategory());
        } else if (entity instanceof SysDictType t) {
            m.put("name", t.getName());
            m.put("code", t.getCode());
            m.put("status", t.getStatus());
        } else if (entity instanceof SysDictData data) {
            m.put("label", data.getLabel());
            m.put("value", data.getValue());
            m.put("dictTypeId", data.getDictTypeId());
            m.put("status", data.getStatus());
        }
        return m;
    }
}
