package com.s2admin.module.system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.s2admin.module.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;

/**
 * 系统角色
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role")
@SQLDelete(sql = "UPDATE sys_role SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysRole extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column
    private Integer sort = 0;

    /** 数据范围:1 全部 2 本部门及以下 3 本部门 4 本人 */
    @Column(name = "data_scope")
    private Integer dataScope = 1;

    /** 状态:0 正常 1 禁用 */
    @Column
    private Integer status = 0;

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "sys_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<SysPermission> permissions = new HashSet<>();
}
