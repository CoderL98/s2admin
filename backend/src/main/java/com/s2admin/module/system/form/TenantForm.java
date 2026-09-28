package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantForm {

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 50)
    private String name;

    @NotBlank(message = "租户编码不能为空")
    @Size(max = 32)
    private String code;

    private Integer status = 0;

    @Size(max = 50)
    private String contact;

    @Size(max = 500)
    private String remark;
}
