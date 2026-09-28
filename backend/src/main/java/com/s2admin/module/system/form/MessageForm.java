package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MessageForm {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200)
    private String title;

    private String content;

    @NotEmpty(message = "请选择接收人")
    private List<Long> receiverIds;
}
