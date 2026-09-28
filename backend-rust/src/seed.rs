use sqlx::{Sqlite, Transaction};

use crate::db::last_id;
use crate::error::AppError;
use crate::password;
use crate::state::AppState;
use crate::util::now_text;

pub async fn run(state: &AppState) -> Result<(), AppError> {
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_user").fetch_one(&state.db).await?;
    if count == 0 {
        let mut tx = state.db.begin().await?;
        seed_base(&mut tx).await?;
        tx.commit().await?;
        tracing::info!("种子数据初始化完成: admin / admin123");
    }
    let mut tx = state.db.begin().await?;
    seed_p1(&mut tx).await?;
    tx.commit().await?;
    Ok(())
}

async fn seed_base(tx: &mut Transaction<'_, Sqlite>) -> Result<(), AppError> {
    let now = now_text();
    let super_id = insert_role(tx, "超级管理员", "SUPER_ADMIN", 1, 1, "系统最高权限,可管理所有功能", &now).await?;
    insert_role(tx, "系统管理员", "ADMIN", 2, 1, "系统管理功能,不可删除超级管理员", &now).await?;
    insert_role(tx, "普通用户", "USER", 3, 4, "基本功能使用权限", &now).await?;
    insert_role(tx, "审计员", "AUDITOR", 4, 1, "仅查看日志,无操作权限", &now).await?;
    insert_role(tx, "访客", "GUEST", 5, 4, "只读权限", &now).await?;

    let system = insert_perm(tx, "系统管理", "system", 1, 0, 1, &now).await?;
    let monitor = insert_perm(tx, "监控中心", "monitor", 1, 0, 2, &now).await?;
    let tools = insert_perm(tx, "工具中心", "tools", 1, 0, 3, &now).await?;
    let mut perms = vec![system, monitor, tools];
    for (name, code, sort, parent) in [
        ("用户查看", "system:user:view", 1, system),
        ("用户新增", "system:user:add", 2, system),
        ("用户编辑", "system:user:edit", 3, system),
        ("用户删除", "system:user:delete", 4, system),
        ("角色查看", "system:role:view", 11, system),
        ("角色新增", "system:role:add", 12, system),
        ("角色编辑", "system:role:edit", 13, system),
        ("角色删除", "system:role:delete", 14, system),
        ("角色授权", "system:role:assign", 15, system),
        ("菜单查看", "system:menu:view", 21, system),
        ("菜单新增", "system:menu:add", 22, system),
        ("菜单编辑", "system:menu:edit", 23, system),
        ("菜单删除", "system:menu:delete", 24, system),
        ("权限查看", "system:permission:view", 31, system),
        ("权限新增", "system:permission:add", 32, system),
        ("权限编辑", "system:permission:edit", 33, system),
        ("权限删除", "system:permission:delete", 34, system),
        ("配置查看", "system:config:view", 41, system),
        ("配置新增", "system:config:add", 42, system),
        ("配置编辑", "system:config:edit", 43, system),
        ("配置删除", "system:config:delete", 44, system),
        ("日志查看", "monitor:log:view", 1, monitor),
        ("日志清空", "monitor:log:delete", 2, monitor),
        ("字典查看", "tools:dict:view", 1, tools),
        ("字典编辑", "tools:dict:edit", 2, tools),
    ] {
        perms.push(insert_perm(tx, name, code, 2, parent, sort, &now).await?);
    }

    let system_menu = insert_menu(tx, "系统管理", 0, "/system", "Layout", "Setting", 1, 1, None, &now).await?;
    let monitor_menu = insert_menu(tx, "系统监控", 0, "/monitor", "Layout", "Monitor", 2, 1, None, &now).await?;
    let tools_menu = insert_menu(tx, "系统工具", 0, "/tools", "Layout", "Wrench", 3, 1, None, &now).await?;
    for (name, parent, path, icon, sort, perm) in [
        ("用户管理", system_menu, "/system/user", "User", 1, Some("system:user:view")),
        ("角色管理", system_menu, "/system/role", "Role", 2, Some("system:role:view")),
        ("菜单管理", system_menu, "/system/menu", "Menu", 3, Some("system:menu:view")),
        ("权限管理", system_menu, "/system/permission", "Shield", 4, Some("system:permission:view")),
        ("系统配置", system_menu, "/system/config", "Tool", 5, Some("system:config:view")),
        ("登录日志", monitor_menu, "/monitor/login-log", "Log", 1, Some("monitor:log:view")),
        ("操作日志", monitor_menu, "/monitor/op-log", "FileText", 2, Some("monitor:log:view")),
        ("异常日志", monitor_menu, "/monitor/error-log", "AlertTriangle", 3, Some("monitor:log:view")),
        ("字典管理", tools_menu, "/tools/dict", "Book", 1, Some("tools:dict:view")),
        ("代码生成", tools_menu, "/tools/build", "Code", 2, None),
    ] {
        let component = format!("{path}/index");
        insert_menu(tx, name, parent, path, &component, icon, sort, 2, perm, &now).await?;
    }

    let hash = password::hash("admin123")?;
    sqlx::query(
        "INSERT INTO sys_user (username, password, nickname, email, phone, status, pwd_reset, deleted, create_time, update_time)
         VALUES ('admin', ?, '超级管理员', 'admin@example.com', '13800138000', 0, 0, 0, ?, ?)",
    )
    .bind(hash)
    .bind(&now)
    .bind(&now)
    .execute(&mut **tx)
    .await?;
    let admin_id = last_id(&mut **tx).await?;
    sqlx::query("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)")
        .bind(admin_id)
        .bind(super_id)
        .execute(&mut **tx)
        .await?;
    for perm in perms {
        sqlx::query("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)")
            .bind(super_id)
            .bind(perm)
            .execute(&mut **tx)
            .await?;
    }

    for (key, value, typ, remark) in [
        ("sys.user.initPassword", "123456", "string", "用户初始密码"),
        ("sys.account.captchaEnabled", "true", "boolean", "是否启用验证码"),
        ("sys.account.captchaExpiration", "5", "number", "验证码有效期(分钟)"),
        ("sys.account.lockThreshold", "5", "number", "登录失败锁定阈值"),
        ("sys.account.lockDuration", "30", "number", "账号锁定时长(分钟)"),
    ] {
        insert_config(tx, key, value, typ, "system", remark, &now).await?;
    }

    let user_status = insert_dict_type(tx, "用户状态", "user_status", "用户账号状态", &now).await?;
    let role_status = insert_dict_type(tx, "角色状态", "role_status", "角色启用状态", &now).await?;
    let menu_type = insert_dict_type(tx, "菜单类型", "menu_type", "菜单节点类型", &now).await?;
    let perm_type = insert_dict_type(tx, "权限类型", "permission_type", "权限节点类型", &now).await?;
    let login_status = insert_dict_type(tx, "登录状态", "login_status", "登录成功/失败", &now).await?;
    for (type_id, label, value, sort) in [
        (user_status, "正常", "0", 1),
        (user_status, "禁用", "1", 2),
        (user_status, "锁定", "2", 3),
        (user_status, "过期", "3", 4),
        (role_status, "正常", "0", 1),
        (role_status, "停用", "1", 2),
        (menu_type, "目录", "1", 1),
        (menu_type, "菜单", "2", 2),
        (menu_type, "按钮", "3", 3),
        (perm_type, "菜单权限", "1", 1),
        (perm_type, "按钮权限", "2", 2),
        (perm_type, "API权限", "3", 3),
        (login_status, "成功", "0", 1),
        (login_status, "失败", "1", 2),
    ] {
        insert_dict_data(tx, type_id, label, value, sort, &now).await?;
    }
    Ok(())
}

async fn seed_p1(tx: &mut Transaction<'_, Sqlite>) -> Result<(), AppError> {
    let now = now_text();
    for (code, name, parent, sort) in [
        ("system:dept:view", "部门查看", "system", 51),
        ("system:dept:add", "部门新增", "system", 52),
        ("system:dept:edit", "部门编辑", "system", 53),
        ("system:dept:delete", "部门删除", "system", 54),
        ("system:file:view", "文件查看", "system", 61),
        ("system:file:delete", "文件删除", "system", 62),
        ("system:notice:view", "公告查看", "system", 71),
        ("system:notice:add", "公告新增", "system", 72),
        ("system:notice:edit", "公告编辑", "system", 73),
        ("system:notice:delete", "公告删除", "system", 74),
        ("system:message:send", "站内信发送", "system", 81),
        ("tools:codegen:generate", "代码生成", "tools", 11),
    ] {
        ensure_perm(tx, code, name, parent, sort, &now).await?;
    }
    ensure_config(
        tx,
        "sys.account.maxSessions",
        "0",
        "number",
        "system",
        "同时在线端数:0不限制,1单端,3最多三端",
        &now,
    )
    .await?;
    let system_id = menu_id(tx, "/system").await?.unwrap_or(0);
    let tools_id = menu_id(tx, "/tools").await?.unwrap_or(0);
    let monitor_id = menu_id(tx, "/monitor").await?.unwrap_or(0);
    if system_id != 0 {
        ensure_menu(tx, "部门管理", system_id, "/system/dept", "Building", 6, "system:dept:view", &now).await?;
        ensure_menu(tx, "文件管理", system_id, "/system/file", "Folder", 7, "system:file:view", &now).await?;
        ensure_menu(tx, "公告管理", system_id, "/system/notice", "Bell", 8, "system:notice:view", &now).await?;
    }
    if tools_id != 0 {
        sqlx::query("UPDATE sys_menu SET permission = 'tools:codegen:generate', update_time = ? WHERE path = '/tools/build' AND deleted = 0 AND permission IS NULL")
            .bind(&now)
            .execute(&mut **tx)
            .await?;
        reparent(tx, "/tools/dict", tools_id).await?;
        reparent(tx, "/tools/build", tools_id).await?;
    }
    if monitor_id != 0 {
        reparent(tx, "/monitor/login-log", monitor_id).await?;
        reparent(tx, "/monitor/op-log", monitor_id).await?;
        reparent(tx, "/monitor/error-log", monitor_id).await?;
    }
    let dept_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_dept WHERE deleted = 0")
        .fetch_one(&mut **tx)
        .await?;
    if dept_count == 0 {
        let root = insert_dept(tx, "总经办", 0, "0", 1, "管理员", &now).await?;
        let ancestors = format!("0,{root}");
        insert_dept(tx, "研发部", root, &ancestors, 2, "张经理", &now).await?;
        insert_dept(tx, "测试部", root, &ancestors, 3, "赵测试", &now).await?;
    }
    let first: Option<(i64, String)> = sqlx::query_as(
        "SELECT id, name FROM sys_dept WHERE deleted = 0 AND status = 0 ORDER BY sort ASC LIMIT 1",
    )
    .fetch_optional(&mut **tx)
    .await?;
    if let Some((id, name)) = first {
        sqlx::query("UPDATE sys_user SET dept_id = ?, dept_name = ? WHERE username = 'admin' AND deleted = 0 AND dept_id IS NULL")
            .bind(id)
            .bind(name)
            .execute(&mut **tx)
            .await?;
    }
    Ok(())
}

async fn insert_role(tx: &mut Transaction<'_, Sqlite>, name: &str, code: &str, sort: i64, scope: i64, remark: &str, now: &str) -> Result<i64, AppError> {
    sqlx::query(
        "INSERT INTO sys_role (name, code, sort, data_scope, status, remark, deleted, create_time, update_time) VALUES (?, ?, ?, ?, 0, ?, 0, ?, ?)",
    )
    .bind(name)
    .bind(code)
    .bind(sort)
    .bind(scope)
    .bind(remark)
    .bind(now)
    .bind(now)
    .execute(&mut **tx)
    .await?;
    Ok(last_id(&mut **tx).await?)
}

async fn insert_perm(tx: &mut Transaction<'_, Sqlite>, name: &str, code: &str, typ: i64, parent: i64, sort: i64, now: &str) -> Result<i64, AppError> {
    sqlx::query(
        "INSERT INTO sys_permission (name, code, type, parent_id, sort, status, deleted, create_time, update_time) VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?)",
    )
    .bind(name)
    .bind(code)
    .bind(typ)
    .bind(parent)
    .bind(sort)
    .bind(now)
    .bind(now)
    .execute(&mut **tx)
    .await?;
    Ok(last_id(&mut **tx).await?)
}

async fn insert_menu(
    tx: &mut Transaction<'_, Sqlite>,
    name: &str,
    parent: i64,
    path: &str,
    component: &str,
    icon: &str,
    sort: i64,
    typ: i64,
    permission: Option<&str>,
    now: &str,
) -> Result<i64, AppError> {
    sqlx::query(
        "INSERT INTO sys_menu (name, parent_id, path, component, icon, sort, type, permission, hidden, status, deleted, create_time, update_time)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?)",
    )
    .bind(name)
    .bind(parent)
    .bind(path)
    .bind(component)
    .bind(icon)
    .bind(sort)
    .bind(typ)
    .bind(permission)
    .bind(now)
    .bind(now)
    .execute(&mut **tx)
    .await?;
    Ok(last_id(&mut **tx).await?)
}

async fn insert_config(tx: &mut Transaction<'_, Sqlite>, key: &str, value: &str, typ: &str, group: &str, remark: &str, now: &str) -> Result<(), AppError> {
    sqlx::query(
        "INSERT INTO sys_config (config_key, config_value, config_type, group_code, remark, deleted, create_time, update_time) VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
    )
    .bind(key)
    .bind(value)
    .bind(typ)
    .bind(group)
    .bind(remark)
    .bind(now)
    .bind(now)
    .execute(&mut **tx)
    .await?;
    Ok(())
}

async fn insert_dict_type(tx: &mut Transaction<'_, Sqlite>, name: &str, code: &str, remark: &str, now: &str) -> Result<i64, AppError> {
    sqlx::query(
        "INSERT INTO sys_dict_type (name, code, status, remark, deleted, create_time, update_time) VALUES (?, ?, 0, ?, 0, ?, ?)",
    )
    .bind(name)
    .bind(code)
    .bind(remark)
    .bind(now)
    .bind(now)
    .execute(&mut **tx)
    .await?;
    Ok(last_id(&mut **tx).await?)
}

async fn insert_dict_data(tx: &mut Transaction<'_, Sqlite>, type_id: i64, label: &str, value: &str, sort: i64, now: &str) -> Result<(), AppError> {
    sqlx::query(
        "INSERT INTO sys_dict_data (dict_type_id, label, value, sort, status, deleted, create_time, update_time) VALUES (?, ?, ?, ?, 0, 0, ?, ?)",
    )
    .bind(type_id)
    .bind(label)
    .bind(value)
    .bind(sort)
    .bind(now)
    .bind(now)
    .execute(&mut **tx)
    .await?;
    Ok(())
}

async fn ensure_perm(tx: &mut Transaction<'_, Sqlite>, code: &str, name: &str, parent_code: &str, sort: i64, now: &str) -> Result<(), AppError> {
    let exists: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_permission WHERE code = ? AND deleted = 0")
        .bind(code)
        .fetch_one(&mut **tx)
        .await?;
    if exists > 0 {
        return Ok(());
    }
    let parent: Option<i64> = sqlx::query_scalar("SELECT id FROM sys_permission WHERE code = ? AND deleted = 0")
        .bind(parent_code)
        .fetch_optional(&mut **tx)
        .await?;
    insert_perm(tx, name, code, 2, parent.unwrap_or(0), sort, now).await?;
    Ok(())
}

async fn ensure_config(tx: &mut Transaction<'_, Sqlite>, key: &str, value: &str, typ: &str, group: &str, remark: &str, now: &str) -> Result<(), AppError> {
    let exists: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_config WHERE config_key = ? AND deleted = 0")
        .bind(key)
        .fetch_one(&mut **tx)
        .await?;
    if exists == 0 {
        insert_config(tx, key, value, typ, group, remark, now).await?;
    }
    Ok(())
}

async fn menu_id(tx: &mut Transaction<'_, Sqlite>, path: &str) -> Result<Option<i64>, AppError> {
    Ok(sqlx::query_scalar("SELECT id FROM sys_menu WHERE path = ? AND deleted = 0 LIMIT 1")
        .bind(path)
        .fetch_optional(&mut **tx)
        .await?)
}

async fn ensure_menu(tx: &mut Transaction<'_, Sqlite>, name: &str, parent: i64, path: &str, icon: &str, sort: i64, permission: &str, now: &str) -> Result<(), AppError> {
    let exists: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sys_menu WHERE path = ? AND deleted = 0")
        .bind(path)
        .fetch_one(&mut **tx)
        .await?;
    if exists == 0 {
        let component = format!("{path}/index");
        insert_menu(tx, name, parent, path, &component, icon, sort, 2, Some(permission), now).await?;
    }
    Ok(())
}

async fn reparent(tx: &mut Transaction<'_, Sqlite>, path: &str, parent: i64) -> Result<(), AppError> {
    sqlx::query("UPDATE sys_menu SET parent_id = ? WHERE path = ? AND deleted = 0 AND (parent_id IS NULL OR parent_id <> ?)")
        .bind(parent)
        .bind(path)
        .bind(parent)
        .execute(&mut **tx)
        .await?;
    Ok(())
}

async fn insert_dept(tx: &mut Transaction<'_, Sqlite>, name: &str, parent: i64, ancestors: &str, sort: i64, leader: &str, now: &str) -> Result<i64, AppError> {
    sqlx::query(
        "INSERT INTO sys_dept (name, parent_id, ancestors, sort, leader, status, deleted, create_time, update_time) VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?)",
    )
    .bind(name)
    .bind(parent)
    .bind(ancestors)
    .bind(sort)
    .bind(leader)
    .bind(now)
    .bind(now)
    .execute(&mut **tx)
    .await?;
    Ok(last_id(&mut **tx).await?)
}
