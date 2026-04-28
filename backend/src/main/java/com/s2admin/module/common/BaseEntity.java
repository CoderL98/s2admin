package com.s2admin.module.common;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类
 * 所有实体类继承此类
 */
@Data
public abstract class BaseEntity implements Serializable {

    private Long id;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private String remark;
}
