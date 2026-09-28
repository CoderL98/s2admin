package com.s2admin.module.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageQueryTest {

    @Test
    void clampsHugePageSize() {
        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(99_999);
        Pageable pageable = query.toPageable();
        assertEquals(PageQuery.MAX_PAGE_SIZE, pageable.getPageSize());
        assertEquals(0, pageable.getPageNumber());
    }

    @Test
    void defaultsInvalidPage() {
        PageQuery query = new PageQuery();
        query.setPageNum(0);
        query.setPageSize(-3);
        Pageable pageable = query.toPageable();
        assertEquals(1, query.resolvedPageNum());
        assertEquals(1, pageable.getPageSize());
    }

    @Test
    void rejectsSortInjection() {
        PageQuery query = new PageQuery();
        query.setOrderBy("id;drop table");
        Pageable pageable = query.toPageable();
        assertEquals(false, pageable.getSort().isSorted());
    }
}
