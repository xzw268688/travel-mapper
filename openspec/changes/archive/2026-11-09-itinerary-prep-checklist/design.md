## Context

见 proposal.md - Why。现状：

- `trip_checklist` 按 `(user_id, itinerary_id, item_key)` 保存，`item_key` 为 VARCHAR(16)，仅支持 7 个固定枚举。
- `ChecklistService.get` 返回固定七项；`upsert` 只接受枚举键。
- 前端规划页顶部使用按国家的 localStorage 静态 10 项，与后端完全脱节；旧的固定清单 UI 已移除。
- `PlanView` 新建行程在保存前没有 itinerary ID；`ItineraryEditView` 有 ID。
- 当前迁移到 V15，新迁移使用 V16。

## Goals / Non-Goals

**Goals:**

- 每条行程一份清单：默认 10 项 + 可增删的自定义项。
- 新建规划先本地维护，保存行程时批量入库；已保存行程编辑页实时读写。
- 旧 7 项勾选状态保留。
- 保持不消耗 AI 额度、不影响地图状态。

**Non-Goals:**

- 不支持多国共用一套清单。
- 不允许删除或隐藏默认 10 项。
- 不改变行程保存/生成/额度逻辑。
- 不把清单放进游记或旅行层级。

## Decisions

### 1. 扩展 `trip_checklist`，继续按行程存储

`V16__itinerary_prep_checklist.sql`：

```sql
ALTER TABLE trip_checklist
  MODIFY item_key VARCHAR(64) NOT NULL,
  ADD COLUMN title VARCHAR(64) NULL,
  ADD COLUMN description VARCHAR(255) NULL,
  ADD COLUMN days_before INT NULL,
  ADD COLUMN sort_order INT NOT NULL DEFAULT 0,
  ADD COLUMN custom TINYINT(1) NOT NULL DEFAULT 0;
```

回填旧 7 项：

| key | title | days | sort |
|---|---|---|---|
| VISA | 签证 | 60 | 10 |
| FLIGHT | 机票 | 45 | 20 |
| HOTEL | 住宿 | 30 | 30 |
| INSURANCE | 保险 | 30 | 40 |
| SIM | 电话卡 / eSIM | 14 | 50 |
| CURRENCY | 换汇与银行卡 | 14 | 60 |
| PLUG | 转换头 + 插排 | 7 | 70 |

旧勾选状态保留；`APPS`、`PACKING`、`CUSTOMS` 不落库，读取时作为默认未勾选项返回。

备选：新建独立表。需要迁移旧数据且多一套 CRUD，收益不大；扩展现有表更直接。

### 2. 默认模板与自定义项

后端新增 `PrepChecklistTemplate`（纯代码常量）定义 10 项：key、title、description、daysBefore、sortOrder。自定义项 key 为 `CUSTOM_<uuid>`，长度不超过 64，`custom=1`，排序值从 100 开始递增。

默认项不允许删除；自定义项删除使用软删除（表已有 `deleted`）。

### 3. 接口设计

- `GET /api/prep/checklist/template`：返回默认 10 项，`checked=false`，供新建规划使用。
- `GET /api/prep/checklist?itineraryId=`：返回默认 10 项（合并已保存勾选）+ 自定义项（按 `sort_order, id`）。
- `PUT /api/prep/checklist`：沿用现有 toggle 语义（`itineraryId`、`itemKey`、`checked`）；默认 key 自动带上模板标题/天数，自定义 key 必须是该行程已存在的自定义项。
- `POST /api/prep/checklist/items`：新增自定义项，body `{itineraryId, title, description?, daysBefore}`，返回完整 Item。
- `DELETE /api/prep/checklist/items/{itemId}`：删除自定义项；默认项返回 `2001`/`2022`。
- `PUT /api/prep/checklist/batch`：body `{itineraryId, items:[...]}`，用于新建行程保存后的批量写入；在同一事务内更新默认项勾选、创建/更新自定义项、软删除未提交的自定义项；默认项始终保留。

保留旧 GET/PUT 兼容；新增接口只供新前端使用。

### 4. DTO 结构

`ChecklistDtos.Item` 扩展为：

```json
{
  "itemKey": "VISA",
  "title": "签证",
  "description": "确认签证类型、材料和办理时长…",
  "daysBefore": 60,
  "checked": false,
  "custom": false,
  "sortOrder": 10
}
```

同时保留 `label`（=title）兼容旧客户端。

### 5. 前端受控组件

`PrepChecklist.vue` 改为受控组件：

- `modelValue: PrepItem[]`，通过 `update:modelValue` 更新。
- 展示默认项与自定义项；默认项不可删除，自定义项显示删除按钮。
- 底部“+ 添加一项”打开内联表单：标题（必填）、提前天数（0–365）、说明（可选）。
- 折叠状态与完成进度保留；不再读写 localStorage。

### 6. PlanView（新建规划）

- 页面加载时请求 `GET /api/prep/checklist/template`，得到 10 项未勾选模板；失败时使用前端内置兜底模板。
- 只有在生成/创建草稿后，在“行程草稿”卡片内部、逐日安排之前渲染 `PrepChecklist`，绑定 `prepItems`；用户先确认行前准备，再调整每天行程。
- 点“保存行程”成功后，调用 `PUT /api/prep/checklist/batch` 写入清单；清单写入失败不影响已保存的行程，但提示用户。
- 开始新的规划（重新生成/切换模式导致草稿重置）时，清单重置为模板。

### 7. ItineraryEditView（已保存行程）

- 进入页面时按 `itineraryId` 请求 `GET /api/prep/checklist`。
- 渲染同一个 `PrepChecklist`，勾选/添加/删除后调用对应接口；也可以统一使用 batch 保存，失败时重新加载。
- 清单放在编辑页顶部，便于出发前检查。

### 8. 测试策略

- 后端集成测试：
  - 模板返回 10 项；
  - 首次读取默认未勾选；勾选后恢复；
  - 添加/删除自定义项；默认项不可删除；
  - 批量保存创建/更新/删除自定义项；
  - 旧 7 项数据升级后保持勾选；
  - 跨用户隔离。
- 前端：构建 + Playwright 验证新建规划保存后清单仍在、编辑页可勾选/加项/删项。

## Risks / Trade-offs

- **批量和单条接口并存** → 旧 GET/PUT 保留兼容，新前端以 batch 为主，规则集中在 Service。
- **自定义 key 长度** → V16 将 `item_key` 放宽到 64，`CUSTOM_<uuid>` 可容纳。
- **批量保存删除未提交自定义项** → 前端必须每次提交完整清单；设计已明确，测试覆盖。
- **新建规划未保存离开丢清单** → 用户已确认；保存时提示写入结果。
- **默认模板前端兜底与后端重复** → 正常路径以后端模板为准，前端兜底仅用于网络失败，允许短暂不一致。
- **旧数据迁移** → 只新增列/回填，不删除旧行；新增三项合成返回。

## Migration Plan

1. 发布 V16：扩展表结构、回填旧 7 项标题/天数/排序。
2. 后端上线模板/读取/自定义项/批量接口。
3. 前端切换规划页与行程编辑页到新接口，移除国家 localStorage。
4. 回滚：旧 GET/PUT 仍可用；新列可保留，不影响旧逻辑。

## Open Questions

（无）