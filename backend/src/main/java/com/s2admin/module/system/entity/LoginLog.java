package com.s2admin.module.system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登录日志
 */
@Getter
@Setter
@Entity
@Table(name = "sys_login_log")
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String username;

    @Column(length = 50)
    private String ip;

    @Column(length = 200)
    private String location;

    @Column(length = 100)
    private String browser;

    @Column(length = 100)
    private String os;

    /** 状态:0 成功 1 失败 */
    @Column
    private Integer status = 0;

    @Column(length = 500)
    private String message;

    @Column(name = "login_time")
    private LocalDateTime loginTime;
}
