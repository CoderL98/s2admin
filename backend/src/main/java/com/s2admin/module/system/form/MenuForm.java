package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单新增/编辑表单
 */
@Data
public class MenuForm {

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称最长 50 个字符")
    private String name;

    private Long parentId = 0L;

    @Size(max = 200, message = "路由路径最长 200 个字符")
    private String path;

    @Size(max = 255, message = "组件路径最长 255 个字符")
    private String component;

    @Size(max = 255, message = "重定向路径最长 255 个字符")
    private String redirect;

    @Size(max = 100, message = "权限标识最长 100 个字符")
    private String permission;

    @Size(max = 100, message = "图标最长 100 个字符")
    private String icon;

    private Integer sort = 0;

    /** 类型:1 目录 2 菜单 3 按钮 */
    private Integer type = 2;

    private Integer hidden = 0;

    private Integer status = 0;

    @Size(max = 500, message = "备注最长 500 个字符")
    private String remark;
}
