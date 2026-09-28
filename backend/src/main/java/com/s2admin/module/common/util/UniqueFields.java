package com.s2admin.module.common.util;

/**
 * 软删除时改写唯一字段,避免 deleted=1 仍占用 UNIQUE 约束。
 */
public final class UniqueFields {

    private UniqueFields() {
    }

    public static String tombstone(String value, Long id, int maxLen) {
        String suffix = "__del_" + (id == null ? "0" : id);
        if (value == null || value.isBlank()) {
            return truncate(suffix, maxLen);
        }
        if (value.length() + suffix.length() <= maxLen) {
            return value + suffix;
        }
        int keep = Math.max(0, maxLen - suffix.length());
        return value.substring(0, keep) + suffix;
    }

    private static String truncate(String value, int maxLen) {
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
