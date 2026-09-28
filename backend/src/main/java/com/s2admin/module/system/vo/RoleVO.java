package com.s2admin.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色 VO
 */
@Data
public class RoleVO {

    private Long id;
    private String name;
    private String code;
    private Integer sort;
    private Integer dataScope;
    private Integer status;
    private Long userCount;
    private List<Long> permissionIds;
    private String remark;
    private LocalDateTime createTime;
}
