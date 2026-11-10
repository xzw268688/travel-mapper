## Purpose

定义 TravelMapper 全系统统一的北京时间口径，约束时间如何存储、传输、展示、按自然日切分以及每日限额何时重置，确保所有业务能力在任意服务器时区配置下表现一致。

## ADDED Requirements

### Requirement: Beijing wall-clock storage and transport
系统 MUST 以北京时间墙钟值存储所有无时区日期时间字段（`DATETIME`），以 `DATE` 存储纯业务日期。API MUST 将日期时间序列化为 `yyyy-MM-dd'T'HH:mm:ss`、将日期序列化为 `yyyy-MM-dd`，且这些值 MUST 按北京时间解释。系统 MUST NOT 因数据库或 JVM 的默认时区不同而改变存储字面值或 API 输出。

#### Scenario: API returns Beijing wall-clock value
- **WHEN** 系统返回一个已记录的日期时间字段
- **THEN** 响应值为 `yyyy-MM-dd'T'HH:mm:ss`，且与数据库中保存的北京时间字面值一致

#### Scenario: Server default timezone does not shift values
- **WHEN** JVM 与数据库会话默认时区均为 UTC
- **THEN** 同一记录的存储字面值与 API 返回值仍为北京时间，不出现 ±8 小时偏移

### Requirement: Single clock source for server computations
系统 MUST 以固定的 Asia/Shanghai 时区计算所有“当前时间”和“今天”，不得依赖 JVM 或数据库的默认时区。同一业务记录中的时间字段 MUST 由同一时钟基准产生，不得混用数据库默认时间戳与应用生成时间。

#### Scenario: Today changes at Beijing midnight
- **WHEN** 北京时间为 00:30，而 JVM 默认时区为 UTC
- **THEN** 系统计算出的“今天”是新的北京日期，而不是 UTC 的前一天

#### Scenario: Record timestamps share one timeline
- **WHEN** 一条业务记录被创建并随后读取
- **THEN** 数据库默认生成与应用显式写入的时间字段都处于同一北京时间基准，不出现相互差 8 小时

### Requirement: Beijing calendar-day boundaries
系统 MUST 以北京时间自然日作为日期过滤、按天分组、旅行归组、状态推导与过期判断的边界。`from` 与 `to` 日期参数 MUST 分别表示北京时间当天的开始与结束（含首尾）。上述判断 MUST NOT 依赖数据库会话时区。

#### Scenario: Date filter uses inclusive Beijing days
- **WHEN** 用户按 `from=2026-11-10&to=2026-11-10` 过滤记录
- **THEN** 结果包含北京时间 2026-11-10 00:00:00 至 23:59:59 的记录，且不包含相邻日期

#### Scenario: Early-morning record belongs to the Beijing date
- **WHEN** 一条记录在北京时间 00:30 创建
- **THEN** 按天分组、旅行归组与时间线排序将其归入当天，而不是前一天

#### Scenario: Expiry uses the Beijing date
- **WHEN** 一段行程在北京时间前一天结束，当前时间为北京时间 00:30
- **THEN** 该行程被视为已结束，而不是仍在进行

### Requirement: Daily quotas reset at Beijing midnight
所有按自然日计算的限额与限流（包括但不限于 AI 免费额度、邮箱验证码、反馈与举报提交上限）MUST 在北京时间零点重置，且在同一服务器上保持相同重置时刻。

#### Scenario: Feedback limit resets at Beijing midnight
- **WHEN** 用户北京时间当天已提交 5 条反馈，时间跨过北京时间零点
- **THEN** 新的一天可以重新提交，且不受前一日计数影响

#### Scenario: UTC server uses Beijing reset time
- **WHEN** 服务端 JVM 默认时区为 UTC，北京时间刚跨过零点
- **THEN** 每日限额立即重置，而不是再等待 8 小时

### Requirement: Beijing display and input semantics
所有界面 MUST 以北京时间展示日期与时间，不得按访问者浏览器时区转换。后台的日期时间输入 MUST 按北京时间解释，并在表单中明确标注该语义。

#### Scenario: Display is independent of browser timezone
- **WHEN** 浏览器所在时区为 UTC 的用户查看一条北京时间 20:00 的记录
- **THEN** 界面显示 20:00，而不是 12:00

#### Scenario: Admin input is interpreted as Beijing time
- **WHEN** 管理员在北京时间语义下输入 `2026-11-10T20:00:00`
- **THEN** 系统保存并返回同一北京时间字面值，不随管理员浏览器时区改变