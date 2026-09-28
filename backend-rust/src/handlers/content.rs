use std::time::Duration;

use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::QueryBuilder;

use crate::db::last_id;
use crate::error::AppError;
use crate::response::Page;
use crate::scope;
use crate::state::{AppState, AuthUser};
use crate::syscfg;
use crate::util::{self, blank_to_none, like_pat, max_chars, now_text, opt_max, require_text, tombstone, PageParams};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConfigQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub keyword: Option<String>,
    pub group_code: Option<String>,
    pub config_type: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConfigForm {
    pub config_key: Option<String>,
    pub config_value: Option<String>,
    pub config_type: Option<String>,
    pub group_code: Option<String>,
    pub remark: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DictTypeQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub keyword: Option<String>,
    pub status: Option<i64>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DictTypeForm {
    pub name: Option<String>,
    pub code: Option<String>,
    pub status: Option<i64>,
    pub remark: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DictDataQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub dict_type_id: Option<i64>,
    pub keyword: Option<String>,
    pub status: Option<i64>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DictDataForm {
    pub dict_type_id: Option<i64>,
    pub label: Option<String>,
    pub value: Option<String>,
    pub sort: Option<i64>,
    pub status: Option<i64>,
    pub remark: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NoticeQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub keyword: Option<String>,
    pub status: Option<i64>,
    #[serde(rename = "type")]
    pub typ: Option<i64>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NoticeForm {
    pub title: Option<String>,
    pub content: Option<String>,
    #[serde(rename = "type")]
    pub typ: Option<i64>,
    pub status: Option<i64>,
    pub pinned: Option<i64>,
    pub remark: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MessageQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub read_flag: Option<i64>,
    pub keyword: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MessageForm {
    pub title: Option<String>,
    pub content: Option<String>,
    pub receiver_ids: Option<Vec<i64>>,
}

pub async fn config_page(state: &AppState, query: &ConfigQuery) -> Result<Page<Value>, AppError> {
    let total = count(state, "sys_config", |qb| push_config(qb, query)).await?;
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT id, config_key, config_value, config_type, group_code, remark, create_by, create_time, update_by, update_time FROM sys_config WHERE deleted = 0");
    push_config(&mut qb, query);
    qb.push(query.page.order_sql(&[("id", "id"), ("configKey", "config_key"), ("createTime", "create_time")], "id", true));
    push_page(&mut qb, &query.page);
    let rows: Vec<ConfigRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(config_vo).collect(), total, query.page.size(), query.page.num()))
}

pub async fn config_by_key(state: &AppState, key: &str) -> Result<Value, AppError> {
    let row: Option<ConfigRow> = sqlx::query_as("SELECT id, config_key, config_value, config_type, group_code, remark, create_by, create_time, update_by, update_time FROM sys_config WHERE deleted = 0 AND config_key = ?")
        .bind(key).fetch_optional(&state.db).await?;
    Ok(row.map(config_vo).unwrap_or(Value::Null))
}

pub async fn config_groups(state: &AppState) -> Result<Vec<String>, AppError> {
    Ok(sqlx::query_scalar("SELECT DISTINCT group_code FROM sys_config WHERE deleted = 0 AND group_code IS NOT NULL AND group_code <> '' ORDER BY group_code")
        .fetch_all(&state.db).await?)
}

pub async fn config_create(state: &AppState, actor: &AuthUser, form: ConfigForm) -> Result<Value, AppError> {
    let key = validate_config(&form)?;
    if key_exists(state, &key, None).await? { return Err(AppError::bad("配置键已存在")); }
    let now = now_text();
    let mut conn = state.db.acquire().await?;
    sqlx::query("INSERT INTO sys_config (config_key, config_value, config_type, group_code, remark, deleted, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?)")
        .bind(&key).bind(form.config_value.clone().unwrap_or_default()).bind(blank_to_none(form.config_type.clone()).unwrap_or_else(|| "string".into()))
        .bind(blank_to_none(form.group_code.clone()).unwrap_or_else(|| "default".into())).bind(blank_to_none(form.remark.clone()))
        .bind(actor.id).bind(&now).bind(actor.id).bind(&now)
        .execute(&mut *conn).await?;
    let id = last_id(&mut *conn).await?;
    syscfg::evict(state, &key);
    config_by_id(state, id).await
}

pub async fn config_update(state: &AppState, actor: &AuthUser, id: i64, form: ConfigForm) -> Result<Value, AppError> {
    let key = validate_config(&form)?;
    let current = config_row(state, id).await?;
    if current.config_key != key && key_exists(state, &key, Some(id)).await? {
        return Err(AppError::bad("配置键已存在"));
    }
    if current.config_key.starts_with("sys.") && current.config_key != key {
        return Err(AppError::bad("内置配置键不允许修改"));
    }
    sqlx::query("UPDATE sys_config SET config_key = ?, config_value = ?, config_type = ?, group_code = ?, remark = ?, update_by = ?, update_time = ? WHERE id = ?")
        .bind(&key).bind(form.config_value.clone().unwrap_or_default())
        .bind(blank_to_none(form.config_type.clone()).unwrap_or_else(|| "string".into()))
        .bind(blank_to_none(form.group_code.clone()).unwrap_or_else(|| "default".into()))
        .bind(blank_to_none(form.remark.clone())).bind(actor.id).bind(now_text()).bind(id)
        .execute(&state.db).await?;
    syscfg::evict(state, &current.config_key);
    syscfg::evict(state, &key);
    config_by_id(state, id).await
}

pub async fn config_delete(state: &AppState, id: i64) -> Result<(), AppError> {
    let current = config_row(state, id).await?;
    if current.config_key.starts_with("sys.") {
        return Err(AppError::bad("内置配置不允许删除"));
    }
    syscfg::evict(state, &current.config_key);
    let key = tombstone(&current.config_key, id, 100);
    sqlx::query("UPDATE sys_config SET config_key = ?, deleted = 1, update_time = ? WHERE id = ?").bind(key).bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(())
}

pub async fn dict_type_page(state: &AppState, query: &DictTypeQuery) -> Result<Page<Value>, AppError> {
    let total = count(state, "sys_dict_type", |qb| push_dict_type(qb, query)).await?;
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT id, name, code, status, remark, create_by, create_time, update_by, update_time FROM sys_dict_type WHERE deleted = 0");
    push_dict_type(&mut qb, query);
    qb.push(query.page.order_sql(&[("id", "id"), ("name", "name"), ("code", "code"), ("createTime", "create_time")], "id", false));
    push_page(&mut qb, &query.page);
    let rows: Vec<DictTypeRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(dict_type_vo).collect(), total, query.page.size(), query.page.num()))
}

pub async fn dict_type_create(state: &AppState, actor: &AuthUser, form: DictTypeForm) -> Result<Value, AppError> {
    let (name, code) = validate_dict_type(&form)?;
    if dict_code_exists(state, &code, None).await? { return Err(AppError::bad("字典编码已存在")); }
    let now = now_text();
    let mut conn = state.db.acquire().await?;
    sqlx::query("INSERT INTO sys_dict_type (name, code, status, remark, deleted, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?)")
        .bind(name).bind(&code).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark)).bind(actor.id).bind(&now).bind(actor.id).bind(&now)
        .execute(&mut *conn).await?;
    let id = last_id(&mut *conn).await?;
    evict_dict(state, &code);
    Ok(dict_type_vo(dict_type_row(state, id).await?))
}

pub async fn dict_type_update(state: &AppState, actor: &AuthUser, id: i64, form: DictTypeForm) -> Result<Value, AppError> {
    let (name, code) = validate_dict_type(&form)?;
    let current = dict_type_row(state, id).await?;
    if current.code != code && dict_code_exists(state, &code, Some(id)).await? {
        return Err(AppError::bad("字典编码已存在"));
    }
    sqlx::query("UPDATE sys_dict_type SET name = ?, code = ?, status = ?, remark = ?, update_by = ?, update_time = ? WHERE id = ?")
        .bind(name).bind(&code).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark)).bind(actor.id).bind(now_text()).bind(id)
        .execute(&state.db).await?;
    evict_dict(state, &current.code);
    evict_dict(state, &code);
    Ok(dict_type_vo(dict_type_row(state, id).await?))
}

pub async fn dict_type_delete(state: &AppState, id: i64) -> Result<(), AppError> {
    let used: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_dict_data WHERE deleted = 0 AND dict_type_id = ?").bind(id).fetch_one(&state.db).await?;
    if used > 0 { return Err(AppError::bad("该字典下仍有字典数据,请先删除字典数据")); }
    let current = dict_type_row(state, id).await?;
    evict_dict(state, &current.code);
    let code = tombstone(&current.code, id, 50);
    sqlx::query("UPDATE sys_dict_type SET code = ?, deleted = 1, update_time = ? WHERE id = ?").bind(code).bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(())
}

pub async fn dict_data_page(state: &AppState, query: &DictDataQuery) -> Result<Page<Value>, AppError> {
    let Some(type_id) = query.dict_type_id else { return Err(AppError::bad("请选择字典类型")); };
    let total = count(state, "sys_dict_data", |qb| push_dict_data(qb, query, type_id)).await?;
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT id, dict_type_id, label, value, sort, status, remark, create_by, create_time, update_by, update_time FROM sys_dict_data WHERE deleted = 0");
    push_dict_data(&mut qb, query, type_id);
    qb.push(query.page.order_sql(&[("id", "id"), ("sort", "sort"), ("label", "label"), ("createTime", "create_time")], "sort", false));
    push_page(&mut qb, &query.page);
    let rows: Vec<DictDataRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(dict_data_vo).collect(), total, query.page.size(), query.page.num()))
}

pub async fn dict_by_code(state: &AppState, code: &str) -> Result<Vec<Value>, AppError> {
    let typ: Option<(i64, Option<i64>)> = sqlx::query_as("SELECT id, status FROM sys_dict_type WHERE deleted = 0 AND code = ?")
        .bind(code).fetch_optional(&state.db).await?;
    let Some((id, status)) = typ else { return Ok(Vec::new()); };
    if status.unwrap_or(0) != 0 { return Ok(Vec::new()); }
    let cache_key = format!("s2admin:dict:{code}");
    if let Some(cached) = state.cache.get(&cache_key) {
        if let Ok(list) = serde_json::from_str::<Vec<Value>>(&cached) {
            return Ok(list.into_iter().filter(|v| v.get("status").and_then(|s| s.as_i64()).unwrap_or(0) == 0).collect());
        }
    }
    let rows: Vec<DictDataRow> = sqlx::query_as("SELECT id, dict_type_id, label, value, sort, status, remark, create_by, create_time, update_by, update_time FROM sys_dict_data WHERE deleted = 0 AND dict_type_id = ? AND (status IS NULL OR status = 0) ORDER BY coalesce(sort, 0), id")
        .bind(id).fetch_all(&state.db).await?;
    let list: Vec<Value> = rows.into_iter().map(dict_data_vo).collect();
    state.cache.set(&cache_key, serde_json::to_string(&list).unwrap_or_else(|_| "[]".into()), Duration::from_secs(3600));
    Ok(list)
}

pub async fn dict_data_create(state: &AppState, actor: &AuthUser, form: DictDataForm) -> Result<Value, AppError> {
    let (type_id, label, value) = validate_dict_data(&form)?;
    let typ = dict_type_row(state, type_id).await?;
    if dict_value_exists(state, type_id, &value, None).await? { return Err(AppError::bad("同一字典下的键值已存在")); }
    let now = now_text();
    let mut conn = state.db.acquire().await?;
    sqlx::query("INSERT INTO sys_dict_data (dict_type_id, label, value, sort, status, remark, deleted, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)")
        .bind(type_id).bind(label).bind(&value).bind(form.sort.unwrap_or(0)).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark)).bind(actor.id).bind(&now).bind(actor.id).bind(&now)
        .execute(&mut *conn).await?;
    let id = last_id(&mut *conn).await?;
    evict_dict(state, &typ.code);
    Ok(dict_data_vo(dict_data_row(state, id).await?))
}

pub async fn dict_data_update(state: &AppState, actor: &AuthUser, id: i64, form: DictDataForm) -> Result<Value, AppError> {
    let (type_id, label, value) = validate_dict_data(&form)?;
    let current = dict_data_row(state, id).await?;
    let old_type = dict_type_row(state, current.dict_type_id).await?;
    let typ = dict_type_row(state, type_id).await?;
    if dict_value_exists(state, type_id, &value, Some(id)).await? { return Err(AppError::bad("同一字典下的键值已存在")); }
    sqlx::query("UPDATE sys_dict_data SET dict_type_id = ?, label = ?, value = ?, sort = ?, status = ?, remark = ?, update_by = ?, update_time = ? WHERE id = ?")
        .bind(type_id).bind(label).bind(&value).bind(form.sort.unwrap_or(0)).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark)).bind(actor.id).bind(now_text()).bind(id)
        .execute(&state.db).await?;
    evict_dict(state, &old_type.code);
    evict_dict(state, &typ.code);
    Ok(dict_data_vo(dict_data_row(state, id).await?))
}

pub async fn dict_data_delete(state: &AppState, id: i64) -> Result<(), AppError> {
    let current = dict_data_row(state, id).await?;
    if let Ok(typ) = dict_type_row(state, current.dict_type_id).await { evict_dict(state, &typ.code); }
    sqlx::query("UPDATE sys_dict_data SET deleted = 1, update_time = ? WHERE id = ?").bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(())
}

pub async fn notice_page(state: &AppState, query: &NoticeQuery) -> Result<Page<Value>, AppError> {
    let total = count(state, "sys_notice", |qb| push_notice(qb, query)).await?;
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT id, title, content, type, status, pinned, publish_time, remark, create_by, create_time, update_by, update_time FROM sys_notice WHERE deleted = 0");
    push_notice(&mut qb, query);
    qb.push(query.page.order_sql(&[("id", "id"), ("title", "title"), ("createTime", "create_time"), ("publishTime", "publish_time")], "id", true));
    push_page(&mut qb, &query.page);
    let rows: Vec<NoticeRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(notice_vo).collect(), total, query.page.size(), query.page.num()))
}

pub async fn notice_published(state: &AppState) -> Result<Vec<Value>, AppError> {
    let rows: Vec<NoticeRow> = sqlx::query_as("SELECT id, title, content, type, status, pinned, publish_time, remark, create_by, create_time, update_by, update_time FROM sys_notice WHERE deleted = 0 AND status = 1 ORDER BY coalesce(pinned, 0) DESC, publish_time DESC, id DESC")
        .fetch_all(&state.db).await?;
    Ok(rows.into_iter().map(notice_vo).collect())
}

pub async fn notice_create(state: &AppState, actor: &AuthUser, form: NoticeForm) -> Result<Value, AppError> {
    let title = validate_notice(&form)?;
    let status = form.status.unwrap_or(0);
    let now = now_text();
    let publish = if status == 1 { Some(now.clone()) } else { None };
    let mut conn = state.db.acquire().await?;
    sqlx::query("INSERT INTO sys_notice (title, content, type, status, pinned, publish_time, remark, deleted, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)")
        .bind(title).bind(form.content.clone()).bind(form.typ.unwrap_or(1)).bind(status).bind(form.pinned.unwrap_or(0)).bind(publish).bind(blank_to_none(form.remark.clone()))
        .bind(actor.id).bind(&now).bind(actor.id).bind(&now)
        .execute(&mut *conn).await?;
    let id = last_id(&mut *conn).await?;
    Ok(notice_vo(notice_row(state, id).await?))
}

pub async fn notice_update(state: &AppState, actor: &AuthUser, id: i64, form: NoticeForm) -> Result<Value, AppError> {
    let title = validate_notice(&form)?;
    let current = notice_row(state, id).await?;
    let status = form.status.unwrap_or(0);
    let publish = if status == 1 { current.publish_time.clone().or_else(|| Some(now_text())) } else { current.publish_time.clone() };
    sqlx::query("UPDATE sys_notice SET title = ?, content = ?, type = ?, status = ?, pinned = ?, publish_time = ?, remark = ?, update_by = ?, update_time = ? WHERE id = ?")
        .bind(title).bind(form.content).bind(form.typ.unwrap_or(1)).bind(status).bind(form.pinned.unwrap_or(0)).bind(publish).bind(blank_to_none(form.remark))
        .bind(actor.id).bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(notice_vo(notice_row(state, id).await?))
}

pub async fn notice_publish(state: &AppState, actor: &AuthUser, id: i64, published: bool) -> Result<Value, AppError> {
    let _ = notice_row(state, id).await?;
    let publish = if published { Some(now_text()) } else { None };
    sqlx::query("UPDATE sys_notice SET status = ?, publish_time = ?, update_by = ?, update_time = ? WHERE id = ?")
        .bind(if published { 1 } else { 0 }).bind(publish).bind(actor.id).bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(notice_vo(notice_row(state, id).await?))
}

pub async fn notice_delete(state: &AppState, id: i64) -> Result<(), AppError> {
    let _ = notice_row(state, id).await?;
    sqlx::query("UPDATE sys_notice SET deleted = 1, update_time = ? WHERE id = ?").bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(())
}

pub async fn message_send(state: &AppState, actor: &AuthUser, form: MessageForm) -> Result<(), AppError> {
    let title = require_text(&form.title, "标题不能为空")?;
    max_chars(&title, 200, "标题最长 200 个字符")?;
    let mut receivers = Vec::new();
    for id in form.receiver_ids.clone().unwrap_or_default() {
        if !receivers.contains(&id) { receivers.push(id); }
    }
    if receivers.is_empty() { return Err(AppError::bad("请选择接收人")); }
    let now = now_text();
    for receiver_id in receivers {
        let exists: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted = 0").bind(receiver_id).fetch_one(&state.db).await?;
        if exists == 0 { return Err(AppError::bad("接收人不存在")); }
        let dept: Option<Option<i64>> = sqlx::query_scalar("SELECT dept_id FROM sys_user WHERE id = ? AND deleted = 0").bind(receiver_id).fetch_optional(&state.db).await?;
        scope::assert_can_access_user(state, actor, receiver_id, dept.flatten()).await?;
        sqlx::query("INSERT INTO sys_message (title, content, sender_id, sender_name, receiver_id, read_flag, deleted, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?)")
            .bind(&title).bind(form.content.clone()).bind(actor.id).bind(&actor.username).bind(receiver_id).bind(actor.id).bind(&now).bind(actor.id).bind(&now)
            .execute(&state.db).await?;
    }
    Ok(())
}

pub async fn message_my(state: &AppState, actor: &AuthUser, query: &MessageQuery) -> Result<Page<Value>, AppError> {
    let total = count(state, "sys_message", |qb| {
        qb.push(" AND receiver_id = ");
        qb.push_bind(actor.id);
        push_message(qb, query);
    }).await?;
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT id, title, content, sender_id, sender_name, receiver_id, read_flag, read_time, remark, create_by, create_time, update_by, update_time FROM sys_message WHERE deleted = 0");
    qb.push(" AND receiver_id = ");
    qb.push_bind(actor.id);
    push_message(&mut qb, query);
    qb.push(query.page.order_sql(&[("id", "id"), ("createTime", "create_time")], "id", true));
    push_page(&mut qb, &query.page);
    let rows: Vec<MessageRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(message_vo).collect(), total, query.page.size(), query.page.num()))
}

pub async fn unread_count(state: &AppState, actor: &AuthUser) -> Result<Value, AppError> {
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_message WHERE deleted = 0 AND receiver_id = ? AND coalesce(read_flag, 0) = 0")
        .bind(actor.id).fetch_one(&state.db).await?;
    Ok(json!({"count": count}))
}

pub async fn mark_read(state: &AppState, actor: &AuthUser, id: i64) -> Result<(), AppError> {
    let row = message_row(state, id).await?;
    if row.receiver_id != actor.id { return Err(AppError::forbidden("不能操作他人的消息")); }
    sqlx::query("UPDATE sys_message SET read_flag = 1, read_time = ?, update_time = ? WHERE id = ?")
        .bind(now_text()).bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(())
}

pub async fn mark_all(state: &AppState, actor: &AuthUser) -> Result<(), AppError> {
    let now = now_text();
    sqlx::query("UPDATE sys_message SET read_flag = 1, read_time = ?, update_time = ? WHERE deleted = 0 AND receiver_id = ? AND coalesce(read_flag, 0) = 0")
        .bind(&now).bind(&now).bind(actor.id).execute(&state.db).await?;
    Ok(())
}

pub async fn message_delete(state: &AppState, actor: &AuthUser, id: i64) -> Result<(), AppError> {
    let row = message_row(state, id).await?;
    if row.receiver_id != actor.id { return Err(AppError::forbidden("不能操作他人的消息")); }
    sqlx::query("UPDATE sys_message SET deleted = 1, update_time = ? WHERE id = ?").bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(())
}

pub async fn dashboard(state: &AppState, actor: &AuthUser) -> Result<Value, AppError> {
    let visible = scope::visible_user_ids(state, actor).await?;
    let start = util::today_start();
    let (user_count, today_login, error_count, role_count) = if let Some(ids) = &visible {
        if ids.is_empty() {
            (0, 0, 0, 0)
        } else {
            (
                count_in(state, "SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND id IN (", ids).await?,
                count_in_with_time(state, &start, ids).await?,
                count_in(state, "SELECT COUNT(*) FROM sys_error_log WHERE user_id IN (", ids).await?,
                count_in(state, "SELECT COUNT(DISTINCT ur.role_id) FROM sys_user_role ur WHERE ur.user_id IN (", ids).await?,
            )
        }
    } else {
        (
            sqlx::query_scalar("SELECT COUNT(*) FROM sys_user WHERE deleted = 0").fetch_one(&state.db).await?,
            sqlx::query_scalar("SELECT COUNT(*) FROM sys_login_log WHERE login_time >= ?").bind(&start).fetch_one(&state.db).await?,
            sqlx::query_scalar("SELECT COUNT(*) FROM sys_error_log").fetch_one(&state.db).await?,
            sqlx::query_scalar("SELECT COUNT(*) FROM sys_role WHERE deleted = 0").fetch_one(&state.db).await?,
        )
    };
    Ok(json!({
        "userCount": user_count,
        "roleCount": role_count,
        "todayLoginCount": today_login,
        "errorCount": error_count
    }))
}

async fn count_in(state: &AppState, prefix: &str, ids: &[i64]) -> Result<i64, AppError> {
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new(prefix);
    let mut sep = qb.separated(", ");
    for id in ids { sep.push_bind(*id); }
    sep.push_unseparated(")");
    Ok(qb.build_query_scalar().fetch_one(&state.db).await?)
}

async fn count_in_with_time(state: &AppState, start: &str, ids: &[i64]) -> Result<i64, AppError> {
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT COUNT(*) FROM sys_login_log WHERE login_time >= ");
    qb.push_bind(start.to_string());
    qb.push(" AND user_id IN (");
    let mut sep = qb.separated(", ");
    for id in ids { sep.push_bind(*id); }
    sep.push_unseparated(")");
    Ok(qb.build_query_scalar().fetch_one(&state.db).await?)
}

#[derive(sqlx::FromRow)]
struct ConfigRow { id: i64, config_key: String, config_value: Option<String>, config_type: Option<String>, group_code: Option<String>, remark: Option<String>, create_by: Option<i64>, create_time: Option<String>, update_by: Option<i64>, update_time: Option<String> }
#[derive(sqlx::FromRow)]
struct DictTypeRow { id: i64, name: String, code: String, status: Option<i64>, remark: Option<String>, create_by: Option<i64>, create_time: Option<String>, update_by: Option<i64>, update_time: Option<String> }
#[derive(sqlx::FromRow)]
struct DictDataRow { id: i64, dict_type_id: i64, label: String, value: String, sort: Option<i64>, status: Option<i64>, remark: Option<String>, create_by: Option<i64>, create_time: Option<String>, update_by: Option<i64>, update_time: Option<String> }
#[derive(Clone, sqlx::FromRow)]
struct NoticeRow { id: i64, title: String, content: Option<String>, #[sqlx(rename = "type")] typ: Option<i64>, status: Option<i64>, pinned: Option<i64>, publish_time: Option<String>, remark: Option<String>, create_by: Option<i64>, create_time: Option<String>, update_by: Option<i64>, update_time: Option<String> }
#[derive(sqlx::FromRow)]
struct MessageRow { id: i64, title: String, content: Option<String>, sender_id: Option<i64>, sender_name: Option<String>, receiver_id: i64, read_flag: Option<i64>, read_time: Option<String>, remark: Option<String>, create_by: Option<i64>, create_time: Option<String>, update_by: Option<i64>, update_time: Option<String> }

fn config_vo(r: ConfigRow) -> Value { json!({"id": r.id, "configKey": r.config_key, "configValue": r.config_value, "configType": r.config_type, "groupCode": r.group_code, "remark": r.remark, "createBy": r.create_by, "createTime": util::normalize_dt(r.create_time), "updateBy": r.update_by, "updateTime": util::normalize_dt(r.update_time)}) }
fn dict_type_vo(r: DictTypeRow) -> Value { json!({"id": r.id, "name": r.name, "code": r.code, "status": r.status.unwrap_or(0), "remark": r.remark, "createBy": r.create_by, "createTime": util::normalize_dt(r.create_time), "updateBy": r.update_by, "updateTime": util::normalize_dt(r.update_time)}) }
fn dict_data_vo(r: DictDataRow) -> Value { json!({"id": r.id, "dictTypeId": r.dict_type_id, "label": r.label, "value": r.value, "sort": r.sort.unwrap_or(0), "status": r.status.unwrap_or(0), "remark": r.remark, "createBy": r.create_by, "createTime": util::normalize_dt(r.create_time), "updateBy": r.update_by, "updateTime": util::normalize_dt(r.update_time)}) }
fn notice_vo(r: NoticeRow) -> Value { json!({"id": r.id, "title": r.title, "content": r.content, "type": r.typ.unwrap_or(1), "status": r.status.unwrap_or(0), "pinned": r.pinned.unwrap_or(0), "publishTime": util::normalize_dt(r.publish_time), "remark": r.remark, "createBy": r.create_by, "createTime": util::normalize_dt(r.create_time), "updateBy": r.update_by, "updateTime": util::normalize_dt(r.update_time)}) }
fn message_vo(r: MessageRow) -> Value { json!({"id": r.id, "title": r.title, "content": r.content, "senderId": r.sender_id, "senderName": r.sender_name, "receiverId": r.receiver_id, "readFlag": r.read_flag.unwrap_or(0), "readTime": util::normalize_dt(r.read_time), "remark": r.remark, "createBy": r.create_by, "createTime": util::normalize_dt(r.create_time), "updateBy": r.update_by, "updateTime": util::normalize_dt(r.update_time)}) }

fn push_page(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, page: &PageParams) {
    qb.push(" LIMIT ");
    qb.push_bind(page.size());
    qb.push(" OFFSET ");
    qb.push_bind(page.offset());
}

async fn count(state: &AppState, table: &str, filters: impl FnOnce(&mut QueryBuilder<'_, sqlx::Sqlite>)) -> Result<i64, AppError> {
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new(format!("SELECT COUNT(*) FROM {table} WHERE deleted = 0"));
    filters(&mut qb);
    Ok(qb.build_query_scalar().fetch_one(&state.db).await?)
}

fn push_config(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &ConfigQuery) {
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(config_key) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(config_value,'')) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(group) = blank_to_none(query.group_code.clone()) {
        qb.push(" AND group_code = ");
        qb.push_bind(group);
    }
    if let Some(typ) = blank_to_none(query.config_type.clone()) {
        qb.push(" AND config_type = ");
        qb.push_bind(typ);
    }
}

fn validate_config(form: &ConfigForm) -> Result<String, AppError> {
    let key = require_text(&form.config_key, "配置键不能为空")?;
    max_chars(&key, 100, "配置键最长 100 个字符")?;
    if let Some(value) = &form.config_value { max_chars(value, 500, "配置值最长 500 个字符")?; }
    opt_max(&form.remark, 500, "备注最长 500 个字符")?;
    Ok(key)
}

async fn key_exists(state: &AppState, key: &str, except: Option<i64>) -> Result<bool, AppError> {
    let count: i64 = if let Some(id) = except {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_config WHERE deleted = 0 AND config_key = ? AND id <> ?").bind(key).bind(id).fetch_one(&state.db).await?
    } else {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_config WHERE deleted = 0 AND config_key = ?").bind(key).fetch_one(&state.db).await?
    };
    Ok(count > 0)
}

async fn config_row(state: &AppState, id: i64) -> Result<ConfigRow, AppError> {
    sqlx::query_as("SELECT id, config_key, config_value, config_type, group_code, remark, create_by, create_time, update_by, update_time FROM sys_config WHERE id = ? AND deleted = 0")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("配置不存在"))
}
async fn config_by_id(state: &AppState, id: i64) -> Result<Value, AppError> { Ok(config_vo(config_row(state, id).await?)) }

fn push_dict_type(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &DictTypeQuery) {
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(name) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(code) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(status) = query.status { qb.push(" AND status = "); qb.push_bind(status); }
}

fn validate_dict_type(form: &DictTypeForm) -> Result<(String, String), AppError> {
    let name = require_text(&form.name, "字典名称不能为空")?;
    max_chars(&name, 50, "字典名称最长 50 个字符")?;
    let code = require_text(&form.code, "字典编码不能为空")?;
    max_chars(&code, 50, "字典编码最长 50 个字符")?;
    util::matches(&code, r"^[a-z][a-z0-9_]*$", "字典编码需为小写字母、数字、下划线,且以字母开头")?;
    opt_max(&form.remark, 500, "备注最长 500 个字符")?;
    Ok((name, code))
}

async fn dict_code_exists(state: &AppState, code: &str, except: Option<i64>) -> Result<bool, AppError> {
    let count: i64 = if let Some(id) = except {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_dict_type WHERE deleted = 0 AND code = ? AND id <> ?").bind(code).bind(id).fetch_one(&state.db).await?
    } else {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_dict_type WHERE deleted = 0 AND code = ?").bind(code).fetch_one(&state.db).await?
    };
    Ok(count > 0)
}

async fn dict_type_row(state: &AppState, id: i64) -> Result<DictTypeRow, AppError> {
    sqlx::query_as("SELECT id, name, code, status, remark, create_by, create_time, update_by, update_time FROM sys_dict_type WHERE id = ? AND deleted = 0")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("字典类型不存在"))
}

fn push_dict_data(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &DictDataQuery, type_id: i64) {
    qb.push(" AND dict_type_id = ");
    qb.push_bind(type_id);
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(label) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(value) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(status) = query.status { qb.push(" AND status = "); qb.push_bind(status); }
}

fn validate_dict_data(form: &DictDataForm) -> Result<(i64, String, String), AppError> {
    let type_id = form.dict_type_id.ok_or_else(|| AppError::bad("字典类型不能为空"))?;
    let label = require_text(&form.label, "字典标签不能为空")?;
    max_chars(&label, 100, "字典标签最长 100 个字符")?;
    let value = require_text(&form.value, "字典值不能为空")?;
    max_chars(&value, 100, "字典值最长 100 个字符")?;
    opt_max(&form.remark, 500, "备注最长 500 个字符")?;
    Ok((type_id, label, value))
}

async fn dict_value_exists(state: &AppState, type_id: i64, value: &str, except: Option<i64>) -> Result<bool, AppError> {
    let count: i64 = if let Some(id) = except {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_dict_data WHERE deleted = 0 AND dict_type_id = ? AND value = ? AND id <> ?").bind(type_id).bind(value).bind(id).fetch_one(&state.db).await?
    } else {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_dict_data WHERE deleted = 0 AND dict_type_id = ? AND value = ?").bind(type_id).bind(value).fetch_one(&state.db).await?
    };
    Ok(count > 0)
}

async fn dict_data_row(state: &AppState, id: i64) -> Result<DictDataRow, AppError> {
    sqlx::query_as("SELECT id, dict_type_id, label, value, sort, status, remark, create_by, create_time, update_by, update_time FROM sys_dict_data WHERE id = ? AND deleted = 0")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("字典数据不存在"))
}

fn evict_dict(state: &AppState, code: &str) {
    if !code.is_empty() { state.cache.delete(&format!("s2admin:dict:{code}")); }
}

fn push_notice(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &NoticeQuery) {
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        qb.push(" AND lower(title) LIKE ");
        qb.push_bind(like_pat(&keyword));
    }
    if let Some(status) = query.status { qb.push(" AND status = "); qb.push_bind(status); }
    if let Some(typ) = query.typ { qb.push(" AND type = "); qb.push_bind(typ); }
}

fn validate_notice(form: &NoticeForm) -> Result<String, AppError> {
    let title = require_text(&form.title, "标题不能为空")?;
    max_chars(&title, 200, "标题最长 200 个字符")?;
    opt_max(&form.remark, 500, "备注最长 500 个字符")?;
    Ok(title)
}

async fn notice_row(state: &AppState, id: i64) -> Result<NoticeRow, AppError> {
    sqlx::query_as("SELECT id, title, content, type, status, pinned, publish_time, remark, create_by, create_time, update_by, update_time FROM sys_notice WHERE id = ? AND deleted = 0")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("公告不存在"))
}

fn push_message(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &MessageQuery) {
    if let Some(flag) = query.read_flag { qb.push(" AND read_flag = "); qb.push_bind(flag); }
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(title) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(content,'')) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
}

async fn message_row(state: &AppState, id: i64) -> Result<MessageRow, AppError> {
    sqlx::query_as("SELECT id, title, content, sender_id, sender_name, receiver_id, read_flag, read_time, remark, create_by, create_time, update_by, update_time FROM sys_message WHERE id = ? AND deleted = 0")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("消息不存在"))
}
