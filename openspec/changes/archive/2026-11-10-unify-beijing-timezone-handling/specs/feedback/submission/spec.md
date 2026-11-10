## MODIFIED Requirements

### Requirement: Daily submission limit
同一用户 MUST 在每个北京时间自然日（Asia/Shanghai）内最多成功提交 5 条反馈。超过上限的提交 MUST 被拒绝且不落库，返回 `5004`（HTTP 429）。

#### Scenario: Fifth submission succeeds
- **WHEN** 用户当天已提交 4 条反馈并提交第 5 条
- **THEN** 第 5 条保存成功

#### Scenario: Sixth submission is rate limited
- **WHEN** 用户当天已成功提交 5 条反馈并再次提交
- **THEN** 系统返回 `5004`，反馈数量保持 5 条

#### Scenario: Limit resets next day
- **WHEN** 进入下一个自然日后用户再次提交
- **THEN** 该次提交不受前一日计数影响

#### Scenario: Limit resets at Beijing midnight
- **WHEN** 北京时间跨过零点后用户再次提交，而服务器 JVM 默认时区为 UTC
- **THEN** 该次提交不受前一日计数影响，立即按新的一天计数