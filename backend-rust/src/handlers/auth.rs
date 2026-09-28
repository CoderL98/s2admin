use std::time::Duration;

use base64::Engine;
use rand::Rng;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use uuid::Uuid;

use crate::error::AppError;
use crate::password::{self, POLICY_MESSAGE};
use crate::security::{self, Issued};
use crate::state::{AppState, AuthUser, ReqMeta};
use crate::syscfg;
use crate::util::{self, blank_to_none, guess_location, lower_email, max_chars, now_text, parse_browser, parse_os, require_text};
use crate::security::SUPER_ADMIN;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LoginForm {
    pub username: Option<String>,
    pub password: Option<String>,
    pub captcha: Option<String>,
    pub captcha_key: Option<String>,
    pub remember_me: Option<bool>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RefreshForm {
    pub refresh_token: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileForm {
    pub nickname: Option<String>,
    pub email: Option<String>,
    pub phone: Option<String>,
    pub avatar: Option<String>,
    pub province: Option<String>,
    pub city: Option<String>,
    pub district: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PasswordForm {
    pub old_password: Option<String>,
    pub new_password: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ForgotForm {
    pub account: Option<String>,
    pub captcha: Option<String>,
    pub captcha_key: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ResetForm {
    pub account: Option<String>,
    pub code: Option<String>,
    pub new_password: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct UserInfo {
    id: i64,
    username: String,
    nickname: Option<String>,
    email: Option<String>,
    phone: Option<String>,
    avatar: Option<String>,
    province: Option<String>,
    city: Option<String>,
    district: Option<String>,
    roles: Vec<String>,
    permissions: Vec<String>,
    must_change_password: bool,
}

struct Account {
    id: i64,
    username: String,
    password: String,
    nickname: Option<String>,
    email: Option<String>,
    phone: Option<String>,
    avatar: Option<String>,
    status: Option<i64>,
    province: Option<String>,
    city: Option<String>,
    district: Option<String>,
    pwd_reset: Option<i64>,
}

pub async fn captcha(state: &AppState, meta: &ReqMeta) -> Result<Value, AppError> {
    security::assert_allowed(
        state,
        &format!("s2admin:rl:captcha:{}", meta.ip),
        20,
        Duration::from_secs(60),
        "验证码请求过于频繁,请稍后再试",
    )?;
    let enabled = syscfg::get_bool(state, "sys.account.captchaEnabled", true).await;
    if !enabled {
        return Ok(json!({"enabled": false, "captchaKey": null, "image": null}));
    }
    let code = random_code(4);
    let key = Uuid::new_v4().simple().to_string();
    let minutes = syscfg::get_int(state, "sys.account.captchaExpiration", 5).await.max(1);
    state.cache.set(
        &format!("s2admin:captcha:{key}"),
        code.to_lowercase(),
        Duration::from_secs((minutes as u64) * 60),
    );
    let image = format!(
        "data:image/svg+xml;base64,{}",
        base64::engine::general_purpose::STANDARD.encode(svg(&code))
    );
    Ok(json!({"enabled": true, "captchaKey": key, "image": image}))
}

pub async fn login(state: &AppState, meta: &ReqMeta, form: LoginForm) -> Result<Value, AppError> {
    let username = require_text(&form.username, "账号不能为空")?;
    let password = require_text(&form.password, "密码不能为空")?;
    let outcome = login_inner(state, meta, &username, &password, form.captcha, form.captcha_key, form.remember_me.unwrap_or(false)).await;
    match &outcome {
        Ok(value) => {
            let uid = value.get("user").and_then(|u| u.get("id")).and_then(|v| v.as_i64());
            record_login(state, uid, &username, meta, 0, "登录成功").await;
        }
        Err(err) => {
            let message = match err {
                AppError::Biz { code: 401, .. } => "用户名或密码错误".to_string(),
                other => other.log_message(),
            };
            record_login(state, None, &username, meta, 1, &message).await;
        }
    }
    outcome
}

async fn login_inner(
    state: &AppState,
    meta: &ReqMeta,
    username: &str,
    password: &str,
    captcha: Option<String>,
    captcha_key: Option<String>,
    remember: bool,
) -> Result<Value, AppError> {
    security::assert_not_locked(state, &format!("s2admin:login:ip-lock:{}", meta.ip), "当前 IP 登录失败次数过多,请稍后再试")?;
    security::assert_allowed(
        state,
        &format!("s2admin:login:ip-req:{}", meta.ip),
        30,
        Duration::from_secs(60),
        "登录请求过于频繁,请稍后再试",
    )?;
    verify_captcha(state, captcha_key.as_deref(), captcha.as_deref()).await?;
    let found = find_for_login(state, username).await?;
    let matched = found.as_ref().is_some_and(|u| password::verify(password, &u.password));
    if !matched {
        on_login_failed(state, found.as_ref().map(|u| u.id), username, &meta.ip).await;
        return Err(AppError::unauthorized("用户名或密码错误"));
    }
    let user = found.expect("matched account");
    if auto_lock_active(state, user.id) {
        return Err(AppError::forbidden("账号已被锁定"));
    }
    let mut status = user.status.unwrap_or(0);
    if release_expired_auto_lock(state, user.id).await? {
        status = 0;
    }
    if status == 2 {
        return Err(AppError::forbidden("账号已被锁定"));
    }
    if status != 0 {
        return Err(AppError::unauthorized("用户名或密码错误"));
    }
    state.cache.delete(&format!("s2admin:login:fail:{}", username.to_lowercase()));
    security::unlock_key(state, &format!("s2admin:login:ip-fail:{}", meta.ip));
    let roles = active_role_codes(state, user.id).await?;
    let issued = security::issue_pair(state, user.id, &user.username, &roles, remember, None)?;
    security::session_register(state, user.id, &issued.sid, &meta.ip, &meta.ua).await?;
    Ok(login_body(&issued, user_info(state, &user).await?))
}

pub async fn logout(state: &AppState, meta: &ReqMeta, header: Option<String>, refresh: Option<String>) -> Result<(), AppError> {
    if let Some(token) = bearer(header.as_deref()) {
        security::blacklist_token(state, &token);
        if let Ok(claims_sid) = session_of(state, &token) {
            security::session_remove(state, claims_sid.0, &claims_sid.1);
        }
    }
    if let Some(refresh) = blank_to_none(refresh) {
        security::blacklist_token(state, &refresh);
    }
    let _ = meta;
    Ok(())
}

pub async fn refresh(state: &AppState, meta: &ReqMeta, refresh_token: &str) -> Result<Value, AppError> {
    let claims = security::parse(state, refresh_token).map_err(|_| AppError::unauthorized("refreshToken 无效或已过期"))?;
    if claims.typ != "refresh" {
        return Err(AppError::unauthorized("refreshToken 类型不正确"));
    }
    if security::is_blacklisted(state, refresh_token) {
        return Err(AppError::unauthorized("refreshToken 已失效,请重新登录"));
    }
    if !security::is_session_active(state, claims.uid, &claims.sid).await? {
        return Err(AppError::unauthorized("登录已在其他设备下线,请重新登录"));
    }
    let user = load_account(state, claims.uid).await?.ok_or_else(|| AppError::unauthorized("用户不存在或已被删除"))?;
    let status = user.status.unwrap_or(0);
    if status != 0 {
        return Err(AppError::forbidden(status_message(status)));
    }
    let exp_ms = claims.exp as i64 * 1000;
    let ttl = exp_ms - util::now_millis();
    if ttl <= 0
        || !state.cache.set_if_absent(
            &format!("s2admin:jwt:refresh-used:{}", claims.jti),
            "1",
            Duration::from_millis(ttl.max(1) as u64),
        )
    {
        return Err(AppError::unauthorized("refreshToken 已失效,请重新登录"));
    }
    security::blacklist_token(state, refresh_token);
    let roles = active_role_codes(state, user.id).await?;
    let remember = claims.rm == "1";
    let sid = if claims.sid.is_empty() { None } else { Some(claims.sid.clone()) };
    let issued = security::issue_pair(state, user.id, &user.username, &roles, remember, sid)?;
    security::session_register(state, user.id, &issued.sid, &meta.ip, &meta.ua).await?;
    Ok(login_body(&issued, user_info(state, &user).await?))
}

pub async fn info(state: &AppState, user: &AuthUser) -> Result<Value, AppError> {
    let account = load_account(state, user.id).await?.ok_or_else(|| AppError::unauthorized("用户不存在或已被删除"))?;
    Ok(serde_json::to_value(user_info(state, &account).await?).unwrap_or(Value::Null))
}

pub async fn update_profile(state: &AppState, user: &AuthUser, form: ProfileForm) -> Result<Value, AppError> {
    let nickname = require_text(&form.nickname, "昵称不能为空")?;
    max_chars(&nickname, 50, "昵称最长 50 个字符")?;
    let email = lower_email(form.email);
    if let Some(email) = &email {
        max_chars(email, 100, "邮箱最长 100 个字符")?;
        if !email_ok(email) {
            return Err(AppError::bad("邮箱格式不正确"));
        }
    }
    let phone = blank_to_none(form.phone);
    if let Some(phone) = &phone {
        max_chars(phone, 20, "手机号最长 20 个字符")?;
        if !phone_ok(phone) {
            return Err(AppError::bad("手机号格式不正确"));
        }
    }
    util::opt_max(&form.avatar, 500, "头像地址过长")?;
    util::opt_max(&form.province, 50, "备注最长 500 个字符")?;
    util::opt_max(&form.city, 50, "备注最长 500 个字符")?;
    util::opt_max(&form.district, 50, "备注最长 500 个字符")?;
    let current = load_account(state, user.id).await?.ok_or_else(|| AppError::unauthorized("用户不存在或已被删除"))?;
    if let Some(email) = &email {
        if current.email.as_deref().map(|e| e.eq_ignore_ascii_case(email)) != Some(true)
            && email_taken(state, email, None).await?
        {
            return Err(AppError::bad("邮箱已被使用"));
        }
    }
    if let Some(phone) = &phone {
        if current.phone.as_deref() != Some(phone.as_str()) && phone_taken(state, phone, None).await? {
            return Err(AppError::bad("手机号已被使用"));
        }
    }
    let now = now_text();
    sqlx::query(
        "UPDATE sys_user SET nickname = ?, email = ?, phone = ?, avatar = ?, province = ?, city = ?, district = ?, update_by = ?, update_time = ? WHERE id = ? AND deleted = 0",
    )
    .bind(&nickname)
    .bind(&email)
    .bind(&phone)
    .bind(blank_to_none(form.avatar))
    .bind(blank_to_none(form.province))
    .bind(blank_to_none(form.city))
    .bind(blank_to_none(form.district))
    .bind(user.id)
    .bind(&now)
    .bind(user.id)
    .execute(&state.db)
    .await?;
    let account = load_account(state, user.id).await?.ok_or_else(|| AppError::unauthorized("用户不存在或已被删除"))?;
    Ok(serde_json::to_value(user_info(state, &account).await?).unwrap_or(Value::Null))
}

pub async fn change_password(state: &AppState, user: &AuthUser, form: PasswordForm) -> Result<(), AppError> {
    let old = require_text(&form.old_password, "原密码不能为空")?;
    let new_password = require_text(&form.new_password, "新密码不能为空")?;
    let len = new_password.chars().count();
    if !(8..=64).contains(&len) {
        return Err(AppError::bad("新密码长度需在 8-64 之间"));
    }
    password::validate(&new_password).map_err(|_| AppError::bad(POLICY_MESSAGE))?;
    let account = load_account(state, user.id).await?.ok_or_else(|| AppError::unauthorized("用户不存在或已被删除"))?;
    if !password::verify(&old, &account.password) {
        return Err(AppError::bad("原密码不正确"));
    }
    if old == new_password {
        return Err(AppError::bad("新密码不能与原密码相同"));
    }
    let hash = password::hash(&new_password)?;
    sqlx::query("UPDATE sys_user SET password = ?, pwd_reset = 0, update_by = ?, update_time = ? WHERE id = ?")
        .bind(hash)
        .bind(user.id)
        .bind(now_text())
        .bind(user.id)
        .execute(&state.db)
        .await?;
    security::evict_user(state, user.id);
    security::invalidate_user(state, user.id);
    Ok(())
}

pub async fn forgot(state: &AppState, meta: &ReqMeta, form: ForgotForm) -> Result<Value, AppError> {
    security::assert_allowed(
        state,
        &format!("s2admin:rl:forgot:{}", meta.ip),
        5,
        Duration::from_secs(600),
        "重置请求过于频繁,请稍后再试",
    )?;
    let account = require_text(&form.account, "账号不能为空")?;
    verify_captcha(state, form.captcha_key.as_deref(), form.captcha.as_deref()).await?;
    let mut body = json!({"message": "若账号存在且已绑定邮箱,验证码将在有效期内可用"});
    if let Some(user) = resolve_account(state, &account).await? {
        if let Some(email) = user.email.as_deref().filter(|e| !e.is_empty()) {
            let code = format!("{:06}", rand::thread_rng().gen_range(0..1_000_000));
            state.cache.set(
                &format!("s2admin:reset:{}", user.username.to_lowercase()),
                &code,
                Duration::from_secs(600),
            );
            tracing::info!(
                "密码重置验证码 account={} email={} mock={} code={}",
                user.username,
                email,
                state.cfg.mail_mock,
                if state.cfg.mail_mock { code.as_str() } else { "******" }
            );
            if state.cfg.mail_mock {
                body["mockCode"] = json!(code);
            }
        }
    }
    Ok(body)
}

pub async fn reset_password(state: &AppState, meta: &ReqMeta, form: ResetForm) -> Result<(), AppError> {
    security::assert_allowed(
        state,
        &format!("s2admin:rl:reset:{}", meta.ip),
        10,
        Duration::from_secs(600),
        "重置请求过于频繁,请稍后再试",
    )?;
    let account_name = require_text(&form.account, "账号不能为空")?;
    let code = require_text(&form.code, "验证码不能为空")?;
    let new_password = require_text(&form.new_password, "新密码不能为空")?;
    if !(8..=64).contains(&new_password.chars().count()) {
        return Err(AppError::bad("密码长度需在 8-64 之间"));
    }
    let user = resolve_account(state, &account_name).await?;
    let verify_key = user
        .as_ref()
        .map(|u| u.username.to_lowercase())
        .unwrap_or_else(|| format!("__missing__:{}", account_name.trim().to_lowercase()));
    let stored = state.cache.get_and_delete(&format!("s2admin:reset:{verify_key}"));
    if stored.as_deref() != Some(code.trim()) {
        return Err(AppError::bad("验证码错误或已过期"));
    }
    password::validate(&new_password)?;
    let Some(user) = user else {
        return Err(AppError::bad("验证码错误或已过期"));
    };
    let hash = password::hash(&new_password)?;
    sqlx::query("UPDATE sys_user SET password = ?, pwd_reset = 0, update_time = ? WHERE id = ?")
        .bind(hash)
        .bind(now_text())
        .bind(user.id)
        .execute(&state.db)
        .await?;
    security::evict_user(state, user.id);
    security::invalidate_user(state, user.id);
    Ok(())
}

pub async fn sessions(state: &AppState, user: &AuthUser) -> Result<Value, AppError> {
    let list = security::session_list(state, user.id)
        .into_iter()
        .map(|d| {
            json!({
                "sid": d.sid,
                "iat": d.iat,
                "ip": d.ip,
                "ua": d.ua,
                "current": d.sid == user.sid
            })
        })
        .collect::<Vec<_>>();
    Ok(Value::Array(list))
}

pub fn kick(state: &AppState, user: &AuthUser, sid: &str) -> Result<(), AppError> {
    if !sid.is_empty() && sid == user.sid {
        return Err(AppError::bad("不能下线当前设备,请使用退出登录"));
    }
    security::session_remove(state, user.id, sid);
    Ok(())
}

pub fn kick_others(state: &AppState, user: &AuthUser) {
    security::session_keep_only(state, user.id, &user.sid);
}

fn login_body(issued: &Issued, user: UserInfo) -> Value {
    json!({
        "token": issued.token,
        "refreshToken": issued.refresh,
        "expiresIn": issued.expires_in,
        "user": user
    })
}

async fn user_info(state: &AppState, user: &Account) -> Result<UserInfo, AppError> {
    let mut roles = active_role_codes(state, user.id).await?;
    roles.sort();
    let mut permissions: Vec<String> = security::load_permissions(state, user.id).await?.into_iter().collect();
    permissions.sort();
    Ok(UserInfo {
        id: user.id,
        username: user.username.clone(),
        nickname: user.nickname.clone(),
        email: user.email.clone(),
        phone: user.phone.clone(),
        avatar: user.avatar.clone(),
        province: user.province.clone(),
        city: user.city.clone(),
        district: user.district.clone(),
        roles,
        permissions,
        must_change_password: user.pwd_reset == Some(1),
    })
}

async fn active_role_codes(state: &AppState, user_id: i64) -> Result<Vec<String>, AppError> {
    let rows: Vec<String> = sqlx::query_scalar(
        "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id
         WHERE ur.user_id = ? AND r.deleted = 0 AND (r.status IS NULL OR r.status = 0)",
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await?;
    let mut set = rows;
    set.sort();
    set.dedup();
    let _ = SUPER_ADMIN;
    Ok(set)
}

async fn find_for_login(state: &AppState, account: &str) -> Result<Option<Account>, AppError> {
    let value = account.trim();
    if value.is_empty() {
        return Ok(None);
    }
    if let Some(user) = load_by(state, "username = ?", value).await? {
        return Ok(Some(user));
    }
    if let Some(user) = load_by(state, "lower(email) = lower(?)", value).await? {
        return Ok(Some(user));
    }
    load_by(state, "phone = ?", value).await
}

async fn resolve_account(state: &AppState, account: &str) -> Result<Option<Account>, AppError> {
    find_for_login(state, account).await
}

async fn load_account(state: &AppState, id: i64) -> Result<Option<Account>, AppError> {
    load_by(state, "id = ?", &id.to_string()).await
}

async fn load_by(state: &AppState, pred: &str, value: &str) -> Result<Option<Account>, AppError> {
    let sql = format!(
        "SELECT id, username, password, nickname, email, phone, avatar, status, province, city, district, pwd_reset
         FROM sys_user WHERE deleted = 0 AND {pred} LIMIT 1"
    );
    let row: Option<(i64, String, String, Option<String>, Option<String>, Option<String>, Option<String>, Option<i64>, Option<String>, Option<String>, Option<String>, Option<i64>)> =
        sqlx::query_as(&sql).bind(value).fetch_optional(&state.db).await?;
    Ok(row.map(|r| Account {
        id: r.0,
        username: r.1,
        password: r.2,
        nickname: r.3,
        email: r.4,
        phone: r.5,
        avatar: r.6,
        status: r.7,
        province: r.8,
        city: r.9,
        district: r.10,
        pwd_reset: r.11,
    }))
}

async fn on_login_failed(state: &AppState, user_id: Option<i64>, username: &str, ip: &str) {
    let threshold = syscfg::get_int(state, "sys.account.lockThreshold", 5).await.max(1);
    let duration = syscfg::get_int(state, "sys.account.lockDuration", 30).await.max(1);
    let ip_window = syscfg::get_int(state, "sys.account.ipFailWindow", 5).await.max(1);
    let ip_lock = syscfg::get_int(state, "sys.account.ipLockDuration", 15).await.max(1);
    let key = format!("s2admin:login:fail:{}", username.to_lowercase());
    let count = state.cache.increment(&key);
    state.cache.expire(&key, Duration::from_secs((duration as u64) * 60));
    if count >= threshold {
        if let Some(user_id) = user_id {
            let _ = arm_auto_lock(state, user_id, Duration::from_secs((duration as u64) * 60)).await;
            security::invalidate_user(state, user_id);
        }
    }
    if !ip.is_empty() {
        let fails = security::rate_increment(state, &format!("s2admin:login:ip-fail:{ip}"), Duration::from_secs((ip_window as u64) * 60));
        if fails >= threshold {
            security::lock_key(state, &format!("s2admin:login:ip-lock:{ip}"), Duration::from_secs((ip_lock as u64) * 60));
        }
    }
}

async fn arm_auto_lock(state: &AppState, user_id: i64, duration: Duration) -> Result<(), AppError> {
    let until = util::now_millis() + duration.as_millis() as i64;
    sqlx::query("UPDATE sys_user SET status = 2, update_time = ? WHERE id = ? AND deleted = 0 AND (status IS NULL OR status = 0)")
        .bind(now_text())
        .bind(user_id)
        .execute(&state.db)
        .await?;
    state.cache.set(&format!("s2admin:login:auto-until:{user_id}"), until.to_string(), Duration::from_secs(30 * 24 * 3600));
    security::evict_user(state, user_id);
    Ok(())
}

fn auto_lock_active(state: &AppState, user_id: i64) -> bool {
    let Some(raw) = state.cache.get(&format!("s2admin:login:auto-until:{user_id}")) else {
        return false;
    };
    raw.trim().parse::<i64>().map(|until| util::now_millis() < until).unwrap_or(false)
}

async fn release_expired_auto_lock(state: &AppState, user_id: i64) -> Result<bool, AppError> {
    let Some(raw) = state.cache.get(&format!("s2admin:login:auto-until:{user_id}")) else {
        return Ok(false);
    };
    let Ok(until) = raw.trim().parse::<i64>() else {
        clear_auto_lock(state, user_id);
        return Ok(false);
    };
    if util::now_millis() < until {
        return Ok(false);
    }
    clear_auto_lock(state, user_id);
    let changed = sqlx::query("UPDATE sys_user SET status = 0, update_time = ? WHERE id = ? AND deleted = 0 AND status = 2")
        .bind(now_text())
        .bind(user_id)
        .execute(&state.db)
        .await?;
    security::evict_user(state, user_id);
    Ok(changed.rows_affected() > 0)
}

pub fn clear_auto_lock(state: &AppState, user_id: i64) {
    state.cache.delete(&format!("s2admin:login:auto-until:{user_id}"));
}

async fn verify_captcha(state: &AppState, key: Option<&str>, code: Option<&str>) -> Result<(), AppError> {
    if !syscfg::get_bool(state, "sys.account.captchaEnabled", true).await {
        return Ok(());
    }
    let Some(key) = key.map(str::trim).filter(|s| !s.is_empty()) else {
        return Err(AppError::bad("请输入验证码"));
    };
    let Some(code) = code.map(str::trim).filter(|s| !s.is_empty()) else {
        return Err(AppError::bad("请输入验证码"));
    };
    let stored = state.cache.get_and_delete(&format!("s2admin:captcha:{key}"));
    if stored.as_deref().map(|s| s.eq_ignore_ascii_case(code)) != Some(true) {
        return Err(AppError::bad("验证码错误或已过期"));
    }
    Ok(())
}

pub async fn email_taken(state: &AppState, email: &str, except: Option<i64>) -> Result<bool, AppError> {
    let count: i64 = if let Some(id) = except {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND lower(email) = lower(?) AND id <> ?")
            .bind(email)
            .bind(id)
            .fetch_one(&state.db)
            .await?
    } else {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND lower(email) = lower(?)")
            .bind(email)
            .fetch_one(&state.db)
            .await?
    };
    Ok(count > 0)
}

pub async fn phone_taken(state: &AppState, phone: &str, except: Option<i64>) -> Result<bool, AppError> {
    let count: i64 = if let Some(id) = except {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND phone = ? AND id <> ?")
            .bind(phone)
            .bind(id)
            .fetch_one(&state.db)
            .await?
    } else {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND phone = ?")
            .bind(phone)
            .fetch_one(&state.db)
            .await?
    };
    Ok(count > 0)
}

pub fn email_ok(email: &str) -> bool {
    regex::Regex::new(r"^[^@\s]+@[^@\s]+\.[^@\s]+$").unwrap().is_match(email)
}

pub fn phone_ok(phone: &str) -> bool {
    regex::Regex::new(r"^1[3-9]\d{9}$").unwrap().is_match(phone)
}

fn status_message(status: i64) -> &'static str {
    match status {
        1 => "账号已被禁用",
        2 => "账号已被锁定",
        3 => "账号已过期",
        _ => "账号状态异常",
    }
}

pub async fn record_login(state: &AppState, user_id: Option<i64>, username: &str, meta: &ReqMeta, status: i64, message: &str) {
    let mut resolved = user_id;
    if resolved.is_none() && !username.trim().is_empty() {
        resolved = find_for_login(state, username).await.ok().flatten().map(|u| u.id);
    }
    let _ = sqlx::query(
        "INSERT INTO sys_login_log (user_id, username, ip, location, browser, os, status, message, login_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
    )
    .bind(resolved)
    .bind(username)
    .bind(&meta.ip)
    .bind(guess_location(&meta.ip))
    .bind(parse_browser(&meta.ua))
    .bind(parse_os(&meta.ua))
    .bind(status)
    .bind(message)
    .bind(now_text())
    .execute(&state.db)
    .await;
}

fn bearer(header: Option<&str>) -> Option<String> {
    let header = header?;
    let rest = header.strip_prefix("Bearer ").or_else(|| header.strip_prefix("bearer "))?;
    let rest = rest.trim();
    if rest.is_empty() { None } else { Some(rest.to_string()) }
}

fn session_of(state: &AppState, token: &str) -> Result<(i64, String), AppError> {
    let claims = security::parse(state, token)?;
    Ok((claims.uid, claims.sid))
}

fn random_code(len: usize) -> String {
    const CHARS: &[u8] = b"ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    let mut rng = rand::thread_rng();
    (0..len).map(|_| CHARS[rng.gen_range(0..CHARS.len())] as char).collect()
}

fn svg(code: &str) -> String {
    let mut rng = rand::thread_rng();
    let w = 120;
    let h = 40;
    let mut sb = format!("<svg xmlns='http://www.w3.org/2000/svg' width='{w}' height='{h}' viewBox='0 0 {w} {h}'><rect width='100%' height='100%' fill='#f4f4f5'/>");
    for _ in 0..4 {
        sb.push_str(&format!(
            "<line x1='{}' y1='{}' x2='{}' y2='{}' stroke='#d4d4d8' stroke-width='1'/>",
            rng.gen_range(0..w),
            rng.gen_range(0..h),
            rng.gen_range(0..w),
            rng.gen_range(0..h)
        ));
    }
    for (i, ch) in code.chars().enumerate() {
        let x = 16 + i * 24;
        let y = 26 + rng.gen_range(0..5);
        let rotate = rng.gen_range(0..30) - 15;
        sb.push_str(&format!(
            "<text x='{x}' y='{y}' font-size='22' font-family='monospace' fill='#18181b' transform='rotate({rotate} {x} {y})'>{ch}</text>"
        ));
    }
    sb.push_str("</svg>");
    sb
}

pub fn username_ok(username: &str) -> bool {
    regex::Regex::new(r"^[a-zA-Z0-9_]{2,50}$").unwrap().is_match(username)
}
