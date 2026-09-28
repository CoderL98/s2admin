package com.s2admin.module.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityPermissionServiceTest {

    private final SecurityPermissionService service = new SecurityPermissionService();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminBypassesSpecificCodes() {
        authenticate(Set.of("SUPER_ADMIN"), Set.of("*"));
        assertTrue(service.hasPermission("system:user:view"));
        assertTrue(service.hasPermission("tools:dict:edit"));
    }

    @Test
    void wildcardPermissionGrantsAll() {
        authenticate(Set.of("ADMIN"), Set.of("*"));
        assertTrue(service.hasPermission("system:role:assign"));
    }

    @Test
    void exactPermissionRequiredOtherwise() {
        authenticate(Set.of("USER"), Set.of("system:user:view"));
        assertTrue(service.hasPermission("system:user:view"));
        assertFalse(service.hasPermission("system:user:delete"));
    }

    @Test
    void anonymousDenied() {
        assertFalse(service.hasPermission("system:user:view"));
    }

    @Test
    void blankPermissionDenied() {
        authenticate(Set.of("USER"), Set.of("system:user:view"));
        assertFalse(service.hasPermission(null));
        assertFalse(service.hasPermission(""));
        assertFalse(service.hasPermission("   "));
    }

    private void authenticate(Set<String> roles, Set<String> permissions) {
        LoginUser user = new LoginUser(1L, "tester", "pwd", roles, permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
