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
 * 系统菜单
 */
@Getter
@Setter
@Entity
@Table(name = "sys_menu")
@SQLDelete(sql = "UPDATE sys_menu SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysMenu extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "parent_id")
    private Long parentId = 0L;

    @Column(nullable = false, length = 200)
    private String path;

    @Column(length = 255)
    private String component;

    @Column(length = 255)
    private String redirect;

    @Column(length = 100)
    private String permission;

    @Column(length = 100)
    private String icon;

    @Column
    private Integer sort = 0;

    /** 类型:1 目录 2 菜单 3 按钮 */
    @Column(nullable = false)
    private Integer type = 1;

    /** 是否隐藏:0 显示 1 隐藏 */
    @Column
    private Integer hidden = 0;

    /** 状态:0 正常 1 禁用 */
    @Column
    private Integer status = 0;
}
