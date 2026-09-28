package com.s2admin.module.monitor.service;

import com.s2admin.module.common.PageQuery;
import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.UniqueFields;
import com.s2admin.module.monitor.entity.SysJob;
import com.s2admin.module.monitor.entity.SysJobLog;
import com.s2admin.module.monitor.form.JobForm;
import com.s2admin.module.monitor.repository.SysJobLogRepository;
import com.s2admin.module.monitor.repository.SysJobRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final SysJobRepository jobRepository;
    private final SysJobLogRepository logRepository;
    private final JobHandlerRegistry handlers;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    @Transactional(readOnly = true)
    public PageResult<SysJob> page(PageQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Specification<SysJob> spec = (root, cq, cb) -> {
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
        Page<SysJob> page = jobRepository.findAll(spec, query.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public PageResult<SysJobLog> logs(Long jobId, PageQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Specification<SysJobLog> spec = (root, cq, cb) -> jobId == null
                ? cb.conjunction()
                : cb.equal(root.get("jobId"), jobId);
        return PageResult.of(logRepository.findAll(spec, query.toPageable()));
    }

    @Transactional
    public SysJob create(JobForm form) {
        String code = normalizeCode(form.getCode());
        if (jobRepository.existsByCode(code)) {
            throw new BusinessException("任务编码已存在");
        }
        validate(form);
        SysJob job = new SysJob();
        apply(job, form, code);
        job.setNextFireTime(nextFire(job.getCron(), LocalDateTime.now()));
        return jobRepository.save(job);
    }

    @Transactional
    public SysJob update(Long id, JobForm form) {
        SysJob job = get(id);
        String code = normalizeCode(form.getCode());
        if (!code.equals(job.getCode()) && jobRepository.existsByCode(code)) {
            throw new BusinessException("任务编码已存在");
        }
        validate(form);
        apply(job, form, code);
        job.setNextFireTime(nextFire(job.getCron(), LocalDateTime.now()));
        return jobRepository.save(job);
    }

    @Transactional
    public void delete(Long id) {
        SysJob job = get(id);
        job.setCode(UniqueFields.tombstone(job.getCode(), id, 50));
        job.setStatus(1);
        jobRepository.save(job);
        jobRepository.delete(job);
    }

    public void runNow(Long id) {
        execute(id, true);
    }

    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (SysJob job : jobRepository.findAll()) {
            if (job.getStatus() != null && job.getStatus() != 0) {
                continue;
            }
            if (job.getNextFireTime() != null && job.getNextFireTime().isAfter(now)) {
                continue;
            }
            execute(job.getId(), false);
        }
    }

    private void execute(Long id, boolean manual) {
        if (!running.add(id)) {
            if (manual) {
                throw new BusinessException("任务正在执行");
            }
            return;
        }
        long started = System.currentTimeMillis();
        SysJob job = jobRepository.findById(id).orElse(null);
        if (job == null) {
            running.remove(id);
            return;
        }
        String message = "";
        int status = 0;
        try {
            message = handlers.run(job.getHandler(), job.getParams());
            if (message == null) {
                message = "完成";
            }
        } catch (Exception e) {
            status = 1;
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("定时任务失败 {}: {}", job.getCode(), message);
        } finally {
            running.remove(id);
        }
        SysJobLog row = new SysJobLog();
        row.setJobId(job.getId());
        row.setJobName(job.getName());
        row.setStatus(status);
        row.setMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
        row.setCostMs(System.currentTimeMillis() - started);
        row.setFireTime(LocalDateTime.now());
        logRepository.save(row);
        if (!manual) {
            job.setLastFireTime(row.getFireTime());
            job.setNextFireTime(nextFire(job.getCron(), LocalDateTime.now()));
            jobRepository.save(job);
        }
    }

    private void validate(JobForm form) {
        if (!JobHandlerRegistry.HANDLERS.contains(form.getHandler())) {
            throw new BusinessException("处理器只允许 sample.ping 或 log.cleanup");
        }
        CronExpression expr = parse(form.getCron());
        LocalDateTime first = expr.next(LocalDateTime.now());
        if (first == null) {
            throw new BusinessException("cron 没有下一次执行时间");
        }
        LocalDateTime second = expr.next(first);
        if (second != null && Duration.between(first, second).getSeconds() < 30) {
            throw new BusinessException("两次触发至少间隔 30 秒");
        }
    }

    private LocalDateTime nextFire(String cron, LocalDateTime from) {
        LocalDateTime next = parse(cron).next(from);
        return next == null ? from.plusDays(1) : next;
    }

    private CronExpression parse(String cron) {
        try {
            return CronExpression.parse(cron.trim());
        } catch (Exception e) {
            throw new BusinessException("cron 不合法，示例：0 0 3 * * *");
        }
    }

    private void apply(SysJob job, JobForm form, String code) {
        job.setName(form.getName().trim());
        job.setCode(code);
        job.setCron(form.getCron().trim());
        job.setHandler(form.getHandler().trim());
        job.setParams(form.getParams());
        job.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        job.setRemark(form.getRemark());
    }

    private String normalizeCode(String code) {
        String value = code == null ? "" : code.trim().toUpperCase();
        if (!value.matches("^[A-Z][A-Z0-9_]{1,49}$")) {
            throw new BusinessException("任务编码需为大写字母开头");
        }
        return value;
    }

    private SysJob get(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> BusinessException.notFound("任务不存在"));
    }
}
