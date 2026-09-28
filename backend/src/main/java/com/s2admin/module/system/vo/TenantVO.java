package com.s2admin.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantVO {
    private Long id;
    private String name;
    private String code;
    private Integer status;
    private String contact;
    private String remark;
    private Long userCount;
    private LocalDateTime createTime;
}
