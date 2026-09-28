package com.s2admin.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeptVO {

    private Long id;
    private String name;
    private Long parentId;
    private String ancestors;
    private Integer sort;
    private String leader;
    private String phone;
    private String email;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private List<DeptVO> children = new ArrayList<>();
}
