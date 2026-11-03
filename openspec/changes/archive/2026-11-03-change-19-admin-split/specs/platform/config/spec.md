## ADDED Requirements

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