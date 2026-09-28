package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典数据表单
 */
@Data
public class DictDataForm {

    @NotNull(message = "字典类型不能为空")
    private Long dictTypeId;

    @NotBlank(message = "字典标签不能为空")
    @Size(max = 100, message = "字典标签最长 100 个字符")
    private String label;

    @NotBlank(message = "字典值不能为空")
    @Size(max = 100, message = "字典值最长 100 个字符")
    private String value;

    private Integer sort = 0;

    private Integer status = 0;

    @Size(max = 500, message = "备注最长 500 个字符")
    private String remark;
}
