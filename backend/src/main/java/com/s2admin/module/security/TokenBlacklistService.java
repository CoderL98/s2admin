package com.s2admin.module.security;

import com.s2admin.module.common.cache.CacheStore;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT 黑名单:缓存 + 进程内双写。缓存故障时仍可在本节点拒绝已登出 Token。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "s2admin:jwt:blacklist:";
    private static final String USER_INVALID_PREFIX = "s2admin:jwt:invalid-before:";

    private final CacheStore cacheStore;
    private final JwtTokenProvider tokenProvider;
    private final SessionService sessionService;
    private final ConcurrentHashMap<String, Long> localJtiUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> localUserInvalidBefore = new ConcurrentHashMap<>();

    public void blacklist(String token) {
        try {
            Claims claims = tokenProvider.parseToken(token);
            String jti = claims.getId();
            if (jti == null || claims.getExpiration() == null) {
                return;
            }
            long exp = claims.getExpiration().getTime();
            long ttl = exp - System.currentTimeMillis();
            if (ttl > 0) {
                localJtiUntil.put(jti, exp);
                cacheStore.set(BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(ttl));
            }
        } catch (Exception e) {
            log.warn("Token 加入黑名单失败: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            Claims claims = tokenProvider.parseToken(token);
            String jti = claims.getId();
            long now = System.currentTimeMillis();
            if (jti != null) {
                Long localExp = localJtiUntil.get(jti);
                if (localExp != null) {
                    if (now < localExp) {
                        return true;
                    }
                    localJtiUntil.remove(jti, localExp);
                }
                try {
                    if (cacheStore.hasKey(BLACKLIST_PREFIX + jti)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("读取 Token 黑名单缓存失败: {}", e.getMessage());
                }
            }
            Long userId = tokenProvider.getUserId(claims);
            return isUserInvalidated(userId, claims.getIssuedAt() == null ? 0L : claims.getIssuedAt().getTime());
        } catch (Exception e) {
            return false;
        }
    }

    public void invalidateUser(Long userId) {
        if (userId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        localUserInvalidBefore.put(userId, now);
        sessionService.clear(userId);
        try {
            cacheStore.set(USER_INVALID_PREFIX + userId, String.valueOf(now), Duration.ofDays(31));
        } catch (Exception e) {
            log.warn("作废用户 Token 失败: {}", e.getMessage());
        }
    }

    public boolean isUserInvalidated(Long userId, long issuedAtMillis) {
        if (userId == null) {
            return false;
        }
        Long local = localUserInvalidBefore.get(userId);
        if (local != null && issuedAtMillis < local) {
            return true;
        }
        try {
            String value = cacheStore.get(USER_INVALID_PREFIX + userId);
            if (value == null) {
                return false;
            }
            return issuedAtMillis < Long.parseLong(value);
        } catch (Exception e) {
            log.warn("读取用户 Token 作废标记失败: {}", e.getMessage());
            return local != null && issuedAtMillis < local;
        }
    }
}
