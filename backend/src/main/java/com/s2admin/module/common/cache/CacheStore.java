package com.s2admin.module.common.cache;

import java.time.Duration;

/**
 * 键值缓存抽象(带 TTL)。
 * 默认使用进程内内存实现;配置 s2admin.cache.type=redis 切换为 Redis。
 * 覆盖验证码 / JWT 黑名单 / 登录失败计数 / 配置与字典缓存等场景。
 */
public interface CacheStore {

    void set(String key, String value, Duration ttl);

    String get(String key);

    /** 读取并删除(验证码一次性校验语义) */
    String getAndDelete(String key);

    boolean hasKey(String key);

    void delete(String key);

    /** 重置剩余存活时间 */
    void expire(String key, Duration ttl);

    /** 自增,不存在时初始化为 1(登录失败计数) */
    long increment(String key);

    /** 仅当 key 不存在(或已过期)时写入,成功返回 true。用于 Refresh Token 一次性消费。 */
    boolean setIfAbsent(String key, String value, Duration ttl);
}
