# Issue 追踪器：OpenSpec changes

本仓库的工作项（issue）就是 **OpenSpec change**，位于 `openspec/changes/<kebab-name>/`。没有外部 issue 追踪器。

## 生命周期

- **新建 issue**：运行 `/opsx:propose <名称或描述>`。它会创建 change 及其规划产物。change 名（kebab-case）就是 issue 标识，没有编号。
- **修改 issue**：运行 `/opsx:update <name>`，只修订规划产物。
- **关闭 issue**：运行 `/opsx:archive <name>`。change 会移动到 `openspec/changes/archive/YYYY-MM-DD-<name>/`。

## 各文件含义

| 文件 | 含义 |
|---|---|
| `proposal.md` | issue body（做什么、为什么） |
| `specs/<capability>/spec.md` | 行为规格增量 |
| `design.md` | 实现方式（可能不存在） |
| `tasks.md` | 实现清单（`- [ ]` / `- [x]`） |

## 读取一个 issue

1. 读取该 change 目录下的 `proposal.md`、`specs/*/spec.md`、`tasks.md`。
2. 判断状态**只能**用：

       openspec status --change "<name>" --json

3. **不要**读取任何文件里的 `Status:` 行 —— 该行不可信，不用于判断状态。

## 列出未关闭的 issue

    openspec list --json

`openspec/changes/archive/` 下的 change 已关闭；除非用户明确要求，不要当作待办列出。

## 当技能要求"发布到 issue 追踪器"

运行 `/opsx:propose` 新建 change。只创建规划产物，不要改项目代码。

## 当技能要求"获取相关工单"

确定 `<name>` 后，读取 `openspec/changes/<name>/proposal.md`、其 `specs/*/spec.md`、`tasks.md`，并运行 `openspec status --change "<name>" --json`。

## 当技能要求"评论 issue"或"打标签"

用 `/opsx:update <name>` 修订 change 的规划产物。Triage 标记记录在 `proposal.md` 的 `Triage:` 行，见 `docs/agents/triage-labels.md`。
