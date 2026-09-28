package com.s2admin.module.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApprovalVO {
    private Long id;
    private Long flowId;
    private String flowName;
    private String title;
    private String content;
    private Long applicantId;
    private String applicantName;
    private Integer status;
    private Integer currentSeq;
    private Long currentRoleId;
    private String currentNodeName;
    private String currentRoleName;
    private boolean canHandle;
    private LocalDateTime createTime;
    private List<ApprovalRecordVO> records = new ArrayList<>();
}
