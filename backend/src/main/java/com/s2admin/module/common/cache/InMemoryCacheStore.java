package com.s2admin.module.common.cache;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 进程内缓存实现:ConcurrentHashMap + 后台线程定期清扫过期项,读写时惰性过期。
 * 默认启用;配置 s2admin.cache.type=redis 时由 RedisCacheStore 替代。
 * 注意:内存缓存进程重启即失效,不适用于多实例部署。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "s2admin.cache", name = "type", havingValue = "memory", matchIfMissing = true)
public class InMemoryCacheStore implements CacheStore {

    /** increment 未显式设置 TTL 时的兜底(调用方随后会 expire 重置) */
    private static final long DEFAULT_TTL_MILLIS = Duration.ofHours(1).toMillis();
    private static final int MAX_ENTRIES = 20_000;
    private static final long SWEEP_INTERVAL_SECONDS = 30;

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cache-store-sweeper");
        t.setDaemon(true);
        return t;
    });

    private record Entry(String value, long expireAtMillis) {
        boolean expired(long now) {
            return now >= expireAtMillis;
        }
    }

    @PostConstruct
    void start() {
        sweeper.scheduleWithFixedDelay(this::sweep, SWEEP_INTERVAL_SECONDS, SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        sweeper.shutdownNow();
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        if (map.size() >= MAX_ENTRIES && !map.containsKey(key)) {
            log.warn("内存缓存条目数已达上限 {},拒绝写入新 key: {}", MAX_ENTRIES, key);
            return;
        }
        map.put(key, new Entry(value, now() + ttlMillis(ttl)));
    }

    @Override
    public String get(String key) {
        Entry entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expired(now())) {
            map.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    @Override
    public String getAndDelete(String key) {
        Entry entry = map.get(key);
        if (entry == null) {
            return null;
        }
        map.remove(key, entry);
        return entry.expired(now()) ? null : entry.value();
    }

    @Override
    public boolean hasKey(String key) {
        return get(key) != null;
    }

    @Override
    public void delete(String key) {
        map.remove(key);
    }

    @Override
    public void expire(String key, Duration ttl) {
        map.computeIfPresent(key, (k, e) -> new Entry(e.value(), now() + ttlMillis(ttl)));
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        long now = now();
        Entry[] created = new Entry[1];
        map.compute(key, (k, existing) -> {
            if (existing != null && !existing.expired(now)) {
                return existing;
            }
            created[0] = new Entry(value, now + ttlMillis(ttl));
            return created[0];
        });
        return created[0] != null;
    }

    @Override
    public long increment(String key) {
        Entry updated = map.compute(key, (k, e) -> {
            boolean fresh = e == null || e.expired(now());
            long next = fresh ? 1 : parseLong(e.value()) + 1;
            long expireAt = fresh ? now() + DEFAULT_TTL_MILLIS : e.expireAtMillis();
            return new Entry(String.valueOf(next), expireAt);
        });
        return parseLong(updated.value());
    }

    private void sweep() {
        long now = now();
        map.entrySet().removeIf(e -> e.getValue().expired(now));
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static long ttlMillis(Duration ttl) {
        return ttl == null ? DEFAULT_TTL_MILLIS : ttl.toMillis();
    }
}
