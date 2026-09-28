package com.s2admin.module.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalRecordVO {
    private Long id;
    private Integer seq;
    private String nodeName;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String comment;
    private LocalDateTime createTime;
}
