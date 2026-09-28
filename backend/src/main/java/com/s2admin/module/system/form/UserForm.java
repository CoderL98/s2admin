package com.s2admin.module.system.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Locale;

/**
 * 用户新增/编辑表单
 */
@Data
public class UserForm {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 50, message = "用户名长度需在 2-50 之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    /** 新增时可选(空则由系统生成随机强口令并标记强制改密),编辑时留空表示不修改 */
    @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
    private String password;

    public void setPassword(String password) {
        this.password = (password == null || password.isBlank()) ? null : password;
    }

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

    private String avatar;

    private Long deptId;

    private String deptName;

    private Integer status = 0;

    private List<Long> roleIds;

    @Size(max = 50)
    private String province;

    @Size(max = 50)
    private String city;

    @Size(max = 50)
    private String district;

    @Size(max = 500, message = "备注最长 500 个字符")
    private String remark;

    /** 仅平台管理员可指定。租户内创建时会被强制改成本租户。 */
    private Long tenantId;
}
