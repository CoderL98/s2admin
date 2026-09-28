package com.s2admin.module.security;

import com.s2admin.module.common.cache.CacheStore;
import com.s2admin.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户权限加载:走 CacheStore(默认内存,可切 Redis),变更时升代淘汰。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionService {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String GEN_KEY = "s2admin:perm:gen";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final SysUserRepository userRepository;
    private final CacheStore cacheStore;

    public Set<String> loadPermissions(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        String key = userKey(userId);
        try {
            String cached = cacheStore.get(key);
            if (cached != null) {
                return decode(cached);
            }
        } catch (Exception e) {
            log.debug("读取权限缓存失败: {}", e.getMessage());
        }
        Set<String> permissions = loadFromDb(userId);
        try {
            cacheStore.set(key, encode(permissions), TTL);
        } catch (Exception e) {
            log.debug("写入权限缓存失败: {}", e.getMessage());
        }
        return permissions;
    }

    public Set<String> loadRoles(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        String key = roleKey(userId);
        try {
            String cached = cacheStore.get(key);
            if (cached != null) {
                return decode(cached);
            }
        } catch (Exception e) {
            log.debug("读取角色缓存失败: {}", e.getMessage());
        }
        Set<String> roles = new HashSet<>(userRepository.findRoleCodesByUserId(userId));
        try {
            cacheStore.set(key, encode(roles), TTL);
        } catch (Exception e) {
            log.debug("写入角色缓存失败: {}", e.getMessage());
        }
        return roles;
    }

    private Set<String> loadFromDb(Long userId) {
        List<String> roles = userRepository.findRoleCodesByUserId(userId);
        if (roles.contains(SUPER_ADMIN)) {
            return Set.of("*");
        }
        return new HashSet<>(userRepository.findPermissionCodesByUserId(userId));
    }

    /** @return null 表示用户不存在或已删除 */
    public Integer accountStatus(Long userId) {
        if (userId == null) {
            return null;
        }
        String key = statusKey(userId);
        try {
            String cached = cacheStore.get(key);
            if ("gone".equals(cached)) {
                return null;
            }
            if (cached != null) {
                return Integer.valueOf(cached);
            }
        } catch (Exception ignored) {
        }
        Integer status = userRepository.findById(userId).map(u -> u.getStatus() == null ? 0 : u.getStatus()).orElse(null);
        try {
            cacheStore.set(key, status == null ? "gone" : String.valueOf(status), TTL);
        } catch (Exception ignored) {
        }
        return status;
    }

    public boolean mustChangePassword(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = pwdKey(userId);
        try {
            String cached = cacheStore.get(key);
            if (cached != null) {
                return "1".equals(cached);
            }
        } catch (Exception ignored) {
        }
        boolean must = userRepository.findById(userId)
                .map(u -> Integer.valueOf(1).equals(u.getPwdReset()))
                .orElse(false);
        try {
            cacheStore.set(key, must ? "1" : "0", TTL);
        } catch (Exception ignored) {
        }
        return must;
    }

    /** @return null 表示平台账号或用户不存在 */
    public Long tenantId(Long userId) {
        if (userId == null) {
            return null;
        }
        String key = tenantKey(userId);
        try {
            String cached = cacheStore.get(key);
            if ("none".equals(cached)) {
                return null;
            }
            if (cached != null) {
                return Long.valueOf(cached);
            }
        } catch (Exception ignored) {
        }
        Long tenantId = userRepository.findById(userId).map(u -> u.getTenantId()).orElse(null);
        try {
            cacheStore.set(key, tenantId == null ? "none" : String.valueOf(tenantId), TTL);
        } catch (Exception ignored) {
        }
        return tenantId;
    }

    public void evict(Long userId) {
        if (userId != null) {
            try {
                cacheStore.delete(userKey(userId));
                cacheStore.delete(roleKey(userId));
                cacheStore.delete(pwdKey(userId));
                cacheStore.delete(statusKey(userId));
                cacheStore.delete(tenantKey(userId));
            } catch (Exception ignored) {
            }
        }
    }

    public void clearAll() {
        try {
            cacheStore.increment(GEN_KEY);
            cacheStore.expire(GEN_KEY, Duration.ofDays(30));
        } catch (Exception e) {
            log.warn("权限缓存升代失败: {}", e.getMessage());
        }
    }

    private long generation() {
        try {
            String g = cacheStore.get(GEN_KEY);
            if (g != null) {
                return Long.parseLong(g);
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private String userKey(Long userId) {
        return "s2admin:perm:" + generation() + ":" + userId;
    }

    private String roleKey(Long userId) {
        return "s2admin:roles:" + generation() + ":" + userId;
    }

    private String pwdKey(Long userId) {
        return "s2admin:pwdreset:" + generation() + ":" + userId;
    }

    private String statusKey(Long userId) {
        return "s2admin:status:" + generation() + ":" + userId;
    }

    private String tenantKey(Long userId) {
        return "s2admin:tenant:" + generation() + ":" + userId;
    }

    private String encode(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "";
        }
        return String.join("\n", permissions);
    }

    private Set<String> decode(String cached) {
        if (cached.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(List.of(cached.split("\n")));
    }
}
