package com.s2admin.module.system.service;

import com.s2admin.module.common.PageQuery;
import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.UniqueFields;
import com.s2admin.module.system.entity.SysTenant;
import com.s2admin.module.system.form.TenantForm;
import com.s2admin.module.system.repository.SysTenantRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import com.s2admin.module.system.vo.TenantVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    public static final String DEFAULT_CODE = "DEFAULT";

    private final SysTenantRepository tenantRepository;
    private final SysUserRepository userRepository;

    @Transactional
    public Long defaultId() {
        return tenantRepository.findByCode(DEFAULT_CODE).map(SysTenant::getId).orElseGet(() -> {
            SysTenant tenant = new SysTenant();
            tenant.setName("默认租户");
            tenant.setCode(DEFAULT_CODE);
            tenant.setStatus(0);
            tenant.setRemark("系统内置租户");
            return tenantRepository.save(tenant).getId();
        });
    }

    @Transactional(readOnly = true)
    public SysTenant requireActive(Long id) {
        SysTenant tenant = tenantRepository.findById(id).orElseThrow(() -> new BusinessException("租户不存在"));
        if (tenant.getStatus() != null && tenant.getStatus() != 0) {
            throw new BusinessException("租户已停用");
        }
        return tenant;
    }

    @Transactional(readOnly = true)
    public PageResult<TenantVO> page(PageQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Specification<SysTenant> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SysTenant> page = tenantRepository.findAll(spec, query.toPageable());
        return PageResult.of(page, this::toVO);
    }

    @Transactional(readOnly = true)
    public List<TenantVO> options() {
        return tenantRepository.findAll().stream()
                .filter(item -> item.getStatus() == null || item.getStatus() == 0)
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public TenantVO create(TenantForm form) {
        String code = normalize(form.getCode());
        if (tenantRepository.existsByCode(code)) {
            throw new BusinessException("租户编码已存在");
        }
        SysTenant tenant = new SysTenant();
        apply(tenant, form, code);
        tenantRepository.save(tenant);
        return toVO(tenant);
    }

    @Transactional
    public TenantVO update(Long id, TenantForm form) {
        SysTenant tenant = get(id);
        String code = normalize(form.getCode());
        if (DEFAULT_CODE.equals(tenant.getCode()) && !DEFAULT_CODE.equals(code)) {
            throw new BusinessException("不能修改默认租户编码");
        }
        if (DEFAULT_CODE.equals(tenant.getCode()) && form.getStatus() != null && form.getStatus() != 0) {
            throw new BusinessException("不能停用默认租户");
        }
        if (!code.equals(tenant.getCode()) && tenantRepository.existsByCodeAndIdNot(code, id)) {
            throw new BusinessException("租户编码已存在");
        }
        apply(tenant, form, code);
        tenantRepository.save(tenant);
        return toVO(tenant);
    }

    @Transactional
    public void delete(Long id) {
        SysTenant tenant = get(id);
        if (DEFAULT_CODE.equals(tenant.getCode())) {
            throw new BusinessException("不能删除默认租户");
        }
        if (userRepository.countByTenantId(id) > 0) {
            throw new BusinessException("租户下仍有用户,不能删除");
        }
        tenant.setCode(UniqueFields.tombstone(tenant.getCode(), id, 32));
        tenantRepository.save(tenant);
        tenantRepository.delete(tenant);
    }

    private void apply(SysTenant tenant, TenantForm form, String code) {
        tenant.setName(form.getName().trim());
        tenant.setCode(code);
        int status = form.getStatus() == null ? 0 : form.getStatus();
        if (status != 0 && status != 1) {
            throw new BusinessException("租户状态只能是启用或停用");
        }
        tenant.setStatus(status);
        tenant.setContact(StringUtils.hasText(form.getContact()) ? form.getContact().trim() : null);
        tenant.setRemark(form.getRemark());
    }

    private String normalize(String code) {
        String value = code == null ? "" : code.trim().toUpperCase();
        if (!value.matches("^[A-Z][A-Z0-9_]{1,31}$")) {
            throw new BusinessException("租户编码需为大写字母开头,只含字母、数字和下划线");
        }
        return value;
    }

    private SysTenant get(Long id) {
        return tenantRepository.findById(id).orElseThrow(() -> BusinessException.notFound("租户不存在"));
    }

    private TenantVO toVO(SysTenant tenant) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setName(tenant.getName());
        vo.setCode(tenant.getCode());
        vo.setStatus(tenant.getStatus());
        vo.setContact(tenant.getContact());
        vo.setRemark(tenant.getRemark());
        vo.setCreateTime(tenant.getCreateTime());
        vo.setUserCount(tenant.getId() == null ? 0L : userRepository.countByTenantId(tenant.getId()));
        return vo;
    }
}
