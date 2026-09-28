package com.s2admin.module.workflow.vo;

import lombok.Data;

@Data
public class FlowNodeVO {
    private Long id;
    private String name;
    private Integer sort;
    private Long roleId;
    private String roleName;
    private Integer nodeType;
    private Integer signMode;
    private Integer rejectTo;
    private String conditionExpr;
    private Integer yesSeq;
    private Integer noSeq;
}
