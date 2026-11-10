## Context

见 proposal.md - Why。现状：

- `MapView.vue` 抽屉中 `hasUngrouped` 为真时渲染一个 `drawer-section`，标题为“未归组”，统计行固定输出四项。
- 时间线行程卡在 `tripId` 为空时渲染标签“未归组”。
- 后端 `CountryDrawerResponse.ungrouped` 字段与 `hasUngrouped` 判断保持不变；内部领域术语仍是 ungrouped。
- 当前只有一条未归组行程“一个人的东京”，所以抽屉出现“行程 1 · 游记 0 · 消费 0 · 花费 ¥0.00”。

## Goals / Non-Goals

**Goals:**

- 用用户能理解的说法替换内部术语。
- 去掉值为 0 的统计项，让这一块更短、更聚焦。
- 时间线与抽屉使用同一套用户文案。

**Non-Goals:**

- 不改变 `trip_id` 为空的分组规则、后端 DTO 或接口。
- 不增加未归组记录的查看入口或展开列表。
- 不清理用户已有的未归组数据。

## Decisions

### 1. 统一用户文案为“未加入旅行”

抽屉标题与时间线标签都改为“未加入旅行”。内部字段 `ungrouped`、规格中的领域术语、API 文档保持原样，避免无意义的后端与契约改动。

### 2. 抽屉增加一行解释

标题下方使用现有 `.meta` 样式显示：

> 这些记录还没有归入任何一次旅行

解释只在 `hasUngrouped` 为真时出现，不增加帮助按钮或悬浮提示。

### 3. 统计行只保留非零项

新增 computed `ungroupedStats`：

```ts
const ungroupedStats = computed(() => {
  const u = drawer.value?.ungrouped
  if (!u) return []
  const items: string[] = []
  if (u.itineraries.length) items.push(`行程 ${u.itineraries.length}`)
  if (u.notes.length) items.push(`游记 ${u.notes.length}`)
  if (u.expenses.length) items.push(`消费 ${u.expenses.length}`)
  if (Number(u.expenseCny) > 0) items.push(`花费 ¥${u.expenseCny}`)
  return items
})
```

模板使用 `{{ ungroupedStats.join(' · ') }}`，天然只在项之间加分隔符。

### 4. 国家状态标签与图例统一

`drawerStatusLabel` 与兜底列表使用的状态文案改为：

| 状态 | 旧文案 | 新文案 |
|---|---|---|
| `GRAY` | 未点亮 | 无记录 |
| `YELLOW` | 计划中 | 仅有行程 |
| `GREEN` | 已完成 | 有游记 |

这样“计划中/已完成”只用于旅行状态，不再和国家状态混用。

### 5. 旅行分组标题与手动状态提示

- “计划中” → “计划中的旅行”
- “已完成” → “已完成的旅行”
- 在两个分组上方显示一次“旅行状态由你手动标记”，仅在存在旅行时显示。
- 空状态文案同步改为“暂无计划中的旅行 / 暂无已完成的旅行”。

### 6. 未加入旅行合并到顶部汇总

移除抽屉底部的独立块与 `hasUngrouped` 模板判断，改用 computed `ungroupedNote`：

```ts
const ungroupedNote = computed(() => {
  const u = drawer.value?.ungrouped
  if (!u) return ''
  const items: string[] = []
  if (u.itineraries.length) items.push(`${u.itineraries.length} 条行程`)
  if (u.notes.length) items.push(`${u.notes.length} 篇游记`)
  if (u.expenses.length) items.push(`${u.expenses.length} 笔消费`)
  return items.length ? `另有 ${items.join('、')}未加入旅行` : ''
})
```

提示放在抽屉顶部汇总（行程/游记/花费）下方，使用 `.meta` 样式；金额不单独出现在提示中，避免和顶部花费汇总重复。

### 7. 同步未归档规格的措辞

`openspec/changes/itinerary-timeline/specs/itinerary/management/spec.md` 中“标记为未归组”改为“标记为未加入旅行”，保持待归档规格与 UI 一致。该变更尚未归档，不涉及主规格重写。

## Risks / Trade-offs

- **“未加入旅行”仍可能被理解为操作按钮** → 解释小字明确说明是记录状态；不提供点击入口。
- **规格领域术语与 UI 文案不一致** → 仅在用户可见文案层面改名，领域模型与 API 保持不变，并同步未归档规格中的标签措辞。
- **只显示非零项导致用户不知道全部维度** → 这一块的目的是提示存在未加入旅行的记录，不是完整统计；需要完整明细时去对应业务页面。

## Migration Plan

1. 前端文案与展示改动，随版本发布。
2. 无数据库迁移、无后端接口变更。
3. 回滚：还原“未归组”与固定四项统计即可。

## Open Questions

（无）