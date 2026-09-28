package com.s2admin.module.auth.vo;

import lombok.Data;

import java.util.List;

/**
 * 登录用户信息 VO
 */
@Data
public class UserInfoVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private String province;
    private String city;
    private String district;
    private List<String> roles;
    private List<String> permissions;
    private Boolean mustChangePassword;
    private Long tenantId;
}
