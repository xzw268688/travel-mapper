## MODIFIED Requirements

### Requirement: User detail

管理员 MUST 能查看指定用户详情：基础资料、角色与状态、二次验证是否开启、当日免费额度用量、次数包余额、VIP 到期时间、游记/行程/旅行数量，以及最近的卡密兑换记录。已注销用户 MUST 只返回账号壳，不返回内容统计。详情接口 MUST 仅管理员可访问。

#### Scenario: Admin opens a user detail
- **WHEN** 管理员在用户列表点击某个用户
- **THEN** 展示额度、VIP、内容数量、二次验证状态与兑换记录

#### Scenario: Deleted user has no content stats
- **WHEN** 管理员查看已注销用户
- **THEN** 只返回匿名化资料，不返回游记/行程统计

#### Scenario: Normal user cannot read admin detail
- **WHEN** 普通用户调用用户详情接口
- **THEN** 系统返回 `4002`

#### Scenario: Admin sees two-factor status
- **WHEN** 管理员查看已开启或未开启二次验证的用户
- **THEN** 详情中的二次验证状态与实际绑定状态一致

## ADDED Requirements

### Requirement: Reset user two-factor authentication

管理员 MUST 能重置指定用户的二次验证：清除 TOTP 密钥、关闭二次验证、删除该用户全部恢复码，并使该用户全部已签发 JWT 立即失效。重置 MUST 写审计日志（动作 `USER_2FA_RESET`）并向用户邮箱发送通知。目标用户不存在或已注销 MUST 返回 `2001`；非管理员访问 MUST 返回 `4002`。重置未开启二次验证的用户 MUST 返回成功且不产生错误。

#### Scenario: Admin resets a user's two-factor binding
- **WHEN** 管理员对已开启 2FA 的用户执行重置
- **THEN** 该用户 2FA 关闭、恢复码清空、旧 JWT 返回 `1006`，审计记录重置动作

#### Scenario: Reset sends a notice
- **WHEN** 管理员重置用户的二次验证
- **THEN** 用户邮箱收到二次验证已被重置的通知

#### Scenario: Reset of an unbound user is a no-op success
- **WHEN** 管理员重置一个未开启 2FA 的用户
- **THEN** 接口返回成功，不改变账号状态

#### Scenario: Normal user cannot reset two-factor
- **WHEN** 普通用户调用重置接口
- **THEN** 系统返回 `4002`