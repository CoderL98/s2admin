use std::sync::OnceLock;

use sqlx::sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePoolOptions};
use sqlx::SqlitePool;

use crate::config::AppConfig;
use crate::util::{client_ip, now_text};

static ERROR_DB: OnceLock<SqlitePool> = OnceLock::new();

pub async fn connect(cfg: &AppConfig) -> Result<SqlitePool, sqlx::Error> {
    if let Some(parent) = cfg.db_path.parent() {
        if !parent.as_os_str().is_empty() {
            std::fs::create_dir_all(parent).ok();
        }
    }
    std::fs::create_dir_all(&cfg.upload_dir).ok();
    let options = SqliteConnectOptions::new()
        .filename(&cfg.db_path)
        .create_if_missing(true)
        .journal_mode(SqliteJournalMode::Wal)
        .busy_timeout(std::time::Duration::from_secs(10))
        .foreign_keys(true);
    let pool = SqlitePoolOptions::new()
        .max_connections(8)
        .connect_with(options)
        .await?;
    sqlx::query("PRAGMA busy_timeout = 10000")
        .execute(&pool)
        .await?;
    migrate(&pool).await?;
    let _ = ERROR_DB.set(pool.clone());
    Ok(pool)
}

async fn migrate(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    sqlx::raw_sql(include_str!("../sql/schema.sql")).execute(pool).await?;
    for (table, column, definition) in [
        ("sys_user", "pwd_reset", "INTEGER DEFAULT 0"),
        ("sys_user", "province", "VARCHAR(50)"),
        ("sys_user", "city", "VARCHAR(50)"),
        ("sys_user", "district", "VARCHAR(50)"),
    ] {
        let exists: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM pragma_table_info(?) WHERE name = ?",
        )
        .bind(table)
        .bind(column)
        .fetch_one(pool)
        .await
        .unwrap_or(0);
        if exists == 0 {
            let sql = format!("ALTER TABLE {table} ADD COLUMN {column} {definition}");
            let _ = sqlx::query(&sql).execute(pool).await;
        }
    }
    Ok(())
}

pub fn spawn_error_log(detail: &str) {
    let Some(db) = ERROR_DB.get().cloned() else {
        return;
    };
    let detail = detail.to_string();
    let trace = uuid::Uuid::new_v4().simple().to_string();
    let trace = trace.chars().take(16).collect::<String>();
    let when = now_text();
    tokio::spawn(async move {
        let _ = sqlx::query(
            "INSERT INTO sys_error_log (trace_id, exception, stack_trace, error_time, ip, url, method) VALUES (?, ?, ?, ?, '', '', '')",
        )
        .bind(trace)
        .bind(format!("Internal: {detail}"))
        .bind(detail.chars().take(4000).collect::<String>())
        .bind(when)
        .execute(&db)
        .await;
    });
}

pub fn peer_ip(parts: &axum::http::request::Parts, trusted: bool) -> String {
    let peer = parts
        .extensions
        .get::<axum::extract::ConnectInfo<std::net::SocketAddr>>()
        .map(|c| c.0.ip().to_string())
        .unwrap_or_default();
    let forwarded = header(parts, "x-forwarded-for");
    let real = header(parts, "x-real-ip");
    client_ip(trusted, forwarded.as_deref(), real.as_deref(), &peer)
}

pub fn header(parts: &axum::http::request::Parts, name: &str) -> Option<String> {
    parts
        .headers
        .get(name)
        .and_then(|v| v.to_str().ok())
        .map(|s| s.to_string())
}

pub async fn last_id(exec: impl sqlx::Executor<'_, Database = sqlx::Sqlite>) -> Result<i64, sqlx::Error> {
    sqlx::query_scalar("SELECT last_insert_rowid()").fetch_one(exec).await
}
