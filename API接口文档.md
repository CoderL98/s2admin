# S2Admin API 接口文档

> 适用版本:前后端分离骨架 s2admin(Spring Boot 3.5 + Spring Security 6 + JPA / SvelteKit)
> 用途:供前端开发与后端联调使用。本文档由后端 controller 与前端 request.ts 一一核对生成。

## 1. 通用约定

### 1.1 Base URL

| 环境 | 地址 |
| --- | --- |
| 本地开发(默认) | `http://localhost:8080` |
| 生产 | 由前端环境变量 `PUBLIC_API_BASE_URL` 指定(如 `https://api.example.com`) |

前端所有请求以 `/api` 开头,由 `frontend/src/lib/utils/request.ts` 统一拼接 Base URL 并携带 Token。

### 1.2 统一响应结构

所有 JSON 接口(除文件下载/导出)统一返回 `Result<T>`:

```json
{
  "code": 200,
  "message": "success",
  "data": { },
  "timestamp": 1730000000000
}
```

前端 `request.ts` 只解析 `code === 200` 的响应并返回 `data`;其余情况抛 `ApiError(code, message, data)`。

### 1.3 响应码(ResultCode)

| code | 含义 | 说明 |
| --- | --- | --- |
| 200 | 成功 | 业务成功 |
| 400 | 参数错误 | 参数校验失败、请求体格式错误、上传超限、数据冲突等 |
| 401 | 未认证/过期 | 未携带 Token、Token 无效/黑名单、会话被下线 |
| 403 | 无权限 | 缺少权限码;或"请先修改初始密码"拦截 |
| 404 | 资源不存在 | |
| 429 | 请求过于频繁 | 限流 |
| 500 | 系统异常 | 已记录异常日志,前端提示"系统繁忙,请稍后重试" |

> 注意:大多数业务错误返回 **HTTP 200 + 非 200 的业务 code**(由全局异常处理器统一输出);**401 / 403 由安全过滤器直接以 HTTP 401 / 403 返回**(响应体仍是统一 Result 格式)。

### 1.4 认证方式

- 请求头携带:`Authorization: Bearer <accessToken>`
- AccessToken 有效期 **15 分钟**,RefreshToken 有效期 **7 天**(登录勾选"记住我"时为 **30 天**)
- 前端在收到 HTTP 401 时会自动用 RefreshToken 调 `POST /api/auth/refresh` 刷新并重试一次(并发请求共享一次刷新);刷新失败则清理本地会话并跳转登录页
- 登出需调 `POST /api/auth/logout` 使 Token 进入黑名单

### 1.5 强制修改初始密码

当用户 `pwdReset = 1`(例如初始密码、被管理员重置密码、导入未设置密码)时,除以下接口外,其余请求会被拦截并返回 **HTTP 403**:

```json
{ "code": 403, "message": "请先修改初始密码", "data": null, "timestamp": 1730000000000 }
```

放行接口:`PUT /api/auth/password`、`GET /api/auth/info`、`GET /api/auth/menus`、`POST /api/auth/logout`、`POST /api/auth/refresh`。前端 `request.ts` 检测到该 message 时会触发强制改密流程。

### 1.6 分页参数与分页结果

所有"分页查询"接口都支持以下 Query 参数(由 `PageQuery` 提供):

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| pageNum | int | 否 | 1 | 页码,≥ 1 |
| pageSize | int | 否 | 10 | 每页条数,1 ~ 200 |
| orderBy | string | 否 | - | 排序字段(仅允许字母/数字/下划线,防注入) |
| sortDirection | string | 否 | asc | asc / desc |

分页响应 `PageResult<T>`:

```json
{
  "records": [ ],
  "total": 100,
  "size": 10,
  "current": 1,
  "pages": 10
}
```

### 1.7 时间与上传

- 时间格式统一为 `yyyy-MM-dd HH:mm:ss`(时区 Asia/Shanghai)
- 文件上传:HTTP `multipart/form-data`,**前端不手动设置 Content-Type**(fetch 自动生成 boundary)
- 上传限制:单文件最大 **10MB**,单请求最大 **20MB**

### 1.8 权限码说明

权限由 `@PreAuthorize("@ss.hasPermission('xxx')")` 在方法上声明;角色 `SUPER_ADMIN` 拥有全部权限(`*`)。文档中每个接口标注了所需权限码;标注"登录即可"的接口仅需有效 Token。

### 1.9 CORS

默认允许 `http://localhost:5173` / `http://127.0.0.1:5173`,可用环境变量 `CORS_ORIGINS` 覆盖;支持 GET/POST/PUT/DELETE/PATCH/OPTIONS。

---

## 2. 认证模块 `/api/auth`

### 2.1 获取验证码 `GET /api/auth/captcha`

公开接口(无需 Token)。

**响应 `data: CaptchaVO`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| enabled | boolean | 是否启用图形验证码(由配置 `sys.account.captchaEnabled` 控制,默认 true) |
| captchaKey | string | 验证码 Key,登录/找回密码时回传(enabled=false 时为 null) |
| image | string | `data:image/svg+xml;base64,...` 图片数据 |

限流:每 IP 20 次/分钟。

### 2.2 登录 `POST /api/auth/login`

公开接口。账号支持**用户名 / 邮箱 / 手机号**任一方式登录。

**请求体 `LoginForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 账号(用户名/邮箱/手机号) |
| password | string | 是 | 密码 |
| captcha | string | 否 | 验证码(开启后必填) |
| captchaKey | string | 否 | 验证码 Key |
| rememberMe | boolean | 否 | 记住我:RefreshToken 延长至 30 天 |

**响应 `data: LoginVO`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| token | string | AccessToken(15 分钟),请求头 `Authorization: Bearer <token>` |
| refreshToken | string | RefreshToken(7 天 / 记住我 30 天),用于刷新与登出 |
| expiresIn | long | AccessToken 剩余秒数(900) |
| user | UserInfoVO | 用户信息(见 16.2) |

**错误**:账号或密码错误 → 401"用户名或密码错误";验证码错误 → 400;登录失败次数达到阈值(默认 5 次,配置 `sys.account.lockThreshold`)后账号锁定 30 分钟(`sys.account.lockDuration`),IP 连续失败还会触发 IP 锁定 15 分钟;登录接口限流 30 次/分钟/IP。

**示例**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","rememberMe":true}'
```

### 2.3 登出 `POST /api/auth/logout`

需登录。请求体可选:

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| refreshToken | string | 否 | 传入则一并加入黑名单 |

登出后 AccessToken 与 RefreshToken 均失效,会话被移除。返回 `data: null`。

### 2.4 刷新 Token `POST /api/auth/refresh`

公开接口。

**请求体 `RefreshForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| refreshToken | string | 是 | RefreshToken |

**响应**:同 2.2 的 `LoginVO`(旧的 refreshToken 会失效,需用新值覆盖本地存储)。

### 2.5 当前用户信息 `GET /api/auth/info`

需登录。**响应 `data: UserInfoVO`**,包含 roles / permissions 列表与 mustChangePassword 标记,前端用于渲染菜单与按钮权限。

### 2.6 当前用户菜单 `GET /api/auth/menus`

需登录。**响应 `data: MenuVO[]`**(树形,children 嵌套),按当前用户权限过滤,前端用于侧边栏。

### 2.7 修改个人资料 `PUT /api/auth/profile`

需登录。**请求体 `ProfileForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| nickname | string | 是 | 昵称,≤ 50 字符 |
| email | string | 否 | 邮箱,≤ 100,格式校验;唯一 |
| phone | string | 否 | 手机号,`1[3-9]xxxxxxxxx` 或空;唯一 |
| avatar | string | 否 | 头像地址,≤ 500 |
| province / city / district | string | 否 | 省/市/区,各 ≤ 50 |

**响应 `data: UserInfoVO`**。

### 2.8 修改自己的密码 `PUT /api/auth/password`

需登录(强制改密状态下该接口放行)。

**请求体 `ChangePasswordForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| oldPassword | string | 是 | 原密码 |
| newPassword | string | 是 | 新密码,8 ~ 64 位 |

> 密码策略:至少 8 位(最多 64),**必须同时包含大写字母、小写字母、数字和特殊字符**;新密码不能与原密码相同。修改成功后当前用户所有会话的 Token 全部失效(需重新登录),同时清除 `pwdReset` 标记。

### 2.9 忘记密码(发送重置验证码) `POST /api/auth/forgot-password`

公开接口。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| account | string | 是 | 账号 |
| captcha | string | 否 | 图形验证码 |
| captchaKey | string | 否 | 验证码 Key |

**响应 `data: { message: string, mockCode?: string }`**。仅当账号存在且绑定邮箱时发送邮件;开发环境邮件为 mock 模式(`s2admin.mail.mock=true`),返回的 `mockCode` 即验证码,便于联调。限流 5 次/10 分钟/IP。

### 2.10 重置密码 `POST /api/auth/reset-password`

公开接口。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| account | string | 是 | 账号 |
| code | string | 是 | 邮箱验证码 |
| newPassword | string | 是 | 新密码,8 ~ 64 位,遵循密码策略 |

限流 10 次/10 分钟/IP。成功后该用户所有会话失效。

### 2.11 我的登录会话 `GET /api/auth/sessions`

需登录。**响应 `data: SessionVO[]`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| sid | string | 会话 ID |
| iat | long | 会话创建时间戳 |
| ip | string | 登录 IP |
| ua | string | User-Agent |
| current | boolean | 是否当前设备 |

### 2.12 下线其他设备 `DELETE /api/auth/sessions/others`

需登录。保留当前会话,移除其余会话(对应 Token 立即失效)。

### 2.13 下线指定会话 `DELETE /api/auth/sessions/{sid}`

需登录。不能下线当前设备(应使用登出接口)。

### 2.14 我的登录日志 `GET /api/auth/my-login-logs`

需登录。分页参数:`pageNum`、`pageSize`(也支持 LogQuery 的 keyword/status/beginTime/endTime)。**响应 `data: PageResult<LoginLog>`**。

### 2.15 我的操作日志 `GET /api/auth/my-operations`

需登录。参数同 2.14。**响应 `data: PageResult<OperationLog>`**。

### 2.16 上传头像 `POST /api/auth/avatar`

需登录。`multipart/form-data`,字段名 `file`。仅支持 jpg / jpeg / png / gif / webp。**响应 `data: FileVO`**(avatar 类文件对所有登录用户可预览访问)。

---

## 3. 用户管理 `/api/system/user`

### 3.1 分页查询用户 `GET /api/system/user`

权限:`system:user:view`

Query 参数:

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| keyword | string | 用户名/昵称模糊搜索 |
| status | int | 0 正常 / 1 禁用 / 2 锁定 / 3 过期 |
| deptId | long | 部门 ID |
| roleId | long | 角色 ID |
| beginTime | string | 创建时间起(`yyyy-MM-dd HH:mm:ss`) |
| endTime | string | 创建时间止 |
| fields | string | 导出用,逗号分隔的列名 |

**响应 `data: PageResult<UserVO>`**(字段见 16.1)。列表受数据范围约束。

### 3.2 用户详情 `GET /api/system/user/{id}`

权限:`system:user:view`。**响应 `data: UserVO`**。

### 3.3 新增用户 `POST /api/system/user`

权限:`system:user:add`

**请求体 `UserForm`**

| 字段 | 类型 | 必填 | 校验/说明 |
| --- | --- | --- | --- |
| username | string | 是 | 2 ~ 50 位,仅字母、数字、下划线 |
| password | string | 否 | 8 ~ 64 位;留空则系统生成随机强口令并置 `pwdReset=1`(强制首次改密) |
| nickname | string | 是 | ≤ 50 |
| email | string | 否 | ≤ 100,邮箱格式,唯一 |
| phone | string | 否 | `1[3-9]xxxxxxxxx` 或空,唯一 |
| avatar | string | 否 | 头像 URL |
| deptId / deptName | long / string | 否 | 所属部门 |
| status | int | 否 | 默认 0 |
| roleIds | long[] | 否 | 角色 ID 列表 |
| province / city / district | string | 否 | 各 ≤ 50 |
| remark | string | 否 | ≤ 500 |

**响应 `data: UserVO`**。

### 3.4 修改用户 `PUT /api/system/user/{id}`

权限:`system:user:edit`。请求体同 3.3(密码留空表示不修改)。**响应 `data: UserVO`**。

### 3.5 删除用户 `DELETE /api/system/user/{id}`

权限:`system:user:delete`。软删除;存在关联数据时返回 400。

### 3.6 批量删除 `DELETE /api/system/user?ids=1&ids=2`

权限:`system:user:delete`。Query 参数 `ids`(可重复)。

### 3.7 修改用户状态 `PUT /api/system/user/{id}/status`

权限:`system:user:edit`。请求体:`{"status": 0}`。

### 3.8 批量修改状态 `PUT /api/system/user/status`

权限:`system:user:edit`。请求体:`{"ids": [1,2], "status": 0}`。

### 3.9 重置密码 `PUT /api/system/user/{id}/password`

权限:`system:user:edit`。请求体:`{"password": "xxx"}`。重置后该用户所有会话失效且 `pwdReset=1`(首次登录需改密)。

### 3.10 导出用户(CSV) `GET /api/system/user/export`

权限:`system:user:view`。Query 参数同 3.1(支持 `fields` 选择列)。响应为 CSV 文件下载(`users.csv`,UTF-8)。前端下载需携带 Token。

### 3.11 下载导入模板(CSV) `GET /api/system/user/import-template`

权限:`system:user:add`。响应为 `user-import-template.csv` 下载。

### 3.12 导入用户(CSV) `POST /api/system/user/import`

权限:`system:user:add`。`multipart/form-data`,字段 `file`。**响应 `data: ImportResultVO`**:`{ created, skipped, errors: string[] }`。

### 3.13 导出用户(XLSX) `GET /api/system/user/export-xlsx`

权限:`system:user:view`。参数同 3.10,输出 `users.xlsx`。

### 3.14 导入用户(XLSX) `POST /api/system/user/import-xlsx`

权限:`system:user:add`。multipart 上传 `file`,响应 `ImportResultVO`。

---

## 4. 角色管理 `/api/system/role`

### 4.1 分页查询 `GET /api/system/role`

权限:`system:role:view`。Query:`keyword`(名称/编码)、`status`。**响应 `data: PageResult<RoleVO>`**。

### 4.2 全部角色 `GET /api/system/role/all`

权限:`system:role:view`。**响应 `data: RoleVO[]`**(下拉选择用)。

### 4.3 新增角色 `POST /api/system/role`

权限:`system:role:add`

**请求体 `RoleForm`**

| 字段 | 类型 | 必填 | 校验/说明 |
| --- | --- | --- | --- |
| name | string | 是 | ≤ 50 |
| code | string | 是 | `^[A-Z][A-Z0-9_]*$`,≤ 50,唯一(如 SUPER_ADMIN) |
| sort | int | 否 | 默认 0 |
| dataScope | int | 否 | 1 全部 / 2 本部门及以下 / 3 本部门 / 4 本人,默认 4 |
| status | int | 否 | 默认 0 |
| permissionIds | long[] | 否 | 权限 ID 列表 |
| remark | string | 否 | ≤ 500 |

**响应 `data: RoleVO`**。

### 4.4 修改角色 `PUT /api/system/role/{id}`

权限:`system:role:edit`。请求体同 4.3。**响应 `data: RoleVO`**。

### 4.5 删除角色 `DELETE /api/system/role/{id}`

权限:`system:role:delete`。

### 4.6 查询角色权限 `GET /api/system/role/{id}/permissions`

权限:`system:role:view`。**响应 `data: long[]`**(已授权权限 ID)。

### 4.7 分配权限 `PUT /api/system/role/{id}/permissions`

权限:`system:role:assign`。请求体:`{"permissionIds": [1,2,3]}`。

---

## 5. 菜单管理 `/api/system/menu`

### 5.1 分页查询 `GET /api/system/menu`

权限:`system:menu:view`。Query:`keyword`、`type`(1 目录 / 2 菜单 / 3 按钮)、`status`。**响应 `data: PageResult<MenuVO>`**。

### 5.2 菜单树 `GET /api/system/menu/tree`

权限:`system:menu:view`。**响应 `data: MenuVO[]`**(树形)。

### 5.3 新增菜单 `POST /api/system/menu`

权限:`system:menu:add`

**请求体 `MenuForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 菜单名称 |
| parentId | long | 否 | 父菜单 ID,默认 0(根) |
| path | string | 否 | 路由路径 |
| component | string | 否 | 前端组件路径 |
| redirect | string | 否 | 重定向地址 |
| permission | string | 否 | 权限标识(如 system:user:view) |
| icon | string | 否 | 图标 |
| sort | int | 否 | 排序,默认 0 |
| type | int | 否 | 1 目录 / 2 菜单 / 3 按钮,默认 2 |
| hidden | int | 否 | 0 显示 / 1 隐藏 |
| status | int | 否 | 默认 0 |
| remark | string | 否 | ≤ 500 |

**响应 `data: MenuVO`**。

### 5.4 修改菜单 `PUT /api/system/menu/{id}`

权限:`system:menu:edit`。请求体同 5.3。

### 5.5 删除菜单 `DELETE /api/system/menu/{id}`

权限:`system:menu:delete`。存在子菜单时拒绝删除(400)。

### 5.6 菜单排序 `PUT /api/system/menu/{id}/move?direction=up|down`

权限:`system:menu:edit`。`direction` 仅允许 `up` / `down`(其他值返回 400)。

---

## 6. 权限管理 `/api/system/permission`

权限类型:1 菜单 / 2 按钮 / 3 API。

### 6.1 分页查询 `GET /api/system/permission`

权限:`system:permission:view`。Query:`keyword`、`type`、`status`。

### 6.2 权限树 `GET /api/system/permission/tree`

权限:`system:permission:view`。**响应 `data: PermissionVO[]`**。

### 6.3 全部权限 `GET /api/system/permission/all`

权限:`system:permission:view`。**响应 `data: PermissionVO[]`**(扁平列表,授权选择用)。

### 6.4 新增权限 `POST /api/system/permission`

权限:`system:permission:add`

**请求体 `PermissionForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 权限名称 |
| code | string | 是 | 权限编码(如 system:user:view) |
| type | int | 否 | 1 菜单 / 2 按钮 / 3 API,默认 2 |
| parentId | long | 否 | 父权限 ID,默认 0 |
| path | string | 否 | 路由路径 |
| icon | string | 否 | 图标 |
| sort | int | 否 | 默认 0 |
| status | int | 否 | 默认 0 |
| remark | string | 否 | ≤ 500 |

### 6.5 修改权限 `PUT /api/system/permission/{id}`

权限:`system:permission:edit`。

### 6.6 删除权限 `DELETE /api/system/permission/{id}`

权限:`system:permission:delete`。

---

## 7. 部门管理 `/api/system/dept`

### 7.1 部门树 `GET /api/system/dept/tree`

权限:`system:dept:view`。**响应 `data: DeptVO[]`**(树形)。

### 7.2 全部部门 `GET /api/system/dept/all`

权限:`system:dept:view`。**响应 `data: DeptVO[]`**(扁平列表)。

### 7.3 可选部门 `GET /api/system/dept/options`

**登录即可**(用户表单/筛选下拉用,不做权限拦截)。**响应 `data: DeptVO[]`**。

### 7.4 新增部门 `POST /api/system/dept`

权限:`system:dept:add`

**请求体 `DeptForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 部门名称 |
| parentId | long | 否 | 父部门 ID,默认 0 |
| sort | int | 否 | 默认 0 |
| leader | string | 否 | 负责人 |
| phone | string | 否 | 联系电话 |
| email | string | 否 | 邮箱 |
| status | int | 否 | 默认 0 |
| remark | string | 否 | ≤ 500 |

**响应 `data: DeptVO`**。

### 7.5 修改部门 `PUT /api/system/dept/{id}`

权限:`system:dept:edit`。请求体同 7.4。

### 7.6 删除部门 `DELETE /api/system/dept/{id}`

权限:`system:dept:delete`。存在子部门或关联用户时拒绝(400)。

---

## 8. 系统配置 `/api/system/config`

### 8.1 分页查询 `GET /api/system/config`

权限:`system:config:view`。Query:`keyword`(键/值)、`groupCode`、`configType`。**响应 `data: PageResult<SysConfig>`**。

### 8.2 按 Key 查询 `GET /api/system/config/key/{key}`

权限:`system:config:view`。**响应 `data: SysConfig`**。

### 8.3 配置分组列表 `GET /api/system/config/groups`

权限:`system:config:view`。**响应 `data: string[]`**。

### 8.4 新增配置 `POST /api/system/config`

权限:`system:config:add`

**请求体 `ConfigForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| configKey | string | 是 | 配置键,唯一 |
| configValue | string | 是 | 配置值 |
| configType | string | 否 | 值类型,默认 string |
| groupCode | string | 否 | 分组编码,默认 default |
| remark | string | 否 | ≤ 500 |

**响应 `data: SysConfig`**。

### 8.5 修改配置 `PUT /api/system/config/{id}`

权限:`system:config:edit`。请求体同 8.4。

### 8.6 删除配置 `DELETE /api/system/config/{id}`

权限:`system:config:delete`。

---

## 9. 字典管理 `/api/system/dict`

权限码统一为 `tools:dict:view`(查询) / `tools:dict:edit`(增删改)。

### 9.1 字典类型分页 `GET /api/system/dict/type`

Query:`keyword`、`status`。**响应 `data: PageResult<SysDictType>`**。

### 9.2 新增字典类型 `POST /api/system/dict/type`

**请求体 `DictTypeForm`**

| 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| name | string | 是 | ≤ 50 |
| code | string | 是 | `^[a-z][a-z0-9_]*$`,≤ 50,唯一 |
| status | int | 否 | 默认 0 |
| remark | string | 否 | ≤ 500 |

**响应 `data: SysDictType`**。

### 9.3 修改字典类型 `PUT /api/system/dict/type/{id}`

请求体同 9.2。

### 9.4 删除字典类型 `DELETE /api/system/dict/type/{id}`

存在字典数据时拒绝(400)。

### 9.5 字典数据分页 `GET /api/system/dict/data`

Query:`dictTypeId`、`keyword`、`status`。**响应 `data: PageResult<SysDictData>`**。

### 9.6 按类型编码查字典数据 `GET /api/system/dict/data/type/{typeCode}`

**响应 `data: SysDictData[]`**。前端下拉/枚举渲染常用此接口。

### 9.7 新增字典数据 `POST /api/system/dict/data`

**请求体 `DictDataForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| dictTypeId | long | 是 | 所属字典类型 ID |
| label | string | 是 | 标签,≤ 100 |
| value | string | 是 | 值,≤ 100 |
| sort | int | 否 | 默认 0 |
| status | int | 否 | 默认 0 |
| remark | string | 否 | ≤ 500 |

**响应 `data: SysDictData`**。

### 9.8 修改字典数据 `PUT /api/system/dict/data/{id}`

### 9.9 删除字典数据 `DELETE /api/system/dict/data/{id}`

---

## 10. 文件管理 `/api/system/file`

允许上传类型:jpg / jpeg / png / gif / webp / pdf / doc / docx / xls / xlsx / csv / zip(单文件 ≤ 10MB)。

### 10.1 分页查询 `GET /api/system/file`

权限:`system:file:view`。Query:`keyword`(原文件名)、`category`。列表仅可见自己(或数据范围内)上传的文件。**响应 `data: PageResult<FileVO>`**。

### 10.2 上传文件 `POST /api/system/file/upload`

权限:`system:file:view`。`multipart/form-data`:

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | file | 是 | 文件 |
| category | string | 否 | 分类,默认 default(如 avatar / default) |

**响应 `data: FileVO`**:

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 文件 ID |
| url | string | 访问地址(相对路径或完整 URL,可拼 Base URL 访问) |
| name | string | 原始文件名 |
| storedName | string | 存储文件名 |
| size | long | 字节数 |
| category | string | 分类 |
| contentType | string | MIME 类型 |
| storageType | string | local / s3 |
| createTime | string | 上传时间 |

### 10.3 下载/预览 `GET /api/system/file/{filename}`

需登录。`filename` 为存储文件名(含扩展名)。图片 / PDF 走 inline 预览,其余为附件下载(UTF-8 文件名)。`avatar` 分类的文件所有登录用户可访问;其余文件需具备数据权限(本人或上级可见)。

### 10.4 删除文件(按 ID) `DELETE /api/system/file/id/{id}`

权限:`system:file:delete`。

### 10.5 删除文件(按文件名) `DELETE /api/system/file/{filename}`

权限:`system:file:delete`。

---

## 11. 系统公告 `/api/system/notice`

### 11.1 分页查询 `GET /api/system/notice`

权限:`system:notice:view`。Query:`keyword`、`status`(0 草稿 / 1 已发布)、`type`(1 通知 / 2 公告)。**响应 `data: PageResult<SysNotice>`**。

### 11.2 已发布公告 `GET /api/system/notice/published`

**登录即可**。**响应 `data: SysNotice[]`**(仅已发布,按置顶/时间排序)。

### 11.3 新增公告 `POST /api/system/notice`

权限:`system:notice:add`

**请求体 `NoticeForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| title | string | 是 | ≤ 200 |
| content | string | 否 | 正文(TEXT) |
| type | int | 否 | 1 通知 / 2 公告,默认 1 |
| status | int | 否 | 0 草稿 / 1 发布,默认 0 |
| pinned | int | 否 | 0 不置顶 / 1 置顶 |
| remark | string | 否 | ≤ 500 |

**响应 `data: SysNotice`**(发布时写入 publishTime)。

### 11.4 修改公告 `PUT /api/system/notice/{id}`

权限:`system:notice:edit`。请求体同 11.3。

### 11.5 发布/撤回 `PUT /api/system/notice/{id}/publish`

权限:`system:notice:edit`。请求体:`{"published": true|false}`(缺省视为发布)。

### 11.6 删除公告 `DELETE /api/system/notice/{id}`

权限:`system:notice:delete`。

---

## 12. 站内信 `/api/system/message`

### 12.1 发送站内信 `POST /api/system/message`

权限:`system:message:send`

**请求体 `MessageForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| title | string | 是 | ≤ 200 |
| content | string | 否 | 正文 |
| receiverIds | long[] | 是 | 接收人用户 ID,至少 1 个 |

返回 `data: null`。

### 12.2 我的站内信 `GET /api/system/message/my`

**登录即可**。Query:`readFlag`(0 未读 / 1 已读)、`keyword`(标题模糊)。**响应 `data: PageResult<SysMessage>`**。

### 12.3 未读数 `GET /api/system/message/unread-count`

**登录即可**。**响应 `data: { "count": 3 }`**。

### 12.4 标记已读 `PUT /api/system/message/{id}/read`

**登录即可**(仅限自己的消息)。

### 12.5 全部已读 `PUT /api/system/message/read-all`

**登录即可**。

### 12.6 删除消息 `DELETE /api/system/message/{id}`

**登录即可**(仅限自己的消息)。

---

## 13. 监控日志 `/api/monitor`

权限:`monitor:log:view`(查询/导出)、`monitor:log:delete`(清空)。

**公共 Query(`LogQuery` 继承分页参数)**:`keyword`、`status`、`module`、`beginTime`、`endTime`。

### 13.1 登录日志分页 `GET /api/monitor/login-log`

**响应 `data: PageResult<LoginLog>`**。

### 13.2 导出登录日志 `GET /api/monitor/login-log/export`

CSV 下载(`login-log.csv`)。

### 13.3 清空登录日志 `DELETE /api/monitor/login-log/clean`

权限:`monitor:log:delete`。

### 13.4 操作日志分页 `GET /api/monitor/op-log`

**响应 `data: PageResult<OperationLog>`**。

### 13.5 操作日志详情 `GET /api/monitor/op-log/{id}`

### 13.6 导出操作日志 `GET /api/monitor/op-log/export`

CSV 下载(`operation-log.csv`)。

### 13.7 清空操作日志 `DELETE /api/monitor/op-log/clean`

权限:`monitor:log:delete`。

### 13.8 异常日志分页 `GET /api/monitor/error-log`

**响应 `data: PageResult<ErrorLog>`**。

### 13.9 异常日志详情 `GET /api/monitor/error-log/{id}`

### 13.10 清空异常日志 `DELETE /api/monitor/error-log/clean`

权限:`monitor:log:delete`。

---

## 14. 仪表盘 `/api/system/dashboard`

### 14.1 统计 `GET /api/system/dashboard/stats`

**登录即可**。**响应 `data: DashboardStatsVO`**:

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| userCount | long | 用户数(受数据范围约束) |
| roleCount | long | 角色数 |
| todayLoginCount | long | 今日登录次数(受数据范围约束) |
| errorCount | long | 异常数(受数据范围约束) |

---

## 15. 代码生成 `/api/tools/codegen`

### 15.1 生成代码 `POST /api/tools/codegen`

权限:`tools:codegen:generate`。响应为 **zip 包下载**(`codegen.zip`),不是 JSON。

**请求体 `CodegenForm`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| module | string | 是 | 业务模块名 |
| entity | string | 是 | 实体名 |
| tableName | string | 是 | 表名 |
| permission | string | 是 | 权限前缀(如 system:xxx) |
| path | string | 是 | 前端路由路径 |
| remark | string | 否 | 备注 |
| fields | Field[] | 是 | 字段定义,至少 1 个 |

Field:

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| name | string | 字段名(必填) |
| javaType | string | Java 类型(必填) |
| label | string | 显示名(必填) |
| query | boolean | 是否查询条件 |
| required | boolean | 是否必填 |

---

## 16. 数据模型(响应字段)

> 所有实体含 BaseEntity 公共字段:`id`、`createBy`、`createTime`、`updateBy`、`updateTime`、`remark`。软删除字段 `deleted` 不暴露。

### 16.1 UserVO

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | ID |
| username | string | 用户名 |
| nickname | string | 昵称 |
| email | string | 邮箱 |
| phone | string | 手机号 |
| avatar | string | 头像 |
| status | int | 0 正常 / 1 禁用 / 2 锁定 / 3 过期 |
| deptId | long | 部门 ID |
| deptName | string | 部门名称 |
| roleIds | long[] | 角色 ID |
| roleNames | string[] | 角色名 |
| province / city / district | string | 省/市/区 |
| pwdReset | int | 1 = 需强制改密 |
| createBy / createTime / updateTime | - | 审计字段 |

### 16.2 认证相关 VO

- **LoginVO**:token, refreshToken, expiresIn, user(UserInfoVO)
- **UserInfoVO**:id, username, nickname, email, phone, avatar, province, city, district, roles(string[]), permissions(string[]), mustChangePassword(boolean)
- **SessionVO**:sid, iat, ip, ua, current
- **CaptchaVO**:enabled, captchaKey, image

### 16.3 RoleVO

id, name, code, sort, dataScope(1 全部 / 2 本部门及以下 / 3 本部门 / 4 本人), status, permissionIds(long[]), remark, createTime。

### 16.4 MenuVO

id, name, parentId, path, component, redirect, permission, icon, sort, type(1 目录 / 2 菜单 / 3 按钮), hidden, status, remark, createTime, children(MenuVO[])。

### 16.5 PermissionVO

id, name, code, type(1 菜单 / 2 按钮 / 3 API), parentId, path, icon, sort, status, remark, createTime, children(PermissionVO[])。

### 16.6 DeptVO

id, name, parentId, ancestors, sort, leader, phone, email, status, remark, createTime, children(DeptVO[])。

### 16.7 SysConfig

configKey, configValue, configType, groupCode + BaseEntity 公共字段。

### 16.8 SysDictType

name, code, status + 公共字段。

### 16.9 SysDictData

dictTypeId, label, value, sort, status + 公共字段。

### 16.10 SysMessage

title, content, senderId, senderName, receiverId, readFlag(0 未读 / 1 已读), readTime + 公共字段。

### 16.11 SysNotice

title, content, type(1 通知 / 2 公告), status(0 草稿 / 1 发布), pinned(0/1), publishTime + 公共字段。

### 16.12 LoginLog

id, userId, username, ip, location, browser, os, status(0 成功 / 1 失败), message, loginTime。

### 16.13 OperationLog

id, userId, username, operation, module, method, url, ip, location, oldValue, newValue, status(0 成功 / 1 失败), errorMsg, executeTime(毫秒), operationTime。

### 16.14 ErrorLog

id, traceId, userId, username, ip, url, method, params, exception, stackTrace, errorTime。

### 16.15 ImportResultVO

created(int), skipped(int), errors(string[])。

### 16.16 DashboardStatsVO

userCount(long), roleCount(long), todayLoginCount(long), errorCount(long)。

---

## 17. 前端对接指引

### 17.1 已有封装

- 请求客户端:`frontend/src/lib/utils/request.ts` —— 自动拼接 Base URL、携带 Token、401 自动刷新重试、统一抛 `ApiError`
- 各业务 API:`frontend/src/lib/api/{auth,user,role,menu,permission,config,dict,dept,file,notice,message,login-log,op-log,error-log,dashboard,codegen}.ts`(统一从 `api/index.ts` 导出)
- 环境变量:`PUBLIC_API_BASE_URL` / `PUBLIC_TOKEN_KEY` / `PUBLIC_REFRESH_TOKEN_KEY` / `PUBLIC_USE_MOCK`(见 `frontend/.env.example`)
- 真实/模拟切换:Mock 实现位于 `src/lib/api/mock/*`,真实实现位于 `src/lib/api/real/*`

### 17.2 新增接口的对接步骤

1. 在 `frontend/src/lib/api/real/<domain>.ts` 新增函数,复用 `get/post/put/del/upload`
2. 查询类接口传 `PageQuery`(pageNum/pageSize);列表返回 `PageResult<T>` 时使用 `records` 渲染
3. 文件下载(导出/模板/zip)使用 `frontend/src/lib/utils/download.ts` 的 `downloadAuthenticated`(携带 Token)
4. 文件上传用 `upload<T>(endpoint, formData)`,字段名与后端 `@RequestParam("file")` 一致
5. 错误处理:捕获 `ApiError`,按 `code` 提示;401 由客户端自动刷新,403"请先修改初始密码"由全局回调处理

### 17.3 联调自检清单

- [ ] 登录成功 → 本地保存 `token` / `refreshToken`,后续请求头携带 Bearer
- [ ] 15 分钟后请求返回 401 → 客户端自动刷新成功
- [ ] 无权限访问接口 → 403"没有操作权限"
- [ ] 密码策略错误(如纯数字) → 400 提示"密码至少 8 位,且须包含大小写字母、数字和特殊字符"
- [ ] 上传非白名单类型 → 400"不支持的文件类型"
- [ ] 分页 pageSize > 200 被钳制;orderBy 含非法字符被忽略
