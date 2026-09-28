package com.s2admin.module.system.service;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.system.entity.ErrorLog;
import com.s2admin.module.system.entity.LoginLog;
import com.s2admin.module.system.entity.OperationLog;
import com.s2admin.module.system.form.LogQuery;
import com.s2admin.module.system.repository.ErrorLogRepository;
import com.s2admin.module.system.repository.LoginLogRepository;
import com.s2admin.module.system.repository.OperationLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.s2admin.module.common.util.CsvUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 日志服务(登录/操作/异常日志查询与清理)
 */
@Service
@RequiredArgsConstructor
public class LogService {

    private static final int MAX_EXPORT = 10_000;

    private final LoginLogRepository loginLogRepository;
    private final OperationLogRepository operationLogRepository;
    private final ErrorLogRepository errorLogRepository;
    private final DataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<LoginLog> loginLogPage(LogQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("loginTime");
            query.setSortDirection("desc");
        }
        Specification<LoginLog> spec = buildSpec(
                query,
                "loginTime",
                (root, cb, like) -> cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("ip")), like)
                ));
        Page<LoginLog> page = loginLogRepository.findAll(spec, query.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public PageResult<OperationLog> opLogPage(LogQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("operationTime");
            query.setSortDirection("desc");
        }
        Specification<OperationLog> spec = buildSpec(
                query,
                "operationTime",
                (root, cb, like) -> cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("module")), like),
                        cb.like(cb.lower(root.get("url")), like)
                ));
        Page<OperationLog> page = operationLogRepository.findAll(spec, query.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public PageResult<ErrorLog> errorLogPage(LogQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("errorTime");
            query.setSortDirection("desc");
        }
        Specification<ErrorLog> spec = buildSpec(
                query,
                "errorTime",
                (root, cb, like) -> cb.or(
                        cb.like(cb.lower(root.get("traceId")), like),
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("url")), like),
                        cb.like(cb.lower(root.get("exception")), like)
                ));
        Page<ErrorLog> page = errorLogRepository.findAll(spec, query.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public PageResult<LoginLog> myLoginLogs(LogQuery query) {
        Long uid = com.s2admin.module.common.util.SecurityUtils.getUserId();
        if (query.getOrderBy() == null) {
            query.setOrderBy("loginTime");
            query.setSortDirection("desc");
        }
        Specification<LoginLog> spec = (root, cq, cb) -> cb.equal(root.get("userId"), uid);
        return PageResult.of(loginLogRepository.findAll(spec, query.toPageable()));
    }

    @Transactional(readOnly = true)
    public PageResult<OperationLog> myOperations(LogQuery query) {
        Long uid = com.s2admin.module.common.util.SecurityUtils.getUserId();
        if (query.getOrderBy() == null) {
            query.setOrderBy("operationTime");
            query.setSortDirection("desc");
        }
        Specification<OperationLog> spec = (root, cq, cb) -> cb.equal(root.get("userId"), uid);
        return PageResult.of(operationLogRepository.findAll(spec, query.toPageable()));
    }

    @Transactional(readOnly = true)
    public ErrorLog errorLogById(Long id) {
        ErrorLog log = errorLogRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("异常日志不存在"));
        dataScopeService.assertCanAccessOwner(log.getUserId());
        return log;
    }

    @Transactional(readOnly = true)
    public OperationLog opLogById(Long id) {
        OperationLog log = operationLogRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("操作日志不存在"));
        dataScopeService.assertCanAccessOwner(log.getUserId());
        return log;
    }

    @Transactional(readOnly = true)
    public String exportLoginLogCsv(LogQuery query) {
        Specification<LoginLog> spec = buildSpec(
                query,
                "loginTime",
                (root, cb, like) -> cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("ip")), like)
                ));
        assertExportSize(loginLogRepository.count(spec));
        List<LoginLog> rows = loginLogRepository.findAll(spec);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF').append("username,ip,browser,os,status,message,loginTime\n");
        for (LoginLog row : rows) {
            sb.append(CsvUtils.escape(row.getUsername())).append(',')
                    .append(CsvUtils.escape(row.getIp())).append(',')
                    .append(CsvUtils.escape(row.getBrowser())).append(',')
                    .append(CsvUtils.escape(row.getOs())).append(',')
                    .append(row.getStatus()).append(',')
                    .append(CsvUtils.escape(row.getMessage())).append(',')
                    .append(row.getLoginTime()).append('\n');
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportOpLogCsv(LogQuery query) {
        Specification<OperationLog> spec = buildSpec(
                query,
                "operationTime",
                (root, cb, like) -> cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("module")), like),
                        cb.like(cb.lower(root.get("url")), like)
                ));
        assertExportSize(operationLogRepository.count(spec));
        List<OperationLog> rows = operationLogRepository.findAll(spec);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF').append("username,module,operation,method,url,ip,status,executeTime,operationTime\n");
        for (OperationLog row : rows) {
            sb.append(CsvUtils.escape(row.getUsername())).append(',')
                    .append(CsvUtils.escape(row.getModule())).append(',')
                    .append(CsvUtils.escape(row.getOperation())).append(',')
                    .append(CsvUtils.escape(row.getMethod())).append(',')
                    .append(CsvUtils.escape(row.getUrl())).append(',')
                    .append(CsvUtils.escape(row.getIp())).append(',')
                    .append(row.getStatus()).append(',')
                    .append(row.getExecuteTime()).append(',')
                    .append(row.getOperationTime()).append('\n');
        }
        return sb.toString();
    }

    @Transactional
    public void cleanLoginLog() {
        deleteVisible(loginLogRepository::deleteAllInBatch, loginLogRepository::deleteByUserIdIn);
    }

    @Transactional
    public void cleanOpLog() {
        deleteVisible(operationLogRepository::deleteAllInBatch, operationLogRepository::deleteByUserIdIn);
    }

    @Transactional
    public void cleanErrorLog() {
        deleteVisible(errorLogRepository::deleteAllInBatch, errorLogRepository::deleteByUserIdIn);
    }

    private void deleteVisible(Runnable deleteAll, java.util.function.Consumer<Set<Long>> deleteByUsers) {
        Set<Long> ids = dataScopeService.visibleUserIds();
        if (ids == null) {
            deleteAll.run();
            return;
        }
        if (!ids.isEmpty()) {
            deleteByUsers.accept(ids);
        }
    }

    private void assertExportSize(long count) {
        if (count > MAX_EXPORT) {
            throw new BusinessException("导出条数超过 " + MAX_EXPORT + ",请缩小筛选范围");
        }
    }

    private <T> Specification<T> buildSpec(LogQuery query,
                                           String timeField,
                                           TimeLikeMatcher<T> matcher) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(matcher.apply(root, cb, like));
            }
            if (query.getStatus() != null && hasAttribute(root, "status")) {
                predicates.add(cb.equal(root.get("status"), query.getStatus()));
            }
            if (StringUtils.hasText(query.getModule()) && hasAttribute(root, "module")) {
                predicates.add(cb.equal(cb.lower(root.get("module")), query.getModule().toLowerCase()));
            }
            if (query.getBeginTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(timeField), query.getBeginTime()));
            }
            if (query.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(timeField), query.getEndTime()));
            }
            try {
                if (root.getModel().getAttribute("userId") != null) {
                    dataScopeService.applyOwner(root, "userId", cb, predicates);
                }
            } catch (IllegalArgumentException ignored) {
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean hasAttribute(jakarta.persistence.criteria.Root<?> root, String name) {
        try {
            return root.getModel().getAttribute(name) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface TimeLikeMatcher<T> {
        Predicate apply(jakarta.persistence.criteria.Root<T> root,
                        jakarta.persistence.criteria.CriteriaBuilder cb,
                        String like);
    }
}