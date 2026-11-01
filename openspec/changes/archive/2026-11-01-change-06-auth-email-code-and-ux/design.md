## Context

见 `proposal.md` 的 Why。现状约束：

- 后端已有 `EmailTokenService`（数据库 `email_token` 表、一次性长令牌）与 `POST /api/auth/resend-verify`。
- Redis 已用于限流与缓存，`RedisRateLimiter`、`UserStatusCache` 已有可复用的模式。
- 前端认证页分散实现，未复用布局；错误统一走 `ElMessage`。
- 当前项目未正式上线，可以接受破坏性接口调整（已确认）。

## Goals / Non-Goals

**Goals**

- 用验证码替换长令牌，覆盖注册验证与找回密码。
- 明确管理员登录入口与会话保持方式，且不改变服务端 JWT 契约。
- 让 E2E 能在没有 SMTP 的情况下验证完整注册链路。

**Non-Goals**

- 不引入短信、第三方登录、图形验证码。
- 不新增或修改数据库表结构。
- 不做管理员独立会话或独立认证服务。
- 不清理历史未验证账号。

## Decisions

### 1. 验证码存 Redis，不落库

键：`auth:code:{scene}:{email}` → hash 字段 `{digest, attempts}`，TTL 600 秒；`scene` 取 `VERIFY` / `RESET`。

- **为什么不用 `email_token` 表**：验证码是短生命周期、高频覆盖的对象，Redis 天然支持 TTL 与原子 `INCR`；不落库也避免为 6 位码设计表结构。
- **为什么只存摘要**：即使 Redis 被读取，也不直接暴露可用验证码。摘要使用 `HMAC-SHA256(email|scene|code, appSecret)`。6 位码空间小，离线暴力破解无法靠哈希强度阻止，真正的防线是 10 分钟 TTL、5 次试错与发送限流——设计上明确这一点，避免误以为哈希本身足够。
- 同一邮箱同一场景再次发送时直接覆盖旧键，天然满足“只有最新验证码有效”。

### 2. 试错与作废在同一个 Lua 脚本里完成

校验流程用 Lua 原子执行：读取摘要 → `attempts+1` → 比较 → 命中则删除键，未命中且 `attempts>=5` 也删除键。避免并发请求把 5 次上限刷成更多次。

### 3. 发信限流分三层

| 层级 | 键 | 上限 | 超限返回 |
|---|---|---|---|
| 冷发间隔 | `auth:code:gap:{email}` TTL 60s | 60 秒 | `1005` |
| 邮箱日限额 | `auth:code:daily:{email}:{yyyyMMdd}` TTL 至次日 | 10 次 | `1005` |
| IP 小时限额 | `auth:code:ip:{ip}:{yyyyMMddHH}` TTL 1h | 20 次 | `5004` |

日期按 Asia/Shanghai 计算，与额度系统保持一致。IP 取值沿用现有请求身份/限流组件的方式，不新写一套。

### 4. 发送接口不泄露账号是否存在

`POST /api/auth/send-code {email, scene}` 永远返回 200 与 `emailSent=true`（除非命中限流）。

- `VERIFY`：未注册邮箱**需要**收到验证码（注册前校验），因此允许发码；已注册且已验证的邮箱不发码。
- `RESET`：仅对存在且未注销的账号发码。

已注册与否不会体现在响应里，避免被用来探测账号。代价是任意邮箱都可能收到验证邮件，依靠 60 秒 / 每日 10 次 / IP 每小时 20 次限流缓解。

### 5. 接口收敛

```
POST /api/auth/send-code        { email, scene }
POST /api/auth/register         { username, email, password, agreeTerms, code }
POST /api/auth/reset-password   { email, code, newPassword }
```

- 删除 `POST /api/auth/resend-verify`、`POST /api/auth/forgot-password` 与 `POST /api/auth/verify-email`。
- 注册内校验验证码：先查重、再校验码，通过后创建 **已激活** 账号；响应仅 `{ userId }`。
- 验证码错误/过期返回 `1004`；重复用户名/邮箱返回 `1008` 且不消耗验证码。
- 错误码沿用：验证码错误/过期用 `1004`（文案改为“验证码无效或已过期”）、发送过频 `1005`、IP 限流 `5004`。

### 6. 开发环境验证码写日志，不写响应

`MAIL_ENABLED=false` 时，验证码以固定格式打印，例如：

```
[auth-dev] scene=VERIFY email=user@example.com code=123456 expiresIn=600s
```

生产配置下该分支不执行；单元测试断言 `MAIL_ENABLED=true` 时日志中不含 `code=`。前端只提示“开发环境请查看后端服务日志”。

### 7. 管理员登录复用同一会话，前端做角色校验

`/admin/login` 调用同一个登录接口；成功后若 `role !== ADMIN`，调用 `auth.logout()` 并显示内联错误，不写入登录态。管理员入口不出现在普通页面，只靠地址访问。

### 8. 会话保持只改客户端存储

`auth` store 同时读 `localStorage.tm_token` 与 `sessionStorage.tm_token`；登录时按勾选写入其一，并清理另一处；退出时两处都清。JWT 仍是 7 天，服务端零改动。

### 9. E2E 从后端日志取验证码

Playwright 通过 `E2E_BACKEND_LOG` 指向后端日志文件，正则读取最新一条 `[auth-dev] ... email=<目标邮箱> code=(\d{6})`。未设置该变量时跳过注册验证用例并打印原因。不新增任何取码接口。

### 10. 单页多步骤取代两次跳转

- `/register`：用户名 → 邮箱 → 验证码（就地发送 + 60 秒倒计时）→ 密码 → 确认密码 → 协议 → 注册成功后自动登录并进入地图。
- `/reset-password`：输入邮箱 → 发送验证码 → 输入验证码 + 新密码 + 确认 → 提交。
- 删除 `/verify-email` 页面与路由；`/forgot-password` 仅做重定向，保留旧链接可用。

## Risks / Trade-offs

- **6 位码可被离线暴力破解** → 依靠 10 分钟 TTL、5 次在线试错与多层发送限流；不把哈希当作安全边界。
- **无 SMTP 时开发者看不到验证码** → 固定格式日志 + 文档说明；E2E 依赖日志路径。
- **注册前发码可被用于向任意邮箱发信** → 三层限流兜底；如需更强防护，后续可加同 IP 每日总发信上限。
- **破坏性接口变更** → 项目未上线，前后端同时发布；旧邮件中的令牌将失效，需在发布说明中写明。
- **`email_token` 表变为未使用** → 本次保留表结构与历史数据，不写迁移；后续可单独做清理 change。
- **IP 限流在反向代理后可能取到代理 IP** → 沿用现有 `RequestIdentityFilter` 的取值约定，部署时按 Nginx 配置传递真实 IP。
- **未验证账号堆积** → 保持现状，记录为后续独立任务。

## Migration Plan

1. 后端与前端同一版本发布，避免新前端调用已删除接口。
2. 发布前清空 `auth:code:*` 与旧 `email_token` 中未使用记录（可选，不影响功能）。
3. 回滚：回退到上一版本镜像即可；Redis 验证码键会自然过期，无需数据回滚。
4. 发布公告中说明“旧邮件中的验证/重置令牌不再可用”。

## Open Questions

无。未验证账号清理与 `email_token` 表下线作为后续独立 change 处理。