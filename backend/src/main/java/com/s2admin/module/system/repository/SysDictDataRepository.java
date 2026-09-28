package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysDictData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 字典数据仓储
 */
public interface SysDictDataRepository extends JpaRepository<SysDictData, Long>, JpaSpecificationExecutor<SysDictData> {

    List<SysDictData> findByDictTypeIdOrderBySortAsc(Long dictTypeId);

    boolean existsByDictTypeId(Long dictTypeId);

    boolean existsByDictTypeIdAndValue(Long dictTypeId, String value);

    boolean existsByDictTypeIdAndValueAndIdNot(Long dictTypeId, String value, Long id);
}
