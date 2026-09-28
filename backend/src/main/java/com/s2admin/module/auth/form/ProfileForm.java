package com.s2admin.module.auth.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Locale;

/**
 * 个人资料
 */
@Data
public class ProfileForm {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最长 50 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长 100 个字符")
    private String email;

    public void setEmail(String email) {
        this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Size(max = 20, message = "手机号最长 20 个字符")
    private String phone;

    public void setPhone(String phone) {
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
    }

    @Size(max = 500, message = "头像地址过长")
    private String avatar;

    @Size(max = 50)
    private String province;

    @Size(max = 50)
    private String city;

    @Size(max = 50)
    private String district;
}
