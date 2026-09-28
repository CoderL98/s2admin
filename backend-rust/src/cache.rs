use std::collections::HashMap;
use std::sync::Mutex;
use std::time::{Duration, Instant};

const MAX_ENTRIES: usize = 20_000;
const DEFAULT_TTL: Duration = Duration::from_secs(3600);

#[derive(Clone)]
struct Entry {
    value: String,
    expire_at: Instant,
}

#[derive(Default)]
pub struct MemoryCache {
    map: Mutex<HashMap<String, Entry>>,
}

impl MemoryCache {
    pub fn new() -> Self {
        Self::default()
    }

    fn purge_expired(map: &mut HashMap<String, Entry>, now: Instant) {
        map.retain(|_, entry| entry.expire_at > now);
    }

    pub fn set(&self, key: &str, value: impl Into<String>, ttl: Duration) {
        let mut map = self.map.lock().expect("cache");
        let now = Instant::now();
        Self::purge_expired(&mut map, now);
        if map.len() >= MAX_ENTRIES && !map.contains_key(key) {
            tracing::warn!("内存缓存条目数已达上限 {MAX_ENTRIES},拒绝写入新 key: {key}");
            return;
        }
        map.insert(
            key.to_string(),
            Entry {
                value: value.into(),
                expire_at: now + ttl_or(ttl),
            },
        );
    }

    pub fn get(&self, key: &str) -> Option<String> {
        let mut map = self.map.lock().expect("cache");
        let now = Instant::now();
        match map.get(key) {
            Some(entry) if entry.expire_at > now => Some(entry.value.clone()),
            Some(_) => {
                map.remove(key);
                None
            }
            None => None,
        }
    }

    pub fn get_and_delete(&self, key: &str) -> Option<String> {
        let mut map = self.map.lock().expect("cache");
        let now = Instant::now();
        match map.remove(key) {
            Some(entry) if entry.expire_at > now => Some(entry.value),
            _ => None,
        }
    }

    pub fn has_key(&self, key: &str) -> bool {
        self.get(key).is_some()
    }

    pub fn delete(&self, key: &str) {
        self.map.lock().expect("cache").remove(key);
    }

    pub fn expire(&self, key: &str, ttl: Duration) {
        let mut map = self.map.lock().expect("cache");
        if let Some(entry) = map.get_mut(key) {
            entry.expire_at = Instant::now() + ttl_or(ttl);
        }
    }

    pub fn set_if_absent(&self, key: &str, value: impl Into<String>, ttl: Duration) -> bool {
        let mut map = self.map.lock().expect("cache");
        let now = Instant::now();
        if let Some(existing) = map.get(key) {
            if existing.expire_at > now {
                return false;
            }
        }
        if map.len() >= MAX_ENTRIES && !map.contains_key(key) {
            return false;
        }
        map.insert(
            key.to_string(),
            Entry {
                value: value.into(),
                expire_at: now + ttl_or(ttl),
            },
        );
        true
    }

    pub fn increment(&self, key: &str) -> i64 {
        let mut map = self.map.lock().expect("cache");
        let now = Instant::now();
        let fresh = match map.get(key) {
            Some(entry) if entry.expire_at > now => false,
            _ => true,
        };
        let next = if fresh {
            1
        } else {
            map.get(key)
                .and_then(|e| e.value.parse::<i64>().ok())
                .unwrap_or(0)
                + 1
        };
        let expire_at = if fresh {
            now + DEFAULT_TTL
        } else {
            map.get(key).map(|e| e.expire_at).unwrap_or(now + DEFAULT_TTL)
        };
        map.insert(
            key.to_string(),
            Entry {
                value: next.to_string(),
                expire_at,
            },
        );
        next
    }
}

fn ttl_or(ttl: Duration) -> Duration {
    if ttl.is_zero() {
        Duration::from_millis(1)
    } else {
        ttl
    }
}
