use std::path::PathBuf;

use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::QueryBuilder;
use uuid::Uuid;

use crate::db::last_id;
use crate::error::AppError;
use crate::response::Page;
use crate::scope;
use crate::state::{AppState, AuthUser};
use crate::util::{self, blank_to_none, like_pat, now_text, PageParams};

const TYPES: &[&str] = &["jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "csv", "zip"];
const IMAGES: &[&str] = &["jpg", "jpeg", "png", "gif", "webp"];

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FileQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub keyword: Option<String>,
    pub category: Option<String>,
}

pub struct StoredFile {
    pub bytes: Vec<u8>,
    pub content_type: String,
    pub filename: String,
    pub inline: bool,
}

pub async fn upload(state: &AppState, actor: &AuthUser, original: &str, bytes: &[u8], category: &str) -> Result<Value, AppError> {
    if category.eq_ignore_ascii_case("avatar") {
        return Err(AppError::bad("头像请在个人中心上传"));
    }
    store(state, actor, original, bytes, category).await
}

pub async fn upload_avatar(state: &AppState, actor: &AuthUser, original: &str, bytes: &[u8]) -> Result<Value, AppError> {
    let ext = extension(original);
    if !IMAGES.contains(&ext.as_str()) {
        return Err(AppError::bad("头像仅支持 jpg/png/gif/webp"));
    }
    store(state, actor, original, bytes, "avatar").await
}

pub async fn page(state: &AppState, actor: &AuthUser, query: &FileQuery) -> Result<Page<Value>, AppError> {
    let visible = scope::visible_user_ids(state, actor).await?;
    let total = {
        let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT COUNT(*) FROM sys_file WHERE deleted = 0");
        push_file(&mut qb, query, &visible);
        qb.build_query_scalar::<i64>().fetch_one(&state.db).await?
    };
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new(
        "SELECT id, original_name, stored_name, url, content_type, size, category, storage_type, create_time FROM sys_file WHERE deleted = 0",
    );
    push_file(&mut qb, query, &visible);
    qb.push(query.page.order_sql(&[("id", "id"), ("createTime", "create_time"), ("size", "size")], "id", true));
    qb.push(" LIMIT ");
    qb.push_bind(query.page.size());
    qb.push(" OFFSET ");
    qb.push_bind(query.page.offset());
    let rows: Vec<FileRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(file_vo).collect(), total, query.page.size(), query.page.num()))
}

pub async fn load(state: &AppState, actor: &AuthUser, filename: &str) -> Result<StoredFile, AppError> {
    if filename.contains("..") || filename.contains('/') || filename.contains('\\') {
        return Err(AppError::not_found("文件不存在"));
    }
    let row = by_name(state, filename).await?;
    let kind = content_type(&extension(filename));
    let public_avatar = row.category.as_deref().eq(&Some("avatar")) && kind.starts_with("image/");
    if !public_avatar {
        if !actor.has_perm("system:file:view") {
            return Err(AppError::forbidden("没有操作权限"));
        }
        scope::assert_can_access_owner(state, actor, row.create_by).await?;
    }
    let path = safe_path(state, filename)?;
    let bytes = tokio::fs::read(&path).await.map_err(|_| AppError::not_found("文件不存在"))?;
    Ok(StoredFile {
        bytes,
        content_type: kind.clone(),
        filename: filename.to_string(),
        inline: kind.starts_with("image/") || kind == "application/pdf",
    })
}

pub async fn delete_name(state: &AppState, actor: &AuthUser, filename: &str) -> Result<(), AppError> {
    let row = by_name(state, filename).await?;
    delete_row(state, actor, row).await
}

pub async fn delete_id(state: &AppState, actor: &AuthUser, id: i64) -> Result<(), AppError> {
    let row = by_id(state, id).await?;
    delete_row(state, actor, row).await
}

async fn delete_row(state: &AppState, actor: &AuthUser, row: FileRow) -> Result<(), AppError> {
    scope::assert_can_access_owner(state, actor, row.create_by).await?;
    if let Ok(path) = safe_path(state, &row.stored_name) {
        let _ = tokio::fs::remove_file(path).await;
    }
    sqlx::query("UPDATE sys_file SET deleted = 1, update_time = ? WHERE id = ?")
        .bind(now_text())
        .bind(row.id)
        .execute(&state.db)
        .await?;
    Ok(())
}

async fn store(state: &AppState, actor: &AuthUser, original: &str, bytes: &[u8], category: &str) -> Result<Value, AppError> {
    if bytes.is_empty() {
        return Err(AppError::bad("请选择文件"));
    }
    if bytes.len() > 10 * 1024 * 1024 {
        return Err(AppError::bad("上传文件超过大小限制"));
    }
    let original = file_name_only(original);
    let ext = extension(&original);
    if !TYPES.contains(&ext.as_str()) {
        return Err(AppError::bad(format!("不支持的文件类型: {ext}")));
    }
    let stored = format!("{}.{}", Uuid::new_v4().simple(), ext);
    let path = safe_path(state, &stored)?;
    if let Some(parent) = path.parent() {
        tokio::fs::create_dir_all(parent).await.ok();
    }
    tokio::fs::write(&path, bytes).await.map_err(|_| AppError::bad("文件保存失败"))?;
    let now = now_text();
    let url = format!("/api/system/file/{stored}");
    let category = if category.trim().is_empty() { "default" } else { category };
    let mut conn = state.db.acquire().await?;
    sqlx::query(
        "INSERT INTO sys_file (original_name, stored_name, url, content_type, size, category, storage_type, deleted, create_by, create_time, update_by, update_time)
         VALUES (?, ?, ?, ?, ?, ?, 'local', 0, ?, ?, ?, ?)",
    )
    .bind(&original)
    .bind(&stored)
    .bind(&url)
    .bind(content_type(&ext))
    .bind(bytes.len() as i64)
    .bind(category)
    .bind(actor.id)
    .bind(&now)
    .bind(actor.id)
    .bind(&now)
    .execute(&mut *conn)
    .await?;
    let id = last_id(&mut *conn).await?;
    Ok(file_vo(by_id(state, id).await?))
}

fn push_file(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &FileQuery, visible: &Option<Vec<i64>>) {
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        qb.push(" AND lower(coalesce(original_name,'')) LIKE ");
        qb.push_bind(like_pat(&keyword));
    }
    if let Some(category) = blank_to_none(query.category.clone()) {
        qb.push(" AND category = ");
        qb.push_bind(category);
    }
    scope::push_owner_scope(qb, visible, "create_by");
}

#[derive(sqlx::FromRow)]
struct FileRow {
    id: i64,
    original_name: Option<String>,
    stored_name: String,
    url: Option<String>,
    content_type: Option<String>,
    size: Option<i64>,
    category: Option<String>,
    storage_type: Option<String>,
    create_time: Option<String>,
    #[sqlx(default)]
    create_by: Option<i64>,
}

fn file_vo(row: FileRow) -> Value {
    json!({
        "id": row.id,
        "url": row.url,
        "name": row.original_name,
        "storedName": row.stored_name,
        "size": row.size.unwrap_or(0),
        "category": row.category,
        "contentType": row.content_type,
        "storageType": row.storage_type,
        "createTime": util::normalize_dt(row.create_time)
    })
}

async fn by_name(state: &AppState, name: &str) -> Result<FileRow, AppError> {
    sqlx::query_as("SELECT id, original_name, stored_name, url, content_type, size, category, storage_type, create_time, create_by FROM sys_file WHERE deleted = 0 AND stored_name = ?")
        .bind(name)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::not_found("文件不存在"))
}

async fn by_id(state: &AppState, id: i64) -> Result<FileRow, AppError> {
    sqlx::query_as("SELECT id, original_name, stored_name, url, content_type, size, category, storage_type, create_time, create_by FROM sys_file WHERE deleted = 0 AND id = ?")
        .bind(id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::not_found("文件不存在"))
}

fn safe_path(state: &AppState, stored: &str) -> Result<PathBuf, AppError> {
    if stored.is_empty() || stored.contains("..") || stored.contains('/') || stored.contains('\\') {
        return Err(AppError::not_found("文件不存在"));
    }
    let dir = state.cfg.upload_dir.canonicalize().unwrap_or(state.cfg.upload_dir.clone());
    let target = dir.join(stored);
    if !target.starts_with(&dir) {
        return Err(AppError::not_found("文件不存在"));
    }
    Ok(target)
}

fn file_name_only(name: &str) -> String {
    let name = name.trim();
    let name = name.rsplit(['/', '\\']).next().unwrap_or(name);
    if name.is_empty() { "file".into() } else { name.to_string() }
}

fn extension(name: &str) -> String {
    name.rsplit_once('.').map(|(_, ext)| ext.to_lowercase()).filter(|e| !e.is_empty()).unwrap_or_default()
}

fn content_type(ext: &str) -> String {
    match ext {
        "jpg" | "jpeg" => "image/jpeg",
        "png" => "image/png",
        "gif" => "image/gif",
        "webp" => "image/webp",
        "pdf" => "application/pdf",
        "csv" => "text/csv",
        "zip" => "application/zip",
        "doc" => "application/msword",
        "docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" => "application/vnd.ms-excel",
        "xlsx" => "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        _ => "application/octet-stream",
    }
    .into()
}
