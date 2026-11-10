## MODIFIED Requirements

### Requirement: Moderation list

管理员 MUST 能分页查询全部用户的游记元信息：标题、作者、国家与城市、可见性、下架状态、是否含媒体、媒体审核状态、创建时间；MUST 支持按标题关键字、作者关键字、下架状态、媒体审核状态与创建时间筛选。列表 MUST NOT 返回正文内容。管理员 MUST 能在列表中直接下架或恢复游记，下架 MUST 填写原因，行为沿用现有下架/恢复规则并写审计日志。媒体审核筛选 MUST 支持「待审核」「已审核」与不过滤三种取值，仅含图片或视频的游记参与该筛选。

#### Scenario: Admin browses all notes
- **WHEN** 管理员打开内容管理
- **THEN** 以创建时间倒序看到全部用户的游记元信息，不含正文

#### Scenario: Filter by keyword and status
- **WHEN** 管理员按标题关键字与「已下架」筛选
- **THEN** 只返回匹配的已下架游记

#### Scenario: Filter by media review state
- **WHEN** 管理员选择「待审核」
- **THEN** 只返回含图片或视频且尚未标记审核的游记

#### Scenario: Takedown from the list
- **WHEN** 管理员在列表中对某篇游记填写原因并点击下架
- **THEN** 该游记立即下架、分享令牌撤销、审计记录生成，列表状态刷新

#### Scenario: Restore from the list
- **WHEN** 管理员对已下架游记点击恢复
- **THEN** 游记恢复可见，旧分享链接不复活

## ADDED Requirements

### Requirement: Media review queue

带图片或视频的游记 MUST 在创建后进入媒体待审核状态（`media_reviewed_at` 为空），但 MUST NOT 因此延迟发布或改变可见性。管理员 MUST 能在用户详情中查看全部媒体并标记「已审核」；标记 MUST 记录审核管理员与时间，并写审计日志 `NOTE_MEDIA_REVIEW`。编辑游记导致 `images` 或 `videos` 发生变化时，审核状态 MUST 重置为待审核。不含媒体的游记 MUST NOT 出现在待审核列表。重复标记已审核 MUST 幂等成功。

#### Scenario: New note with media is pending
- **WHEN** 用户创建含图片或视频的游记
- **THEN** 游记按原可见性立即生效，同时出现在待审核媒体列表

#### Scenario: Admin marks media reviewed
- **WHEN** 管理员查看待审核游记并点击「已审核」
- **THEN** 记录审核管理员与时间，待审核列表不再包含该游记，审计新增 `NOTE_MEDIA_REVIEW`

#### Scenario: Editing media resets review
- **WHEN** 作者修改游记的图片或视频列表并保存
- **THEN** 该游记媒体审核状态重置为待审核

#### Scenario: Notes without media are excluded
- **WHEN** 游记没有任何图片或视频
- **THEN** 待审核媒体列表不包含该游记

#### Scenario: Repeated review is idempotent
- **WHEN** 管理员对已审核游记再次点击「已审核」
- **THEN** 接口成功，不产生额外状态变化