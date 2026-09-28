package com.s2admin.module.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT Token 提供者
 * 生成 / 解析 / 校验 AccessToken 与 RefreshToken
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_REMEMBER = "rm";
    public static final String CLAIM_SID = "sid";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessExpiration;
    private final long refreshExpiration;
    private final long rememberRefreshExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration:900000}") long accessExpiration,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshExpiration,
            @Value("${jwt.remember-me-refresh-expiration:2592000000}") long rememberRefreshExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
        this.rememberRefreshExpiration = rememberRefreshExpiration;
    }

    public long getAccessExpiration() {
        return accessExpiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public String generateAccessToken(Long userId, String username, Collection<String> roles) {
        return generateAccessToken(userId, username, roles, UUID.randomUUID().toString());
    }

    public String generateAccessToken(Long userId, String username, Collection<String> roles, String sid) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_ROLES, roles == null ? List.of() : roles)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_SID, sid == null || sid.isBlank() ? UUID.randomUUID().toString() : sid)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiration))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId, String username) {
        return generateRefreshToken(userId, username, false);
    }

    public String generateRefreshToken(Long userId, String username, boolean rememberMe) {
        return generateRefreshToken(userId, username, rememberMe, UUID.randomUUID().toString());
    }

    public String generateRefreshToken(Long userId, String username, boolean rememberMe, String sid) {
        long ttl = rememberMe ? rememberRefreshExpiration : refreshExpiration;
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_REMEMBER, rememberMe ? "1" : "0")
                .claim(CLAIM_SID, sid == null || sid.isBlank() ? UUID.randomUUID().toString() : sid)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 Token,非法或过期抛出 JwtException
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(Claims claims) {
        Number uid = claims.get(CLAIM_UID, Number.class);
        return uid == null ? null : uid.longValue();
    }

    @SuppressWarnings("unchecked")
    public Set<String> getRoles(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        if (roles instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of();
    }

    public String getType(Claims claims) {
        return claims.get(CLAIM_TYPE, String.class);
    }

    public boolean isRememberMe(Claims claims) {
        Object v = claims.get(CLAIM_REMEMBER);
        return "1".equals(String.valueOf(v)) || Boolean.TRUE.equals(v);
    }

    public String getSid(Claims claims) {
        Object sid = claims.get(CLAIM_SID);
        if (sid == null) {
            return null;
        }
        String value = String.valueOf(sid);
        return value.isBlank() ? null : value;
    }
}
