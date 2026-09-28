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
 * 流程节点。按 sort 从小到大依次审批,同一节点由该角色中任意一人处理。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_flow_node")
@SQLDelete(sql = "UPDATE sys_flow_node SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysFlowNode extends BaseEntity {

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column
    private Integer sort = 1;

    /** 条件节点存 0,审批节点为角色 id。 */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /** 1 审批 2 条件 */
    @Column(name = "node_type")
    private Integer nodeType = 1;

    /** 1 或签 2 会签 */
    @Column(name = "sign_mode")
    private Integer signMode = 1;

    /** 空或 0 结束驳回,-1 上一审批节点,正数跳到该序号 */
    @Column(name = "reject_to")
    private Integer rejectTo;

    @Column(name = "condition_expr", length = 200)
    private String conditionExpr;

    @Column(name = "yes_seq")
    private Integer yesSeq;

    @Column(name = "no_seq")
    private Integer noSeq;
}
