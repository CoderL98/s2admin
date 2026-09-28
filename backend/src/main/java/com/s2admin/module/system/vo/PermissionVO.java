package com.s2admin.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 权限 VO(含树形 children)
 */
@Data
public class PermissionVO {

    private Long id;
    private String name;
    private String code;
    private Integer type;
    private Long parentId;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
    private Long roleCount;
    private String remark;
    private LocalDateTime createTime;
    private List<PermissionVO> children = new ArrayList<>();
}
