package com.s2admin.module.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.s2admin.module.common.cache.CacheStore;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.system.service.ConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多端登录控制:sys.account.maxSessions=0 不限制,1 单端,3 最多三端。
 * 设备列表始终写入,踢人走 kicked 集合,这样不限制端数时也能下线指定设备。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    public record Device(String sid, long iat, String ip, String ua) {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SessionState {
        private List<Device> devices = new ArrayList<>();
        private List<String> kicked = new ArrayList<>();
    }

    private static final String KEY = "s2admin:sess:";
    private static final String ONLINE_KEY = "s2admin:sess:online";
    private static final String KICKED_KEY = "s2admin:sess:kicked:";
    private static final Duration TTL = Duration.ofDays(31);
    private static final Duration KICKED_TTL = Duration.ofDays(31);
    /** 不限制端数时的安全上限。超出则踢掉最旧设备,避免静默丢出列表后令牌仍有效。 */
    private static final int ACTIVE_CAP = 200;
    private static final int KICKED_CAP = 50;

    private final CacheStore cacheStore;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();
    private final Object onlineLock = new Object();

    public int maxSessions() {
        return Math.max(0, configService.getInt("sys.account.maxSessions", 0));
    }

    public void register(Long userId, String sid, String ip, String ua) {
        if (userId == null || !StringUtils.hasText(sid)) {
            return;
        }
        synchronized (lock(userId)) {
            if (isKicked(userId, sid)) {
                throw BusinessException.unauthorized("登录已在其他设备下线,请重新登录");
            }
            SessionState state = load(userId);
            if (state.getKicked().contains(sid)) {
                throw BusinessException.unauthorized("登录已在其他设备下线,请重新登录");
            }
            state.getDevices().removeIf(d -> sid.equals(d.sid()));
            state.getDevices().add(0, new Device(sid, System.currentTimeMillis(),
                    ip == null ? "" : ip, ua == null ? "" : trimUa(ua)));
            int max = maxSessions();
            int cap = max > 0 ? max : ACTIVE_CAP;
            while (state.getDevices().size() > cap) {
                Device evicted = state.getDevices().remove(state.getDevices().size() - 1);
                if (evicted != null) {
                    addKicked(userId, state, evicted.sid());
                }
            }
            save(userId, state);
            syncOnline(userId, state);
        }
    }

    public boolean isActive(Long userId, String sid) {
        if (userId == null) {
            return false;
        }
        synchronized (lock(userId)) {
            if (isKicked(userId, sid)) {
                return false;
            }
            SessionState state = load(userId);
            if (StringUtils.hasText(sid) && state.getKicked().contains(sid)) {
                return false;
            }
            int max = maxSessions();
            if (max <= 0) {
                if (!StringUtils.hasText(sid) || state.getDevices().isEmpty()) {
                    return true;
                }
                return state.getDevices().stream().anyMatch(d -> sid.equals(d.sid()));
            }
            if (!StringUtils.hasText(sid)) {
                return false;
            }
            if (state.getDevices().isEmpty()) {
                return true;
            }
            return state.getDevices().stream().anyMatch(d -> sid.equals(d.sid()));
        }
    }

    public void remove(Long userId, String sid) {
        if (userId == null || !StringUtils.hasText(sid)) {
            return;
        }
        synchronized (lock(userId)) {
            SessionState state = load(userId);
            state.getDevices().removeIf(d -> sid.equals(d.sid()));
            addKicked(userId, state, sid);
            save(userId, state);
            syncOnline(userId, state);
        }
    }

    public void keepOnly(Long userId, String sid) {
        if (userId == null) {
            return;
        }
        synchronized (lock(userId)) {
            SessionState state = load(userId);
            Iterator<Device> it = state.getDevices().iterator();
            Device keep = null;
            while (it.hasNext()) {
                Device d = it.next();
                if (sid != null && sid.equals(d.sid())) {
                    keep = d;
                } else {
                    addKicked(userId, state, d.sid());
                    it.remove();
                }
            }
            if (keep == null && StringUtils.hasText(sid)) {
                keep = new Device(sid, System.currentTimeMillis(), "", "current");
                state.getDevices().add(keep);
            }
            if (StringUtils.hasText(sid)) {
                state.getKicked().remove(sid);
                try {
                    cacheStore.delete(kickedKey(userId, sid));
                } catch (Exception ignored) {
                }
            }
            save(userId, state);
            syncOnline(userId, state);
        }
    }

    public void clear(Long userId) {
        if (userId == null) {
            return;
        }
        synchronized (lock(userId)) {
            try {
                cacheStore.delete(KEY + userId);
            } catch (Exception ignored) {
            }
            syncOnline(userId, new SessionState());
        }
    }

    public List<Long> onlineUserIds() {
        synchronized (onlineLock) {
            return new ArrayList<>(readOnline());
        }
    }

    public List<Device> list(Long userId) {
        if (userId == null) {
            return List.of();
        }
        synchronized (lock(userId)) {
            return new ArrayList<>(load(userId).getDevices());
        }
    }

    private void syncOnline(Long userId, SessionState state) {
        boolean online = state.getDevices() != null && !state.getDevices().isEmpty();
        synchronized (onlineLock) {
            java.util.LinkedHashSet<Long> ids = readOnline();
            if (online) {
                ids.add(userId);
            } else {
                ids.remove(userId);
            }
            try {
                String value = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
                cacheStore.set(ONLINE_KEY, value, TTL);
            } catch (Exception e) {
                log.debug("写入在线用户索引失败: {}", e.getMessage());
            }
        }
    }

    private java.util.LinkedHashSet<Long> readOnline() {
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        try {
            String raw = cacheStore.get(ONLINE_KEY);
            if (!StringUtils.hasText(raw)) {
                return ids;
            }
            for (String part : raw.split(",")) {
                if (part.isBlank()) {
                    continue;
                }
                ids.add(Long.parseLong(part.trim()));
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private Object lock(Long userId) {
        return locks.computeIfAbsent(userId, id -> new Object());
    }

    private void addKicked(Long userId, SessionState state, String sid) {
        if (!StringUtils.hasText(sid)) {
            return;
        }
        markKicked(userId, sid);
        state.getKicked().remove(sid);
        state.getKicked().add(0, sid);
        while (state.getKicked().size() > KICKED_CAP) {
            state.getKicked().remove(state.getKicked().size() - 1);
        }
    }

    private void markKicked(Long userId, String sid) {
        try {
            cacheStore.set(kickedKey(userId, sid), "1", KICKED_TTL);
        } catch (Exception e) {
            log.debug("写入下线标记失败: {}", e.getMessage());
        }
    }

    private boolean isKicked(Long userId, String sid) {
        if (!StringUtils.hasText(sid)) {
            return false;
        }
        try {
            return cacheStore.hasKey(kickedKey(userId, sid));
        } catch (Exception e) {
            return false;
        }
    }

    private String kickedKey(Long userId, String sid) {
        return KICKED_KEY + userId + ":" + sid;
    }

    private SessionState load(Long userId) {
        try {
            String raw = cacheStore.get(KEY + userId);
            if (!StringUtils.hasText(raw)) {
                return new SessionState();
            }
            SessionState parsed = objectMapper.readValue(raw, SessionState.class);
            if (parsed == null) {
                return new SessionState();
            }
            if (parsed.getDevices() == null) {
                parsed.setDevices(new ArrayList<>());
            }
            if (parsed.getKicked() == null) {
                parsed.setKicked(new ArrayList<>());
            }
            return parsed;
        } catch (Exception e) {
            log.debug("读取登录会话失败: {}", e.getMessage());
            return new SessionState();
        }
    }

    private void save(Long userId, SessionState state) {
        try {
            cacheStore.set(KEY + userId, objectMapper.writeValueAsString(state), TTL);
        } catch (Exception e) {
            log.debug("写入登录会话失败: {}", e.getMessage());
        }
    }

    private static String trimUa(String ua) {
        return ua.length() > 180 ? ua.substring(0, 180) : ua;
    }
}
