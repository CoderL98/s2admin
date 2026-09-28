mod cache;
mod config;
mod db;
mod error;
mod extract;
mod handlers;
mod oplog;
mod password;
mod response;
mod scope;
mod security;
mod seed;
mod state;
mod syscfg;
mod util;
mod xlsx;

use std::net::SocketAddr;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use axum::body::Body;
use axum::extract::{ConnectInfo, DefaultBodyLimit, Multipart, Path, State};
use axum::http::{header, HeaderMap, Method, StatusCode};
use axum::middleware::{self, Next};
use axum::response::{IntoResponse, Response};
use axum::routing::{delete, get, post, put};
use axum::{Json, Router};
use serde::Deserialize;
use serde::Serialize;
use serde_json::Value;
use tokio::net::TcpListener;
use tower_http::cors::{AllowHeaders, Any, CorsLayer};

use crate::error::AppError;
use crate::extract::{Auth, JsonBody, Meta, Q};
use crate::handlers::{auth, codegen, content, file, logs, rbac, user};
use crate::response::{ok, ok_null, ApiBody};
use crate::state::{AppState, ReqMeta};

pub use crate::config::AppConfig;

pub async fn serve(cfg: AppConfig) -> anyhow::Result<()> {
    let host = cfg.host.clone();
    let port = cfg.port;
    let (router, _) = boot(cfg).await?;
    let listener = TcpListener::bind(format!("{host}:{port}")).await?;
    tracing::info!("s2admin rust backend listening on http://{host}:{port}");
    axum::serve(listener, router.into_make_service_with_connect_info::<SocketAddr>()).await?;
    Ok(())
}

pub async fn boot(cfg: AppConfig) -> anyhow::Result<(Router, AppState)> {
    let db = db::connect(&cfg).await?;
    let state = AppState {
        db,
        cfg: Arc::new(cfg),
        cache: Arc::new(cache::MemoryCache::new()),
        sessions: Arc::new(Mutex::new(std::collections::HashMap::new())),
        jti_until: Arc::new(Mutex::new(std::collections::HashMap::new())),
        user_invalid_before: Arc::new(Mutex::new(std::collections::HashMap::new())),
    };
    seed::run(&state).await?;
    Ok((router(state.clone()), state))
}

fn router(state: AppState) -> Router {
    let cors = cors_layer(&state.cfg);
    Router::new()
        .route("/api/auth/captcha", get(captcha))
        .route("/api/auth/login", post(login))
        .route("/api/auth/logout", post(logout))
        .route("/api/auth/refresh", post(refresh))
        .route("/api/auth/info", get(info))
        .route("/api/auth/menus", get(menus))
        .route("/api/auth/profile", put(profile))
        .route("/api/auth/password", put(password_change))
        .route("/api/auth/forgot-password", post(forgot))
        .route("/api/auth/reset-password", post(reset_password))
        .route("/api/auth/sessions", get(sessions))
        .route("/api/auth/sessions/others", delete(kick_others))
        .route("/api/auth/sessions/{sid}", delete(kick_session))
        .route("/api/auth/my-login-logs", get(my_login_logs))
        .route("/api/auth/my-operations", get(my_operations))
        .route("/api/auth/avatar", post(upload_avatar))
        .route("/api/system/user/export", get(user_export))
        .route("/api/system/user/import-template", get(user_template))
        .route("/api/system/user/import", post(user_import))
        .route("/api/system/user/export-xlsx", get(user_export_xlsx))
        .route("/api/system/user/import-xlsx", post(user_import_xlsx))
        .route("/api/system/user/status", put(user_batch_status))
        .route("/api/system/user/{id}/status", put(user_status))
        .route("/api/system/user/{id}/password", put(user_reset_password))
        .route("/api/system/user/{id}", get(user_get).put(user_update).delete(user_delete))
        .route("/api/system/user", get(user_page).post(user_create).delete(user_batch_delete))
        .route("/api/system/role/all", get(role_all))
        .route("/api/system/role/{id}/permissions", get(role_perms).put(role_assign))
        .route("/api/system/role/{id}", put(role_update).delete(role_delete))
        .route("/api/system/role", get(role_page).post(role_create))
        .route("/api/system/menu/tree", get(menu_tree))
        .route("/api/system/menu/{id}/move", put(menu_move))
        .route("/api/system/menu/{id}", put(menu_update).delete(menu_delete))
        .route("/api/system/menu", get(menu_page).post(menu_create))
        .route("/api/system/permission/tree", get(perm_tree))
        .route("/api/system/permission/all", get(perm_all))
        .route("/api/system/permission/{id}", put(perm_update).delete(perm_delete))
        .route("/api/system/permission", get(perm_page).post(perm_create))
        .route("/api/system/dept/tree", get(dept_tree))
        .route("/api/system/dept/all", get(dept_all))
        .route("/api/system/dept/options", get(dept_options))
        .route("/api/system/dept/{id}", put(dept_update).delete(dept_delete))
        .route("/api/system/dept", post(dept_create))
        .route("/api/system/config/key/{key}", get(config_key))
        .route("/api/system/config/groups", get(config_groups))
        .route("/api/system/config/{id}", put(config_update).delete(config_delete))
        .route("/api/system/config", get(config_page).post(config_create))
        .route("/api/system/dict/type/{id}", put(dict_type_update).delete(dict_type_delete))
        .route("/api/system/dict/type", get(dict_type_page).post(dict_type_create))
        .route("/api/system/dict/data/type/{typeCode}", get(dict_by_code))
        .route("/api/system/dict/data/{id}", put(dict_data_update).delete(dict_data_delete))
        .route("/api/system/dict/data", get(dict_data_page).post(dict_data_create))
        .route("/api/system/file/upload", post(file_upload))
        .route("/api/system/file/id/{id}", delete(file_delete_id))
        .route("/api/system/file/{filename}", get(file_download).delete(file_delete_name))
        .route("/api/system/file", get(file_page))
        .route("/api/system/notice/published", get(notice_published))
        .route("/api/system/notice/{id}/publish", put(notice_publish))
        .route("/api/system/notice/{id}", put(notice_update).delete(notice_delete))
        .route("/api/system/notice", get(notice_page).post(notice_create))
        .route("/api/system/message/my", get(message_my))
        .route("/api/system/message/unread-count", get(message_unread))
        .route("/api/system/message/read-all", put(message_read_all))
        .route("/api/system/message/{id}/read", put(message_read))
        .route("/api/system/message/{id}", delete(message_delete))
        .route("/api/system/message", post(message_send))
        .route("/api/system/dashboard/stats", get(dashboard))
        .route("/api/monitor/login-log/export", get(login_export))
        .route("/api/monitor/login-log/clean", delete(login_clean))
        .route("/api/monitor/login-log", get(login_page))
        .route("/api/monitor/op-log/export", get(op_export))
        .route("/api/monitor/op-log/clean", delete(op_clean))
        .route("/api/monitor/op-log/{id}", get(op_one))
        .route("/api/monitor/op-log", get(op_page))
        .route("/api/monitor/error-log/clean", delete(error_clean))
        .route("/api/monitor/error-log/{id}", get(error_one))
        .route("/api/monitor/error-log", get(error_page))
        .route("/api/tools/codegen", post(codegen_zip))
        .fallback(not_found)
        .layer(DefaultBodyLimit::max(20 * 1024 * 1024))
        .layer(cors)
        .layer(middleware::from_fn_with_state(state.clone(), guard))
        .with_state(state)
}

fn cors_layer(cfg: &AppConfig) -> CorsLayer {
    let methods = [Method::GET, Method::POST, Method::PUT, Method::DELETE, Method::PATCH, Method::OPTIONS];
    let layer = CorsLayer::new()
        .allow_methods(methods)
        .allow_headers(AllowHeaders::mirror_request())
        .max_age(Duration::from_secs(3600));
    if cfg.cors_origins.iter().any(|origin| origin == "*") {
        layer.allow_origin(Any)
    } else {
        let origins: Vec<_> = cfg.cors_origins.iter().filter_map(|origin| origin.parse().ok()).collect();
        layer.allow_origin(origins).allow_credentials(true)
    }
}

async fn guard(State(state): State<AppState>, mut req: axum::extract::Request, next: Next) -> Response {
    let path = req.uri().path().to_string();
    let method = req.method().clone();
    let peer = req
        .extensions()
        .get::<ConnectInfo<SocketAddr>>()
        .map(|info| info.0.ip().to_string())
        .unwrap_or_default();
    let forwarded = header_string(req.headers(), "x-forwarded-for");
    let real = header_string(req.headers(), "x-real-ip");
    let ua = header_string(req.headers(), "user-agent").unwrap_or_default();
    let ip = util::client_ip(state.cfg.trusted_proxy, forwarded.as_deref(), real.as_deref(), &peer);
    req.extensions_mut().insert(ReqMeta {
        ip,
        ua,
        path: path.clone(),
        method: method.to_string(),
    });
    if is_public(&method, &path) {
        return next.run(req).await;
    }
    let token = req
        .headers()
        .get(header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer ").or_else(|| v.strip_prefix("bearer ")))
        .map(str::trim)
        .filter(|v| !v.is_empty())
        .map(|v| v.to_string());
    let Some(token) = token else {
        return AppError::http_unauthorized("未登录或登录已过期").into_response();
    };
    match security::authenticate(&state, &token).await {
        Ok(user) => {
            if user.must_change_password && !password_allowed(&method, &path) {
                return AppError::http_forbidden("请先修改初始密码").into_response();
            }
            req.extensions_mut().insert(user);
            next.run(req).await
        }
        Err(err) => err.into_response(),
    }
}

fn is_public(method: &Method, path: &str) -> bool {
    if *method == Method::OPTIONS {
        return true;
    }
    matches!(
        (method.as_str(), path),
        ("POST", "/api/auth/login")
            | ("POST", "/api/auth/refresh")
            | ("GET", "/api/auth/captcha")
            | ("POST", "/api/auth/forgot-password")
            | ("POST", "/api/auth/reset-password")
    )
}

fn password_allowed(method: &Method, path: &str) -> bool {
    if *method == Method::OPTIONS {
        return true;
    }
    (*method == Method::PUT && path.starts_with("/api/auth/password"))
        || path.starts_with("/api/auth/info")
        || path.starts_with("/api/auth/menus")
        || path.starts_with("/api/auth/logout")
        || path.starts_with("/api/auth/refresh")
}

fn header_string(headers: &HeaderMap, name: &str) -> Option<String> {
    headers.get(name).and_then(|v| v.to_str().ok()).map(|s| s.to_string())
}

async fn not_found() -> impl IntoResponse {
    (
        StatusCode::OK,
        Json(ApiBody {
            code: 404,
            message: "资源不存在".into(),
            data: None::<()>,
            timestamp: util::now_millis(),
        }),
    )
}

fn json_ok<T: Serialize>(data: T) -> Json<ApiBody<T>> {
    Json(ok(data))
}

async fn audit<T>(
    state: &AppState,
    user: &state::AuthUser,
    meta: &ReqMeta,
    module: &str,
    operation: &str,
    method_sig: &str,
    target_id: Option<i64>,
    new_value: String,
    started: Instant,
    old: Option<String>,
    result: Result<T, AppError>,
) -> Result<T, AppError> {
    oplog::write(
        state,
        Some(user),
        meta,
        &oplog::Op { module, operation, method_sig, target_id, new_value: Some(new_value) },
        old,
        result.as_ref().err(),
        started,
    )
    .await;
    result
}

async fn captcha(State(state): State<AppState>, Meta(meta): Meta) -> Result<Json<ApiBody<Value>>, AppError> {
    Ok(json_ok(auth::captcha(&state, &meta).await?))
}

async fn login(State(state): State<AppState>, Meta(meta): Meta, JsonBody(form): JsonBody<auth::LoginForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    Ok(json_ok(auth::login(&state, &meta, form).await?))
}

async fn logout(State(state): State<AppState>, Meta(meta): Meta, headers: HeaderMap, body: axum::body::Bytes) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    let refresh = if body.is_empty() {
        None
    } else {
        serde_json::from_slice::<auth::RefreshForm>(&body).map_err(|_| AppError::bad("请求体格式错误"))?.refresh_token
    };
    let header = headers.get(header::AUTHORIZATION).and_then(|v| v.to_str().ok()).map(|s| s.to_string());
    auth::logout(&state, &meta, header, refresh).await?;
    Ok(Json(ok_null()))
}

async fn refresh(State(state): State<AppState>, Meta(meta): Meta, JsonBody(form): JsonBody<auth::RefreshForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    let token = util::require_text(&form.refresh_token, "refreshToken 不能为空")?;
    Ok(json_ok(auth::refresh(&state, &meta, &token).await?))
}

async fn info(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Value>>, AppError> {
    Ok(json_ok(auth::info(&state, &user).await?))
}

async fn menus(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    Ok(json_ok(rbac::user_menus(&state, user.id).await?))
}

async fn profile(State(state): State<AppState>, Auth(user): Auth, JsonBody(form): JsonBody<auth::ProfileForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    Ok(json_ok(auth::update_profile(&state, &user, form).await?))
}

async fn password_change(State(state): State<AppState>, Auth(user): Auth, JsonBody(form): JsonBody<auth::PasswordForm>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    auth::change_password(&state, &user, form).await?;
    Ok(Json(ok_null()))
}

async fn forgot(State(state): State<AppState>, Meta(meta): Meta, JsonBody(form): JsonBody<auth::ForgotForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    Ok(json_ok(auth::forgot(&state, &meta, form).await?))
}

async fn reset_password(State(state): State<AppState>, Meta(meta): Meta, JsonBody(form): JsonBody<auth::ResetForm>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    auth::reset_password(&state, &meta, form).await?;
    Ok(Json(ok_null()))
}

async fn sessions(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Value>>, AppError> {
    Ok(json_ok(auth::sessions(&state, &user).await?))
}

async fn kick_others(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    auth::kick_others(&state, &user);
    Ok(Json(ok_null()))
}

async fn kick_session(State(state): State<AppState>, Auth(user): Auth, Path(sid): Path<String>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    auth::kick(&state, &user, &sid)?;
    Ok(Json(ok_null()))
}

async fn my_login_logs(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<logs::LogQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    Ok(json_ok(logs::my_login(&state, &user, &query).await?))
}

async fn my_operations(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<logs::LogQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    Ok(json_ok(logs::my_op(&state, &user, &query).await?))
}

async fn upload_avatar(State(state): State<AppState>, Auth(user): Auth, multipart: Multipart) -> Result<Json<ApiBody<Value>>, AppError> {
    let (name, _category, bytes) = read_upload(multipart).await?;
    Ok(json_ok(file::upload_avatar(&state, &user, &name, &bytes).await?))
}

async fn user_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<user::UserQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "system:user:view")?;
    Ok(json_ok(user::page(&state, &user, &query).await?))
}

async fn user_get(State(state): State<AppState>, Auth(user): Auth, Path(id): Path<i64>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:user:view")?;
    Ok(json_ok(user::get_by_id(&state, &user, id).await?))
}

async fn user_create(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<user::UserForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:user:add")?;
    let new_value = serde_json::to_string(&serde_json::json!({"username": form.username, "nickname": form.nickname})).unwrap_or_default();
    let started = Instant::now();
    let result = user::create(&state, &user, form).await;
    let result = audit(&state, &user, &meta, "用户管理", "新增", "UserController.create", None, new_value, started, None, result).await?;
    Ok(json_ok(result))
}

async fn user_update(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(form): JsonBody<user::UserForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:user:edit")?;
    let old = oplog::capture(&state, "用户管理", "修改", Some(id)).await;
    let started = Instant::now();
    let new_value = serde_json::to_string(&serde_json::json!({"id": id, "nickname": form.nickname})).unwrap_or_default();
    let result = user::update(&state, &user, id, form).await;
    let result = audit(&state, &user, &meta, "用户管理", "修改", "UserController.update", Some(id), new_value, started, old, result).await?;
    Ok(json_ok(result))
}

async fn user_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:user:delete")?;
    let old = oplog::capture(&state, "用户管理", "删除", Some(id)).await;
    let started = Instant::now();
    let result = user::delete(&state, &user, id).await;
    audit(&state, &user, &meta, "用户管理", "删除", "UserController.delete", Some(id), format!("[{id}]"), started, old, result).await?;
    Ok(Json(ok_null()))
}

#[derive(Deserialize)]
struct IdsQuery { ids: Option<Vec<i64>> }

async fn user_batch_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Q(query): Q<IdsQuery>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:user:delete")?;
    let ids = query.ids.unwrap_or_default();
    let started = Instant::now();
    let result = user::batch_delete(&state, &user, &ids).await;
    audit(&state, &user, &meta, "用户管理", "批量删除", "UserController.batchDelete", None, format!("{ids:?}"), started, None, result).await?;
    Ok(Json(ok_null()))
}

#[derive(Deserialize)]
struct StatusBody { status: Option<i64> }
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct BatchStatus { ids: Option<Vec<i64>>, status: Option<i64> }
#[derive(Deserialize)]
struct PasswordBody { password: Option<String> }

async fn user_status(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(body): JsonBody<StatusBody>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:user:edit")?;
    let old = oplog::capture(&state, "用户管理", "修改状态", Some(id)).await;
    let started = Instant::now();
    let result = user::update_status(&state, &user, id, body.status).await;
    audit(&state, &user, &meta, "用户管理", "修改状态", "UserController.updateStatus", Some(id), format!("{{\"status\":{:?}}}", body.status), started, old, result).await?;
    Ok(Json(ok_null()))
}

async fn user_batch_status(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(body): JsonBody<BatchStatus>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:user:edit")?;
    let started = Instant::now();
    let ids = body.ids.clone().unwrap_or_default();
    let mut result = Ok(());
    for id in &ids {
        if let Err(err) = user::update_status(&state, &user, *id, body.status).await {
            result = Err(err);
            break;
        }
    }
    audit(&state, &user, &meta, "用户管理", "批量改状态", "UserController.batchUpdateStatus", None, format!("{ids:?}"), started, None, result).await?;
    Ok(Json(ok_null()))
}

async fn user_reset_password(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(body): JsonBody<PasswordBody>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:user:edit")?;
    let password = body.password.unwrap_or_default();
    if password.is_empty() { return Err(AppError::bad("密码不能为空")); }
    let old = oplog::capture(&state, "用户管理", "重置密码", Some(id)).await;
    let started = Instant::now();
    let result = user::reset_password(&state, &user, id, &password).await;
    audit(&state, &user, &meta, "用户管理", "重置密码", "UserController.resetPassword", Some(id), "{\"password\":\"***\"}".into(), started, old, result).await?;
    Ok(Json(ok_null()))
}

async fn user_export(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<user::UserQuery>) -> Result<Response, AppError> {
    security::require_perm(&user, "system:user:view")?;
    Ok(download("users.csv", "text/csv;charset=UTF-8", user::export_csv(&state, &user, &query).await?.into_bytes()))
}

async fn user_template(Auth(user): Auth) -> Result<Response, AppError> {
    security::require_perm(&user, "system:user:add")?;
    Ok(download("user-import-template.csv", "text/csv;charset=UTF-8", user::import_template().into_bytes()))
}

async fn user_import(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, multipart: Multipart) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:user:add")?;
    let (_name, _category, bytes) = read_upload(multipart).await?;
    let text = String::from_utf8_lossy(&bytes).to_string();
    let started = Instant::now();
    let result = user::import_csv(&state, &user, &text).await;
    let result = audit(&state, &user, &meta, "用户管理", "导入", "UserController.importCsv", None, "[]".into(), started, None, result).await?;
    Ok(json_ok(result))
}

async fn user_export_xlsx(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<user::UserQuery>) -> Result<Response, AppError> {
    security::require_perm(&user, "system:user:view")?;
    Ok(download("users.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", user::export_xlsx(&state, &user, &query).await?))
}

async fn user_import_xlsx(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, multipart: Multipart) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:user:add")?;
    let (_name, _category, bytes) = read_upload(multipart).await?;
    let started = Instant::now();
    let result = user::import_xlsx(&state, &user, &bytes).await;
    let result = audit(&state, &user, &meta, "用户管理", "导入Excel", "UserController.importExcel", None, "[]".into(), started, None, result).await?;
    Ok(json_ok(result))
}

async fn role_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<rbac::RoleQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "system:role:view")?;
    Ok(json_ok(rbac::role_page(&state, &query).await?))
}
async fn role_all(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    security::require_perm(&user, "system:role:view")?;
    Ok(json_ok(rbac::role_all(&state).await?))
}
async fn role_create(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<rbac::RoleForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:role:add")?;
    let started = Instant::now();
    let new_value = serde_json::to_string(&serde_json::json!({"name": form.name, "code": form.code})).unwrap_or_default();
    let result = rbac::role_create(&state, &user, form).await;
    let result = audit(&state, &user, &meta, "角色管理", "新增", "RoleController.create", None, new_value, started, None, result).await?;
    Ok(json_ok(result))
}
async fn role_update(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(form): JsonBody<rbac::RoleForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:role:edit")?;
    let old = oplog::capture(&state, "角色管理", "修改", Some(id)).await;
    let started = Instant::now();
    let result = rbac::role_update(&state, &user, id, form).await;
    let result = audit(&state, &user, &meta, "角色管理", "修改", "RoleController.update", Some(id), format!("{id}"), started, old, result).await?;
    Ok(json_ok(result))
}
async fn role_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:role:delete")?;
    let old = oplog::capture(&state, "角色管理", "删除", Some(id)).await;
    let started = Instant::now();
    let result = rbac::role_delete(&state, &user, id).await;
    audit(&state, &user, &meta, "角色管理", "删除", "RoleController.delete", Some(id), format!("{id}"), started, old, result).await?;
    Ok(Json(ok_null()))
}
async fn role_perms(State(state): State<AppState>, Auth(user): Auth, Path(id): Path<i64>) -> Result<Json<ApiBody<Vec<i64>>>, AppError> {
    security::require_perm(&user, "system:role:view")?;
    Ok(json_ok(rbac::role_perm_ids(&state, id).await?))
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct PermIds { permission_ids: Option<Vec<i64>> }
async fn role_assign(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(body): JsonBody<PermIds>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:role:assign")?;
    let started = Instant::now();
    let ids = body.permission_ids.unwrap_or_default();
    let result = rbac::role_assign(&state, &user, id, ids.clone()).await;
    audit(&state, &user, &meta, "角色管理", "授权", "RoleController.assign", Some(id), format!("{ids:?}"), started, None, result).await?;
    Ok(Json(ok_null()))
}

async fn menu_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<rbac::MenuQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "system:menu:view")?;
    Ok(json_ok(rbac::menu_page(&state, &query).await?))
}
async fn menu_tree(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    security::require_perm(&user, "system:menu:view")?;
    Ok(json_ok(rbac::menu_tree(&state).await?))
}
async fn menu_create(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<rbac::MenuForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:menu:add")?;
    let started = Instant::now();
    let new_value = form.name.clone().unwrap_or_default();
    let result = rbac::menu_create(&state, &user, form).await;
    let result = audit(&state, &user, &meta, "菜单管理", "新增", "MenuController.create", None, new_value, started, None, result).await?;
    Ok(json_ok(result))
}
async fn menu_update(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(form): JsonBody<rbac::MenuForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:menu:edit")?;
    let old = oplog::capture(&state, "菜单管理", "修改", Some(id)).await;
    let started = Instant::now();
    let result = rbac::menu_update(&state, &user, id, form).await;
    let result = audit(&state, &user, &meta, "菜单管理", "修改", "MenuController.update", Some(id), format!("{id}"), started, old, result).await?;
    Ok(json_ok(result))
}
async fn menu_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:menu:delete")?;
    let old = oplog::capture(&state, "菜单管理", "删除", Some(id)).await;
    let started = Instant::now();
    let result = rbac::menu_delete(&state, id).await;
    audit(&state, &user, &meta, "菜单管理", "删除", "MenuController.delete", Some(id), format!("{id}"), started, old, result).await?;
    Ok(Json(ok_null()))
}
#[derive(Deserialize)]
struct MoveQuery { direction: Option<String> }
async fn menu_move(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, Q(query): Q<MoveQuery>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:menu:edit")?;
    let direction = query.direction.unwrap_or_default();
    let started = Instant::now();
    let result = rbac::menu_move(&state, id, &direction).await;
    audit(&state, &user, &meta, "菜单管理", "排序", "MenuController.move", Some(id), direction, started, None, result).await?;
    Ok(Json(ok_null()))
}

async fn perm_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<rbac::PermQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "system:permission:view")?;
    Ok(json_ok(rbac::perm_page(&state, &query).await?))
}
async fn perm_tree(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    security::require_perm(&user, "system:permission:view")?;
    Ok(json_ok(rbac::perm_tree(&state).await?))
}
async fn perm_all(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    security::require_perm(&user, "system:permission:view")?;
    Ok(json_ok(rbac::perm_all(&state).await?))
}
async fn perm_create(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<rbac::PermForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:permission:add")?;
    let started = Instant::now();
    let new_value = form.code.clone().unwrap_or_default();
    let result = rbac::perm_create(&state, &user, form).await;
    let result = audit(&state, &user, &meta, "权限管理", "新增", "PermissionController.create", None, new_value, started, None, result).await?;
    Ok(json_ok(result))
}
async fn perm_update(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(form): JsonBody<rbac::PermForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:permission:edit")?;
    let old = oplog::capture(&state, "权限管理", "修改", Some(id)).await;
    let started = Instant::now();
    let result = rbac::perm_update(&state, &user, id, form).await;
    let result = audit(&state, &user, &meta, "权限管理", "修改", "PermissionController.update", Some(id), format!("{id}"), started, old, result).await?;
    Ok(json_ok(result))
}
async fn perm_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:permission:delete")?;
    let old = oplog::capture(&state, "权限管理", "删除", Some(id)).await;
    let started = Instant::now();
    let result = rbac::perm_delete(&state, &user, id).await;
    audit(&state, &user, &meta, "权限管理", "删除", "PermissionController.delete", Some(id), format!("{id}"), started, old, result).await?;
    Ok(Json(ok_null()))
}

async fn dept_tree(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    security::require_perm(&user, "system:dept:view")?;
    Ok(json_ok(rbac::dept_tree(&state, &user).await?))
}
async fn dept_all(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    security::require_perm(&user, "system:dept:view")?;
    Ok(json_ok(rbac::dept_all(&state, &user).await?))
}
async fn dept_options(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    Ok(json_ok(rbac::dept_all(&state, &user).await?))
}
async fn dept_create(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<rbac::DeptForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:dept:add")?;
    let started = Instant::now();
    let new_value = form.name.clone().unwrap_or_default();
    let result = rbac::dept_create(&state, &user, form).await;
    let result = audit(&state, &user, &meta, "部门管理", "新增", "DeptController.create", None, new_value, started, None, result).await?;
    Ok(json_ok(result))
}
async fn dept_update(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(form): JsonBody<rbac::DeptForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:dept:edit")?;
    let old = oplog::capture(&state, "部门管理", "修改", Some(id)).await;
    let started = Instant::now();
    let result = rbac::dept_update(&state, &user, id, form).await;
    let result = audit(&state, &user, &meta, "部门管理", "修改", "DeptController.update", Some(id), format!("{id}"), started, old, result).await?;
    Ok(json_ok(result))
}
async fn dept_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:dept:delete")?;
    let old = oplog::capture(&state, "部门管理", "删除", Some(id)).await;
    let started = Instant::now();
    let result = rbac::dept_delete(&state, &user, id).await;
    audit(&state, &user, &meta, "部门管理", "删除", "DeptController.delete", Some(id), format!("{id}"), started, old, result).await?;
    Ok(Json(ok_null()))
}

async fn config_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<content::ConfigQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "system:config:view")?;
    Ok(json_ok(content::config_page(&state, &query).await?))
}
async fn config_key(State(state): State<AppState>, Auth(user): Auth, Path(key): Path<String>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:config:view")?;
    Ok(json_ok(content::config_by_key(&state, &key).await?))
}
async fn config_groups(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Vec<String>>>, AppError> {
    security::require_perm(&user, "system:config:view")?;
    Ok(json_ok(content::config_groups(&state).await?))
}
async fn config_create(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<content::ConfigForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:config:add")?;
    let started = Instant::now();
    let new_value = form.config_key.clone().unwrap_or_default();
    let result = content::config_create(&state, &user, form).await;
    let result = audit(&state, &user, &meta, "系统配置", "新增", "ConfigController.create", None, new_value, started, None, result).await?;
    Ok(json_ok(result))
}
async fn config_update(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(form): JsonBody<content::ConfigForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:config:edit")?;
    let old = oplog::capture(&state, "系统配置", "修改", Some(id)).await;
    let started = Instant::now();
    let result = content::config_update(&state, &user, id, form).await;
    let result = audit(&state, &user, &meta, "系统配置", "修改", "ConfigController.update", Some(id), format!("{id}"), started, old, result).await?;
    Ok(json_ok(result))
}
async fn config_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:config:delete")?;
    let old = oplog::capture(&state, "系统配置", "删除", Some(id)).await;
    let started = Instant::now();
    let result = content::config_delete(&state, id).await;
    audit(&state, &user, &meta, "系统配置", "删除", "ConfigController.delete", Some(id), format!("{id}"), started, old, result).await?;
    Ok(Json(ok_null()))
}

async fn dict_type_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<content::DictTypeQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "tools:dict:view")?;
    Ok(json_ok(content::dict_type_page(&state, &query).await?))
}
async fn dict_type_create(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<content::DictTypeForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "tools:dict:edit")?;
    let started = Instant::now();
    let new_value = form.code.clone().unwrap_or_default();
    let result = content::dict_type_create(&state, &user, form).await;
    let result = audit(&state, &user, &meta, "字典管理", "新增类型", "DictController.createType", None, new_value, started, None, result).await?;
    Ok(json_ok(result))
}
async fn dict_type_update(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(form): JsonBody<content::DictTypeForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "tools:dict:edit")?;
    let old = oplog::capture(&state, "字典管理", "修改类型", Some(id)).await;
    let started = Instant::now();
    let result = content::dict_type_update(&state, &user, id, form).await;
    let result = audit(&state, &user, &meta, "字典管理", "修改类型", "DictController.updateType", Some(id), format!("{id}"), started, old, result).await?;
    Ok(json_ok(result))
}
async fn dict_type_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "tools:dict:edit")?;
    let old = oplog::capture(&state, "字典管理", "删除类型", Some(id)).await;
    let started = Instant::now();
    let result = content::dict_type_delete(&state, id).await;
    audit(&state, &user, &meta, "字典管理", "删除类型", "DictController.deleteType", Some(id), format!("{id}"), started, old, result).await?;
    Ok(Json(ok_null()))
}
async fn dict_data_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<content::DictDataQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "tools:dict:view")?;
    Ok(json_ok(content::dict_data_page(&state, &query).await?))
}
async fn dict_by_code(State(state): State<AppState>, Auth(user): Auth, Path(code): Path<String>) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    security::require_perm(&user, "tools:dict:view")?;
    Ok(json_ok(content::dict_by_code(&state, &code).await?))
}
async fn dict_data_create(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<content::DictDataForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "tools:dict:edit")?;
    let started = Instant::now();
    let result = content::dict_data_create(&state, &user, form).await;
    let result = audit(&state, &user, &meta, "字典管理", "新增数据", "DictController.createData", None, String::new(), started, None, result).await?;
    Ok(json_ok(result))
}
async fn dict_data_update(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(form): JsonBody<content::DictDataForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "tools:dict:edit")?;
    let old = oplog::capture(&state, "字典管理", "修改数据", Some(id)).await;
    let started = Instant::now();
    let result = content::dict_data_update(&state, &user, id, form).await;
    let result = audit(&state, &user, &meta, "字典管理", "修改数据", "DictController.updateData", Some(id), format!("{id}"), started, old, result).await?;
    Ok(json_ok(result))
}
async fn dict_data_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "tools:dict:edit")?;
    let old = oplog::capture(&state, "字典管理", "删除数据", Some(id)).await;
    let started = Instant::now();
    let result = content::dict_data_delete(&state, id).await;
    audit(&state, &user, &meta, "字典管理", "删除数据", "DictController.deleteData", Some(id), format!("{id}"), started, old, result).await?;
    Ok(Json(ok_null()))
}

async fn file_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<file::FileQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "system:file:view")?;
    Ok(json_ok(file::page(&state, &user, &query).await?))
}
async fn file_upload(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, multipart: Multipart) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:file:view")?;
    let (name, category, bytes) = read_upload(multipart).await?;
    let started = Instant::now();
    let result = file::upload(&state, &user, &name, &bytes, &category).await;
    let result = audit(&state, &user, &meta, "文件", "上传", "FileController.upload", None, name, started, None, result).await?;
    Ok(json_ok(result))
}
async fn file_download(State(state): State<AppState>, Auth(user): Auth, Path(filename): Path<String>) -> Result<Response, AppError> {
    let stored = file::load(&state, &user, &filename).await?;
    let disposition = if stored.inline { "inline" } else { "attachment" };
    Ok(Response::builder()
        .header(header::CONTENT_TYPE, stored.content_type)
        .header(header::CONTENT_DISPOSITION, format!("{disposition}; filename=\"{}\"", stored.filename))
        .header("X-Content-Type-Options", "nosniff")
        .body(Body::from(stored.bytes))
        .unwrap())
}
async fn file_delete_id(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:file:delete")?;
    let old = oplog::capture(&state, "文件", "删除", Some(id)).await;
    let started = Instant::now();
    let result = file::delete_id(&state, &user, id).await;
    audit(&state, &user, &meta, "文件", "删除", "FileController.deleteById", Some(id), format!("{id}"), started, old, result).await?;
    Ok(Json(ok_null()))
}
async fn file_delete_name(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(filename): Path<String>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:file:delete")?;
    let started = Instant::now();
    let result = file::delete_name(&state, &user, &filename).await;
    audit(&state, &user, &meta, "文件", "删除", "FileController.delete", None, filename, started, None, result).await?;
    Ok(Json(ok_null()))
}

async fn notice_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<content::NoticeQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "system:notice:view")?;
    Ok(json_ok(content::notice_page(&state, &query).await?))
}
async fn notice_published(State(state): State<AppState>, Auth(_user): Auth) -> Result<Json<ApiBody<Vec<Value>>>, AppError> {
    Ok(json_ok(content::notice_published(&state).await?))
}
async fn notice_create(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<content::NoticeForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:notice:add")?;
    let started = Instant::now();
    let new_value = form.title.clone().unwrap_or_default();
    let result = content::notice_create(&state, &user, form).await;
    let result = audit(&state, &user, &meta, "公告", "新增", "NoticeController.create", None, new_value, started, None, result).await?;
    Ok(json_ok(result))
}
async fn notice_update(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(form): JsonBody<content::NoticeForm>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:notice:edit")?;
    let old = oplog::capture(&state, "公告", "修改", Some(id)).await;
    let started = Instant::now();
    let result = content::notice_update(&state, &user, id, form).await;
    let result = audit(&state, &user, &meta, "公告", "修改", "NoticeController.update", Some(id), format!("{id}"), started, old, result).await?;
    Ok(json_ok(result))
}
#[derive(Deserialize)]
struct PublishBody { published: Option<bool> }
async fn notice_publish(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>, JsonBody(body): JsonBody<PublishBody>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "system:notice:edit")?;
    let started = Instant::now();
    let result = content::notice_publish(&state, &user, id, body.published.unwrap_or(true)).await;
    let result = audit(&state, &user, &meta, "公告", "发布", "NoticeController.publish", Some(id), format!("{id}"), started, None, result).await?;
    Ok(json_ok(result))
}
async fn notice_delete(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:notice:delete")?;
    let old = oplog::capture(&state, "公告", "删除", Some(id)).await;
    let started = Instant::now();
    let result = content::notice_delete(&state, id).await;
    audit(&state, &user, &meta, "公告", "删除", "NoticeController.delete", Some(id), format!("{id}"), started, old, result).await?;
    Ok(Json(ok_null()))
}

async fn message_send(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta, JsonBody(form): JsonBody<content::MessageForm>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "system:message:send")?;
    let started = Instant::now();
    let new_value = form.title.clone().unwrap_or_default();
    let result = content::message_send(&state, &user, form).await;
    audit(&state, &user, &meta, "站内信", "发送", "MessageController.send", None, new_value, started, None, result).await?;
    Ok(Json(ok_null()))
}
async fn message_my(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<content::MessageQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    Ok(json_ok(content::message_my(&state, &user, &query).await?))
}
async fn message_unread(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Value>>, AppError> {
    Ok(json_ok(content::unread_count(&state, &user).await?))
}
async fn message_read(State(state): State<AppState>, Auth(user): Auth, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    content::mark_read(&state, &user, id).await?;
    Ok(Json(ok_null()))
}
async fn message_read_all(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    content::mark_all(&state, &user).await?;
    Ok(Json(ok_null()))
}
async fn message_delete(State(state): State<AppState>, Auth(user): Auth, Path(id): Path<i64>) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    content::message_delete(&state, &user, id).await?;
    Ok(Json(ok_null()))
}

async fn dashboard(State(state): State<AppState>, Auth(user): Auth) -> Result<Json<ApiBody<Value>>, AppError> {
    Ok(json_ok(content::dashboard(&state, &user).await?))
}

async fn login_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<logs::LogQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "monitor:log:view")?;
    Ok(json_ok(logs::login_page(&state, &user, &query).await?))
}
async fn login_export(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<logs::LogQuery>) -> Result<Response, AppError> {
    security::require_perm(&user, "monitor:log:view")?;
    Ok(download("login-log.csv", "text/csv;charset=UTF-8", logs::export_login(&state, &user, &query).await?.into_bytes()))
}
async fn login_clean(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "monitor:log:delete")?;
    let started = Instant::now();
    let result = logs::clean_login(&state, &user).await;
    audit(&state, &user, &meta, "登录日志", "清空", "LogController.cleanLogin", None, String::new(), started, None, result).await?;
    Ok(Json(ok_null()))
}
async fn op_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<logs::LogQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "monitor:log:view")?;
    Ok(json_ok(logs::op_page(&state, &user, &query).await?))
}
async fn op_one(State(state): State<AppState>, Auth(user): Auth, Path(id): Path<i64>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "monitor:log:view")?;
    Ok(json_ok(logs::op_one(&state, &user, id).await?))
}
async fn op_export(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<logs::LogQuery>) -> Result<Response, AppError> {
    security::require_perm(&user, "monitor:log:view")?;
    Ok(download("operation-log.csv", "text/csv;charset=UTF-8", logs::export_op(&state, &user, &query).await?.into_bytes()))
}
async fn op_clean(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "monitor:log:delete")?;
    let started = Instant::now();
    let result = logs::clean_op(&state, &user).await;
    audit(&state, &user, &meta, "操作日志", "清空", "LogController.cleanOp", None, String::new(), started, None, result).await?;
    Ok(Json(ok_null()))
}
async fn error_page(State(state): State<AppState>, Auth(user): Auth, Q(query): Q<logs::LogQuery>) -> Result<Json<ApiBody<response::Page<Value>>>, AppError> {
    security::require_perm(&user, "monitor:log:view")?;
    Ok(json_ok(logs::error_page(&state, &user, &query).await?))
}
async fn error_one(State(state): State<AppState>, Auth(user): Auth, Path(id): Path<i64>) -> Result<Json<ApiBody<Value>>, AppError> {
    security::require_perm(&user, "monitor:log:view")?;
    Ok(json_ok(logs::error_one(&state, &user, id).await?))
}
async fn error_clean(State(state): State<AppState>, Auth(user): Auth, Meta(meta): Meta) -> Result<Json<ApiBody<Option<()>>>, AppError> {
    security::require_perm(&user, "monitor:log:delete")?;
    let started = Instant::now();
    let result = logs::clean_error(&state, &user).await;
    audit(&state, &user, &meta, "异常日志", "清空", "LogController.cleanError", None, String::new(), started, None, result).await?;
    Ok(Json(ok_null()))
}

async fn codegen_zip(State(_state): State<AppState>, Auth(user): Auth, JsonBody(form): JsonBody<codegen::CodegenForm>) -> Result<Response, AppError> {
    security::require_perm(&user, "tools:codegen:generate")?;
    Ok(download("codegen.zip", "application/zip", codegen::generate(&form)?))
}

fn download(filename: &str, content_type: &str, bytes: Vec<u8>) -> Response {
    Response::builder()
        .header(header::CONTENT_TYPE, content_type)
        .header(header::CONTENT_DISPOSITION, format!("attachment; filename={filename}"))
        .body(Body::from(bytes))
        .unwrap()
}

async fn read_upload(mut multipart: Multipart) -> Result<(String, String, Vec<u8>), AppError> {
    let mut name = String::from("file");
    let mut category = String::from("default");
    let mut bytes = None;
    while let Some(field) = multipart.next_field().await.map_err(|_| AppError::bad("请求类型不正确,请使用 multipart 上传文件"))? {
        let field_name = field.name().unwrap_or("").to_string();
        if field_name == "category" {
            category = field.text().await.unwrap_or_else(|_| "default".into());
        } else if field_name == "file" || bytes.is_none() {
            if let Some(file_name) = field.file_name() {
                name = file_name.to_string();
            }
            let data = field.bytes().await.map_err(|_| AppError::bad("请求类型不正确,请使用 multipart 上传文件"))?;
            if data.len() > 10 * 1024 * 1024 {
                return Err(AppError::bad("上传文件超过大小限制"));
            }
            bytes = Some(data.to_vec());
        }
    }
    let bytes = bytes.ok_or_else(|| AppError::bad("请选择文件"))?;
    if bytes.is_empty() {
        return Err(AppError::bad("请选择文件"));
    }
    Ok((name, category, bytes))
}
