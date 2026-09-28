use std::collections::HashMap;

use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::QueryBuilder;

use crate::db::last_id;
use crate::error::AppError;
use crate::response::Page;
use crate::scope;
use crate::security::{self, SUPER_ADMIN};
use crate::state::{AppState, AuthUser};
use crate::util::{self, assert_acyclic, blank_to_none, like_pat, max_chars, now_text, opt_max, require_text, tombstone, PageParams};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RoleQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub keyword: Option<String>,
    pub status: Option<i64>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RoleForm {
    pub name: Option<String>,
    pub code: Option<String>,
    pub sort: Option<i64>,
    pub data_scope: Option<i64>,
    pub status: Option<i64>,
    pub permission_ids: Option<Vec<i64>>,
    pub remark: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MenuQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub keyword: Option<String>,
    #[serde(rename = "type")]
    pub typ: Option<i64>,
    pub status: Option<i64>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MenuForm {
    pub name: Option<String>,
    pub parent_id: Option<i64>,
    pub path: Option<String>,
    pub component: Option<String>,
    pub redirect: Option<String>,
    pub permission: Option<String>,
    pub icon: Option<String>,
    pub sort: Option<i64>,
    #[serde(rename = "type")]
    pub typ: Option<i64>,
    pub hidden: Option<i64>,
    pub status: Option<i64>,
    pub remark: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PermQuery {
    #[serde(flatten)]
    pub page: PageParams,
    pub keyword: Option<String>,
    #[serde(rename = "type")]
    pub typ: Option<i64>,
    pub status: Option<i64>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PermForm {
    pub name: Option<String>,
    pub code: Option<String>,
    #[serde(rename = "type")]
    pub typ: Option<i64>,
    pub parent_id: Option<i64>,
    pub path: Option<String>,
    pub icon: Option<String>,
    pub sort: Option<i64>,
    pub status: Option<i64>,
    pub remark: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeptForm {
    pub name: Option<String>,
    pub parent_id: Option<i64>,
    pub sort: Option<i64>,
    pub leader: Option<String>,
    pub phone: Option<String>,
    pub email: Option<String>,
    pub status: Option<i64>,
    pub remark: Option<String>,
}

pub async fn role_page(state: &AppState, query: &RoleQuery) -> Result<Page<Value>, AppError> {
    let mut qb = base_role(query);
    let total: i64 = {
        let mut c = QueryBuilder::<sqlx::Sqlite>::new("SELECT COUNT(*) FROM sys_role WHERE deleted = 0");
        push_role_filters(&mut c, query);
        c.build_query_scalar().fetch_one(&state.db).await?
    };
    let order = query.page.order_sql(&[("id", "id"), ("name", "name"), ("code", "code"), ("sort", "sort"), ("createTime", "create_time"), ("status", "status")], "sort", false);
    qb.push(order);
    qb.push(" LIMIT ");
    qb.push_bind(query.page.size());
    qb.push(" OFFSET ");
    qb.push_bind(query.page.offset());
    let rows: Vec<RoleRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(role_vo).collect(), total, query.page.size(), query.page.num()))
}

pub async fn role_all(state: &AppState) -> Result<Vec<Value>, AppError> {
    let rows: Vec<RoleRow> = sqlx::query_as("SELECT id, name, code, sort, data_scope, status, remark, create_time FROM sys_role WHERE deleted = 0 ORDER BY sort ASC, id ASC")
        .fetch_all(&state.db)
        .await?;
    Ok(rows.into_iter().map(role_vo).collect())
}

pub async fn role_create(state: &AppState, actor: &AuthUser, form: RoleForm) -> Result<Value, AppError> {
    let (name, code) = validate_role(&form)?;
    if code == SUPER_ADMIN {
        return Err(AppError::bad("不允许创建超级管理员角色"));
    }
    if code_exists(state, "sys_role", &code, None).await? {
        return Err(AppError::bad("角色编码已存在"));
    }
    scope::assert_can_assign_data_scope(state, actor, form.data_scope).await?;
    let perms = load_perms(state, form.permission_ids.clone()).await?;
    assert_grantable(state, actor, &perms).await?;
    let now = now_text();
    let mut tx = state.db.begin().await?;
    sqlx::query("INSERT INTO sys_role (name, code, sort, data_scope, status, remark, deleted, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)")
        .bind(&name).bind(&code).bind(form.sort.unwrap_or(0)).bind(form.data_scope.unwrap_or(4)).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark.clone())).bind(actor.id).bind(&now).bind(actor.id).bind(&now)
        .execute(&mut *tx).await?;
    let id = last_id(&mut *tx).await?;
    replace_role_perms(&mut tx, id, &perms).await?;
    tx.commit().await?;
    role_one(state, id).await
}

pub async fn role_update(state: &AppState, actor: &AuthUser, id: i64, form: RoleForm) -> Result<Value, AppError> {
    let (name, code) = validate_role(&form)?;
    let current = role_row(state, id).await?;
    if current.code == SUPER_ADMIN && form.status.unwrap_or(0) != 0 {
        return Err(AppError::bad("超级管理员角色不允许停用"));
    }
    if current.code == SUPER_ADMIN && code != SUPER_ADMIN {
        return Err(AppError::bad("超级管理员角色编码不允许修改"));
    }
    if current.code != SUPER_ADMIN && code == SUPER_ADMIN {
        return Err(AppError::bad("不允许将角色编码改为 SUPER_ADMIN"));
    }
    if current.code != code && code_exists(state, "sys_role", &code, Some(id)).await? {
        return Err(AppError::bad("角色编码已存在"));
    }
    assert_can_manage_role(state, actor, &current).await?;
    scope::assert_can_assign_data_scope(state, actor, form.data_scope).await?;
    sqlx::query("UPDATE sys_role SET name = ?, code = ?, sort = ?, data_scope = ?, status = ?, remark = ?, update_by = ?, update_time = ? WHERE id = ? AND deleted = 0")
        .bind(name).bind(code).bind(form.sort.unwrap_or(0)).bind(form.data_scope.unwrap_or(4)).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark)).bind(actor.id).bind(now_text()).bind(id)
        .execute(&state.db).await?;
    security::clear_permissions(state);
    role_one(state, id).await
}

pub async fn role_delete(state: &AppState, actor: &AuthUser, id: i64) -> Result<(), AppError> {
    let role = role_row(state, id).await?;
    if role.code == SUPER_ADMIN {
        return Err(AppError::bad("超级管理员角色不允许删除"));
    }
    assert_can_manage_role(state, actor, &role).await?;
    let used: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id WHERE ur.role_id = ? AND u.deleted = 0",
    )
    .bind(id)
    .fetch_one(&state.db)
    .await?;
    if used > 0 {
        return Err(AppError::bad("该角色仍有用户使用,不允许删除"));
    }
    let code = tombstone(&role.code, id, 50);
    let mut tx = state.db.begin().await?;
    sqlx::query("UPDATE sys_role SET code = ?, deleted = 1, update_time = ? WHERE id = ?")
        .bind(code).bind(now_text()).bind(id).execute(&mut *tx).await?;
    sqlx::query("DELETE FROM sys_role_permission WHERE role_id = ?").bind(id).execute(&mut *tx).await?;
    sqlx::query("DELETE FROM sys_user_role WHERE role_id = ?").bind(id).execute(&mut *tx).await?;
    tx.commit().await?;
    security::clear_permissions(state);
    Ok(())
}

pub async fn role_perm_ids(state: &AppState, id: i64) -> Result<Vec<i64>, AppError> {
    let _ = role_row(state, id).await?;
    Ok(sqlx::query_scalar("SELECT permission_id FROM sys_role_permission WHERE role_id = ? ORDER BY permission_id")
        .bind(id)
        .fetch_all(&state.db)
        .await?)
}

pub async fn role_assign(state: &AppState, actor: &AuthUser, id: i64, permission_ids: Vec<i64>) -> Result<(), AppError> {
    let role = role_row(state, id).await?;
    assert_can_manage_role(state, actor, &role).await?;
    let perms = load_perms(state, Some(permission_ids)).await?;
    assert_grantable(state, actor, &perms).await?;
    let mut tx = state.db.begin().await?;
    replace_role_perms(&mut tx, id, &perms).await?;
    tx.commit().await?;
    security::clear_permissions(state);
    Ok(())
}

pub async fn menu_page(state: &AppState, query: &MenuQuery) -> Result<Page<Value>, AppError> {
    let total = count_simple(state, "sys_menu", |qb| push_menu_filters(qb, query)).await?;
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT id, name, parent_id, path, component, redirect, permission, icon, sort, type, hidden, status, remark, create_time FROM sys_menu WHERE deleted = 0");
    push_menu_filters(&mut qb, query);
    qb.push(query.page.order_sql(&[("id", "id"), ("name", "name"), ("sort", "sort"), ("createTime", "create_time"), ("type", "type")], "sort", false));
    qb.push(" LIMIT ");
    qb.push_bind(query.page.size());
    qb.push(" OFFSET ");
    qb.push_bind(query.page.offset());
    let rows: Vec<MenuRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(|r| menu_vo(r, Vec::new())).collect(), total, query.page.size(), query.page.num()))
}

pub async fn menu_tree(state: &AppState) -> Result<Vec<Value>, AppError> {
    let rows = all_menus(state, false).await?;
    Ok(tree_menus(rows))
}

pub async fn user_menus(state: &AppState, user_id: i64) -> Result<Vec<Value>, AppError> {
    let perms = security::load_permissions(state, user_id).await?;
    let rows = all_menus(state, true).await?;
    if perms.contains("*") {
        return Ok(tree_menus(rows));
    }
    let by_id: HashMap<i64, MenuRow> = rows.iter().map(|m| (m.id, m.clone())).collect();
    let mut visible = std::collections::HashSet::new();
    for menu in &rows {
        if menu.hidden == Some(1) {
            continue;
        }
        if !directly_visible(menu, &perms) {
            continue;
        }
        visible.insert(menu.id);
        let mut parent = menu.parent_id.unwrap_or(0);
        for _ in 0..64 {
            if parent == 0 {
                break;
            }
            visible.insert(parent);
            parent = by_id.get(&parent).and_then(|p| p.parent_id).unwrap_or(0);
        }
    }
    let flat: Vec<MenuRow> = rows.into_iter().filter(|m| visible.contains(&m.id)).collect();
    Ok(tree_menus(flat))
}

pub async fn menu_create(state: &AppState, actor: &AuthUser, form: MenuForm) -> Result<Value, AppError> {
    let name = validate_menu(&form)?;
    let parent = form.parent_id.unwrap_or(0);
    assert_acyclic(None, parent, |id| parent_of(state, "sys_menu", id))?;
    // parent_of is async; handle below
    let _ = (name, actor);
    check_menu_cycle(state, None, parent).await?;
    let id = insert_menu(state, actor, &form).await?;
    Ok(menu_vo(menu_row(state, id).await?, Vec::new()))
}

pub async fn menu_update(state: &AppState, actor: &AuthUser, id: i64, form: MenuForm) -> Result<Value, AppError> {
    let _ = validate_menu(&form)?;
    let current = menu_row(state, id).await?;
    let parent = form.parent_id.unwrap_or(0);
    check_menu_cycle(state, Some(id), parent).await?;
    let redirect = if form.redirect.is_none() { current.redirect } else { blank_to_none(form.redirect.clone()) };
    sqlx::query("UPDATE sys_menu SET name = ?, parent_id = ?, path = ?, component = ?, redirect = ?, permission = ?, icon = ?, sort = ?, type = ?, hidden = ?, status = ?, remark = ?, update_by = ?, update_time = ? WHERE id = ?")
        .bind(require_text(&form.name, "菜单名称不能为空")?)
        .bind(parent)
        .bind(blank_to_none(form.path.clone()).unwrap_or_default())
        .bind(blank_to_none(form.component.clone()))
        .bind(redirect)
        .bind(blank_to_none(form.permission.clone()))
        .bind(blank_to_none(form.icon.clone()))
        .bind(form.sort.unwrap_or(0))
        .bind(form.typ.unwrap_or(2))
        .bind(form.hidden.unwrap_or(0))
        .bind(form.status.unwrap_or(0))
        .bind(blank_to_none(form.remark.clone()))
        .bind(actor.id)
        .bind(now_text())
        .bind(id)
        .execute(&state.db)
        .await?;
    Ok(menu_vo(menu_row(state, id).await?, Vec::new()))
}

pub async fn menu_delete(state: &AppState, id: i64) -> Result<(), AppError> {
    delete_menu_rec(state, id).await
}

pub async fn menu_move(state: &AppState, id: i64, direction: &str) -> Result<(), AppError> {
    let delta: i64 = if direction.eq_ignore_ascii_case("up") {
        -1
    } else if direction.eq_ignore_ascii_case("down") {
        1
    } else {
        return Err(AppError::bad("排序方向只能是 up 或 down"));
    };
    let menu = menu_row(state, id).await?;
    let parent = menu.parent_id.unwrap_or(0);
    let mut siblings: Vec<MenuRow> = sqlx::query_as(
        "SELECT id, name, parent_id, path, component, redirect, permission, icon, sort, type, hidden, status, remark, create_time FROM sys_menu WHERE deleted = 0 AND coalesce(parent_id, 0) = ? ORDER BY coalesce(sort, 0), id",
    )
    .bind(parent)
    .fetch_all(&state.db)
    .await?;
    let idx = siblings.iter().position(|m| m.id == id);
    let Some(idx) = idx else { return Ok(()) };
    let swap = idx as i64 + delta;
    if swap < 0 || swap as usize >= siblings.len() {
        return Ok(());
    }
    let swap = swap as usize;
    let mut a = siblings[idx].sort.unwrap_or(0);
    let mut b = siblings[swap].sort.unwrap_or(0);
    if a == b {
        for (i, item) in siblings.iter_mut().enumerate() {
            item.sort = Some((i as i64) * 10);
            sqlx::query("UPDATE sys_menu SET sort = ? WHERE id = ?").bind(item.sort).bind(item.id).execute(&state.db).await?;
        }
        a = idx as i64 * 10;
        b = swap as i64 * 10;
    }
    sqlx::query("UPDATE sys_menu SET sort = ? WHERE id = ?").bind(b).bind(siblings[idx].id).execute(&state.db).await?;
    sqlx::query("UPDATE sys_menu SET sort = ? WHERE id = ?").bind(a).bind(siblings[swap].id).execute(&state.db).await?;
    Ok(())
}

pub async fn perm_page(state: &AppState, query: &PermQuery) -> Result<Page<Value>, AppError> {
    let total = count_simple(state, "sys_permission", |qb| push_perm_filters(qb, query)).await?;
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT id, name, code, type, parent_id, path, icon, sort, status, remark, create_time FROM sys_permission WHERE deleted = 0");
    push_perm_filters(&mut qb, query);
    qb.push(query.page.order_sql(&[("id", "id"), ("name", "name"), ("code", "code"), ("sort", "sort"), ("createTime", "create_time")], "sort", false));
    qb.push(" LIMIT ");
    qb.push_bind(query.page.size());
    qb.push(" OFFSET ");
    qb.push_bind(query.page.offset());
    let rows: Vec<PermRow> = qb.build_query_as().fetch_all(&state.db).await?;
    Ok(Page::new(rows.into_iter().map(|r| perm_vo(r, Vec::new())).collect(), total, query.page.size(), query.page.num()))
}

pub async fn perm_tree(state: &AppState) -> Result<Vec<Value>, AppError> {
    Ok(tree_perms(all_perms(state).await?))
}

pub async fn perm_all(state: &AppState) -> Result<Vec<Value>, AppError> {
    Ok(all_perms(state).await?.into_iter().map(|r| perm_vo(r, Vec::new())).collect())
}

pub async fn perm_create(state: &AppState, actor: &AuthUser, form: PermForm) -> Result<Value, AppError> {
    let (name, code) = validate_perm(&form)?;
    if code_exists(state, "sys_permission", &code, None).await? {
        return Err(AppError::bad("权限编码已存在"));
    }
    let parent = form.parent_id.unwrap_or(0);
    check_perm_cycle(state, None, parent).await?;
    let now = now_text();
    let mut conn = state.db.acquire().await?;
    sqlx::query("INSERT INTO sys_permission (name, code, type, parent_id, path, icon, sort, status, remark, deleted, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)")
        .bind(name).bind(&code).bind(form.typ.unwrap_or(2)).bind(parent).bind(blank_to_none(form.path)).bind(blank_to_none(form.icon)).bind(form.sort.unwrap_or(0)).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark)).bind(actor.id).bind(&now).bind(actor.id).bind(&now)
        .execute(&mut *conn).await?;
    let id = last_id(&mut *conn).await?;
    security::clear_permissions(state);
    Ok(perm_vo(perm_row(state, id).await?, Vec::new()))
}

pub async fn perm_update(state: &AppState, actor: &AuthUser, id: i64, form: PermForm) -> Result<Value, AppError> {
    let (name, code) = validate_perm(&form)?;
    let current = perm_row(state, id).await?;
    if current.code != code && code_exists(state, "sys_permission", &code, Some(id)).await? {
        return Err(AppError::bad("权限编码已存在"));
    }
    let parent = form.parent_id.unwrap_or(0);
    check_perm_cycle(state, Some(id), parent).await?;
    assert_can_edit_perm(state, actor, &current.code, &code).await?;
    let path = if form.path.is_none() { current.path } else { blank_to_none(form.path.clone()) };
    let icon = if form.icon.is_none() { current.icon } else { blank_to_none(form.icon.clone()) };
    sqlx::query("UPDATE sys_permission SET name = ?, code = ?, type = ?, parent_id = ?, path = ?, icon = ?, sort = ?, status = ?, remark = ?, update_by = ?, update_time = ? WHERE id = ?")
        .bind(name).bind(&code).bind(form.typ.unwrap_or(2)).bind(parent).bind(path).bind(icon).bind(form.sort.unwrap_or(0)).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark)).bind(actor.id).bind(now_text()).bind(id)
        .execute(&state.db).await?;
    if !current.code.is_empty() && current.code != code {
        sqlx::query("UPDATE sys_menu SET permission = ? WHERE permission = ? AND deleted = 0").bind(&code).bind(&current.code).execute(&state.db).await?;
    }
    security::clear_permissions(state);
    Ok(perm_vo(perm_row(state, id).await?, Vec::new()))
}

pub async fn perm_delete(state: &AppState, actor: &AuthUser, id: i64) -> Result<(), AppError> {
    let children: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_permission WHERE deleted = 0 AND parent_id = ?").bind(id).fetch_one(&state.db).await?;
    if children > 0 {
        return Err(AppError::bad("存在子权限,不允许删除"));
    }
    let bound: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_role_permission WHERE permission_id = ?").bind(id).fetch_one(&state.db).await?;
    if bound > 0 {
        return Err(AppError::bad("该权限仍被角色引用,不允许删除"));
    }
    let perm = perm_row(state, id).await?;
    assert_can_edit_perm(state, actor, &perm.code, &perm.code).await?;
    let code = tombstone(&perm.code, id, 100);
    sqlx::query("UPDATE sys_permission SET code = ?, deleted = 1, update_time = ? WHERE id = ?").bind(code).bind(now_text()).bind(id).execute(&state.db).await?;
    security::clear_permissions(state);
    Ok(())
}

pub async fn dept_tree(state: &AppState, actor: &AuthUser) -> Result<Vec<Value>, AppError> {
    let visible = scope::visible_dept_ids_with_ancestors(state, actor).await?;
    let rows = all_depts(state).await?;
    let rows: Vec<DeptRow> = rows.into_iter().filter(|d| visible.as_ref().map(|set| set.contains(&d.id)).unwrap_or(true)).collect();
    Ok(tree_depts(rows))
}

pub async fn dept_all(state: &AppState, actor: &AuthUser) -> Result<Vec<Value>, AppError> {
    let scope = scope::current(state, actor).await?;
    let rows: Vec<DeptRow> = sqlx::query_as("SELECT id, name, parent_id, ancestors, sort, leader, phone, email, status, remark, create_time FROM sys_dept WHERE deleted = 0 AND status = 0 ORDER BY sort ASC, id ASC")
        .fetch_all(&state.db).await?;
    if scope.all {
        return Ok(rows.into_iter().map(|d| dept_vo(d, Vec::new())).collect());
    }
    let mut ids = std::collections::HashSet::new();
    if scope.self_only {
        let mine: Option<i64> = sqlx::query_scalar("SELECT dept_id FROM sys_user WHERE id = ? AND deleted = 0").bind(actor.id).fetch_optional(&state.db).await?;
        if let Some(id) = mine { ids.insert(id); }
    } else {
        ids.extend(scope.dept_ids);
    }
    Ok(rows.into_iter().filter(|d| ids.contains(&d.id)).map(|d| dept_vo(d, Vec::new())).collect())
}

pub async fn dept_create(state: &AppState, actor: &AuthUser, form: DeptForm) -> Result<Value, AppError> {
    let name = require_text(&form.name, "部门名称不能为空")?;
    max_chars(&name, 50, "部门名称不能为空")?;
    opt_max(&form.leader, 50, "备注最长 500 个字符")?;
    opt_max(&form.phone, 20, "备注最长 500 个字符")?;
    opt_max(&form.email, 100, "备注最长 500 个字符")?;
    opt_max(&form.remark, 500, "备注最长 500 个字符")?;
    let parent = form.parent_id.unwrap_or(0);
    if parent != 0 {
        scope::assert_can_access_dept(state, actor, Some(parent)).await?;
    } else if !scope::current(state, actor).await?.all {
        return Err(AppError::bad("无权创建根部门"));
    }
    check_dept_cycle(state, None, parent).await?;
    if dept_name_exists(state, &name, parent, None).await? {
        return Err(AppError::bad("同级部门名称已存在"));
    }
    let ancestors = ancestors_of(state, parent).await?;
    let now = now_text();
    let mut conn = state.db.acquire().await?;
    sqlx::query("INSERT INTO sys_dept (name, parent_id, ancestors, sort, leader, phone, email, status, remark, deleted, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)")
        .bind(&name).bind(parent).bind(ancestors).bind(form.sort.unwrap_or(0)).bind(blank_to_none(form.leader)).bind(blank_to_none(form.phone)).bind(blank_to_none(form.email)).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark)).bind(actor.id).bind(&now).bind(actor.id).bind(&now)
        .execute(&mut *conn).await?;
    let id = last_id(&mut *conn).await?;
    Ok(dept_vo(dept_row(state, id).await?, Vec::new()))
}

pub async fn dept_update(state: &AppState, actor: &AuthUser, id: i64, form: DeptForm) -> Result<Value, AppError> {
    let name = require_text(&form.name, "部门名称不能为空")?;
    max_chars(&name, 50, "部门名称不能为空")?;
    let dept = dept_row(state, id).await?;
    scope::assert_can_access_dept(state, actor, Some(id)).await?;
    let parent = form.parent_id.unwrap_or(0);
    if parent != 0 {
        scope::assert_can_access_dept(state, actor, Some(parent)).await?;
    } else if !scope::current(state, actor).await?.all {
        return Err(AppError::bad("无权将部门挂到根级"));
    }
    check_dept_cycle(state, Some(id), parent).await?;
    let old_parent = dept.parent_id.unwrap_or(0);
    if (dept.name != name || old_parent != parent) && dept_name_exists(state, &name, parent, Some(id)).await? {
        return Err(AppError::bad("同级部门名称已存在"));
    }
    let old_chain = chain(dept.ancestors.as_deref(), id);
    let ancestors = ancestors_of(state, parent).await?;
    sqlx::query("UPDATE sys_dept SET name = ?, parent_id = ?, ancestors = ?, sort = ?, leader = ?, phone = ?, email = ?, status = ?, remark = ?, update_by = ?, update_time = ? WHERE id = ?")
        .bind(&name).bind(parent).bind(&ancestors).bind(form.sort.unwrap_or(0)).bind(blank_to_none(form.leader)).bind(blank_to_none(form.phone)).bind(blank_to_none(form.email)).bind(form.status.unwrap_or(0)).bind(blank_to_none(form.remark)).bind(actor.id).bind(now_text()).bind(id)
        .execute(&state.db).await?;
    let new_chain = chain(Some(&ancestors), id);
    rewrite_children(state, id, &old_chain, &new_chain).await?;
    if dept.name != name {
        sqlx::query("UPDATE sys_user SET dept_name = ? WHERE dept_id = ? AND deleted = 0").bind(&name).bind(id).execute(&state.db).await?;
    }
    Ok(dept_vo(dept_row(state, id).await?, Vec::new()))
}

pub async fn dept_delete(state: &AppState, actor: &AuthUser, id: i64) -> Result<(), AppError> {
    scope::assert_can_access_dept(state, actor, Some(id)).await?;
    let children: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_dept WHERE deleted = 0 AND parent_id = ?").bind(id).fetch_one(&state.db).await?;
    if children > 0 {
        return Err(AppError::bad("存在子部门,不允许删除"));
    }
    let used: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND dept_id = ?").bind(id).fetch_one(&state.db).await?;
    if used > 0 {
        return Err(AppError::bad("部门下仍有用户,不允许删除"));
    }
    let _ = dept_row(state, id).await?;
    sqlx::query("UPDATE sys_dept SET deleted = 1, update_time = ? WHERE id = ?").bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(())
}

#[derive(Clone, sqlx::FromRow)]
struct RoleRow {
    id: i64,
    name: String,
    code: String,
    sort: Option<i64>,
    data_scope: Option<i64>,
    status: Option<i64>,
    remark: Option<String>,
    create_time: Option<String>,
}

#[derive(Clone, sqlx::FromRow)]
struct MenuRow {
    id: i64,
    name: String,
    parent_id: Option<i64>,
    path: String,
    component: Option<String>,
    redirect: Option<String>,
    permission: Option<String>,
    icon: Option<String>,
    sort: Option<i64>,
    #[sqlx(rename = "type")]
    typ: i64,
    hidden: Option<i64>,
    status: Option<i64>,
    remark: Option<String>,
    create_time: Option<String>,
}

#[derive(Clone, sqlx::FromRow)]
struct PermRow {
    id: i64,
    name: String,
    code: String,
    #[sqlx(rename = "type")]
    typ: i64,
    parent_id: Option<i64>,
    path: Option<String>,
    icon: Option<String>,
    sort: Option<i64>,
    status: Option<i64>,
    remark: Option<String>,
    create_time: Option<String>,
}

#[derive(Clone, sqlx::FromRow)]
struct DeptRow {
    id: i64,
    name: String,
    parent_id: Option<i64>,
    ancestors: Option<String>,
    sort: Option<i64>,
    leader: Option<String>,
    phone: Option<String>,
    email: Option<String>,
    status: Option<i64>,
    remark: Option<String>,
    create_time: Option<String>,
}

fn role_vo(row: RoleRow) -> Value {
    json!({
        "id": row.id, "name": row.name, "code": row.code, "sort": row.sort.unwrap_or(0),
        "dataScope": row.data_scope.unwrap_or(4), "status": row.status.unwrap_or(0),
        "remark": row.remark, "createTime": util::normalize_dt(row.create_time), "permissionIds": null
    })
}

fn menu_vo(row: MenuRow, children: Vec<Value>) -> Value {
    json!({
        "id": row.id, "name": row.name, "parentId": row.parent_id.unwrap_or(0), "path": row.path,
        "component": row.component, "redirect": row.redirect, "permission": row.permission, "icon": row.icon,
        "sort": row.sort.unwrap_or(0), "type": row.typ, "hidden": row.hidden.unwrap_or(0), "status": row.status.unwrap_or(0),
        "remark": row.remark, "createTime": util::normalize_dt(row.create_time), "children": children
    })
}

fn perm_vo(row: PermRow, children: Vec<Value>) -> Value {
    json!({
        "id": row.id, "name": row.name, "code": row.code, "type": row.typ, "parentId": row.parent_id.unwrap_or(0),
        "path": row.path, "icon": row.icon, "sort": row.sort.unwrap_or(0), "status": row.status.unwrap_or(0),
        "remark": row.remark, "createTime": util::normalize_dt(row.create_time), "children": children
    })
}

fn dept_vo(row: DeptRow, children: Vec<Value>) -> Value {
    json!({
        "id": row.id, "name": row.name, "parentId": row.parent_id.unwrap_or(0), "ancestors": row.ancestors,
        "sort": row.sort.unwrap_or(0), "leader": row.leader, "phone": row.phone, "email": row.email,
        "status": row.status.unwrap_or(0), "remark": row.remark, "createTime": util::normalize_dt(row.create_time),
        "children": children
    })
}

fn tree_menus(rows: Vec<MenuRow>) -> Vec<Value> {
    tree(rows, |r| r.id, |r| r.parent_id.unwrap_or(0), |row, children| menu_vo(row, children))
}
fn tree_perms(rows: Vec<PermRow>) -> Vec<Value> {
    tree(rows, |r| r.id, |r| r.parent_id.unwrap_or(0), |row, children| perm_vo(row, children))
}
fn tree_depts(rows: Vec<DeptRow>) -> Vec<Value> {
    tree(rows, |r| r.id, |r| r.parent_id.unwrap_or(0), |row, children| dept_vo(row, children))
}

fn tree<T: Clone>(rows: Vec<T>, id: impl Fn(&T) -> i64, parent: impl Fn(&T) -> i64, render: impl Fn(T, Vec<Value>) -> Value) -> Vec<Value> {
    let ids: std::collections::HashSet<i64> = rows.iter().map(|r| id(r)).collect();
    fn walk<T: Clone>(rows: &[T], id: &impl Fn(&T) -> i64, parent: &impl Fn(&T) -> i64, render: &impl Fn(T, Vec<Value>) -> Value, self_id: i64) -> Value {
        let children: Vec<Value> = rows.iter().filter(|r| parent(r) == self_id && id(r) != self_id).map(|r| walk(rows, id, parent, render, id(r))).collect();
        let row = rows.iter().find(|r| id(r) == self_id).unwrap().clone();
        render(row, children)
    }
    rows.iter()
        .filter(|r| parent(r) == 0 || !ids.contains(&parent(r)))
        .map(|r| walk(&rows, &id, &parent, &render, id(r)))
        .collect()
}

fn directly_visible(menu: &MenuRow, perms: &std::collections::HashSet<String>) -> bool {
    if let Some(permission) = menu.permission.as_deref().filter(|s| !s.is_empty()) {
        return perms.contains(permission);
    }
    menu.path.starts_with("http://") || menu.path.starts_with("https://")
}

fn base_role(query: &RoleQuery) -> QueryBuilder<'static, sqlx::Sqlite> {
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new("SELECT id, name, code, sort, data_scope, status, remark, create_time FROM sys_role WHERE deleted = 0");
    push_role_filters(&mut qb, query);
    qb
}

fn push_role_filters(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &RoleQuery) {
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(name) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(code) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(status) = query.status {
        qb.push(" AND status = ");
        qb.push_bind(status);
    }
}

fn push_menu_filters(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &MenuQuery) {
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(name) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(path) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(typ) = query.typ {
        qb.push(" AND type = ");
        qb.push_bind(typ);
    }
    if let Some(status) = query.status {
        qb.push(" AND status = ");
        qb.push_bind(status);
    }
}

fn push_perm_filters(qb: &mut QueryBuilder<'_, sqlx::Sqlite>, query: &PermQuery) {
    if let Some(keyword) = blank_to_none(query.keyword.clone()) {
        let like = like_pat(&keyword);
        qb.push(" AND (lower(name) LIKE ");
        qb.push_bind(like.clone());
        qb.push(" OR lower(code) LIKE ");
        qb.push_bind(like);
        qb.push(")");
    }
    if let Some(typ) = query.typ {
        qb.push(" AND type = ");
        qb.push_bind(typ);
    }
    if let Some(status) = query.status {
        qb.push(" AND status = ");
        qb.push_bind(status);
    }
}

async fn count_simple(state: &AppState, table: &str, filters: impl FnOnce(&mut QueryBuilder<'_, sqlx::Sqlite>)) -> Result<i64, AppError> {
    let mut qb = QueryBuilder::<sqlx::Sqlite>::new(format!("SELECT COUNT(*) FROM {table} WHERE deleted = 0"));
    filters(&mut qb);
    Ok(qb.build_query_scalar().fetch_one(&state.db).await?)
}

async fn role_row(state: &AppState, id: i64) -> Result<RoleRow, AppError> {
    sqlx::query_as("SELECT id, name, code, sort, data_scope, status, remark, create_time FROM sys_role WHERE id = ? AND deleted = 0")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("角色不存在"))
}
async fn role_one(state: &AppState, id: i64) -> Result<Value, AppError> {
    Ok(role_vo(role_row(state, id).await?))
}

async fn code_exists(state: &AppState, table: &str, code: &str, except: Option<i64>) -> Result<bool, AppError> {
    let sql = if except.is_some() {
        format!("SELECT COUNT(*) FROM {table} WHERE deleted = 0 AND code = ? AND id <> ?")
    } else {
        format!("SELECT COUNT(*) FROM {table} WHERE deleted = 0 AND code = ?")
    };
    let count: i64 = if let Some(id) = except {
        sqlx::query_scalar(&sql).bind(code).bind(id).fetch_one(&state.db).await?
    } else {
        sqlx::query_scalar(&sql).bind(code).fetch_one(&state.db).await?
    };
    Ok(count > 0)
}

fn validate_role(form: &RoleForm) -> Result<(String, String), AppError> {
    let name = require_text(&form.name, "角色名称不能为空")?;
    max_chars(&name, 50, "角色名称最长 50 个字符")?;
    let code = require_text(&form.code, "角色编码不能为空")?;
    max_chars(&code, 50, "角色编码最长 50 个字符")?;
    util::matches(&code, r"^[A-Z][A-Z0-9_]*$", "角色编码需为大写字母、数字、下划线,且以字母开头")?;
    opt_max(&form.remark, 500, "备注最长 500 个字符")?;
    Ok((name, code))
}

async fn load_perms(state: &AppState, ids: Option<Vec<i64>>) -> Result<Vec<i64>, AppError> {
    let Some(ids) = ids else { return Ok(Vec::new()) };
    let mut distinct = Vec::new();
    for id in ids {
        if !distinct.contains(&id) { distinct.push(id); }
    }
    for id in &distinct {
        let exists: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_permission WHERE id = ? AND deleted = 0").bind(id).fetch_one(&state.db).await?;
        if exists == 0 { return Err(AppError::bad("部分权限不存在或已删除")); }
    }
    Ok(distinct)
}

async fn assert_grantable(state: &AppState, actor: &AuthUser, perms: &[i64]) -> Result<(), AppError> {
    if actor.is_privileged() || perms.is_empty() { return Ok(()); }
    let mine = security::load_permissions(state, actor.id).await?;
    for id in perms {
        let row: Option<(String, Option<i64>)> = sqlx::query_as("SELECT code, status FROM sys_permission WHERE id = ? AND deleted = 0").bind(id).fetch_optional(&state.db).await?;
        if let Some((code, status)) = row {
            if status.unwrap_or(0) != 0 || code.is_empty() { continue; }
            if !mine.contains(&code) {
                return Err(AppError::bad(format!("不能分配超出自身范围的权限: {code}")));
            }
        }
    }
    Ok(())
}

async fn assert_can_manage_role(state: &AppState, actor: &AuthUser, role: &RoleRow) -> Result<(), AppError> {
    if actor.is_privileged() { return Ok(()); }
    if role.code == SUPER_ADMIN { return Err(AppError::bad("不允许修改超级管理员角色")); }
    scope::assert_can_assign_data_scope(state, actor, role.data_scope).await?;
    let mine = security::load_permissions(state, actor.id).await?;
    let codes: Vec<(String, Option<i64>)> = sqlx::query_as(
        "SELECT p.code, p.status FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id = p.id WHERE rp.role_id = ? AND p.deleted = 0",
    ).bind(role.id).fetch_all(&state.db).await?;
    for (code, status) in codes {
        if status.unwrap_or(0) != 0 || code.is_empty() { continue; }
        if !mine.contains(&code) {
            return Err(AppError::bad("不能修改含超出自身权限的角色"));
        }
    }
    Ok(())
}

async fn replace_role_perms(tx: &mut sqlx::Transaction<'_, sqlx::Sqlite>, role_id: i64, perms: &[i64]) -> Result<(), AppError> {
    sqlx::query("DELETE FROM sys_role_permission WHERE role_id = ?").bind(role_id).execute(&mut **tx).await?;
    for id in perms {
        sqlx::query("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)").bind(role_id).bind(id).execute(&mut **tx).await?;
    }
    Ok(())
}

async fn all_menus(state: &AppState, enabled_only: bool) -> Result<Vec<MenuRow>, AppError> {
    let sql = if enabled_only {
        "SELECT id, name, parent_id, path, component, redirect, permission, icon, sort, type, hidden, status, remark, create_time FROM sys_menu WHERE deleted = 0 AND status = 0 ORDER BY coalesce(sort, 0), id"
    } else {
        "SELECT id, name, parent_id, path, component, redirect, permission, icon, sort, type, hidden, status, remark, create_time FROM sys_menu WHERE deleted = 0 ORDER BY coalesce(sort, 0), id"
    };
    Ok(sqlx::query_as(sql).fetch_all(&state.db).await?)
}

async fn menu_row(state: &AppState, id: i64) -> Result<MenuRow, AppError> {
    sqlx::query_as("SELECT id, name, parent_id, path, component, redirect, permission, icon, sort, type, hidden, status, remark, create_time FROM sys_menu WHERE id = ? AND deleted = 0")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("菜单不存在"))
}

fn validate_menu(form: &MenuForm) -> Result<String, AppError> {
    let name = require_text(&form.name, "菜单名称不能为空")?;
    max_chars(&name, 50, "菜单名称最长 50 个字符")?;
    opt_max(&form.path, 200, "路由路径最长 200 个字符")?;
    opt_max(&form.component, 255, "组件路径最长 255 个字符")?;
    opt_max(&form.redirect, 255, "重定向路径最长 255 个字符")?;
    opt_max(&form.permission, 100, "权限标识最长 100 个字符")?;
    opt_max(&form.icon, 100, "图标最长 100 个字符")?;
    opt_max(&form.remark, 500, "备注最长 500 个字符")?;
    let typ = form.typ.unwrap_or(2);
    if typ != 3 && blank_to_none(form.path.clone()).is_none() {
        return Err(AppError::bad("目录和菜单的路由路径不能为空"));
    }
    Ok(name)
}

async fn insert_menu(state: &AppState, actor: &AuthUser, form: &MenuForm) -> Result<i64, AppError> {
    let now = now_text();
    let mut conn = state.db.acquire().await?;
    sqlx::query("INSERT INTO sys_menu (name, parent_id, path, component, redirect, permission, icon, sort, type, hidden, status, remark, deleted, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)")
        .bind(require_text(&form.name, "菜单名称不能为空")?)
        .bind(form.parent_id.unwrap_or(0))
        .bind(blank_to_none(form.path.clone()).unwrap_or_default())
        .bind(blank_to_none(form.component.clone()))
        .bind(blank_to_none(form.redirect.clone()))
        .bind(blank_to_none(form.permission.clone()))
        .bind(blank_to_none(form.icon.clone()))
        .bind(form.sort.unwrap_or(0))
        .bind(form.typ.unwrap_or(2))
        .bind(form.hidden.unwrap_or(0))
        .bind(form.status.unwrap_or(0))
        .bind(blank_to_none(form.remark.clone()))
        .bind(actor.id).bind(&now).bind(actor.id).bind(&now)
        .execute(&mut *conn).await?;
    Ok(last_id(&mut *conn).await?)
}

async fn delete_menu_rec(state: &AppState, id: i64) -> Result<(), AppError> {
    let children: Vec<i64> = sqlx::query_scalar("SELECT id FROM sys_menu WHERE deleted = 0 AND parent_id = ?").bind(id).fetch_all(&state.db).await?;
    for child in children {
        Box::pin(delete_menu_rec(state, child)).await?;
    }
    let _ = menu_row(state, id).await?;
    sqlx::query("UPDATE sys_menu SET deleted = 1, update_time = ? WHERE id = ?").bind(now_text()).bind(id).execute(&state.db).await?;
    Ok(())
}

fn parent_of(_: &AppState, _: &str, _: i64) -> Result<i64, AppError> { Ok(0) }

async fn check_menu_cycle(state: &AppState, id: Option<i64>, parent: i64) -> Result<(), AppError> {
    assert_acyclic(id, parent, |cursor| {
        // sync callback can't query. Use a preloaded map via blocking pattern: we check inline instead.
        let _ = cursor;
        Ok(0)
    })?;
    let rows: Vec<(i64, Option<i64>)> = sqlx::query_as("SELECT id, parent_id FROM sys_menu WHERE deleted = 0").fetch_all(&state.db).await?;
    let map: HashMap<i64, i64> = rows.into_iter().map(|(i, p)| (i, p.unwrap_or(0))).collect();
    assert_acyclic(id, parent, |cursor| {
        map.get(&cursor).copied().ok_or_else(|| AppError::bad("父级菜单不存在"))
    })
}

async fn check_perm_cycle(state: &AppState, id: Option<i64>, parent: i64) -> Result<(), AppError> {
    let rows: Vec<(i64, Option<i64>)> = sqlx::query_as("SELECT id, parent_id FROM sys_permission WHERE deleted = 0").fetch_all(&state.db).await?;
    let map: HashMap<i64, i64> = rows.into_iter().map(|(i, p)| (i, p.unwrap_or(0))).collect();
    assert_acyclic(id, parent, |cursor| map.get(&cursor).copied().ok_or_else(|| AppError::bad("父级权限不存在")))
}

async fn check_dept_cycle(state: &AppState, id: Option<i64>, parent: i64) -> Result<(), AppError> {
    let rows: Vec<(i64, Option<i64>)> = sqlx::query_as("SELECT id, parent_id FROM sys_dept WHERE deleted = 0").fetch_all(&state.db).await?;
    let map: HashMap<i64, i64> = rows.into_iter().map(|(i, p)| (i, p.unwrap_or(0))).collect();
    assert_acyclic(id, parent, |cursor| map.get(&cursor).copied().ok_or_else(|| AppError::bad("部门不存在")))
}

async fn all_perms(state: &AppState) -> Result<Vec<PermRow>, AppError> {
    Ok(sqlx::query_as("SELECT id, name, code, type, parent_id, path, icon, sort, status, remark, create_time FROM sys_permission WHERE deleted = 0 ORDER BY coalesce(sort, 0), id").fetch_all(&state.db).await?)
}

async fn perm_row(state: &AppState, id: i64) -> Result<PermRow, AppError> {
    sqlx::query_as("SELECT id, name, code, type, parent_id, path, icon, sort, status, remark, create_time FROM sys_permission WHERE id = ? AND deleted = 0")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("权限不存在"))
}

fn validate_perm(form: &PermForm) -> Result<(String, String), AppError> {
    let name = require_text(&form.name, "权限名称不能为空")?;
    max_chars(&name, 50, "权限名称最长 50 个字符")?;
    let code = require_text(&form.code, "权限编码不能为空")?;
    max_chars(&code, 100, "权限编码最长 100 个字符")?;
    util::matches(&code, r"^[a-z][a-z0-9_:.-]*$", "权限编码格式不正确(如 system:user:view)")?;
    opt_max(&form.path, 200, "路由路径最长 200 个字符")?;
    opt_max(&form.icon, 100, "图标最长 100 个字符")?;
    opt_max(&form.remark, 500, "备注最长 500 个字符")?;
    Ok((name, code))
}

async fn assert_can_edit_perm(state: &AppState, actor: &AuthUser, current: &str, new_code: &str) -> Result<(), AppError> {
    if actor.is_privileged() { return Ok(()); }
    let mine = security::load_permissions(state, actor.id).await?;
    if current.is_empty() || !mine.contains(current) {
        return Err(AppError::bad("不能修改超出自身范围的权限"));
    }
    if new_code != current && !mine.contains(new_code) {
        return Err(AppError::bad("不能把权限编码改成自己没有的编码"));
    }
    let _ = state;
    Ok(())
}

async fn all_depts(state: &AppState) -> Result<Vec<DeptRow>, AppError> {
    Ok(sqlx::query_as("SELECT id, name, parent_id, ancestors, sort, leader, phone, email, status, remark, create_time FROM sys_dept WHERE deleted = 0 ORDER BY coalesce(sort, 0), id").fetch_all(&state.db).await?)
}

async fn dept_row(state: &AppState, id: i64) -> Result<DeptRow, AppError> {
    sqlx::query_as("SELECT id, name, parent_id, ancestors, sort, leader, phone, email, status, remark, create_time FROM sys_dept WHERE id = ? AND deleted = 0")
        .bind(id).fetch_optional(&state.db).await?.ok_or_else(|| AppError::not_found("部门不存在"))
}

async fn dept_name_exists(state: &AppState, name: &str, parent: i64, except: Option<i64>) -> Result<bool, AppError> {
    let count: i64 = if let Some(id) = except {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_dept WHERE deleted = 0 AND name = ? AND coalesce(parent_id, 0) = ? AND id <> ?")
            .bind(name).bind(parent).bind(id).fetch_one(&state.db).await?
    } else {
        sqlx::query_scalar("SELECT COUNT(*) FROM sys_dept WHERE deleted = 0 AND name = ? AND coalesce(parent_id, 0) = ?")
            .bind(name).bind(parent).fetch_one(&state.db).await?
    };
    Ok(count > 0)
}

async fn ancestors_of(state: &AppState, parent: i64) -> Result<String, AppError> {
    if parent == 0 { return Ok("0".into()); }
    let parent_row = dept_row(state, parent).await?;
    Ok(chain(parent_row.ancestors.as_deref(), parent_row.id))
}

fn chain(ancestors: Option<&str>, id: i64) -> String {
    let base = ancestors.filter(|s| !s.is_empty()).unwrap_or("0");
    format!("{base},{id}")
}

async fn rewrite_children(state: &AppState, id: i64, old_prefix: &str, new_prefix: &str) -> Result<(), AppError> {
    if old_prefix.is_empty() || old_prefix == new_prefix { return Ok(()); }
    let rows: Vec<(i64, Option<String>)> = sqlx::query_as("SELECT id, ancestors FROM sys_dept WHERE deleted = 0").fetch_all(&state.db).await?;
    for (child, ancestors) in rows {
        if child == id { continue; }
        let Some(ancestors) = ancestors else { continue };
        if ancestors == old_prefix || ancestors.starts_with(&format!("{old_prefix},")) {
            let next = format!("{new_prefix}{}", &ancestors[old_prefix.len()..]);
            sqlx::query("UPDATE sys_dept SET ancestors = ? WHERE id = ?").bind(next).bind(child).execute(&state.db).await?;
        }
    }
    Ok(())
}
