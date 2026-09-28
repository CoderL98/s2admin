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
@Table(name = "sys_job_log")
@SQLDelete(sql = "UPDATE sys_job_log SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysJobLog extends BaseEntity {

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "job_name", length = 50)
    private String jobName;

    /** 0 成功 1 失败 */
    @Column
    private Integer status = 0;

    @Column(length = 1000)
    private String message;

    @Column(name = "cost_ms")
    private Long costMs;

    @Column(name = "fire_time")
    private LocalDateTime fireTime;
}
