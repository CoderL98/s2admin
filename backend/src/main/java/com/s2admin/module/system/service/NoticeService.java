package com.s2admin.module.system.service;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.system.entity.SysNotice;
import com.s2admin.module.system.form.NoticeForm;
import com.s2admin.module.system.form.NoticeQuery;
import com.s2admin.module.system.repository.SysNoticeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final SysNoticeRepository noticeRepository;

    @Transactional(readOnly = true)
    public PageResult<SysNotice> page(NoticeQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Specification<SysNotice> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), like));
            }
            if (query.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), query.getStatus()));
            }
            if (query.getType() != null) {
                predicates.add(cb.equal(root.get("type"), query.getType()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SysNotice> page = noticeRepository.findAll(spec, query.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public List<SysNotice> published() {
        return noticeRepository.findByStatusOrderByPinnedDescPublishTimeDesc(1);
    }

    @Transactional
    public SysNotice create(NoticeForm form) {
        SysNotice notice = new SysNotice();
        apply(notice, form);
        return noticeRepository.save(notice);
    }

    @Transactional
    public SysNotice update(Long id, NoticeForm form) {
        SysNotice notice = getEntity(id);
        apply(notice, form);
        return noticeRepository.save(notice);
    }

    @Transactional
    public void delete(Long id) {
        noticeRepository.delete(getEntity(id));
    }

    @Transactional
    public SysNotice publish(Long id, boolean published) {
        SysNotice notice = getEntity(id);
        notice.setStatus(published ? 1 : 0);
        notice.setPublishTime(published ? LocalDateTime.now() : null);
        return noticeRepository.save(notice);
    }

    private void apply(SysNotice notice, NoticeForm form) {
        notice.setTitle(form.getTitle());
        notice.setContent(form.getContent());
        notice.setType(form.getType() == null ? 1 : form.getType());
        notice.setPinned(form.getPinned() == null ? 0 : form.getPinned());
        notice.setRemark(form.getRemark());
        int status = form.getStatus() == null ? 0 : form.getStatus();
        notice.setStatus(status);
        if (status == 1 && notice.getPublishTime() == null) {
            notice.setPublishTime(LocalDateTime.now());
        }
    }

    private SysNotice getEntity(Long id) {
        return noticeRepository.findById(id).orElseThrow(() -> BusinessException.notFound("公告不存在"));
    }
}
