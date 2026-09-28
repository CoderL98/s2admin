package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SysFileRepository extends JpaRepository<SysFile, Long>, JpaSpecificationExecutor<SysFile> {

    Optional<SysFile> findByStoredName(String storedName);
}
