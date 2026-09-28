package com.s2admin.module.auth.vo;

import lombok.Data;

/**
 * 登录验证码
 */
@Data
public class CaptchaVO {

    /** 是否启用验证码 */
    private boolean enabled;

    private String captchaKey;

    /** data:image/svg+xml;base64,... */
    private String image;
}
