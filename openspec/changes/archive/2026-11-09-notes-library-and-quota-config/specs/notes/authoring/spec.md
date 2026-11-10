## ADDED Requirements

### Requirement: Note listing and search
本人游记页 MUST 提供按创建时间倒序的分页列表，默认每页 10 条；MUST 支持关键字匹配标题或正文纯文本摘要、按创建日期区间（`from`/`to`，闭区间）筛选。列表项 MUST 返回游记 `id`、标题、摘要、封面签名 URL、国家与城市名称、可见性、下架状态与创建时间。列表 MUST 支持直接打开与删除本人游记。页面 MUST NOT 要求用户输入游记 ID 才能打开游记。

#### Scenario: Recent notes are listed newest first
- **WHEN** 用户打开游记页
- **THEN** 按创建时间倒序展示本人未删除游记，并给出总数

#### Scenario: Keyword matches title or summary
- **WHEN** 用户输入关键字并查询
- **THEN** 返回标题或正文摘要包含该关键字的游记

#### Scenario: Date range filters by creation time
- **WHEN** 用户选择开始与结束日期并查询
- **THEN** 只返回创建时间落在区间内的游记

#### Scenario: Open and delete from the list
- **WHEN** 用户在列表中点击「打开」或「删除」
- **THEN** 分别进入游记详情或确认后删除该游记，并刷新列表

#### Scenario: No ID input is required
- **WHEN** 用户想查看已有游记
- **THEN** 直接从列表选择，页面不出现游记 ID 输入框

#### Scenario: Missing cover degrades
- **WHEN** 封面文件不存在或签名失败
- **THEN** 列表显示「无封面」占位，不展示破图