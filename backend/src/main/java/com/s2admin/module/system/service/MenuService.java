package com.s2admin.module.system.service;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.ParentCycle;
import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.system.entity.SysMenu;
import com.s2admin.module.system.form.MenuForm;
import com.s2admin.module.system.form.MenuQuery;
import com.s2admin.module.system.repository.SysMenuRepository;
import com.s2admin.module.system.vo.MenuVO;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuRepository menuRepository;
    private final UserPermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<MenuVO> page(MenuQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("sort");
            query.setSortDirection("asc");
        }
        Specification<SysMenu> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("path")), like)
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
        Page<SysMenu> page = menuRepository.findAll(spec, pageable);
        return PageResult.of(page, this::toVO);
    }

    /**
     * 菜单树(全部,不分页)
     */
    @Transactional(readOnly = true)
    public List<MenuVO> tree() {
        List<SysMenu> all = menuRepository.findAll().stream()
                .sorted(Comparator.comparing(m -> m.getSort() == null ? 0 : m.getSort()))
                .toList();
        return buildTree(all.stream().map(this::toVO).toList());
    }

    @Transactional
    public MenuVO create(MenuForm form) {
        validatePath(form);
        ParentCycle.assertAcyclic(null, form.getParentId(), this::parentIdOf);
        SysMenu menu = new SysMenu();
        applyForm(menu, form);
        menuRepository.save(menu);
        return toVO(menu);
    }

    @Transactional
    public MenuVO update(Long id, MenuForm form) {
        validatePath(form);
        SysMenu menu = getEntity(id);
        ParentCycle.assertAcyclic(id, form.getParentId(), this::parentIdOf);
        String redirect = menu.getRedirect();
        applyForm(menu, form);
        if (form.getRedirect() == null) {
            menu.setRedirect(redirect);
        }
        menuRepository.save(menu);
        return toVO(menu);
    }

    @Transactional
    public void delete(Long id) {
        deleteRecursive(id);
    }

    @Transactional
    public void move(Long id, int direction) {
        if (direction != -1 && direction != 1) {
            throw new BusinessException("排序方向不合法");
        }
        SysMenu menu = getEntity(id);
        Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
        List<SysMenu> siblings = menuRepository.findByParentId(parentId).stream()
                .sorted(Comparator
                        .comparing((SysMenu m) -> m.getSort() == null ? 0 : m.getSort())
                        .thenComparing(SysMenu::getId))
                .toList();
        int idx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (id.equals(siblings.get(i).getId())) {
                idx = i;
                break;
            }
        }
        int swap = idx + direction;
        if (idx < 0 || swap < 0 || swap >= siblings.size()) {
            return;
        }
        Integer a = siblings.get(idx).getSort() == null ? 0 : siblings.get(idx).getSort();
        Integer b = siblings.get(swap).getSort() == null ? 0 : siblings.get(swap).getSort();
        if (a.equals(b)) {
            for (int i = 0; i < siblings.size(); i++) {
                siblings.get(i).setSort(i * 10);
                menuRepository.save(siblings.get(i));
            }
            a = idx * 10;
            b = swap * 10;
        }
        siblings.get(idx).setSort(b);
        siblings.get(swap).setSort(a);
        menuRepository.save(siblings.get(idx));
        menuRepository.save(siblings.get(swap));
    }

    private void deleteRecursive(Long id) {
        for (SysMenu child : menuRepository.findByParentId(id)) {
            deleteRecursive(child.getId());
        }
        menuRepository.delete(getEntity(id));
    }

    /**
     * 获取当前用户可见菜单树(用于前端动态菜单)
     */
    @Transactional(readOnly = true)
    public List<MenuVO> getUserMenus(Long userId) {
        Set<String> permissions = permissionService.loadPermissions(userId);
        boolean isSuper = permissions.contains("*");
        List<SysMenu> all = menuRepository.findByStatusOrderBySortAsc(0);
        if (isSuper) {
            return buildTree(all.stream().map(this::toVO).toList());
        }
        Map<Long, SysMenu> byId = all.stream().collect(Collectors.toMap(SysMenu::getId, m -> m));
        Set<Long> visible = new HashSet<>();
        for (SysMenu menu : all) {
            if (menu.getHidden() != null && menu.getHidden() == 1) {
                continue;
            }
            if (!directlyVisible(menu, permissions)) {
                continue;
            }
            visible.add(menu.getId());
            Long parentId = menu.getParentId();
            for (int guard = 0; parentId != null && parentId != 0 && guard < 64; guard++) {
                visible.add(parentId);
                SysMenu parent = byId.get(parentId);
                if (parent == null) {
                    break;
                }
                parentId = parent.getParentId();
            }
        }
        List<MenuVO> flat = all.stream()
                .filter(m -> visible.contains(m.getId()))
                .filter(m -> m.getHidden() == null || m.getHidden() == 0)
                .map(this::toVO)
                .toList();
        return buildTree(flat);
    }

    /**
     * 无权限码的内部页面不对所有人开放;目录只作为已授权子菜单的祖先出现。
     * 外链未填权限码时仍可展示。
     */
    private boolean directlyVisible(SysMenu menu, Set<String> permissions) {
        if (StringUtils.hasText(menu.getPermission())) {
            return permissions.contains(menu.getPermission());
        }
        String path = menu.getPath();
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }

    private List<MenuVO> buildTree(List<MenuVO> flat) {
        Map<Long, MenuVO> map = flat.stream().collect(Collectors.toMap(MenuVO::getId, m -> m));
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO vo : flat) {
            if (vo.getParentId() == null || vo.getParentId() == 0) {
                roots.add(vo);
            } else {
                MenuVO parent = map.get(vo.getParentId());
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
        SysMenu parent = menuRepository.findById(id)
                .orElseThrow(() -> new BusinessException("父级菜单不存在"));
        return parent.getParentId() == null ? 0L : parent.getParentId();
    }

    private void validatePath(MenuForm form) {
        Integer type = form.getType() == null ? 2 : form.getType();
        if (type != 3 && !StringUtils.hasText(form.getPath())) {
            throw new BusinessException("目录和菜单的路由路径不能为空");
        }
    }

    private void applyForm(SysMenu menu, MenuForm form) {
        menu.setName(form.getName());
        menu.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        menu.setPath(form.getPath());
        menu.setComponent(form.getComponent());
        menu.setRedirect(form.getRedirect());
        menu.setPermission(form.getPermission());
        menu.setIcon(form.getIcon());
        menu.setSort(form.getSort() == null ? 0 : form.getSort());
        menu.setType(form.getType() == null ? 2 : form.getType());
        menu.setHidden(form.getHidden() == null ? 0 : form.getHidden());
        menu.setStatus(form.getStatus() == null ? 0 : form.getStatus());
        menu.setRemark(form.getRemark());
    }

    private SysMenu getEntity(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("菜单不存在"));
    }

    private MenuVO toVO(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getId());
        vo.setName(menu.getName());
        vo.setParentId(menu.getParentId());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setRedirect(menu.getRedirect());
        vo.setPermission(menu.getPermission());
        vo.setIcon(menu.getIcon());
        vo.setSort(menu.getSort());
        vo.setType(menu.getType());
        vo.setHidden(menu.getHidden());
        vo.setStatus(menu.getStatus());
        vo.setRemark(menu.getRemark());
        vo.setCreateTime(menu.getCreateTime());
        return vo;
    }
}
