use std::path::PathBuf;
use std::time::Duration;

#[derive(Clone, Debug)]
pub struct AppConfig {
    pub host: String,
    pub port: u16,
    pub db_path: PathBuf,
    pub upload_dir: PathBuf,
    pub jwt_secret: String,
    pub access_ttl: Duration,
    pub refresh_ttl: Duration,
    pub remember_ttl: Duration,
    pub cors_origins: Vec<String>,
    pub trusted_proxy: bool,
    pub mail_mock: bool,
}

impl AppConfig {
    pub fn from_env() -> Self {
        let db = std::env::var("S2ADMIN_DB").unwrap_or_else(|_| "data/s2admin.db".into());
        let upload = std::env::var("UPLOAD_DIR").unwrap_or_else(|_| "uploads".into());
        let port = std::env::var("PORT")
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(8080);
        let origins = std::env::var("CORS_ORIGINS")
            .unwrap_or_else(|_| "http://localhost:5173,http://127.0.0.1:5173".into());
        Self {
            host: std::env::var("HOST").unwrap_or_else(|_| "0.0.0.0".into()),
            port,
            db_path: PathBuf::from(db),
            upload_dir: PathBuf::from(upload),
            jwt_secret: std::env::var("JWT_SECRET").unwrap_or_else(|_| {
                "s2admin-secret-key-change-in-production-minimum-32-characters".into()
            }),
            access_ttl: Duration::from_millis(env_u64("JWT_ACCESS_TTL_MS", 900_000)),
            refresh_ttl: Duration::from_millis(env_u64("JWT_REFRESH_TTL_MS", 604_800_000)),
            remember_ttl: Duration::from_millis(env_u64("JWT_REMEMBER_TTL_MS", 2_592_000_000)),
            cors_origins: origins
                .split(',')
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .collect(),
            trusted_proxy: env_bool("TRUSTED_PROXY", false),
            mail_mock: env_bool("MAIL_MOCK", true),
        }
    }
}

fn env_u64(key: &str, default: u64) -> u64 {
    std::env::var(key)
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(default)
}

fn env_bool(key: &str, default: bool) -> bool {
    match std::env::var(key) {
        Ok(v) => matches!(v.as_str(), "1" | "true" | "TRUE" | "yes"),
        Err(_) => default,
    }
}
