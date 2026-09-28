package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色新增/编辑表单
 */
@Data
public class RoleForm {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称最长 50 个字符")
    private String name;

    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色编码需为大写字母、数字、下划线,且以字母开头")
    @Size(max = 50, message = "角色编码最长 50 个字符")
    private String code;

    private Integer sort = 0;

    /** 数据范围:1 全部 2 本部门及以下 3 本部门 4 本人 */
    private Integer dataScope = 4;

    private Integer status = 0;

    /** 权限 ID 列表(授权时使用) */
    private List<Long> permissionIds;

    @Size(max = 500, message = "备注最长 500 个字符")
    private String remark;
}
