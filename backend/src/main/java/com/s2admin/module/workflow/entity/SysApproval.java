package com.s2admin.module.workflow.entity;

import com.s2admin.module.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 审批单。节点快照写在 stepsJson,流程事后修改不影响在途单据。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_approval")
@SQLDelete(sql = "UPDATE sys_approval SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysApproval extends BaseEntity {

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "flow_name", length = 50)
    private String flowName;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "applicant_name", length = 50)
    private String applicantName;

    /** 1 审批中 2 已通过 3 已驳回 4 已撤回 */
    @Column
    private Integer status = 1;

    /** 当前步骤,从 1 开始 */
    @Column(name = "current_seq")
    private Integer currentSeq = 1;

    @Column(name = "current_role_id")
    private Long currentRoleId;

    @Column(name = "current_node_name", length = 50)
    private String currentNodeName;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    @Column(name = "tenant_id")
    private Long tenantId;

    /** 当前会签节点已同意的用户 id,逗号分隔。 */
    @Column(name = "approved_user_ids", columnDefinition = "TEXT")
    private String approvedUserIds;
}
