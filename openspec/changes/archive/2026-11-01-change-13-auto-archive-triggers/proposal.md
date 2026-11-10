## Why

归档目前只能由保存行程创建。不做计划的用户写游记、记账时永远匹配不到归档，数据一直留在“未归组”，与“归档自动生成”的方向矛盾。

## What Changes

- 游记、消费在无匹配归档时 MUST 直接新建归档，初始日期区间为记录当天。
- 归档匹配窗口统一为**前后各 30 天**（对称），避免“先记账后补行程”把一次旅行拆成两个归档。
- 游记使用创建时间、消费使用消费日期作为归档日期。
- 新数据不再产生“未归组”；历史未归组由重算接口补齐。
- 接口字段与表结构不变。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `trips/grouping`: 自动归类改为“匹配不到就新建归档”，匹配窗口改为 ±30 天；重算补齐历史未归组。

## Impact

- 后端：`TripService`（匹配与新建）、`NoteService`、`ExpenseService`、`TripReclusterService`、测试。
- 前端：无需改动（未归组为空时已隐藏）。
- 文档：API、SRS、README。
