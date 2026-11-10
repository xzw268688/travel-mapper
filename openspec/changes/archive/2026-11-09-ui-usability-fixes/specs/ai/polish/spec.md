## MODIFIED Requirements

### Requirement: In-editor selection polish
游记编辑器 MUST 提供「AI 润色」入口，仅当正文选中非空文本（1–5000 字）时可用。润色 MUST 复用 `POST /api/ai/polish/stream`，`target` 固定为 `selected`，风格 MUST 支持 `vivid`（默认）/`concise`/`elegant`。生成期间编辑器 MUST 只读以保持选区稳定，并 MUST 在独立结果区流式展示输出。润色完成 MUST NOT 自动修改正文；结果区 MUST 与正文在视觉上明确区分，并提供「采用」与「放弃」操作：点击「采用」后才将结果替换所选文本（用户 MUST 可通过撤销恢复原文），点击「放弃」或关闭结果区 MUST 保持正文原样。前端 MUST NOT 再提供独立 `/polish` 页面或其路由/导航入口。

#### Scenario: Selected text is polished in place
- **WHEN** 用户选中一段文字并点击「AI 润色」且生成成功
- **THEN** 润色结果完整呈现于结果区；用户点击「采用」后原位替换所选文本

#### Scenario: Polished result waits for adoption
- **WHEN** 用户选中一段文字并点击「AI 润色」且生成成功
- **THEN** 润色结果显示在独立结果区，正文保持不变，直到用户点击「采用」

#### Scenario: Adopt replaces the selection
- **WHEN** 用户点击结果区的「采用」
- **THEN** 所选文本被润色结果替换，光标留在替换内容之后，用户可通过撤销恢复原文

#### Scenario: Discard keeps the original
- **WHEN** 用户点击「放弃」或关闭结果区
- **THEN** 正文保持原样，结果被丢弃

#### Scenario: No selection does not call AI
- **WHEN** 用户未选中文本时触发润色
- **THEN** 前端提示“请先选中文本”，不发起任何 AI 请求、不扣额度

#### Scenario: Selection exceeds limit
- **WHEN** 选中文本超过 5000 字
- **THEN** 前端提示超限，不发起请求

#### Scenario: Style selection is honored
- **WHEN** 用户选择「简洁」后润色
- **THEN** 请求携带 `style=concise`，流式结果按简洁风格返回

### Requirement: Editor polish cancellation and retry
生成期间 MUST 提供取消入口；取消 MUST 先调用 `POST /api/ai/cancel` 再中断连接，并 MUST NOT 修改正文；取消不退回额度。失败时 MUST 保留原文，显示错误并提供重试；重试 MUST 使用新的 `requestId` 重新发起请求。已输出部分内容后失败 MUST NOT 自动替换正文，且 MUST NOT 提供「采用」操作。

#### Scenario: Cancel keeps original text
- **WHEN** 用户点击取消
- **THEN** 流停止、正文保持原样，提示“已取消（不退回额度）”

#### Scenario: Failure offers retry
- **WHEN** AI 在输出前报错
- **THEN** 正文不变，显示错误与重试入口，服务端按既有规则退回额度

#### Scenario: Partial failure does not replace
- **WHEN** 已收到部分增量后流以错误结束
- **THEN** 正文保持原样，结果区不提供「采用」按钮；用户可重试并产生新的计费请求

#### Scenario: Legacy page is gone
- **WHEN** 用户访问 `/polish`
- **THEN** 路由不存在，按前端兜底规则跳转；导航栏不再显示「AI 润色」入口