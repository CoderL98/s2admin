package com.s2admin.module.workflow.entity;

import com.s2admin.module.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 审批流程定义
 */
@Getter
@Setter
@Entity
@Table(name = "sys_flow")
@SQLDelete(sql = "UPDATE sys_flow SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysFlow extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** 0 启用 1 停用 */
    @Column
    private Integer status = 0;
}
