# s2admin Rust 后端

与现有 Spring Boot 后端对齐的一版 Rust 实现。默认使用 SQLite、进程内缓存和本地文件存储，HTTP 接口、统一响应、JWT 和权限模型与 Java 版保持一致，前端可以把 `PUBLIC_API_BASE_URL` 指到本服务。

## 运行

```bash
cd backend-rust
cargo run
```

默认监听 `0.0.0.0:8080`。数据库文件是 `backend-rust/data/s2admin.db`，空库会写入和 Java 版相同的种子数据。

| 账号 | 密码 |
| --- | --- |
| admin | admin123 |

如果本机 `8080` 已被 Java 后端占用，换一个端口：

```bash
PORT=18080 cargo run
```

## 环境变量

| 变量 | 默认 | 作用 |
| --- | --- | --- |
| `PORT` | `8080` | 监听端口 |
| `HOST` | `0.0.0.0` | 监听地址 |
| `S2ADMIN_DB` | `data/s2admin.db` | SQLite 文件 |
| `JWT_SECRET` | 与 Java 默认密钥相同 | HS384 签名密钥 |
| `CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 允许的来源，逗号分隔；`*` 表示任意来源且不带凭证 |
| `UPLOAD_DIR` | `uploads` | 本地上传目录 |
| `TRUSTED_PROXY` | `false` | 为 true 时才读取 `X-Forwarded-For` |
| `MAIL_MOCK` | `true` | 找回密码时在响应里返回 `mockCode` |

AccessToken 15 分钟，RefreshToken 7 天，勾选记住我时 30 天。

## 和 Java 版的对应关系

- 响应体仍是 `{ code, message, data, timestamp }`。业务错误多数是 HTTP 200 加业务码；未登录是 HTTP 401，无权限和“请先修改初始密码”是 HTTP 403。
- 权限码、数据范围、软删除、密码策略、验证码、登录锁定、多端会话和强制改密与 Java 服务一致。
- 初始管理员是超级管理员，权限集合为 `*`。
- 本版实现的是默认运行方式：SQLite、内存缓存、本地磁盘。MySQL、PostgreSQL、Redis 和 S3 仍由 Java 版的 profile 提供。

## 测试

```bash
cargo test
```
