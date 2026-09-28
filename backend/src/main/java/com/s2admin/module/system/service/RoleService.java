package com.s2admin.module.system.service;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.SecurityUtils;
import com.s2admin.module.security.LoginUser;
import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.system.entity.SysPermission;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.form.RoleForm;
import com.s2admin.module.system.form.RoleQuery;
import com.s2admin.module.common.util.UniqueFields;
import com.s2admin.module.system.repository.SysPermissionRepository;
import com.s2admin.module.system.repository.SysRoleRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import com.s2admin.module.system.vo.RoleVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色服务
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysUserRepository userRepository;
    private final UserPermissionService permissionService;
    private final DataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<RoleVO> page(RoleQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("sort");
            query.setSortDirection("asc");
        }
        Specification<SysRole> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like)
                ));
            }
            if (query.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), query.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = query.toPageable();
        Page<SysRole> page = roleRepository.findAll(spec, pageable);
        java.util.Map<Long, Long> counts = userCounts();
        return PageResult.of(page, role -> toVO(role, counts));
    }

    @Transactional(readOnly = true)
    public List<RoleVO> listAll() {
        java.util.Map<Long, Long> counts = userCounts();
        return roleRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(
                        a.getSort() == null ? 0 : a.getSort(),
                        b.getSort() == null ? 0 : b.getSort()))
                .map(role -> toVO(role, counts))
                .toList();
    }

    @Transactional
    public RoleVO create(RoleForm form) {
        if (SUPER_ADMIN.equals(form.getCode())) {
            throw new BusinessException("不允许创建超级管理员角色");
        }
        if (roleRepository.existsByCode(form.getCode())) {
            throw new BusinessException("角色编码已存在");
        }
        dataScopeService.assertCanAssignDataScope(form.getDataScope());
        Set<SysPermission> permissions = loadPermissions(form.getPermissionIds());
        assertGrantable(permissions);
        SysRole role = new SysRole();
        applyForm(role, form);
        role.setPermissions(permissions);
        roleRepository.save(role);
        return toVO(role);
    }

    @Transactional
    public RoleVO update(Long id, RoleForm form) {
        SysRole role = getEntity(id);
        if (SUPER_ADMIN.equals(role.getCode()) && (form.getStatus() != null && form.getStatus() != 0)) {
            throw new BusinessException("超级管理员角色不允许停用");
        }
        if (SUPER_ADMIN.equals(role.getCode()) && !SUPER_ADMIN.equals(form.getCode())) {
            throw new BusinessException("超级管理员角色编码不允许修改");
        }
        if (!SUPER_ADMIN.equals(role.getCode()) && SUPER_ADMIN.equals(form.getCode())) {
            throw new BusinessException("不允许将角色编码改为 SUPER_ADMIN");
        }
        if (!role.getCode().equals(form.getCode()) && roleRepository.existsByCode(form.getCode())) {
            throw new BusinessException("角色编码已存在");
        }
        assertCanManage(role);
        dataScopeService.assertCanAssignDataScope(form.getDataScope());
        applyForm(role, form);
        roleRepository.save(role);
        permissionService.clearAll();
        return toVO(role);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = getEntity(id);
        if (SUPER_ADMIN.equals(role.getCode())) {
            throw new BusinessException("超级管理员角色不允许删除");
        }
        assertCanManage(role);
        if (userRepository.countByRoleId(id) > 0) {
            throw new BusinessException("该角色仍有用户使用,不允许删除");
        }
        role.setCode(UniqueFields.tombstone(role.getCode(), id, 50));
        roleRepository.save(role);
        roleRepository.deleteRolePermissions(id);
        roleRepository.deleteRoleUsers(id);
        roleRepository.delete(role);
        permissionService.clearAll();
    }

    @Transactional(readOnly = true)
    public List<Long> getPermissionIds(Long roleId) {
        return roleRepository.findPermissionIdsByRoleId(roleId);
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        SysRole role = getEntity(roleId);
        assertCanManage(role);
        Set<SysPermission> permissions = loadPermissions(permissionIds);
        assertGrantable(permissions);
        role.setPermissions(permissions);
        roleRepository.save(role);
        permissionService.clearAll();
    }

    private Set<SysPermission> loadPermissions(List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> distinct = permissionIds.stream().filter(Objects::nonNull).distinct().toList();
        List<SysPermission> found = permissionRepository.findAllById(distinct);
        if (found.size() != distinct.size()) {
            throw new BusinessException("部分权限不存在或已删除");
        }
        return new HashSet<>(found);
    }

    private boolean isPrivileged() {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return false;
        }
        if (loginUser.getRoles() != null && loginUser.getRoles().contains(SUPER_ADMIN)) {
            return true;
        }
        return loginUser.getPermissions() != null && loginUser.getPermissions().contains("*");
    }

    /** 非超管不能改超管角色,也不能改权限或数据范围比自己更宽的角色。 */
    private void assertCanManage(SysRole role) {
        if (role == null || isPrivileged()) {
            return;
        }
        if (SUPER_ADMIN.equals(role.getCode())) {
            throw new BusinessException("不允许修改超级管理员角色");
        }
        dataScopeService.assertCanAssignDataScope(role.getDataScope());
        Set<String> mine = permissionService.loadPermissions(SecurityUtils.getUserId());
        for (String code : activeCodes(role.getPermissions())) {
            if (!mine.contains(code)) {
                throw new BusinessException("不能修改含超出自身权限的角色");
            }
        }
    }

    private void assertGrantable(Set<SysPermission> permissions) {
        if (isPrivileged() || permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<String> mine = permissionService.loadPermissions(SecurityUtils.getUserId());
        for (String code : activeCodes(permissions)) {
            if (!mine.contains(code)) {
                throw new BusinessException("不能分配超出自身范围的权限: " + code);
            }
        }
    }

    private Set<String> activeCodes(Set<SysPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        return permissions.stream()
                .filter(p -> p.getStatus() == null || p.getStatus() == 0)
                .map(SysPermission::getCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private void applyForm(SysRole role, RoleForm form) {
        role.setName(form.getName());
        role.setCode(form.getCode());
        role.setSort(form.getSort() == null ? 0 : form.getSort());
        role.setDataScope(form.getDataScope() == null ? 4 : form.getDataScope());
        role.setStatus(form.getStatus() == null ? 0 : form.getStatus());
        role.setRemark(form.getRemark());
    }

    private SysRole getEntity(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("角色不存在"));
    }

    private RoleVO toVO(SysRole role) {
        return toVO(role, userCounts());
    }

    private RoleVO toVO(SysRole role, java.util.Map<Long, Long> counts) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setName(role.getName());
        vo.setCode(role.getCode());
        vo.setSort(role.getSort());
        vo.setDataScope(role.getDataScope());
        vo.setStatus(role.getStatus());
        vo.setUserCount(role.getId() == null || counts == null ? 0L : counts.getOrDefault(role.getId(), 0L));
        vo.setRemark(role.getRemark());
        vo.setCreateTime(role.getCreateTime());
        return vo;
    }

    private java.util.Map<Long, Long> userCounts() {
        java.util.Map<Long, Long> map = new java.util.HashMap<>();
        for (Object[] row : roleRepository.countUsersGroupByRole()) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }
}
