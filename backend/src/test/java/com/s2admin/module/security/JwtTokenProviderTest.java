package com.s2admin.module.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(
                "s2admin-test-secret-key-minimum-32-characters",
                60_000L,
                120_000L,
                240_000L
        );
    }

    @Test
    void generateAndParseAccessToken() {
        String token = provider.generateAccessToken(1L, "admin", Set.of("SUPER_ADMIN"));
        Claims claims = provider.parseToken(token);
        assertEquals("admin", claims.getSubject());
        assertEquals(1L, provider.getUserId(claims));
        assertEquals(JwtTokenProvider.TYPE_ACCESS, provider.getType(claims));
        assertTrue(provider.getRoles(claims).contains("SUPER_ADMIN"));
        assertNotNull(claims.getId());
    }

    @Test
    void refreshTokenTypeDiffersFromAccess() {
        String refresh = provider.generateRefreshToken(1L, "admin");
        Claims claims = provider.parseToken(refresh);
        assertEquals(JwtTokenProvider.TYPE_REFRESH, provider.getType(claims));
        assertTrue(provider.getRoles(claims).isEmpty());
    }

    @Test
    void rememberMeRefreshClaimSurvivesParse() {
        String remember = provider.generateRefreshToken(1L, "admin", true);
        String normal = provider.generateRefreshToken(1L, "admin", false);
        assertTrue(provider.isRememberMe(provider.parseToken(remember)));
        assertFalse(provider.isRememberMe(provider.parseToken(normal)));
    }

    @Test
    void sessionIdSharedByAccessAndRefresh() {
        String sid = "sess-1";
        String access = provider.generateAccessToken(1L, "admin", Set.of("USER"), sid);
        String refresh = provider.generateRefreshToken(1L, "admin", false, sid);
        assertEquals(sid, provider.getSid(provider.parseToken(access)));
        assertEquals(sid, provider.getSid(provider.parseToken(refresh)));
        assertNotEquals(provider.parseToken(access).getId(), provider.parseToken(refresh).getId());
    }

    @Test
    void invalidTokenRejected() {
        assertThrows(JwtException.class, () -> provider.parseToken("not-a-jwt"));
        assertFalse(provider.isValid("not-a-jwt"));
    }
}
