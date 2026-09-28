package com.s2admin.module.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 分页查询基类
 * 所有分页查询继承此类
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 200, message = "pageSize 最大为 200")
    private Integer pageSize = 10;

    private String orderBy;

    private String sortDirection = "asc";

    /** 可选关键字。具体查询是否使用由各 Service 决定。 */
    private String keyword;

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 200;

    public int resolvedPageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int resolvedPageSize() {
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public long getOffset() {
        return (long) (resolvedPageNum() - 1) * resolvedPageSize();
    }

    /**
     * 构建 Pageable;orderBy 仅允许字母/数字/下划线,防止排序注入
     * 若需要按关联字段排序,请在各 Service 自行处理
     * pageSize 即使未走 Bean Validation 也会钳制到 1..200
     */
    public Pageable toPageable() {
        int num = resolvedPageNum();
        int size = resolvedPageSize();
        if (orderBy != null && orderBy.matches("^[A-Za-z0-9_]+$")
                && !orderBy.equalsIgnoreCase("password")
                && !orderBy.equalsIgnoreCase("pwdReset")
                && !orderBy.equalsIgnoreCase("pwd_reset")) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection)
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            return PageRequest.of(num - 1, size, Sort.by(direction, orderBy));
        }
        return PageRequest.of(num - 1, size);
    }
}
