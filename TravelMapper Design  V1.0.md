# 📐 概要设计文档：旅迹地图 V1.0

> 基于《旅迹地图 V1.0 需求规格说明书（补丁版）》编写。  
> 文档范围：概要设计 + 关键详细设计（架构分层、模块划分、核心 ER、P0 接口清单、错误码表、关键流程、部署拓扑、通用设计约定）。  
> 字段与类细节在编码阶段补充；AI 辅助编码需人工审查后合入。  
> 本次修订已对齐需求文档补丁版，重点补齐：AI 取消机制、60 秒去重、preparations 缓存、卡密导出、汇率快照口径。

---

## 目录

1. 技术选型汇总  
2. 系统架构分层  
3. 核心 ER 图（表结构概要）  
4. 模块划分与依赖（含 4.1 存储抽象、4.2 搜索抽象）  
5. P0 接口清单  
6. 错误码表  
7. 关键流程详细设计（7.0～7.22，含 7.10.1、7.21、7.22）  
8. 部署拓扑  
9. 通用设计约定  
10. 后续文档计划  
11. 待补清单  
12. 修订记录  

---

## 1. 技术选型汇总

| 层次 | 选型 | 说明 |
|---|---|---|
| 前端框架 | Vue3 + Vite + TypeScript + Pinia + Vue Router | PC 为 P0，移动端可用级适配 |
| 前端 UI | Element Plus | 组件全、AI 生成代码准；后台管理页直接用表格/表单 |
| 前端地图 | ECharts 世界地图 GeoJSON | 开发阶段使用，上线前确认合规来源 |
| 前端富文本 | TipTap（基于 ProseMirror） | 支持选中文本润色、图文混排；配合后端 XSS 过滤 |
| 前端流式 | fetch + ReadableStream | 用于 AI 润色打字机效果 |
| 海报生成 | html2canvas + 独立 Canvas 绘制连线图 + qrcode 库 | 见 7.16 |
| 后端框架 | SpringBoot 3 + Java 17 | 单体应用，不采用微服务 |
| 数据访问 | MyBatis-Plus + 手写复杂 SQL | 简单 CRUD 用 MP，复杂统计手写 SQL/XML |
| 代码简化 | Lombok | 减少样板代码 |
| LLM 接入 | OpenAI 兼容 HTTP 适配器（保留 `LlmProvider` 接口） | 业务层不直接依赖；LangChain4j 可作为后续实现替换 |
| 数据库 | MySQL 8.0 | 主数据存储 |
| 缓存/锁 | Redis 7 | 额度计数、卡密幂等键、guides 缓存、preparations 缓存、取消标志、分布式锁、用户状态缓存 |
| 对象存储 | 云 OSS（生产）/ MinIO（本地） | 按 S3 兼容协议抽象，见 4.1 |
| 反向代理 | Nginx | 静态文件、API 反代、SSE 免缓冲 |
| 构建部署 | Maven + npm + Docker Compose（可选） | Monorepo，前后端分开构建 |
| 接口文档 | springdoc 自动生成 + `docs/api.md` 轻量契约 | 契约进 Git，Swagger UI 给前端看 |
| 文档管理 | Git + Markdown | 需求与设计文档入仓，变更记录可追溯 |

---

## 2. 系统架构分层

```
┌─────────────────────────────────────────────────────────┐
│                      浏览器（Vue3 SPA）                    │
│  地图页 │ 行程页 │ 游记页 │ 记账页 │ 后台管理 │ 分享页(只读)   │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTPS / SSE
┌──────────────────────────▼──────────────────────────────┐
│                         Nginx                            │
│   静态文件托管 │ /api 反代 │ SSE proxy_buffering off        │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│                   SpringBoot 单体应用                      │
│  Controller → Service → Repository                        │
│  ├─ 认证权限模块（JWT 过滤器、角色校验、行级隔离、封禁/注销拦截）│
│  ├─ 用户与账号模块（注册、邮箱验证、找回密码、注销）             │
│  ├─ 地图模块（三态计算、旅行/城市分组统计）                    │
│  ├─ 城市与旅行模块（city、trip、tripId 归组）                │
│  ├─ 行程模块（AI 生成、微调、手动景点、保存/编辑/删除）         │
│  ├─ 行前准备模块（preparations、清单、官方链接）               │
│  ├─ 搜索增强模块（SearchProvider、guides 缓存、降级）         │
│  ├─ 游记模块（富文本、图片上传、分享令牌）                     │
│  ├─ AI 润色模块（SSE 流式、取消）                           │
│  ├─ 记账模块（多币种、汇率快照、多维度汇总）                   │
│  ├─ 海报模块（前端渲染 + 二维码 + 连线图）                    │
│  ├─ 卡密与额度模块（生成、兑换、幂等、额度扣减、去重/取消）      │
│  ├─ 后台管理模块（配置、用户、内容、汇率、城市、官方链接）        │
│  └─ 通用能力（存储抽象、搜索抽象、审计日志、限流、异常、日志脱敏） │
└──────┬──────────────────┬──────────────────┬─────────────┘
       │                  │                  │
┌──────▼──────┐   ┌───────▼───────┐  ┌───────▼───────┐
│  MySQL 8.0  │   │   Redis 7     │  │  云 OSS/MinIO  │
│  主数据存储  │   │ 额度/锁/缓存/  │  │   图片存储     │
│             │   │ 取消标志/状态  │  │               │
└─────────────┘   └───────────────┘  └───────────────┘
                           │
                  ┌────────▼────────┐
                  │ LLM API / 搜索API│
                  │  动态配置、可切换  │
                  └─────────────────┘
```

> **时间约定**：全系统固定北京时间。数据库存无时区 `DATETIME` 墙钟；后端“现在/今天”统一由固定 `Asia/Shanghai` 的 `TimeProvider` 计算；前端只做格式化，不做时区转换。

---

## 3. 核心 ER 图（表结构概要）

> 字段只列关键项；完整字段、索引、约束在编码阶段补充。所有业务表统一带 `created_at`、`updated_at`、`deleted`（软删除）。

### 3.1 用户与认证

| 表 | 关键字段 | 说明 |
|---|---|---|
| `user` | id, username, email, password_hash, nickname, avatar_preset, role(USER/ADMIN), vip_expire_at, status(NORMAL/BANNED/DELETED), token_version, deleted | 平台角色只有 `USER`/`ADMIN`；**VIP 不是角色**，由 `vip_expire_at > NOW()` 派生（见 9 章约定） |
| `email_token` | id, user_id, email, token, type(VERIFY/RESET), expire_at, used | 邮箱验证与找回密码，30 分钟有效、一次性 |

### 3.2 城市与旅行

| 表 | 关键字段 | 说明 |
|---|---|---|
| `country` | id, code, name_zh, name_en, continent, status(ENABLED/DISABLED), sort_order | **国家字典**；`GET /api/countries` 返回启用国家，接口响应中的 `countryName` 由此表取 |
| `city` | id, country_code, city_code, name, name_en, level(CITY/MAJOR), status(ENABLED/DISABLED), sort_order | 中国地级市 + 国外常用城市；被引用后只停用不物理删除 |
| `trip` | id, user_id, country_code, name, start_date, end_date, deleted | 多次旅行归组；删除行程不删 trip；未归组为业务表 trip_id 为空 |

### 3.3 行程

| 表 | 关键字段 | 说明 |
|---|---|---|
| `itinerary` | id, user_id, trip_id(可空), country_code, title, start_date, end_date, guides_json, preparations_json, status(SAVED), deleted | 行程主表；**草稿不落库，V1 只有 SAVED 一种状态** |
| `itinerary_day` | id, itinerary_id, day_index, visit_date, city_code | 每天一行；`visit_date` 避免 MySQL 关键字；子表物理删除 |
| `itinerary_spot` | id, day_id, sort_order, source(AI/MANUAL), name, description, suggested_duration | 景点拆表；子表物理删除，重新生成时整体替换 |

### 3.4 行前准备

| 表 | 关键字段 | 说明 |
|---|---|---|
| `country_official_link` | id, country_code, title, url, sort_order | 管理员维护的国家-官方链接映射 |
| `trip_checklist` | id, user_id, country_code, itinerary_id, item_key, checked, title, description, days_before, sort_order, custom, deleted | 默认 10 项 + 自定义项；唯一键 `(user_id, itinerary_id, item_key)` |

### 3.5 游记与分享

| 表 | 关键字段 | 说明 |
|---|---|---|
| `travel_note` | id, user_id, trip_id(可空), country_code, city_code, title, content, cover_image, visibility(PRIVATE/SHARED), deleted | 游记默认私密 |
| `note_share` | id, note_id, token, expire_at, revoked | 分享令牌，默认 7 天有效，可撤销；内容下架时批量撤销 |

### 3.6 记账

| 表 | 关键字段 | 说明 |
|---|---|---|
| `expense` | id, user_id, trip_id(可空), country_code, city_code(可空), currency, amount, exchange_rate_snapshot, category, expense_date, remark, deleted | 金额 `DECIMAL(12,2)`；汇率快照**存值不存 ID** |
| `exchange_rate` | id, currency, rate_to_cny, effective_at | 管理员维护的固定汇率表；变更留痕 |

### 3.7 商业化与配置

| 表 | 关键字段 | 说明 |
|---|---|---|
| `card_key` | id, batch_id, code, type(AI_QUOTA/VIP), value, status(UNUSED/USED/VOID/EXPIRED), expire_at, used_by, used_at, created_at | 卡密状态机；`batch_id` 支持按批次导出 |
| `quota_pack` | id, user_id, card_key_id, granted_count, used_count, expire_at, created_at | **次数包批次表**（DB 为准）；支撑"365 天过期、先进先出扣减" |
| `quota_ledger` | id, user_id, type, delta, source, ref_id, remark, created_at | 额度与 VIP 流水，只追加，用于审计对账 |
| `sys_config` | id, config_key, config_value, encrypted, masked, updated_by, updated_at | **统一配置表**，承接 `llm.*` / `search.*` / `storage.*` 前缀；非敏感项不加密 |
| `audit_log` | id, user_id, action, target_type, target_id, detail, created_at | 审计日志，脱敏后保留 30 天 |

> `ai_call_log`、`search_cache` 暂不建表；AI 调用走应用日志，guides 缓存放 Redis，需要时再补表。

---

## 4. 模块划分与依赖

| 模块 | 职责 | 主要依赖 |
|---|---|---|
| 认证权限 | 注册登录、JWT、邮箱验证、找回密码、注销、封禁拦截、角色校验、行级隔离 | user、email_token、Redis |
| 城市与旅行 | 国家字典、城市表维护、trip 生成/命名/归组 | country、city、trip |
| 地图 | 三态计算、旅行/城市分组统计、侧边抽屉组装 | itinerary、travel_note、expense、trip |
| 行程 | AI 生成、微调、手动景点、保存/编辑/删除、多城市分配 | itinerary、itinerary_day、itinerary_spot、LlmProvider、SearchProvider |
| 行前准备 | preparations 展示与缓存、清单勾选、官方链接 | itinerary、trip_checklist、country_official_link、Redis |
| 搜索增强 | SearchProvider 抽象、guides 缓存、降级 | Redis、SearchProvider |
| 游记 | 富文本、图片上传、分享令牌 | travel_note、note_share、StorageProvider |
| AI 润色 | SSE 流式润色、额度校验、取消 | LlmProvider、Redis |
| 记账 | 多币种、汇率快照、多维度汇总 | expense、exchange_rate |
| 海报 | 前端 DOM 截图 + Canvas 连线图 + 二维码 | 前端资源、/api/map/stats、分享令牌 |
| 卡密与额度 | 卡密生成/兑换、额度扣减、幂等、生成去重、取消标志 | card_key、quota_pack、quota_ledger、Redis |
| 账号安全 | TOTP 二次验证、恢复码、改密/改邮箱、退出所有设备、注销 | user、user_recovery_code、Redis |
| 内容治理 | 游客举报、媒体审核、下架/恢复 | note_report、travel_note、audit_log |
| 运营 | 通知公告、用户反馈、登录日志 | announcement、announcement_read、feedback、login_log |
| 监控与告警 | 在线用户、服务/数据库/缓存/存储快照、飞书故障提醒、数据保留清理 | Redis、OSHI、sys_config、audit_log、login_log |
| 后台管理 | 配置、用户、内容、汇率、城市、官方链接 | sys_config、user、city、country_official_link、audit_log |
| 通用能力 | 存储抽象、搜索抽象、审计、限流、异常、日志脱敏、固定北京时间 | 全局 |

### 4.1 存储抽象接口

```
interface StorageProvider {
    PresignedUpload presignUpload(String objectKey, String contentType, long expireSeconds);
    String presignDownload(String objectKey, long expireSeconds);
    void delete(String objectKey);
    boolean exists(String objectKey);
}

record PresignedUpload(String uploadUrl, Map<String,String> headers, long expireAt) {}
```

**实现**
- `S3CompatibleStorageProvider`：一套实现同时适配云 OSS（阿里云/腾讯云 S3 兼容端点）和 MinIO，仅 endpoint、region、bucket、accessKey、secretKey、pathStyle 不同。
- 生产环境 `storage.provider=oss`，本地开发 `storage.provider=minio`，均由 `sys_config` 提供。

**配置项**（`sys_config`，敏感字段 AES 加密）

| config_key | 说明 |
|---|---|
| `storage.provider` | `oss` / `minio` |
| `storage.endpoint` | 服务端点 |
| `storage.region` | 区域（MinIO 可空） |
| `storage.bucket` | 桶名 |
| `storage.access-key` | 加密存储、页面掩码 |
| `storage.secret-key` | 加密存储、页面掩码 |
| `storage.public-domain` | 可选，CDN/自定义域名 |
| `storage.path-style` | MinIO 通常为 true，云 OSS 为 false |

**对象 Key 规范**
- 游记图片：`note/{userId}/{yyyy}/{MM}/{uuid}.{ext}`
- **不用 `noteId`**：上传发生在创建游记之前，此时 noteId 尚不存在
- 文件名不直接用用户输入，统一 uuid + 白名单扩展名（jpg/png/webp）
- 孤立对象（上传成功但未发布游记）由每日清理任务按 `created_at > 24h 且未被任何游记引用` 删除

**签名 URL 有效期**
- 普通访问：600 秒
- 分享页图片：`min(分享令牌剩余有效期, 3600 秒)`
- 上传签名：300 秒

**切换机制**
- 配置热加载（见 7.8）时重建 `StorageProvider` Bean；进行中的上传/下载使用旧实例完成，不受影响。

### 4.2 搜索抽象接口

```
interface SearchProvider {
    List<GuideLink> searchGuides(GuideSearchQuery query);
}

record GuideSearchQuery(
    String countryCode,
    String countryName,
    List<String> cityCodes,
    List<String> cityNames,
    int days,
    String normalizedPreference
) {}

record GuideLink(
    String title,
    String url,
    String source
) {}
```

**实现类**：`BochaSearchProvider`、`TavilySearchProvider` 等，按 `sys_config` 的 `search.provider` 选择。

**规则**
- 超时：实现内部设置连接超时 3s、读取超时 5s；超时抛 `SearchTimeoutException`。
- 返回条数：实现内部最多取 5 条，调用方再截断为 3 条并去重、校验 http/https。
- 异常：实现只抛 `SearchTimeoutException`、`SearchException`；调用方统一捕获降级，不向上抛。
- 不解析网页正文：只返回标题、URL、来源域名。
- Key 来源：从 `sys_config` 读取，由 `ProviderHolder` 注入，不在接口签名里传。

---

## 5. P0 接口清单

> 统一前缀 `/api`；统一响应 `{ code, message, data }`；鉴权头 `Authorization: Bearer <JWT>`；分页参数 `page`、`size`。

### 5.1 认证与账号

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册，校验邮箱验证码并创建已激活账号 |
| POST | `/api/auth/send-code` | 发送邮箱验证码（`VERIFY` / `RESET`，60 秒间隔、每日 10 次） |
| POST | `/api/auth/login` | 登录，返回 JWT |
| POST | `/api/auth/reset-password` | 用邮箱验证码重置密码 |
| POST | `/api/auth/logout` | 退出（可选，JWT 无状态） |
| POST | `/api/user/logout-account` | 账号注销 |
| GET | `/api/user/profile` | 当前用户信息 |
| PUT | `/api/user/profile` | 修改昵称/头像预设 |

### 5.2 城市与旅行

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/countries` | 国家字典（仅返回 ENABLED），供下拉框与名称展示 |
| GET | `/api/cities?countryCode=` | 按国家查城市（仅返回 ENABLED） |
| GET | `/api/trips?countryCode=` | 查用户旅行列表 |
| PUT | `/api/trips/{id}` | 旅行改名 |
| DELETE | `/api/trips/{id}?force=` | 删除旅行（有挂载数据需 `force=true`） |

### 5.3 地图

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/map/stats` | 返回用户所有国家点亮状态与次数 |
| GET | `/api/map/country/{countryCode}` | 国家侧边抽屉：旅行分组、城市分组、花费、游记、行程（结构见 7.14） |

### 5.4 AI 与行程

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/itinerary/generate` | AI 生成行程 + guides + preparations（支持 60 秒去重） |
| POST | `/api/ai/cancel` | **取消 AI 生成/润色**，入参 `requestId`，写 Redis 取消标志 |
| POST | `/api/itinerary/save` | 保存正式行程，首次生成 tripId |
| GET | `/api/itinerary/{id}` | 行程详情 |
| PUT | `/api/itinerary/{id}` | 编辑行程标题与整体日期（天数不变） |
| DELETE | `/api/itinerary/{id}` | 删除行程（软删除） |
| POST | `/api/itinerary/{id}/days` | **新增一天**（指定插入位置、日期、城市） |
| PUT | `/api/itinerary/{id}/days/{dayId}` | **修改某天的城市与日期** |
| DELETE | `/api/itinerary/{id}/days/{dayId}` | **删除某天**（该天景点一并物理删除） |
| POST | `/api/itinerary/{id}/regenerate` | 重新生成，询问是否保留手动景点 |
| POST | `/api/itinerary/{id}/days/{dayId}/spots` | 手动添加景点 |
| PUT | `/api/itinerary/spots/{spotId}` | 编辑景点 |
| DELETE | `/api/itinerary/spots/{spotId}` | 删除景点 |
| PUT | `/api/itinerary/spots/{spotId}/move` | 移动到某天 / 天内排序 |

### 5.5 行前准备

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/prep/checklist?itineraryId=` | 获取清单状态（未落库项返回默认未勾选） |
| PUT | `/api/prep/checklist` | 勾选/取消清单项，upsert |
| GET | `/api/prep/official-links?countryCode=` | 官方链接；未配置返回通用搜索兜底 |

### 5.6 游记与分享

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/upload/presign` | 图片上传签名（前端直传 OSS，见 4.1） |
| POST | `/api/notes` | 创建游记 |
| PUT | `/api/notes/{id}` | 编辑游记 |
| DELETE | `/api/notes/{id}` | 删除游记 |
| GET | `/api/notes/{id}` | 游记详情 |
| POST | `/api/notes/{id}/share` | 生成分享令牌 |
| DELETE | `/api/notes/{id}/share` | 撤销分享 |
| GET | `/s/{token}` | 只读分享页（无需登录，字段白名单见 7.12） |

### 5.7 AI 润色

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ai/polish/stream` | SSE 流式润色，fetch + ReadableStream，支持取消 |

### 5.8 记账

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/expenses` | 新增消费 |
| GET | `/api/expenses` | 消费列表（支持国家/城市/旅行/日期/分类过滤 + 分页） |
| PUT | `/api/expenses/{id}` | 编辑消费 |
| DELETE | `/api/expenses/{id}` | 软删除 |
| GET | `/api/expenses/summary` | 按国家/城市/旅行/日期多维汇总（逻辑见 7.15） |

### 5.9 卡密与额度

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/card/redeem` | 兑换卡密，需 `Idempotency-Key` |
| GET | `/api/quota/me` | 当前额度/VIP 状态 |

### 5.10 后台管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/PUT | `/api/admin/config/llm` | LLM 配置（掩码展示） |
| GET/PUT | `/api/admin/config/oss` | OSS 配置 |
| GET/PUT | `/api/admin/config/search` | 搜索配置 |
| POST | `/api/admin/cards/generate` | 批量生成卡密（规则见 7.18） |
| GET | `/api/admin/cards` | 卡密列表 |
| GET | `/api/admin/cards/export?batchId=` | 导出卡密 CSV（UTF-8 BOM） |
| PUT | `/api/admin/cards/{id}/void` | 作废卡密 |
| GET | `/api/admin/users` | 用户列表 |
| PUT | `/api/admin/users/{id}/status` | 封禁/解封（JWT 失效见 7.19） |
| PUT | `/api/admin/users/{id}/quota` | 调整额度/VIP |
| GET/POST | `/api/admin/cities` | 城市列表 / 新增城市 |
| PUT/DELETE | `/api/admin/cities/{id}` | 修改状态 / 物理删除（仅未被引用） |
| GET/POST | `/api/admin/official-links` | 官方链接列表 / 新增 |
| PUT/DELETE | `/api/admin/official-links/{id}` | 修改 / 删除 |
| GET/PUT | `/api/admin/exchange-rates` | 汇率维护（**新增币种时回填历史 NULL 快照**，见 7.15） |
| PUT | `/api/admin/notes/{id}/takedown` | 内容下架（同时撤销全部分享令牌） |
| PUT | `/api/admin/notes/{id}/restore` | 内容恢复（原分享令牌仍失效，需重新生成） |
| PUT | `/api/admin/notes/{id}/media-review` | 标记媒体已审核 |
| GET | `/api/admin/feedback` | 反馈列表（统计/详情/更新见接口文档 12.18–12.21） |
| GET | `/api/admin/reports` | 举报列表（统计/详情/采纳/驳回见接口文档 12.22–12.26） |
| GET/POST | `/api/admin/announcements` | 公告列表 / 新建（发布、下线、删除见接口文档 12.28） |
| GET | `/api/admin/login-logs` | 登录日志 |
| GET | `/api/admin/monitor/online-users` | 在线用户（服务/数据库/缓存/存储快照见接口文档 12.29） |
| GET/PUT | `/api/admin/config/mail` | 邮件 SMTP 配置 |
| GET/PUT | `/api/admin/config/alert` | 飞书故障提醒配置；`POST /api/admin/config/alert/test` 发送测试消息 |
| POST | `/api/admin/config/oss/test` | OSS 连接测试 |

---

## 6. 错误码表

| 错误码 | 含义 | HTTP | 触发场景 |
|---|---|---|---|
| 0 | 成功 | 200 | 正常返回 |
| 1001 | 用户名或密码错误 | 401 | 登录失败 |
| 1002 | 邮箱未验证 | 403 | 未验证邮箱登录 |
| 1003 | 账号已被封禁 | 403 | 封禁用户登录/访问 |
| 1004 | 验证/重置令牌无效或过期 | 400 | 邮箱验证、找回密码 |
| 1005 | 邮件发送过于频繁 | 429 | 60 秒限流 |
| 1006 | JWT 无效或过期 | 401 | Token 失效 |
| 1007 | 账号已注销 | 403 | 已注销用户登录/访问 |
| 1008 | 用户名或邮箱已存在 | 409 | 注册查重 |
| 2001 | 参数校验失败 | 400 | 必填缺失、格式错误 |
| 2002 | 国家/城市不存在或已停用 | 400 | 城市选择 |
| 2003 | 行程不存在或无权访问 | 404 | 行程详情/编辑 |
| 2004 | 游记不存在或无权访问 | 404 | 游记详情/编辑 |
| 2005 | 消费记录不存在或无权访问 | 404 | 消费编辑/删除 |
| 2006 | 手动景点已达上限 | 400 | 超过 30 个 |
| 2007 | 分享令牌无效或已失效 | 410 | 分享页访问 |
| 2008 | 卡密不存在 | 404 | 兑换卡密 |
| 2009 | 卡密已使用 | 409 | 重复兑换 |
| 2010 | 卡密已作废或过期 | 410 | 兑换卡密 |
| 2011 | 额度耗尽 | 429 | AI 生成/润色 |
| 2012 | AI 服务不可用 | 503 | 模型超时/报错/JSON 失败 |
| 2013 | 搜索服务不可用 | 503 | 搜索超时（不阻断行程） |
| 2014 | 未配置汇率 | 200 | 汇总提示（非阻断） |
| 2015 | 旅行不存在或无权访问 | 404 | 旅行相关（防枚举） |
| 2016 | 景点不存在或无权访问 | 404 | 景点相关（防枚举） |
| 2017 | 旅行下存在挂载数据，需 `force=true` | 409 | 删除旅行 |
| 2018 | 图片数量超限（> 20 张） | 400 | 游记保存 |
| 2019 | 单图超限（> 5 MB） | 400 | 上传签名 |
| 2020 | 正文超限（> 200 KB） | 400 | 游记保存 |
| 2021 | 不支持的图片类型 | 400 | 上传签名 |
| 2022 | 清单项不在固定枚举内 | 400 | 清单勾选 |
| 2023 | 分享令牌创建失败 | 500 | 创建分享 |
| 2024 | 内容已下架，不可操作 | 403 | 编辑/分享 |
| 2025 | 城市已被引用，不能物理删除 | 409 | 删除城市 |
| 2026 | 该国官方链接已达上限（3 条） | 400 | 新增官方链接 |
| 3001 | 重复请求 / 相同请求正在处理 | 409 | 幂等键冲突；AI 生成去重在途（见 7.1） |
| 3002 | 并发操作冲突 | 409 | 卡密并发兑换 |
| 4001 | 无权访问该资源 | 403 | 行级隔离越权 |
| 4002 | 需要管理员权限 | 403 | 普通用户访问后台 |
| 5001 | 系统内部错误 | 500 | 未捕获异常 |
| 5002 | 数据库操作失败 | 500 | DB 异常 |
| 5003 | 存储服务不可用 | 503 | OSS/MinIO 异常 |
| 5004 | 限流拦截 | 429 | 接口限流 |

**约定**：`code=0` 成功；非 0 失败；HTTP 状态码与 `code` 配合使用；`message` 给用户，`detail` 可选给开发排查；新增错误码在 `docs/api.md` 和代码常量类同步维护。

---

## 7. 关键流程详细设计

### 7.0 AI 行程 JSON Schema

```json
{
  "countryCode": "JP",
  "days": [
    {
      "dayIndex": 1,
      "cityCode": "TYO",
      "spots": [
        {
          "name": "浅草寺",
          "description": "东京最古老的寺庙，适合上午游览",
          "suggestedDuration": "2小时"
        }
      ]
    }
  ],
  "guides": [
    { "title": "东京5日游攻略", "url": "https://example.com", "source": "example.com" }
  ],
  "preparations": {
    "visa": "中国公民赴日旅游通常需办理签证，建议提前1-2个月准备",
    "currency": "日元 JPY，建议提前兑换少量现金",
    "powerPlug": "日本电压100V，两脚扁插，建议带转换头",
    "bestSeason": "春季樱花季和秋季红叶季最佳",
    "notes": ["建议购买旅行保险", "下载离线地图和翻译App"]
  }
}
```

**校验规则**
- `countryCode` 必填，必须与用户选择一致，否则拒绝。
- `days` 长度必须等于用户输入天数；`dayIndex` 从 1 连续递增。
- `cityCode` 必填，必须属于用户选择的城市集合。
- 每天 `spots` 至少 1 个、最多 6 个；`name` 必填，`description` 和 `suggestedDuration` 可为空字符串。
- `date` 不由模型输出，后端按出发日期 + `dayIndex` 生成。
- `guides` 由搜索模块填充，不由模型输出；最多 3 条，`url` 必须 http/https。
- `preparations` 五项固定；前四项为字符串，`notes` 为字符串数组，最多 5 条。

### 7.1 AI 生成行程流程

```
用户提交（国家 + 城市[] + 天数 + 偏好）
  │
  ├─ 1. 额度校验（Redis）：VIP/次数包/免费额度，失败返回 2011
  │
  ├─ 2. 60 秒去重检查（两段式，防并发双扣）：
  │     fingerprint = sha256(countryCode + "|" + sortedCityCodes + "|" + days + "|" + normalizedPreference)
  │     ├─ 2a. 结果缓存命中 quota:dedupe:{userId}:{fingerprint}
  │     │      → 直接返回首次结果，不扣额度
  │     ├─ 2b. 在途锁 quota:dedupe:lock:{userId}:{fingerprint}
  │     │      SET NX EX 120
  │     │      ├─ 抢到 → 继续（生成完成后释放锁并写入结果缓存）
  │     │      └─ 抢不到 → 说明有相同请求正在生成，返回 3001
  │     │                 前端提示"相同行程正在生成中"，2 秒后重试一次
  │     └─ 未命中且抢到锁 → 继续
  │
  ├─ 3. 生成 requestId，先扣额度（Redis Lua 原子扣减，记录扣减来源与 requestId）
  │
  ├─ 4. 并行发起：
  │     ├─ LlmProvider.generateItinerary(prompt) → JSON
  │     └─ SearchProvider.searchGuides(query) → guides
  │
  ├─ 5. 搜索独立超时 5s：超时/失败 → guides = []
  │
  ├─ 6. LLM 返回解析：
  │     ├─ 成功 → 校验 JSON Schema，补全 countryCode/date/dayIndex
  │     └─ 失败 → 重试 1 次（提示词强制只输出 JSON）
  │           └─ 仍失败 → 退回额度，返回 2012
  │
  ├─ 7. 组装返回：days/spots + guides + preparations
  │
  ├─ 8. 写去重结果缓存 quota:dedupe:{userId}:{fingerprint} = 结果 JSON，TTL 60 秒
  │     并释放在途锁 quota:dedupe:lock（无论成功失败都要释放，建议 finally）
  │
  └─ 9. 不落库（草稿态），前端微调后调 /save
```

**关键点**
- 先扣后调、失败补偿退回；搜索失败不退回额度。
- **去重**：结果缓存 + 在途锁两段式。只写结果缓存会留下并发窗口——两个相同请求同时到达都未命中，就会双扣。在途锁保证同一指纹同时只有一个请求在跑，其余返回 `3001`。
- 在途锁 TTL 120 秒，必须大于 `LLM 超时 30s × 2 次重试`；用 `finally` 释放，避免异常时锁悬挂。
- JSON 解析失败重试 1 次；`preparations` 与同一次 LLM 调用返回。
- 生成阶段（非流式）不做 chunk 级取消检查；取消接口主要服务于润色流式场景（见 7.4）。

### 7.2 额度扣减流程

```
Redis Key 设计：
  quota:free:{userId}:{yyyyMMdd}        → 当日免费已用次数（INCR，TTL 到次日零点）
  quota:pack:{userId}                   → 次数包可用余额（缓存，DB 为准）
  vip:{userId}                          → VIP 过期时间戳
  quota:daily:vip:{userId}:{yyyyMMdd}   → VIP 日上限计数（兜底，默认 200）
  quota:dedupe:{userId}:{fingerprint}   → 60 秒生成去重结果，TTL 60s
  quota:dedupe:lock:{userId}:{fingerprint} → 在途锁，SET NX EX 120
  quota:deduct:{userId}:{requestId}     → 本次扣减来源与批次，TTL 10min（用于失败退回）
  quota:req:{userId}:{requestId}        → requestId 归属标记，TTL 5min（用于取消越权校验）
  cancel:{userId}:{requestId}           → 取消标志，TTL = 请求最长生命周期

扣减顺序（Lua 原子脚本）：
  1. 若 VIP 有效 → 检查 VIP 日上限（默认 200）→ 通过则放行，不扣包不扣免费
  2. 否则若存在未过期的次数包 → 按「最早过期优先」扣减
  3. 否则若免费额度 < 3 → INCR 免费计数
  4. 否则 → 返回 2011
```

**次数包的过期与扣减（关键设计）**

单一计数器**无法表达 365 天过期**：用户第 1 天兑 100 次、第 300 天兑 100 次，第 366 天时无法区分哪 100 次已失效。因此引入批次表：

```
quota_pack 表（DB 为准）
  id, user_id, card_key_id, granted_count, used_count, expire_at, created_at

扣减：
  1. SELECT * FROM quota_pack
     WHERE user_id = ? AND used_count < granted_count AND expire_at > NOW()
     ORDER BY expire_at ASC, id ASC
     LIMIT 1 FOR UPDATE;
  2. UPDATE quota_pack SET used_count = used_count + 1 WHERE id = ?;
  3. 刷新 Redis 配额缓存 quota:pack:{userId}

退回（AI 失败）：
  1. 从 quota:deduct:{userId}:{requestId} 读到 {"source":"PACK","packId":123}
  2. UPDATE quota_pack SET used_count = used_count - 1 WHERE id = 123 AND used_count > 0;
  3. 刷新缓存；键不存在则视为已退，直接返回（幂等）
```

`quota:pack:{userId}` 只是读缓存（`GET /quota/me` 用），扣减的**真实依据永远是 DB 行锁**。Redis 缓存与 DB 不一致时以 DB 回填。

```
扣减来源记录：
  SET quota:deduct:{userId}:{requestId} = {"source":"VIP"|"PACK"|"FREE","packId":123|null} EX 600
  SET quota:req:{userId}:{requestId} = 1 EX 300

失败退回（幂等）：
  读取 quota:deduct:{userId}:{requestId}
  ├─ 键不存在 → 已退过或从未扣过，直接返回
  ├─ VIP  → DECR quota:daily:vip
  ├─ PACK → 批次表 used_count - 1（见上）
  └─ FREE → DECR quota:free（不低于 0）
  回补后 DEL quota:deduct 键，保证重复退款不会多退
```

**关键点**
- VIP 日上限与免费额度用 Lua 保证"检查 + 扣减"原子性；次数包用 DB 行锁保证。
- **去重优先于扣减**：结果缓存命中或抢不到在途锁时，完全不进入扣减流程。
- `quota:req` 供 7.21 的取消接口做归属校验，防止横向取消他人请求。
- 取消标志由 `/api/ai/cancel` 写入，流式场景每个 chunk 前检查。

### 7.3 卡密兑换流程

```
用户提交 code + Idempotency-Key
  │
  ├─ 1. 幂等检查：Redis SETNX idem:{key}，已存在则返回上次结果
  ├─ 2. 分布式锁：lock:card:{code}（SET NX PX 10s）
  ├─ 3. 查 card_key：不存在→2008 / 已用→2009 / 作废或过期→2010
  ├─ 4. 事务内：
  │     ├─ UPDATE card_key SET status=USED, used_by, used_at WHERE id=? AND status=UNUSED
  │     ├─ 若影响行数=0 → 回滚，返回 3002
  │     ├─ 加额度或 VIP 时长（同类型累加）
  │     └─ 写 quota_ledger / audit_log
  ├─ 5. 释放锁，写幂等结果（TTL 24h）
  └─ 6. 返回成功
```

**兑换成功后的入账规则**

| 卡密类型 | 入账动作 |
|---|---|
| `AI_QUOTA` | **插入一条 `quota_pack` 批次**：`granted_count=value`、`used_count=0`、`expire_at=now + quota.pack-valid-days(365)天`、`card_key_id=卡密 id`；同时刷新 Redis `quota:pack:{userId}` |
| `VIP` | `vip_expire_at = GREATEST(NOW(), vip_expire_at) + value 天`（未过期则叠加，已过期则从当下起算）；不清空次数包 |

> **这是易漏点**：只写 `quota_ledger` 而不写 `quota_pack`，用户兑换完后扣减时找不到批次，额度看似到账却无法使用。管理员在后台手动调增 `AI_QUOTA` 时走同一逻辑（`card_key_id=NULL`）。

**关键点**：Redis 幂等键 + 分布式锁 + 数据库条件更新三重兜底；已使用卡密不可回滚；**AI 次数包必须落 `quota_pack` 批次**。

### 7.4 SSE 润色流程

```
前端 fetch POST /api/ai/polish/stream
  Headers: Authorization: Bearer <JWT>
  Body: { requestId, noteId?, text, style?, target? }
  │
  ├─ 1. JWT 过滤器校验登录态 + user.status
  ├─ 2. 额度校验（同 7.2，先扣后调，生成 requestId 并记录扣减来源）
  ├─ 3. 调用 LlmProvider.polishStream(text)
  ├─ 4. 逐块写回，每输出一个 chunk 前检查取消标志：
  │     GET cancel:{userId}:{requestId}
  │     ├─ 命中 → 停止生成，发送 event: cancelled / data: {"requestId":"..."}
  │     └─ 未命中 → 继续发送 event: delta / data: {"delta":"..."}
  ├─ 5. 正常结束：发送 event: done / data: {"requestId":"...","totalChars":N}
  ├─ 6. 异常：发送 event: error / data: {"code":2012,"message":"..."}，并按规则退回额度
  └─ 7. 前端 ReadableStream 逐块解析，打字机渲染
```

**取消判定规则（与 BR-002 一致）**

| 场景 | 判定 | 额度处理 |
|---|---|---|
| 用户点"取消生成"→ 前端调 `/api/ai/cancel` | 主动取消 | **不退回** |
| 未输出任何有效内容即断网/关标签 | 异常断连 | **退回** |
| 已输出部分流式内容后关闭 | 已交付部分结果 | **不退回** |

**关键点**
- 前端"取消生成"按钮必须同时调用 `/api/ai/cancel`（传 `requestId`）并 abort fetch；仅 abort 不构成主动取消。
- `AbortController` 只用于释放浏览器连接，不作为服务端判定依据。
- Nginx `proxy_buffering off`、`proxy_read_timeout` 大于模型超时。

### 7.5 tripId 归组流程

```
保存行程（首次）：
  ├─ 若请求未带 tripId → 新建 trip（name 默认"国家 · 年月"，
  │    start_date/end_date 取该行程的日期）
  └─ itinerary.trip_id = 新 trip.id

保存行程（归入已有 trip）：
  ├─ 校验 trip.user_id = 当前用户（否则 2015）
  ├─ 校验 trip.country_code = 行程 country_code（否则 2001：不能把日本行程挂到泰国旅行）
  └─ 挂载后重算 trip.start_date/end_date = MIN/MAX(关联行程日期)

保存游记/消费：
  ├─ 关联了行程 → trip_id = itinerary.trip_id（不做二次选择）
  ├─ 未关联 → 前端弹选择：已有 trip 列表 / 未归组
  ├─ 手动归组时校验 trip.country_code = 数据的 country_code
  │   前端下拉只展示**该国家**的旅行，避免跨国误挂
  └─ 默认选中该国家最近一次 trip

行程日期变化（编辑日期、增删天、删除行程、删除旅行）：
  └─ 同步重算受影响 trip 的 start_date/end_date；
     若无关联行程则保持原值（前端显示"无日期"）

统计：
  ├─ 按国家汇总：SUM(expense) GROUP BY country_code
  └─ 按旅行拆分：SUM(expense) GROUP BY trip_id（未归组 trip_id IS NULL）

删除旅行：
  ├─ 无挂载数据 → 直接软删除
  └─ 有挂载数据 → 需 force=true；事务内将 itinerary/travel_note/expense 的 trip_id 置 NULL
```

**关键点**
- 删除行程不删 trip；删除 trip 不删除挂载数据（置空进入"未归组"）；分享页不展示 tripId 和旅行名称。
- **跨国家挂载必须拦截**：`trip.country_code` 与数据的 `country_code` 不一致时返回 `2001`，否则按国家汇总会串账。
- **旅行日期区间是派生值**：由关联行程的 MIN/MAX 维护，行程增删改后必须重算；否则抽屉显示的日期范围会与行程不符。

### 7.6 地图三态计算

```
输入：当前 userId

1. 查未删除正式行程，按 country_code 聚合：
   SELECT country_code, COUNT(*) AS itinerary_count,
          MAX(CASE WHEN end_date >= CURDATE() THEN 1 ELSE 0 END) AS has_unexpired
   FROM itinerary
   WHERE user_id = ? AND deleted = 0 AND status = 'SAVED'
   GROUP BY country_code
   -- MAX 而非 MIN：只要存在一条未过期行程，has_unexpired 即为 1

2. 查未删除游记（含私密），按 country_code 聚合：
   SELECT country_code, COUNT(*) AS note_count
   FROM travel_note
   WHERE user_id = ? AND deleted = 0
   GROUP BY country_code

3. 合并结果集，按 country_code 计算状态：
   - note_count > 0 → GREEN
   - note_count = 0 且 itinerary_count > 0 → YELLOW
     - has_unexpired = 0（全部行程都已过期）→ YELLOW + expired=true
     - has_unexpired = 1 → YELLOW + expired=false
   - 都没有 → 不返回，前端默认灰色

4. 返回：
   { countryCode, status, itineraryCount, noteCount,
     totalCount = itineraryCount + noteCount, expired }
```

**关键点**
- 绿色优先；全部行程都过期才标 `expired=true`（只要有 1 条未过期就不标）。
- 点亮只看有无未删除数据，与行程日期无关；`expired` 只是展示角标。
- AI 生成未保存的临时结果不落库，天然不参与计算。
- 加 `(user_id, deleted, country_code)` 索引。

### 7.7 重新生成时手动景点合并算法

```
输入：旧行程（含 source=manual 的景点）、新行程（AI 生成）
输出：新行程 + 合并后的手动景点 + 合并提示列表

1. 收集旧行程所有 source=manual 的景点，记录 (dayIndex, cityCode, spotName, spot)

2. 对每个手动景点，按顺序尝试合并：
   a. 新行程存在第 dayIndex 天，且 cityCode 相同
      → 追加到该天末尾，无提示
   b. 新行程存在第 dayIndex 天，但 cityCode 不同
      → 追加到该天末尾，标记 cityMismatch=true
   c. 新行程不存在第 dayIndex 天（天数减少）
      → 追加到第 1 天末尾，标记 movedToFirstDay=true

3. 追加时 sort_order 按当前天最大 sort_order 递增，source 保持 manual

4. 额度保护：调用 AI **之前**先查现有 manual 数；若 keepManualSpots=true 且已 ≥ 30
   → 直接返回 2006，不扣额度、不调 AI
   合并完成后再校验一次；若合并结果 > 30 → 返回 2006（此时 AI 已调用，额度不退）

5. 返回提示：
   - cityMismatch："手动景点「XXX」原属城市A，已合并到城市B的第N天，请确认"
   - movedToFirstDay："手动景点「XXX」原属第N天，因新行程天数减少，已移至第1天"

6. 前端展示提示，用户可继续拖拽、移动到某天、删除
```

**关键点**：不丢数据；城市不匹配和天数减少都合并但提示；合并纯数据库操作，不消耗额度。

### 7.8 配置热加载机制

```
配置读取：
  ├─ 启动时从 sys_config 加载 llm.* / search.* / storage.* 到 ConfigHolder（内存）
  └─ 敏感字段 AES 解密后仅保存在内存，不返回前端

配置变更：
  ├─ 管理员 PUT /api/admin/config/{type}
  ├─ Service 校验新配置（可选：用新配置发起一次轻量探测调用）
  │    ├─ 校验失败 → 拒绝保存，保留旧配置，返回 2001/5003
  │    └─ 校验通过 → 加密写库，更新 updated_at
  ├─ 发布 ApplicationEvent：ConfigChangedEvent(type)
  └─ 监听器 ConfigRefreshListener：
       ├─ 重建对应工厂：LlmProviderFactory / SearchProviderFactory / StorageProviderFactory
       ├─ 新实例放入 ProviderHolder（AtomicReference 替换）
       └─ 进行中的请求持有旧实例引用，不受影响

兜底刷新：
  └─ 定时任务每 60s 比对 sys_config.updated_at 与内存版本，防止事件丢失

Provider 获取方式：
  └─ 业务层统一从 ProviderHolder.getLlm() / getSearch() / getStorage() 取当前实例
```

**关键点**：保存前校验、事件驱动重建、AtomicReference 替换、进行中请求不受影响、定时兜底；不重启服务即生效，满足 AC-04。

### 7.9 账号注销处理清单

注销在单个事务内按表处理，任一失败整体回滚；成功后 `user.status=DELETED`，JWT 过滤器拦截返回 1007。

| 表 | 处理 |
|---|---|
| `user` | `status=DELETED`、`deleted=1`；`username` 改为 `deleted_{id}`；`email` 改为 `deleted_{id}@deleted.local`；`nickname` 改为"已注销用户"；`password_hash` 置空；`vip_expire_at` 置空；`token_version=token_version+1`（旧 JWT 立即失效） |
| `email_token` | 全部 `used=1` |
| `trip` | `deleted=1`；`name` 改为"已注销旅行" |
| `itinerary` | `deleted=1`；`title` 改为"已注销行程"；`guides_json`/`preparations_json` 置空 |
| `itinerary_day` / `itinerary_spot` | 随 itinerary 软删除；`itinerary_spot.name/description` 置空 |
| `travel_note` | `deleted=1`；`title` 改为"已注销游记"；`content` 置空；`cover_image` 保留对象 Key 但不再生成签名 |
| `note_share` | `revoked=1`；`expire_at=now`；分享页访问立即返回 2007 |
| `expense` | `deleted=1`；`remark` 置空 |
| `trip_checklist` | `deleted=1` |
| `card_key` | 不处理（已使用记录保留，`used_by` 保留原 user_id 用于审计） |
| `quota_pack` | **物理删除**该用户所有批次（余额是用户资产，账号注销即失效，无需保留） |
| `quota_ledger` | 保留，`user_id` 保留原值（审计需要）；不对外展示 |
| `audit_log` | 保留，`detail` 脱敏；`user_id` 保留原值用于追溯 |

**注销后行为**
- 登录返回 1007；
- JWT 过滤器校验 `user.status != DELETED`，否则返回 1007；
- 地图统计接口按 `user.status` 过滤，已注销用户数据不计入；
- 分享页访问返回 2007"分享已失效"；
- 注销不可恢复；管理员后台用户列表显示"已注销"。

### 7.10 行前准备流程

```
数据来源：
  ├─ preparations：随 AI 行程生成，存 itinerary.preparations_json
  ├─ 清单：默认 10 项枚举（VISA/FLIGHT/HOTEL/INSURANCE/SIM/CURRENCY/PLUG/APPS/PACKING/CUSTOMS）+ 自定义项
  └─ 官方链接：country_official_link 表

展示判断：
  └─ preparations_json 为 null，或五项均为空字符串且 notes 为空 → 前端不展示整区

清单读取（GET /api/prep/checklist?itineraryId=）：
  ├─ 校验 itinerary 属于当前用户
  ├─ 查 trip_checklist（user_id + country_code + itinerary_id）
  ├─ 对未落库的默认 item_key 返回默认 checked=false
  └─ 返回默认 10 项 + 自定义项顺序列表

清单勾选（PUT /api/prep/checklist）：
  ├─ 入参：itineraryId、itemKey、checked
  ├─ 校验 itinerary 属于当前用户、itemKey 属于默认枚举或 CUSTOM_ 前缀
  └─ upsert：
      INSERT INTO trip_checklist(user_id, country_code, itinerary_id, item_key, checked,
                                 title, description, days_before, sort_order, custom)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      AS new
      ON DUPLICATE KEY UPDATE checked = new.checked, title = new.title,
                             description = new.description, days_before = new.days_before,
                             sort_order = new.sort_order, custom = new.custom, deleted = 0;

官方链接（GET /api/prep/official-links?countryCode=）：
  ├─ 已配置：返回 [{title, url}]，最多 3 条，url 做 http/https + 长度校验
  └─ 未配置：返回 { fallback: true,
                   searchUrl: "https://www.bing.com/search?q=" + encodeURIComponent(countryName + " 签证 官方") }
      前端展示"去搜索"按钮，新窗口打开并提示外部链接
```

**关键点**：默认项不预落库、勾选或自定义时 upsert；唯一键 `(user_id, itinerary_id, item_key)`；官方链接未配置时给通用搜索兜底，不报错；`preparations` 为空时整区不展示。

### 7.10.1 preparations 缓存机制

```
缓存目的：避免同一国家+城市被多位用户重复调用 LLM 生成相同的行前准备信息，降低 Token 消耗。

缓存键：
  key = "prep:" + countryCode + ":" + sha256(sortedCityCodes)
  └─ preparations 与具体天数、偏好无关，故不纳入指纹；城市集合影响签证/插头等提示

实现方式：
  ├─ LLM 返回后，提取 preparations 字段
  ├─ SETEX prep:{countryCode}:{cityHash} 86400 <preparations_json>
  └─ 下次生成前先 GET，命中则直接复用（仍会正常扣 AI 额度，因为用户是在生成行程）

与行程的关系：
  ├─ preparations 不可单独生成（V1 无独立入口）
  ├─ 缓存只省 Token，不改变额度语义：行程生成仍按次扣费
  └─ preparations 随行程一起返回，保存时写入 itinerary.preparations_json

一致性：
  ├─ 缓存 TTL 24 小时，过期后下次生成自动刷新
  └─ 管理员修改官方链接映射不影响 preparations 缓存（两者独立）
```

**关键点**
- preparations 缓存按"国家 + 城市集合"维度，与 guides 的"国家+城市+天数+偏好"缓存分离。
- 命中也**不跳过扣额度**——用户拿到的是新行程，preparations 只是复用文本。
- 城市集合为空（理论上不允许，行程必须选城市）时按国家维度缓存。

### 7.11 搜索缓存键与 TTL 规范化

```
缓存键：
  key = "guides:" + countryCode + ":" + sha256(cityCodes + "|" + days + "|" + normalizedPreference)

规范化：
  ├─ cityCodes：用户选择的城市代码排序后逗号拼接
  ├─ days：整数
  └─ normalizedPreference：
       trim → 转小写 → 去标点符号 → 按空白分词 → 去重 → 排序 → 拼接 → 截断 100 字符

TTL：24 小时（需求 BR-006 明确）
存储：Redis String，value 为 guides JSON

搜索日上限：
  key = "search:daily:{userId}:{yyyyMMdd}"
  INCR 后判断是否 > 50；超限则 guides 为空，不报错，不退回额度
  （50 为默认值，可通过 sys_config 的 search.daily-limit 调整）

降级：
  ├─ 搜索超时 5s → guides = []
  ├─ 搜索报错 → guides = []
  └─ 不因搜索失败退回额度
```

**关键点**：缓存键对偏好做归一化，避免同义输入命中不了；TTL 写死 24h；日上限防滥用，用户无感。

### 7.12 分享页白名单字段与签名 URL

```
令牌校验（GET /s/{token}）：
  ├─ 查 note_share：不存在 / revoked=1 / expire_at < now → 2007
  ├─ 查 travel_note：不存在或 deleted=1 → 2007
  └─ 查 user：status=DELETED → 2007

返回字段白名单：
  {
    "title": note.title,
    "summary": note.content 纯文本截取前 200 字,
    "coverImageUrl": 签名 URL,
    "images": [签名 URL...]（最多 6 张）,
    "countryCode": note.country_code,
    "countryName": 国家名称,
    "cityCode": note.city_code,
    "cityName": 城市名称,
    "createdAt": note.created_at,
    "nicknameMasked": 昵称脱敏（如"张*"）
  }

不返回：
  ├─ 正文全文
  ├─ 消费金额、tripId、旅行名称
  ├─ 用户邮箱、用户 ID
  └─ 其他游记或地图数据

签名 URL：
  └─ 有效期 = min(note_share 剩余有效期, 3600 秒)
      生成时调用 StorageProvider.presignDownload(objectKey, ttl)
```

**关键点**：白名单字段固定，不返回正文全文和花费；昵称脱敏；图片签名有效期与分享令牌对齐；令牌失效、游记删除、用户注销任一情况都返回 2007。

### 7.13 手动景点上限校验位置

```
前置说明：草稿页（AI 生成后未保存）没有 itineraryId，所有景点增删改排都在
  前端本地状态完成；只有「保存行程」时由 6.3 统一落库并校验上限。
  下面的接口校验只针对「已保存行程的编辑页」。

校验点：Service 层，在以下操作前统计当前行程手动景点数量：
  ├─ 新增手动景点（POST /days/{dayId}/spots）
  ├─ 保存行程（POST /itinerary/save，校验 days[] 中 MANUAL 总数）
  └─ 重新生成并保留手动景点（**调用 AI 之前**先预校验，见下）

统计 SQL：
  SELECT COUNT(*) FROM itinerary_spot s
  JOIN itinerary_day d ON s.day_id = d.id
  WHERE d.itinerary_id = ? AND s.source = 'MANUAL'
  -- 注意：itinerary_spot / itinerary_day 为物理删除，没有 deleted 字段

规则：
  ├─ 新增：count >= 30 → 返回 2006
  ├─ 保存：days[] 汇总 MANUAL > 30 → 返回 2006，整单拒绝
  ├─ 移动：不增加数量，不校验
  ├─ 编辑：不增加数量，不校验
  └─ 重新生成：**先预校验**——若 keepManualSpots=true 且当前 manual 数 > 30，
       在扣额度、调 AI 之前直接返回 2006，不消耗额度、不退回（因为根本没扣）
       （注意：合并后总数可能因新行程天数减少而等于原数，所以预校验只拦"原本已超 30"的情况）

并发：
  └─ 单人项目应用层校验足够；如需更强保证，可在事务内 SELECT ... FOR UPDATE
```

**关键点**：校验覆盖新增、保存、重新生成三个入口；**重新生成必须在扣费前预校验**，否则用户会为一个注定失败的请求白扣额度；移动/编辑不校验；错误码 2006。

### 7.14 国家侧边抽屉组装逻辑

```
接口：GET /api/map/country/{countryCode}

返回结构：
{
  "countryCode": "JP",
  "countryName": "日本",
  "status": "GREEN",
  "summary": {
    "itineraryCount": 3,
    "noteCount": 2,
    "totalExpenseCny": "12500.00",
    "hasUnconfiguredCurrency": false,
    "unconfiguredCurrencies": []
  },
  "trips": [
    {
      "tripId": 101,
      "tripName": "日本 · 2025-06",
      "startDate": "2025-06-01",
      "endDate": "2025-06-07",
      "subtotalCny": "8000.00",
      "cities": [
        {
          "cityCode": "TYO",
          "cityName": "东京",
          "cityLabel": "COMPLETED",
          "itineraries": [
            { "id": 1, "title": "东京5日", "startDate": "2025-06-01", "endDate": "2025-06-05", "expired": false }
          ],
          "notes": [
            { "id": 11, "title": "东京散步", "visibility": "PRIVATE", "createdAt": "2025-06-08" }
          ],
          "expenseCny": "5000.00"
        }
      ]
    }
  ],
  "ungrouped": {
    "itineraries": [
      {
        "id": 9,
        "title": "无归组行程",
        "startDate": "2025-04-01",
        "endDate": "2025-04-03",
        "expired": true,
        "cities": [
          { "cityCode": "TYO", "cityName": "东京" }
        ]
      }
    ],
    "notes": [],
    "expenses": [],
    "expenseCny": "4500.00"
  }
}

组装规则：
  ├─ status：与地图点亮一致（GREEN/YELLOW），只看有无游记/行程，不看旅行分组
  ├─ 多城市行程：按 itinerary_day.city_code 去重后，在该行程涉及的**每个城市分组下各展示一次**
  │    （同一行程出现在两个城市组中属正常，前端按 id 去重渲染链接即可）
  ├─ cityLabel：该城市下存在游记 → COMPLETED；无游记但有行程 → PLANNED；
  │    只有花费没有行程/游记 → 不返回该城市（花费通过 ungrouped 或旅行小计体现）
  ├─ trips 排序：按 start_date 倒序，最近旅行在前；无 start_date 按 created_at 倒序
  ├─ ungrouped：**trip_id IS NULL 的行程、游记和消费都要返回**；
  │    行程必须包含，否则"删除旅行后行程置空"会导致其从抽屉中消失
  ├─ 花费：totalExpenseCny / subtotalCny / expenseCny 按汇率快照折算
  ├─ unconfiguredCurrencies：与记账汇总口径一致，列出未配置汇率的币种
  └─ hasUnconfiguredCurrency：存在未配置汇率的币种时为 true，前端提示

隐私：
  ├─ 本接口仅本人可访问（JWT + user_id 行级隔离），tripName 只在此处返回
  ├─ 私密游记在本人抽屉里正常展示，带 visibility 字段
  ├─ 分享页（7.12）不复用本接口，分享页白名单不含 tripId、tripName、花费
  └─ 越权访问他人国家抽屉 → 4001

性能：
  └─ 一次请求内多次查询，P95 < 500ms；按 (user_id, country_code) 加索引
```

**关键点**：`tripName` 属于本人可见字段，分享页走独立接口且不含该字段；`ungrouped` 字段始终存在（空数组），避免前端判空。

### 7.15 记账汇总计算

```
接口：GET /api/expenses/summary?groupBy=country,city,trip,date

汇总维度（可多选，默认 country）：
  ├─ country：按国家汇总
  ├─ city：按城市汇总（城市为空归入"未指定城市"）
  ├─ trip：按旅行汇总（trip_id IS NULL 归入"未归组"）
  └─ date：按消费日期汇总（日粒度）

计算规则：
  ├─ 每笔消费用记录时 exchange_rate_snapshot 折算为 CNY；不使用当前汇率表
  ├─ 负数金额照常参与 SUM，自然冲减
  ├─ 软删除记录不参与汇总
  ├─ 未配置汇率快照的记录：不计入 CNY 合计，返回 hasUnconfiguredCurrency=true
  │   并在 unconfiguredCurrencies 列出币种
  └─ 分类小计按固定 7 类返回，未匹配归入"其他"

返回结构：
{
  "groupBy": ["country"],
  "currency": "CNY",
  "totalCny": "12500.00",
  "hasUnconfiguredCurrency": false,
  "unconfiguredCurrencies": [],
  "groups": [
    {
      "key": "JP",
      "label": "日本",
      "totalCny": "8000.00",
      "categories": [
        { "category": "TRANSPORT", "totalCny": "2000.00" },
        { "category": "FOOD", "totalCny": "1500.00" }
      ],
      "originalCurrencies": [
        { "currency": "JPY", "total": "120000.00" },
        { "currency": "CNY", "total": "500.00" }
      ]
    }
  ]
}

规则：
  ├─ groupBy 多维时，groups[].key 用 | 拼接，如 JP|TYO|101
  ├─ originalCurrencies 只在 groupBy 包含 country 或 trip 时返回
  └─ 汇总查询走手写 SQL，加 (user_id, deleted, country_code, expense_date) 索引
```

**关键点**：汇率快照**存值不存 ID**，汇率表变更不回溯历史；未配置汇率的记录单独标记，不静默丢弃。

**"补配置后自动纳入"的实现（必须做，否则 AC-16 无法通过）**

快照为 NULL 的行不会自己变，必须由管理员**新增**币种时回填：

```
PUT /api/admin/exchange-rates  (首次为某币种，即 INSERT 而非 UPDATE 时)
  │
  ├─ 1. 事务内插入 exchange_rate(currency, rate_to_cny)
  ├─ 2. 回填该币种的历史 NULL 快照：
  │      UPDATE expense
  │      SET exchange_rate_snapshot = ?
  │      WHERE currency = ? AND exchange_rate_snapshot IS NULL AND deleted = 0;
  │      -- amount_cny 是 STORED 生成列，会自动重算
  ├─ 3. 记录影响行数为 affectedUnconfiguredRecords，返回给前端
  └─ 4. 写 audit_log（旧值 NULL → 新值）
```

**修改**已有币种的汇率（UPDATE）时**不回填**——已保存的快照是历史事实，不得篡改。这样同时满足两条规则：

| 规则 | 实现 |
|---|---|
| 汇率变更不回溯历史汇总 | UPDATE 时不改 `exchange_rate_snapshot` |
| 补配置后自动纳入 | INSERT 新币种时回填 NULL 快照（那些记录此前从未被计入） |

**边界**：若管理员先配 JPY 汇率、用户后录入 JPY 消费，消费在保存时直接取当前汇率写快照，无需回填。

### 7.16 海报生成方案

```
生成方式：前端 DOM 截图 + 独立 Canvas 连线图 + 二维码库

流程：
  ├─ 1. 用户在海报生成页选择一篇自己的游记（默认最近一篇）
  ├─ 2. 调 POST /api/notes/{id}/share 确保分享令牌存在，拿到 token
  ├─ 3. 调 GET /api/map/stats 拿到 GREEN/YELLOW 国家集合
  ├─ 4. 隐藏 DOM 模板填充数据：
  │     ├─ 精选游记摘要（正文纯文本前 100 字，不含私密全文）
  │     ├─ 点亮国家集合/连线示意（Canvas 绘制后嵌入）
  │     └─ 二维码（qrcode 库生成，指向 /s/{token}）
  ├─ 5. html2canvas 截 DOM 为图片
  └─ 6. 用户保存图片分享

海报与分享页的关系：
  ├─ 两者分离：海报是静态图片，分享页是独立只读 URL
  ├─ 用户可只分享海报图片，也可只分享分享页链接；二维码固定指向分享页
  └─ 海报由用户主动点击"生成海报"触发，发布游记时不自动生成

连线图：
  ├─ 取 GREEN/YELLOW 国家，在世界地图缩略图上画点
  ├─ 按旅行时间顺序连线（不是 GPS 轨迹）
  └─ 用独立 Canvas 绘制，避免 html2canvas 对复杂 SVG/地图的兼容问题

不包含：
  ├─ 消费金额
  ├─ 私密全文
  ├─ 未公开照片
  └─ 复杂排版

后端不参与图片生成，2 核 4G 无压力。
```

### 7.17 多城市行程按天分配

```
输入：用户选择的城市列表 [cityA, cityB, ...]、总天数 N

生成前：
  ├─ 若只选了 1 个城市 → 所有天 cityCode 固定为该城市
  └─ 若选了多个城市 → prompt 中明确：
       "本次行程共 N 天，可选城市为 A、B、C；
        请按天分配城市，同一城市的天数尽量连续；
        每天 cityCode 必须属于上述集合。"

生成后校验：
  ├─ 每天 cityCode 必须属于用户选择的城市集合，否则视为校验失败
  ├─ 若 N >= 城市数，每个用户选择的城市至少出现 1 天；
  │   未出现的城市在返回中提示"城市X未被安排，可手动调整或重新生成"
  ├─ 相邻天城市变化次数不做强制限制，但 prompt 里建议尽量连续
  └─ 校验失败 → 重试 1 次；仍失败 → 退回额度，返回 2012

退化策略（兜底）：
  ├─ 模型未输出 cityCode / 输出空值 / 输出不在所选集合 / 连续两次校验失败
  │   → 回退为单城市生成（取用户所选城市中的第一个）并在前端提示
  ├─ 回退不报错、不退回额度（用户仍拿到了可用行程）
  └─ 前端提示文案："多城市生成不稳定，已按单城市（{城市名}）生成，可重新调整或分城市生成"
```

**关键点**：校验失败重试 1 次；仍失败则回退单城市并在前端提示，不报错也不退额度（与需求补丁一致）。

### 7.18 卡密批量生成规则

```
生成参数：
  ├─ type：AI_QUOTA / VIP
  ├─ value：AI 次数包为次数（如 100），VIP 为天数（如 30）
  ├─ expireAt：卡密自身有效期，可为空（空表示永久有效，直到被使用）
  ├─ quantity：单次生成数量，上限 500
  └─ prefix：可选前缀，如 TM（默认 "TM"）

编码规则：
  ├─ 格式：{prefix}-{16位随机码}
  ├─ 随机码用大写字母 + 数字，剔除易混淆字符（0/O、1/I/L）
  ├─ card_key.code 唯一索引；冲突则重新生成该条
  └─ 不包含业务含义，不暴露类型和面额

生成方式：
  ├─ 同一批次写入相同 batch_id，单事务批量插入 quantity 条，状态 UNUSED
  ├─ 生成成功后返回本次生成的卡密列表
  └─ 写 audit_log（操作人、类型、数量、面额、batch_id）

导出：
  ├─ GET /api/admin/cards/export?batchId= 返回 CSV
  ├─ 编码 UTF-8 with BOM（Excel 直接打开不乱码）
  ├─ 字段：code,type,value,expireAt,status,createdAt
  ├─ 文件名：cards_{batchId}_{yyyyMMddHHmmss}.csv
  └─ 导出操作写 audit_log

作废：
  ├─ 仅 UNUSED 可作废；已使用不可回滚
  └─ 作废后状态 VOID，导出和列表中可见
```

**关键点**：导出固定 CSV + UTF-8 BOM + 指定字段顺序；`batch_id` 让"按批次导出"可落库实现。

### 7.19 封禁/注销后的 Token 失效

```
JWT 过滤器流程：
  ├─ 1. 解析 token，取 userId
  ├─ 2. 查 Redis user:status:{userId}（TTL 30s）
  │     ├─ 命中 → 直接用
  │     └─ 未命中 → 查 user 表，回填缓存
  ├─ 3. status=BANNED → 返回 1003
  ├─ 4. status=DELETED → 返回 1007
  └─ 5. status=NORMAL → 放行

封禁/解封：
  ├─ 管理员 PUT /api/admin/users/{id}/status
  ├─ 更新 user.status
  ├─ 删除 Redis user:status:{userId}（主动失效，立即生效）
  └─ 写 audit_log

注销：
  └─ 同封禁，更新 status=DELETED 后删除缓存

改密 / 重置密码：
  └─ 同样 INCR tokenVersion 并删除 user:status 缓存，使该用户所有已签发 JWT 立即失效
     （需求 3.2「重置密码后需重新登录」由此实现）

Token 版本号兜底（防缓存击穿）：
  ├─ Redis user:tv:{userId} = tokenVersion（登录时签发）
  ├─ 封禁/注销时 INCR tokenVersion
  ├─ JWT 载荷带 tv 字段，过滤器比对 tv 与 Redis 值
  └─ 不一致 → 返回 1006（JWT 失效），强制重新登录

缓存不一致兜底：
  └─ 若 Redis 不可用，降级为每次查库（可接受，用户量小）；TTL 30s 仅作为缓存加速，不作为安全依据
```

**关键点**：从 60s 降到 30s 只解决"最坏延迟"，真正的安全保证是**主动删缓存 + tokenVersion 校验**；Token 版本号让封禁立即生效，不依赖缓存过期。

### 7.20 移动端地图手势方案

```
地图容器：
  ├─ 默认 touch-action: pan-y → 单指上下滑动时页面滚动，地图不拦截
  ├─ 地图区域顶部加"双指缩放"提示
  └─ 点击国家仍打开抽屉

双指手势：
  ├─ touchstart 检测到 2 个触点 → preventDefault 并进入缩放模式
  ├─ 缩放地图（ECharts 实例的缩放 API）
  └─ 双指结束 → 退出缩放模式，恢复页面滚动

冲突处理：
  ├─ 单指滑动：页面滚动优先，地图不拦截
  ├─ 双指：地图缩放优先，阻止页面滚动
  └─ 点击：正常触发国家点击

不做：
  ├─ 单指拖拽地图（V1 不支持，避免和页面滚动冲突）
  └─ 旋转、倾斜、惯性等高级手势（V2）
```

### 7.21 AI 取消接口设计

```
接口：POST /api/ai/cancel
鉴权：需要登录
入参：{ "requestId": "uuid" }
出参：{ "code": 0 }

处理：
  ├─ 1. 校验 requestId 格式
  ├─ 2. 校验该 requestId 属于当前用户（防止横向越权取消他人请求）
  │     实现：扣额度时写 quota:req:{userId}:{requestId}=1，取消时检查该键存在
  ├─ 3. SETEX cancel:{userId}:{requestId} 300 1（TTL 5 分钟，覆盖最长请求）
  └─ 4. 返回成功（无论请求是否已结束，幂等）

幂等：
  ├─ 重复取消同一 requestId → 直接返回成功
  └─ 取消不存在的 requestId（已过期/非本人）→ 仍返回成功，但不写标志
     （避免泄露"requestId 是否存在"的信息）

与流式循环的配合：
  └─ 每次写 chunk 前 GET cancel:{userId}:{requestId}
       ├─ 命中 → 停止生成，记录 cancelled=true，**不退回额度**
       └─ 未命中 → 继续

前端配合：
  ├─ "取消生成"按钮：先 POST /api/ai/cancel，再 abort fetch
  ├─ 仅关页面/断网：不发 cancel，服务端按"异常断连"处理
  └─ requestId 由前端生成（crypto.randomUUID）并随生成请求一起提交
```

**关键点**：取消接口**幂等**且**不泄露请求是否存在**；`requestId` 由前端生成并随请求提交，服务端在扣额度时登记归属，防止越权取消；取消信号只影响额度退回判定，不影响其他请求。

### 7.22 行程天数管理（增删天、改当天城市）

> 已保存行程的长度与城市可能随实际行程变化（临时多玩一天、改去另一座城市）。这些操作**不调 AI、不消耗额度、不改变地图点亮状态**。  
> **上限**：单条行程最多 15 天（与 AI 生成的上限一致）；至少保留 1 天。

```
新增一天  POST /api/itinerary/{id}/days
  入参：{ "insertIndex": 3, "date": "2025-06-04", "cityCode": "OSA" }
  处理：
    ├─ 校验行程归属、cityCode 属于该国家且 ENABLED
    ├─ 事务内：将 day_index >= insertIndex 的天后移一位（倒序更新，避免唯一键冲突）
    ├─ INSERT itinerary_day(day_index=insertIndex, visit_date, city_code)
    └─ UPDATE itinerary SET end_date = MAX(visit_date)
  出参：更新后的行程结构（同接口文档 6.4「行程详情」）

修改某天  PUT /api/itinerary/{id}/days/{dayId}
  入参：{ "date": "2025-06-04", "cityCode": "KYO" }
  处理：只改该天的日期与城市，景点原样保留；同步重算 itinerary.start/end_date
  注意：改城市不会删除该天已有景点（景点可能跨城，允许用户自行调整）

删除某天  DELETE /api/itinerary/{id}/days/{dayId}
  处理：
    ├─ 物理删除该天及其所有景点（子表本就物理删除）
    ├─ 将 day_index > 被删天的行前移一位（正序更新）
    ├─ 重算 itinerary.start_date / end_date；若删成 0 天 → 返回 2001，至少保留 1 天
    └─ 手动景点总数相应减少，无需额外校验
  出参：{ "itineraryId": 12, "deletedSpots": 4, "dayCount": 4, "startDate": "...", "endDate": "..." }

排序要点：
  ├─ 更新 day_index 时用「先改负数或倒序」避免 UNIQUE(itinerary_id, day_index) 冲突
  ├─ 推荐：倒序（从大到小）更新后移方向，正序（从小到大）更新前移方向
  └─ 全程在单个事务内，失败回滚
```

**关键点**
- 天数变化后 `itinerary.start_date/end_date` 必须同步重算，否则地图"计划已过期"判断会错。
- 与 `POST /regenerate` 的区别：本流程**只调整结构**，不重新生成内容、不扣额度；regenerate 会调 AI。
- 至少保留 1 天；若用户想清空，应删除整条行程。

---

## 8. 部署拓扑

### 8.1 生产环境（单机）

```
一台 2 核 4G 云服务器
├─ Nginx（80/443）
│   ├─ /            → 前端静态文件（dist）
│   ├─ /api/        → 反代 SpringBoot:8080
│   └─ /s/          → 反代 SpringBoot（分享页）
│        SSE 路由：proxy_buffering off
├─ SpringBoot（8080，堆内存 -Xmx1g）
├─ MySQL 8.0（3306，innodb_buffer_pool_size 约 512M）
├─ Redis 7（6379，maxmemory 256M，AOF 开启）
└─ 云 OSS（图片存储，签名 URL 访问）
```

### 8.2 本地开发

```
docker-compose（可选）：
├─ MySQL 8.0
├─ Redis 7
├─ MinIO（替代云 OSS）
└─ 邮件：默认关闭（验证码只写日志）；需真实发信时配置外部 SMTP（如 Resend）

本地启动：
├─ backend: mvn spring-boot:run
└─ frontend: npm run dev（Vite 代理 /api 到 8080）
```

### 8.3 备份与恢复
- MySQL 每日 `mysqldump`，同步到云 OSS；
- Redis 开启 AOF；
- RPO：数据库 ≤ 1 天，Redis ≤ 几秒；RTO：手工恢复 ≤ 2 小时；
- 开发阶段和上线前各演练一次恢复。

---

## 9. 通用设计约定

| 约定 | 内容 |
|---|---|
| 统一响应 | `{ code: 0, message: "ok", data: ... }`；错误码分段：1xxx 认证、2xxx 业务、3xxx 额度/卡密、4xxx 权限、5xxx 系统 |
| 鉴权 | JWT 放 `Authorization: Bearer`；过滤器统一校验 `user.status` + `tokenVersion`（Redis 缓存 30s，主动失效）；后台接口额外校验 `role=ADMIN` |
| 行级隔离 | 所有用户数据查询强制带 `user_id` 条件；越权返回 4001 |
| 软删除 | 业务表统一 `deleted` 字段；查询默认过滤；统计不计入 |
| 金额 | `DECIMAL(12,2)`，Java 用 `BigDecimal`，禁止 `double/float` |
| 时间 | 统一 `DATETIME`，后端用 `LocalDateTime`；API 返回 ISO 格式；额度按北京时间零点重置 |
| 分页 | `page` 从 1 开始，`size` 默认 10、上限 100；返回 `total`、`list` |
| 幂等 | 卡密兑换等写接口要求 `Idempotency-Key` 头；Redis SETNX + TTL 24h |
| 限流 | 登录、发邮件、AI 调用、分享页访问按 IP/用户限流，Redis 计数 |
| 日志 | 统一 traceId；脱敏密码、Token、API Key；AI 请求原文不落日志 |
| 异常 | 全局异常处理器；业务异常带错误码；不向前端暴露堆栈 |
| 上传 | 前端压缩 → 后端签名 → 直传 OSS；类型白名单、大小限制、剥离 EXIF；见 4.1 |
| 外链 | guides 和官方链接只允许 http/https，长度限制，前端新窗口打开并提示 |
| 数据访问 | 简单 CRUD 用 MyBatis-Plus；复杂统计手写 SQL/XML；实体用 Lombok 简化 |
| 前端 UI | Element Plus；后台管理页用其表格/表单；前台用卡片、抽屉、时间轴类组件 |
| 富文本 | TipTap；存储 HTML，后端 XSS 过滤后入库；润色用选区 API 取文本 |
| 配置 | 统一 `sys_config` 表；敏感字段 AES 加密；变更走 7.8 热加载机制 |
| 存储 | 统一 `StorageProvider` 接口；生产 OSS、本地 MinIO；签名 URL 有效期见 4.1 |
| 搜索 | 统一 `SearchProvider` 接口；实现内部超时 3s/5s；调用方统一降级；见 4.2 |
| 封禁/注销 | JWT 过滤器查 `user.status` + `tokenVersion`，封禁/注销后主动删缓存并 INCR 版本号，立即生效；见 7.19 |
| 角色与 VIP | `user.role` 只有 `USER`/`ADMIN`；**VIP 不作为角色落库**，由 `vip_expire_at > NOW()` 实时派生，接口返回 `plan` 字段。避免 VIP 到期后角色状态与过期时间不一致 |
| AI 取消 | 前端生成 `requestId`，取消时调 `/api/ai/cancel`；服务端 chunk 级检查；见 7.4、7.21 |
| AI 去重 | 同用户 60s 内相同参数返回首次结果，不二次扣费；见 7.1、7.2 |

---

## 10. 后续文档计划

| 文档 | 内容 | 时机 | 状态 |
|---|---|---|---|
| `docs/api.md` | P0 接口轻量契约：路径、方法、鉴权、请求/响应字段、错误码、示例 | 前后端并行前 | ✅ 已完成（见《接口文档 V1.0》） |
| `docs/db.md` | 完整表结构、索引、初始化数据（城市、汇率、清单项） | 建表时 | ✅ 已完成（见《数据库设计文档 V1.0》） |
| `README.md` | 本地开发启动步骤、docker-compose、环境变量说明 | 搭骨架时 | 待写 |
| `docs/deploy.md` | 环境要求、配置项、数据库初始化、构建启动、Nginx、备份恢复、管理员重置、上线检查清单 | 上线前 | 待写 |

---

## 11. 待补清单（V1 开工前不阻塞，实现到对应模块时必须补齐）

> 以下为四份文档交叉审查后发现的**设计真空**。它们不影响 P0 主体开工，但在实现到相应模块前必须给出结论，否则会留下实现分歧。

| # | 缺口 | 影响模块 | 建议方案 | 建议时机 |
|---|---|---|---|---|
| T1 | **敏感词过滤**无词表来源、存储、更新机制 | 游记 / AI 润色 | 词表放 `src/main/resources/sensitive-words.txt`（DFA 自动机加载），V1 只读不改；后台不提供维护界面 | 写游记模块前 |
| T2 | **登录失败锁定策略**只在 API 文档出现过（5 次锁 15 分钟），设计未落 | 认证 | 补充到 7.x：Redis 计数 `login:fail:{account}`，TTL 15 分钟，成功即清零 | 写认证模块前 |
| T3 | **管理员不可注销**未在接口层校验 | 账号 | `/user/logout-account` 校验 `role != ADMIN`，否则返回 `4002` | 写注销接口时 |
| T4 | **防重放攻击**只有幂等键，无 nonce/时间戳方案 | 全局 | 写接口要求 `Idempotency-Key`（已做）；JWT 有效期 7 天（已做）。V1 不做额外 nonce，接受风险并在文档注明 | 上线前确认 |
| T5 | **EXIF 剥离**依赖前端 canvas 重绘，服务端无法校验 | 上传 | V1 接受风险（前端 canvas 重绘天然剥离 EXIF）；在安全章节注明"直传 OSS 无法服务端二次校验"。V2 可加服务端转码 | 上线前确认 |
| T6 | `travel_note.visibility` 在 V1 无实际语义 | 游记 | 没有公开列表/发现功能，`SHARED` 仅作展示。建议 V1 保留字段但不作为权限依据（权限只看分享令牌），并在注释说明 | 写游记模块前 |
| T7 | 分享图片签名 URL 被截断为 3600s，与 SRS"与分享令牌一致"有偏差 | 分享 | 已确认：`min(令牌剩余, 3600)`。SRS 已注明，属合理偏差（避免长期有效的图片链接） | 已解决 |
| T8 | 城市 `sort_order` 初始化规则 | 城市 | 中国按行政区划代码升序，国外按"热门程度人工排序"（首都/一线在前）；管理员可后台调整 | 导入城市数据时 |
| T9 | 图片总量无上限，恶意用户可刷存储 | 上传 / 成本 | V1 加每用户每日上传 100 张上限（Redis 计数），超限返回 `5004`；不做容量配额 | 写上传模块前 |

---

## 12. 修订记录

| 版本 | 日期 | 修订内容 | 原因 |
|---|---|---|---|
| V1.0 | 初版 | 概要设计 + 关键详细设计 | 与需求封版对齐 |
| V1.0 修订版 | 第 1 次 | AI 取消机制（7.4/7.21）、60 秒生成去重（7.1/7.2）、preparations 24h 缓存（7.10.1）、多城市回退（7.17）、卡密 CSV（7.18）、汇率快照存值（7.15）、旅行隐私（7.14）、Token 版本号（7.19）、搜索日上限（7.11） | 需求补丁版下推 |
| V1.0 一致性修订 | 本次 | ① 新增 `country` 国家字典表；② 新增 `quota_pack` 批次表，解决次数包 365 天过期（7.2）；③ AI 生成去重改为「结果缓存 + 在途锁」，消除并发双扣（7.1）；④ 汇率新增时回填 NULL 快照，落实"补配置自动纳入"（7.15）；⑤ 新增行程增删天/改当天城市流程（7.22）与接口（5.4）；⑥ 补 `resend-verify`、`upload/presign`、`GET /expenses`、`countries`、城市/官方链接增删、`restore` 等接口；⑦ 错误码补齐 1008、2015–2026；⑧ 修正 7.6 地图 SQL 的 `MAX/MIN` 语义；⑨ 对象 Key 去掉不存在的 `noteId`；⑩ 取消事件统一为 `event: cancelled`；⑪ `itinerary.status` 只保留 `SAVED`；⑫ 补 `quota:req` 键；⑬ 新增第 11 章待补清单；⑭ **`role` 收敛为 `USER`/`ADMIN`，VIP 由 `vip_expire_at` 派生**；⑮ 行程天 API 字段统一为 `date`，DB 列 `visit_date` 用 `@TableField` 显式映射；⑯ 抽屉 `ungrouped` 补入**行程**，修复"删除旅行后行程从抽屉消失"；⑰ 明确多城市行程在每个涉足城市下各展示一次；⑱ 卡密兑换/管理调增**必须写入 `quota_pack` 批次**，并补 VIP 到期叠加公式；⑲ 注销清单补 `quota_pack` 与 `token_version`；⑳ 7.14 补 `unconfiguredCurrencies`；㉑ 7.22 明确单行程上限 15 天；㉒ 7.4 请求体与接口文档对齐；㉓ 7.5 补**跨国家挂载拦截**与**旅行日期区间派生维护**；㉔ 7.13 明确草稿页景点仅前端本地、重新生成在扣费前预校验、修正子表误用 `deleted`；㉕ 7.10 清单 upsert 改为 8.0 别名语法并复位 `deleted`；㉖ 7.19 补改密/重置密码同样使 JWT 失效 | 深度语义审查（第三轮 + 第四轮） |
| V1.1 认证验证码 | 2026-11-01 | 注册验证与找回密码改为 6 位邮箱验证码（Redis 存 HMAC 摘要、10 分钟、5 次试错、分层发信限流）；注册改为“注册前校验验证码”，删除 `verify-email`/`resend-verify`/`forgot-password`，新增 `/auth/send-code`；删除独立验证页；开发环境验证码只写日志；前端新增独立管理员登录入口与会话保持策略 | 认证体验对齐常见网站（change-06） |
| V1.1.1 邮件发信修复 | 2026-11-09 | ① SMTP 显式设置 `mail.smtp.localhost` 为发件人域名，避免 EHLO 反向 DNS 卡顿；② 验证码/重置密码发信同步等待上限由 10 秒放宽到 20 秒；③ 前端注册/找回密码对邮箱输入做 NFKC 归一化并去空白（全角 `＠`/`．` 容错） | 实测 Resend SMTP 建连认证耗时 16–21 秒，被 10 秒上限误判为发送失败 |
| V1.2 运营与安全能力 | 2026-11-10 | ① 模块划分补账号安全、内容治理、运营、监控与告警；② 5.10 补反馈/举报/公告/登录日志/监控/邮件/告警/OSS 测试接口；③ LLM 接入改为 OpenAI 兼容 HTTP 适配器；④ 补充固定北京时间约定 | 实现与基线同步（account-security / media-review / feedback / moderation / announcements / monitoring / alert / timezone） |

---

**文档状态：V1.0 设计文档（一致性修订版，与需求/接口/数据库文档对齐，可开工）**  
**后续动作：按《数据库设计文档》建表 → 按《接口文档》搭骨架 → 跑通"登录 → 生成行程 → 保存 → 地图点亮"最小链路。**
