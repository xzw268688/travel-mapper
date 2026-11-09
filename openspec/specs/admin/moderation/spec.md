# Admin Moderation Specification

## Purpose

为管理员提供游记内容下架与恢复能力，确保下架立即对外失效、本人仍可查看，恢复后不复活旧分享链接，并与用户软删除保持独立。

## Requirements

### Requirement: Content takedown
管理员 MUST 能下架指定游记，记录 `takedown=1`、原因、时间与操作人；下架 MUST 立即撤销该游记全部分享令牌；MUST 写审计日志；不存在的游记 MUST 返回 `2004`。

#### Scenario: Takedown revokes shares
- **WHEN** 管理员下架一篇已分享游记
- **THEN** `takedown=true`，所有分享令牌撤销，返回撤销数量

#### Scenario: Takedown is audited
- **WHEN** 管理员执行下架
- **THEN** `audit_log` 记录操作人、游记、原因和时间

#### Scenario: Unknown note
- **WHEN** 管理员下架不存在的游记
- **THEN** 系统返回 `2004`

### Requirement: Owner visibility after takedown
下架后本人 MUST 仍能查看游记详情并看到“已下架”标记；本人 MUST NOT 能编辑或创建分享；其他人 MUST NOT 能通过分享页访问。

#### Scenario: Owner can still view
- **WHEN** 作者查看被下架游记
- **THEN** 详情返回成功且 `takedown=true`

#### Scenario: Owner cannot edit
- **WHEN** 作者编辑被下架游记
- **THEN** 系统返回 `2024`

#### Scenario: Public access is blocked
- **WHEN** 访客访问被下架游记的分享链接
- **THEN** 分享页返回 `2007`

### Requirement: Content restore
管理员 MUST 能恢复已下架游记，将 `takedown` 改回 0；恢复后本人 MUST 可再次编辑，但原分享令牌 MUST 保持失效，用户 MUST 重新创建分享后才能对外访问。

#### Scenario: Restore re-enables editing
- **WHEN** 管理员恢复游记后作者编辑内容
- **THEN** 编辑成功

#### Scenario: Old links stay dead
- **WHEN** 管理员恢复游记后访问旧分享链接
- **THEN** 仍返回 `2007`

#### Scenario: New share works after restore
- **WHEN** 作者在恢复后重新创建分享
- **THEN** 新链接可正常访问

### Requirement: Takedown and deletion are independent
`takedown` 与用户软删除 `deleted` MUST 是两个独立字段；下架 MUST NOT 设置 `deleted`，删除 MUST NOT 依赖 `takedown`；两者任一为真都 MUST 使分享页失效。

#### Scenario: Takedown does not delete
- **WHEN** 管理员下架游记
- **THEN** 游记仍存在于作者列表，`deleted` 保持原值

#### Scenario: Deleted note remains deleted
- **WHEN** 作者删除一篇曾被下架的游记
- **THEN** `deleted=1`，管理员恢复操作不能使已删除游记重新对外可见

### Requirement: Admin-only moderation
内容下架与恢复接口 MUST 仅允许 `ADMIN` 角色访问；普通用户 MUST 返回 `4002`。

#### Scenario: Normal user cannot moderate
- **WHEN** 普通用户调用下架或恢复接口
- **THEN** 系统返回 `4002`

### Requirement: Moderation list
管理员 MUST 能分页查询全部用户的游记元信息：标题、作者、国家与城市、可见性、下架状态、创建时间；MUST 支持按标题关键字、作者关键字、下架状态与创建时间筛选。列表 MUST NOT 返回正文内容。管理员 MUST 能在列表中直接下架或恢复游记，下架 MUST 填写原因，行为沿用现有下架/恢复规则并写审计日志。

#### Scenario: Admin browses all notes
- **WHEN** 管理员打开内容管理
- **THEN** 以创建时间倒序看到全部用户的游记元信息，不含正文

#### Scenario: Filter by keyword and status
- **WHEN** 管理员按标题关键字与「已下架」筛选
- **THEN** 只返回匹配的已下架游记

#### Scenario: Takedown from the list
- **WHEN** 管理员在列表中对某篇游记填写原因并点击下架
- **THEN** 该游记立即下架、分享令牌撤销、审计记录生成，列表状态刷新

#### Scenario: Restore from the list
- **WHEN** 管理员对已下架游记点击恢复
- **THEN** 游记恢复可见，旧分享链接不复活

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
