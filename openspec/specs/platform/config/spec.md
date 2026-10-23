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
