package com.s2admin.module.common.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XlsxUtilsTest {

    @Test
    void roundTripInlineStrings() {
        byte[] bytes = XlsxUtils.write(List.of("username", "status"), List.of(List.of("ada", "0")));
        List<List<String>> rows = XlsxUtils.read(new ByteArrayInputStream(bytes));
        assertEquals(List.of("username", "status"), rows.get(0));
        assertEquals(List.of("ada", "0"), rows.get(1));
    }

    @Test
    void readsSharedStringsAndSkipsEmptyCells() throws Exception {
        String shared = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>username</t></si>
                  <si><t>ada</t></si>
                </sst>
                """;
        String sheet = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                  <row r="1">
                    <c r="A1" t="s"><v>0</v></c>
                    <c r="C1" t="inlineStr"><is><t>status</t></is></c>
                  </row>
                  <row r="2">
                    <c r="A2" t="s"><v>1</v></c>
                    <c r="B2"><v>13800138000</v></c>
                    <c r="C2"><v>0</v></c>
                  </row>
                </sheetData></worksheet>
                """;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            put(zip, "xl/sharedStrings.xml", shared);
            put(zip, "xl/worksheets/sheet1.xml", sheet);
        }
        List<List<String>> rows = XlsxUtils.read(new ByteArrayInputStream(bos.toByteArray()));
        assertEquals(List.of("username", "", "status"), rows.get(0));
        assertEquals(List.of("ada", "13800138000", "0"), rows.get(1));
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
