package com.s2admin.module.workflow.form;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApprovalActionForm {

    @Size(max = 500)
    private String comment;
}
