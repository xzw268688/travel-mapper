## ADDED Requirements

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