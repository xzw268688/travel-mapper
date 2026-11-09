# admin/audit-log Specification

## Purpose
为运营管理员提供审计日志的只读查看能力：按时间、动作、操作人与对象筛选，展示操作详情，便于追查配置变更、封禁、额度调整、内容下架与卡密操作。

## Requirements

### Requirement: Audit log listing
管理员 MUST 能分页查询审计日志，按创建时间倒序；MUST 支持按动作、操作人 ID、对象类型与时间区间筛选。每条记录 MUST 返回时间、操作人、动作、对象类型与 ID、脱敏后的详情与 IP。接口 MUST 仅管理员可访问，且 MUST NOT 提供修改或删除能力。

#### Scenario: Admin lists recent audit entries
- **WHEN** 管理员打开审计日志页
- **THEN** 按时间倒序展示审计记录

#### Scenario: Filter by action and operator
- **WHEN** 管理员选择动作 `CONFIG_UPDATE` 并指定操作人
- **THEN** 只返回该操作人的配置变更记录

#### Scenario: Detail is expandable
- **WHEN** 管理员展开某条记录
- **THEN** 展示脱敏后的 `detail` JSON

#### Scenario: Normal user cannot read audit logs
- **WHEN** 普通用户调用审计日志接口
- **THEN** 系统返回 `4002`

#### Scenario: Audit logs are read-only
- **WHEN** 管理员尝试修改或删除审计记录
- **THEN** 系统不存在对应接口，返回 404 或 405
