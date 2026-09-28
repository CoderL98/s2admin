package com.s2admin.module.common.util;

import com.s2admin.module.common.exception.BusinessException;

import java.util.function.Function;

/**
 * 树节点改父级时检测成环。
 */
public final class ParentCycle {

    private ParentCycle() {
    }

    /**
     * @param id       当前节点,新增时可为 null
     * @param parentId 拟设置的父级
     * @param parentOf 给定 id 返回其 parentId;节点不存在时应抛业务异常
     */
    public static void assertAcyclic(Long id, Long parentId, Function<Long, Long> parentOf) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        Long cursor = parentId;
        for (int i = 0; i < 64; i++) {
            if (cursor == null || cursor == 0L) {
                return;
            }
            if (id != null && id.equals(cursor)) {
                throw new BusinessException("不能将子级设为父级");
            }
            cursor = parentOf.apply(cursor);
        }
        throw new BusinessException("层级过深或存在环");
    }
}
