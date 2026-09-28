package com.s2admin.module.security;

import com.s2admin.module.common.ResultCode;
import com.s2admin.module.common.cache.CacheStore;
import com.s2admin.module.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于缓存的简易限流:计数窗口 + 锁定标记。
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final CacheStore cacheStore;

    public void assertAllowed(String key, int max, Duration window, String message) {
        try {
            long count = cacheStore.increment(key);
            if (count == 1) {
                cacheStore.expire(key, window);
            }
            if (count > max) {
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, message);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
            // 缓存不可用时不阻断主流程
        }
    }

    public void assertNotLocked(String key, String message) {
        try {
            if (cacheStore.hasKey(key)) {
                throw new BusinessException(ResultCode.FORBIDDEN, message);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
        }
    }

    public long increment(String key, Duration window) {
        try {
            long count = cacheStore.increment(key);
            if (count == 1) {
                cacheStore.expire(key, window);
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    public void lock(String key, Duration ttl) {
        try {
            cacheStore.set(key, "1", ttl);
        } catch (Exception ignored) {
        }
    }

    public void unlock(String key) {
        try {
            cacheStore.delete(key);
        } catch (Exception ignored) {
        }
    }
}
