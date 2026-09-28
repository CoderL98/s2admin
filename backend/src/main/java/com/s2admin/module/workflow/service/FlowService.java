package com.s2admin.module.workflow.service;

import com.s2admin.module.common.PageQuery;
import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.UniqueFields;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.repository.SysRoleRepository;
import com.s2admin.module.workflow.ConditionExpr;
import com.s2admin.module.workflow.entity.SysFlow;
import com.s2admin.module.workflow.entity.SysFlowNode;
import com.s2admin.module.workflow.form.FlowSaveForm;
import com.s2admin.module.workflow.repository.SysApprovalRepository;
import com.s2admin.module.workflow.repository.SysFlowNodeRepository;
import com.s2admin.module.workflow.repository.SysFlowRepository;
import com.s2admin.module.workflow.vo.FlowNodeVO;
import com.s2admin.module.workflow.vo.FlowVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowService {

    private static final int MAX_NODES = 16;

    private final SysFlowRepository flowRepository;
    private final SysFlowNodeRepository nodeRepository;
    private final SysApprovalRepository approvalRepository;
    private final SysRoleRepository roleRepository;

    @Transactional(readOnly = true)
    public PageResult<FlowVO> page(PageQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Specification<SysFlow> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SysFlow> page = flowRepository.findAll(spec, query.toPageable());
        return PageResult.of(page, this::toVO);
    }

    @Transactional(readOnly = true)
    public List<FlowVO> options() {
        return flowRepository.findAll().stream()
                .filter(flow -> flow.getStatus() == null || flow.getStatus() == 0)
                .map(this::toVO)
                .toList();
    }

    @Transactional(readOnly = true)
    public FlowVO get(Long id) {
        return toVO(getEntity(id));
    }

    @Transactional
    public FlowVO create(FlowSaveForm form) {
        String code = normalizeCode(form.getCode());
        if (flowRepository.existsByCode(code)) {
            throw new BusinessException("流程编码已存在");
        }
        SysFlow flow = new SysFlow();
        apply(flow, form, code);
        flowRepository.save(flow);
        replaceNodes(flow.getId(), form.getNodes());
        return toVO(flow);
    }

    @Transactional
    public FlowVO update(Long id, FlowSaveForm form) {
        SysFlow flow = getEntity(id);
        String code = normalizeCode(form.getCode());
        if (!code.equals(flow.getCode()) && flowRepository.existsByCode(code)) {
            throw new BusinessException("流程编码已存在");
        }
        apply(flow, form, code);
        flowRepository.save(flow);
        replaceNodes(flow.getId(), form.getNodes());
        return toVO(flow);
    }

    @Transactional
    public void delete(Long id) {
        SysFlow flow = getEntity(id);
        if (approvalRepository.countByFlowIdAndStatus(id, 1) > 0) {
            throw new BusinessException("仍有审批中的单据,不能删除流程");
        }
        for (SysFlowNode node : nodeRepository.findByFlowIdOrderBySortAscIdAsc(id)) {
            nodeRepository.delete(node);
        }
        flow.setCode(UniqueFields.tombstone(flow.getCode(), id, 50));
        flowRepository.save(flow);
        flowRepository.delete(flow);
    }

    private void apply(SysFlow flow, FlowSaveForm form, String code) {
        flow.setName(form.getName().trim());
        flow.setCode(code);
        flow.setStatus(form.getStatus() == null ? 0 : form.getStatus());
        flow.setRemark(form.getRemark());
    }

    private void replaceNodes(Long flowId, List<FlowSaveForm.Node> nodes) {
        if (nodes == null || nodes.isEmpty() || nodes.size() > MAX_NODES) {
            throw new BusinessException("审批节点数量需在 1 到 " + MAX_NODES + " 之间");
        }
        for (SysFlowNode old : nodeRepository.findByFlowIdOrderBySortAscIdAsc(flowId)) {
            nodeRepository.delete(old);
        }
        int seq = 1;
        int approveCount = 0;
        for (FlowSaveForm.Node node : nodes) {
            int type = node.getNodeType() == null ? 1 : node.getNodeType();
            if (type != 1 && type != 2) {
                throw new BusinessException("节点类型不正确");
            }
            SysFlowNode entity = new SysFlowNode();
            entity.setFlowId(flowId);
            entity.setName(node.getName().trim());
            entity.setSort(seq);
            entity.setNodeType(type);
            if (type == 2) {
                ConditionExpr.validate(node.getConditionExpr());
                entity.setRoleId(0L);
                entity.setConditionExpr(node.getConditionExpr().trim());
                entity.setYesSeq(requireJump(node.getYesSeq(), nodes.size(), seq, "满足"));
                entity.setNoSeq(requireJump(node.getNoSeq(), nodes.size(), seq, "不满足"));
                entity.setSignMode(1);
            } else {
                if (node.getRoleId() == null) {
                    throw new BusinessException("请选择审批角色");
                }
                SysRole role = roleRepository.findById(node.getRoleId())
                        .orElseThrow(() -> new BusinessException("审批角色不存在"));
                if (role.getStatus() != null && role.getStatus() != 0) {
                    throw new BusinessException("不能使用已停用的角色: " + role.getName());
                }
                if ("SUPER_ADMIN".equals(role.getCode())) {
                    throw new BusinessException("不要把超级管理员角色设为审批节点");
                }
                int sign = node.getSignMode() == null ? 1 : node.getSignMode();
                if (sign != 1 && sign != 2) {
                    throw new BusinessException("会签方式不正确");
                }
                entity.setRoleId(role.getId());
                entity.setSignMode(sign);
                entity.setRejectTo(normalizeReject(node.getRejectTo(), nodes.size(), seq));
                approveCount++;
            }
            nodeRepository.save(entity);
            seq++;
        }
        if (approveCount == 0) {
            throw new BusinessException("流程至少要有一个审批节点");
        }
    }

    private Integer requireJump(Integer target, int size, int self, String label) {
        if (target == null || target < 0 || target > size || target == self) {
            throw new BusinessException(label + "时要指向 0(结束并通过)或其他节点序号");
        }
        return target;
    }

    private Integer normalizeReject(Integer target, int size, int self) {
        if (target == null || target == 0) {
            return null;
        }
        if (target != -1 && (target < 1 || target > size || target == self)) {
            throw new BusinessException("驳回目标只能留空、-1(上一节点)或其他节点序号");
        }
        return target;
    }

    private String normalizeCode(String code) {
        String value = code == null ? "" : code.trim().toUpperCase();
        if (!value.matches("^[A-Z][A-Z0-9_]{1,49}$")) {
            throw new BusinessException("流程编码需为大写字母开头,只含字母、数字和下划线");
        }
        return value;
    }

    private SysFlow getEntity(Long id) {
        return flowRepository.findById(id).orElseThrow(() -> BusinessException.notFound("流程不存在"));
    }

    private FlowVO toVO(SysFlow flow) {
        FlowVO vo = new FlowVO();
        vo.setId(flow.getId());
        vo.setName(flow.getName());
        vo.setCode(flow.getCode());
        vo.setStatus(flow.getStatus());
        vo.setRemark(flow.getRemark());
        vo.setCreateTime(flow.getCreateTime());
        List<FlowNodeVO> nodes = new ArrayList<>();
        if (flow.getId() != null) {
            for (SysFlowNode node : nodeRepository.findByFlowIdOrderBySortAscIdAsc(flow.getId())) {
                FlowNodeVO item = new FlowNodeVO();
                item.setId(node.getId());
                item.setName(node.getName());
                item.setSort(node.getSort());
                item.setRoleId(node.getRoleId());
                item.setNodeType(node.getNodeType() == null ? 1 : node.getNodeType());
                item.setSignMode(node.getSignMode() == null ? 1 : node.getSignMode());
                item.setRejectTo(node.getRejectTo());
                item.setConditionExpr(node.getConditionExpr());
                item.setYesSeq(node.getYesSeq());
                item.setNoSeq(node.getNoSeq());
                if (node.getRoleId() != null && node.getRoleId() > 0) {
                    roleRepository.findById(node.getRoleId()).ifPresent(role -> item.setRoleName(role.getName()));
                }
                nodes.add(item);
            }
        }
        vo.setNodes(nodes);
        vo.setNodeCount(nodes.size());
        return vo;
    }
}
