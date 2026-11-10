## ADDED Requirements

### Requirement: Admin note content access
管理员 MUST 能查看任意游记的完整内容，包括私密、已分享、已下架与用户软删除的游记。详情 MUST 返回完整正文 HTML、图片与视频的短时签名 URL（600 秒）、作者摘要、国家/城市、可见性、分享状态、下架标记、下架原因与下架时间。游记不存在时 MUST 返回 `2004`。该接口 MUST 仅允许 `ADMIN` 角色访问，普通用户 MUST 返回 `4002`；查看操作 MUST NOT 修改数据或写入审计日志。

#### Scenario: Admin views a private note
- **WHEN** 管理员查看一篇从未分享的私密游记
- **THEN** 返回完整正文与媒体签名 URL

#### Scenario: Admin views a taken-down note
- **WHEN** 管理员查看已下架游记
- **THEN** 返回正文、`takedown=true`、下架原因与下架时间

#### Scenario: Admin views a soft-deleted note
- **WHEN** 管理员查看作者已删除但账号仍存在的游记
- **THEN** 返回游记内容与 `deleted=true`

#### Scenario: Normal user is denied
- **WHEN** 普通用户调用管理员游记详情接口
- **THEN** 系统返回 `4002`

#### Scenario: Unknown note
- **WHEN** 管理员查看不存在的游记 ID
- **THEN** 系统返回 `2004`

#### Scenario: Viewing is read-only
- **WHEN** 管理员查看游记内容
- **THEN** 游记数据、分享令牌与审计日志均不发生变化