package com.s2admin.module.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录表单
 */
@Data
public class LoginForm {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码(预留,启用后必填) */
    private String captcha;

    /** 验证码 KEY(预留) */
    private String captchaKey;

    /** 记住我:延长 RefreshToken */
    private Boolean rememberMe;
}
