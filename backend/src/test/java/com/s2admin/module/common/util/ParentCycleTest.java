package com.s2admin.module.common.util;

import com.s2admin.module.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParentCycleTest {

    @Test
    void rejectsDescendantAsParent() {
        Map<Long, Long> tree = Map.of(2L, 1L, 3L, 2L);
        assertThrows(BusinessException.class, () ->
                ParentCycle.assertAcyclic(1L, 3L, tree::get));
    }

    @Test
    void allowsRootOrUnrelated() {
        Map<Long, Long> tree = Map.of(2L, 1L, 3L, 0L);
        assertDoesNotThrow(() -> ParentCycle.assertAcyclic(2L, 3L, id -> tree.getOrDefault(id, 0L)));
        assertDoesNotThrow(() -> ParentCycle.assertAcyclic(2L, 0L, tree::get));
    }
}
