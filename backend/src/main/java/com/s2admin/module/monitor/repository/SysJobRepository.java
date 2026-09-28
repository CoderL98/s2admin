package com.s2admin.module.monitor.repository;

import com.s2admin.module.monitor.entity.SysJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SysJobRepository extends JpaRepository<SysJob, Long>, JpaSpecificationExecutor<SysJob> {

    boolean existsByCode(String code);

    Optional<SysJob> findByCode(String code);
}
