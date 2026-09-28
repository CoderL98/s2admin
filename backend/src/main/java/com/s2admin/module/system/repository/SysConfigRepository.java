package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置仓储
 */
public interface SysConfigRepository extends JpaRepository<SysConfig, Long>, JpaSpecificationExecutor<SysConfig> {

    Optional<SysConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);

    @Query("SELECT DISTINCT c.groupCode FROM SysConfig c WHERE c.groupCode IS NOT NULL AND c.groupCode <> '' ORDER BY c.groupCode")
    List<String> findDistinctGroupCodes();
}
