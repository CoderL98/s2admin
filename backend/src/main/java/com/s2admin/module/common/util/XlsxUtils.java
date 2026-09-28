package com.s2admin.module.common.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 最小 xlsx 读写(不引入 POI):适合固定列表头的导入导出。
 */
public final class XlsxUtils {

    private XlsxUtils() {
    }

    public static byte[] write(List<String> headers, List<List<String>> rows) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bos)) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", rels());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows));
            zip.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("写入 xlsx 失败", e);
        }
    }

    public static List<List<String>> read(InputStream in) {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            byte[] sheet = null;
            byte[] shared = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    sheet = zip.readAllBytes();
                } else if ("xl/sharedStrings.xml".equals(entry.getName())) {
                    shared = zip.readAllBytes();
                }
            }
            if (sheet == null) {
                return List.of();
            }
            List<String> strings = shared == null
                    ? List.of()
                    : parseSharedStrings(new String(shared, StandardCharsets.UTF_8));
            return parseSheet(new String(sheet, StandardCharsets.UTF_8), strings);
        } catch (Exception e) {
            throw new IllegalStateException("读取 xlsx 失败", e);
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package-relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """;
    }

    private static String rels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private static String workbookRels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """;
    }

    private static String workbook() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="users" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """;
    }

    private static String sheet(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                """);
        writeRow(sb, 1, headers);
        int r = 2;
        for (List<String> row : rows) {
            writeRow(sb, r++, row);
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void writeRow(StringBuilder sb, int rowNum, List<String> cols) {
        sb.append("<row r=\"").append(rowNum).append("\">");
        for (int i = 0; i < cols.size(); i++) {
            String ref = colName(i) + rowNum;
            String v = FormulaSafe.neutralize(cols.get(i) == null ? "" : cols.get(i));
            sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(v)).append("</t></is></c>");
        }
        sb.append("</row>");
    }

    private static List<String> parseSharedStrings(String xml) {
        List<String> strings = new ArrayList<>();
        int idx = 0;
        while (true) {
            int start = xml.indexOf("<si", idx);
            if (start < 0) {
                break;
            }
            int end = xml.indexOf("</si>", start);
            if (end < 0) {
                break;
            }
            String item = xml.substring(start, end);
            StringBuilder text = new StringBuilder();
            int t = 0;
            while (true) {
                int ts = item.indexOf("<t", t);
                if (ts < 0) {
                    break;
                }
                int gt = item.indexOf('>', ts);
                int te = item.indexOf("</t>", gt);
                if (gt < 0 || te < 0) {
                    break;
                }
                text.append(unescape(item.substring(gt + 1, te)));
                t = te + 4;
            }
            strings.add(text.toString());
            idx = end + 5;
        }
        return strings;
    }

    private static List<List<String>> parseSheet(String xml, List<String> sharedStrings) {
        List<List<String>> rows = new ArrayList<>();
        int rowIdx = 0;
        while (true) {
            int rs = xml.indexOf("<row", rowIdx);
            if (rs < 0) {
                break;
            }
            int re = xml.indexOf("</row>", rs);
            if (re < 0) {
                break;
            }
            String rowXml = xml.substring(rs, re);
            int width = 0;
            List<int[]> cells = new ArrayList<>();
            List<String> values = new ArrayList<>();
            int cidx = 0;
            while (true) {
                int cs = rowXml.indexOf("<c ", cidx);
                int cs2 = rowXml.indexOf("<c>", cidx);
                int start = cs < 0 ? cs2 : (cs2 < 0 ? cs : Math.min(cs, cs2));
                if (start < 0) {
                    break;
                }
                int tagEnd = rowXml.indexOf('>', start);
                if (tagEnd < 0) {
                    break;
                }
                String open = rowXml.substring(start, tagEnd + 1);
                String body;
                int next;
                if (open.endsWith("/>")) {
                    body = "";
                    next = tagEnd + 1;
                } else {
                    int close = rowXml.indexOf("</c>", tagEnd);
                    if (close < 0) {
                        break;
                    }
                    body = rowXml.substring(tagEnd + 1, close);
                    next = close + 4;
                }
                int col = columnIndex(open);
                cells.add(new int[]{col, values.size()});
                values.add(cellText(open, body, sharedStrings));
                width = Math.max(width, col + 1);
                cidx = next;
            }
            if (!values.isEmpty()) {
                List<String> cols = new ArrayList<>();
                for (int i = 0; i < width; i++) {
                    cols.add("");
                }
                for (int i = 0; i < cells.size(); i++) {
                    int col = cells.get(i)[0];
                    if (col >= 0 && col < cols.size()) {
                        cols.set(col, values.get(cells.get(i)[1]));
                    }
                }
                rows.add(cols);
            }
            rowIdx = re + 6;
        }
        return rows;
    }

    private static String cellText(String openTag, String body, List<String> sharedStrings) {
        String type = attr(openTag, "t");
        if ("inlineStr".equals(type)) {
            return textNodes(body);
        }
        String raw = valueNode(body);
        if ("s".equals(type)) {
            try {
                int index = Integer.parseInt(raw.trim());
                return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        return raw;
    }

    private static String textNodes(String xml) {
        StringBuilder text = new StringBuilder();
        int t = 0;
        while (true) {
            int ts = xml.indexOf("<t", t);
            if (ts < 0) {
                break;
            }
            int gt = xml.indexOf('>', ts);
            int te = xml.indexOf("</t>", gt);
            if (gt < 0 || te < 0) {
                break;
            }
            text.append(unescape(xml.substring(gt + 1, te)));
            t = te + 4;
        }
        return text.toString();
    }

    private static String valueNode(String xml) {
        int vs = xml.indexOf("<v>");
        if (vs < 0) {
            return textNodes(xml);
        }
        int ve = xml.indexOf("</v>", vs);
        if (ve < 0) {
            return "";
        }
        return unescape(xml.substring(vs + 3, ve));
    }

    private static String attr(String openTag, String name) {
        String key = name + "=\"";
        int i = openTag.indexOf(key);
        if (i < 0) {
            return "";
        }
        int start = i + key.length();
        int end = openTag.indexOf('"', start);
        return end < 0 ? "" : openTag.substring(start, end);
    }

    private static int columnIndex(String openTag) {
        String ref = attr(openTag, "r");
        int n = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                n = n * 26 + (ch - 'A' + 1);
            } else if (ch >= 'a' && ch <= 'z') {
                n = n * 26 + (ch - 'a' + 1);
            } else {
                break;
            }
        }
        return Math.max(n - 1, 0);
    }

    private static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
