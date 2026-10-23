# Triage 标签

技能使用五个固定的 triage 角色。本文件把这些角色映射到本仓库实际使用的标记。

OpenSpec change 没有原生标签，因此角色记录在 change 的 `proposal.md` 顶部一行，例如：

    Triage: ready-for-agent

角色变化时替换该行；只有确实同时适用多个角色时才用逗号分隔。这是 triage 标记，
**不是状态**——状态一律用 `openspec status --change "<name>" --json` 查询。

| 技能中的标签 | 本仓库的标记 | 含义 |
| --- | --- | --- |
| `needs-triage` | `needs-triage` | 维护者需要评估 |
| `needs-info` | `needs-info` | 等待报告者补充信息 |
| `ready-for-agent` | `ready-for-agent` | 规格完整，可交给 AFK agent |
| `ready-for-human` | `ready-for-human` | 需要人工实现 |
| `wontfix` | `wontfix` | 不会处理 |

技能提到某个角色时，使用右列对应的标记值。
