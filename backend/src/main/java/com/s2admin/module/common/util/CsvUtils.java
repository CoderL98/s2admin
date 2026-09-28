package com.s2admin.module.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 风格 CSV 行解析/转义(支持引号与单元格内逗号)。
 */
public final class CsvUtils {

    private CsvUtils() {
    }

    public static List<String> parseLine(String line) {
        List<String> cols = new ArrayList<>();
        if (line == null) {
            return cols;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols;
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String v = FormulaSafe.neutralize(value);
        boolean formula = !v.equals(value);
        if (formula || v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
