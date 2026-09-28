package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysDictType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 字典类型仓储
 */
public interface SysDictTypeRepository extends JpaRepository<SysDictType, Long>, JpaSpecificationExecutor<SysDictType> {

    boolean existsByCode(String code);

    Optional<SysDictType> findByCode(String code);
}
