package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.ErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 异常日志仓储
 */
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long>, JpaSpecificationExecutor<ErrorLog> {

    long countByUserIdIn(java.util.Collection<Long> userIds);

    void deleteByUserIdIn(java.util.Collection<Long> userIds);

    void deleteByErrorTimeBefore(java.time.LocalDateTime time);
}
