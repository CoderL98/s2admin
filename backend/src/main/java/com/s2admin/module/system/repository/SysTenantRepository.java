package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SysTenantRepository extends JpaRepository<SysTenant, Long>, JpaSpecificationExecutor<SysTenant> {

    Optional<SysTenant> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
