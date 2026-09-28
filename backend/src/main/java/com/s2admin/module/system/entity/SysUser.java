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
 * 系统用户
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user")
@SQLDelete(sql = "UPDATE sys_user SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 200)
    private String password;

    @Column(length = 50)
    private String nickname;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 500)
    private String avatar;

    /** 状态:0 正常 1 禁用 2 锁定 3 过期 */
    @Column
    private Integer status = 0;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "dept_name", length = 50)
    private String deptName;

    /** 1 首次/重置后必须改密 */
    @Column(name = "pwd_reset")
    private Integer pwdReset = 0;

    /** 自动锁定的截止时间(epoch 毫秒)。空表示不是输错密码导致的锁定。 */
    @JsonIgnore
    @Column(name = "lock_until")
    private Long lockUntil;

    /** 空表示平台账号(仅超级管理员)。 */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(length = 50)
    private String province;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String district;

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<SysRole> roles = new HashSet<>();
}
