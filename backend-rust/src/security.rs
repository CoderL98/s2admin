use std::collections::HashSet;
use std::time::Duration;

use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use sqlx::SqlitePool;
use uuid::Uuid;

use crate::error::AppError;
use crate::state::{AppState, AuthUser, Device, SessionState};
use crate::syscfg;
use crate::util::now_millis;

pub const SUPER_ADMIN: &str = "SUPER_ADMIN";
const ACTIVE_CAP: usize = 200;
const KICKED_CAP: usize = 50;

#[derive(Debug, Serialize, Deserialize)]
pub struct TokenClaims {
    pub sub: String,
    pub jti: String,
    pub iat: u64,
    pub exp: u64,
    pub uid: i64,
    #[serde(default)]
    pub roles: Vec<String>,
    #[serde(rename = "type")]
    pub typ: String,
    #[serde(default)]
    pub rm: String,
    #[serde(default)]
    pub sid: String,
}

pub struct Issued {
    pub token: String,
    pub refresh: String,
    pub expires_in: i64,
    pub sid: String,
}

pub fn issue_pair(
    state: &AppState,
    user_id: i64,
    username: &str,
    roles: &[String],
    remember: bool,
    sid: Option<String>,
) -> Result<Issued, AppError> {
    let sid = sid
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let access = sign(state, user_id, username, roles, "access", false, &sid, state.cfg.access_ttl)?;
    let refresh_ttl = if remember { state.cfg.remember_ttl } else { state.cfg.refresh_ttl };
    let refresh = sign(state, user_id, username, &[], "refresh", remember, &sid, refresh_ttl)?;
    Ok(Issued {
        token: access,
        refresh,
        expires_in: state.cfg.access_ttl.as_secs() as i64,
        sid,
    })
}

fn sign(
    state: &AppState,
    user_id: i64,
    username: &str,
    roles: &[String],
    typ: &str,
    remember: bool,
    sid: &str,
    ttl: Duration,
) -> Result<String, AppError> {
    let now = now_millis() / 1000;
    let exp = now + ttl.as_secs() as i64;
    let claims = TokenClaims {
        sub: username.to_string(),
        jti: Uuid::new_v4().to_string(),
        iat: now as u64,
        exp: exp.max(now + 1) as u64,
        uid: user_id,
        roles: roles.to_vec(),
        typ: typ.into(),
        rm: if remember { "1".into() } else { "0".into() },
        sid: sid.into(),
    };
    encode(
        &Header::new(Algorithm::HS384),
        &claims,
        &EncodingKey::from_secret(state.cfg.jwt_secret.as_bytes()),
    )
    .map_err(|e| AppError::Internal(e.to_string()))
}

pub fn parse(state: &AppState, token: &str) -> Result<TokenClaims, AppError> {
    let mut validation = Validation::new(Algorithm::HS384);
    validation.validate_exp = true;
    validation.validate_nbf = false;
    validation.set_required_spec_claims(&["exp"]);
    validation.leeway = 0;
    decode::<TokenClaims>(
        token,
        &DecodingKey::from_secret(state.cfg.jwt_secret.as_bytes()),
        &validation,
    )
    .map(|data| data.claims)
    .map_err(|_| AppError::unauthorized("未登录或登录已过期"))
}

pub fn blacklist_token(state: &AppState, token: &str) {
    let Ok(claims) = parse(state, token) else {
        return;
    };
    let exp_ms = claims.exp as i64 * 1000;
    let ttl = exp_ms - now_millis();
    if ttl <= 0 {
        return;
    }
    state
        .jti_until
        .lock()
        .expect("jti")
        .insert(claims.jti.clone(), exp_ms);
    state.cache.set(
        &format!("s2admin:jwt:blacklist:{}", claims.jti),
        "1",
        Duration::from_millis(ttl as u64),
    );
}

pub fn is_blacklisted(state: &AppState, token: &str) -> bool {
    let Ok(claims) = parse(state, token) else {
        return false;
    };
    let now = now_millis();
    {
        let mut map = state.jti_until.lock().expect("jti");
        if let Some(exp) = map.get(&claims.jti).copied() {
            if now < exp {
                return true;
            }
            map.remove(&claims.jti);
        }
    }
    if state.cache.has_key(&format!("s2admin:jwt:blacklist:{}", claims.jti)) {
        return true;
    }
    is_user_invalidated(state, claims.uid, claims.iat as i64 * 1000)
}

pub fn invalidate_user(state: &AppState, user_id: i64) {
    let now = now_millis();
    state.user_invalid_before.lock().expect("inv").insert(user_id, now);
    state.sessions.lock().expect("sess").remove(&user_id);
    state.cache.set(
        &format!("s2admin:jwt:invalid-before:{user_id}"),
        now.to_string(),
        Duration::from_secs(8 * 24 * 3600),
    );
}

fn is_user_invalidated(state: &AppState, user_id: i64, issued_ms: i64) -> bool {
    if let Some(local) = state.user_invalid_before.lock().expect("inv").get(&user_id).copied() {
        if issued_ms < local {
            return true;
        }
    }
    if let Some(value) = state.cache.get(&format!("s2admin:jwt:invalid-before:{user_id}")) {
        if let Ok(ts) = value.parse::<i64>() {
            return issued_ms < ts;
        }
    }
    false
}

pub async fn authenticate(state: &AppState, token: &str) -> Result<AuthUser, AppError> {
    let claims = parse(state, token).map_err(|_| AppError::http_unauthorized("未登录或登录已过期"))?;
    if claims.typ != "access" || is_blacklisted(state, token) {
        return Err(AppError::http_unauthorized("未登录或登录已过期"));
    }
    let sid = claims.sid.clone();
    if !is_session_active(state, claims.uid, &sid).await? {
        return Err(AppError::http_unauthorized("未登录或登录已过期"));
    }
    let permissions = load_permissions(state, claims.uid).await?;
    let roles = load_roles(state, claims.uid).await?;
    let must = must_change_password(state, claims.uid).await?;
    Ok(AuthUser {
        id: claims.uid,
        username: claims.sub,
        roles: roles.into_iter().collect(),
        permissions: permissions.into_iter().collect(),
        sid,
        jti: claims.jti,
        iat_ms: claims.iat as i64 * 1000,
        must_change_password: must,
    })
}

pub async fn load_permissions(state: &AppState, user_id: i64) -> Result<HashSet<String>, AppError> {
    let key = format!("s2admin:perm:{}:{user_id}", generation(state));
    if let Some(cached) = state.cache.get(&key) {
        return Ok(decode_set(&cached));
    }
    let roles = role_codes(&state.db, user_id).await?;
    let perms = if roles.iter().any(|r| r == SUPER_ADMIN) {
        HashSet::from(["*".to_string()])
    } else {
        permission_codes(&state.db, user_id).await?
    };
    state.cache.set(&key, encode_set(&perms), Duration::from_secs(300));
    Ok(perms)
}

pub async fn load_roles(state: &AppState, user_id: i64) -> Result<HashSet<String>, AppError> {
    let key = format!("s2admin:roles:{}:{user_id}", generation(state));
    if let Some(cached) = state.cache.get(&key) {
        return Ok(decode_set(&cached));
    }
    let roles = role_codes(&state.db, user_id).await?;
    let set: HashSet<String> = roles.into_iter().collect();
    state.cache.set(&key, encode_set(&set), Duration::from_secs(300));
    Ok(set)
}

pub async fn must_change_password(state: &AppState, user_id: i64) -> Result<bool, AppError> {
    let key = format!("s2admin:pwdreset:{}:{user_id}", generation(state));
    if let Some(cached) = state.cache.get(&key) {
        return Ok(cached == "1");
    }
    let flag: Option<i64> = sqlx::query_scalar("SELECT pwd_reset FROM sys_user WHERE id = ? AND deleted = 0")
        .bind(user_id)
        .fetch_optional(&state.db)
        .await?;
    let must = flag == Some(1);
    state.cache.set(&key, if must { "1" } else { "0" }, Duration::from_secs(300));
    Ok(must)
}

pub fn evict_user(state: &AppState, user_id: i64) {
    let gen = generation(state);
    state.cache.delete(&format!("s2admin:perm:{gen}:{user_id}"));
    state.cache.delete(&format!("s2admin:roles:{gen}:{user_id}"));
    state.cache.delete(&format!("s2admin:pwdreset:{gen}:{user_id}"));
    state.cache.delete(&format!("s2admin:status:{gen}:{user_id}"));
}

pub fn clear_permissions(state: &AppState) {
    let _ = state.cache.increment("s2admin:perm:gen");
    state.cache.expire("s2admin:perm:gen", Duration::from_secs(30 * 24 * 3600));
}

fn generation(state: &AppState) -> i64 {
    state
        .cache
        .get("s2admin:perm:gen")
        .and_then(|v| v.parse().ok())
        .unwrap_or(0)
}

fn encode_set(values: &HashSet<String>) -> String {
    let mut list: Vec<&str> = values.iter().map(String::as_str).collect();
    list.sort_unstable();
    list.join("\n")
}

fn decode_set(cached: &str) -> HashSet<String> {
    if cached.is_empty() {
        HashSet::new()
    } else {
        cached.split('\n').map(|s| s.to_string()).collect()
    }
}

pub async fn role_codes(db: &SqlitePool, user_id: i64) -> Result<Vec<String>, AppError> {
    let rows: Vec<String> = sqlx::query_scalar(
        "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ? AND r.deleted = 0 AND r.status = 0",
    )
    .bind(user_id)
    .fetch_all(db)
    .await?;
    Ok(rows)
}

pub async fn permission_codes(db: &SqlitePool, user_id: i64) -> Result<HashSet<String>, AppError> {
    let rows: Vec<String> = sqlx::query_scalar(
        "SELECT DISTINCT p.code FROM sys_permission p
         JOIN sys_role_permission rp ON rp.permission_id = p.id
         JOIN sys_user_role ur ON ur.role_id = rp.role_id
         JOIN sys_role r ON r.id = ur.role_id
         WHERE ur.user_id = ? AND p.deleted = 0 AND p.status = 0 AND r.deleted = 0 AND r.status = 0
           AND p.code IS NOT NULL AND p.code <> ''",
    )
    .bind(user_id)
    .fetch_all(db)
    .await?;
    Ok(rows.into_iter().collect())
}

pub fn require_perm(user: &AuthUser, permission: &str) -> Result<(), AppError> {
    if user.has_perm(permission) {
        Ok(())
    } else {
        Err(AppError::http_forbidden("没有操作权限"))
    }
}

pub async fn session_register(state: &AppState, user_id: i64, sid: &str, ip: &str, ua: &str) -> Result<(), AppError> {
    if sid.is_empty() {
        return Ok(());
    }
    let max = syscfg::get_int(state, "sys.account.maxSessions", 0).await.max(0);
    let mut guard = state.sessions.lock().expect("sess");
    if is_kicked(state, user_id, sid) {
        return Err(AppError::unauthorized("登录已在其他设备下线,请重新登录"));
    }
    let entry = guard.entry(user_id).or_default();
    if entry.kicked.iter().any(|k| k == sid) {
        return Err(AppError::unauthorized("登录已在其他设备下线,请重新登录"));
    }
    entry.devices.retain(|d| d.sid != sid);
    let trimmed = if ua.chars().count() > 180 {
        ua.chars().take(180).collect()
    } else {
        ua.to_string()
    };
    entry.devices.insert(
        0,
        Device {
            sid: sid.into(),
            iat: now_millis(),
            ip: ip.into(),
            ua: trimmed,
        },
    );
    let cap = if max > 0 { max as usize } else { ACTIVE_CAP };
    let mut evicted = Vec::new();
    while entry.devices.len() > cap {
        if let Some(device) = entry.devices.pop() {
            evicted.push(device.sid);
        }
    }
    for sid_evicted in evicted {
        add_kicked(state, user_id, entry, &sid_evicted);
    }
    Ok(())
}

pub async fn is_session_active(state: &AppState, user_id: i64, sid: &str) -> Result<bool, AppError> {
    let max = syscfg::get_int(state, "sys.account.maxSessions", 0).await.max(0);
    if is_kicked(state, user_id, sid) {
        return Ok(false);
    }
    let guard = state.sessions.lock().expect("sess");
    let state_sess = guard.get(&user_id);
    let kicked = state_sess.map(|s| s.kicked.iter().any(|k| k == sid)).unwrap_or(false);
    if !sid.is_empty() && kicked {
        return Ok(false);
    }
    let devices_empty = state_sess.map(|s| s.devices.is_empty()).unwrap_or(true);
    let contains = state_sess
        .map(|s| s.devices.iter().any(|d| d.sid == sid))
        .unwrap_or(false);
    if max <= 0 {
        if sid.is_empty() || devices_empty {
            return Ok(true);
        }
        return Ok(contains);
    }
    if sid.is_empty() {
        return Ok(false);
    }
    if devices_empty {
        return Ok(true);
    }
    Ok(contains)
}

pub fn session_remove(state: &AppState, user_id: i64, sid: &str) {
    if sid.is_empty() {
        return;
    }
    let mut guard = state.sessions.lock().expect("sess");
    let entry = guard.entry(user_id).or_default();
    entry.devices.retain(|d| d.sid != sid);
    add_kicked(state, user_id, entry, sid);
}

pub fn session_keep_only(state: &AppState, user_id: i64, sid: &str) {
    let mut guard = state.sessions.lock().expect("sess");
    let entry = guard.entry(user_id).or_default();
    let mut keep = None;
    let mut rest = Vec::new();
    for device in entry.devices.drain(..) {
        if device.sid == sid {
            keep = Some(device);
        } else {
            rest.push(device.sid);
        }
    }
    for kicked in rest {
        add_kicked(state, user_id, entry, &kicked);
    }
    if keep.is_none() && !sid.is_empty() {
        keep = Some(Device {
            sid: sid.into(),
            iat: now_millis(),
            ip: String::new(),
            ua: "current".into(),
        });
    }
    if let Some(device) = keep {
        entry.devices.push(device);
    }
    if !sid.is_empty() {
        entry.kicked.retain(|k| k != sid);
        state.cache.delete(&kicked_key(user_id, sid));
    }
}

pub fn session_list(state: &AppState, user_id: i64) -> Vec<Device> {
    state
        .sessions
        .lock()
        .expect("sess")
        .get(&user_id)
        .map(|s| s.devices.clone())
        .unwrap_or_default()
}

fn add_kicked(state: &AppState, user_id: i64, entry: &mut SessionState, sid: &str) {
    if sid.is_empty() {
        return;
    }
    mark_kicked(state, user_id, sid);
    entry.kicked.retain(|k| k != sid);
    entry.kicked.insert(0, sid.to_string());
    while entry.kicked.len() > KICKED_CAP {
        entry.kicked.pop();
    }
}

fn is_kicked(state: &AppState, user_id: i64, sid: &str) -> bool {
    if sid.is_empty() {
        return false;
    }
    state.cache.has_key(&kicked_key(user_id, sid))
}

fn kicked_key(user_id: i64, sid: &str) -> String {
    format!("s2admin:sess:kicked:{user_id}:{sid}")
}

pub fn mark_kicked(state: &AppState, user_id: i64, sid: &str) {
    if sid.is_empty() {
        return;
    }
    state.cache.set(
        &kicked_key(user_id, sid),
        "1",
        Duration::from_secs(31 * 24 * 3600),
    );
}

pub fn assert_allowed(state: &AppState, key: &str, max: i64, window: Duration, message: &str) -> Result<(), AppError> {
    let count = state.cache.increment(key);
    if count == 1 {
        state.cache.expire(key, window);
    }
    if count > max {
        Err(AppError::too_many(message))
    } else {
        Ok(())
    }
}

pub fn assert_not_locked(state: &AppState, key: &str, message: &str) -> Result<(), AppError> {
    if state.cache.has_key(key) {
        Err(AppError::forbidden(message))
    } else {
        Ok(())
    }
}

pub fn rate_increment(state: &AppState, key: &str, window: Duration) -> i64 {
    let count = state.cache.increment(key);
    if count == 1 {
        state.cache.expire(key, window);
    }
    count
}

pub fn lock_key(state: &AppState, key: &str, ttl: Duration) {
    state.cache.set(key, "1", ttl);
}

pub fn unlock_key(state: &AppState, key: &str) {
    state.cache.delete(key);
}

