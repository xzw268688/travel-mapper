# 文档与代码对照（学习指南）

四份 V1.0 文档是需求与设计基线，代码在实现时做了少量取舍。本文件用于回答两个问题：

1. 读到文档某一段时，去代码哪里找？
2. 哪些地方文档与代码不完全一致？

> 当前最贴近实现的需求契约是 `openspec/specs/`（归档时同步的主规格），
> 其次是归档的 change 记录 `openspec/changes/archive/`，再次是四份原始文档。

## 建议阅读顺序

1. **SRS**：第 2 章（范围/非目标）、第 5 章（功能需求）、第 6 章（BR）、第 9 章（AC）。
2. **DB**：第 1 章（约定）、第 2 章（ER）、第 3 章（表与删除策略）、第 4 章（DDL）、第 7 章（核心查询）。
3. **API**：第 1–2 章（通用约定/错误码），再按模块读第 3–12 章；第 13 章 SSE 单独看。
4. **Design**：第 2 章（架构分层）、第 4 章（模块划分）、第 7 章（关键流程）。
5. 最后跑一遍应用，按下面的端到端链路对照代码。

## 端到端链路 → 文档与代码位置

| 链路 | SRS | DB | API | 后端代码 | 前端代码 |
|---|---|---|---|---|---|
| 注册 / 验邮 / 登录 | 5、AC | `user`、`email_token` | 第 3 章 | `controller/auth`、`service/auth` | `views/RegisterView.vue`、`VerifyEmailView.vue`、`LoginView.vue` |
| 地图点亮 / 国家抽屉 | REQ-MAP-001 | `country`、`city`、`trip`、`itinerary`、`travel_note` | 第 5 章 | `service/map`、`service/catalog` | `views/MapView.vue` |
| AI 规划 / 保存 / 编辑 | REQ-AI-001/002/003 | `itinerary`、`itinerary_day`、`itinerary_spot`、`trip` | 第 6 章 | `service/itinerary`、`service/llm`、`service/search` | `views/PlanView.vue`、`ItineraryEditView.vue` |
| 行前准备 / 官方链接 | 功能清单 | `trip_checklist`、`country_official_link` | 第 7 章 | `service/prep` | PlanView 下半部分 |
| 游记 / 分享 / 海报 | REQ-MKT-001 | `travel_note`、`note_share` | 第 8–9 章 | `service/note`、`service/storage` | `NotesView.vue`、`NoteDetailView.vue`、`ShareView.vue`、`PosterView.vue` |
| 记账 / 汇率 | 功能清单、BR | `expense`、`exchange_rate` | 第 10 章 | `service/expense`、`ExpenseMapper` | `ExpensesView.vue`、`AdminRatesView.vue` |
| 卡密 / 额度 | 功能清单 | `card_key`、`quota_pack`、`quota_ledger` | 第 11 章 | `service/card`、`service/quota` | `ProfileView.vue` |
| 后台管理 | 功能清单 | 各业务表 + `audit_log` | 第 12 章 | `controller/admin` | `views/Admin*.vue` |

## 关键代码入口

- 表结构与种子：`backend/src/main/resources/db/migration/`
- 统一响应/异常/安全：`backend/src/main/java/com/travelmapper/common/`
- 业务服务：`backend/src/main/java/com/travelmapper/service/`
- 接口路由：`backend/src/main/java/com/travelmapper/controller/`
- 前端路由：`frontend/src/router/index.ts`
- 前端 API 客户端：`frontend/src/api/`
- 浏览器 E2E：`frontend/e2e/`
- 性能基准：`scripts/perf/`、`docs/ops/performance.md`

## 已知文档/代码差异

| 主题 | 文档写法 | 当前实现 | 说明 |
|---|---|---|---|
| 分享页路径 | `/s/{token}`，返回 HTML 或由前端路由接管 | 后端 `/s/{token}` 返回 JSON；前端页面 `/share/{token}`；二维码指向 `/share` | 功能正常，路径契约不一致 |
| 游记关联行程 | API 有 `itineraryId` | `travel_note` 无 `itinerary_id` 列，接口固定返回 `null`；实际通过 `trip_id` 归组 | 需决定“加字段”或“删字段” |
| 注销后登录 | 期望原凭据返回 `1007` | DB 匿名化用户名/邮箱，原凭据无法命中 | 契约与匿名化设计冲突 |
| LLM 接入 | Design 列 LangChain4j 为实现之一 | 使用 OpenAI 兼容 HTTP 适配器，保留 `LlmProvider` 接口 | 架构保留可替换性 |
| 城市数据 | 中国约 300+ 地级市 | 已预置 123 个常用地级市，其余后续补 | 用户已确认分批补 |
| 游记默认归组 | 默认最近一次旅行 | 后端 `null=未归组`，前端未预选最近旅行 | 小差异 |
| 移动端手势 | iOS/Android/微信真机验收 | 已实现 ECharts roam + `touch-action`，未真机验收 | change-03 `6.5` 未完成 |
| 真实外部服务 | SMTP / LLM / 搜索 | 本地用 Fake Provider / 未配置真实 Key | 需生产凭据联调 |

## 如何验证你读到的是对的

```bash
# 后端全量测试
cd backend && mvn test ...

# 前端构建
cd frontend && npm run build

# 浏览器 E2E（需后端已启动）
cd frontend && npm run test:e2e

# OpenSpec 主规格
openspec validate --specs --strict
```

遇到文档与代码冲突时，先看 `openspec/specs/` 与归档 change 的任务备注；
如果仍冲突，以“当前测试覆盖的行为”为准，并在本文件补充记录。
