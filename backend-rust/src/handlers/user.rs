use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::QueryBuilder;

use crate::db::last_id;
use crate::error::AppError;
use crate::handlers::auth::{self, email_ok, email_taken, phone_ok, phone_taken, username_ok};
use crate::password;
use crate::response::Page;
use crate::scope::{self, Scope};
use crate::security::{self, SUPER_ADMIN};
use crate::state::{AppState, AuthUser};
use crate::util::{self, blank_to_none, csv_escape, like_pat, lower_email, max_chars, now_text, opt_max, require_text, tombstone, PageParams};
use crate::xlsx;

const MAX_EXPORT: i64 = 10_000;
const MAX_IMPORT: i64 = 2_000;
const EXPORT_FIELDS: &[&str] = &["username", "nickname", "email", "phone", "deptName", "status"];

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UserQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub keyword: Option<String>,
    pub status: Option<i64>,
    pub dept_id: Option<i64>,
    pub role_id: Option<i64>,
    pub begin_time: Option<String>,
    pub end_time: Option<String>,
    pub fields: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UserForm {
    pub username: Option<String>,
    pub password: Option<String>,
    pub nickname: Option<String>,
    pub email: Option<String>,
    pub phone: Option<String>,
    pub avatar: Option<String>,
    pub dept_id: Option<i64>,
    pub dept_name: Option<String>,
    pub status: Option<i64>,
    pub role_ids: Option<Vec<i64>>,
    pub province: Option<String>,
    pub city: Option<String>,
    pub district: Option<String>,
    pub remark: Option<String>,
}

#[derive(sqlx::FromRow)]
struct UserRow {
    id: i64,
    username: String,
    nickname: Option<String>,
    email: Option<String>,
    phone: Option<String>,
    avatar: Option<String>,
    status: Option<i64>,
    dept_id: Option<i64>,
    dept_name: Option<String>,
    province: Option<String>,
    city: Option<String>,
    district: Option<String>,
    remark: Option<String>,
    pwd_reset: Option<i64>,
    create_by: Option<i64>,
    create_time: Option<String>,
    update_time: Option<String>,
}

pub async fn page(state: &AppState, actor: &AuthUser, query: &UserQuery) -> Result<Page<Value>, AppError> {
    let scope = scope::current(state, actor).await?;
    let total = count_users(state, actor, query, &scope).await?;
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new(
        "SELECT id, username, nickname, email, phone, avatar, status, dept_id, dept_name, province, city, district, remark, pwd_reset, create_by, create_time, update_time FROM sys_user u WHERE u.deleted = 0",
    );
    push_filters(&mut qb, actor, query, &scope);
    let order = query.page.order_sql(
        &[
            ("id", "u.id"),
            ("username", "u.username"),
            ("nickname", "u.nickname"),
            ("email", "u.email"),
            ("phone", "u.phone"),
            ("status", "u.status"),
            ("createTime", "u.create_time"),
            ("deptName", "u.dept_name"),
        ],
        "u.id",
        true,
    );
    qb.push(order);
    qb.push(" LIMIT ");
    qb.push_bind(query.page.size());
    qb.push(" OFFSET ");
    qb.push_bind(query.page.offset());
    let rows: Vec<UserRow> = qb.build_query_as().fetch_all(&state.db).await?;
    let mut records = Vec::new();
    for row in rows {
        records.push(to_vo(state, row).await?);
    }
    Ok(Page::new(records, total, query.page.size(), query.page.num()))
}

pub async fn get_by_id(state: &AppState, actor: &AuthUser, id: i64) -> Result<Value, AppError> {
    let row = load(state, id).await?;
    scope::assert_can_access_user(state, actor, row.id, row.dept_id).await?;
    to_vo(state, row).await
}

pub async fn create(state: &AppState, actor: &AuthUser, mut form: UserForm) -> Result<Value, AppError> {
    validate_form(&form, true)?;
    resolve_dept(state, actor, &mut form, true).await?;
    let username = form.username.clone().unwrap_or_default();
    if username_exists(state, &username).await? {
        return Err(AppError::bad("用户名已存在"));
    }
    let email = lower_email(form.email.clone());
    let phone = blank_to_none(form.phone.clone());
    if let Some(email) = &email {
        if email_taken(state, email, None).await? {
            return Err(AppError::bad("邮箱已被使用"));
        }
    }
    if let Some(phone) = &phone {
        if phone_taken(state, phone, None).await? {
            return Err(AppError::bad("手机号已被使用"));
        }
    }
    if let Some(password) = blank_to_none(form.password.clone()) {
        password::validate_length_then_policy(&password)?;
    }
    scope::assert_can_assign_dept(state, actor, form.dept_id).await?;
    let custom = blank_to_none(form.password.clone());
    let raw = custom.clone().unwrap_or_else(password::random_strong);
    let hash = password::hash(&raw)?;
    let pwd_reset = if custom.is_some() { 0 } else { 1 };
    let roles = resolve_roles(state, form.role_ids.clone()).await?;
    assert_can_assign_roles(state, actor, None, &roles).await?;
    let (dept_id, dept_name) = dept_pair(state, form.dept_id, form.dept_name.clone()).await?;
    let now = now_text();
    let mut tx = state.db.begin().await?;
    sqlx::query(
        "INSERT INTO sys_user (username, password, nickname, email, phone, avatar, status, dept_id, dept_name, province, city, district, remark, pwd_reset, deleted, create_by, create_time, update_by, update_time)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)",
    )
    .bind(&username)
    .bind(hash)
    .bind(require_text(&form.nickname, "昵称不能为空")?)
    .bind(email)
    .bind(phone)
    .bind(blank_to_none(form.avatar.clone()))
    .bind(normalize_status(form.status)?)
    .bind(dept_id)
    .bind(dept_name)
    .bind(blank_to_none(form.province.clone()))
    .bind(blank_to_none(form.city.clone()))
    .bind(blank_to_none(form.district.clone()))
    .bind(blank_to_none(form.remark.clone()))
    .bind(pwd_reset)
    .bind(actor.id)
    .bind(&now)
    .bind(actor.id)
    .bind(&now)
    .execute(&mut *tx)
    .await?;
    let id = last_id(&mut *tx).await?;
    replace_roles(&mut tx, id, &roles).await?;
    tx.commit().await?;
    get_by_id(state, actor, id).await
}

pub async fn update(state: &AppState, actor: &AuthUser, id: i64, form: UserForm) -> Result<Value, AppError> {
    validate_form(&form, false)?;
    let existing = load(state, id).await?;
    scope::assert_can_access_user(state, actor, existing.id, existing.dept_id).await?;
    assert_can_edit_user(state, actor, id).await?;
    if form.status.is_some_and(|s| s != 0) && id == actor.id {
        return Err(AppError::bad("不能停用当前登录用户"));
    }
    let mut form = form;
    resolve_dept(state, actor, &mut form, false).await?;
    let scope = scope::current(state, actor).await?;
    if form.dept_id.is_none() && existing.dept_id.is_some() && !scope.all {
        return Err(AppError::bad("不能清空该用户的部门"));
    }
    if form.dept_id.is_some() && form.dept_id != existing.dept_id {
        scope::assert_can_assign_dept(state, actor, form.dept_id).await?;
    }
    if let Some(username) = blank_to_none(form.username.clone()) {
        if username != existing.username {
            return Err(AppError::bad("用户名创建后不允许修改"));
        }
    }
    let email = lower_email(form.email.clone());
    let phone = blank_to_none(form.phone.clone());
    if let Some(email) = &email {
        if email_taken(state, email, Some(id)).await? {
            return Err(AppError::bad("邮箱已被使用"));
        }
    }
    if let Some(phone) = &phone {
        if phone_taken(state, phone, Some(id)).await? {
            return Err(AppError::bad("手机号已被使用"));
        }
    }
    let previous = existing.status.unwrap_or(0);
    let (dept_id, dept_name) = if form.dept_id.is_some() {
        dept_pair(state, form.dept_id, form.dept_name.clone()).await?
    } else {
        (None, blank_to_none(form.dept_name.clone()))
    };
    let mut status = existing.status;
    if let Some(next) = form.status {
        status = Some(normalize_status(Some(next))?);
    }
    let now = now_text();
    sqlx::query(
        "UPDATE sys_user SET nickname = ?, email = ?, phone = ?, avatar = CASE WHEN ? THEN ? ELSE avatar END,
         dept_id = ?, dept_name = ?, status = ?, province = ?, city = ?, district = ?, remark = ?, update_by = ?, update_time = ?
         WHERE id = ? AND deleted = 0",
    )
    .bind(require_text(&form.nickname, "昵称不能为空")?)
    .bind(&email)
    .bind(&phone)
    .bind(form.avatar.is_some())
    .bind(blank_to_none(form.avatar.clone()))
    .bind(dept_id)
    .bind(&dept_name)
    .bind(status)
    .bind(blank_to_none(form.province.clone()))
    .bind(blank_to_none(form.city.clone()))
    .bind(blank_to_none(form.district.clone()))
    .bind(blank_to_none(form.remark.clone()))
    .bind(actor.id)
    .bind(&now)
    .bind(id)
    .execute(&state.db)
    .await?;
    if form.status.is_some() && previous != status.unwrap_or(0) {
        auth::clear_auto_lock(state, id);
        if status.unwrap_or(0) != 0 {
            security::invalidate_user(state, id);
        }
    }
    if let Some(password) = blank_to_none(form.password.clone()) {
        password::validate_length_then_policy(&password)?;
        let hash = password::hash(&password)?;
        sqlx::query("UPDATE sys_user SET password = ?, pwd_reset = 1, update_time = ? WHERE id = ?")
            .bind(hash)
            .bind(&now)
            .bind(id)
            .execute(&state.db)
            .await?;
        security::invalidate_user(state, id);
    }
    if let Some(role_ids) = form.role_ids.clone() {
        let roles = resolve_roles(state, Some(role_ids)).await?;
        assert_can_assign_roles(state, actor, Some(id), &roles).await?;
        assert_keep_last_super(state, id, &roles).await?;
        let mut tx = state.db.begin().await?;
        replace_roles(&mut tx, id, &roles).await?;
        tx.commit().await?;
        security::invalidate_user(state, id);
    }
    security::evict_user(state, id);
    let row = load(state, id).await?;
    to_vo(state, row).await
}

pub async fn delete(state: &AppState, actor: &AuthUser, id: i64) -> Result<(), AppError> {
    let user = load(state, id).await?;
    scope::assert_can_access_user(state, actor, user.id, user.dept_id).await?;
    if user.id == actor.id {
        return Err(AppError::bad("不能删除当前登录用户"));
    }
    if user.username == "admin" {
        return Err(AppError::bad("内置管理员不允许删除"));
    }
    assert_can_edit_user(state, actor, id).await?;
    let username = tombstone(&user.username, id, 50);
    let email = user.email.as_deref().map(|v| tombstone(v, id, 100));
    let phone = user.phone.as_deref().map(|v| tombstone(v, id, 20));
    let now = now_text();
    let mut tx = state.db.begin().await?;
    sqlx::query("UPDATE sys_user SET username = ?, email = ?, phone = ?, deleted = 1, update_by = ?, update_time = ? WHERE id = ?")
        .bind(username)
        .bind(email)
        .bind(phone)
        .bind(actor.id)
        .bind(&now)
        .bind(id)
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM sys_user_role WHERE user_id = ?").bind(id).execute(&mut *tx).await?;
    tx.commit().await?;
    security::evict_user(state, id);
    security::invalidate_user(state, id);
    Ok(())
}

pub async fn batch_delete(state: &AppState, actor: &AuthUser, ids: &[i64]) -> Result<(), AppError> {
    for id in ids {
        delete(state, actor, *id).await?;
    }
    Ok(())
}

pub async fn update_status(state: &AppState, actor: &AuthUser, id: i64, status: Option<i64>) -> Result<(), AppError> {
    let user = load(state, id).await?;
    scope::assert_can_access_user(state, actor, user.id, user.dept_id).await?;
    let status = normalize_status(status)?;
    if id == actor.id && status != 0 {
        return Err(AppError::bad("不能停用当前登录用户"));
    }
    assert_can_edit_user(state, actor, id).await?;
    sqlx::query("UPDATE sys_user SET status = ?, update_by = ?, update_time = ? WHERE id = ? AND deleted = 0")
        .bind(status)
        .bind(actor.id)
        .bind(now_text())
        .bind(id)
        .execute(&state.db)
        .await?;
    auth::clear_auto_lock(state, id);
    security::evict_user(state, id);
    if status != 0 {
        security::invalidate_user(state, id);
    }
    Ok(())
}

pub async fn reset_password(state: &AppState, actor: &AuthUser, id: i64, new_password: &str) -> Result<(), AppError> {
    password::validate_length_then_policy(new_password)?;
    let user = load(state, id).await?;
    scope::assert_can_access_user(state, actor, user.id, user.dept_id).await?;
    assert_can_edit_user(state, actor, id).await?;
    let hash = password::hash(new_password)?;
    sqlx::query("UPDATE sys_user SET password = ?, pwd_reset = 1, update_by = ?, update_time = ? WHERE id = ?")
        .bind(hash)
        .bind(actor.id)
        .bind(now_text())
        .bind(id)
        .execute(&state.db)
        .await?;
    security::evict_user(state, id);
    security::invalidate_user(state, id);
    Ok(())
}

pub async fn export_csv(state: &AppState, actor: &AuthUser, query: &UserQuery) -> Result<String, AppError> {
    let rows = export_rows(state, actor, query).await?;
    let cols = resolve_fields(query.fields.as_deref());
    let mut sb = String::from('\u{feff}');
    sb.push_str(&cols.join(","));
    sb.push('\n');
    for row in rows {
        let vals: Vec<String> = cols.iter().map(|c| csv_escape(&export_value(&row, c))).collect();
        sb.push_str(&vals.join(","));
        sb.push('\n');
    }
    Ok(sb)
}

pub fn import_template() -> String {
    "\u{feff}username,nickname,email,phone,deptName,status\nadmin_demo,示例用户,demo@example.com,13800138000,研发部,0\n".into()
}

pub async fn import_csv(state: &AppState, actor: &AuthUser, text: &str) -> Result<Value, AppError> {
    let mut created = 0;
    let mut skipped = 0;
    let mut errors = Vec::new();
    for (idx, raw) in text.lines().enumerate() {
        let line_no = idx as i64 + 1;
        let mut line = raw.to_string();
        if line.starts_with('\u{feff}') {
            line = line.trim_start_matches('\u{feff}').to_string();
        }
        if line_no > MAX_IMPORT + 1 {
            errors.push(format!("超过最大导入行数 {MAX_IMPORT},后续行已忽略"));
            break;
        }
        if line_no == 1 && line.to_lowercase().contains("username") {
            continue;
        }
        if line.trim().is_empty() {
            continue;
        }
        let cols = util::parse_csv_line(&line);
        if cols.first().map(|s| s.trim().is_empty()).unwrap_or(true) {
            skipped += 1;
            errors.push(format!("第 {line_no} 行缺少用户名"));
            continue;
        }
        match import_one(state, actor, &cols).await {
            Ok(()) => created += 1,
            Err(err) => {
                skipped += 1;
                errors.push(format!("第 {line_no} 行: {}", import_message(&err)));
            }
        }
    }
    Ok(json!({"created": created, "skipped": skipped, "errors": errors}))
}

pub async fn export_xlsx(state: &AppState, actor: &AuthUser, query: &UserQuery) -> Result<Vec<u8>, AppError> {
    let rows = export_rows(state, actor, query).await?;
    let cols = resolve_fields(query.fields.as_deref());
    let data: Vec<Vec<String>> = rows.iter().map(|row| cols.iter().map(|c| export_value(row, c)).collect()).collect();
    let headers: Vec<String> = cols.into_iter().map(|s| s.to_string()).collect();
    xlsx::write_xlsx(&headers, &data).map_err(|_| AppError::bad("导出 Excel 失败"))
}

pub async fn import_xlsx(state: &AppState, actor: &AuthUser, bytes: &[u8]) -> Result<Value, AppError> {
    let rows = xlsx::read_xlsx(bytes).map_err(|_| AppError::bad("解析 Excel 失败"))?;
    let mut created = 0;
    let mut skipped = 0;
    let mut errors = Vec::new();
    for (idx, row) in rows.into_iter().enumerate() {
        let line_no = idx as i64 + 1;
        if line_no > MAX_IMPORT + 1 {
            errors.push(format!("超过最大导入行数 {MAX_IMPORT},后续行已忽略"));
            break;
        }
        if line_no == 1 && row.iter().any(|c| c.to_lowercase().contains("username")) {
            continue;
        }
        if row.first().map(|s| s.trim().is_empty()).unwrap_or(true) {
            skipped += 1;
            errors.push(format!("第 {line_no} 行缺少用户名"));
            continue;
        }
        match import_one(state, actor, &row).await {
            Ok(()) => created += 1,
            Err(err) => {
                skipped += 1;
                errors.push(format!("第 {line_no} 行: {}", import_message(&err)));
            }
        }
    }
    Ok(json!({"created": created, "skipped": skipped, "errors": errors}))
}

async fn import_one(state: &AppState, actor: &AuthUser, cols: &[String]) -> Result<(), AppError> {
    let username = cols.first().map(|s| s.trim().to_string()).unwrap_or_default();
    if username_exists(state, &username).await? {
        return Err(AppError::bad(format!("用户名已存在: {username}")));
    }
    let form = UserForm {
        username: Some(username.clone()),
        password: None,
        nickname: Some(if cols.get(1).is_some_and(|s| !s.trim().is_empty()) { cols[1].trim().to_string() } else { username }),
        email: cols.get(2).cloned(),
        phone: cols.get(3).cloned(),
        avatar: None,
        dept_id: None,
        dept_name: cols.get(4).cloned(),
        status: cols.get(5).and_then(|s| s.trim().parse().ok()).or(Some(0)),
        role_ids: None,
        province: None,
        city: None,
        district: None,
        remark: None,
    };
    validate_imported(&form)?;
    create(state, actor, form).await?;
    Ok(())
}

fn import_message(err: &AppError) -> String {
    match err {
        AppError::Biz { message, .. } => message.clone(),
        AppError::Internal(_) => "导入失败".into(),
    }
}

fn validate_imported(form: &UserForm) -> Result<(), AppError> {
    let username = form.username.as_deref().unwrap_or("");
    if !username_ok(username) {
        return Err(AppError::bad("用户名只能包含 2-50 位字母、数字和下划线"));
    }
    if let Some(phone) = blank_to_none(form.phone.clone()) {
        if !phone_ok(&phone) {
            return Err(AppError::bad("手机号格式不正确"));
        }
    }
    if let Some(email) = blank_to_none(form.email.clone()) {
        if !email_ok(&email) {
            return Err(AppError::bad("邮箱格式不正确"));
        }
    }
    normalize_status(form.status)?;
    Ok(())
}

async fn export_rows(state: &AppState, actor: &AuthUser, query: &UserQuery) -> Result<Vec<UserRow>, AppError> {
    let scope = scope::current(state, actor).await?;
    let total = count_users(state, actor, query, &scope).await?;
    if total > MAX_EXPORT {
        return Err(AppError::bad(format!("导出条数超过 {MAX_EXPORT},请缩小筛选范围")));
    }
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new(
        "SELECT id, username, nickname, email, phone, avatar, status, dept_id, dept_name, province, city, district, remark, pwd_reset, create_by, create_time, update_time FROM sys_user u WHERE u.deleted = 0",
    );
    push_filters(&mut qb, actor, query, &scope);
    Ok(qb.build_query_as().fetch_all(&state.db).await?)
}

fn resolve_fields(fields: Option<&str>) -> Vec<String> {
    let Some(fields) = fields.filter(|s| !s.trim().is_empty()) else {
        return EXPORT_FIELDS.iter().map(|s| (*s).to_string()).collect();
    };
    let mut cols = Vec::new();
    for raw in fields.split(',') {
        let col = raw.trim();
        if EXPORT_FIELDS.contains(&col) && !cols.iter().any(|c| c == col) {
            cols.push(col.to_string());
        }
    }
    if cols.is_empty() {
        EXPORT_FIELDS.iter().map(|s| (*s).to_string()).collect()
    } else {
        cols
    }
}

fn export_value(row: &UserRow, col: &str) -> String {
    match col {
        "username" => row.username.clone(),
        "nickname" => row.nickname.clone().unwrap_or_default(),
        "email" => row.email.clone().unwrap_or_default(),
        "phone" => row.phone.clone().unwrap_or_default(),
        "deptName" => row.dept_name.clone().unwrap_or_default(),
        "status" => row.status.unwrap_or(0).to_string(),
        _ => String::new(),
    }
}

fn push_filters(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, actor: &AuthUser, query: &UserQuery, scope: &Scope) {
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(u.username) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(u.nickname,'')) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(u.email,'')) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(coalesce(u.phone,'')) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(status) = query.status {
        qb.push(" AND u.status = ");
        qb.push_bind(status);
    }
    if let Some(dept_id) = query.dept_id {
        qb.push(" AND u.dept_id = ");
        qb.push_bind(dept_id);
    }
    if let Some(role_id) = query.role_id {
        qb.push(" AND EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = ");
        qb.push_bind(role_id);
        qb.push(")");
    }
    if let Some(begin) = blank_to_none(query.begin_time.clone()) {
        qb.push(" AND u.create_time >= ");
        qb.push_bind(begin);
    }
    if let Some(end) = blank_to_none(query.end_time.clone()) {
        qb.push(" AND u.create_time <= ");
        qb.push_bind(end);
    }
    scope::push_user_scope(qb, scope, actor.id, "u");
}

async fn count_users(state: &AppState, actor: &AuthUser, query: &UserQuery, scope: &Scope) -> Result<i64, AppError> {
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT COUNT(*) FROM sys_user u WHERE u.deleted = 0");
    push_filters(&mut qb, actor, query, scope);
    Ok(qb.build_query_scalar().fetch_one(&state.db).await?)
}

async fn load(state: &AppState, id: i64) -> Result<UserRow, AppError> {
    sqlx::query_as(
        "SELECT id, username, nickname, email, phone, avatar, status, dept_id, dept_name, province, city, district, remark, pwd_reset, create_by, create_time, update_time FROM sys_user WHERE id = ? AND deleted = 0",
    )
    .bind(id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::not_found("用户不存在"))
}

async fn to_vo(state: &AppState, row: UserRow) -> Result<Value, AppError> {
    let roles: Vec<(i64, String)> = sqlx::query_as(
        "SELECT r.id, r.name FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ? AND r.deleted = 0 ORDER BY r.sort, r.id",
    )
    .bind(row.id)
    .fetch_all(&state.db)
    .await?;
    Ok(json!({
        "id": row.id,
        "username": row.username,
        "nickname": row.nickname,
        "email": row.email,
        "phone": row.phone,
        "avatar": row.avatar,
        "status": row.status.unwrap_or(0),
        "deptId": row.dept_id,
        "deptName": row.dept_name,
        "province": row.province,
        "city": row.city,
        "district": row.district,
        "remark": row.remark,
        "pwdReset": row.pwd_reset.unwrap_or(0),
        "createBy": row.create_by,
        "createTime": util::normalize_dt(row.create_time),
        "updateTime": util::normalize_dt(row.update_time),
        "roleIds": roles.iter().map(|r| r.0).collect::<Vec<_>>(),
        "roleNames": roles.iter().map(|r| r.1.clone()).collect::<Vec<_>>()
    }))
}

fn validate_form(form: &UserForm, creating: bool) -> Result<(), AppError> {
    if creating {
        let username = require_text(&form.username, "用户名不能为空")?;
        max_chars(&username, 50, "用户名长度需在 2-50 之间")?;
        if username.chars().count() < 2 {
            return Err(AppError::bad("用户名长度需在 2-50 之间"));
        }
        util::matches(&username, r"^[a-zA-Z0-9_]+$", "用户名只能包含字母、数字和下划线")?;
    }
    let nickname = require_text(&form.nickname, "昵称不能为空")?;
    max_chars(&nickname, 50, "昵称最长 50 个字符")?;
    if let Some(email) = lower_email(form.email.clone()) {
        max_chars(&email, 100, "邮箱最长 100 个字符")?;
        if !email_ok(&email) {
            return Err(AppError::bad("邮箱格式不正确"));
        }
    }
    if let Some(phone) = blank_to_none(form.phone.clone()) {
        max_chars(&phone, 20, "手机号最长 20 个字符")?;
        if !phone_ok(&phone) {
            return Err(AppError::bad("手机号格式不正确"));
        }
    }
    if let Some(password) = blank_to_none(form.password.clone()) {
        let len = password.chars().count();
        if !(8..=64).contains(&len) {
            return Err(AppError::bad("密码长度需在 8-64 之间"));
        }
    }
    opt_max(&form.remark, 500, "备注最长 500 个字符")?;
    opt_max(&form.province, 50, "备注最长 500 个字符")?;
    opt_max(&form.city, 50, "备注最长 500 个字符")?;
    opt_max(&form.district, 50, "备注最长 500 个字符")?;
    Ok(())
}

fn normalize_status(status: Option<i64>) -> Result<i64, AppError> {
    let value = status.unwrap_or(0);
    if !(0..=3).contains(&value) {
        Err(AppError::bad("状态值不合法"))
    } else {
        Ok(value)
    }
}

async fn username_exists(state: &AppState, username: &str) -> Result<bool, AppError> {
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND username = ?")
        .bind(username)
        .fetch_one(&state.db)
        .await?;
    Ok(count > 0)
}

async fn resolve_dept(state: &AppState, actor: &AuthUser, form: &mut UserForm, creating: bool) -> Result<(), AppError> {
    if form.dept_id.is_some() {
        return Ok(());
    }
    if let Some(name) = blank_to_none(form.dept_name.clone()) {
        let matches: Vec<i64> = sqlx::query_scalar("SELECT id FROM sys_dept WHERE deleted = 0 AND name = ?")
            .bind(&name)
            .fetch_all(&state.db)
            .await?;
        if matches.is_empty() {
            return Err(AppError::bad(format!("部门不存在: {name}")));
        }
        if matches.len() > 1 {
            return Err(AppError::bad(format!("部门名称不唯一,请在页面指定部门: {name}")));
        }
        form.dept_id = Some(matches[0]);
        return Ok(());
    }
    if !creating {
        return Ok(());
    }
    let scope = scope::current(state, actor).await?;
    if scope.all || scope.self_only {
        return Ok(());
    }
    let mine: Option<i64> = sqlx::query_scalar("SELECT dept_id FROM sys_user WHERE id = ? AND deleted = 0")
        .bind(actor.id)
        .fetch_optional(&state.db)
        .await?;
    if let Some(id) = mine {
        form.dept_id = Some(id);
    }
    Ok(())
}

async fn dept_pair(state: &AppState, dept_id: Option<i64>, dept_name: Option<String>) -> Result<(Option<i64>, Option<String>), AppError> {
    let Some(dept_id) = dept_id else {
        return Ok((None, blank_to_none(dept_name)));
    };
    let name: Option<String> = sqlx::query_scalar("SELECT name FROM sys_dept WHERE id = ? AND deleted = 0")
        .bind(dept_id)
        .fetch_optional(&state.db)
        .await?
        .ok_or_else(|| AppError::bad("部门不存在"))?;
    Ok((Some(dept_id), name))
}

struct RoleLite {
    id: i64,
    code: String,
    name: String,
    data_scope: Option<i64>,
    status: Option<i64>,
}

async fn resolve_roles(state: &AppState, role_ids: Option<Vec<i64>>) -> Result<Vec<RoleLite>, AppError> {
    let Some(role_ids) = role_ids else {
        return Ok(Vec::new());
    };
    let mut distinct = Vec::new();
    for id in role_ids.into_iter().flatten_ids() {
        if !distinct.contains(&id) {
            distinct.push(id);
        }
    }
    if distinct.is_empty() {
        return Ok(Vec::new());
    }
    let mut found = Vec::new();
    for id in &distinct {
        let row: Option<(i64, String, String, Option<i64>, Option<i64>)> = sqlx::query_as(
            "SELECT id, code, name, data_scope, status FROM sys_role WHERE id = ? AND deleted = 0",
        )
        .bind(id)
        .fetch_optional(&state.db)
        .await?;
        let Some(row) = row else {
            return Err(AppError::bad("部分角色不存在或已删除"));
        };
        found.push(RoleLite { id: row.0, code: row.1, name: row.2, data_scope: row.3, status: row.4 });
    }
    Ok(found)
}

trait FlattenIds {
    fn flatten_ids(self) -> Vec<i64>;
}
impl FlattenIds for std::vec::IntoIter<i64> {
    fn flatten_ids(self) -> Vec<i64> {
        self.collect()
    }
}

async fn assert_can_assign_roles(state: &AppState, actor: &AuthUser, existing_id: Option<i64>, roles: &[RoleLite]) -> Result<(), AppError> {
    if roles.is_empty() || actor.is_privileged() {
        return Ok(());
    }
    let kept = if let Some(id) = existing_id {
        let ids: Vec<i64> = sqlx::query_scalar("SELECT role_id FROM sys_user_role WHERE user_id = ?")
            .bind(id)
            .fetch_all(&state.db)
            .await?;
        ids
    } else {
        Vec::new()
    };
    let mine = security::load_permissions(state, actor.id).await?;
    for role in roles {
        if kept.contains(&role.id) {
            continue;
        }
        if role.code == SUPER_ADMIN {
            return Err(AppError::bad("不允许分配超级管理员角色"));
        }
        scope::assert_can_assign_data_scope(state, actor, role.data_scope).await?;
        let codes: Vec<(String, Option<i64>)> = sqlx::query_as(
            "SELECT p.code, p.status FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id = p.id WHERE rp.role_id = ? AND p.deleted = 0",
        )
        .bind(role.id)
        .fetch_all(&state.db)
        .await?;
        for (code, status) in codes {
            if status.unwrap_or(0) != 0 || code.is_empty() {
                continue;
            }
            if !mine.contains(&code) {
                return Err(AppError::bad(format!("不能分配超出自身权限的角色: {}", role.name)));
            }
        }
    }
    Ok(())
}

async fn assert_can_edit_user(state: &AppState, actor: &AuthUser, user_id: i64) -> Result<(), AppError> {
    if actor.is_super() {
        return Ok(());
    }
    let codes: Vec<String> = sqlx::query_scalar(
        "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ? AND r.deleted = 0",
    )
    .bind(user_id)
    .fetch_all(&state.db)
    .await?;
    if codes.iter().any(|c| c == SUPER_ADMIN) {
        return Err(AppError::bad("不允许修改超级管理员用户"));
    }
    let _ = state;
    Ok(())
}

async fn assert_keep_last_super(state: &AppState, user_id: i64, new_roles: &[RoleLite]) -> Result<(), AppError> {
    let had: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ? AND r.deleted = 0 AND r.code = 'SUPER_ADMIN'",
    )
    .bind(user_id)
    .fetch_one(&state.db)
    .await?;
    let still = new_roles.iter().any(|r| r.code == SUPER_ADMIN);
    if had > 0 && !still {
        let remaining: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id
             WHERE u.deleted = 0 AND r.deleted = 0 AND r.code = 'SUPER_ADMIN' AND u.id <> ?",
        )
        .bind(user_id)
        .fetch_one(&state.db)
        .await?;
        if remaining == 0 {
            return Err(AppError::bad("至少保留一名超级管理员"));
        }
    }
    Ok(())
}

async fn replace_roles(tx: &mut sqlx::Transaction<'_, sqlx::Sqlite>, user_id: i64, roles: &[RoleLite]) -> Result<(), AppError> {
    sqlx::query("DELETE FROM sys_user_role WHERE user_id = ?").bind(user_id).execute(&mut **tx).await?;
    for role in roles {
        sqlx::query("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)")
            .bind(user_id)
            .bind(role.id)
            .execute(&mut **tx)
            .await?;
    }
    Ok(())
}
