package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系统配置表单
 */
@Data
public class ConfigForm {

    @NotBlank(message = "配置键不能为空")
    @Size(max = 100, message = "配置键最长 100 个字符")
    private String configKey;

    @Size(max = 500, message = "配置值最长 500 个字符")
    private String configValue;

    private String configType = "string";

    private String groupCode = "default";

    @Size(max = 500, message = "备注最长 500 个字符")
    private String remark;
}
