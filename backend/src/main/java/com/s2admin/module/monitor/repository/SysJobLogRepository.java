package com.s2admin.module.monitor.repository;

import com.s2admin.module.monitor.entity.SysJobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SysJobLogRepository extends JpaRepository<SysJobLog, Long>, JpaSpecificationExecutor<SysJobLog> {
}
