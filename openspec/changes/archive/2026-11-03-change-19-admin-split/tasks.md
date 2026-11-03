## 1. 后端邮件配置与热加载

- [x] 1.1 增加 `mail.*` 配置键与 `AppProperties.Mail` 字段，`application.yml` 的 `app.mail.*` 绑定现有环境变量；验证默认回退值
- [x] 1.2 实现 `MailSenderHolder implements EmailSender, ReloadableProvider`，替换 `EmailConfig` 条件 Bean；未启用回退日志发送器，启用且完整时构建 `JavaMailSenderImpl`，构建失败不替换旧实例
- [x] 1.3 `EmailCodeService` 改用 holder 的运行期状态决定是否打印开发验证码；验证启用 SMTP 后不再打印
- [x] 1.4 新增 `MailConfigDto` 与 `GET/PUT /admin/config/mail`，密码掩码/留空保留，复用 `apply()` 热加载与审计；集成测试覆盖读取掩码、保存后 `isSmtpEnabled` 翻转、普通用户 403
- [x] 1.5 后端全量测试通过

## 2. 后台导航与配置页

- [x] 2.1 `App.vue` 把后台入口拆为「管理」与「配置」；配置项链接到 `/admin/settings?tab=llm|search|storage|mail`
- [x] 2.2 `AdminSettingsView` 改为页签：LLM、搜索、对象存储、邮件 SMTP；读取/监听 `?tab`，非法值回退 LLM
- [x] 2.3 增加对象存储表单（endpoint/region/bucket/accessKey/secretKey/pathStyle/publicDomain），复用现有 admin API
- [x] 2.4 增加邮件 SMTP 表单（enabled/host/port/username/password/from/smtpAuth/starttls），保存提示热加载；密码留空保留
- [x] 2.5 前端 `npm run build` 通过

## 3. 验收与文档

- [x] 3.1 Playwright 覆盖：管理员看到「管理/配置」，配置菜单定位到对应页签，普通用户不显示后台入口；配置页四组页签可见
- [x] 3.2 全量 E2E 通过并生成配置页截图
- [x] 3.3 更新 API、SRS、`frontend/README.md`、开发部署说明
- [x] 3.4 `openspec validate change-19-admin-split --strict` 通过并归档