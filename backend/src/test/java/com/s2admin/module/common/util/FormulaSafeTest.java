package com.s2admin.module.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormulaSafeTest {

    @Test
    void prefixesFormulaStarters() {
        assertEquals("'=1+1", FormulaSafe.neutralize("=1+1"));
        assertEquals("'+cmd", FormulaSafe.neutralize("+cmd"));
        assertEquals("'-1", FormulaSafe.neutralize("-1"));
        assertEquals("'@sum", FormulaSafe.neutralize("@sum"));
    }

    @Test
    void leavesNormalText() {
        assertEquals("admin", FormulaSafe.neutralize("admin"));
        assertEquals("", FormulaSafe.neutralize(""));
        assertEquals("", FormulaSafe.neutralize(null));
    }
}
