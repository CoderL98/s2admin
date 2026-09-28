use std::time::Duration;

use crate::error::AppError;
use crate::state::AppState;

pub async fn get_value(state: &AppState, key: &str, default_value: &str) -> String {
    let cache_key = format!("s2admin:config:{key}");
    if let Some(cached) = state.cache.get(&cache_key) {
        return cached;
    }
    let row: Option<String> = sqlx::query_scalar(
        "SELECT config_value FROM sys_config WHERE config_key = ? AND deleted = 0",
    )
    .bind(key)
    .fetch_optional(&state.db)
    .await
    .ok()
    .flatten();
    match row {
        Some(value) => {
            state.cache.set(&cache_key, &value, Duration::from_secs(3600));
            value
        }
        None => default_value.to_string(),
    }
}

pub async fn get_bool(state: &AppState, key: &str, default_value: bool) -> bool {
    let value = get_value(state, key, if default_value { "true" } else { "false" }).await;
    value.eq_ignore_ascii_case("true") || value == "1"
}

pub async fn get_int(state: &AppState, key: &str, default_value: i64) -> i64 {
    let value = get_value(state, key, &default_value.to_string()).await;
    value.trim().parse().unwrap_or(default_value)
}

pub fn evict(state: &AppState, key: &str) {
    state.cache.delete(&format!("s2admin:config:{key}"));
}

pub async fn ensure_ready(state: &AppState) -> Result<(), AppError> {
    let _ = state;
    Ok(())
}
