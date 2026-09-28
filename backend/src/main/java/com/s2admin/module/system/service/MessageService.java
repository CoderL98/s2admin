package com.s2admin.module.system.service;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.SecurityUtils;
import com.s2admin.module.system.entity.SysMessage;
import com.s2admin.module.system.entity.SysUser;
import com.s2admin.module.system.form.MessageForm;
import com.s2admin.module.system.form.MessageQuery;
import com.s2admin.module.system.repository.SysMessageRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final SysMessageRepository messageRepository;
    private final SysUserRepository userRepository;
    private final DataScopeService dataScopeService;

    @Transactional
    public void send(MessageForm form) {
        Long senderId = SecurityUtils.getUserId();
        String senderName = SecurityUtils.getUsername();
        LinkedHashSet<Long> receivers = new LinkedHashSet<>();
        for (Long receiverId : form.getReceiverIds()) {
            if (receiverId == null) {
                throw new BusinessException("接收人不存在");
            }
            receivers.add(receiverId);
        }
        if (receivers.isEmpty()) {
            throw new BusinessException("请选择接收人");
        }
        for (Long receiverId : receivers) {
            SysUser receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new BusinessException("接收人不存在"));
            dataScopeService.assertCanAccessUser(receiver);
            SysMessage msg = new SysMessage();
            msg.setTitle(form.getTitle());
            msg.setContent(form.getContent());
            msg.setSenderId(senderId);
            msg.setSenderName(senderName);
            msg.setReceiverId(receiverId);
            msg.setReadFlag(0);
            messageRepository.save(msg);
        }
    }

    /**
     * 系统通知,不按发送人的数据范围过滤。
     * 不加事务:SQLite 单连接下内层事务抛错会把外层审批事务标成必须回滚。
     */
    public void sendNotice(MessageForm form) {
        if (form == null || form.getReceiverIds() == null || form.getReceiverIds().isEmpty()) {
            return;
        }
        Long senderId = SecurityUtils.getUserId();
        String senderName = SecurityUtils.getUsername();
        for (Long receiverId : form.getReceiverIds()) {
            if (receiverId == null || userRepository.findById(receiverId).isEmpty()) {
                continue;
            }
            SysMessage msg = new SysMessage();
            msg.setTitle(form.getTitle());
            msg.setContent(form.getContent());
            msg.setSenderId(senderId);
            msg.setSenderName(senderName);
            msg.setReceiverId(receiverId);
            msg.setReadFlag(0);
            messageRepository.save(msg);
        }
    }

    @Transactional(readOnly = true)
    public PageResult<SysMessage> myPage(MessageQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Long uid = SecurityUtils.getUserId();
        Specification<SysMessage> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("receiverId"), uid));
            if (query.getReadFlag() != null) {
                predicates.add(cb.equal(root.get("readFlag"), query.getReadFlag()));
            }
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("content")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SysMessage> page = messageRepository.findAll(spec, query.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return messageRepository.countByReceiverIdAndReadFlag(SecurityUtils.getUserId(), 0);
    }

    @Transactional
    public void markRead(Long id) {
        SysMessage msg = messageRepository.findById(id).orElseThrow(() -> BusinessException.notFound("消息不存在"));
        if (!msg.getReceiverId().equals(SecurityUtils.getUserId())) {
            throw BusinessException.forbidden("不能操作他人的消息");
        }
        msg.setReadFlag(1);
        msg.setReadTime(LocalDateTime.now());
        messageRepository.save(msg);
    }

    @Transactional
    public void deleteMine(Long id) {
        SysMessage msg = messageRepository.findById(id).orElseThrow(() -> BusinessException.notFound("消息不存在"));
        if (!msg.getReceiverId().equals(SecurityUtils.getUserId())) {
            throw BusinessException.forbidden("不能操作他人的消息");
        }
        messageRepository.delete(msg);
    }

    @Transactional
    public void markAllRead() {
        Long uid = SecurityUtils.getUserId();
        messageRepository.findByReceiverIdOrderByCreateTimeDesc(uid, org.springframework.data.domain.Pageable.unpaged())
                .forEach(msg -> {
                    if (msg.getReadFlag() == null || msg.getReadFlag() == 0) {
                        msg.setReadFlag(1);
                        msg.setReadTime(LocalDateTime.now());
                    }
                });
    }

    @Transactional(readOnly = true)
    public SysUser requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }
}
