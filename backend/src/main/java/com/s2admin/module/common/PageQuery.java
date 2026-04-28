package com.s2admin.module.common;

import lombok.Data;

/**
 * 分页查询基类
 * 所有分页查询继承此类
 */
@Data
public class PageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String orderBy;

    private String sortDirection = "desc";

    public long getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
