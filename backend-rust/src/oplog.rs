use std::time::Instant;

use serde_json::{json, Value};

use crate::error::AppError;
use crate::state::{AppState, AuthUser, ReqMeta};
use crate::util::{clip, guess_location, mask_secrets, now_text};

pub struct Op<'a> {
    pub module: &'a str,
    pub operation: &'a str,
    pub method_sig: &'a str,
    pub target_id: Option<i64>,
    pub new_value: Option<String>,
}

pub async fn capture(state: &AppState, module: &str, operation: &str, id: Option<i64>) -> Option<String> {
    let id = id.filter(|id| *id > 0)?;
    snapshot(state, module, operation, id).await
}

pub async fn write(
    state: &AppState,
    user: Option<&AuthUser>,
    meta: &ReqMeta,
    op: &Op<'_>,
    old_value: Option<String>,
    err: Option<&AppError>,
    started: Instant,
) {
    let status = if err.is_none() { 0 } else { 1 };
    let error_msg = err.map(AppError::log_message);
    let new_value = op.new_value.as_deref().map(|v| {
        let masked = mask_secrets(v);
        clip(&masked, 2000)
    });
    let old_value = old_value.map(|v| clip(&v, 2000));
    let _ = sqlx::query(
        "INSERT INTO sys_operation_log
        (user_id, username, operation, module, method, url, ip, location, old_value, new_value, status, error_msg, execute_time, operation_time)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    )
    .bind(user.map(|u| u.id))
    .bind(user.map(|u| u.username.clone()))
    .bind(op.operation)
    .bind(op.module)
    .bind(op.method_sig)
    .bind(&meta.path)
    .bind(&meta.ip)
    .bind(guess_location(&meta.ip))
    .bind(old_value)
    .bind(new_value)
    .bind(status)
    .bind(error_msg)
    .bind(started.elapsed().as_millis() as i64)
    .bind(now_text())
    .execute(&state.db)
    .await;
}

pub fn json_arg(value: &impl serde::Serialize) -> String {
    mask_secrets(&serde_json::to_string(value).unwrap_or_else(|_| "[]".into()))
}

async fn snapshot(state: &AppState, module: &str, operation: &str, id: i64) -> Option<String> {
    if id <= 0 {
        return None;
    }
    let value = match module {
        "用户管理" => user_snap(state, id).await,
        "角色管理" => role_snap(state, id).await,
        "菜单管理" => menu_snap(state, id).await,
        "权限管理" => perm_snap(state, id).await,
        "部门管理" => dept_snap(state, id).await,
        "系统配置" => config_snap(state, id).await,
        "公告" => notice_snap(state, id).await,
        "文件" => file_snap(state, id).await,
        "字典管理" if operation.contains('数') => dict_data_snap(state, id).await,
        "字典管理" => dict_type_snap(state, id).await,
        _ => None,
    };
    value.or_else(|| Some(format!("id={id}")))
}

async fn one(state: &AppState, sql: &str, id: i64) -> Option<Value> {
    let row = sqlx::query_as::<_, (Option<String>,)>(sql)
        .bind(id)
        .fetch_optional(&state.db)
        .await
        .ok()
        .flatten()?;
    serde_json::from_str(&row.0?).ok()
}

async fn user_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (String, Option<String>, Option<String>, Option<String>, Option<i64>, Option<i64>, Option<String>, Option<i64>) =
        sqlx::query_as(
            "SELECT username, nickname, email, phone, status, dept_id, dept_name, pwd_reset FROM sys_user WHERE id = ? AND deleted = 0",
        )
        .bind(id)
        .fetch_optional(&state.db)
        .await
        .ok()
        .flatten()?;
    Some(json!({
        "id": id, "username": row.0, "nickname": row.1, "email": row.2, "phone": row.3,
        "status": row.4, "deptId": row.5, "deptName": row.6, "pwdReset": row.7
    }).to_string())
}

async fn role_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (String, String, Option<i64>, Option<i64>) = sqlx::query_as(
        "SELECT name, code, status, data_scope FROM sys_role WHERE id = ? AND deleted = 0",
    )
    .bind(id)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten()?;
    Some(json!({"id": id, "name": row.0, "code": row.1, "status": row.2, "dataScope": row.3}).to_string())
}

async fn menu_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (String, Option<i64>, String, Option<String>, Option<i64>, Option<i64>, Option<i64>, Option<i64>) =
        sqlx::query_as(
            "SELECT name, parent_id, path, icon, sort, type, hidden, status FROM sys_menu WHERE id = ? AND deleted = 0",
        )
        .bind(id)
        .fetch_optional(&state.db)
        .await
        .ok()
        .flatten()?;
    Some(json!({
        "id": id, "name": row.0, "parentId": row.1, "path": row.2, "icon": row.3,
        "sort": row.4, "type": row.5, "hidden": row.6, "status": row.7
    }).to_string())
}

async fn perm_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (String, String, Option<i64>, Option<i64>, Option<i64>) = sqlx::query_as(
        "SELECT name, code, type, parent_id, status FROM sys_permission WHERE id = ? AND deleted = 0",
    )
    .bind(id)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten()?;
    Some(json!({"id": id, "name": row.0, "code": row.1, "type": row.2, "parentId": row.3, "status": row.4}).to_string())
}

async fn dept_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (String, Option<i64>, Option<i64>, Option<String>) = sqlx::query_as(
        "SELECT name, parent_id, status, leader FROM sys_dept WHERE id = ? AND deleted = 0",
    )
    .bind(id)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten()?;
    Some(json!({"id": id, "name": row.0, "parentId": row.1, "status": row.2, "leader": row.3}).to_string())
}

async fn config_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (String, Option<String>, Option<String>) = sqlx::query_as(
        "SELECT config_key, config_value, group_code FROM sys_config WHERE id = ? AND deleted = 0",
    )
    .bind(id)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten()?;
    Some(json!({"id": id, "configKey": row.0, "configValue": row.1, "groupCode": row.2}).to_string())
}

async fn notice_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (String, Option<i64>, Option<i64>, Option<i64>) = sqlx::query_as(
        "SELECT title, status, pinned, type FROM sys_notice WHERE id = ? AND deleted = 0",
    )
    .bind(id)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten()?;
    Some(json!({"id": id, "title": row.0, "status": row.1, "pinned": row.2, "type": row.3}).to_string())
}

async fn file_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (Option<String>, String, Option<String>) = sqlx::query_as(
        "SELECT original_name, stored_name, category FROM sys_file WHERE id = ? AND deleted = 0",
    )
    .bind(id)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten()?;
    Some(json!({"id": id, "originalName": row.0, "storedName": row.1, "category": row.2}).to_string())
}

async fn dict_type_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (String, String, Option<i64>) = sqlx::query_as(
        "SELECT name, code, status FROM sys_dict_type WHERE id = ? AND deleted = 0",
    )
    .bind(id)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten()?;
    Some(json!({"id": id, "name": row.0, "code": row.1, "status": row.2}).to_string())
}

async fn dict_data_snap(state: &AppState, id: i64) -> Option<String> {
    let row: (String, String, i64, Option<i64>) = sqlx::query_as(
        "SELECT label, value, dict_type_id, status FROM sys_dict_data WHERE id = ? AND deleted = 0",
    )
    .bind(id)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten()?;
    Some(json!({"id": id, "label": row.0, "value": row.1, "dictTypeId": row.2, "status": row.3}).to_string())
}

#[allow(dead_code)]
fn _keep(v: Value) -> Value {
    v
}

#[allow(dead_code)]
async fn _unused(state: &AppState) {
    let _ = one(state, "SELECT '{}' ", 0).await;
}
