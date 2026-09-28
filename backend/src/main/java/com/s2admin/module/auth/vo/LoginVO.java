package com.s2admin.module.auth.vo;

import lombok.Data;

/**
 * 登录响应 VO
 */
@Data
public class LoginVO {

    private String token;
    private String refreshToken;
    private long expiresIn;
    private UserInfoVO user;
}
