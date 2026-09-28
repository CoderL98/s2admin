# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

注意前端开发时使用 /frontend-design 技能进行设计 
项目全局持续使用 /planning-with-files 跟踪进度 
所有npm pnpm npx命令前加上 source ~/proxy.sh

## 语言要求

所有回复使用简体中文。

## 项目概述

s2admin 是一个通用后台管理系统骨架,采用前后端分离架构,目标是为后续业务系统提供可改造、可定制的脚手架。

- **前端**: Svelte 5 (Runes 模式) + SvelteKit 2 + shadcn-svelte + TailwindCSS 4 + TypeScript
- **后端**: Spring Boot 3.5.16 + JDK 25 + Spring Security 6 + Spring Data JPA + JJWT 0.12.5
- **数据**: SQLite(默认,零依赖) / MySQL / PostgreSQL 切换;缓存:进程内内存(默认) / Redis 切换
- **切换方式**: 数据源 `mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=mysql` 或 `postgres`;缓存 `--s2admin.cache.type=redis`(默认 memory)。SQLite 文件在 `backend/data/s2admin.db`
- **虚拟线程已关闭**(`spring.threads.virtual.enabled: false`):sqlite-jdbc 是 JNI 阻塞库,虚拟线程在原生调用钉住会导致 Hikari 连接孤儿/写锁悬挂,SQLite 模式必须关闭
- **认证**: JWT Token(AccessToken 15 分钟 / RefreshToken 7 天) + RBAC 权限模型
- **规划文件**: 根目录的 `task_plan.md` / `findings.md` / `progress.md` / `需求文档.md` / `设计文档.md` 记录了完整的需求、设计与进度,需开发前查阅

## 常用命令

### 前端 (`frontend/`)

| 操作 | 命令 |
|------|------|
| 安装依赖 | `pnpm install` (项目使用 pnpm,见 `pnpm-lock.yaml`) |
| 启动 dev 服务器 | `pnpm dev` |
| 类型检查 | `pnpm check` (svelte-check) |
| 类型检查(监听) | `pnpm check:watch` |
| 生产构建 | `pnpm build` |
| 预览生产包 | `pnpm preview` |
| 添加 shadcn-svelte 组件 | `npx shadcn-svelte@latest add <component>` |
| 端口与代理 | `vite.config.ts` 目前只有 tailwindcss + sveltekit 插件,后端代理按需添加 |

### 后端 (`backend/`)

| 操作 | 命令 |
|------|------|
| 启动 | `mvn spring-boot:run` (需 JDK 25) |
| 编译 | `mvn clean compile` |
| 打包 | `mvn clean package` (跳过测试加 `-DskipTests`) |
| 运行测试 | `mvn test` |
| 跑单个测试 | `mvn test -Dtest=ClassName#methodName` |

### 数据库

- 初始化脚本: `backend/src/main/resources/db/init.sql` (含 12 张核心表 + 初始化数据)
- 编译产物同步路径: `backend/target/classes/db/init.sql` (Maven 资源拷贝结果)
- 默认连接: `jdbc:mysql://localhost:3306/s2admin` (账号 `root` / `root123`),`application.yml` 可改

## 目录结构

```
s2admin/
├── backend/                        Spring Boot 后端
│   ├── pom.xml                     Maven 配置(JDK 25 / Spring Boot 3.5.16)
│   └── src/main/
│       ├── java/com/s2admin/
│       │   ├── S2AdminApplication.java   启动类
│       │   ├── config/                   配置类(目前为空目录,待 Security/CORS/Redis 配置)
│       │   └── module/
│       │       ├── common/               Result / PageResult / BaseEntity / PageQuery
│       │       ├── security/             JWT / Spring Security 相关(待开发)
│       │       ├── util/                 工具类(待开发)
│       │       └── system/               系统管理域(目前只建好子包骨架)
│       │           ├── controller/       REST 控制器
│       │           ├── service/          业务服务
│       │           ├── repository/       JPA 仓储
│       │           ├── entity/           JPA 实体(继承 BaseEntity)
│       │           ├── form/             入参 DTO(带 @Valid 校验)
│       │           └── vo/               出参 VO
│       └── resources/
│           ├── application.yml           应用配置(端口 8080、JWT、MySQL、Redis)
│           └── db/init.sql               数据库初始化脚本
│
└── frontend/                       SvelteKit 前端
    ├── package.json                pnpm 工程,使用 Svelte 5 Runes
    ├── svelte.config.js            强制所有非 node_modules 文件走 Runes 模式
    ├── vite.config.ts              极简:t tailwindcss + sveltekit 插件
    ├── components.json             shadcn-svelte 配置(neutral 主题、vega 风格、lucide 图标)
    ├── tsconfig.json               strict + bundler resolution
    ├── .env.example                环境变量模板(API_BASE_URL、Token 键名等)
    ├── .env.local                  本地开发配置(不提交)
    ├── .env.production             生产环境模板
    └── src/
        ├── app.d.ts                SvelteKit 类型声明
        ├── app.html                HTML 模板
        ├── routes/                 文件系统路由
        │   ├── +layout.svelte      根布局(挂载 layout.css + favicon)
        │   ├── +page.svelte        根路径(目前是欢迎页)
        │   └── (admin)/            路由分组(URL 中不出现,用于组织后台页面)
        │       ├── login/          登录页 + LoginForm 组件
        │       ├── dashboard/      仪表盘(SectionCards + ChartAreaInteractive + DataTable)
        │       ├── system/         系统管理: user / role / menu / permission / config(目录已建,页面待开发)
        │       ├── monitor/        系统监控: login-log / op-log / error-log
        │       └── tools/          系统工具: dict / build
        └── lib/
            ├── index.ts            $lib 入口(目前空)
            ├── api/                API 封装目录(目前空,待开发)
            ├── stores/             状态管理(目前空,待开发)
            ├── types/              全局类型(目前空,待开发)
            ├── hooks/              Svelte 5 hooks(如 is-mobile.svelte.ts)
            ├── utils/
            │   └── request.ts      Fetch 封装的 HTTP 客户端(get/post/put/del/upload + Token 注入)
            ├── components/
            │   ├── schemas.ts      DataTable 行类型定义
            │   ├── index.ts        组件聚合导出
            │   ├── app-sidebar.svelte / site-header.svelte / section-cards.svelte
            │   ├── chart-area-interactive.svelte (layerchart)
            │   ├── login-form.svelte
            │   ├── nav-*.svelte    (main/documents/secondary/user) 侧边栏导航
            │   ├── data-table-*.svelte  旧的单文件 DataTable 拆分组件
            │   ├── data-table/     新版模块化 DataTable(分 files / columns / state / toolbar / pagination / content)
            │   └── ui/             shadcn-svelte 组件(avatar badge button card chart checkbox data-table drawer dropdown-menu field input label select separator sheet sidebar skeleton table tabs toggle toggle-group tooltip)
            └── assets/             静态资源(含 favicon.svg)
```

## 架构要点

### 后端包分层

按模块(`module/*`)而非按层组织代码,每个业务域自带 `controller → service → repository → entity → form/vo` 全套。`module/common` 提供所有域复用的 `Result` / `PageResult` / `BaseEntity` / `PageQuery`。

- 新增业务域: 在 `com.s2admin.module` 下建包,沿用 `entity/service/repository/controller/form/vo` 六件套
- 实体继承 `BaseEntity`(id/createBy/createTime/updateBy/updateTime/remark),`deleted` 字段由 SQL 维护,Java 端不出现
- 所有 Controller 返回 `Result<T>`,分页用 `PageResult<T>`
- `PageQuery` 提供 pageNum/pageSize/orderBy/sortDirection + 计算好的 `offset`

### 统一响应结构

后端 `Result<T>` 与前端 `request.ts` 中 `handleApiResponse` 一一对应:

```ts
{ code: number; message: string; data: T; timestamp: number }
```

前端只在 `code === 200` 时返回 `data`,否则抛 `ApiError(code, message, data)`。响应码常量在 `request.ts` 顶部(`ResultCode`)。

### 前端 API 调用

- 基础地址、Token 键名均来自 `PUBLIC_*` 环境变量(`$env/dynamic/public` 读取)
- `request.ts` 默认 `withToken: true`,自动从 localStorage 取 Token 拼 `Authorization: Bearer <token>`
- 上传文件用 `upload<T>(endpoint, FormData)`,不会强制设 `Content-Type`
- 新增业务 API: 在 `src/lib/api/<domain>.ts` 编写,导出函数并复用 `request`/`get`/`post`/`put`/`del`

### 前端 Svelte 5 规范

- **全项目强制 Runes 模式**(`svelte.config.js` 中 `runes: ({ filename }) => filename.includes('node_modules') ? undefined : true`)
- 组件用 `let { xxx } = $props()` 声明属性
- 响应式状态用 `$state` / `$derived` / `$effect`,不再用 `$:` 与 `export let`
- 自定义 hook 文件用 `.svelte.ts` 后缀(如 `is-mobile.svelte.ts`)
- shadcn-svelte 的 bits-ui 组件按 `$lib/components/ui/<name>/index.ts` 聚合导出
- 优先使用shadcn-svelte的组件库 尽量不使用自定义组件

### DataTable 组件

- 新版模块化版本: `src/lib/components/data-table/data-table.svelte` + `data-table-columns.ts` + `data-table-state.ts` + `data-table-{toolbar,pagination,content}.svelte` + `index.ts`
- 旧版单文件拆分组件(`data-table-*.svelte`)保留但不再使用,新增页面请用新版
- 表格核心基于 `@tanstack/table-core` 的 `createSvelteTable`(`ui/data-table/data-table.svelte.ts`)
- 默认列定义在 `data-table-columns.ts` 的 `getDefaultColumns()`,预设列类型见 `columnPresets`
- 行类型定义在 `../schemas.ts` 的 `Schema`,自定义列需扩展此类型或新建独立 schema

### 路由与布局

- `(admin)` 是 SvelteKit 路由分组(URL 中不出现),用于统一后台页面的 chrome(侧边栏、顶栏)
- `routes/+layout.svelte` 是根布局(挂 `layout.css` 和 favicon)
- `(admin)/dashboard/+page.svelte` 内已使用 `Sidebar.Provider` + `AppSidebar` + `SiteHeader` 组成完整后台框架
- 新建后台页面: 在 `(admin)/<domain>/<page>/+page.svelte` 创建,**注意: `(admin)/system/{user,role,menu,permission,config}` 目录已建但内部是空的,需要补 `+page.svelte` 才能访问**

### 权限模型(RBAC)

- `sys_user` ↔ `sys_role` (多对多,中间表 `sys_user_role`)
- `sys_role` ↔ `sys_permission` (多对多,中间表 `sys_role_permission`)
- 权限类型: 1 菜单 / 2 按钮 / 3 API
- 数据范围: 1 全部 / 2 本部门及以下 / 3 本部门 / 4 本人
- 角色代码: `SUPER_ADMIN` / `ADMIN` / `USER` / `AUDITOR` / `GUEST`
- 初始化账号 `admin` / `admin123`(BCrypt 已加密,见 `init.sql`)
- 默认菜单: 系统管理 / 系统监控 / 系统工具(详见 `init.sql` 第 275-286 行)
- 默认权限编码: `system:user:view` / `system:user:add` / `system:user:edit` / `system:user:delete` / `system:role:*` 等(详见 `init.sql` 第 289-298 行)

### 数据库

- 12 张表,统一前缀 `sys_`,使用 `utf8mb4_unicode_ci`,引擎 InnoDB
- 所有表含 `deleted` 软删除字段(0 正常 / 1 已删),由 SQL 维护,Java 端不出现
- 所有表含审计字段 `create_by` / `create_time` / `update_by` / `update_time`
- 启动时 `ddl-auto: update` 会同步实体变更,**不要在线上生产环境使用 update,改用 Flyway 迁移**

## 当前进度

- ✅ 阶段 1-4: 需求与设计文档
- ✅ 阶段 5: 项目初始化(前后端脚手架 + DB 脚本)
- ✅ 阶段 5.1: 前端登录页、仪表盘、shadcn 组件库
- 🚧 阶段 6: **核心功能开发,待开始** — 公共模块、Spring Security/JWT、登录登出、用户/角色/菜单 CRUD 都要从零补

后端目前只有 `S2AdminApplication.java` + 4 个 common 公共类。`module/system`、`module/security`、`module/util`、`config/` 目录都是空骨架,需按 `task_plan.md` 阶段 6 顺序补齐。

前端 `lib/api/`、`lib/stores/`、`lib/types/` 三个目录为空,API 调用与状态管理还未封装,页面中也尚未引入 `request.ts`。
