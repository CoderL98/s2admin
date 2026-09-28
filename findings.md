# 研究发现与决策记录

## 项目概述

### 项目名称
通用后台管理系统 (s2admin)

### 项目目标
构建一套功能完整、架构合理的后台管理基础框架，支持后续各类业务系统的快速改造和定制开发。

### 技术栈

#### 前端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| Svelte | 5.x | 前端框架，采用 Runes 语法进行响应式编程 |
| SvelteKit | 2.x | 全栈框架，提供文件系统路由和 SSR 能力 |
| shadcn-svelte | latest | UI 组件库，基于 TailwindCSS 的无头组件 |
| TailwindCSS | 3.x | CSS 框架，提供原子化样式 |
| TypeScript | 5.x | 类型安全，增强代码可维护性 |
| Pinia | 2.x | Svelte 官方推荐的状态管理库 |

#### 后端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 25 | Java 开发环境，包含虚拟线程等新特性 |
| SpringBoot | 3.x | 应用框架，简化 Spring 配置 |
| Spring Security | 6.x | 安全框架，提供认证授权能力 |
| Spring Data JPA | 3.x | 数据访问层，简化数据库操作 |
| SQLite | 3.x | 默认数据源，零依赖(可切换 MySQL/PostgreSQL) |
| 缓存 | 内存/Redis | 默认进程内缓存(可切换 Redis) |
| JJWT | 0.12.x | JWT Token 生成和验证库 |

---

## 研究发现

### 2026-09-23 逻辑自检

- 登录失败若和 `status=2` 写在同一个事务里，抛错会把锁定回滚。锁定必须在独立事务提交；自动锁定的截止时间要落在 `sys_user.lock_until`，不能只放缓存，否则重启后分不清「到期的自动锁」和「管理员锁定」。
- 默认不限制端数时，把超出展示上限的会话从列表拿掉但不记入踢出集合，那些 refresh 仍然有效。踢出记录也不能只留一个会溢出的短列表。
- 空权限码的目录会出现在所有人的菜单里，前端再用「路径前缀」判断访问，`/system` 会把 `/system/user` 一并放行。用户菜单只应包含有权限的页面，外加它们的祖先目录。
- 有 `system:user:edit` / `system:role:assign` 的人不能分配比自己更宽的数据范围或自己没有的权限码。已有角色可以保留，避免编辑资料时被迫改角色。
- 导入只写 `deptName` 时必须解析成 `deptId`。编辑用户时未提交头像不能把原头像写成空。

### 2026-08-26 多端登录

- 只把 access `jti` 放进会话列表不够：被踢设备仍可用 refresh 换新 access 再注册。access 与 refresh 必须带同一 `sid`，刷新时校验 `sid` 仍在允许列表且不在 kicked。
- `maxSessions=0` 时若 `register` 直接 return，个人中心永远看不到设备。应始终写入设备列表，限制端数只决定是否把溢出 sid 打进 kicked。
- 缓存被清空后 `devices` 为空应 fail-open，否则 Redis 重启会把所有人踢下线。

### 2026-08-26 前端审计

- 强制改密时 layout 不能先渲染 dashboard，否则 stats/公告会 403；改密成功不要再打已拉黑的 logout。
- `handleApiResponse` 的业务 401（HTTP 200）不能当会话过期。
- 字典页 `$effect` 读写 `dataPageNum` 会把分页打回第 1 页，要用 `untrack`。
- `PUBLIC_USE_MOCK=true` 时 dept/file/notice/message/codegen 若仍打真实后端，mock JWT 会被 401 踢下线。

### 2026-08-26 审计补丁

- `open-in-view=false` 下 `user.getRoles()` 必须 JOIN FETCH 或在事务里，否则仪表盘 500。
- `@CreatedBy` 没有 `AuditingEntityListener` 时 createBy 恒为 null，文件 dataScope 会把自己的文件滤掉。
- 有 `system:role:add` 就能建 dataScope=1 的角色抬权；创建/更新不得宽于当前用户。
- `pwd_reset` 只加实体不够，已有库要 ALTER；prod `ddl-auto=validate` 尤其如此。

### 2026-08-26 再次自检

- 未填密码若落到固定 `123456`，导入账号可被直接登录；应发随机强口令并只走「重置密码」发放。
- `DELETE /file/{name}` 在库中无记录时仍删磁盘文件；下载也不看归属。非头像必须有元数据并走 dataScope。
- 仪表盘 `count()` 是全局数字，部门管理员会看到全站规模；应按 `visibleUserIds()` 计数。

### 2026-08-26 继续完善

- Refresh Token 旋转必须原子消费 jti（`CacheStore.setIfAbsent`），否则并发刷新会复制会话。
- 操作日志旧值要在 `proceed()` 之前 `EntityManager.find`，删除后再查就是空。
- 未设密/管理员重置密码应写 `pwdReset`，过滤器只放行改密与会话接口。
- dataScope 不能只挡用户列表：文件按 `createBy`、日志按 `userId`、部门树带祖先才能挂得住。

### 2026-08-26 缺陷自检（已修）

- 用户 dataScope 只过滤列表时，`GET/PUT/DELETE /api/system/user/{id}` 可越权，必须在按 ID 的读写上复用同一套 Scope。
- Access Token 里的 `roles` 会过期：方法权限与 dataScope 不能信 JWT，应每次从 DB/缓存加载。
- 文件 `Content-Type` 不能用客户端值；inline + 伪造 HTML 类型会 XSS。按扩展名推断并加 `X-Content-Type-Options: nosniff`。
- GET 分页的 Bean Validation 默认不生效，必须在 `PageQuery.toPageable()` 内硬钳制。
- 树改父级只禁自身不够，要把拟父级沿 parent 链走到根，遇到自己就是环。

### Svelte5 新特性

**Runes 语法**
```javascript
// Svelte 4 - 传统响应式
let count = 0;
$: doubled = count * 2;

// Svelte 5 - Runes 语法
let count = $state(0);
let doubled = $derived(count * 2);
```

**核心 Runes**
| Rune | 功能 | 示例 |
|------|------|------|
| `$state` | 声明响应式状态 | `let count = $state(0)` |
| `$derived` | 派生状态 | `let doubled = $derived(count * 2)` |
| `$effect` | 副作用 | `$effect(() => console.log(count))` |
| `$props` | 组件属性 | `let { name, age } = $props()` |

### shadcn-svelte 组件库

**特点**
- 无头组件：不带样式，可完全定制
- 基于 Radix UI：可访问性有保障
- TailwindCSS 样式：原子化设计
- 按需引入：只导入需要的组件

**常用组件**
```bash
npx shadcn-svelte@latest add button
npx shadcn-svelte@latest add input
npx shadcn-svelte@latest add dialog
npx shadcn-svelte@latest add table
npx shadcn-svelte@latest add dropdown-menu
```

### SpringBoot 3.x 与 JDK25

**JDK25 新特性**
| 特性 | 说明 |
|------|------|
| 虚拟线程 (Virtual Threads) | 轻量级线程，大幅提升并发能力(JDK 21 起正式,本工程已启用) |
| Stream Gatherers | 流式数据自定义中间操作(JDK 25 正式) |
| Flexible Constructor Bodies | 构造器体中允许执行前置语句(JDK 25 正式) |
| Simple Source Files & Instance Main Methods | 简化启动类书写(JDK 25 正式) |
| Security Manager 禁用 | 安全管理器永久禁用(JDK 24 起) |

**SpringBoot 3.x 特性**
- 原生支持 JDK25(Spring Boot 3.5+ 官方支持至 Java 25)
- GraalVM 原生编译支持
- 新的自动配置机制
- 增强的测试支持

### RBAC 权限模型

**模型结构**
```
用户 (User)
    ↓ (多对多)
用户角色关联表 (user_role)
    ↓
角色 (Role)
    ↓ (多对多)
角色权限关联表 (role_permission)
    ↓
权限 (Permission)
    ↓ (一对多)
菜单 (Menu)
```

**权限类型**
| 类型 | 代码 | 说明 |
|------|------|------|
| 菜单权限 | 1 | 对应菜单项 |
| 按钮权限 | 2 | 对应操作按钮 |
| API权限 | 3 | 对应后端接口 |

**数据范围**
| 值 | 说明 |
|------|------|
| 1 | 全部数据 |
| 2 | 本部门及以下数据 |
| 3 | 本部门数据 |
| 4 | 本人数据 |

---

## 技术决策

### 架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 前后端分离 | 是 | 前端独立部署，前后端解耦，支持多端 |
| 认证方式 | JWT Token | 无状态，适合分布式和微服务架构 |
| 权限模型 | RBAC | 企业级标准，通用且灵活 |
| 接口风格 | RESTful | 标准化，易于理解和使用 |
| 模块设计 | 模块化 | 低耦合高内聚，便于扩展 |

### 安全决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 密码加密 | BCrypt | 行业标准，内置盐值，防彩虹表攻击 |
| 传输安全 | HTTPS | 全站加密，防止中间人攻击 |
| XSS防护 | 输入过滤+输出转义 | 双重防护 |
| SQL注入防护 | 参数化查询 | JPA 原生支持 |
| CSRF防护 | JWT Token | Token 机制天然防护 |
| 接口限流 | 限流组件 | 防止恶意请求和暴力破解 |

### 性能决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 字典缓存 | Redis | 高性能，支持多种数据结构 |
| 菜单缓存 | Redis | 减少数据库查询 |
| JWT AccessToken | 15分钟 | 短期令牌，安全性保障 |
| JWT RefreshToken | 7天 | 长期令牌，用于刷新 |
| 分页大小 | 默认10条 | 平衡性能和用户体验 |

---

## 遇到的问题与解决

| 问题 | 发现场景 | 解决方案 |
|------|----------|----------|
| SUPER_ADMIN 权限码为 `*` 时 `@PreAuthorize("hasAuthority('system:user:view')")` 全部 403 | 对接真实后端 | 改为 `@ss.hasPermission`，SUPER_ADMIN / `*` 视为全权限 |
| 登出后 AccessToken 仍可用 | JWT 过滤器未查黑名单 | 抽出 TokenBlacklistService，过滤器拒绝已拉黑 token |
| 刷新页面 currentUser 为空、401 不清会话 | 前端只恢复 token | authStore.hydrate + setAuthExpiredHandler |
| 角色 dataScope / 菜单类型 Select 0 基下标 | 编辑回显错乱 | 使用 1-4 / 1-3 真实枚举值 |
| 列表一律 pageSize=1000 | 仪表盘与 CRUD | CrudTable 真分页 + 仪表盘独立 stats 接口 |
| OperationLogAspect.setParams 编译失败 | 实体无 params 字段 | 写入 newValue,并脱敏 password/token |
| 登录 @Transactional(readOnly) 写登录日志 | 日志可能落不了库 | LoginLogService 独立 REQUIRES_NEW |
| init.sql 监控/工具 parent_id 指错 | 菜单树挂到配置/异常日志下 | 监控=7,工具=11；权限树同步修正 |
| 软删除后 UNIQUE 仍占坑 | 删用户/角色后无法再建同名 | 删除前把唯一字段改写成 `value__del_{id}` 再 SQLDelete |
| 任意编辑者可赋 SUPER_ADMIN | 角色多选无保护 | 仅超管可分配/修改超管用户；禁止把其它角色编码改为 SUPER_ADMIN |
| `/uploads/**` permitAll | UUID 文件名可被猜下 | 改为登录后 `/api/system/file/{filename}` 下载删除；头像走带 Token 的 AuthImage |
| 空菜单回退全量侧栏 | 无权限账号仍看见全部入口 | 去掉 fallbackGroups；`canAccess` 在菜单为空时只放行仪表盘/个人中心 |
| CORS `*` + credentials | 任意站点可带凭证打 API | `s2admin.cors.allowed-origins` 默认本机 Vite 地址 |
| 内网 Maven 源不可解析新依赖 | Flyway/EasyExcel/MinIO 无法下载 | 用 SchemaMigrator + 自研 xlsx zip + FileStorage 策略接口，不引入新包 |
| dataScope 只存不生效 | 无部门树 | 部门 ancestors + DataScopeService 过滤用户列表 |

---

## 参考资源

### 官方文档
- [Svelte5 官方文档](https://svelte.dev/)
- [SvelteKit 官方文档](https://kit.svelte.dev/)
- [shadcn-svelte 官方文档](https://www.shadcn-svelte.com/)
- [TailwindCSS 官方文档](https://tailwindcss.com/)
- [SpringBoot 官方文档](https://spring.io/projects/spring-boot)
- [Spring Security 官方文档](https://spring.io/projects/spring-security)
- [JDK25 官方文档](https://openjdk.org/projects/jdk/25/)

### 学习资源
- [Svelte 入门教程](https://svelte.dev/tutorial)
- [SvelteKit 教程](https://kit.svelte.dev/docs)
- [shadcn-svelte 组件示例](https://www.shadcn-svelte.com/docs)
- [Spring Security 指南](https://spring.io/guides/topicals/spring-security/)
- [JWT 最佳实践](https://jwt.io/)

### 工具推荐
| 用途 | 工具 | 说明 |
|------|------|------|
| API 测试 | Postman / Insomnia | 接口调试 |
| 数据库管理 | DBeaver / DataGrip | 数据库客户端 |
| Redis 管理 | RedisInsight | Redis 可视化 |
| Git 管理 | GitKraken / SourceTree | Git 客户端 |
| API 文档 | Swagger / Knife4j | 接口文档生成 |

---

## 可视化发现

### shadcn-svelte 组件示例

**按钮组件**
```svelte
<script>
  import { Button } from "$lib/components/ui/button";
</script>

<Button variant="default">默认按钮</Button>
<Button variant="destructive">危险按钮</Button>
<Button variant="outline">描边按钮</Button>
<Button variant="ghost">幽灵按钮</Button>
```

**对话框组件**
```svelte
<script>
  import { Dialog, DialogTrigger, DialogContent } from "$lib/components/ui/dialog";
</script>

<Dialog>
  <DialogTrigger><Button>打开对话框</Button></DialogTrigger>
  <DialogContent>
    <h2>对话框标题</h2>
    <p>对话框内容</p>
  </DialogContent>
</Dialog>
```

### Svelte5 组件示例

**Runes 语法**
```svelte
<script>
  let count = $state(0);
  let doubled = $derived(count * 2);

  function increment() {
    count++;
  }
</script>

<button onclick={increment}>
  点击次数: {count}, 两倍: {doubled}
</button>
```

### SpringBoot 控制器示例

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginForm form) {
        return Result.success(authService.login(form));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @GetMapping("/info")
    public Result<UserInfoVO> getUserInfo() {
        return Result.success(authService.getUserInfo());
    }
}
```

---

## 更新记录

| 时间 | 更新内容 | 操作人 |
|------|----------|--------|
| 2026-04-24 | 初始版本，包含技术栈研究和架构决策 | AI Assistant |
| 2026-08-25 | 记录软删唯一键、超管保护、文件鉴权、CORS 白名单等修复决策 | AI Assistant |
