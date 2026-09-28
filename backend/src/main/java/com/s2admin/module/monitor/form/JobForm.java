package com.s2admin.module.monitor.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobForm {

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 50)
    private String name;

    @NotBlank(message = "任务编码不能为空")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "cron 不能为空")
    @Size(max = 50)
    private String cron;

    @NotBlank(message = "处理器不能为空")
    @Size(max = 50)
    private String handler;

    @Size(max = 200)
    private String params;

    private Integer status = 1;

    @Size(max = 500)
    private String remark;
}
