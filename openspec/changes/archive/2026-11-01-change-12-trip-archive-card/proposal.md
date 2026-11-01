## Why

抽屉把城市、行程、游记全部展开，和归档详情页重复；未归组为空时仍显示“暂无未归组数据 / ¥0.00”，看起来像有数据。用户希望抽屉只展示“归档”概要与入口，点击后再看全部内容。

## What Changes

- 抽屉改为：国家状态与汇总 + 每个归档一张可点卡片（名称、日期、行程数、游记数、花费）+ 未归组（仅在有数据时显示）。
- 移除抽屉内城市、行程、游记的展开列表。
- 用户界面统一使用“归档”称呼原来的“旅行”。
- 纯展示调整，接口与数据结构不变。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

（无，`trip` 数据与接口不变）

## Impact

- 前端：`MapView.vue` 抽屉、`TripDetailView.vue` 文案、`ExpensesView.vue` 筛选与表单文案、`frontend/README.md`。
- 测试：地图 E2E 改为点击归档卡片进入详情；断言空未归组不渲染。
