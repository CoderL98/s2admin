use serde::Serialize;

use crate::util::now_millis;

#[derive(Debug, Serialize)]
pub struct ApiBody<T> {
    pub code: i32,
    pub message: String,
    pub data: T,
    pub timestamp: i64,
}

pub fn ok<T: Serialize>(data: T) -> ApiBody<T> {
    ApiBody {
        code: 200,
        message: "success".into(),
        data,
        timestamp: now_millis(),
    }
}

pub fn ok_null() -> ApiBody<Option<()>> {
    ok(None)
}

#[derive(Debug, Serialize)]
pub struct Page<T> {
    pub records: Vec<T>,
    pub total: i64,
    pub size: i64,
    pub current: i64,
    pub pages: i64,
}

impl<T> Page<T> {
    pub fn new(records: Vec<T>, total: i64, size: i64, current: i64) -> Self {
        let pages = if size <= 0 || total <= 0 {
            0
        } else {
            (total + size - 1) / size
        };
        Self {
            records,
            total,
            size,
            current,
            pages,
        }
    }
}
