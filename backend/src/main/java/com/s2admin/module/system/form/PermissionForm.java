package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 权限新增/编辑表单
 */
@Data
public class PermissionForm {

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 50, message = "权限名称最长 50 个字符")
    private String name;

    @NotBlank(message = "权限编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_:.-]*$", message = "权限编码格式不正确(如 system:user:view)")
    @Size(max = 100, message = "权限编码最长 100 个字符")
    private String code;

    /** 类型:1 菜单 2 按钮 3 API */
    private Integer type = 2;

    private Long parentId = 0L;

    @Size(max = 200, message = "路由路径最长 200 个字符")
    private String path;

    @Size(max = 100, message = "图标最长 100 个字符")
    private String icon;

    private Integer sort = 0;

    private Integer status = 0;

    @Size(max = 500, message = "备注最长 500 个字符")
    private String remark;
}
