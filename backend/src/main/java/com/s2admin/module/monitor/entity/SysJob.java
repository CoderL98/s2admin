package com.s2admin.module.monitor.entity;

import com.s2admin.module.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "sys_job")
@SQLDelete(sql = "UPDATE sys_job SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysJob extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Spring 6 位 cron，含秒。两次触发至少间隔 30 秒。 */
    @Column(nullable = false, length = 50)
    private String cron;

    @Column(nullable = false, length = 50)
    private String handler;

    @Column(length = 200)
    private String params;

    /** 0 启用 1 停用 */
    @Column
    private Integer status = 1;

    @Column(name = "next_fire_time")
    private LocalDateTime nextFireTime;

    @Column(name = "last_fire_time")
    private LocalDateTime lastFireTime;
}
