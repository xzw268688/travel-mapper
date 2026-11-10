## Why

行程规划完成后，用户需要在旅途中记录花费、发布图文游记，并在结束后生成可分享的足迹海报。没有游记就不会出现地图绿色状态，没有记账就无法按国家/旅行汇总花费，没有分享页就无法验证“收集与裂变”的产品目标。本 change 补齐行程之后的完整闭环。

## What Changes

- 实现游记：创建/详情/编辑/删除，富文本 XSS 与敏感词过滤，正文 ≤200KB、图片 ≤20 张、单图 ≤5MB、仅 jpg/png/webp，默认私密；必选国家 + 城市，可选关联行程并继承 `tripId`，或手动归组。
- 实现图片上传：`POST /api/upload/presign` 只接受 `NOTE_IMAGE`、白名单类型与 ≤5MB，返回 300 秒预签名 PUT；前端压缩并剥离 EXIF 后直传。
- 实现分享：创建/复用/撤销分享令牌（7 天、30 天、永久），只读分享页按白名单返回标题、200 字摘要、封面、最多 6 张图、国家/城市、日期与脱敏昵称；签名 URL 有效期取 `min(令牌剩余, 3600 秒)`；令牌失效、游记删除、下架、用户注销统一返回 `2007`。
- 实现记账：新增/编辑/删除/列表/汇总；国家必选、城市可选、币种、金额字符串两位小数且允许负数、固定 7 类、日期不晚于今天；关联行程时继承国家/城市/`tripId`；保存时写汇率值快照；支持按国家/城市/旅行/日期多维汇总。
- 实现汇率后台：新增币种时回填该币种历史 NULL 快照并返回影响条数；修改已有币种时不回填；变更写审计日志。
- 实现后台内容下架/恢复：下架立即撤销全部分享令牌，本人可见但不可编辑/分享；恢复后本人可编辑，但原分享链接仍失效，需重新生成。
- 实现足迹海报：前端 `html2canvas` + Canvas 连线图 + 二维码，二维码指向分享页；海报不含消费金额、私密全文和未公开照片，由用户主动触发。
- **不包含**AI 生成、行程编辑与行前准备（change-04）；本 change 消费其 `itineraryId`/`tripId` 契约。

## Capabilities

### New Capabilities

- `notes/authoring`: 游记创建/详情/编辑/删除、富文本安全、图片上传与限制、行程关联与旅行归组。
- `notes/sharing`: 分享令牌创建/复用/撤销、只读分享页白名单、签名 URL、失效与下架语义。
- `expenses/accounting`: 消费 CRUD、多币种、汇率快照、关联行程/旅行、列表与多维汇总。
- `expenses/exchange-rates`: 后台汇率查询与新增/修改、新增币种回填、审计。
- `poster/footprint`: 前端足迹海报生成、数据边界与二维码指向。
- `admin/moderation`: 后台内容下架/恢复、分享令牌撤销与审计。

### Modified Capabilities

无。全部为新增能力。

## Impact

- **新增接口**：`POST /api/upload/presign`、`/api/notes*`、`GET /api/s/{token}`、`/api/expenses*`、`/api/admin/exchange-rates`、`/api/admin/notes/{id}/takedown`、`/api/admin/notes/{id}/restore`（API 8、9、10、12.14–12.16）。
- **数据表**：`travel_note`、`note_share`、`expense`、`exchange_rate`（结构由 change-01 提供）；读取 `itinerary`、`trip`、`city`、`country`。
- **依赖**：change-01 的 `StorageProvider` 与签名 URL；change-02 的鉴权与行级隔离；change-03 的 `trip` 归组与城市校验；change-04 的行程详情与 `tripId`。
- **前端**：TipTap 富文本编辑器、图片压缩/EXIF 剥离、分享页、记账页与图表、海报生成页。
- **文档依据**：SRS REQ-REC-001、REQ-COM-001、REQ-MKT-001、BR-003/005；Design 7.12/7.15/7.16；API 8、9、10、12.14–12.16；DB `travel_note`、`note_share`、`expense`、`exchange_rate`。
- **无破坏性变更**。
