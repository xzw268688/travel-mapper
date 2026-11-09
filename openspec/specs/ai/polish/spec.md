# Ai Polish Specification

## Purpose

定义游记文本的 AI 流式润色协议与取消/退回规则，使前端获得稳定的打字机体验，并保证主动取消、断网与部分输出三类场景的额度处理一致。

## Requirements

### Requirement: Polish request validation
润色请求 MUST 包含 `requestId`、1–5000 字的 `text`；`style` MUST 为 `vivid`/`concise`/`elegant`（默认 `vivid`）；`target` MUST 为 `selected`/`full`（默认 `selected`）；可选的 `noteId` MUST 校验属于当前用户。

#### Scenario: Empty text is rejected
- **WHEN** 用户提交空文本
- **THEN** 系统返回 `2001`，不扣额度

#### Scenario: Other user's noteId is rejected
- **WHEN** 请求携带他人的 `noteId`
- **THEN** 系统返回 `2004` 或 `2003`，不进入润色

#### Scenario: Style defaults to vivid
- **WHEN** 请求未提供 `style`
- **THEN** 系统按 `vivid` 处理

### Requirement: Quota deduction for polish
润色 MUST 在建立流式连接前完成额度检查与扣减，并与行程生成共享免费额度；额度不足 MUST 返回 `2011`，MUST NOT 调用 LLM。

#### Scenario: Exhausted quota blocks polish
- **WHEN** 用户当日额度已用完
- **THEN** 接口返回 `2011`，不建立 SSE 流

#### Scenario: VIP does not consume free quota
- **WHEN** VIP 用户在有效期内润色
- **THEN** 免费计数与次数包余额不变

### Requirement: SSE event protocol
成功响应 MUST 为 `text/event-stream`，并按顺序发送可选 `meta`、若干 `delta`、以及 `done`/`cancelled`/`error` 之一；`meta` MUST 包含 `requestId` 与 `quotaSource`；`delta` MUST 包含文本增量；`done` MUST 包含 `totalChars`；`error` MUST 包含错误码与用户可读消息。

#### Scenario: Normal stream completes
- **WHEN** 润色正常结束
- **THEN** 前端依次收到 `meta`、多个 `delta` 和 `done`，按序拼接得到完整文本

#### Scenario: Error terminates stream
- **WHEN** LLM 在输出前报错
- **THEN** 发送 `error` 事件并结束流，按规则退回额度

#### Scenario: Proxy does not buffer
- **WHEN** 请求经过 Nginx
- **THEN** SSE 路径关闭缓冲，首个字符 P95 小于 1.5 秒

### Requirement: Cancellation endpoint
`POST /api/ai/cancel` MUST 幂等：重复取消同一 `requestId` 返回成功；只对当前用户且已登记的 `requestId` 写取消标志；不存在或非本人的 `requestId` MUST 仍返回成功但不写标志；取消标志 TTL 为 5 分钟。

#### Scenario: Repeated cancel succeeds
- **WHEN** 用户两次取消同一 `requestId`
- **THEN** 两次均返回成功，取消标志只写一次

#### Scenario: Cannot cancel another user's request
- **WHEN** 用户尝试取消他人的 `requestId`
- **THEN** 接口返回成功，但不写取消标志，其他用户的流不受影响

#### Scenario: Stream stops on cancel
- **WHEN** 服务端在每个 chunk 前检测到取消标志
- **THEN** 停止生成并发送 `cancelled` 事件

### Requirement: Refund boundaries
用户主动取消 MUST NOT 退回额度；未输出任何有效内容即断网 MUST 退回额度；已输出部分流式内容后断开 MUST NOT 退回额度；异常且未输出任何内容 MUST 退回额度；同一 `requestId` 的退回 MUST 幂等。

#### Scenario: Active cancel keeps charge
- **WHEN** 用户点击“取消生成”并调用 `/api/ai/cancel`
- **THEN** 流停止并发送 `cancelled`，本次额度不退回

#### Scenario: Disconnect before output refunds
- **WHEN** 连接在收到任何 `delta` 前断开且未调用取消接口
- **THEN** 本次扣减被退回

#### Scenario: Disconnect after partial output keeps charge
- **WHEN** 已经输出至少一个 `delta` 后连接断开
- **THEN** 本次额度不退回

#### Scenario: Repeated refund is idempotent
- **WHEN** 同一 `requestId` 的退回逻辑被触发两次
- **THEN** 第二次不产生额外退回

### Requirement: Frontend streaming behavior
前端 MUST 使用 `fetch` + `ReadableStream` 而非 `EventSource`；收到 `delta` MUST 逐字拼接渲染；点击“取消生成” MUST 先调用 `/api/ai/cancel` 再 abort fetch；仅关闭页面/断网 MUST NOT 调用取消接口。

#### Scenario: Typewriter rendering
- **WHEN** 前端收到多个 `delta`
- **THEN** 文本按顺序逐块追加，呈现打字机效果

#### Scenario: Cancel sequence
- **WHEN** 用户点击“取消生成”
- **THEN** 前端先发送取消请求，再中断 fetch 连接

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
