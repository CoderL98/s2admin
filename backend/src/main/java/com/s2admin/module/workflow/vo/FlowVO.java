package com.s2admin.module.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class FlowVO {
    private Long id;
    private String name;
    private String code;
    private Integer status;
    private String remark;
    private Integer nodeCount;
    private LocalDateTime createTime;
    private List<FlowNodeVO> nodes = new ArrayList<>();
}
