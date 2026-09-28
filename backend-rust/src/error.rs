use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;

use crate::response::ApiBody;

#[derive(Debug)]
pub enum AppError {
    Biz {
        http: StatusCode,
        code: i32,
        message: String,
    },
    Internal(String),
}

impl std::fmt::Display for AppError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.log_message())
    }
}

impl std::error::Error for AppError {}

impl AppError {
    pub fn bad(message: impl Into<String>) -> Self {
        Self::Biz {
            http: StatusCode::OK,
            code: 400,
            message: message.into(),
        }
    }

    pub fn unauthorized(message: impl Into<String>) -> Self {
        Self::Biz {
            http: StatusCode::OK,
            code: 401,
            message: message.into(),
        }
    }

    pub fn forbidden(message: impl Into<String>) -> Self {
        Self::Biz {
            http: StatusCode::OK,
            code: 403,
            message: message.into(),
        }
    }

    pub fn not_found(message: impl Into<String>) -> Self {
        Self::Biz {
            http: StatusCode::OK,
            code: 404,
            message: message.into(),
        }
    }

    pub fn too_many(message: impl Into<String>) -> Self {
        Self::Biz {
            http: StatusCode::OK,
            code: 429,
            message: message.into(),
        }
    }

    pub fn http_unauthorized(message: impl Into<String>) -> Self {
        Self::Biz {
            http: StatusCode::UNAUTHORIZED,
            code: 401,
            message: message.into(),
        }
    }

    pub fn http_forbidden(message: impl Into<String>) -> Self {
        Self::Biz {
            http: StatusCode::FORBIDDEN,
            code: 403,
            message: message.into(),
        }
    }

    pub fn log_message(&self) -> String {
        match self {
            Self::Biz { message, .. } => message.clone(),
            Self::Internal(message) => message.clone(),
        }
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        match self {
            Self::Biz { http, code, message } => {
                let body = ApiBody {
                    code,
                    message,
                    data: None::<()>,
                    timestamp: crate::util::now_millis(),
                };
                (http, Json(body)).into_response()
            }
            Self::Internal(detail) => {
                tracing::error!("系统异常: {detail}");
                crate::db::spawn_error_log(&detail);
                let body = ApiBody {
                    code: 500,
                    message: "系统繁忙,请稍后重试".to_string(),
                    data: None::<()>,
                    timestamp: crate::util::now_millis(),
                };
                (StatusCode::OK, Json(body)).into_response()
            }
        }
    }
}

impl From<sqlx::Error> for AppError {
    fn from(err: sqlx::Error) -> Self {
        let text = err.to_string();
        if text.contains("UNIQUE") || text.contains("constraint") {
            Self::bad("数据冲突:唯一键重复或存在关联数据,操作被拒绝")
        } else {
            Self::Internal(text)
        }
    }
}

pub type ApiResult<T> = Result<T, AppError>;
