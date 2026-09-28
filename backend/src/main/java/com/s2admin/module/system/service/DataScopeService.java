package com.s2admin.module.system.service;

import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.SecurityUtils;
import com.s2admin.module.security.LoginUser;
import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.system.entity.SysDept;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.entity.SysUser;
import com.s2admin.module.system.repository.SysDeptRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按角色 dataScope 过滤列表:1 全部 2 本部门及以下 3 本部门 4 本人。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    /** level: 1 全部 2 本部门及以下 3 本部门 4 本人 */
    public record Scope(boolean all, boolean selfOnly, Set<Long> deptIds, int level) {
    }

    private final SysUserRepository userRepository;
    private final SysDeptRepository deptRepository;

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Scope current() {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return new Scope(false, true, Set.of(), 4);
        }
        if (loginUser.getPermissions() != null && loginUser.getPermissions().contains("*")) {
            return new Scope(true, false, Set.of(), 1);
        }
        if (loginUser.getRoles() != null && loginUser.getRoles().contains(UserPermissionService.SUPER_ADMIN)) {
            return new Scope(true, false, Set.of(), 1);
        }
        SysUser user = userRepository.findWithRolesById(loginUser.getId()).orElse(null);
        if (user == null) {
            return new Scope(false, true, Set.of(), 4);
        }
        int best = 4;
        for (SysRole role : user.getRoles()) {
            if (role.getStatus() != null && role.getStatus() != 0) {
                continue;
            }
            int scope = role.getDataScope() == null ? 4 : role.getDataScope();
            best = Math.min(best, scope);
        }
        if (best <= 1) {
            return new Scope(true, false, Set.of(), 1);
        }
        if (best >= 4 || user.getDeptId() == null) {
            return new Scope(false, true, Set.of(), 4);
        }
        Set<Long> ids = new HashSet<>();
        ids.add(user.getDeptId());
        if (best == 2) {
            ids.addAll(descendantIds(user.getDeptId()));
        }
        return new Scope(false, false, ids, best);
    }

    public void assertCanAssignDataScope(Integer dataScope) {
        int requested = dataScope == null ? 4 : dataScope;
        if (requested < 1 || requested > 4) {
            throw new BusinessException("数据范围不合法");
        }
        Scope mine = current();
        if (mine.all()) {
            return;
        }
        if (requested < mine.level()) {
            throw new BusinessException("不能设置比自己更宽的数据范围");
        }
    }

    public void applyUser(Root<SysUser> root, CriteriaBuilder cb, List<Predicate> predicates) {
        Scope scope = current();
        if (scope.all()) {
            return;
        }
        Long uid = SecurityUtils.getUserId();
        if (scope.selfOnly()) {
            predicates.add(cb.equal(root.get("id"), uid));
            return;
        }
        if (scope.deptIds() == null || scope.deptIds().isEmpty()) {
            predicates.add(cb.equal(root.get("id"), uid));
            return;
        }
        Predicate inDept = root.get("deptId").in(scope.deptIds());
        Predicate self = cb.equal(root.get("id"), uid);
        predicates.add(cb.or(inDept, self));
    }

    public boolean canAccessUser(SysUser target) {
        if (target == null) {
            return false;
        }
        Scope scope = current();
        if (scope.all()) {
            return true;
        }
        Long uid = SecurityUtils.getUserId();
        if (uid != null && uid.equals(target.getId())) {
            return true;
        }
        if (scope.selfOnly()) {
            return false;
        }
        return target.getDeptId() != null && scope.deptIds().contains(target.getDeptId());
    }

    public void assertCanAccessUser(SysUser target) {
        if (!canAccessUser(target)) {
            throw BusinessException.forbidden("超出数据权限范围");
        }
    }

    public void assertCanAssignDept(Long deptId) {
        Scope scope = current();
        if (scope.all()) {
            return;
        }
        if (scope.selfOnly()) {
            throw BusinessException.forbidden("当前数据权限不允许操作其他用户");
        }
        if (deptId == null || !scope.deptIds().contains(deptId)) {
            throw BusinessException.forbidden("不能操作该部门的数据");
        }
    }

    /**
     * @return null 表示全部可见;否则仅这些用户 ID
     */
    public Set<Long> visibleUserIds() {
        Scope scope = current();
        if (scope.all()) {
            return null;
        }
        Set<Long> ids = new HashSet<>();
        Long uid = SecurityUtils.getUserId();
        if (uid != null) {
            ids.add(uid);
        }
        if (!scope.selfOnly() && scope.deptIds() != null && !scope.deptIds().isEmpty()) {
            ids.addAll(userRepository.findIdsByDeptIdIn(scope.deptIds()));
        }
        return ids;
    }

    public void applyOwner(Root<?> root, String field, CriteriaBuilder cb, List<Predicate> predicates) {
        Set<Long> ids = visibleUserIds();
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            predicates.add(cb.disjunction());
            return;
        }
        predicates.add(root.get(field).in(ids));
    }

    public void assertCanAccessOwner(Long ownerId) {
        Set<Long> ids = visibleUserIds();
        if (ids == null) {
            return;
        }
        if (ownerId == null || !ids.contains(ownerId)) {
            throw BusinessException.forbidden("超出数据权限范围");
        }
    }

    public void assertCanAccessDept(Long deptId) {
        Scope scope = current();
        if (scope.all()) {
            return;
        }
        if (deptId == null) {
            throw BusinessException.forbidden("超出数据权限范围");
        }
        if (scope.selfOnly()) {
            SysUser me = userRepository.findById(SecurityUtils.getUserId()).orElse(null);
            if (me == null || !deptId.equals(me.getDeptId())) {
                throw BusinessException.forbidden("超出数据权限范围");
            }
            return;
        }
        if (scope.deptIds() == null || !scope.deptIds().contains(deptId)) {
            throw BusinessException.forbidden("超出数据权限范围");
        }
    }

    public Set<Long> visibleDeptIdsWithAncestors() {
        Scope scope = current();
        if (scope.all()) {
            return null;
        }
        Set<Long> allowed = new HashSet<>();
        if (scope.selfOnly()) {
            userRepository.findById(SecurityUtils.getUserId())
                    .map(SysUser::getDeptId)
                    .ifPresent(allowed::add);
        } else if (scope.deptIds() != null) {
            allowed.addAll(scope.deptIds());
        }
        Set<Long> withAncestors = new HashSet<>(allowed);
        for (SysDept dept : deptRepository.findAll()) {
            if (!allowed.contains(dept.getId()) || !StringUtils.hasText(dept.getAncestors())) {
                continue;
            }
            for (String part : dept.getAncestors().split(",")) {
                String p = part.trim();
                if (!p.isEmpty() && !"0".equals(p)) {
                    try {
                        withAncestors.add(Long.parseLong(p));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return withAncestors;
    }

    public Set<Long> descendantIds(Long deptId) {
        Set<Long> ids = new HashSet<>();
        if (deptId == null) {
            return ids;
        }
        String needle = String.valueOf(deptId);
        for (SysDept dept : deptRepository.findAll()) {
            if (deptId.equals(dept.getId())) {
                continue;
            }
            if (!StringUtils.hasText(dept.getAncestors())) {
                continue;
            }
            boolean match = Arrays.stream(dept.getAncestors().split(","))
                    .map(String::trim)
                    .anyMatch(needle::equals);
            if (match) {
                ids.add(dept.getId());
            }
        }
        return ids;
    }
}
