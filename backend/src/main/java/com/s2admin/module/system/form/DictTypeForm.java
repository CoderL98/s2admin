package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典类型表单
 */
@Data
public class DictTypeForm {

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 50, message = "字典名称最长 50 个字符")
    private String name;

    @NotBlank(message = "字典编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字典编码需为小写字母、数字、下划线,且以字母开头")
    @Size(max = 50, message = "字典编码最长 50 个字符")
    private String code;

    private Integer status = 0;

    @Size(max = 500, message = "备注最长 500 个字符")
    private String remark;
}
