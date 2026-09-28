package com.s2admin.module.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.s2admin.module.common.PageQuery;
import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.SecurityUtils;
import com.s2admin.module.security.LoginUser;
import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.entity.SysUser;
import com.s2admin.module.system.form.MessageForm;
import com.s2admin.module.system.repository.SysRoleRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import com.s2admin.module.system.service.MessageService;
import com.s2admin.module.tenant.TenantContext;
import com.s2admin.module.workflow.ConditionExpr;
import com.s2admin.module.workflow.entity.SysApproval;
import com.s2admin.module.workflow.entity.SysApprovalRecord;
import com.s2admin.module.workflow.entity.SysFlow;
import com.s2admin.module.workflow.entity.SysFlowNode;
import com.s2admin.module.workflow.form.ApprovalActionForm;
import com.s2admin.module.workflow.form.ApprovalSubmitForm;
import com.s2admin.module.workflow.repository.SysApprovalRecordRepository;
import com.s2admin.module.workflow.repository.SysApprovalRepository;
import com.s2admin.module.workflow.repository.SysFlowNodeRepository;
import com.s2admin.module.workflow.repository.SysFlowRepository;
import com.s2admin.module.workflow.vo.ApprovalRecordVO;
import com.s2admin.module.workflow.vo.ApprovalVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final SysFlowRepository flowRepository;
    private final SysFlowNodeRepository nodeRepository;
    private final SysApprovalRepository approvalRepository;
    private final SysApprovalRecordRepository recordRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRepository userRepository;
    private final UserPermissionService permissionService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ApprovalVO submit(ApprovalSubmitForm form) {
        SysFlow flow = flowRepository.findById(form.getFlowId())
                .orElseThrow(() -> BusinessException.notFound("流程不存在"));
        if (flow.getStatus() != null && flow.getStatus() != 0) {
            throw new BusinessException("流程已停用");
        }
        List<SysFlowNode> nodes = nodeRepository.findByFlowIdOrderBySortAscIdAsc(flow.getId());
        if (nodes.isEmpty()) {
            throw new BusinessException("流程没有审批节点");
        }
        List<Step> steps = new ArrayList<>();
        int seq = 1;
        for (SysFlowNode node : nodes) {
            steps.add(snapshot(seq++, node));
        }
        if (steps.stream().noneMatch(step -> !isCondition(step))) {
            throw new BusinessException("流程至少要有一个审批节点");
        }
        LoginUser me = SecurityUtils.getLoginUser();
        SysApproval approval = new SysApproval();
        approval.setFlowId(flow.getId());
        approval.setFlowName(flow.getName());
        approval.setTitle(form.getTitle().trim());
        approval.setContent(form.getContent());
        approval.setApplicantId(me.getId());
        approval.setApplicantName(me.getUsername());
        approval.setTenantId(resolveTenant(me.getId()));
        approval.setStatus(1);
        Step first = landOnApprove(steps.get(0), steps, approval);
        if (first == null) {
            throw new BusinessException("流程条件没有进入审批节点");
        }
        applyStep(approval, first);
        approval.setStepsJson(writeSteps(steps));
        approvalRepository.save(approval);
        record(approval.getId(), 0, "提交", "submit", me, form.getContent());
        notifyRole(first.getRoleId(), "待审批: " + approval.getTitle(),
                me.getUsername() + " 提交了「" + approval.getTitle() + "」,请处理。");
        return toVO(approval, true);
    }

    @Transactional(readOnly = true)
    public PageResult<ApprovalVO> mine(PageQuery query) {
        Long uid = SecurityUtils.getUserId();
        return page((root, cq, cb) -> {
            List<Predicate> predicates = keyword(query, root, cb);
            predicates.add(cb.equal(root.get("applicantId"), uid));
            limitTenant(predicates, root, cb);
            return cb.and(predicates.toArray(new Predicate[0]));
        }, query);
    }

    @Transactional(readOnly = true)
    public PageResult<ApprovalVO> pending(PageQuery query) {
        Set<Long> roleIds = currentRoleIds();
        boolean privileged = isPrivileged();
        return page((root, cq, cb) -> {
            List<Predicate> predicates = keyword(query, root, cb);
            predicates.add(cb.equal(root.get("status"), 1));
            if (!privileged) {
                if (roleIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("currentRoleId").in(roleIds));
                }
            }
            limitTenant(predicates, root, cb);
            return cb.and(predicates.toArray(new Predicate[0]));
        }, query);
    }

    @Transactional(readOnly = true)
    public ApprovalVO detail(Long id) {
        SysApproval approval = getEntity(id);
        assertCanView(approval);
        return toVO(approval, true);
    }

    @Transactional
    public ApprovalVO approve(Long id, ApprovalActionForm form) {
        SysApproval approval = getEntity(id);
        assertPendingHandler(approval);
        List<Step> steps = readSteps(approval);
        int index = approval.getCurrentSeq() == null ? 1 : approval.getCurrentSeq();
        Step current = steps.stream().filter(step -> step.seq == index).findFirst()
                .orElseThrow(() -> new BusinessException("当前审批节点不存在"));
        boolean countersign = current.getSignMode() != null && current.getSignMode() == 2;
        LoginUser me = SecurityUtils.getLoginUser();
        if (countersign && parseIds(approval.getApprovedUserIds()).contains(me.getId())) {
            throw new BusinessException("你已审批过当前节点");
        }
        record(approval.getId(), current.getSeq(), current.getName(), "approve", me, comment(form));
        if (countersign && !isPrivileged()) {
            Set<Long> required = requiredSigners(current, approval);
            if (!required.contains(me.getId())) {
                throw BusinessException.forbidden("当前节点不由你审批");
            }
            Set<Long> approved = parseIds(approval.getApprovedUserIds());
            approved.add(me.getId());
            approval.setApprovedUserIds(joinIds(approved));
            if (!approved.containsAll(required)) {
                approvalRepository.save(approval);
                return toVO(approval, true);
            }
        }
        moveNext(approval, steps, current);
        approvalRepository.save(approval);
        return toVO(approval, true);
    }

    @Transactional
    public ApprovalVO reject(Long id, ApprovalActionForm form) {
        SysApproval approval = getEntity(id);
        assertPendingHandler(approval);
        if (!StringUtils.hasText(comment(form))) {
            throw new BusinessException("驳回时请填写意见");
        }
        List<Step> steps = readSteps(approval);
        int index = approval.getCurrentSeq() == null ? 1 : approval.getCurrentSeq();
        Step current = steps.stream().filter(step -> step.getSeq() == index).findFirst().orElse(null);
        record(approval.getId(), approval.getCurrentSeq(), approval.getCurrentNodeName(),
                "reject", SecurityUtils.getLoginUser(), comment(form));
        Integer rejectTo = current == null ? null : current.getRejectTo();
        Step target = null;
        if (rejectTo != null && rejectTo == -1) {
            target = previousApprove(steps, index);
        } else if (rejectTo != null && rejectTo > 0) {
            target = steps.stream().filter(step -> step.getSeq() == rejectTo).findFirst().orElse(null);
        }
        if (target == null) {
            finishRejected(approval);
        } else {
            Step landed = landOnApprove(target, steps, approval);
            if (landed == null) {
                finishRejected(approval);
            } else {
                approval.setStatus(1);
                applyStep(approval, landed);
                notifyRole(landed.getRoleId(), "待审批: " + approval.getTitle(),
                        "「" + approval.getTitle() + "」被驳回到「" + landed.getName() + "」。");
            }
        }
        approvalRepository.save(approval);
        return toVO(approval, true);
    }

    @Transactional
    public ApprovalVO cancel(Long id, ApprovalActionForm form) {
        SysApproval approval = getEntity(id);
        if (!SecurityUtils.getUserId().equals(approval.getApplicantId())) {
            throw BusinessException.forbidden("只能撤回自己的申请");
        }
        if (approval.getStatus() == null || approval.getStatus() != 1) {
            throw new BusinessException("只有审批中的单据可以撤回");
        }
        record(approval.getId(), approval.getCurrentSeq(), approval.getCurrentNodeName(),
                "cancel", SecurityUtils.getLoginUser(), comment(form));
        approval.setStatus(4);
        approval.setCurrentRoleId(null);
        approval.setCurrentNodeName("已撤回");
        approvalRepository.save(approval);
        return toVO(approval, true);
    }

    private PageResult<ApprovalVO> page(Specification<SysApproval> spec, PageQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Page<SysApproval> page = approvalRepository.findAll(spec, query.toPageable());
        return PageResult.of(page, item -> toVO(item, false));
    }

    private List<Predicate> keyword(PageQuery query, jakarta.persistence.criteria.Root<SysApproval> root,
                                    jakarta.persistence.criteria.CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();
        limitTenant(predicates, root, cb);
        if (StringUtils.hasText(query.getKeyword())) {
            String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("applicantName")), like)
            ));
        }
        return predicates;
    }

    private void assertPendingHandler(SysApproval approval) {
        if (approval.getStatus() == null || approval.getStatus() != 1) {
            throw new BusinessException("单据不在审批中");
        }
        LoginUser me = SecurityUtils.getLoginUser();
        if (!isPrivileged() && me.getId().equals(approval.getApplicantId())) {
            throw new BusinessException("不能审批自己发起的申请");
        }
        if (isPrivileged()) {
            return;
        }
        if (approval.getCurrentRoleId() == null || !currentRoleIds().contains(approval.getCurrentRoleId())) {
            throw BusinessException.forbidden("当前节点不由你审批");
        }
    }

    private void assertCanView(SysApproval approval) {
        Long uid = SecurityUtils.getUserId();
        if (uid.equals(approval.getApplicantId()) || isPrivileged()) {
            return;
        }
        if (approval.getStatus() != null && approval.getStatus() == 1
                && approval.getCurrentRoleId() != null
                && currentRoleIds().contains(approval.getCurrentRoleId())) {
            return;
        }
        if (SecurityUtils.hasPermission("system:flow:view")) {
            return;
        }
        throw BusinessException.forbidden("无权查看该审批单");
    }

    private void applyStep(SysApproval approval, Step step) {
        approval.setCurrentSeq(step.getSeq());
        approval.setCurrentRoleId(step.getRoleId() != null && step.getRoleId() > 0 ? step.getRoleId() : null);
        approval.setCurrentNodeName(step.getName());
        approval.setApprovedUserIds(null);
    }

    private void moveNext(SysApproval approval, List<Step> steps, Step current) {
        Step next = landOnApprove(stepAfter(steps, current.getSeq()), steps, approval);
        if (next == null) {
            approval.setStatus(2);
            approval.setCurrentRoleId(null);
            approval.setCurrentNodeName("已通过");
            approval.setApprovedUserIds(null);
            return;
        }
        applyStep(approval, next);
        notifyRole(next.getRoleId(), "待审批: " + approval.getTitle(),
                "「" + approval.getTitle() + "」已进入「" + next.getName() + "」。");
    }

    private void finishRejected(SysApproval approval) {
        approval.setStatus(3);
        approval.setCurrentRoleId(null);
        approval.setCurrentNodeName("已驳回");
        approval.setApprovedUserIds(null);
    }

    private Step snapshot(int seq, SysFlowNode node) {
        Step step = new Step();
        step.setSeq(seq);
        step.setName(node.getName());
        int type = node.getNodeType() == null ? 1 : node.getNodeType();
        step.setNodeType(type);
        step.setSignMode(node.getSignMode() == null ? 1 : node.getSignMode());
        step.setRejectTo(node.getRejectTo());
        step.setConditionExpr(node.getConditionExpr());
        step.setYesSeq(node.getYesSeq());
        step.setNoSeq(node.getNoSeq());
        if (type == 2) {
            step.setRoleId(0L);
            step.setRoleName("条件");
            return step;
        }
        if (node.getRoleId() == null || node.getRoleId() == 0) {
            throw new BusinessException("审批节点缺少角色");
        }
        SysRole role = roleRepository.findById(node.getRoleId())
                .orElseThrow(() -> new BusinessException("审批角色不存在"));
        if (role.getStatus() != null && role.getStatus() != 0) {
            throw new BusinessException("审批角色已停用: " + role.getName());
        }
        step.setRoleId(role.getId());
        step.setRoleName(role.getName());
        return step;
    }

    private Step landOnApprove(Step cursor, List<Step> steps, SysApproval approval) {
        Set<Integer> seen = new HashSet<>();
        while (cursor != null && isCondition(cursor)) {
            if (!seen.add(cursor.getSeq())) {
                throw new BusinessException("条件分支出现循环");
            }
            boolean yes = ConditionExpr.matches(cursor.getConditionExpr(), approval.getTitle(), approval.getContent());
            Integer jump = yes ? cursor.getYesSeq() : cursor.getNoSeq();
            if (jump == null || jump == 0) {
                return null;
            }
            int target = jump;
            cursor = steps.stream().filter(step -> step.getSeq() == target).findFirst()
                    .orElseThrow(() -> new BusinessException("条件指向的节点不存在"));
        }
        return cursor;
    }

    private Step stepAfter(List<Step> steps, int seq) {
        return steps.stream().filter(step -> step.getSeq() == seq + 1).findFirst().orElse(null);
    }

    private Step previousApprove(List<Step> steps, int seq) {
        Step found = null;
        for (Step step : steps) {
            if (!isCondition(step) && step.getSeq() < seq && (found == null || step.getSeq() > found.getSeq())) {
                found = step;
            }
        }
        return found;
    }

    private boolean isCondition(Step step) {
        return step.getNodeType() != null && step.getNodeType() == 2;
    }

    private Set<Long> requiredSigners(Step step, SysApproval approval) {
        Set<Long> ids = new HashSet<>(userRepository.findIdsByRoleId(step.getRoleId()));
        if (approval.getApplicantId() != null) {
            ids.remove(approval.getApplicantId());
        }
        if (ids.isEmpty()) {
            throw new BusinessException("会签节点没有其他审批人");
        }
        return ids;
    }

    private Set<Long> parseIds(String csv) {
        Set<Long> ids = new HashSet<>();
        if (!StringUtils.hasText(csv)) {
            return ids;
        }
        for (String part : csv.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private String joinIds(Set<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private Long resolveTenant(Long userId) {
        Long forced = TenantContext.get();
        if (forced != null && forced > 0) {
            return forced;
        }
        return userRepository.findById(userId).map(SysUser::getTenantId).orElse(null);
    }

    private void limitTenant(List<Predicate> predicates, jakarta.persistence.criteria.Root<SysApproval> root,
                             jakarta.persistence.criteria.CriteriaBuilder cb) {
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
        }
    }

    private void record(Long approvalId, Integer seq, String nodeName, String action, LoginUser user, String comment) {
        SysApprovalRecord record = new SysApprovalRecord();
        record.setApprovalId(approvalId);
        record.setSeq(seq);
        record.setNodeName(nodeName);
        record.setAction(action);
        record.setOperatorId(user.getId());
        record.setOperatorName(user.getUsername());
        record.setComment(StringUtils.hasText(comment) ? comment.trim() : null);
        recordRepository.save(record);
    }

    private void notifyRole(Long roleId, String title, String content) {
        if (roleId == null || roleId <= 0) {
            return;
        }
        try {
            List<Long> ids = userRepository.findIdsByRoleId(roleId);
            if (ids.isEmpty()) {
                return;
            }
            if (ids.size() > 20) {
                ids = ids.subList(0, 20);
            }
            MessageForm form = new MessageForm();
            form.setTitle(title);
            form.setContent(content);
            form.setReceiverIds(ids);
            messageService.sendNotice(form);
        } catch (Exception e) {
            log.debug("审批通知未发出: {}", e.getMessage());
        }
    }

    private Set<Long> currentRoleIds() {
        Set<String> codes = permissionService.loadRoles(SecurityUtils.getUserId());
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(roleRepository.findIdsByCodeIn(codes));
    }

    private boolean isPrivileged() {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null) {
            return false;
        }
        if (user.getRoles() != null && user.getRoles().contains(UserPermissionService.SUPER_ADMIN)) {
            return true;
        }
        return user.getPermissions() != null && user.getPermissions().contains("*");
    }

    private String comment(ApprovalActionForm form) {
        return form == null ? null : form.getComment();
    }

    private String writeSteps(List<Step> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception e) {
            throw new BusinessException("保存流程快照失败");
        }
    }

    private List<Step> readSteps(SysApproval approval) {
        try {
            List<Step> steps = objectMapper.readValue(approval.getStepsJson(), new TypeReference<>() {
            });
            if (steps == null || steps.isEmpty()) {
                throw new BusinessException("审批快照为空");
            }
            return steps;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("审批快照无法读取");
        }
    }

    private SysApproval getEntity(Long id) {
        SysApproval approval = approvalRepository.findById(id).orElseThrow(() -> BusinessException.notFound("审批单不存在"));
        Long tenantId = TenantContext.get();
        if (tenantId != null && !tenantId.equals(approval.getTenantId())) {
            throw BusinessException.notFound("审批单不存在");
        }
        return approval;
    }

    private ApprovalVO toVO(SysApproval approval, boolean withRecords) {
        ApprovalVO vo = new ApprovalVO();
        vo.setId(approval.getId());
        vo.setFlowId(approval.getFlowId());
        vo.setFlowName(approval.getFlowName());
        vo.setTitle(approval.getTitle());
        vo.setContent(approval.getContent());
        vo.setApplicantId(approval.getApplicantId());
        vo.setApplicantName(approval.getApplicantName());
        vo.setStatus(approval.getStatus());
        vo.setCurrentSeq(approval.getCurrentSeq());
        vo.setCurrentRoleId(approval.getCurrentRoleId());
        vo.setCurrentNodeName(approval.getCurrentNodeName());
        if (approval.getCurrentRoleId() != null) {
            roleRepository.findById(approval.getCurrentRoleId()).ifPresent(role -> vo.setCurrentRoleName(role.getName()));
        }
        boolean pending = approval.getStatus() != null && approval.getStatus() == 1;
        boolean self = SecurityUtils.getUserId().equals(approval.getApplicantId());
        boolean roleMatch = approval.getCurrentRoleId() != null && currentRoleIds().contains(approval.getCurrentRoleId());
        boolean already = parseIds(approval.getApprovedUserIds()).contains(SecurityUtils.getUserId());
        vo.setCanHandle(pending && !already && (isPrivileged() || (roleMatch && !self)));
        if (withRecords && approval.getId() != null) {
            vo.setRecords(recordRepository.findByApprovalIdOrderByIdAsc(approval.getId()).stream().map(record -> {
                ApprovalRecordVO item = new ApprovalRecordVO();
                item.setId(record.getId());
                item.setSeq(record.getSeq());
                item.setNodeName(record.getNodeName());
                item.setAction(record.getAction());
                item.setOperatorId(record.getOperatorId());
                item.setOperatorName(record.getOperatorName());
                item.setComment(record.getComment());
                item.setCreateTime(record.getCreateTime());
                return item;
            }).toList());
        }
        return vo;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class Step {
        private int seq;
        private String name;
        private Long roleId;
        private String roleName;
        /** 1 审批 2 条件 */
        private Integer nodeType;
        /** 1 或签 2 会签 */
        private Integer signMode;
        private Integer rejectTo;
        private String conditionExpr;
        private Integer yesSeq;
        private Integer noSeq;

        public Step(int seq, String name, Long roleId, String roleName) {
            this.seq = seq;
            this.name = name;
            this.roleId = roleId;
            this.roleName = roleName;
            this.nodeType = 1;
            this.signMode = 1;
        }
    }
}
