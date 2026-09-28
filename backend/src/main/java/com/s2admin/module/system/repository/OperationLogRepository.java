package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 操作日志仓储
 */
public interface OperationLogRepository extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    void deleteByUserIdIn(java.util.Collection<Long> userIds);

    void deleteByOperationTimeBefore(java.time.LocalDateTime time);
}
