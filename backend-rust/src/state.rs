use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use serde::{Deserialize, Serialize};
use sqlx::SqlitePool;

use crate::cache::MemoryCache;
use crate::config::AppConfig;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Device {
    pub sid: String,
    pub iat: i64,
    pub ip: String,
    pub ua: String,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct SessionState {
    #[serde(default)]
    pub devices: Vec<Device>,
    #[serde(default)]
    pub kicked: Vec<String>,
}

#[derive(Clone)]
pub struct AppState {
    pub db: SqlitePool,
    pub cfg: Arc<AppConfig>,
    pub cache: Arc<MemoryCache>,
    pub sessions: Arc<Mutex<HashMap<i64, SessionState>>>,
    pub jti_until: Arc<Mutex<HashMap<String, i64>>>,
    pub user_invalid_before: Arc<Mutex<HashMap<i64, i64>>>,
}

#[derive(Clone, Debug)]
pub struct ReqMeta {
    pub ip: String,
    pub ua: String,
    pub path: String,
    pub method: String,
}

#[derive(Clone, Debug)]
pub struct AuthUser {
    pub id: i64,
    pub username: String,
    pub roles: Vec<String>,
    pub permissions: Vec<String>,
    pub sid: String,
    pub jti: String,
    pub iat_ms: i64,
    pub must_change_password: bool,
}

impl AuthUser {
    pub fn has_perm(&self, permission: &str) -> bool {
        if permission.is_empty() {
            return false;
        }
        self.permissions.iter().any(|p| p == "*" || p == permission)
            || self.roles.iter().any(|r| r == "SUPER_ADMIN")
    }

    pub fn is_super(&self) -> bool {
        self.roles.iter().any(|r| r == "SUPER_ADMIN")
    }

    pub fn is_privileged(&self) -> bool {
        self.is_super() || self.permissions.iter().any(|p| p == "*")
    }
}
