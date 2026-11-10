# feedback/submission Specification

## Purpose
为登录用户提供产品内反馈提交与本人处理进度追踪，同时约束内容质量、提交频率和隐私边界，使反馈成为可被管理员处理、对用户可追踪的闭环。

## Requirements

### Requirement: Submit feedback
已登录用户 MUST 能提交一条反馈，包含必填的分类与正文、必填标题和可选联系方式。分类 MUST 为 `FEATURE`（功能建议）、`BUG`（问题反馈）、`UX`（体验问题）、`OTHER`（其他）之一；标题 MUST 为 1–50 字；正文 MUST 为 10–1000 字；联系方式 MAY 为空且 MUST ≤100 字。提交时系统 MUST 自动记录当前页面路径、浏览器 UA 与前端版本号。标题与正文 MUST 在入库前经过敏感词过滤，命中词替换为 `***`。新反馈状态 MUST 为 `NEW`。

#### Scenario: Valid feedback is stored
- **WHEN** 登录用户提交合法分类、标题和正文
- **THEN** 系统保存反馈并返回 `id`、`status=NEW` 与 `createdAt`

#### Scenario: Optional contact and context are captured
- **WHEN** 用户未填写联系方式但提供了页面路径
- **THEN** 反馈保存成功，联系方式为空，页面路径、UA 与版本号被记录

#### Scenario: Invalid fields are rejected
- **WHEN** 分类不在枚举内、标题为空或超过 50 字、正文少于 10 字或超过 1000 字、联系方式超过 100 字
- **THEN** 系统返回 `2001`，不保存任何数据

#### Scenario: Sensitive words are filtered
- **WHEN** 标题或正文包含配置的敏感词
- **THEN** 入库内容中的敏感词被替换为 `***`

#### Scenario: Unauthenticated submission is blocked
- **WHEN** 未携带有效 JWT 的请求调用提交接口
- **THEN** 系统返回 `1006`，不保存任何数据

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

### Requirement: Own feedback history
用户 MUST 能分页查看本人提交的反馈，按创建时间倒序返回。列表项 MUST 包含分类、标题、正文、联系方式、状态、创建时间与更新时间；MUST NOT 包含管理员内部备注或处理人信息。查询 MUST 严格限定为当前登录用户的数据。

#### Scenario: User sees only own feedback
- **WHEN** 用户 A 请求本人反馈历史，而库中存在用户 B 的反馈
- **THEN** 只返回用户 A 的反馈

#### Scenario: Status is visible to the owner
- **WHEN** 管理员将某条反馈从 `NEW` 改为 `RESOLVED` 后作者查看历史
- **THEN** 该作者看到该条反馈状态为 `RESOLVED`

#### Scenario: Internal note is hidden
- **WHEN** 管理员为反馈填写内部备注后作者查看历史或详情
- **THEN** 响应中不包含内部备注字段

#### Scenario: Pagination is applied
- **WHEN** 用户有超过一页的反馈并以 `page=2&size=10` 查询
- **THEN** 返回第二页数据，`total` 为本人反馈总数

### Requirement: Feedback is immutable for users
用户 MUST NOT 编辑、撤回或删除已提交的反馈；系统 MUST NOT 提供对应的用户接口。状态变化 MUST 仅在下一次查询时体现，不另行推送通知。

#### Scenario: No edit endpoint exists
- **WHEN** 用户尝试调用编辑或删除反馈的接口
- **THEN** 系统不提供该能力，请求按不存在的路由处理

#### Scenario: Status changes are pull-based
- **WHEN** 管理员更新状态而用户未刷新历史
- **THEN** 用户界面不会收到主动通知，刷新后才显示新状态
