# Platform Config Specification

## Purpose

提供 LLM、对象存储与搜索服务的外部配置中心，使管理员无需重启应用即可切换供应商与密钥，并保证敏感配置加密存储、页面掩码展示。

## Requirements

### Requirement: Centralized configuration storage
系统 MUST 将所有 LLM、搜索和存储配置存放在 `sys_config` 键值表中，键名使用 `llm.*`、`search.*`、`storage.*` 前缀；敏感值 MUST 使用 AES 加密，密钥 MUST 来自环境变量。

#### Scenario: Sensitive value is encrypted at rest
- **WHEN** 管理员保存 LLM API Key
- **THEN** 数据库中的 `config_value` 为密文，`encrypted=1`，明文不出现在数据库、日志或接口响应中

#### Scenario: Non-sensitive value stays readable
- **WHEN** 管理员保存模型名称或搜索供应商名称
- **THEN** 该值以非加密方式存储，读取时直接返回

### Requirement: Masked and non-destructive updates
配置读取接口 MUST 对敏感项返回掩码值；更新请求中敏感字段留空时 MUST 保持原值不变。

#### Scenario: Reading config masks secrets
- **WHEN** 管理员读取 LLM/OSS/搜索配置
- **THEN** API Key、Secret 等字段仅显示掩码，完整值不可获取

#### Scenario: Blank update preserves secret
- **WHEN** 管理员更新配置时把敏感字段留空
- **THEN** 数据库中的原密文保持不变，其他字段正常更新

### Requirement: Hot reload without restart
配置修改成功后，系统 MUST 在无需重启的情况下让下一次使用配置的请求采用新值；配置重建失败的更新 MUST NOT 生效，且旧配置继续可用。

#### Scenario: Switching LLM provider takes effect immediately
- **WHEN** 管理员把 LLM 供应商从通义千问切换为 Kimi 并保存
- **THEN** 下一次 AI 请求使用 Kimi，无需重启应用

#### Scenario: Invalid config keeps previous value
- **WHEN** 管理员提交无法解析或无法初始化供应商的配置
- **THEN** 接口返回校验错误，数据库保留旧配置，下一次请求仍使用旧配置

### Requirement: Admin-only configuration endpoints
配置读写接口 MUST 仅允许 `ADMIN` 角色访问；普通用户 Token 访问 MUST 返回 403。

#### Scenario: Normal user is forbidden
- **WHEN** 普通用户携带有效 JWT 调用任意配置接口
- **THEN** 系统返回 403，且不读取或修改配置

#### Scenario: Admin can read and update all three config groups
- **WHEN** 管理员分别读取和更新 LLM、OSS、搜索配置
- **THEN** 三组配置均可成功保存并在读取时返回掩码后的最新值

### Requirement: Configuration change audit
每次配置变更 MUST 写入 `audit_log`，记录操作人、配置键和脱敏后的变更详情；日志 MUST NOT 包含明文密钥。

#### Scenario: Config update is auditable
- **WHEN** 管理员成功修改任一配置项
- **THEN** `audit_log` 新增一条 `CONFIG` 记录，包含操作人、配置键和脱敏详情

### Requirement: Mail SMTP configuration
系统 MUST 将邮件配置存放在 `sys_config` 的 `mail.*` 键下：`mail.enabled`、`mail.host`、`mail.port`、`mail.username`、`mail.password`、`mail.from`、`mail.smtp-auth`、`mail.smtp-starttls`。`mail.password` MUST 使用 AES 加密存储，读取接口 MUST 只返回掩码；更新时密码留空 MUST 保留原值。管理员 MUST 能通过 `GET/PUT /admin/config/mail` 读写该组配置，普通用户访问 MUST 返回 403。

#### Scenario: Admin reads mail config
- **WHEN** 管理员读取邮件配置
- **THEN** 返回启用状态、主机、端口、用户名、发件人、认证/STARTTLS 开关和掩码后的密码

#### Scenario: Blank password preserves secret
- **WHEN** 管理员更新邮件配置但密码留空
- **THEN** 原密码保持不变，其他字段正常更新

#### Scenario: Normal user cannot change mail config
- **WHEN** 普通用户调用邮件配置接口
- **THEN** 系统返回 403，不读取或修改配置

### Requirement: Mail sender hot reload
邮件配置保存成功后，系统 MUST 在下一次发送邮件时使用新配置，无需重启应用。`mail.enabled=true` 且主机/发件人有效时 MUST 使用 SMTP 发送；未启用或未配置完整时 MUST 回退日志发送器。配置重建失败时 MUST 保留旧发送器并返回可读错误，不得导致应用启动失败。开发模式下邮件未启用时 MUST 继续在日志打印验证码；DB 配置启用 SMTP 后 MUST NOT 再打印验证码。

#### Scenario: SMTP change applies without restart
- **WHEN** 管理员保存新的 SMTP 主机、端口与账号
- **THEN** 下一次发送验证码邮件使用新 SMTP，无需重启

#### Scenario: Disabled mail falls back to logging
- **WHEN** `mail.enabled=false` 或主机为空
- **THEN** 邮件发送走日志发送器，开发环境仍打印验证码

#### Scenario: Enabled SMTP stops code logging
- **WHEN** 管理员启用 SMTP 并保存完整配置
- **THEN** 发送验证码时仅调用 SMTP，不再在日志打印验证码

#### Scenario: Failed rebuild keeps previous sender
- **WHEN** 新邮件配置无法构建发送器
- **THEN** 接口返回错误，旧发送器继续工作，应用不重启

### Requirement: Daily free quota configuration
后台 MUST 提供每日免费 AI 额度配置：`GET /admin/config/quota` 读取，`PUT /admin/config/quota` 更新；`freeLimit` MUST 为 0–100 的整数，超出范围 MUST 返回 `2001`。该额度 MUST 同时约束行程生成与 AI 润色，0 表示不提供免费次数。保存后 MUST 立即生效（热加载），无需重启，并 MUST 记录配置变更审计。

#### Scenario: Admin reads current free limit
- **WHEN** 管理员打开 AI 模型配置页
- **THEN** 「每日免费额度」显示当前值

#### Scenario: Admin updates free limit
- **WHEN** 管理员修改额度并保存
- **THEN** 配置写入并立即用于后续生成与润色

#### Scenario: Out-of-range value is rejected
- **WHEN** 管理员提交小于 0 或大于 100 的值
- **THEN** 系统返回 `2001`，原配置不变

#### Scenario: Zero disables free quota
- **WHEN** 管理员将额度设为 0
- **THEN** 用户没有免费次数，需使用次数包
