# 领域文档

工程技能在探索代码库时应如何消费本仓库的领域文档。

## 探索前先读

- 仓库根目录的 **`CONTEXT.md`**；或
- 如果根目录存在 **`CONTEXT-MAP.md`**，它指向每个上下文的 `CONTEXT.md`——读取与当前主题相关的那些。
- **`docs/adr/`**——读取涉及你即将改动区域的 ADR。多上下文仓库还要检查 `src/<context>/docs/adr/`。

如果这些文件不存在，**静默继续**。不要提示缺失，也不要主动建议创建。`/domain-modeling` 技能
（经 `/grill-with-docs` 和 `/improve-codebase-architecture` 触达）会在术语或决策真正确定时按需创建。

## 文件结构

单上下文仓库（大多数仓库）：

    /
    ├── CONTEXT.md
    ├── docs/adr/
    │   ├── 0001-event-sourced-orders.md
    │   └── 0002-postgres-for-write-model.md
    └── src/

多上下文仓库（根目录存在 `CONTEXT-MAP.md`）：

    /
    ├── CONTEXT-MAP.md
    ├── docs/adr/                          ← 系统级决策
    └── src/
        ├── ordering/
        │   ├── CONTEXT.md
        │   └── docs/adr/                  ← 上下文级决策
        └── billing/
            ├── CONTEXT.md
            └── docs/adr/

本仓库为**单上下文**。

## 使用术语表中的词汇

输出中命名领域概念时（issue 标题、重构提案、假设、测试名），使用 `CONTEXT.md` 中定义的术语。
不要漂移到术语表明确避免的同义词。

如果你需要的概念还不在术语表中，这是一个信号——要么你在发明项目不使用的语言（重新考虑），
要么确实存在缺口（记录给 `/domain-modeling`）。

## 标记 ADR 冲突

如果你的输出与现有 ADR 矛盾，明确指出来，而不是静默覆盖：

> _与 ADR-0007（event-sourced orders）矛盾——但值得重新讨论，因为……_
