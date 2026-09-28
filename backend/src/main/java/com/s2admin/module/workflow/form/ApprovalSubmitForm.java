package com.s2admin.module.workflow.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApprovalSubmitForm {

    @NotNull(message = "请选择流程")
    private Long flowId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200)
    private String title;

    @Size(max = 4000)
    private String content;
}
