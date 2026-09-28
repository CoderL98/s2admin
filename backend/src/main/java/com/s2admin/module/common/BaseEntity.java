package com.s2admin.module.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类
 * 所有业务实体继承此类(日志类除外)
 * id / createBy / createTime / updateBy / updateTime / remark
 * deleted 软删除字段由 @SQLDelete/@SQLRestriction 在各实体上维护,Java 端不暴露
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedBy
    @Column(name = "create_by", updatable = false)
    private Long createBy;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @LastModifiedBy
    @Column(name = "update_by")
    private Long updateBy;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * 软删除标记:0 未删除 1 已删除
     * 业务代码不直接操作该字段,由 @SQLDelete/@SQLRestriction 维护
     */
    @JsonIgnore
    @Column(name = "deleted", nullable = false)
    private Integer deleted = 0;
}