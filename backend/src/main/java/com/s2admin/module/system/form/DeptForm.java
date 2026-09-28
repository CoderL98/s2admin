package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeptForm {

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50)
    private String name;

    private Long parentId = 0L;

    private Integer sort = 0;

    @Size(max = 50)
    private String leader;

    @Size(max = 20)
    private String phone;

    @Size(max = 100)
    private String email;

    private Integer status = 0;

    @Size(max = 500)
    private String remark;
}
