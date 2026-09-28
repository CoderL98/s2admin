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
 * 系统公告
 */
@Getter
@Setter
@Entity
@Table(name = "sys_notice")
@SQLDelete(sql = "UPDATE sys_notice SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysNotice extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 1 通知 2 公告 */
    @Column
    private Integer type = 1;

    /** 0 草稿 1 已发布 */
    @Column
    private Integer status = 0;

    @Column
    private Integer pinned = 0;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;
}
