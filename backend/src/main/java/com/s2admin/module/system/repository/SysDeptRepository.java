package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysDept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SysDeptRepository extends JpaRepository<SysDept, Long>, JpaSpecificationExecutor<SysDept> {

    boolean existsByParentId(Long parentId);

    List<SysDept> findByParentId(Long parentId);

    List<SysDept> findByStatusOrderBySortAsc(Integer status);

    boolean existsByNameAndParentId(String name, Long parentId);

    boolean existsByNameAndParentIdAndIdNot(String name, Long parentId, Long id);

    List<SysDept> findByName(String name);
}
