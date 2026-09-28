package com.s2admin.module.common.util;

/**
 * 防止 CSV/Excel 把用户可控文本当公式执行。
 */
public final class FormulaSafe {

    private FormulaSafe() {
    }

    public static String neutralize(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        char c = value.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + value;
        }
        return value;
    }
}
