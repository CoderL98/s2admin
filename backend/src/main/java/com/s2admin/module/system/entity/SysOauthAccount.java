package com.s2admin.module.system.entity;

import com.s2admin.module.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 第三方账号绑定。同一提供方的 openId 只对应一个本地用户。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_oauth_account", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "open_id"}))
@SQLDelete(sql = "UPDATE sys_oauth_account SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysOauthAccount extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "open_id", nullable = false, length = 128)
    private String openId;

    @Column(name = "union_id", length = 128)
    private String unionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 100)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Column(length = 500)
    private String avatar;
}
