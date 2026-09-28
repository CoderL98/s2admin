package com.s2admin.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 VO
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private Long deptId;
    private String deptName;
    private List<Long> roleIds;
    private List<String> roleNames;
    private String province;
    private String city;
    private String district;
    private String remark;
    private Integer pwdReset;
    private Long tenantId;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
