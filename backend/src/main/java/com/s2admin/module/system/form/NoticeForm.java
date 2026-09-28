package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NoticeForm {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200)
    private String title;

    private String content;

    /** 1 通知 2 公告 */
    private Integer type = 1;

    /** 0 草稿 1 发布 */
    private Integer status = 0;

    private Integer pinned = 0;

    @Size(max = 500)
    private String remark;
}
