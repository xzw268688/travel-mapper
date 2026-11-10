## Context

见 `proposal.md` 的 Why。已核实的现状：

- `App.vue` 顶栏在 `route.meta.bare` 为真时整体隐藏；子页面目前没有任何返回入口。
- `RichTextEditor.vue` 润色完成时直接用 `insertContentAt` 替换选区（`polishRange`），并通过 `polishPreview` 展示流式输出面板；面板仅在已有输出/状态时出现。
- 编辑器工具栏按钮均为纯文本 `el-button`，没有 tooltip。
- `ExpensesView.vue` 的筛选行末尾并列「查询」「重置」「记一笔」三个按钮；卡片标题只有「记账」。

## Goals / Non-Goals

**Goals**
- 让返回、润色确认、主要操作三处行为可预测且不依赖用户猜测。
- 不改变任何后端接口、额度与数据模型。

**Non-Goals**
- 不重做导航结构或面包屑。
- 不改变润色的计费、取消、重试语义。
- 不调整记账筛选字段本身。

## Decisions

### D1：返回按钮放在顶栏，按路由白名单隐藏
- 在 `App.vue` 顶栏品牌左侧渲染 `← 返回`，仅当 `route.meta.hideBack !== true` 且非 bare 时显示。
- 在路由表为首页 `/`、登录/注册/找回密码、`/share/:token` 等公开页标记 `meta.hideBack = true`（新增 meta，不影响现有 `bare`）。
- 点击逻辑：若 `window.history.state?.back` 存在则 `router.back()`，否则 `router.push('/')`。Vue Router 4 会在 history state 写入 `back`，比 `history.length` 更可靠。
- 隐藏 `bare` 页面（登录、分享、海报等全屏页）保持现状；海报页自带返回，不重复加。

### D2：润色结果「先预览、后采用」
- 流式过程中仍写入 `polishPreview`，但完成后不再调用替换逻辑；保留 `polishRange` 供「采用」使用。
- 结果区使用独立容器（浅色背景 + 左侧色条 + 「AI 润色结果」标题），与正文视觉区分；提供「采用」「放弃」按钮。
- 采用：`editor.chain().focus().insertContentAt(polishRange, polishPreview).run()`，随后清空结果区；撤销可恢复。
- 放弃/关闭：清空 `polishPreview`、`polishRange`、状态，不修改正文。
- 生成期间编辑器只读的现有行为保留，确保选区与 `polishRange` 稳定。
- 失败或取消时结果区不显示「采用」，与既有「部分失败不替换」规则一致。

### D3：工具栏 tooltip
- 统一用 `el-tooltip` 包裹按钮（`placement="top"`、`show-after=200`），内容说明用途与条件。
- 对 `AI 润色`、`撤销/重做` 等有条件的按钮，提示中写明条件（需选中 1–5000 字；无历史时不可用）。
- 不改按钮文字，避免布局变化。

### D4：记账页主操作上移
- 卡片 `#header` 改为左右布局：左侧「记账」标题，右侧「记一笔」主按钮。
- 筛选行移除「记一笔」，保留「查询」「重置」。
- 移动端标题栏允许换行，按钮保持可点。

## Risks / Trade-offs

- [采用时选区已失效] → 生成期间编辑器只读，选区不会变化；采用前若用户点击别处导致选区丢失，`polishRange` 仍为生成开始时的位置，按该范围替换，结果可预测且可撤销。
- [返回按钮与浏览器返回重复] → 按钮只是浏览器返回的显式入口，行为一致，不引入额外状态。
- [tooltip 在触屏设备不可见] → 移动端保持按钮文字自解释；提示是增强而非唯一说明。

## Migration Plan

纯前端发布，无数据迁移。回滚只需回退前端版本。

## Open Questions

无。