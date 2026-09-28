package com.s2admin.module.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordForm {

    @NotBlank(message = "账号不能为空")
    private String account;

    private String captcha;

    private String captchaKey;
}
