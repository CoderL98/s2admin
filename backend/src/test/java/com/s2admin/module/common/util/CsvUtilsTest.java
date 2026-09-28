package com.s2admin.module.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvUtilsTest {

    @Test
    void parsePlain() {
        assertEquals(List.of("a", "b", "c"), CsvUtils.parseLine("a,b,c"));
    }

    @Test
    void parseQuotedComma() {
        assertEquals(List.of("研发,测试", "ok"), CsvUtils.parseLine("\"研发,测试\",ok"));
    }

    @Test
    void parseEscapedQuote() {
        assertEquals(List.of("say \"hi\"", "x"), CsvUtils.parseLine("\"say \"\"hi\"\"\",x"));
    }

    @Test
    void escapeRoundTrip() {
        String raw = "a,b\"c";
        assertEquals(List.of(raw), CsvUtils.parseLine(CsvUtils.escape(raw)));
    }

    @Test
    void escapeNeutralizesFormula() {
        String escaped = CsvUtils.escape("=cmd|'/C calc'!A0");
        assertEquals("\"'=cmd|'/C calc'!A0\"", escaped);
    }
}
