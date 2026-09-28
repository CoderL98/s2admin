use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::QueryBuilder;

use crate::error::AppError;
use crate::response::Page;
use crate::scope;
use crate::state::{AppState, AuthUser};
use crate::util::{self, blank_to_none, csv_escape, like_pat, PageParams};

const MAX_EXPORT: i64 = 10_000;

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LogQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub keyword: Option<String>,
    pub status: Option<i64>,
    pub module: Option<String>,
    pub begin_time: Option<String>,
    pub end_time: Option<String>,
}

pub async fn login_page(state: &AppState, actor: &AuthUser, query: &LogQuery) -> Result<Page<Value>, AppError> {
    page_login(state, actor, query, false).await
}

pub async fn my_login(state: &AppState, actor: &AuthUser, query: &LogQuery) -> Result<Page<Value>, AppError> {
    page_login(state, actor, query, true).await
}

pub async fn op_page(state: &AppState, actor: &AuthUser, query: &LogQuery) -> Result<Page<Value>, AppError> {
    page_op(state, actor, query, false).await
}

pub async fn my_op(state: &AppState, actor: &AuthUser, query: &LogQuery) -> Result<Page<Value>, AppError> {
    page_op(state, actor, query, true).await
}

pub async fn error_page(state: &AppState, actor: &AuthUser, query: &LogQuery) -> Result<Page<Value>, AppError> {
    let visible = scope::visible_user_ids(state, actor).await?;
    let total = count_error(state, query, &visible).await?;
    let mut qb = error_select();
    push_error(&mut qb, query, &visible);
    qb.push(query.page.order_sql(&[("id", "id"), ("errorTime", "error_time")], "error_time", true));
    push_limit(&mut qb, &query.page);
    let rows: Vec<ErrorRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(error_vo).collect(), total, query.page.size(), query.page.num()))
}

pub async fn op_one(state: &AppState, actor: &AuthUser, id: i64) -> Result<Value, AppError> {
    let row = op_row(state, id).await?;
    scope::assert_can_access_owner(state, actor, row.user_id).await?;
    Ok(op_vo(row))
}

pub async fn error_one(state: &AppState, actor: &AuthUser, id: i64) -> Result<Value, AppError> {
    let row = error_row(state, id).await?;
    scope::assert_can_access_owner(state, actor, row.user_id).await?;
    Ok(error_vo(row))
}

pub async fn export_login(state: &AppState, actor: &AuthUser, query: &LogQuery) -> Result<String, AppError> {
    let visible = scope::visible_user_ids(state, actor).await?;
    let total = count_login(state, query, &visible, false, actor.id).await?;
    if total > MAX_EXPORT {
        return Err(AppError::bad(format!("导出条数超过 {MAX_EXPORT},请缩小筛选范围")));
    }
    let mut qb = login_select();
    push_login(&mut qb, query, &visible, false, actor.id);
    let rows: Vec<LoginRow> = qb.build_query_as().fetch_all(&state.db).await?;
    let mut sb = String::from("\u{feff}username,ip,browser,os,status,message,loginTime\n");
    for row in rows {
        sb.push_str(&format!(
            "{},{},{},{},{},{},{}\n",
            csv_escape(&row.username.clone().unwrap_or_default()),
            csv_escape(&row.ip.clone().unwrap_or_default()),
            csv_escape(&row.browser.clone().unwrap_or_default()),
            csv_escape(&row.os.clone().unwrap_or_default()),
            row.status.unwrap_or(0),
            csv_escape(&row.message.clone().unwrap_or_default()),
            util::normalize_dt(row.login_time).unwrap_or_default()
        ));
    }
    Ok(sb)
}

pub async fn export_op(state: &AppState, actor: &AuthUser, query: &LogQuery) -> Result<String, AppError> {
    let visible = scope::visible_user_ids(state, actor).await?;
    let total = count_op(state, query, &visible, false, actor.id).await?;
    if total > MAX_EXPORT {
        return Err(AppError::bad(format!("导出条数超过 {MAX_EXPORT},请缩小筛选范围")));
    }
    let mut qb = op_select();
    push_op(&mut qb, query, &visible, false, actor.id);
    let rows: Vec<OpRow> = qb.build_query_as().fetch_all(&state.db).await?;
    let mut sb = String::from("\u{feff}username,module,operation,method,url,ip,status,executeTime,operationTime\n");
    for row in rows {
        sb.push_str(&format!(
            "{},{},{},{},{},{},{},{},{}\n",
            csv_escape(&row.username.clone().unwrap_or_default()),
            csv_escape(&row.module.clone().unwrap_or_default()),
            csv_escape(&row.operation.clone().unwrap_or_default()),
            csv_escape(&row.method.clone().unwrap_or_default()),
            csv_escape(&row.url.clone().unwrap_or_default()),
            csv_escape(&row.ip.clone().unwrap_or_default()),
            row.status.unwrap_or(0),
            row.execute_time.unwrap_or(0),
            util::normalize_dt(row.operation_time).unwrap_or_default()
        ));
    }
    Ok(sb)
}

pub async fn clean_login(state: &AppState, actor: &AuthUser) -> Result<(), AppError> {
    clean(state, actor, "sys_login_log").await
}
pub async fn clean_op(state: &AppState, actor: &AuthUser) -> Result<(), AppError> {
    clean(state, actor, "sys_operation_log").await
}
pub async fn clean_error(state: &AppState, actor: &AuthUser) -> Result<(), AppError> {
    clean(state, actor, "sys_error_log").await
}

async fn clean(state: &AppState, actor: &AuthUser, table: &str) -> Result<(), AppError> {
    let visible = scope::visible_user_ids(state, actor).await?;
    if let Some(ids) = visible {
        if ids.is_empty() {
            return Ok(());
        }
        let mut qb = QueryBuilder::<sqlx::Sqlite>::new(format!("DELETE FROM {table} WHERE user_id IN ("));
        let mut sep = qb.separated(", ");
        for id in ids {
            sep.push_bind(id);
        }
        sep.push_unseparated(")");
        qb.build().execute(&state.db).await?;
    } else {
        sqlx::query(&format!("DELETE FROM {table}")).execute(&state.db).await?;
    }
    Ok(())
}

async fn page_login(state: &AppState, actor: &AuthUser, query: &LogQuery, mine: bool) -> Result<Page<Value>, AppError> {
    let visible = if mine { None } else { scope::visible_user_ids(state, actor).await? };
    let total = count_login(state, query, &visible, mine, actor.id).await?;
    let mut qb = login_select();
    push_login(&mut qb, query, &visible, mine, actor.id);
    let order = if query.page.order_by.is_none() {
        " ORDER BY login_time DESC".to_string()
    } else {
        query.page.order_sql(&[("id", "id"), ("loginTime", "login_time"), ("username", "username")], "login_time", true)
    };
    qb.push(order);
    push_limit(&mut qb, &query.page);
    let rows: Vec<LoginRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(login_vo).collect(), total, query.page.size(), query.page.num()))
}

async fn page_op(state: &AppState, actor: &AuthUser, query: &LogQuery, mine: bool) -> Result<Page<Value>, AppError> {
    let visible = if mine { None } else { scope::visible_user_ids(state, actor).await? };
    let total = count_op(state, query, &visible, mine, actor.id).await?;
    let mut qb = op_select();
    push_op(&mut qb, query, &visible, mine, actor.id);
    qb.push(query.page.order_sql(&[("id", "id"), ("operationTime", "operation_time"), ("module", "module")], "operation_time", true));
    push_limit(&mut qb, &query.page);
    let rows: Vec<OpRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(op_vo).collect(), total, query.page.size(), query.page.num()))
}

fn login_select() -> QueryBuilder<'static, sqlx::Sqlite> {
    QueryBuilder::new("SELECT id, user_id, username, ip, location, browser, os, status, message, login_time FROM sys_login_log WHERE 1 = 1")
}
fn op_select() -> QueryBuilder<'static, sqlx::Sqlite> {
    QueryBuilder::new("SELECT id, user_id, username, operation, module, method, url, ip, location, old_value, new_value, status, error_msg, execute_time, operation_time FROM sys_operation_log WHERE 1 = 1")
}
fn error_select() -> QueryBuilder<'static, sqlx::Sqlite> {
    QueryBuilder::new("SELECT id, trace_id, user_id, username, ip, url, method, params, exception, stack_trace, error_time FROM sys_error_log WHERE 1 = 1")
}

fn push_login(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &LogQuery, visible: &Option<Vec<i64>>, mine: bool, uid: i64) {
    if mine {
        qb.push(" AND user_id = ");
        qb.push_bind(uid);
        return;
    }
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(coalesce(username,'')) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(ip,'')) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(status) = query.status {
        qb.push(" AND status = ");
        qb.push_bind(status);
    }
    if let Some(begin) = blank_to_none(query.begin_time.clone()) {
        qb.push(" AND login_time >= ");
        qb.push_bind(begin);
    }
    if let Some(end) = blank_to_none(query.end_time.clone()) {
        qb.push(" AND login_time <= ");
        qb.push_bind(end);
    }
    scope::push_owner_scope(qb, visible, "user_id");
}

fn push_op(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &LogQuery, visible: &Option<Vec<i64>>, mine: bool, uid: i64) {
    if mine {
        qb.push(" AND user_id = ");
        qb.push_bind(uid);
        return;
    }
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(coalesce(username,'')) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(module,'')) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(url,'')) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(status) = query.status {
        qb.push(" AND status = ");
        qb.push_bind(status);
    }
    if let Some(module) = blank_to_none(query.module.clone()) {
        qb.push(" AND lower(module) = ");
        qb.push_bind(module.to_lowercase());
    }
    if let Some(begin) = blank_to_none(query.begin_time.clone()) {
        qb.push(" AND operation_time >= ");
        qb.push_bind(begin);
    }
    if let Some(end) = blank_to_none(query.end_time.clone()) {
        qb.push(" AND operation_time <= ");
        qb.push_bind(end);
    }
    scope::push_owner_scope(qb, visible, "user_id");
}

fn push_error(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &LogQuery, visible: &Option<Vec<i64>>) {
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(coalesce(trace_id,'')) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(username,'')) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(url,'')) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(exception,'')) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(begin) = blank_to_none(query.begin_time.clone()) {
        qb.push(" AND error_time >= ");
        qb.push_bind(begin);
    }
    if let Some(end) = blank_to_none(query.end_time.clone()) {
        qb.push(" AND error_time <= ");
        qb.push_bind(end);
    }
    scope::push_owner_scope(qb, visible, "user_id");
}

fn push_limit(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, page: &PageParams) {
    qb.push(" LIMIT ");
    qb.push_bind(page.size());
    qb.push(" OFFSET ");
    qb.push_bind(page.offset());
}

async fn count_login(state: &AppState, query: &LogQuery, visible: &Option<Vec<i64>>, mine: bool, uid: i64) -> Result<i64, AppError> {
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT COUNT(*) FROM sys_login_log WHERE 1 = 1");
    push_login(&mut qb, query, visible, mine, uid);
    Ok(qb.build_query_scalar().fetch_one(&state.db).await?)
}
async fn count_op(state: &AppState, query: &LogQuery, visible: &Option<Vec<i64>>, mine: bool, uid: i64) -> Result<i64, AppError> {
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT COUNT(*) FROM sys_operation_log WHERE 1 = 1");
    push_op(&mut qb, query, visible, mine, uid);
    Ok(qb.build_query_scalar().fetch_one(&state.db).await?)
}
async fn count_error(state: &AppState, query: &LogQuery, visible: &Option<Vec<i64>>) -> Result<i64, AppError> {
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT COUNT(*) FROM sys_error_log WHERE 1 = 1");
    push_error(&mut qb, query, visible);
    Ok(qb.build_query_scalar().fetch_one(&state.db).await?)
}

#[derive(sqlx::FromRow)]
struct LoginRow { id: i64, user_id: Option<i64>, username: Option<String>, ip: Option<String>, location: Option<String>, browser: Option<String>, os: Option<String>, status: Option<i64>, message: Option<String>, login_time: Option<String> }
#[derive(sqlx::FromRow)]
struct OpRow { id: i64, user_id: Option<i64>, username: Option<String>, operation: Option<String>, module: Option<String>, method: Option<String>, url: Option<String>, ip: Option<String>, location: Option<String>, old_value: Option<String>, new_value: Option<String>, status: Option<i64>, error_msg: Option<String>, execute_time: Option<i64>, operation_time: Option<String> }
#[derive(sqlx::FromRow)]
struct ErrorRow { id: i64, trace_id: Option<String>, user_id: Option<i64>, username: Option<String>, ip: Option<String>, url: Option<String>, method: Option<String>, params: Option<String>, exception: Option<String>, stack_trace: Option<String>, error_time: Option<String> }

fn login_vo(r: LoginRow) -> Value {
    json!({"id": r.id, "userId": r.user_id, "username": r.username, "ip": r.ip, "location": r.location, "browser": r.browser, "os": r.os, "status": r.status, "message": r.message, "loginTime": util::normalize_dt(r.login_time)})
}
fn op_vo(r: OpRow) -> Value {
    json!({"id": r.id, "userId": r.user_id, "username": r.username, "operation": r.operation, "module": r.module, "method": r.method, "url": r.url, "ip": r.ip, "location": r.location, "oldValue": r.old_value, "newValue": r.new_value, "status": r.status, "errorMsg": r.error_msg, "executeTime": r.execute_time, "operationTime": util::normalize_dt(r.operation_time)})
}
fn error_vo(r: ErrorRow) -> Value {
    json!({"id": r.id, "traceId": r.trace_id, "userId": r.user_id, "username": r.username, "ip": r.ip, "url": r.url, "method": r.method, "params": r.params, "exception": r.exception, "stackTrace": r.stack_trace, "errorTime": util::normalize_dt(r.error_time)})
}

async fn op_row(state: &AppState, id: i64) -> Result<OpRow, AppError> {
    sqlx::query_as("SELECT id, user_id, username, operation, module, method, url, ip, location, old_value, new_value, status, error_msg, execute_time, operation_time FROM sys_operation_log WHERE id = ?")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("操作日志不存在"))
}
async fn error_row(state: &AppState, id: i64) -> Result<ErrorRow, AppError> {
    sqlx::query_as("SELECT id, trace_id, user_id, username, ip, url, method, params, exception, stack_trace, error_time FROM sys_error_log WHERE id = ?")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("异常日志不存在"))
}
