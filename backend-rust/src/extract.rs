use axum::extract::{FromRequest, FromRequestParts, Query, Request};
use axum::http::request::Parts;
use axum::Json;
use serde::de::DeserializeOwned;

use crate::error::AppError;
use crate::state::{AuthUser, ReqMeta};

pub struct Auth(pub AuthUser);

impl<S> FromRequestParts<S> for Auth
where
    S: Send + Sync,
{
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, _: &S) -> Result<Self, Self::Rejection> {
        parts
            .extensions
            .get::<AuthUser>()
            .cloned()
            .map(Auth)
            .ok_or_else(|| AppError::http_unauthorized("未登录或登录已过期"))
    }
}

pub struct Meta(pub ReqMeta);

impl<S> FromRequestParts<S> for Meta
where
    S: Send + Sync,
{
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, _: &S) -> Result<Self, Self::Rejection> {
        parts
            .extensions
            .get::<ReqMeta>()
            .cloned()
            .map(Meta)
            .ok_or_else(|| AppError::bad("缺少必要参数"))
    }
}

pub struct JsonBody<T>(pub T);

impl<S, T> FromRequest<S> for JsonBody<T>
where
    S: Send + Sync,
    T: DeserializeOwned,
{
    type Rejection = AppError;

    async fn from_request(req: Request, state: &S) -> Result<Self, Self::Rejection> {
        match Json::<T>::from_request(req, state).await {
            Ok(Json(value)) => Ok(JsonBody(value)),
            Err(_) => Err(AppError::bad("请求体格式错误")),
        }
    }
}

pub struct Q<T>(pub T);

impl<S, T> FromRequestParts<S> for Q<T>
where
    S: Send + Sync,
    T: DeserializeOwned,
{
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        match Query::<T>::from_request_parts(parts, state).await {
            Ok(Query(value)) => Ok(Q(value)),
            Err(_) => Err(AppError::bad("参数校验失败")),
        }
    }
}
