package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 登录日志仓储
 */
public interface LoginLogRepository extends JpaRepository<LoginLog, Long>, JpaSpecificationExecutor<LoginLog> {

    @Query("select count(l) from LoginLog l where l.loginTime >= :start")
    long countByLoginTimeGreaterThanEqual(@Param("start") LocalDateTime start);

    long countByLoginTimeGreaterThanEqualAndUserIdIn(LocalDateTime start, java.util.Collection<Long> userIds);

    void deleteByUserIdIn(Collection<Long> userIds);

    void deleteByLoginTimeBefore(LocalDateTime time);
}
