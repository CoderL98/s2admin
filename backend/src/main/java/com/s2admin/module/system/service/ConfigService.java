package com.s2admin.module.system.service;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.cache.CacheStore;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.UniqueFields;
import com.s2admin.module.system.entity.SysConfig;
import com.s2admin.module.system.form.ConfigForm;
import com.s2admin.module.system.form.ConfigQuery;
import com.s2admin.module.system.repository.SysConfigRepository;
import jakarta.persistence.criteria.Predicate;
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
 * 系统配置服务(按 key 走 Redis 缓存)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private static final String CACHE_PREFIX = "s2admin:config:";

    private final SysConfigRepository configRepository;
    private final CacheStore cacheStore;

    @Transactional(readOnly = true)
    public PageResult<SysConfig> page(ConfigQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Specification<SysConfig> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("configKey")), like),
                        cb.like(cb.lower(root.get("configValue")), like)
                ));
            }
            if (StringUtils.hasText(query.getGroupCode())) {
                predicates.add(cb.equal(root.get("groupCode"), query.getGroupCode()));
            }
            if (StringUtils.hasText(query.getConfigType())) {
                predicates.add(cb.equal(root.get("configType"), query.getConfigType()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = query.toPageable();
        Page<SysConfig> page = configRepository.findAll(spec, pageable);
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public SysConfig getByKey(String key) {
        return configRepository.findByConfigKey(key).orElse(null);
    }

    public String getValue(String key, String defaultValue) {
        try {
            String cached = cacheStore.get(CACHE_PREFIX + key);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.debug("读取配置缓存失败: {}", e.getMessage());
        }
        SysConfig config = getByKey(key);
        if (config == null || config.getConfigValue() == null) {
            return defaultValue;
        }
        String value = config.getConfigValue();
        try {
            cacheStore.set(CACHE_PREFIX + key, value, Duration.ofHours(1));
        } catch (Exception ignored) {
            // 缓存失败不影响主流程
        }
        return value;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key, String.valueOf(defaultValue));
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public int getInt(String key, int defaultValue) {
        String value = getValue(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void evict(String key) {
        try {
            cacheStore.delete(CACHE_PREFIX + key);
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public List<String> listGroups() {
        return configRepository.findDistinctGroupCodes();
    }

    @Transactional
    public SysConfig create(ConfigForm form) {
        if (configRepository.existsByConfigKey(form.getConfigKey())) {
            throw new BusinessException("配置键已存在");
        }
        SysConfig config = new SysConfig();
        applyForm(config, form);
        SysConfig saved = configRepository.save(config);
        evict(saved.getConfigKey());
        return saved;
    }

    @Transactional
    public SysConfig update(Long id, ConfigForm form) {
        SysConfig config = getEntity(id);
        String oldKey = config.getConfigKey();
        if (!config.getConfigKey().equals(form.getConfigKey())
                && configRepository.existsByConfigKey(form.getConfigKey())) {
            throw new BusinessException("配置键已存在");
        }
        if (config.getConfigKey() != null && config.getConfigKey().startsWith("sys.")
                && !config.getConfigKey().equals(form.getConfigKey())) {
            throw new BusinessException("内置配置键不允许修改");
        }
        applyForm(config, form);
        evict(oldKey);
        evict(form.getConfigKey());
        return configRepository.save(config);
    }

    @Transactional
    public void delete(Long id) {
        SysConfig config = getEntity(id);
        if (config.getConfigKey() != null && config.getConfigKey().startsWith("sys.")) {
            throw new BusinessException("内置配置不允许删除");
        }
        evict(config.getConfigKey());
        config.setConfigKey(UniqueFields.tombstone(config.getConfigKey(), id, 100));
        configRepository.save(config);
        configRepository.delete(config);
    }

    private void applyForm(SysConfig config, ConfigForm form) {
        config.setConfigKey(form.getConfigKey());
        config.setConfigValue(form.getConfigValue());
        config.setConfigType(StringUtils.hasText(form.getConfigType()) ? form.getConfigType() : "string");
        config.setGroupCode(StringUtils.hasText(form.getGroupCode()) ? form.getGroupCode() : "default");
        config.setRemark(form.getRemark());
    }

    private SysConfig getEntity(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("配置不存在"));
    }
}
