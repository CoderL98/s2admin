package com.s2admin.module.workflow.repository;

import com.s2admin.module.workflow.entity.SysFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SysFlowRepository extends JpaRepository<SysFlow, Long>, JpaSpecificationExecutor<SysFlow> {

    boolean existsByCode(String code);

    Optional<SysFlow> findByCode(String code);
}
