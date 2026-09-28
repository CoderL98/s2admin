use std::collections::HashSet;

use crate::error::AppError;
use crate::state::{AppState, AuthUser};
use crate::util::ancestor_hit;

#[derive(Clone, Debug)]
pub struct Scope {
    pub all: bool,
    pub self_only: bool,
    pub dept_ids: HashSet<i64>,
    pub level: i32,
}

pub async fn current(state: &AppState, user: &AuthUser) -> Result<Scope, AppError> {
    if user.permissions.iter().any(|p| p == "*") || user.is_super() {
        return Ok(Scope {
            all: true,
            self_only: false,
            dept_ids: HashSet::new(),
            level: 1,
        });
    }
    let dept_id: Option<Option<i64>> = sqlx::query_scalar("SELECT dept_id FROM sys_user WHERE id = ? AND deleted = 0")
        .bind(user.id)
        .fetch_optional(&state.db)
        .await?;
    let Some(dept_id) = dept_id else {
        return Ok(self_scope());
    };
    let roles: Vec<(Option<i64>, Option<i64>)> = sqlx::query_as(
        "SELECT r.data_scope, r.status FROM sys_role r
         JOIN sys_user_role ur ON ur.role_id = r.id
         WHERE ur.user_id = ? AND r.deleted = 0",
    )
    .bind(user.id)
    .fetch_all(&state.db)
    .await?;
    let mut best = 4i32;
    for (scope, status) in &roles {
        if status.unwrap_or(0) != 0 {
            continue;
        }
        best = best.min(scope.unwrap_or(4) as i32);
    }
    if best <= 1 {
        return Ok(Scope {
            all: true,
            self_only: false,
            dept_ids: HashSet::new(),
            level: 1,
        });
    }
    let Some(dept_id) = dept_id else {
        return Ok(self_scope());
    };
    if best >= 4 {
        return Ok(self_scope());
    }
    let mut ids = HashSet::from([dept_id]);
    if best == 2 {
        ids.extend(descendant_ids(state, dept_id).await?);
    }
    Ok(Scope {
        all: false,
        self_only: false,
        dept_ids: ids,
        level: best,
    })
}

fn self_scope() -> Scope {
    Scope { all: false, self_only: true, dept_ids: HashSet::new(), level: 4 }
}

pub async fn descendant_ids(state: &AppState, dept_id: i64) -> Result<HashSet<i64>, AppError> {
    let rows: Vec<(i64, Option<String>)> = sqlx::query_as(
        "SELECT id, ancestors FROM sys_dept WHERE deleted = 0",
    )
    .fetch_all(&state.db)
    .await?;
    let mut ids = HashSet::new();
    for (id, ancestors) in rows {
        if id == dept_id {
            continue;
        }
        if ancestors.as_deref().is_some_and(|a| ancestor_hit(a, dept_id)) {
            ids.insert(id);
        }
    }
    Ok(ids)
}

pub async fn assert_can_assign_data_scope(state: &AppState, user: &AuthUser, data_scope: Option<i64>) -> Result<(), AppError> {
    let requested = data_scope.unwrap_or(4) as i32;
    if !(1..=4).contains(&requested) {
        return Err(AppError::bad("数据范围不合法"));
    }
    let mine = current(state, user).await?;
    if mine.all {
        return Ok(());
    }
    if requested < mine.level {
        return Err(AppError::bad("不能设置比自己更宽的数据范围"));
    }
    Ok(())
}

pub async fn can_access_user(state: &AppState, user: &AuthUser, target_id: i64, target_dept: Option<i64>) -> Result<bool, AppError> {
    let scope = current(state, user).await?;
    if scope.all || user.id == target_id {
        return Ok(true);
    }
    if scope.self_only {
        return Ok(false);
    }
    Ok(target_dept.is_some_and(|id| scope.dept_ids.contains(&id)))
}

pub async fn assert_can_access_user(state: &AppState, user: &AuthUser, target_id: i64, target_dept: Option<i64>) -> Result<(), AppError> {
    if can_access_user(state, user, target_id, target_dept).await? {
        Ok(())
    } else {
        Err(AppError::forbidden("超出数据权限范围"))
    }
}

pub async fn assert_can_assign_dept(state: &AppState, user: &AuthUser, dept_id: Option<i64>) -> Result<(), AppError> {
    let scope = current(state, user).await?;
    if scope.all {
        return Ok(());
    }
    if scope.self_only {
        return Err(AppError::forbidden("当前数据权限不允许操作其他用户"));
    }
    match dept_id {
        Some(id) if scope.dept_ids.contains(&id) => Ok(()),
        _ => Err(AppError::forbidden("不能操作该部门的数据")),
    }
}

pub async fn visible_user_ids(state: &AppState, user: &AuthUser) -> Result<Option<Vec<i64>>, AppError> {
    let scope = current(state, user).await?;
    if scope.all {
        return Ok(None);
    }
    let mut ids = HashSet::from([user.id]);
    if !scope.self_only && !scope.dept_ids.is_empty() {
        let list: Vec<i64> = {
            let mut qb = sqlx::QueryBuilder::<sqlx::Sqlite>::new(
                "SELECT id FROM sys_user WHERE deleted = 0 AND dept_id IN (",
            );
            let mut separated = qb.separated(", ");
            for id in &scope.dept_ids {
                separated.push_bind(*id);
            }
            separated.push_unseparated(")");
            qb.build_query_scalar().fetch_all(&state.db).await?
        };
        ids.extend(list);
    }
    let mut out: Vec<i64> = ids.into_iter().collect();
    out.sort_unstable();
    Ok(Some(out))
}

pub async fn assert_can_access_owner(state: &AppState, user: &AuthUser, owner_id: Option<i64>) -> Result<(), AppError> {
    let Some(ids) = visible_user_ids(state, user).await? else {
        return Ok(());
    };
    if owner_id.is_some_and(|id| ids.contains(&id)) {
        Ok(())
    } else {
        Err(AppError::forbidden("超出数据权限范围"))
    }
}

pub async fn assert_can_access_dept(state: &AppState, user: &AuthUser, dept_id: Option<i64>) -> Result<(), AppError> {
    let scope = current(state, user).await?;
    if scope.all {
        return Ok(());
    }
    let Some(dept_id) = dept_id else {
        return Err(AppError::forbidden("超出数据权限范围"));
    };
    if scope.self_only {
        let mine: Option<i64> = sqlx::query_scalar("SELECT dept_id FROM sys_user WHERE id = ? AND deleted = 0")
            .bind(user.id)
            .fetch_optional(&state.db)
            .await?;
        if mine == Some(dept_id) {
            Ok(())
        } else {
            Err(AppError::forbidden("超出数据权限范围"))
        }
    } else if scope.dept_ids.contains(&dept_id) {
        Ok(())
    } else {
        Err(AppError::forbidden("超出数据权限范围"))
    }
}

pub async fn visible_dept_ids_with_ancestors(state: &AppState, user: &AuthUser) -> Result<Option<HashSet<i64>>, AppError> {
    let scope = current(state, user).await?;
    if scope.all {
        return Ok(None);
    }
    let mut allowed = HashSet::new();
    if scope.self_only {
        let mine: Option<i64> = sqlx::query_scalar("SELECT dept_id FROM sys_user WHERE id = ? AND deleted = 0")
            .bind(user.id)
            .fetch_optional(&state.db)
            .await?;
        if let Some(id) = mine {
            allowed.insert(id);
        }
    } else {
        allowed.extend(scope.dept_ids.iter().copied());
    }
    let depts: Vec<(i64, Option<String>)> = sqlx::query_as("SELECT id, ancestors FROM sys_dept WHERE deleted = 0")
        .fetch_all(&state.db)
        .await?;
    let mut with = allowed.clone();
    for (id, ancestors) in depts {
        if !allowed.contains(&id) {
            continue;
        }
        if let Some(ancestors) = ancestors {
            for part in ancestors.split(',') {
                let part = part.trim();
                if !part.is_empty() && part != "0" {
                    if let Ok(n) = part.parse::<i64>() {
                        with.insert(n);
                    }
                }
            }
        }
    }
    Ok(Some(with))
}

pub fn push_user_scope(qb: &mut sqlx::QueryBuilder<'_, sqlx::Sqlite>, scope: &Scope, user_id: i64, alias: &str) {
    if scope.all {
        return;
    }
    if scope.self_only || scope.dept_ids.is_empty() {
        qb.push(format!(" AND {alias}.id = "));
        qb.push_bind(user_id);
        return;
    }
    qb.push(format!(" AND ({alias}.id = "));
    qb.push_bind(user_id);
    qb.push(format!(" OR {alias}.dept_id IN ("));
    let mut sep = qb.separated(", ");
    for id in &scope.dept_ids {
        sep.push_bind(*id);
    }
    sep.push_unseparated("))");
}

pub fn push_owner_scope(qb: &mut sqlx::QueryBuilder<'_, sqlx::Sqlite>, ids: &Option<Vec<i64>>, column: &str) {
    let Some(ids) = ids else {
        return;
    };
    if ids.is_empty() {
        qb.push(" AND 1 = 0");
        return;
    }
    qb.push(format!(" AND {column} IN ("));
    let mut sep = qb.separated(", ");
    for id in ids {
        sep.push_bind(*id);
    }
    sep.push_unseparated(")");
}
