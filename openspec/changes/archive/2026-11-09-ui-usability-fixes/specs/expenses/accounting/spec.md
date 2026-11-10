## ADDED Requirements

### Requirement: Expense page action separation
记账页 MUST 将「记一笔」作为页面级主操作展示在记账卡片标题区域，MUST NOT 与筛选条件或「查询/重置」并列。筛选区 MUST 只包含筛选条件与「查询」「重置」按钮。两组操作在布局上 MUST 分区，使查询不会误触新增、新增不会误触查询。

#### Scenario: Record button sits with the page title
- **WHEN** 用户打开记账页
- **THEN** 「记一笔」显示在记账卡片标题区域，筛选区只显示筛选条件与「查询」「重置」

#### Scenario: Filters do not trigger creation
- **WHEN** 用户点击「查询」或「重置」
- **THEN** 只执行筛选或重置，不打开新增消费表单

#### Scenario: Primary action opens the form
- **WHEN** 用户点击标题栏的「记一笔」
- **THEN** 打开新增消费表单，筛选条件保持不变