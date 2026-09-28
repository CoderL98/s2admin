package com.s2admin.module.system.service;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.cache.CacheStore;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.UniqueFields;
import com.s2admin.module.system.entity.SysDictData;
import com.s2admin.module.system.entity.SysDictType;
import com.s2admin.module.system.form.DictDataForm;
import com.s2admin.module.system.form.DictDataQuery;
import com.s2admin.module.system.form.DictTypeForm;
import com.s2admin.module.system.form.DictTypeQuery;
import com.s2admin.module.system.repository.SysDictDataRepository;
import com.s2admin.module.system.repository.SysDictTypeRepository;
import jakarta.persistence.criteria.Predicate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 字典服务(类型 + 数据,按 typeCode 缓存)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictService {

    private static final String CACHE_PREFIX = "s2admin:dict:";

    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictDataRepository dictDataRepository;
    private final CacheStore cacheStore;
    private final ObjectMapper objectMapper;

    // ========== 字典类型 ==========

    @Transactional(readOnly = true)
    public PageResult<SysDictType> typePage(DictTypeQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("asc");
        }
        Specification<SysDictType> spec = (root, cq, cb) -> {
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
        Page<SysDictType> page = dictTypeRepository.findAll(spec, pageable);
        return PageResult.of(page);
    }

    @Transactional
    public SysDictType createType(DictTypeForm form) {
        if (dictTypeRepository.existsByCode(form.getCode())) {
            throw new BusinessException("字典编码已存在");
        }
        SysDictType type = new SysDictType();
        type.setName(form.getName());
        type.setCode(form.getCode());
        type.setStatus(form.getStatus() == null ? 0 : form.getStatus());
        type.setRemark(form.getRemark());
        SysDictType saved = dictTypeRepository.save(type);
        evict(saved.getCode());
        return saved;
    }

    @Transactional
    public SysDictType updateType(Long id, DictTypeForm form) {
        SysDictType type = getTypeEntity(id);
        if (!type.getCode().equals(form.getCode()) && dictTypeRepository.existsByCode(form.getCode())) {
            throw new BusinessException("字典编码已存在");
        }
        String oldCode = type.getCode();
        type.setName(form.getName());
        type.setCode(form.getCode());
        type.setStatus(form.getStatus() == null ? 0 : form.getStatus());
        type.setRemark(form.getRemark());
        SysDictType saved = dictTypeRepository.save(type);
        evict(oldCode);
        evict(saved.getCode());
        return saved;
    }

    @Transactional
    public void deleteType(Long id) {
        if (dictDataRepository.existsByDictTypeId(id)) {
            throw new BusinessException("该字典下仍有字典数据,请先删除字典数据");
        }
        SysDictType type = getTypeEntity(id);
        evict(type.getCode());
        type.setCode(UniqueFields.tombstone(type.getCode(), id, 50));
        dictTypeRepository.save(type);
        dictTypeRepository.delete(type);
    }

    // ========== 字典数据 ==========

    @Transactional(readOnly = true)
    public PageResult<SysDictData> dataPage(DictDataQuery query) {
        if (query.getDictTypeId() == null) {
            throw new BusinessException("请选择字典类型");
        }
        if (query.getOrderBy() == null) {
            query.setOrderBy("sort");
            query.setSortDirection("asc");
        }
        Specification<SysDictData> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("dictTypeId"), query.getDictTypeId()));
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("label")), like),
                        cb.like(cb.lower(root.get("value")), like)
                ));
            }
            if (query.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), query.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = query.toPageable();
        Page<SysDictData> page = dictDataRepository.findAll(spec, pageable);
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public List<SysDictData> getDataByTypeCode(String typeCode) {
        SysDictType type = dictTypeRepository.findByCode(typeCode).orElse(null);
        if (type == null || (type.getStatus() != null && type.getStatus() != 0)) {
            return List.of();
        }
        try {
            String cached = cacheStore.get(CACHE_PREFIX + typeCode);
            if (cached != null) {
                List<SysDictData> cachedList = objectMapper.readValue(cached, new TypeReference<>() {
                });
                return enabledOnly(cachedList);
            }
        } catch (Exception e) {
            log.debug("读取字典缓存失败: {}", e.getMessage());
        }
        List<SysDictData> list = enabledOnly(dictDataRepository.findByDictTypeIdOrderBySortAsc(type.getId()));
        try {
            cacheStore.set(CACHE_PREFIX + typeCode, objectMapper.writeValueAsString(list), Duration.ofHours(1));
        } catch (Exception ignored) {
        }
        return list;
    }

    private List<SysDictData> enabledOnly(List<SysDictData> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().filter(d -> d.getStatus() == null || d.getStatus() == 0).toList();
    }

    @Transactional
    public SysDictData createData(DictDataForm form) {
        SysDictType type = getTypeEntity(form.getDictTypeId());
        if (dictDataRepository.existsByDictTypeIdAndValue(type.getId(), form.getValue())) {
            throw new BusinessException("同一字典下的键值已存在");
        }
        SysDictData data = new SysDictData();
        applyDataForm(data, form);
        SysDictData saved = dictDataRepository.save(data);
        evict(type.getCode());
        return saved;
    }

    @Transactional
    public SysDictData updateData(Long id, DictDataForm form) {
        SysDictData data = getDataEntity(id);
        Long oldTypeId = data.getDictTypeId();
        SysDictType type = getTypeEntity(form.getDictTypeId());
        if (dictDataRepository.existsByDictTypeIdAndValueAndIdNot(type.getId(), form.getValue(), id)) {
            throw new BusinessException("同一字典下的键值已存在");
        }
        applyDataForm(data, form);
        SysDictData saved = dictDataRepository.save(data);
        evictByTypeId(oldTypeId);
        evictByTypeId(saved.getDictTypeId());
        return saved;
    }

    @Transactional
    public void deleteData(Long id) {
        SysDictData data = getDataEntity(id);
        evictByTypeId(data.getDictTypeId());
        dictDataRepository.delete(data);
    }

    private void evictByTypeId(Long typeId) {
        dictTypeRepository.findById(typeId).ifPresent(t -> evict(t.getCode()));
    }

    private void evict(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            return;
        }
        try {
            cacheStore.delete(CACHE_PREFIX + typeCode);
        } catch (Exception ignored) {
        }
    }

    private void applyDataForm(SysDictData data, DictDataForm form) {
        data.setDictTypeId(form.getDictTypeId());
        data.setLabel(form.getLabel());
        data.setValue(form.getValue());
        data.setSort(form.getSort() == null ? 0 : form.getSort());
        data.setStatus(form.getStatus() == null ? 0 : form.getStatus());
        data.setRemark(form.getRemark());
    }

    private SysDictType getTypeEntity(Long id) {
        return dictTypeRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("字典类型不存在"));
    }

    private SysDictData getDataEntity(Long id) {
        return dictDataRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("字典数据不存在"));
    }
}
