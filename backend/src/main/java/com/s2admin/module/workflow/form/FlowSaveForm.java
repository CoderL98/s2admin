package com.s2admin.module.workflow.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FlowSaveForm {

    @NotBlank(message = "流程名称不能为空")
    @Size(max = 50)
    private String name;

    @NotBlank(message = "流程编码不能为空")
    @Size(max = 50)
    private String code;

    private Integer status = 0;

    @Size(max = 500)
    private String remark;

    @NotEmpty(message = "至少配置一个审批节点")
    @Valid
    private List<Node> nodes;

    @Data
    public static class Node {
        @NotBlank(message = "节点名称不能为空")
        @Size(max = 50)
        private String name;

        /** 1 审批 2 条件。条件节点可以不选角色。 */
        private Integer nodeType = 1;

        private Long roleId;

        /** 1 或签 2 会签 */
        private Integer signMode = 1;

        /** 空结束,-1 上一节点,正数为节点序号 */
        private Integer rejectTo;

        @Size(max = 200)
        private String conditionExpr;

        private Integer yesSeq;

        private Integer noSeq;
    }
}
