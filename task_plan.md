# 任务计划：通用后台管理系统 (s2admin)

## 任务目标

为通用后台管理系统创建详细的需求文档和设计文档，技术栈：
- **前端**：Svelte5 + SvelteKit + shadcn-svelte + TypeScript + TailwindCSS
- **后端**：SpringBoot + JDK25 + MySQL + Redis + JWT

## 当前阶段

**当前阶段**：P0 + 主要 P1 已落地。2026-09-23 又修了一批权限、登录锁定、菜单可见性和导入/会话逻辑缺陷。缺口见 `功能缺口与缺陷.md`。下一轮：真实邮件/S3 SDK、完整县级区划、跨级拖拽、OpenAPI。

---

## 任务阶段总览

### 阶段 1：需求与发现 ✅ 完成
### 阶段 2：需求文档创建 ✅ 完成
### 阶段 3：设计文档创建 ✅ 完成
### 阶段 4：审查与定稿 ✅ 完成

### 阶段 5：项目初始化 ✅ 已完成
- [x] 前端项目初始化 (SvelteKit + TailwindCSS + shadcn-svelte)
- [x] 后端项目初始化 (SpringBoot + JDK25)
- [x] 数据库初始化脚本创建
- **状态**：已完成 (2026-04-27)

### 阶段 5.1：前端页面开发 ✅ 已完成 (用户)
- [x] 登录页面创建 (login 页面 + login-form 组件)
- [x] 仪表盘页面创建 (dashboard 页面 + 相关组件)
- [x] shadcn-svelte 组件库安装 (avatar, badge, chart, checkbox, data-table, drawer, dropdown-menu, field, select, separator, sheet, sidebar, skeleton, table, tabs, toggle, tooltip 等)
- **状态**：已完成 (2026-04-27)

### 阶段 6：核心功能开发 ✅ 骨架已完成,正在打磨
- [x] 公共模块开发 (统一响应、异常处理、工具类)
- [x] 安全模块开发 (JWT Token、Spring Security)
- [x] 认证授权模块 (登录、登出、Token 刷新)
- [x] 用户管理模块 (用户 CRUD)
- [x] 角色权限模块 (角色管理、权限分配)
- [x] 菜单 / 权限 / 配置 / 字典 / 日志 后端与页面
- [x] SUPER_ADMIN `*` 权限与 `@PreAuthorize` 对齐
- [x] AccessToken 黑名单在过滤器生效
- [x] 前端会话恢复、动态菜单、真分页、角色授权
- [x] 个人中心(资料/改密/头像)
- [x] 登录验证码与失败锁定
- [x] 用户 CSV 导入导出
- [x] 本地文件上传
- [x] 配置/字典 Redis 缓存
- [x] JDK 25 + Lombok 1.18.40 编译通过（MenuService Set/List 类型一并修）
- [x] `./dev.sh` 启停/重启/状态/日志
- [x] 缺口盘点写入 `功能缺口与缺陷.md`
- [x] 2026-08-25：正确性/安全缺陷与 P0 体验（邮箱手机登录、文件鉴权、批量、日志筛选导出、超管保护、软删唯一键）
- [x] 2026-08-25：P1 部门+dataScope、记住我、忘记密码、文件管理页、公告/站内信、Excel、代码生成、生产 profile
- [x] 2026-08-26：缺陷自检（dataScope IDOR、JWT 角色、文件 MIME、分页钳制、树成环、字典 Tab、假 zip）
- [x] 2026-08-26：继续完善（操作日志快照、dataScope 文件/日志/部门、Refresh 一次性、强制改密、菜单图标与排序、配置分组、置顶公告、Compose）
- [x] 2026-08-26：再次自检（随机初始口令、文件下载/删除越权、仪表盘 dataScope、强制改密 403 跳转、codegen 表名）
- [x] 2026-08-26：审计补丁（pwd_reset 迁移、JOIN FETCH、角色 dataScope、JPA 审计、日志详情范围）
- [x] 2026-08-26：前端审计（强制改密壳、Mock 分流、业务 401、字典分页、筛选哨兵值）
- [x] 2026-08-26：多端登录 sid、个人中心设备/日志、站内信删除、导出字段、省市县、外链菜单、置顶公告弹窗
- **状态**：P1 可交接；需求缺口本轮已补 (2026-08-26)

---

## 关键问题

### 1. 系统核心模块有哪些？
| 模块 | 功能描述 | 优先级 |
|------|----------|--------|
| 认证授权模块 | 登录登出、JWT Token、密码管理、登录日志 | P0 |
| 用户管理模块 | 用户CRUD、状态管理、导入导出 | P0 |
| 角色权限模块 | 角色管理、权限管理、菜单管理、角色分配 | P0 |
| 系统配置模块 | 参数配置、字典数据管理 | P1 |
| 消息通知模块 | 系统公告、站内信、消息模板 | P2 |
| 操作日志模块 | 登录日志、操作日志、异常日志 | P0 |
| 公共功能模块 | 文件上传下载、数据字典、省市县联动 | P1 |

### 2. 需要支持哪些通用 CRUD 操作？
- 用户管理：分页查询、条件筛选、增删改查、批量操作
- 角色管理：增删改查、角色授权
- 菜单管理：树形结构CRUD、拖拽排序
- 权限管理：权限项CRUD、权限类型（菜单/按钮/API）
- 配置管理：参数增删改查、动态生效
- 字典管理：字典类型CRUD、字典数据CRUD
- 日志管理：日志查询、日志导出、日志详情

### 3. 如何设计可扩展的模块化架构？
```
项目结构设计原则：
├── 核心模块 (system)      - 用户、角色、菜单、权限、配置、字典、日志
├── 业务扩展模块 (business) - 可按需添加的业务模块
├── 公共模块 (common)      - 统一响应、日志、异常、工具
└── 安全模块 (security)    - 认证、授权、JWT、权限校验
```

### 4. 前后端交互采用什么方式？
| 交互方式 | 选择 | 理由 |
|----------|------|------|
| REST API | ✓ | 标准化接口，易于理解和集成 |
| GraphQL | ✗ | 对于后台管理系统过于复杂 |
| RPC | ✗ | 不适合 HTTP 场景 |

### 5. 认证授权机制如何设计？
```
认证流程：
1. 用户登录 → 后端验证密码 → 发放 JWT Token
2. 后续请求携带 Token → 后端验证 Token → 校验权限 → 返回数据
3. Token 过期 → 使用 RefreshToken 刷新 → 获取新 Token

权限模型：
- RBAC (Role-Based Access Control) - 基于角色的访问控制
- 用户 ↔ 角色 ↔ 权限 (多对多关系)
- 支持数据范围配置（本人、本部门、全部）
```

---

## 技术决策记录

| 决策 | 理由 |
|------|------|
| 使用 JWT Token 进行认证 | 无状态认证，适合分布式部署和水平扩展 |
| 采用 RBAC 角色权限模型 | 通用且灵活的权限管理方式，企业级标准 |
| RESTful API 设计 | 标准化接口，前端后端解耦，利于前后端并行开发 |
| 模块化架构设计 | 低耦合高内聚，支持后续业务系统改造和功能扩展 |
| shadcn-svelte UI组件 | 现代、可定制、基于 TailwindCSS 的无头组件库 |
| 统一响应结构 | {code, message, data, timestamp} 格式，前后端交互标准化 |
| 软删除设计 | deleted 字段标记，便于数据恢复和审计 |
| 密码 BCrypt 加密 | 行业标准的密码哈希算法 |
| Redis 缓存字典数据 | 提高字典查询性能，减少数据库压力 |

---

## 已完成决策

| 决策 | 理由 |
|------|------|
| 前端技术栈：Svelte5 + SvelteKit + shadcn-svelte | Svelte5 Runes 语法提供更好的响应式编程体验 |
| 后端技术栈：SpringBoot + JDK25 | JDK25 虚拟线程提升并发性能，SpringBoot 3.5+ 官方支持 |
| 数据库：MySQL 8.x | 成熟稳定，支持 JSON、窗口函数等现代 SQL 特性 |
| 缓存：Redis 7.x | 高性能缓存，支持多种数据结构 |
| 状态管理：Pinia | Svelte 官方推荐的状态管理库 |

---

## 遇到的问题与解决方案

| 问题 | 尝试次数 | 解决方案 |
|------|----------|----------|
| 暂无 | - | - |

---

## 备注

- 使用 planning-with-files 技能跟踪进度
- 项目目录：`/home/rcc/GitHub/s2admin`
- 需求文档：`需求文档.md`
- 设计文档：`设计文档.md`

### 前端开发规范

#### 1. 环境变量配置
- 所有配置使用环境变量，不得硬编码
- 使用 `$env/dynamic/public` 读取环境变量
- 环境变量文件：
  - `.env.example` - 配置示例模板
  - `.env.local` - 本地开发配置
  - `.env.production` - 生产环境配置
- 敏感配置 (API 地址、Token 键名等) 必须使用环境变量

#### 2. API 请求规范
- 使用 `src/lib/utils/request.ts` 封装请求
- 所有 API 调用通过 request 模块
- 支持 get / post / put / del / upload 方法
- 自动携带 JWT Token
- 统一错误处理

#### 3. shadcn-svelte 组件使用
- 使用 shadcn-svelte 技能进行组件开发
- 组件安装在 `src/lib/components/ui/` 目录
- 复杂业务组件安装在 `src/lib/components/` 目录

---

## 下一步计划

需求文档和设计文档已完成创建，后续可按以下详细顺序开发：

---

## 阶段一：项目初始化

### 1.1 前端项目初始化

#### 1.1.1 创建 SvelteKit 项目
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 安装 Node.js 环境 | 确保 Node.js >= 18.x，npm >= 9.x | Node.js 环境 | P0 |
| 创建 SvelteKit 项目 | `npm create svelte@latest s2admin-frontend` | 项目基础结构 | P0 |
| 选择项目模板 | 选择 Skeleton Project + TypeScript | 基础代码 | P0 |
| 安装依赖 | `npm install` | node_modules | P0 |
| 配置 Git 仓库 | `git init`, 创建 .gitignore | Git 仓库 | P1 |

#### 1.1.2 配置 TailwindCSS
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 安装 TailwindCSS | `npx svelte-add@latest tailwindcss` | TailwindCSS 配置 | P0 |
| 配置 tailwind.config.js | 配置 content 路径、主题扩展 | 配置文件 | P0 |
| 配置 postcss.config.js | PostCSS 配置 | 配置文件 | P0 |
| 创建全局样式 | 创建 app.css，引入 Tailwind 指令 | 全局样式 | P0 |
| 验证样式 | 运行 dev server，检查样式生效 | - | P0 |

#### 1.1.3 安装并配置 shadcn-svelte
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 初始化 shadcn-svelte | `npx shadcn-svelte@latest init` | 组件库配置 | P0 |
| 安装基础组件 | `npx shadcn-svelte@latest add button input card dialog` | UI 组件 | P0 |
| 配置组件路径 | 在 svelte.config.js 配置 alias | 路径别名 | P0 |
| 创建组件目录结构 | 按设计文档创建 lib/components 子目录 | 目录结构 | P1 |

#### 1.1.4 配置开发工具
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 配置 ESLint | 安装并配置 ESLint + Prettier | 代码检查 | P1 |
| 配置 TypeScript | 配置 tsconfig.json strict 模式 | 类型检查 | P0 |
| 配置环境变量 | 创建 .env, .env.example | 环境配置 | P1 |
| 配置 Vite | 配置 proxy、alias 等 | 构建配置 | P1 |

### 1.2 后端项目初始化

#### 1.2.1 创建 SpringBoot 项目
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 检查 JDK 环境 | 确保 JDK 25 已安装 | JDK 环境 | P0 |
| 创建项目 | 使用 Spring Initializr 或 IDE 创建项目 | 项目基础结构 | P0 |
| 选择依赖 | Spring Web, Spring Security, Spring Data JPA, MySQL Driver, Redis, Lombok, Validation | pom.xml | P0 |
| 导入 Maven 依赖 | `mvn clean install` | 依赖下载 | P0 |
| 配置 Git 仓库 | 创建 .gitignore | Git 仓库 | P1 |

#### 1.2.2 配置 JDK25 和 SpringBoot 3.x
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 配置 pom.xml | 设置 java-version=25, maven-compiler | 编译配置 | P0 |
| 配置 application.yml | 配置端口、上下文路径 | 应用配置 | P0 |
| 创建主启动类 | S2AdminApplication.java | 启动类 | P0 |
| 虚拟线程 | 已关闭(SQLite 模式下 sqlite-jdbc JNI 阻塞与虚拟线程不兼容) | 配置说明 | P1 |

#### 1.2.3 配置数据层
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 配置 MySQL 数据源 | pom.xml 添加驱动，application.yml 配置 | 数据源 | P0 |
| 配置 Redis | application.yml 配置 Redis 连接 | Redis 配置 | P0 |
| 配置 JPA | 配置 hibernate ddl-auto, naming strategy | ORM 配置 | P0 |
| 配置 Druid 连接池 | 添加 druid-spring-boot-starter | 连接池 | P1 |

#### 1.2.4 创建项目包结构
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 创建基础包 | com.s2admin.common, security, module | 包结构 | P0 |
| 创建实体类包 | module.system.entity | 实体包 | P0 |
| 创建仓储包 | module.system.repository | 仓储包 | P0 |
| 创建服务包 | module.system.service | 服务包 | P0 |
| 创建控制器包 | module.system.controller | 控制器包 | P0 |
| 创建 VO/Form 包 | module.system.vo, form | 数据传输对象包 | P0 |

### 1.3 数据库初始化

#### 1.3.1 创建数据库和表结构
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 创建数据库 | `CREATE DATABASE s2admin DEFAULT CHARSET utf8mb4` | 数据库 | P0 |
| 创建用户表 | sys_user 表（详见设计文档） | 表结构 | P0 |
| 创建角色表 | sys_role 表 | 表结构 | P0 |
| 创建权限表 | sys_permission 表 | 表结构 | P0 |
| 创建菜单表 | sys_menu 表 | 表结构 | P0 |
| 创建关联表 | sys_user_role, sys_role_permission | 关联表 | P0 |
| 创建配置表 | sys_config 表 | 表结构 | P0 |
| 创建字典表 | sys_dict_type, sys_dict_data 表 | 表结构 | P0 |
| 创建日志表 | sys_login_log, sys_operation_log, sys_error_log | 日志表 | P0 |

#### 1.3.2 创建初始化数据
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 插入超级管理员 | admin 用户，BCrypt 加密密码 | 初始化数据 | P0 |
| 插入内置角色 | SUPER_ADMIN, ADMIN, USER, AUDITOR, GUEST | 初始化数据 | P0 |
| 插入默认菜单 | 系统管理、用户管理、角色管理等默认菜单 | 初始化数据 | P0 |
| 插入默认权限 | system:user:view, system:user:add 等默认权限 | 初始化数据 | P0 |
| 插入字典数据 | 用户状态、角色状态等基础字典 | 初始化数据 | P1 |

#### 1.3.3 配置持久化
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|--------|--------|
| 配置 ddl-auto | development: update, production: validate | 持久化配置 | P0 |
| 创建 Flyway 迁移脚本 | 管理数据库版本 | 迁移脚本 | P1 |
| 创建数据更新脚本 | 用于版本迭代 | 更新脚本 | P2 |

---

## 阶段二：核心功能开发

### 2.1 公共模块开发

#### 2.1.1 统一响应结构
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建 Result 类 | 统一响应格式 {code, message, data, timestamp} | Result.java | P0 |
| 创建 PageResult 类 | 分页响应格式 | PageResult.java | P0 |
| 创建 ResultCode 枚举 | 响应码枚举（200, 400, 401, 403, 404, 500） | ResultCode.java | P0 |
| 创建响应工具方法 | success(), error(), build() | Result.java | P0 |

#### 2.1.2 基础实体类
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建 BaseEntity | id, createBy, createTime, updateBy, updateTime, remark | BaseEntity.java | P0 |
| 创建 PageQuery | 分页查询基类 | PageQuery.java | P0 |
| 创建逻辑删除 | @Where 子句过滤 deleted=0 | BaseEntity.java | P0 |

#### 2.1.3 全局异常处理
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建业务异常 | BusinessException | BusinessException.java | P0 |
| 创建全局异常处理器 | @RestControllerAdvice | GlobalExceptionHandler.java | P0 |
| 处理参数校验异常 | ConstraintViolationException | GlobalExceptionHandler.java | P0 |
| 处理 JPA 异常 | DataIntegrityViolationException | GlobalExceptionHandler.java | P0 |

#### 2.1.4 工具类
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 字符串工具类 | StringUtils 常用方法 | StringUtils.java | P1 |
| 日期工具类 | DateUtils 常用方法 | DateUtils.java | P1 |
| IP 工具类 | 获取客户端 IP | IpUtils.java | P1 |
| 密码工具类 | BCrypt 加密验证 | PasswordUtils.java | P0 |

### 2.2 安全模块开发

#### 2.2.1 JWT Token 模块
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建 Token 提供者 | 生成、解析、验证 Token | JwtTokenProvider.java | P0 |
| 创建 Token 刷新机制 | RefreshToken 刷新 AccessToken | TokenService.java | P0 |
| 配置 Token 有效期 | AccessToken: 15分钟, RefreshToken: 7天 | application.yml | P0 |
| 创建 Token 黑名单 | Redis 存储已失效 Token | TokenBlacklistService.java | P1 |

#### 2.2.2 Spring Security 配置
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建 Security 配置类 | @EnableWebSecurity, CORS 配置 | SecurityConfig.java | P0 |
| 配置密码加密器 | BCryptPasswordEncoder | SecurityConfig.java | P0 |
| 配置忽略路径 | /api/auth/**, /swagger/** 等 | SecurityConfig.java | P0 |
| 配置会话管理 | 无状态 session | SecurityConfig.java | P0 |

#### 2.2.3 JWT 认证过滤器
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建认证过滤器 | JwtAuthenticationFilter | JwtAuthenticationFilter.java | P0 |
| 解析 Token | 从 Header 提取 Token | JwtAuthenticationFilter.java | P0 |
| 验证 Token | 调用 JwtTokenProvider 验证 | JwtAuthenticationFilter.java | P0 |
| 设置用户上下文 | SecurityContextHolder 设置认证用户 | JwtAuthenticationFilter.java | P0 |
| 处理 Token 过期 | 返回 401 响应，引导刷新 Token | JwtAuthenticationFilter.java | P0 |

#### 2.2.4 权限校验服务
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建权限服务 | SecurityPermissionService | SecurityPermissionService.java | P0 |
| 获取用户权限 | 从数据库加载用户权限 | SecurityPermissionService.java | P0 |
| 权限校验方法 | hasPermission(code) | SecurityPermissionService.java | P0 |
| 注解式权限校验 | @RequirePermission 注解 | RequirePermission.java | P1 |

### 2.3 认证授权模块

#### 2.3.1 登录功能
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建登录表单类 | LoginForm {username, password, captcha, captchaKey} | LoginForm.java | P0 |
| 创建登录 VO | LoginVO {accessToken, refreshToken, expiresIn} | LoginVO.java | P0 |
| 创建登录接口 | POST /api/auth/login | AuthController.java | P0 |
| 验证用户名密码 | 调用 UserDetailsService | AuthService.java | P0 |
| 生成 Token | 调用 JwtTokenProvider | AuthService.java | P0 |
| 记录登录日志 | 保存登录信息到 sys_login_log | LoginLogService.java | P0 |

#### 2.3.2 登出功能
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建登出接口 | POST /api/auth/logout | AuthController.java | P0 |
| 添加 Token 到黑名单 | Redis 存储失效 Token | AuthService.java | P0 |
| 清除 SecurityContext | SecurityContextHolder.clearContext() | AuthService.java | P0 |

#### 2.3.3 Token 刷新
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建刷新接口 | POST /api/auth/refresh | AuthController.java | P0 |
| 验证 RefreshToken | 检查 Token 是否有效 | AuthService.java | P0 |
| 生成新 Token | 生成新的 AccessToken 和 RefreshToken | AuthService.java | P0 |
| 旧 RefreshToken 失效 | 添加到黑名单 | AuthService.java | P0 |

#### 2.3.4 获取用户信息
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建用户信息 VO | UserInfoVO {id, username, nickname, avatar, roles, permissions} | UserInfoVO.java | P0 |
| 创建获取信息接口 | GET /api/auth/info | AuthController.java | P0 |
| 加载用户权限 | 查询用户角色和权限 | AuthService.java | P0 |
| 构建权限树 | 构建前端需要的权限树结构 | AuthService.java | P0 |

#### 2.3.5 前端认证集成
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|---------|--------|
| 创建认证 API | auth.ts 封装登录、登出、刷新接口 | api/auth.ts | P0 |
| 创建认证状态管理 | authStore 管理 Token 和用户信息 | stores/auth.ts | P0 |
| 创建登录页面 | Login 页面，包含表单和验证码 | routes/login/+page.svelte | P0 |
| 创建路由守卫 | 前端路由守卫，未登录跳转登录 | hooks.server.ts | P0 |
| 配置 Axios 拦截器 | 自动携带 Token，处理 401 | utils/request.ts | P0 |

### 2.4 用户管理模块

#### 2.4.1 后端用户管理
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建用户实体 | User extends BaseEntity | User.java | P0 |
| 创建用户查询类 | UserQuery extends PageQuery | UserQuery.java | P0 |
| 创建用户表单类 | UserForm {username, nickname, email, phone, roleIds} | UserForm.java | P0 |
| 创建用户 VO | UserVO 包含用户信息和角色 | UserVO.java | P0 |
| 创建用户仓储 | UserRepository extends JpaRepository | UserRepository.java | P0 |
| 创建用户服务 | UserService | UserService.java | P0 |
| 创建用户控制器 | UserController | UserController.java | P0 |

#### 2.4.2 用户 CRUD 接口
| 任务 | 具体操作 | 接口 | 优先级 |
|------|----------|------|--------|
| 用户分页列表 | GET /api/system/user | UserController | P0 |
| 用户详情 | GET /api/system/user/{id} | UserController | P0 |
| 新增用户 | POST /api/system/user | UserController | P0 |
| 修改用户 | PUT /api/system/user/{id} | UserController | P0 |
| 删除用户 | DELETE /api/system/user/{id} | UserController | P0 |
| 批量删除用户 | DELETE /api/system/user | UserController | P0 |
| 修改用户状态 | PUT /api/system/user/{id}/status | UserController | P0 |

#### 2.4.3 前端用户管理
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|---------|--------|
| 创建用户 API | user.ts 封装用户接口 | api/user.ts | P0 |
| 创建用户状态管理 | userStore | stores/user.ts | P0 |
| 创建用户列表页面 | UserList 页面，包含表格和分页 | routes/(admin)/system/user/+page.svelte | P0 |
| 创建用户新增/编辑弹窗 | UserModal 组件 | components/UserModal.svelte | P0 |
| 创建用户状态切换 | 启用/禁用状态切换 | UserModal.svelte | P0 |

### 2.5 角色权限模块

#### 2.5.1 后端角色管理
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建角色实体 | Role extends BaseEntity | Role.java | P0 |
| 创建角色仓储 | RoleRepository | RoleRepository.java | P0 |
| 创建角色服务 | RoleService | RoleService.java | P0 |
| 创建角色控制器 | RoleController | RoleController.java | P0 |
| CRUD 接口 | POST/GET/PUT/DELETE /api/system/role | RoleController | P0 |
| 角色授权接口 | PUT /api/system/role/{id}/permissions | RoleController | P0 |

#### 2.5.2 后端权限管理
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建权限实体 | Permission extends BaseEntity | Permission.java | P0 |
| 创建权限类型枚举 | PermissionType {MENU, BUTTON, API} | PermissionType.java | P0 |
| 创建权限仓储 | PermissionRepository | PermissionRepository.java | P0 |
| 创建权限服务 | PermissionService | PermissionService.java | P0 |
| 创建权限控制器 | PermissionController | PermissionController.java | P0 |
| 权限树接口 | GET /api/system/permission/tree | PermissionController | P0 |

#### 2.5.3 后端菜单管理
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建菜单实体 | Menu extends BaseEntity | Menu.java | P0 |
| 创建菜单类型枚举 | MenuType {DIR, MENU, BUTTON} | MenuType.java | P0 |
| 创建菜单仓储 | MenuRepository | MenuRepository.java | P0 |
| 创建菜单服务 | MenuService | MenuService.java | P0 |
| 创建菜单控制器 | MenuController | MenuController.java | P0 |
| 菜单树接口 | GET /api/system/menu/tree | MenuController | P0 |
| 获取用户菜单树 | 根据用户角色获取菜单 | MenuService | P0 |

#### 2.5.4 前端角色权限管理
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|---------|--------|
| 创建角色管理页面 | RoleList 页面 | routes/(admin)/system/role/+page.svelte | P0 |
| 创建权限分配弹窗 | PermissionModal 组件 | components/PermissionModal.svelte | P0 |
| 创建菜单管理页面 | MenuList 页面，树形展示 | routes/(admin)/system/menu/+page.svelte | P0 |
| 创建菜单编辑弹窗 | MenuModal 组件 | components/MenuModal.svelte | P0 |
| 创建权限管理页面 | PermissionList 页面 | routes/(admin)/system/permission/+page.svelte | P0 |

---

## 阶段三：系统管理功能

### 3.1 系统配置模块

#### 3.1.1 后端配置管理
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建配置实体 | SysConfig extends BaseEntity | SysConfig.java | P0 |
| 创建配置服务 | SysConfigService | SysConfigService.java | P0 |
| 创建配置控制器 | SysConfigController | SysConfigController.java | P0 |
| CRUD 接口 | POST/GET/PUT/DELETE /api/system/config | SysConfigController | P0 |
| 根据键获取值 | GET /api/system/config/{key} | SysConfigController | P0 |
| Redis 缓存配置 | @Cacheable 缓存配置 | SysConfigService | P1 |
| 配置变更通知 | 配置更新时清除缓存 | SysConfigService | P1 |

#### 3.1.2 前端配置管理
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|---------|--------|
| 创建配置管理页面 | ConfigList 页面 | routes/(admin)/system/config/+page.svelte | P0 |
| 创建配置编辑弹窗 | ConfigModal 组件 | components/ConfigModal.svelte | P1 |

### 3.2 字典管理模块

#### 3.2.1 后端字典管理
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建字典类型实体 | DictType extends BaseEntity | DictType.java | P0 |
| 创建字典数据实体 | DictData extends BaseEntity | DictData.java | P0 |
| 创建字典服务 | DictService | DictService.java | P0 |
| 创建字典控制器 | DictController | DictController.java | P0 |
| 字典类型 CRUD | POST/GET/PUT/DELETE /api/system/dict/type | DictController | P0 |
| 字典数据 CRUD | POST/GET/PUT/DELETE /api/system/dict/data | DictController | P0 |
| 获取字典项 | GET /api/system/dict/data/{typeCode} | DictController | P0 |
| Redis 缓存字典 | @Cacheable 缓存字典数据 | DictService | P1 |

#### 3.2.2 前端字典管理
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|---------|--------|
| 创建字典类型页面 | DictTypeList 页面 | routes/(admin)/tools/dict/type/+page.svelte | P0 |
| 创建字典数据页面 | DictDataList 页面 | routes/(admin)/tools/dict/data/+page.svelte | P0 |
| 创建字典组件 | DictSelect 组件，表单中使用 | components/DictSelect.svelte | P0 |

### 3.3 日志管理模块

#### 3.3.1 后端日志管理
| 任务 | 具体操作 | 接口/类 | 优先级 |
|------|----------|---------|--------|
| 创建登录日志实体 | LoginLog | LoginLog.java | P0 |
| 创建操作日志实体 | OperationLog | OperationLog.java | P0 |
| 创建异常日志实体 | ErrorLog | ErrorLog.java | P0 |
| 创建日志服务 | LogService | LogService.java | P0 |
| 创建日志控制器 | LogController | LogController.java | P0 |
| 登录日志分页 | GET /api/system/log/login | LogController | P0 |
| 操作日志分页 | GET /api/system/log/operation | LogController | P0 |
| 异常日志分页 | GET /api/system/log/error | LogController | P0 |
| 操作日志详情 | GET /api/system/log/operation/{id} | LogController | P1 |
| AOP 记录操作日志 | @Log 注解，AOP 拦截记录 | LogAspect.java | P1 |

#### 3.3.2 前端日志管理
| 任务 | 具体操作 | 产出物 | 优先级 |
|------|----------|---------|--------|
| 创建登录日志页面 | LoginLogList 页面 | routes/(admin)/monitor/login-log/+page.svelte | P0 |
| 创建操作日志页面 | OperationLogList 页面 | routes/(admin)/monitor/operation-log/+page.svelte | P0 |
| 创建异常日志页面 | ErrorLogList 页面 | routes/(admin)/monitor/error-log/+page.svelte | P0 |
| 创建日志详情弹窗 | LogDetailModal 组件 | components/LogDetailModal.svelte | P1 |

---

## 阶段四：高级功能

### 4.1 消息通知模块

#### 4.1.1 系统公告
| 任务 | 具体操作 | 优先级 |
|------|----------|--------|
| 创建公告实体 | Notice extends BaseEntity | P1 |
| 创建公告服务 | NoticeService | P1 |
| 创建公告控制器 | NoticeController | P1 |
| CRUD 接口 | POST/GET/PUT/DELETE /api/system/notice | P1 |
| 公告列表接口 | GET /api/system/notice | P1 |
| 前端公告管理页面 | NoticeList 页面 | P1 |

#### 4.1.2 站内信
| 任务 | 具体操作 | 优先级 |
|------|----------|--------|
| 创建消息实体 | Message extends BaseEntity | P2 |
| 创建消息服务 | MessageService | P2 |
| 创建消息控制器 | MessageController | P2 |
| 发送消息接口 | POST /api/system/message | P2 |
| 获取我的消息 | GET /api/system/message/my | P2 |
| 标记已读 | PUT /api/system/message/{id}/read | P2 |
| 前端消息页面 | MessageList 页面 | P2 |

### 4.2 文件管理模块

#### 4.2.1 后端文件管理
| 任务 | 具体操作 | 优先级 |
|------|----------|--------|
| 配置文件上传 | application.yml 配置上传限制 | P1 |
| 创建文件服务 | FileService | P1 |
| 文件上传接口 | POST /api/system/file/upload | P1 |
| 文件删除接口 | DELETE /api/system/file/{id} | P1 |
| 文件列表接口 | GET /api/system/file | P1 |
| MinIO/本地存储切换 | 存储策略模式 | P2 |

#### 4.2.2 前端文件管理
| 任务 | 具体操作 | 优先级 |
|------|----------|--------|
| 创建文件上传组件 | FileUpload 组件 | P1 |
| 创建文件列表页面 | FileList 页面 | P1 |
| 集成富文本编辑器 | Tiptap/Viggo 编辑器 | P2 |

### 4.3 数据导入导出

#### 4.3.1 Excel 导入导出
| 任务 | 具体操作 | 优先级 |
|------|----------|--------|
| 集成 EasyExcel | pom.xml 添加依赖 | P1 |
| 创建导入监听器 | ExcelImportListener | P1 |
| 用户导出接口 | GET /api/system/user/export | P1 |
| 用户导入接口 | POST /api/system/user/import | P1 |
| 前端导入组件 | ImportModal 组件 | P1 |

---

## 阶段五：测试与优化

### 5.1 单元测试

| 任务 | 具体操作 | 优先级 |
|------|----------|--------|
| 配置 JUnit5 | 添加 spring-boot-starter-test | P0 |
| 测试认证服务 | AuthService 单元测试 | P0 |
| 测试用户服务 | UserService 单元测试 | P1 |
| 测试角色服务 | RoleService 单元测试 | P1 |
| 测试 JWT 工具 | JwtTokenProvider 单元测试 | P0 |

### 5.2 集成测试

| 任务 | 具体操作 | 优先级 |
|------|----------|--------|
| API 集成测试 | 使用 MockMvc 测试 Controller | P1 |
| 数据库集成测试 | 使用 @DataJpaTest 测试 Repository | P1 |
| 安全配置测试 | 测试认证和授权流程 | P1 |

### 5.3 性能优化

| 任务 | 具体操作 | 优先级 |
|------|----------|--------|
| Redis 缓存优化 | 缓存用户权限、菜单、字典 | P1 |
| 数据库索引优化 | 根据慢查询添加索引 | P1 |
| 虚拟线程配置 | 配置虚拟线程 Executor | P1 |
| 前端性能优化 | 路由懒加载、组件懒加载 | P1 |

---

## 开发优先级总结

| 优先级 | 模块/任务 | 预计工时 | 说明 |
|--------|-----------|----------|------|
| P0 | 项目初始化、前端基础架构、后端基础架构 | 3-5天 | 必须完成的基础工作 |
| P0 | 公共模块（Result、异常、工具类） | 1-2天 | 后续开发的基础 |
| P0 | 安全模块（JWT、Security、认证过滤器） | 2-3天 | 核心安全功能 |
| P0 | 认证授权（登录、登出、Token刷新） | 2-3天 | 用户认证功能 |
| P0 | 用户管理（CRUD、分页、状态） | 2-3天 | 核心业务功能 |
| P0 | 角色权限（CRUD、权限分配） | 3-5天 | 核心业务功能 |
| P0 | 菜单管理（CRUD、菜单树） | 2-3天 | 核心业务功能 |
| P1 | 系统配置、字典管理 | 2-3天 | 系统管理功能 |
| P1 | 日志管理（登录、操作、异常日志） | 2-3天 | 系统管理功能 |
| P1 | 文件管理、数据导入导出 | 2-3天 | 高级功能 |
| P2 | 消息通知模块 | 3-5天 | 可选功能 |
| P1 | 单元测试、集成测试 | 持续 | 质量保障 |
| P1 | 性能优化 | 1-2天 | 可选优化 |

---

## 文档版本记录

| 版本 | 日期 | 修改内容 | 作者 |
|------|------|----------|------|
| V1.0.0 | 2026-04-24 | 初始版本，包含需求文档和设计文档 | AI Assistant |
| V1.0.1 | 2026-04-24 | 增强下一步开发计划，详细描述各阶段任务 | AI Assistant |
| V1.0.2 | 2026-04-27 | 完成项目初始化阶段：创建前端项目(SvelteKit+TailwindCSS)、后端项目(SpringBoot+JDK25)、数据库初始化脚本 | AI Assistant |
