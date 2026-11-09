## ADDED Requirements

### Requirement: Editor toolbar guidance
游记编辑器工具栏的每个操作按钮 MUST 提供悬停提示，说明其用途；有条件限制的操作 MUST 在提示中说明条件（例如「AI 润色」需先选中 1–5000 字文本，「撤销/重做」在无历史时不可用）。提示 MUST NOT 依赖点击后才展示，且 MUST NOT 遮挡正文编辑区域。

#### Scenario: Hovering a format button explains its purpose
- **WHEN** 用户将鼠标悬停在「加粗」或其他格式按钮上
- **THEN** 显示该按钮用途的提示

#### Scenario: Polish tooltip states the selection requirement
- **WHEN** 用户将鼠标悬停在「AI 润色」上
- **THEN** 提示说明需要先选中文本及字数范围

#### Scenario: Tooltips do not block editing
- **WHEN** 提示展示时
- **THEN** 正文编辑区域仍可正常点击与输入