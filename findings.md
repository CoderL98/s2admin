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
| JDK | 21 | Java 开发环境，包含虚拟线程等新特性 |
| SpringBoot | 3.x | 应用框架，简化 Spring 配置 |
| Spring Security | 6.x | 安全框架，提供认证授权能力 |
| Spring Data JPA | 3.x | 数据访问层，简化数据库操作 |
| MySQL | 8.x | 关系型数据库 |
| Redis | 7.x | 缓存数据库，用于 Session 和热点数据缓存 |
| JJWT | 0.12.x | JWT Token 生成和验证库 |

---

## 研究发现

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

### SpringBoot 3.x 与 JDK21

**JDK21 新特性**
| 特性 | 说明 |
|------|------|
| 虚拟线程 (Virtual Threads) | 轻量级线程，大幅提升并发能力 |
| Pattern Matching | 增强的模式匹配 |
| Record Patterns | 记录类型模式匹配 |
| String Templates | 字符串模板 |

**SpringBoot 3.x 特性**
- 原生支持 JDK21
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
| 暂无 | - | - |

---

## 参考资源

### 官方文档
- [Svelte5 官方文档](https://svelte.dev/)
- [SvelteKit 官方文档](https://kit.svelte.dev/)
- [shadcn-svelte 官方文档](https://www.shadcn-svelte.com/)
- [TailwindCSS 官方文档](https://tailwindcss.com/)
- [SpringBoot 官方文档](https://spring.io/projects/spring-boot)
- [Spring Security 官方文档](https://spring.io/projects/spring-security)
- [JDK21 官方文档](https://openjdk.org/projects/jdk/21/)

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
