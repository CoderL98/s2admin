package com.s2admin.module.system.service;

import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.ParentCycle;
import com.s2admin.module.system.entity.SysDept;
import com.s2admin.module.system.form.DeptForm;
import com.s2admin.module.system.repository.SysDeptRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import com.s2admin.module.system.vo.DeptVO;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class DeptService {

    private final SysDeptRepository deptRepository;
    private final SysUserRepository userRepository;
    private final DataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public List<DeptVO> tree() {
        Set<Long> visible = dataScopeService.visibleDeptIdsWithAncestors();
        List<SysDept> all = deptRepository.findAll().stream()
                .filter(d -> visible == null || visible.contains(d.getId()))
                .sorted(Comparator.comparing(d -> d.getSort() == null ? 0 : d.getSort()))
                .toList();
        return buildTree(all.stream().map(this::toVO).toList());
    }

    @Transactional(readOnly = true)
    public List<DeptVO> listAll() {
        DataScopeService.Scope scope = dataScopeService.current();
        List<SysDept> all = deptRepository.findByStatusOrderBySortAsc(0);
        if (scope.all()) {
            return all.stream().map(this::toVO).toList();
        }
        Set<Long> ids = new HashSet<>();
        if (scope.selfOnly()) {
            userRepository.findById(com.s2admin.module.common.util.SecurityUtils.getUserId())
                    .map(com.s2admin.module.system.entity.SysUser::getDeptId)
                    .ifPresent(ids::add);
        } else if (scope.deptIds() != null) {
            ids.addAll(scope.deptIds());
        }
        return all.stream().filter(d -> ids.contains(d.getId())).map(this::toVO).toList();
    }

    @Transactional
    public DeptVO create(DeptForm form) {
        Long parentId = form.getParentId() == null ? 0L : form.getParentId();
        if (parentId != 0L) {
            dataScopeService.assertCanAccessDept(parentId);
        } else if (!dataScopeService.current().all()) {
            throw new BusinessException("无权创建根部门");
        }
        ParentCycle.assertAcyclic(null, parentId, this::parentIdOf);
        if (deptRepository.existsByNameAndParentId(form.getName(), parentId)) {
            throw new BusinessException("同级部门名称已存在");
        }
        SysDept dept = new SysDept();
        applyForm(dept, form);
        dept.setAncestors(ancestorsOf(parentId));
        deptRepository.save(dept);
        return toVO(dept);
    }

    @Transactional
    public DeptVO update(Long id, DeptForm form) {
        SysDept dept = getEntity(id);
        dataScopeService.assertCanAccessDept(id);
        Long parentId = form.getParentId() == null ? 0L : form.getParentId();
        if (parentId != 0L) {
            dataScopeService.assertCanAccessDept(parentId);
        } else if (!dataScopeService.current().all()) {
            throw new BusinessException("无权将部门挂到根级");
        }
        ParentCycle.assertAcyclic(id, parentId, this::parentIdOf);
        Long oldParent = dept.getParentId() == null ? 0L : dept.getParentId();
        boolean nameOrParentChanged = !dept.getName().equals(form.getName()) || !oldParent.equals(parentId);
        if (nameOrParentChanged && deptRepository.existsByNameAndParentIdAndIdNot(form.getName(), parentId, id)) {
            throw new BusinessException("同级部门名称已存在");
        }
        String oldName = dept.getName();
        String oldAncestors = chain(dept.getAncestors(), id);
        applyForm(dept, form);
        dept.setAncestors(ancestorsOf(parentId));
        deptRepository.save(dept);
        rewriteChildrenAncestors(id, oldAncestors, chain(dept.getAncestors(), id));
        if (!oldName.equals(dept.getName())) {
            userRepository.updateDeptName(id, dept.getName());
        }
        return toVO(dept);
    }

    @Transactional
    public void delete(Long id) {
        dataScopeService.assertCanAccessDept(id);
        if (deptRepository.existsByParentId(id)) {
            throw new BusinessException("存在子部门,不允许删除");
        }
        long used = userRepository.countByDeptId(id);
        if (used > 0) {
            throw new BusinessException("部门下仍有用户,不允许删除");
        }
        deptRepository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public SysDept getEntity(Long id) {
        return deptRepository.findById(id).orElseThrow(() -> BusinessException.notFound("部门不存在"));
    }

    private void rewriteChildrenAncestors(Long id, String oldPrefix, String newPrefix) {
        if (!StringUtils.hasText(oldPrefix) || oldPrefix.equals(newPrefix)) {
            return;
        }
        for (SysDept child : deptRepository.findAll()) {
            if (id.equals(child.getId()) || !StringUtils.hasText(child.getAncestors())) {
                continue;
            }
            String ancestors = child.getAncestors();
            if (ancestors.equals(oldPrefix) || ancestors.startsWith(oldPrefix + ",")) {
                child.setAncestors(newPrefix + ancestors.substring(oldPrefix.length()));
                deptRepository.save(child);
            }
        }
    }

    private String chain(String ancestors, Long id) {
        String base = StringUtils.hasText(ancestors) ? ancestors : "0";
        return base + "," + id;
    }

    private Long parentIdOf(Long id) {
        SysDept parent = getEntity(id);
        return parent.getParentId() == null ? 0L : parent.getParentId();
    }

    private String ancestorsOf(Long parentId) {
        if (parentId == null || parentId == 0) {
            return "0";
        }
        SysDept parent = getEntity(parentId);
        return chain(parent.getAncestors(), parent.getId());
    }

    private void applyForm(SysDept dept, DeptForm form) {
        dept.setName(form.getName());
        dept.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        dept.setSort(form.getSort() == null ? 0 : form.getSort());
        dept.setLeader(form.getLeader());
        dept.setPhone(form.getPhone());
        dept.setEmail(form.getEmail());
        dept.setStatus(form.getStatus() == null ? 0 : form.getStatus());
        dept.setRemark(form.getRemark());
    }

    private List<DeptVO> buildTree(List<DeptVO> flat) {
        Map<Long, DeptVO> map = flat.stream().collect(Collectors.toMap(DeptVO::getId, d -> d));
        List<DeptVO> roots = new ArrayList<>();
        for (DeptVO vo : flat) {
            if (vo.getParentId() == null || vo.getParentId() == 0) {
                roots.add(vo);
            } else {
                DeptVO parent = map.get(vo.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        return roots;
    }

    private DeptVO toVO(SysDept dept) {
        DeptVO vo = new DeptVO();
        vo.setId(dept.getId());
        vo.setName(dept.getName());
        vo.setParentId(dept.getParentId());
        vo.setAncestors(dept.getAncestors());
        vo.setSort(dept.getSort());
        vo.setLeader(dept.getLeader());
        vo.setPhone(dept.getPhone());
        vo.setEmail(dept.getEmail());
        vo.setStatus(dept.getStatus());
        vo.setRemark(dept.getRemark());
        vo.setCreateTime(dept.getCreateTime());
        return vo;
    }
}
