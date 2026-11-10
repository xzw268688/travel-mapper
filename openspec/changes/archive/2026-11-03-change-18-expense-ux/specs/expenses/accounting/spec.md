## ADDED Requirements

### Requirement: Date-grouped expense listing
系统 MUST 提供按日期分组的消费查询，支持与国家、城市、旅行、未归组、分类和日期区间相同的筛选条件。结果 MUST 只包含本人未删除消费，按消费日期倒序分组，组内按创建时间/ID 倒序；每组 MUST 包含日期、当日 CNY 合计、当日原币种合计和消费明细。未配置汇率的记录 MUST NOT 计入 CNY 合计，但 MUST 仍出现在明细中并标记 `rateMissing=true`。每日合计 MUST 不受分页影响。

#### Scenario: Expenses are grouped by day
- **WHEN** 用户查看按日期分组的消费
- **THEN** 同一天的消费聚合到同一组，日期从新到旧排列

#### Scenario: Daily totals include all matching rows
- **WHEN** 某天有多笔消费超过普通列表一页的量
- **THEN** 当日合计仍包含该天全部匹配消费，不受分页限制

#### Scenario: Missing rate does not break grouping
- **WHEN** 某天包含未配置汇率的消费
- **THEN** 该笔仍在组内展示且 `rateMissing=true`，但不计入当日 CNY 合计

#### Scenario: Original currencies are summarized
- **WHEN** 某天包含 JPY 与 CNY 消费
- **THEN** 该组返回按币种汇总的原币金额，同时给出折算后的 CNY 合计

#### Scenario: Filters apply to grouping
- **WHEN** 用户按国家、分类或日期区间筛选后切换按日期视图
- **THEN** 分组结果与明细列表使用相同的筛选条件

### Requirement: Expense quick entry and amount expression
新增消费 MUST 记住本机上次使用的国家、币种与分类，并在下一次新增时自动带出；编辑既有消费 MUST 使用该消费自身的值。金额输入 MUST 支持 `+ - * /` 与括号的安全表达式计算，提交前 MUST 换算为最多两位小数的金额字符串；表达式非法、除零或结果溢出 MUST 阻止保存并提示，MUST NOT 调用后端。负数汇总（退款）规则保持不变。

#### Scenario: Last used fields are remembered
- **WHEN** 用户上次以 JPY / 餐饮 / 日本保存消费，再次点击「记一笔」
- **THEN** 表单默认带出日本、JPY、餐饮

#### Scenario: Expression is evaluated
- **WHEN** 用户输入 `12+3.5*2`
- **THEN** 前端显示并提交 `19.00`

#### Scenario: Invalid expression is blocked
- **WHEN** 用户输入 `12++` 或 `1/0`
- **THEN** 前端提示表达式无效，不发起保存请求

#### Scenario: Edit keeps row values
- **WHEN** 用户编辑一条既有消费
- **THEN** 表单使用该记录的金额、币种、国家与分类，不被本机偏好覆盖

### Requirement: Monthly total and category chart
记账页 MUST 提供月份选择与「本月合计」卡片，展示所选月份在当前筛选条件下的 CNY 合计与原币种合计；默认当前月。汇总区 MUST 在现有分组柱状图之外提供分类占比图，分类金额 MUST 来自同一批汇总数据，按 CNY 聚合。

#### Scenario: Monthly total is shown
- **WHEN** 用户选择 2026-11 月
- **THEN** 卡片显示该月 CNY 合计与原币种合计

#### Scenario: Month defaults to current
- **WHEN** 用户打开记账页
- **THEN** 月份默认当前月，并展示本月合计

#### Scenario: Category chart matches summary
- **WHEN** 汇总数据包含交通、餐饮等分类
- **THEN** 饼图展示各分类占 CNY 总额的比例，合计与汇总总额一致

#### Scenario: Empty month degrades
- **WHEN** 所选月份没有消费
- **THEN** 合计显示 0，分类图显示空状态，不报错