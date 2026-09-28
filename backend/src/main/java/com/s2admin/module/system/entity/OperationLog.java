package com.s2admin.module.system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作日志
 */
@Getter
@Setter
@Entity
@Table(name = "sys_operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String username;

    @Column(length = 50)
    private String operation;

    @Column(length = 50)
    private String module;

    @Column(length = 100)
    private String method;

    @Column(length = 200)
    private String url;

    @Column(length = 50)
    private String ip;

    @Column(length = 200)
    private String location;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** 状态:0 成功 1 失败 */
    @Column
    private Integer status = 0;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "execute_time")
    private Long executeTime;

    @Column(name = "operation_time")
    private LocalDateTime operationTime;
}
