package com.s2admin.module.auth.vo;

import lombok.Data;

@Data
public class SessionVO {

    private String sid;
    private long iat;
    private String ip;
    private String ua;
    private boolean current;
}
