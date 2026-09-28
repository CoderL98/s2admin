package com.s2admin.module.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Token 刷新表单
 */
@Data
public class RefreshForm {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
