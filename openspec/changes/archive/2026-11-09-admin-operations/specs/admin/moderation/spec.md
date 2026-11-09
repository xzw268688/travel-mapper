## ADDED Requirements

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