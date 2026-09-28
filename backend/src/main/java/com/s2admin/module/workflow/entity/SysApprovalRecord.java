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
 * 审批动作记录
 */
@Getter
@Setter
@Entity
@Table(name = "sys_approval_record")
@SQLDelete(sql = "UPDATE sys_approval_record SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysApprovalRecord extends BaseEntity {

    @Column(name = "approval_id", nullable = false)
    private Long approvalId;

    @Column
    private Integer seq;

    @Column(name = "node_name", length = 50)
    private String nodeName;

    /** submit / approve / reject / cancel */
    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 50)
    private String operatorName;

    @Column(length = 500)
    private String comment;
}
