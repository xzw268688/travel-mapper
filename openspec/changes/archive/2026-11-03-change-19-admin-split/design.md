## Context

见 `proposal.md`。LLM/搜索/存储已有 `sys_config` + `ConfigReloadService` 热加载模式；邮件目前由 `@ConditionalOnProperty(app.mail.enabled)` 在启动时选择 `LoggingEmailSender` 或 `SmtpEmailSender`，host/port 来自 `spring.mail.*` 环境变量，运行期不可改。管理后台只有 LLM 与搜索两个表单。

## Goals / Non-Goals

**Goals:**

- 管理员在一个配置中心内维护 LLM、搜索、对象存储、SMTP 四组配置。
- SMTP 保存后下一次发信立即生效，失败不破坏旧发送器。
- 管理动作与可替换配置在导航上清晰分离。

**Non-Goals:**

- 不提供邮件模板编辑器、发送测试邮件或在页面查看发送日志。
- 不迁移已有 `spring.mail.*` 环境变量；仅作为未配置 DB 值时的默认回退。
- 不改变验证码生成、限流、校验与登录流程。
- 不新增数据库迁移；邮件配置在首次保存时写入 `sys_config`。

## Decisions

1. **用 `MailSenderHolder` 替代条件 Bean**
   - `MailSenderHolder implements EmailSender, ReloadableProvider`，内部持有当前 `EmailSender`。
   - `reload()` 从 `ConfigService` 读取 `mail.*`；未启用/主机空 → `LoggingEmailSender`；启用且完整 → 构造 `JavaMailSenderImpl` 与 `SmtpEmailSender`。
   - 构建失败抛出异常，`ConfigReloadService` 回滚配置；旧 holder 引用保持不变。
   - 删除 `EmailConfig` 的条件 Bean，避免启动期与运行期两套选择逻辑。

2. **邮件配置回退到环境变量**
   - `AppProperties.Mail` 增加 host/port/username/password/smtpAuth/starttls/from；`application.yml` 的 `app.mail.*` 从现有环境变量读取。
   - `ConfigService` 中缺失的 `mail.*` 使用回退值；管理员首次保存后 DB 值优先。
   - 这样已有部署无需迁移即可继续使用环境变量。

3. **开发日志判定改为运行期发送器状态**
   - `EmailCodeService` 不再读取 `properties.getMail().isEnabled()`；改为 `mailSenderHolder.isSmtpEnabled()`。
   - SMTP 未启用时打印 `[auth-dev]` 验证码；启用后停止打印，避免泄露。

4. **配置中心使用页签与 query 定位**
   - `/admin/settings` 读取 `?tab=llm|search|storage|mail`，默认 `llm`；页签切换用 `router.replace` 更新 query。
   - 顶栏「配置」菜单直接链接到对应 tab；「管理」菜单保留原有页面。
   - 备选：为每组配置新建独立路由/页面。页面重复且导航更长，不采用。

5. **SMTP 校验在保存时完成**
   - 启用时要求 host、port（1–65535）、from 合法；用户名/密码可选（某些内网 SMTP 免认证）。
   - 不主动建立连接；保存只验证配置可构建，避免后台等待网络超时。
   - 密码使用 `ConfigEntry.secret`，GET 返回掩码。

## Risks / Trade-offs

- **无法在保存时发现 SMTP 账号错误** → 首次发信失败会记录日志并让验证码接口返回发送失败；后续可通过修正配置立即生效。不引入测试邮件以减少范围。
- **DB 配置覆盖环境变量** → 首次保存后以 DB 为准；管理员可清空/停用恢复日志发送器。
- **配置热加载影响所有 provider** → 保存邮件配置会触发 LLM/搜索/存储一起 reload；现有机制已保证失败回滚，保持一致性。
- **页签 query 被手动修改** → 非法值回退 `llm`，不报错。

## Migration Plan

无数据库迁移。先发布后端（新增接口与 holder），再发布前端；已有环境变量继续作为默认值。回滚后端后邮件回到启动期条件 Bean，但 DB 中的 `mail.*` 行保留，不影响运行。

## Open Questions

无。