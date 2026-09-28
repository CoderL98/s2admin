use std::net::SocketAddr;
use std::time::{SystemTime, UNIX_EPOCH};

use s2admin_rs::{boot, AppConfig};
use serde_json::{json, Value};
use tokio::net::TcpListener;

async fn start() -> (String, sqlx::SqlitePool) {
    let nonce = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
    let dir = std::env::temp_dir().join(format!("s2admin-rs-{nonce}"));
    std::fs::create_dir_all(&dir).unwrap();
    let cfg = AppConfig {
        host: "127.0.0.1".into(),
        port: 0,
        db_path: dir.join("s2admin.db"),
        upload_dir: dir.join("uploads"),
        jwt_secret: "s2admin-secret-key-change-in-production-minimum-32-characters".into(),
        access_ttl: std::time::Duration::from_secs(900),
        refresh_ttl: std::time::Duration::from_secs(7 * 24 * 3600),
        remember_ttl: std::time::Duration::from_secs(30 * 24 * 3600),
        cors_origins: vec!["http://localhost:5173".into()],
        trusted_proxy: false,
        mail_mock: true,
    };
    let (app, state) = boot(cfg).await.expect("boot");
    sqlx::query("UPDATE sys_config SET config_value = 'false' WHERE config_key = 'sys.account.captchaEnabled'")
        .execute(&state.db)
        .await
        .unwrap();
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    tokio::spawn(async move {
        axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>())
            .await
            .unwrap();
    });
    (format!("http://{addr}"), state.db)
}

fn client() -> reqwest::Client {
    reqwest::Client::new()
}

async fn login(base: &str) -> Value {
    let res = client()
        .post(format!("{base}/api/auth/login"))
        .json(&json!({"username": "admin", "password": "admin123", "rememberMe": true}))
        .send()
        .await
        .unwrap();
    assert_eq!(res.status(), 200);
    let body: Value = res.json().await.unwrap();
    assert_eq!(body["code"], 200, "{body}");
    assert!(body["data"]["token"].as_str().unwrap().len() > 20);
    assert_eq!(body["data"]["expiresIn"], 900);
    assert!(body["data"]["user"]["permissions"].as_array().unwrap().iter().any(|p| p == "*"));
    body
}

#[tokio::test]
async fn auth_users_and_modules() {
    let (base, _db) = start().await;
    let http = client();
    let anon = http.get(format!("{base}/api/auth/info")).send().await.unwrap();
    assert_eq!(anon.status(), 401);
    let anon_body: Value = anon.json().await.unwrap();
    assert_eq!(anon_body["message"], "未登录或登录已过期");

    let bad = http
        .post(format!("{base}/api/auth/login"))
        .json(&json!({"username": "admin", "password": "wrong-password"}))
        .send()
        .await
        .unwrap();
    let bad_body: Value = bad.json().await.unwrap();
    assert_eq!(bad_body["code"], 401);
    assert_eq!(bad_body["message"], "用户名或密码错误");

    let session = login(&base).await;
    let token = session["data"]["token"].as_str().unwrap();
    let refresh = session["data"]["refreshToken"].as_str().unwrap();
    let auth = format!("Bearer {token}");

    let info = http
        .get(format!("{base}/api/auth/info"))
        .header("Authorization", &auth)
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert_eq!(info["data"]["username"], "admin");

    let missing = http
        .get(format!("{base}/api/not-exists"))
        .header("Authorization", &auth)
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert_eq!(missing["code"], 404);

    let menus = http
        .get(format!("{base}/api/auth/menus"))
        .header("Authorization", &auth)
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert!(menus["data"].as_array().unwrap().iter().any(|m| m["path"] == "/system"));

    let users = http
        .get(format!("{base}/api/system/user?pageNum=1&pageSize=10"))
        .header("Authorization", &auth)
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert_eq!(users["code"], 200, "{users}");
    assert!(users["data"]["total"].as_i64().unwrap() >= 1);

    let weak = http
        .post(format!("{base}/api/system/user"))
        .header("Authorization", &auth)
        .json(&json!({"username": "demo_user", "nickname": "演示", "password": "admin123"}))
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert_eq!(weak["code"], 400, "{weak}");

    let created = http
        .post(format!("{base}/api/system/user"))
        .header("Authorization", &auth)
        .json(&json!({
            "username": "demo_user",
            "nickname": "演示",
            "password": "Admin123!",
            "email": "demo_user@example.com",
            "phone": "13900001111"
        }))
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert_eq!(created["code"], 200, "{created}");
    assert_eq!(created["data"]["username"], "demo_user");
    let user_id = created["data"]["id"].as_i64().unwrap();

    let depts = http
        .get(format!("{base}/api/system/dept/tree"))
        .header("Authorization", &auth)
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert_eq!(depts["data"][0]["name"], "总经办");

    let dict = http
        .get(format!("{base}/api/system/dict/data/type/user_status"))
        .header("Authorization", &auth)
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert!(dict["data"].as_array().unwrap().len() >= 4);

    let stats = http
        .get(format!("{base}/api/system/dashboard/stats"))
        .header("Authorization", &auth)
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert!(stats["data"]["userCount"].as_i64().unwrap() >= 2);

    let refreshed = http
        .post(format!("{base}/api/auth/refresh"))
        .json(&json!({"refreshToken": refresh}))
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert_eq!(refreshed["code"], 200, "{refreshed}");
    assert_ne!(refreshed["data"]["refreshToken"], refresh);

    let reused = http
        .post(format!("{base}/api/auth/refresh"))
        .json(&json!({"refreshToken": refresh}))
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
    assert_eq!(reused["code"], 401);

    let zip = http
        .post(format!("{base}/api/tools/codegen"))
        .header("Authorization", &auth)
        .json(&json!({
            "module": "demo",
            "entity": "Item",
            "tableName": "biz_item",
            "permission": "demo:item",
            "path": "/demo/item",
            "fields": [{"name": "title", "javaType": "String", "label": "标题", "query": true, "required": true}]
        }))
        .send()
        .await
        .unwrap();
    assert_eq!(zip.status(), 200);
    let bytes = zip.bytes().await.unwrap();
    assert!(bytes.starts_with(b"PK"));

    http.delete(format!("{base}/api/system/user/{user_id}"))
        .header("Authorization", &auth)
        .send()
        .await
        .unwrap()
        .json::<Value>()
        .await
        .unwrap();
}
