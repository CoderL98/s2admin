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
 * 部门
 */
@Getter
@Setter
@Entity
@Table(name = "sys_dept")
@SQLDelete(sql = "UPDATE sys_dept SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysDept extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "parent_id")
    private Long parentId = 0L;

    /** 祖先链,如 0,1,2,不含自身 */
    @Column(length = 200)
    private String ancestors = "0";

    @Column
    private Integer sort = 0;

    @Column(length = 50)
    private String leader;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    /** 0 正常 1 停用 */
    @Column
    private Integer status = 0;
}
