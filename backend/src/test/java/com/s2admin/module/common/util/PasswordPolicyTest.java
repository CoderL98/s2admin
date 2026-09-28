package com.s2admin.module.common.util;

import com.s2admin.module.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void acceptStrongPassword() {
        assertTrue(PasswordPolicy.isStrong("Admin@123"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("Admin@123"));
    }

    @Test
    void rejectWeakPassword() {
        assertFalse(PasswordPolicy.isStrong("123456"));
        assertFalse(PasswordPolicy.isStrong("admin123"));
        assertFalse(PasswordPolicy.isStrong("Admin123"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("123456"));
    }

    @Test
    void randomStrongMeetsPolicy() {
        for (int i = 0; i < 20; i++) {
            String pwd = PasswordPolicy.randomStrong();
            assertTrue(PasswordPolicy.isStrong(pwd), pwd);
        }
    }
}
