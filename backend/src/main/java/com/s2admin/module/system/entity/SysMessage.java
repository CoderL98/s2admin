package com.s2admin.module.system.entity;

import com.s2admin.module.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 站内信
 */
@Getter
@Setter
@Entity
@Table(name = "sys_message")
@SQLDelete(sql = "UPDATE sys_message SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysMessage extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "sender_name", length = 50)
    private String senderName;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    /** 0 未读 1 已读 */
    @Column(name = "read_flag")
    private Integer readFlag = 0;

    @Column(name = "read_time")
    private LocalDateTime readTime;
}
