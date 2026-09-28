package com.s2admin.module.system.entity;

import com.s2admin.module.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 租户。平台超级管理员不属于任何租户。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_tenant")
@SQLDelete(sql = "UPDATE sys_tenant SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysTenant extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    /** 0 正常 1 停用 */
    @Column
    private Integer status = 0;

    @Column(length = 50)
    private String contact;
}
