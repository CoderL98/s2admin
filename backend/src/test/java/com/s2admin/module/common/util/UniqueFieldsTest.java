package com.s2admin.module.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniqueFieldsTest {

    @Test
    void appendSuffix() {
        assertEquals("admin__del_1", UniqueFields.tombstone("admin", 1L, 50));
    }

    @Test
    void truncateToMaxLen() {
        String value = UniqueFields.tombstone("13800138000", 999999L, 20);
        assertTrue(value.endsWith("__del_999999"));
        assertEquals(20, value.length());
    }
}
