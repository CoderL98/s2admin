package com.s2admin.module.workflow;

import com.s2admin.module.common.exception.BusinessException;
import org.springframework.util.StringUtils;

/**
 * 条件节点表达式。目前支持 contains:文本 与 not:contains:文本,匹配标题加正文。
 */
public final class ConditionExpr {

    private ConditionExpr() {
    }

    public static void validate(String expr) {
        if (!StringUtils.hasText(expr) || expr.trim().length() > 200) {
            throw new BusinessException("条件需为 contains:文本 或 not:contains:文本");
        }
        String body = stripNot(expr.trim());
        if (!body.regionMatches(true, 0, "contains:", 0, 9) || body.substring(9).isBlank()) {
            throw new BusinessException("条件需为 contains:文本 或 not:contains:文本");
        }
    }

    public static boolean matches(String expr, String title, String content) {
        if (!StringUtils.hasText(expr)) {
            return true;
        }
        String raw = expr.trim();
        boolean negate = raw.regionMatches(true, 0, "not:", 0, 4);
        String body = negate ? raw.substring(4).trim() : raw;
        if (!body.regionMatches(true, 0, "contains:", 0, 9)) {
            throw new BusinessException("无法识别的条件: " + expr);
        }
        String needle = body.substring(9).trim().toLowerCase();
        String hay = ((title == null ? "" : title) + "\n" + (content == null ? "" : content)).toLowerCase();
        boolean yes = !needle.isEmpty() && hay.contains(needle);
        return negate != yes;
    }

    private static String stripNot(String expr) {
        if (expr.regionMatches(true, 0, "not:", 0, 4)) {
            return expr.substring(4).trim();
        }
        return expr;
    }
}
