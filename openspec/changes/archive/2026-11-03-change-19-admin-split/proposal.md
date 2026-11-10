Triage: ready-for-agent

## Why

后台导航把用户、卡密、城市、内容等“日常管理”和 LLM、搜索、对象存储等“可替换配置”混在一个下拉菜单里，且 SMTP 仍只能通过环境变量修改、改完必须重启；对象存储虽然后端已有配置接口，但后台没有入口。管理员需要清晰区分管理与配置，并能在页面内完成邮件参数调整。

## What Changes

- 顶栏后台入口拆为两个菜单：
  - **管理**：用户、卡密、城市、官方链接、汇率、内容管理。
  - **配置**：AI 与搜索、邮件 SMTP、对象存储。
- `/admin/settings` 改为配置中心页签：LLM、搜索、对象存储、邮件 SMTP；通过 `?tab=llm|search|storage|mail` 定位页签，默认 LLM。
- 新增后台对象存储配置表单，复用已有 `GET/PUT /admin/config/oss`。
- 新增邮件 SMTP 配置：
  - 新配置键 `mail.enabled`、`mail.host`、`mail.port`、`mail.username`、`mail.password`、`mail.from`、`mail.smtp-auth`、`mail.smtp-starttls`。
  - 新增 `GET/PUT /admin/config/mail`，密码掩码展示、留空保留。
  - 邮件发送改为可热加载的发送器：配置保存后下一次验证码/重置密码邮件立即使用新 SMTP，无需重启；未启用或未配置时回退日志发送器。
- 开发环境继续以日志输出验证码；DB 配置启用 SMTP 后不再打印验证码。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `platform/config`: 配置中心新增邮件 SMTP 组与热加载要求；后台配置入口覆盖 LLM、搜索、存储、邮件四组。
- `ui/branding`: 后台导航拆分为「管理 / 配置」两个入口，并在同一设计令牌下展示配置页签。

## Impact

- 后端：`ConfigKeys`、`AppProperties.Mail`、`MailSenderHolder`（替代 `EmailConfig` 条件 Bean）、`EmailCodeService`、`AdminConfigController`、新增 `MailConfigDto`；无数据库迁移。
- 前端：`App.vue` 导航、`AdminSettingsView` 页签与存储/邮件表单、`api/admin.ts` 类型。
- 测试：后端邮件配置保存/掩码/热加载；Playwright 后台导航与配置页签。
- 文档：API、SRS、`frontend/README.md`、开发部署说明。