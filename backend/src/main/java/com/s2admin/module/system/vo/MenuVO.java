package com.s2admin.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单 VO(含树形 children)
 */
@Data
public class MenuVO {

    private Long id;
    private String name;
    private Long parentId;
    private String path;
    private String component;
    private String redirect;
    private String permission;
    private String icon;
    private Integer sort;
    private Integer type;
    private Integer hidden;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private List<MenuVO> children = new ArrayList<>();
}
