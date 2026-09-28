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
 * 系统权限
 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission")
@SQLDelete(sql = "UPDATE sys_permission SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysPermission extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    /** 类型:1 菜单 2 按钮 3 API */
    @Column(nullable = false)
    private Integer type;

    @Column(name = "parent_id")
    private Long parentId = 0L;

    @Column(length = 200)
    private String path;

    @Column(length = 100)
    private String icon;

    @Column
    private Integer sort = 0;

    /** 状态:0 正常 1 禁用 */
    @Column
    private Integer status = 0;
}
