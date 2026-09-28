package com.s2admin.module.system.service;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.ParentCycle;
import com.s2admin.module.common.util.SecurityUtils;
import com.s2admin.module.common.util.UniqueFields;
import com.s2admin.module.security.LoginUser;
import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.system.entity.SysPermission;
import com.s2admin.module.system.form.PermissionForm;
import com.s2admin.module.system.form.PermissionQuery;
import com.s2admin.module.system.entity.SysMenu;
import com.s2admin.module.system.repository.SysMenuRepository;
import com.s2admin.module.system.repository.SysPermissionRepository;
import com.s2admin.module.system.vo.PermissionVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限服务
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysPermissionRepository permissionRepository;
    private final SysMenuRepository menuRepository;
    private final UserPermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<PermissionVO> page(PermissionQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("sort");
            query.setSortDirection("asc");
        }
        Specification<SysPermission> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like)
                ));
            }
            if (query.getType() != null) {
                predicates.add(cb.equal(root.get("type"), query.getType()));
            }
            if (query.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), query.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = query.toPageable();
        Page<SysPermission> page = permissionRepository.findAll(spec, pageable);
        java.util.Map<Long, Long> counts = roleCounts();
        return PageResult.of(page, permission -> toVO(permission, counts));
    }

    /**
     * 权限树(全部,不分页)
     */
    @Transactional(readOnly = true)
    public List<PermissionVO> tree() {
        List<SysPermission> all = permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(p -> p.getSort() == null ? 0 : p.getSort()))
                .toList();
        java.util.Map<Long, Long> counts = roleCounts();
        return buildTree(all.stream().map(permission -> toVO(permission, counts)).toList());
    }

    @Transactional(readOnly = true)
    public List<PermissionVO> listAll() {
        java.util.Map<Long, Long> counts = roleCounts();
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(p -> p.getSort() == null ? 0 : p.getSort()))
                .map(permission -> toVO(permission, counts))
                .toList();
    }

    @Transactional
    public PermissionVO create(PermissionForm form) {
        if (permissionRepository.existsByCode(form.getCode())) {
            throw new BusinessException("权限编码已存在");
        }
        ParentCycle.assertAcyclic(null, form.getParentId(), this::parentIdOf);
        SysPermission permission = new SysPermission();
        applyForm(permission, form);
        permissionRepository.save(permission);
        permissionService.clearAll();
        return toVO(permission, roleCounts());
    }

    @Transactional
    public PermissionVO update(Long id, PermissionForm form) {
        SysPermission permission = getEntity(id);
        if (!permission.getCode().equals(form.getCode()) && permissionRepository.existsByCode(form.getCode())) {
            throw new BusinessException("权限编码已存在");
        }
        ParentCycle.assertAcyclic(id, form.getParentId(), this::parentIdOf);
        assertCanEditPermission(permission, form.getCode());
        String oldCode = permission.getCode();
        String path = permission.getPath();
        String icon = permission.getIcon();
        applyForm(permission, form);
        if (form.getPath() == null) {
            permission.setPath(path);
        }
        if (form.getIcon() == null) {
            permission.setIcon(icon);
        }
        permissionRepository.save(permission);
        if (StringUtils.hasText(oldCode) && !oldCode.equals(permission.getCode())) {
            for (SysMenu menu : menuRepository.findByPermission(oldCode)) {
                menu.setPermission(permission.getCode());
                menuRepository.save(menu);
            }
        }
        permissionService.clearAll();
        return toVO(permission, roleCounts());
    }

    @Transactional
    public void delete(Long id) {
        List<SysPermission> all = permissionRepository.findAll();
        boolean hasChild = all.stream().anyMatch(p -> id.equals(p.getParentId()));
        if (hasChild) {
            throw new BusinessException("存在子权限,不允许删除");
        }
        if (permissionRepository.countRoleBindings(id) > 0) {
            throw new BusinessException("该权限仍被角色引用,不允许删除");
        }
        SysPermission permission = getEntity(id);
        assertCanEditPermission(permission, permission.getCode());
        permission.setCode(UniqueFields.tombstone(permission.getCode(), id, 100));
        permissionRepository.save(permission);
        permissionRepository.delete(permission);
        permissionService.clearAll();
    }

    private List<PermissionVO> buildTree(List<PermissionVO> flat) {
        Map<Long, PermissionVO> map = flat.stream().collect(Collectors.toMap(PermissionVO::getId, p -> p));
        List<PermissionVO> roots = new ArrayList<>();
        for (PermissionVO vo : flat) {
            if (vo.getParentId() == null || vo.getParentId() == 0) {
                roots.add(vo);
            } else {
                PermissionVO parent = map.get(vo.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        return roots;
    }

    private Long parentIdOf(Long id) {
        SysPermission parent = permissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("父级权限不存在"));
        return parent.getParentId() == null ? 0L : parent.getParentId();
    }

    private void assertCanEditPermission(SysPermission current, String newCode) {
        if (isPrivileged() || current == null) {
            return;
        }
        java.util.Set<String> mine = permissionService.loadPermissions(SecurityUtils.getUserId());
        if (!org.springframework.util.StringUtils.hasText(current.getCode()) || !mine.contains(current.getCode())) {
            throw new BusinessException("不能修改超出自身范围的权限");
        }
        if (newCode != null && !newCode.equals(current.getCode()) && !mine.contains(newCode)) {
            throw new BusinessException("不能把权限编码改成自己没有的编码");
        }
    }

    private boolean isPrivileged() {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return false;
        }
        if (loginUser.getRoles() != null && loginUser.getRoles().contains(UserPermissionService.SUPER_ADMIN)) {
            return true;
        }
        return loginUser.getPermissions() != null && loginUser.getPermissions().contains("*");
    }

    private void applyForm(SysPermission permission, PermissionForm form) {
        permission.setName(form.getName());
        permission.setCode(form.getCode());
        permission.setType(form.getType() == null ? 2 : form.getType());
        permission.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        permission.setPath(form.getPath());
        permission.setIcon(form.getIcon());
        permission.setSort(form.getSort() == null ? 0 : form.getSort());
        permission.setStatus(form.getStatus() == null ? 0 : form.getStatus());
        permission.setRemark(form.getRemark());
    }

    private SysPermission getEntity(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("权限不存在"));
    }

    private PermissionVO toVO(SysPermission permission, java.util.Map<Long, Long> counts) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setName(permission.getName());
        vo.setCode(permission.getCode());
        vo.setType(permission.getType());
        vo.setParentId(permission.getParentId());
        vo.setPath(permission.getPath());
        vo.setIcon(permission.getIcon());
        vo.setSort(permission.getSort());
        vo.setStatus(permission.getStatus());
        vo.setRoleCount(permission.getId() == null || counts == null ? 0L : counts.getOrDefault(permission.getId(), 0L));
        vo.setRemark(permission.getRemark());
        vo.setCreateTime(permission.getCreateTime());
        return vo;
    }

    private java.util.Map<Long, Long> roleCounts() {
        java.util.Map<Long, Long> map = new java.util.HashMap<>();
        for (Object[] row : permissionRepository.countRolesGroupByPermission()) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }
}