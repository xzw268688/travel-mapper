# 🔌 接口文档：旅迹地图 V1.0（P0 契约）

> 基于《旅迹地图 V1.0 需求规格说明书（补丁版）》与《概要设计文档 V1.0（修订版）》编写。  
> 本文档是前后端并行开发的契约：字段名、错误码、状态码一旦确认，改动需同步更新本文档并 commit。  
> 范围：P0 全部接口 + P1 的记账与润色接口（设计文档已给出，一并纳入，便于前端提前排期）。  
> 不在本文档范围：后台 UI 交互细节、前端组件结构、数据库 DDL。

---

## 目录

1. 通用约定
2. 错误码
3. 认证与账号 `/auth`、`/user`
4. 国家、城市与旅行 `/countries`、`/cities`、`/trips`
5. 地图 `/map`
6. 行程与 AI `/itinerary`、`/ai`
7. 行前准备 `/prep`
8. 上传 `/upload`
9. 游记与分享 `/notes`、`/s`
10. 记账 `/expenses`
11. 卡密与额度 `/card`、`/quota`
12. 后台管理 `/admin`
13. SSE 事件协议
14. 枚举值汇总
15. 接口补充错误码
16. 修订记录
附录 A. 接口清单速查

---

## 1. 通用约定

### 1.1 基础信息

| 项 | 约定 |
|---|---|
| Base URL | `https://{domain}/api`（本地开发 `http://localhost:8080/api`） |
| 分享页 | `https://{domain}/s/{token}`（**不带** `/api` 前缀，返回 HTML 或由前端路由接管） |
| 传输 | 生产 HTTPS；本地 HTTP |
| 编码 | UTF-8 |
| Content-Type | `application/json`；文件上传走 OSS 直传，不经后端 |
| 时间格式 | `yyyy-MM-dd'T'HH:mm:ss`（无时区，服务端统一按 `Asia/Shanghai` 解释） |
| 日期格式 | `yyyy-MM-dd` |
| 金额格式 | **字符串**，保留两位小数，如 `"12500.00"`；避免 JS 浮点误差 |
| 字段映射 | API 用 camelCase，DB 用 snake_case，由 MyBatis-Plus 自动映射。**唯一例外**：行程天的 API 字段为 `date`，对应 DB 列 `visit_date`（`date` 是 MySQL 关键字），需在 DTO 上用 `@TableField("visit_date")` 显式指定 |

### 1.2 鉴权

```
Authorization: Bearer <JWT>
```

- JWT 载荷：`{ sub: userId, role: "USER"|"ADMIN", tv: tokenVersion, exp }`
- **VIP 不是角色**：平台角色只有 `USER` / `ADMIN`；是否 VIP 由服务端按 `vip_expire_at > NOW()` 实时派生，接口中以 `plan: "FREE"|"VIP"` 返回
- 有效期：7 天；无 refresh token（V1 简化，过期重新登录）
- 服务端每次请求校验 `user.status` 与 `tv`（Redis 缓存 30s，封禁/注销主动失效）
- 分享页 `/s/{token}` **不需要** JWT
- 后台接口 `/api/admin/**` 额外要求 `role=ADMIN`，否则返回 `4002`

### 1.3 统一响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "c3f1a2b4"
}
```

| 字段 | 说明 |
|---|---|
| `code` | 0 成功；非 0 见第 2 章错误码 |
| `message` | 面向用户的中文提示，前端可直接展示 |
| `data` | 业务数据；失败时为 `null` |
| `traceId` | 便于排查，写入服务端日志 |

**HTTP 状态码与 `code` 并存**：以 `code` 为准做业务判断，HTTP 状态码用于网关/监控。

### 1.4 分页

请求参数：

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `page` | int | 1 | 从 1 开始 |
| `size` | int | 10 | 上限 100 |

响应 `data`：

```json
{
  "total": 128,
  "page": 1,
  "size": 10,
  "list": []
}
```

### 1.5 幂等

需要幂等的写接口（当前仅卡密兑换）要求：

```
Idempotency-Key: <uuid>
```

- 同一个 Key + 同一用户，24 小时内重复提交返回首次结果
- 缺少该头 → `2001`（参数校验失败）
- 不同接口的 Key 互不影响，前端每次用户操作生成新 UUID

### 1.6 通用请求头

| 头 | 必需 | 说明 |
|---|---|---|
| `Authorization` | 是（除注册/登录/分享页） | `Bearer <JWT>` |
| `Idempotency-Key` | 卡密兑换必需 | 幂等键 |
| `X-Request-Id` | 否 | 前端可传，服务端优先使用，便于串联日志 |

### 1.7 通用校验规则

- 页码、ID 均为正整数
- 文本字段：`trim` 后校验；`null` 与空字符串视为未填
- URL 字段：必须 `http://` 或 `https://`，长度 ≤ 500
- 枚举字段：必须命中第 14 章枚举，否则 `2001`

---

## 2. 错误码

> 与设计文档第 6 章一致；本文档第 15 章补充的接口级错误码需同步回设计文档。

| 错误码 | 含义 | HTTP | 典型场景 |
|---|---|---|---|
| 0 | 成功 | 200 | — |
| 1001 | 用户名或密码错误 | 401 | 登录 |
| 1002 | 邮箱未验证 | 403 | 登录 |
| 1003 | 账号已被封禁 | 403 | 任意需登录接口 |
| 1004 | 验证码无效或已过期 | 400 | 邮箱验证、重置密码 |
| 1005 | 邮件发送过于频繁 | 429 | 注册、重发验证、找回密码 |
| 1006 | JWT 无效或过期 | 401 | Token 失效 |
| 1007 | 账号已注销 | 403 | 任意需登录接口 |
| 2001 | 参数校验失败 | 400 | 通用 |
| 2002 | 国家/城市不存在或已停用 | 400 | 城市选择、保存 |
| 2003 | 行程不存在或无权访问 | 404 | 行程相关 |
| 2004 | 游记不存在或无权访问 | 404 | 游记相关 |
| 2005 | 消费记录不存在或无权访问 | 404 | 记账相关 |
| 2006 | 手动景点已达上限 | 400 | 添加/合并手动景点 |
| 2007 | 分享令牌无效或已失效 | 410 | 分享页 |
| 2008 | 卡密不存在 | 404 | 兑换 |
| 2009 | 卡密已使用 | 409 | 兑换 |
| 2010 | 卡密已作废或过期 | 410 | 兑换 |
| 2011 | 额度耗尽 | 429 | AI 生成/润色/重新生成 |
| 2012 | AI 服务不可用 | 503 | 模型超时、报错、JSON 校验失败 |
| 2013 | 搜索服务不可用 | 503 | 搜索超时（**不阻断行程**，通常不出现在响应中） |
| 2014 | 未配置汇率 | 200 | 汇总提示（非阻断，`data` 内标记） |
| 3001 | 重复请求 / 相同请求正在处理 | 409 | 幂等键冲突；AI 生成去重在途（前端 2 秒后重试） |
| 3002 | 并发操作冲突 | 409 | 卡密并发兑换 |
| 4001 | 无权访问该资源 | 403 | 行级隔离越权 |
| 4002 | 需要管理员权限 | 403 | 访问后台 |
| 5001 | 系统内部错误 | 500 | 未捕获异常 |
| 5002 | 数据库操作失败 | 500 | DB 异常 |
| 5003 | 存储服务不可用 | 503 | OSS/MinIO 异常 |
| 5004 | 限流拦截 | 429 | 接口级限流 |

> 另有 `1008`、`2015`～`2026` 共 13 个业务码，覆盖注册查重、旅行/景点 404、游记大小限制、内容下架、城市引用等场景，明细见 **第 15 章**。

---

## 3. 认证与账号

### 3.1 注册

`POST /auth/register` ｜ 无需鉴权

**请求**

```json
{
  "username": "traveler",
  "email": "traveler@example.com",
  "password": "Abc123456",
  "nickname": "小旅",
  "agreeTerms": true
}
```

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| `username` | string | 是 | 4–20 位，字母/数字/下划线，唯一 |
| `email` | string | 是 | 合法邮箱，唯一 |
| `password` | string | 是 | 8–32 位，至少含字母与数字 |
| `nickname` | string | 否 | 1–20 字，缺省取 `username`（当前前端不传） |
| `agreeTerms` | boolean | 是 | 必须为 `true`，否则 `2001` |
| `code` | string | 是 | 邮箱验证码，6 位数字；由 `POST /auth/send-code` 的 `VERIFY` 场景获取 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": 12
  }
}
```

**说明**：

- 注册前必须先通过 `POST /auth/send-code`（`scene=VERIFY`）获取验证码。
- 服务端先校验用户名/邮箱唯一，再校验验证码；验证码有效才创建账号，账号创建即已激活。
- 验证码错误/过期返回 `1004`；用户名或邮箱重复返回 `1008` 且不消耗验证码。
- 注册成功后不再发送邮件。

**错误**：`2001`、`1005`、`1008`（用户名或邮箱已存在）

---

### 3.2 发送邮箱验证码

`POST /auth/send-code` ｜ 无需鉴权

**请求**

```json
{
  "email": "traveler@example.com",
  "scene": "VERIFY"
}
```

| 字段 | 说明 |
|---|---|
| `email` | 目标邮箱 |
| `scene` | `VERIFY`（注册验证）或 `RESET`（找回密码） |

**响应**：`data: { "emailSent": true }`

**错误**：`2001`（参数非法）、`1005`（60 秒间隔或每日 10 次超限）、`5004`（同一 IP 每小时 20 次超限）

**说明**：

- 验证码为 6 位数字，10 分钟内有效，同一邮箱同场景只保留最新一个。
- `VERIFY` 场景：未注册邮箱会收到验证码（注册前校验）；已注册且已验证的邮箱不发码。
- `RESET` 场景：只有存在且未注销的账号会收到验证码。
- 无论邮箱是否存在都返回成功，防止账号枚举。
- 开发环境（`MAIL_ENABLED=false`）验证码只打印在后端日志：`[auth-dev] scene=... email=... code=...`。

---

### 3.3 邮箱验证（已并入注册）

邮箱验证在 `POST /auth/register` 提交时完成，**不再提供独立的验证接口**。验证码错误、过期、已使用或试错超过 5 次时，注册接口返回 `1004`。

---

### 3.4 登录

`POST /auth/login` ｜ 无需鉴权

**请求**

```json
{
  "account": "traveler",
  "password": "Abc123456"
}
```

| 字段 | 说明 |
|---|---|
| `account` | 用户名或邮箱，二选一 |
| `password` | 明文传输（HTTPS 保护），服务端 BCrypt 比对 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "eyJhbGciOi...",
    "expiresIn": 604800,
    "user": {
      "id": 12,
      "username": "traveler",
      "nickname": "小旅",
      "avatarPreset": "preset_03",
      "role": "USER",
      "plan": "FREE",
      "vipExpireAt": null
    }
  }
}
```

**错误**：`1001`、`1002`（邮箱未验证）、`1003`（封禁）、`1007`（已注销）、`5004`（登录限流）

**说明**：连续失败 5 次锁定 15 分钟（Redis 计数），锁定期间返回 `1001` 或 `5004`。

---

### 3.5 找回密码

`POST /auth/send-code`（`scene=RESET`）发送验证码，见 3.2；随后调用 3.6 重置密码。

**说明**：无论邮箱是否存在都返回成功，防账号枚举。

---

### 3.6 重置密码

`POST /auth/reset-password` ｜ 无需鉴权

**请求**

```json
{
  "email": "traveler@example.com",
  "code": "123456",
  "newPassword": "NewAbc123456"
}
```

**响应**：`data: null`

**错误**：`1004`（验证码错误/过期/超次）、`2001`

**说明**：重置成功后该用户所有已签发 JWT 立即失效（`tokenVersion` +1），需重新登录。

---

### 3.7 退出登录

`POST /auth/logout` ｜ 需鉴权

**响应**：`data: null`

**说明**：JWT 无状态，服务端不改状态；前端清除本地 Token。可选实现：将当前 Token 加入 Redis 黑名单（TTL 剩余时长）。

---

### 3.8 获取当前用户信息

`GET /user/profile` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "username": "traveler",
    "email": "traveler@example.com",
    "nickname": "小旅",
    "avatarPreset": "preset_03",
    "role": "USER",
    "vipExpireAt": null,
    "createdAt": "2025-05-01T09:00:00",
    "quota": {
      "plan": "FREE",
      "freeUsedToday": 1,
      "freeLimit": 3,
      "packBalance": 50
    }
  }
}
```

---

### 3.9 修改个人资料

`PUT /user/profile` ｜ 需鉴权

**请求**

```json
{
  "nickname": "旅行家小旅",
  "avatarPreset": "preset_07"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `nickname` | 否 | 1–20 字 |
| `avatarPreset` | 否 | 必须属于系统预设集合（`preset_01`～`preset_12`） |

**响应**：返回更新后的 `user` 对象（同 3.8 的 `data`，不含 `quota`）

**错误**：`2001`（昵称为空或头像不在预设内）

---

### 3.10 账号注销

`POST /user/logout-account` ｜ 需鉴权

**请求**

```json
{
  "password": "Abc123456",
  "confirmText": "注销账号"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `password` | 是 | 二次确认密码 |
| `confirmText` | 是 | 必须精确等于 `注销账号` |

**响应**：`data: { "deletedAt": "2025-06-01T10:00:00" }`

**错误**：`1001`（密码错误）、`2001`（确认文案不符）、`4002`（管理员账号不开放注销）

**说明**

- **管理员账号不允许注销**：`role=ADMIN` 时直接返回 `4002`
- 事务内完成设计文档 7.9 的全部软删除与匿名化；任一失败整体回滚
- 成功后当前 Token 立即失效，后续请求返回 `1007`
- 所有分享令牌立即失效，分享页返回 `2007`
- 不可恢复

---

## 4. 城市与旅行

### 4.1 查询城市列表

`GET /cities?countryCode=JP` ｜ 需鉴权

**查询参数**

| 参数 | 必填 | 说明 |
|---|---|---|
| `countryCode` | 是 | ISO 3166-1 alpha-2，如 `JP`、`CN` |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "cityCode": "TYO",
      "name": "东京",
      "level": "MAJOR",
      "displayName": "东京（日本）"
    }
  ]
}
```

**说明**

- 只返回 `status=ENABLED` 的城市
- 某国无数据时返回空数组，前端提示"暂无可选城市，请联系管理员补充"
- 历史数据里已停用城市不通过本接口返回，但详情接口仍返回其名称

---

### 4.1.1 查询国家列表

`GET /countries` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "code": "JP", "nameZh": "日本", "nameEn": "Japan", "continent": "ASIA" },
    { "code": "TH", "nameZh": "泰国", "nameEn": "Thailand", "continent": "ASIA" }
  ]
}
```

**说明**

- 只返回 `status=ENABLED` 的国家，按 `sort_order` 排序
- 供国家下拉框使用；其他接口响应中的 `countryName` 由服务端从国家字典取，不依赖前端传参
- 国家字典由城市数据导入脚本一并生成

---

### 4.2 查询旅行列表

`GET /trips?countryCode=JP` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 101,
      "countryCode": "JP",
      "name": "日本 · 2025-06",
      "startDate": "2025-06-01",
      "endDate": "2025-06-07",
      "itineraryCount": 2,
      "noteCount": 1,
      "expenseCount": 12,
      "expenseCny": "8000.00",
      "createdAt": "2025-05-20T10:00:00"
    }
  ]
}
```

**说明**：按 `startDate` 倒序，为空时按 `createdAt` 倒序；只返回未删除旅行。归档由系统自动维护：同一国家两次行程日期间隔 ≤30 天时归为同一次归档；游记与消费按日期自动归类，找不到归档时自动新建（单日区间）。

---

### 4.2.1 归档状态

`PUT /trips/{id}/status` ｜ 需鉴权

```json
{ "status": "COMPLETED" }
```

**说明**：`PLANNED` / `COMPLETED`，默认 `PLANNED`；仅本人可改，他人返回 `2015`，非法值返回 `2001`。

---

### 4.2.2 时间线

`GET /trips/timeline` ｜ 需鉴权

返回本人全部归档，按起始日期正序（无日期用创建日期），字段包含名称、国家、日期、状态、行程/游记/消费数与花费。

---

### 4.3 旅行改名

`PUT /trips/{id}` ｜ 需鉴权

**请求**

```json
{ "name": "日本关西行" }
```

**响应**：`data: { "id": 101, "name": "日本关西行" }`

**错误**：`2015`（旅行不存在或无权）、`2001`（名称为空或超 30 字）

**说明**：改名不影响已关联的行程/游记/消费，它们通过 `trip_id` 关联。

---

### 4.4 删除旅行

`DELETE /trips/{id}?force=false` ｜ 需鉴权

**查询参数**

| 参数 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `force` | 否 | false | 存在挂载数据时必须为 `true` |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "tripId": 101,
    "detached": {
      "itineraries": 2,
      "notes": 1,
      "expenses": 12
    }
  }
}
```

**错误**：`2017`（存在挂载数据且 `force` 不为 `true`）、`2015`

---

### 4.6 旅行归组重算（管理员）

`POST /admin/trips/recluster` ｜ 管理员

按“国家 + 30 天窗口”重新聚类全部行程，合并旧旅行、重挂游记/消费，删除空旅行。

**响应**

```json
{ "itineraries": 120, "mergedTrips": 3, "deletedTrips": 2 }
```

**说明**：一次性维护接口，建议先备份数据库；会把未归组的游记/消费按日期归入或新建归档。普通用户返回 `4002`。

---

### 4.5 旅行详情聚合

`GET /trips/{id}` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 101,
    "name": "日本 · 2025-06",
    "countryCode": "JP",
    "countryName": "日本",
    "startDate": "2025-06-01",
    "endDate": "2025-06-07",
    "summary": {
      "itineraryCount": 2,
      "noteCount": 1,
      "expenseCount": 30,
      "expenseCny": "8000.00",
      "hasUnconfiguredCurrency": false,
      "unconfiguredCurrencies": []
    },
    "itineraries": [
      { "id": 201, "title": "东京三日", "startDate": "2025-06-01", "endDate": "2025-06-03",
        "status": "SAVED", "expired": true }
    ],
    "notes": [
      { "id": 301, "title": "东京散步", "visibility": "PRIVATE", "cityCode": "TYO",
        "cityName": "东京", "takedown": false, "createdAt": "2025-06-10T10:00:00" }
    ],
    "recentExpenses": [
      { "id": 401, "expenseDate": "2025-06-02", "category": "FOOD", "amount": "1200.00",
        "currency": "JPY", "amountCny": "57.60", "rateMissing": false, "remark": "拉面" }
    ]
  }
}
```

**说明**：

- 行程与游记返回该旅行下全部未删除记录；消费只返回最近 10 条。
- `summary.expenseCount` 与 `summary.expenseCny` 统计该旅行全部消费（含未返回的明细）。
- 未配置汇率的消费不计入合计，币种通过 `unconfiguredCurrencies` 返回。
- 非本人或不存在旅行返回 `2015`。

**说明**

- 删除旅行**不删除**挂载数据；其 `trip_id` 置 NULL，进入"未归组"
- 前端应先调 `force=false` 探测；收到 `2017` 后弹二次确认，用户确认后再用 `force=true` 重试

---

## 5. 地图

### 5.1 地图统计

`GET /map/stats` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "countries": [
      {
        "countryCode": "JP",
        "countryName": "日本",
        "status": "GREEN",
        "itineraryCount": 3,
        "noteCount": 2,
        "totalCount": 5,
        "expired": false
      },
      {
        "countryCode": "TH",
        "countryName": "泰国",
        "status": "YELLOW",
        "itineraryCount": 1,
        "noteCount": 0,
        "totalCount": 1,
        "expired": true
      }
    ]
  }
}
```

**说明**

- 未出现在 `countries` 里的国家 = 暗灰，前端按默认色渲染
- `status`：`GREEN`（有游记）> `YELLOW`（仅有行程）
- `expired=true` 表示该国**所有**行程都已过期且无游记，前端标"计划已过期"
- 同一国家多条行程/多次旅行只返回一条记录，`totalCount = itineraryCount + noteCount`
- 软删除、已注销用户数据不计入

---

### 5.2 国家侧边抽屉

`GET /map/country/{countryCode}` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
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
              {
                "id": 1,
                "title": "东京5日",
                "startDate": "2025-06-01",
                "endDate": "2025-06-05",
                "expired": false
              }
            ],
            "notes": [
              {
                "id": 11,
                "title": "东京散步",
                "visibility": "PRIVATE",
                "createdAt": "2025-06-08"
              }
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
      "notes": [
        { "id": 13, "title": "成田机场随笔", "visibility": "PRIVATE", "createdAt": "2025-06-09" }
      ],
      "expenses": [
        { "id": 31, "currency": "JPY", "amount": "1200.00", "expenseDate": "2025-06-09", "category": "TRANSPORT" }
      ],
      "expenseCny": "4500.00"
    }
  }
}
```

**说明**

| 字段 | 规则 |
|---|---|
| `status` | 与 5.1 一致 |
| `trips[].tripName` | **仅本接口返回**，分享页不返回 |
| `cityLabel` | 该城市有游记 → `COMPLETED`；仅有行程 → `PLANNED`；都无则不返回该城市（仅花费不构成城市分组） |
| `trips` 排序 | `startDate` 倒序 |
| `ungrouped` | `trip_id IS NULL` 的**行程、游记与消费**；无数据时返回空数组，字段不省略。行程必须包含，否则删除旅行后行程会从抽屉消失 |
| 多城市行程 | 按 `itinerary_day.cityCode` 去重后，在该行程涉及的**每个城市分组下各出现一次**（同一 `id` 可能重复出现，前端按 id 去重链接即可） |
| `hasUnconfiguredCurrency` | 存在未配置汇率的币种时为 `true`，前端提示"部分花费未计入" |
| 越权 | 只能查本人数据，不存在"查他人"入口；无数据时返回空结构而非 403 |

---

## 6. 行程与 AI

### 6.1 AI 生成行程

`POST /itinerary/generate` ｜ 需鉴权 ｜ **消耗 1 次 AI 额度**

**请求**

```json
{
  "requestId": "5f1c9e2a-3b7d-4c8e-9a10-2b3c4d5e6f70",
  "countryCode": "JP",
  "cityCodes": ["TYO", "OSA"],
  "days": 5,
  "startDate": "2025-06-01",
  "preference": "喜欢美食和寺庙，节奏慢一点"
}
```

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| `requestId` | string | 是 | UUID v4，前端生成，用于取消 |
| `countryCode` | string | 是 | ISO 2 位 |
| `cityCodes` | string[] | 是 | 1–5 个，必须均为 `ENABLED` 且属于该国家 |
| `days` | int | 是 | 1–15 |
| `startDate` | date | 是 | 不得早于昨天（允许今天） |
| `preference` | string | 否 | ≤ 200 字 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "requestId": "5f1c9e2a-3b7d-4c8e-9a10-2b3c4d5e6f70",
    "countryCode": "JP",
    "startDate": "2025-06-01",
    "endDate": "2025-06-05",
    "days": [
      {
        "dayIndex": 1,
        "date": "2025-06-01",
        "cityCode": "TYO",
        "cityName": "东京",
        "spots": [
          {
            "name": "浅草寺",
            "description": "东京最古老的寺庙，适合上午游览",
            "suggestedDuration": "2小时",
            "source": "AI"
          }
        ]
      }
    ],
    "guides": [
      { "title": "东京 5 日游攻略", "url": "https://example.com/tokyo", "source": "example.com" }
    ],
    "preparations": {
      "visa": "中国公民赴日旅游通常需办理签证，建议提前 1–2 个月准备",
      "currency": "日元 JPY，建议提前兑换少量现金",
      "powerPlug": "日本电压 100V，两脚扁插，建议带转换头",
      "bestSeason": "春季樱花季和秋季红叶季最佳",
      "notes": ["建议购买旅行保险", "下载离线地图和翻译 App"]
    },
    "warnings": [],
    "deduplicated": false,
    "quota": {
      "source": "FREE",
      "freeUsedToday": 2,
      "freeLimit": 3,
      "packBalance": 50
    }
  }
}
```

**响应头**

| 头 | 说明 |
|---|---|
| `X-Dedup` | `hit` 表示命中 60 秒去重，返回首次结果且未扣费；无此头表示正常生成 |

**关键说明**

- **草稿不落库**：返回结果只在内存与前端，用户点"保存行程"后才落库、才点亮地图
- **60 秒去重**：同用户、同国家+城市集合+天数+偏好文本，60 秒内重复请求返回首次结果，`deduplicated=true`，**不二次扣费**
- **并发去重**：若相同参数的请求**正在生成中**（尚未完成），后到的请求返回 `3001`，`message` 为"相同行程正在生成中，请稍候"。前端应展示提示并在 2 秒后自动重试一次。这避免两个并发请求都未命中去重缓存而双扣额度
- **搜索降级**：搜索超时/失败时 `guides` 为空数组，行程照常返回，不退回额度
- **多城市兜底**：模型无法稳定分配城市时回退为单城市生成，在 `warnings` 中说明，例如
  `["多城市生成不稳定，已按单城市（东京）生成，可重新调整或分城市生成"]`
- **城市未覆盖提示**：某所选城市未分配到任何一天时，`warnings` 追加
  `["城市「大阪」未被安排，可手动调整或重新生成"]`
- **preparations 缓存**：命中国家+城市缓存时仍正常扣 1 次额度（用户拿到的是新行程）

**错误**：`2011`（额度耗尽）、`2012`（AI 不可用，额度已自动退回）、`2002`（城市非法/停用）、`2001`

**HTTP 超时建议**：前端设置 90 秒；服务端 LLM 超时 30 秒、失败重试 1 次。

---

### 6.2 取消 AI 请求

`POST /ai/cancel` ｜ 需鉴权

**请求**

```json
{ "requestId": "5f1c9e2a-3b7d-4c8e-9a10-2b3c4d5e6f70" }
```

**响应**：`data: null`，`code: 0`

**说明**

- **幂等**：重复取消同一 `requestId` 始终返回成功
- 只对**当前用户**的 `requestId` 生效；他人的或不存在的一律返回成功，但不写取消标志（防信息泄露）
- 取消生效后：**不退回额度**
- 前端"取消生成"按钮应**先调用本接口，再 abort fetch**

**与断网的区别**

| 场景 | 服务端判定 | 额度 |
|---|---|---|
| 调用了本接口 | 主动取消 | 不退 |
| 未调用即断开，且未输出任何有效内容 | 异常断连 | **退回** |
| 未调用即断开，但已输出部分流式内容 | 部分交付 | 不退 |

---

### 6.3 保存行程

`POST /itinerary/save` ｜ 需鉴权

**请求**

```json
{
  "countryCode": "JP",
  "title": "东京大阪 5 日",
  "startDate": "2025-06-01",
  "endDate": "2025-06-05",
  "tripId": null,
  "tripName": "日本 · 2025-06",
  "guides": [
    { "title": "东京 5 日游攻略", "url": "https://example.com/tokyo", "source": "example.com" }
  ],
  "preparations": {
    "visa": "…",
    "currency": "…",
    "powerPlug": "…",
    "bestSeason": "…",
    "notes": []
  },
  "days": [
    {
      "dayIndex": 1,
      "date": "2025-06-01",
      "cityCode": "TYO",
      "spots": [
        { "name": "浅草寺", "description": "…", "suggestedDuration": "2小时", "source": "AI" },
        { "name": "我的私藏咖啡店", "description": "…", "suggestedDuration": "1小时", "source": "MANUAL" }
      ]
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `countryCode` | 是 | — |
| `title` | 是 | ≤ 50 字 |
| `startDate` / `endDate` | 是 | `endDate >= startDate`，天数须等于 `days.length` |
| `tripId` | 否 | 归入已有旅行；`null` 或省略 = 新建旅行 |
| `tripName` | 否 | 新建旅行时使用；缺省为 `"{国家名} · {yyyy-MM}"` |
| `guides` | 否 | 最多 3 条；后端复核 URL 合法性，非法则丢弃 |
| `preparations` | 否 | 五项结构；可为 `null` |
| `days[].cityCode` | 是 | 必须属于 `countryCode` 下的 ENABLED 城市 |
| `days[].spots` | 是 | 每天 0–10 个；总量内 `MANUAL` 最多 30 个 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "itineraryId": 12,
    "tripId": 101,
    "tripName": "日本 · 2025-06",
    "tripCreated": true,
    "countryStatusAfter": "YELLOW",
    "manualSpotCount": 1
  }
}
```

**说明**

- 首次保存且未传 `tripId` 时自动创建旅行，返回 `tripCreated=true`；新旅行的日期取本行程的 `startDate/endDate`
- 归入已有旅行时**校验 `trip.countryCode == countryCode`**，不一致返回 `2001`；挂载后重算旅行的日期区间
- 保存成功即点亮地图（`YELLOW`），返回 `countryStatusAfter` 供前端直接刷新
- `MANUAL` 景点总数 > 30 → `2006`，整单拒绝
- 同一天里 `spots` 的 `sortOrder` 按数组顺序写入

**错误**：`2001`（参数错误或旅行的国家与行程不一致）、`2002`、`2006`、`2015`（`tripId` 不属于本人）

---

### 6.4 行程详情

`GET /itinerary/{id}` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "tripId": 101,
    "tripName": "日本 · 2025-06",
    "countryCode": "JP",
    "countryName": "日本",
    "title": "东京大阪 5 日",
    "startDate": "2025-06-01",
    "endDate": "2025-06-05",
    "status": "SAVED",
    "expired": false,
    "guides": [],
    "preparations": { "visa": "…", "currency": "…", "powerPlug": "…", "bestSeason": "…", "notes": [] },
    "days": [
      {
        "dayId": 301,
        "dayIndex": 1,
        "date": "2025-06-01",
        "cityCode": "TYO",
        "cityName": "东京",
        "cityDisabled": false,
        "spots": [
          {
            "spotId": 9001,
            "sortOrder": 0,
            "source": "AI",
            "name": "浅草寺",
            "description": "…",
            "suggestedDuration": "2小时"
          }
        ]
      }
    ],
    "manualSpotCount": 1,
    "createdAt": "2025-05-20T10:00:00",
    "updatedAt": "2025-05-20T10:00:00"
  }
}
```

**说明**

- `cityDisabled=true` 表示该城市已被停用：历史展示正常，但前端编辑时应引导重新选择
- `expired` 由 `endDate < 今天` 计算，仅用于展示角标

**错误**：`2003`（不存在或非本人，统一 404 不区分）

---

### 6.5 编辑行程基本信息

`PUT /itinerary/{id}` ｜ 需鉴权

**请求**

```json
{
  "title": "东京大阪 6 日",
  "startDate": "2025-06-02",
  "endDate": "2025-06-07"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `title` | 否 | ≤ 50 字 |
| `startDate` | 否 | 修改后所有 `day.date` 按 `dayIndex` 平移 |
| `endDate` | 否 | **天数必须保持不变**；增删天请用 6.12～6.14 |

**响应**：返回完整行程结构（同 6.4）

**说明**

- **编辑不改变地图点亮状态**
- 修改日期只平移 `day.date`，不改变 `dayIndex`、城市、景点
- 需要增删天数或修改某天城市，使用 6.12 / 6.13 / 6.14，**不消耗 AI 额度**

**错误**：`2003`、`2001`

---

### 6.6 删除行程

`DELETE /itinerary/{id}` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "itineraryId": 12,
    "countryStatusAfter": "GRAY"
  }
}
```

**说明**

- 软删除；`countryStatusAfter` 为删除后该国的最新点亮状态（`GREEN` / `YELLOW` / `GRAY`），前端直接刷新地图
- 不删除旅行；若该行程是某旅行的最后一条，旅行仍保留在列表中

**错误**：`2003`

---

### 6.7 重新生成行程

`POST /itinerary/{id}/regenerate` ｜ 需鉴权 ｜ **消耗 1 次 AI 额度**

**请求**

```json
{
  "requestId": "7a2b...",
  "keepManualSpots": true,
  "preference": "多点美食，少走路"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `requestId` | 是 | 用于取消 |
| `keepManualSpots` | 是 | `true` 保留手动景点；`false` 全量覆盖 |
| `preference` | 否 | ≤ 200 字 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "itinerary": { },
    "mergedManualSpots": 3,
    "mergeWarnings": [
      "手动景点「咖啡店」原属城市大阪，已合并到城市东京的第 2 天，请确认",
      "手动景点「夜市」原属第 5 天，因新行程天数减少，已移至第 1 天"
    ],
    "warnings": [],
    "quota": { "source": "PACK", "freeUsedToday": 0, "freeLimit": 3, "packBalance": 49 }
  }
}
```

**说明**

- 重新生成**直接更新已保存行程**（保持 `itineraryId`、`tripId` 不变）
- 保留手动景点时按设计文档 7.7 的合并算法：同日同城追加 → 同日异城追加并提示 → 无该天则放第 1 天并提示
- **额度保护**：若 `keepManualSpots=true` 且当前行程已有 30 个手动景点，服务端在**扣额度之前**直接返回 `2006`，不调用 AI、不扣费
- 合并后若因新天数减少仍未超限则正常返回；合并结果超过 30 → `2006`，此时 AI 已调用，额度不退，前端应在调用前用 6.4 的 `manualSpotCount` 做预判
- 地图点亮状态不变

**错误**：`2003`、`2011`、`2012`、`2006`、`2001`

---

### 6.8 手动添加景点

`POST /itinerary/{id}/days/{dayId}/spots` ｜ 需鉴权 ｜ **不消耗 AI 额度**

> **仅用于已保存行程的编辑页。**
> 微调页（AI 生成后、未保存的草稿）**没有 `itineraryId`**，此时"添加景点/编辑/删除/排序/移动到某天"全部在前端本地状态完成，不调用任何接口；点"保存行程"时由 6.3 统一校验 30 个上限并落库。

**请求**

```json
{
  "name": "私藏咖啡店",
  "description": "表参道巷子里，手冲不错",
  "suggestedDuration": "1小时",
  "insertIndex": 2
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `name` | 是 | 1–50 字 |
| `description` | 否 | ≤ 200 字 |
| `suggestedDuration` | 否 | ≤ 20 字 |
| `insertIndex` | 否 | 插入位置；缺省追加到末尾 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "spotId": 9050,
    "dayId": 302,
    "sortOrder": 2,
    "source": "MANUAL",
    "manualSpotCount": 2
  }
}
```

**错误**：`2003`（行程/天不属于本人）、`2006`（手动景点已达 30 个）、`2001`

---

### 6.9 编辑景点

`PUT /itinerary/spots/{spotId}` ｜ 需鉴权

**请求**

```json
{
  "name": "表参道咖啡",
  "description": "…",
  "suggestedDuration": "1.5小时"
}
```

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "spotId": 9050,
    "source": "MANUAL",
    "name": "表参道咖啡"
  }
}
```

**说明**：AI 景点与手动景点都可编辑；编辑**不改变** `source`；不消耗额度、不校验上限。

**错误**：`2016`（景点不存在或无权）、`2001`

---

### 6.10 删除景点

`DELETE /itinerary/spots/{spotId}` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "spotId": 9050,
    "manualSpotCount": 1
  }
}
```

**说明**：软删除；删除后不退回任何额度；剩余景点 `sortOrder` 重新紧凑编号。

**错误**：`2016`

---

### 6.11 移动景点

`PUT /itinerary/spots/{spotId}/move` ｜ 需鉴权

**请求（跨天移动 + 指定位置）**

```json
{
  "targetDayId": 303,
  "targetIndex": 1
}
```

**请求（仅天内排序）**

```json
{
  "targetDayId": 302,
  "targetIndex": 0
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `targetDayId` | 是 | 目标天，必须属于同一行程 |
| `targetIndex` | 否 | 目标位置，从 0 开始；缺省追加到末尾 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "spotId": 9050,
    "fromDayId": 302,
    "toDayId": 303,
    "affectedDays": [
      { "dayId": 302, "spotIds": [9001, 9002] },
      { "dayId": 303, "spotIds": [9010, 9050, 9011] }
    ]
  }
}
```

**说明**

- 服务于"移动到第 X 天"菜单与天内拖拽排序
- **不支持跨天拖拽手势**（V2）；前端用菜单触发本接口即可
- 不改变 `source`，不消耗额度，不做上限校验

**错误**：`2016`、`2015`（`targetDayId` 不属于该行程）、`2001`

---

### 6.12 新增一天

`POST /itinerary/{id}/days` ｜ 需鉴权 ｜ **不消耗 AI 额度**

**请求**

```json
{
  "insertIndex": 3,
  "date": "2025-06-04",
  "cityCode": "OSA"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `insertIndex` | 是 | 插入位置，1 ≤ index ≤ 当前天数+1 |
| `date` | 是 | 该天日期；后端会按 `dayIndex` 与整体日期区间校验连续性 |
| `cityCode` | 是 | 必须属于该行程国家且为 ENABLED |

**响应**：返回完整行程结构（同 6.4），`endDate` 已重算

**说明**

- 插入位置之后的天数自动后移一位（`dayIndex` +1）
- 新天没有景点，`spots` 为空数组
- 不影响地图点亮状态、不计入手动景点上限
- 单条行程最多 15 天（与生成上限一致）

**错误**：`2003`、`2002`、`2001`（超过 15 天或日期不连续）

---

### 6.13 修改某天

`PUT /itinerary/{id}/days/{dayId}` ｜ 需鉴权 ｜ **不消耗 AI 额度**

**请求**

```json
{
  "date": "2025-06-04",
  "cityCode": "KYO"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `date` | 否 | 修改该天日期 |
| `cityCode` | 否 | 修改该天城市，需为 ENABLED |

**响应**：返回完整行程结构（同 6.4）

**说明**

- **改城市不会删除该天已有景点**，景点原样保留；如有需要用户自行调整
- 修改后重算 `itinerary.startDate/endDate`
- `dayIndex` 不变

**错误**：`2003`（行程无权）、`2016`（该天不存在）、`2002`、`2001`

---

### 6.14 删除某天

`DELETE /itinerary/{id}/days/{dayId}` ｜ 需鉴权 ｜ **不消耗 AI 额度**

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "itineraryId": 12,
    "deletedSpots": 4,
    "dayCount": 4,
    "startDate": "2025-06-01",
    "endDate": "2025-06-04"
  }
}
```

**说明**

- 该天及其所有景点一并物理删除
- 后续天数的 `dayIndex` 前移一位
- 重算 `startDate/endDate`
- **至少保留 1 天**；最后一天不允许删除（应删除整条行程）

**错误**：`2003`、`2016`、`2001`（尝试删除最后一天）

---

## 7. 行前准备

### 7.1 获取清单状态

`GET /prep/checklist?itineraryId=12` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "itineraryId": 12,
    "countryCode": "JP",
    "items": [
      { "itemKey": "VISA", "label": "签证已办", "checked": true },
      { "itemKey": "FLIGHT", "label": "机票已订", "checked": false },
      { "itemKey": "HOTEL", "label": "住宿已订", "checked": false },
      { "itemKey": "INSURANCE", "label": "保险已买", "checked": false },
      { "itemKey": "CURRENCY", "label": "货币已换", "checked": false },
      { "itemKey": "PLUG", "label": "插头转换器已备", "checked": false },
      { "itemKey": "SIM", "label": "电话卡/流量已办", "checked": false }
    ]
  }
}
```

**说明**

- 固定 7 项、固定顺序；**不提供自定义项**
- 未落库的项返回 `checked=false`
- 按"用户 + 国家 + 行程"隔离，越权访问返回 `2003`/`4001`

**错误**：`2003`

---

### 7.2 勾选/取消清单项

`PUT /prep/checklist` ｜ 需鉴权

**请求**

```json
{
  "itineraryId": 12,
  "itemKey": "VISA",
  "checked": true
}
```

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "itemKey": "VISA",
    "checked": true,
    "updatedAt": "2025-06-01T10:00:00"
  }
}
```

**说明**：upsert 语义；不消耗额度；不影响地图点亮。

**错误**：`2003`、`2022`（`itemKey` 不在固定枚举内）

---

### 7.3 获取官方链接

`GET /prep/official-links?countryCode=JP` ｜ 需鉴权

**响应（已配置）**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "countryCode": "JP",
    "fallback": false,
    "links": [
      { "title": "日本国驻华大使馆", "url": "https://www.cn.emb-japan.go.jp/" },
      { "title": "日本国家旅游局", "url": "https://www.jnto.go.jp/" }
    ]
  }
}
```

**响应（未配置兜底）**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "countryCode": "KH",
    "fallback": true,
    "links": [],
    "searchUrl": "https://www.bing.com/search?q=%E6%9F%AC%E5%9F%94%E5%AF%A8%20%E7%AD%BE%E8%AF%81%20%E5%AE%98%E6%96%B9"
  }
}
```

**说明**

- 每国最多 3 条，按 `sortOrder` 排序
- 未配置国家返回 `fallback=true` + `searchUrl`，前端展示"去搜索"按钮（新窗口打开并提示外部链接）
- 后端对链接做 http/https 与长度校验，不合格的链接不返回

---

## 8. 上传

### 8.1 获取上传签名

`POST /upload/presign` ｜ 需鉴权

**请求**

```json
{
  "bizType": "NOTE_IMAGE",
  "contentType": "image/jpeg",
  "size": 1834512
}
```

| 字段 | 必填 | 校验 |
|---|---|---|
| `bizType` | 是 | V1 仅 `NOTE_IMAGE` |
| `contentType` | 是 | 仅 `image/jpeg`、`image/png`、`image/webp`（**禁止 SVG**） |
| `size` | 是 | ≤ 5 MB（5242880 字节） |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "objectKey": "note/12/2025/06/4a7f2c9e-1b3d-4e5f-8a9b-0c1d2e3f4a5b.jpg",
    "uploadUrl": "https://bucket.oss-cn-hangzhou.aliyuncs.com/note/...?Signature=...",
    "method": "PUT",
    "headers": { "Content-Type": "image/jpeg" },
    "expireAt": "2025-06-01T10:05:00"
  }
}
```

**流程**

1. 前端调本接口拿 `uploadUrl`
2. 前端**直接 PUT** 文件到 `uploadUrl`（带返回的 `headers`）
3. 上传成功后，把 `objectKey` 写入游记的 `images[]`

**说明**

- 签名有效期 300 秒
- 不经过后端，节省 2 核 4G 的带宽与内存
- 前端仍应先做一次本地压缩与尺寸检查，降低失败率
- EXIF GPS 剥离在前端压缩阶段完成（canvas 重绘天然丢失 EXIF），后端无法二次校验

**错误**：`2021`（类型不支持）、`2019`（单图超 5MB）、`5003`（存储不可用）

---

## 9. 游记与分享

### 9.1 创建游记

`POST /notes` ｜ 需鉴权

**请求**

```json
{
  "title": "东京散步",
  "content": "<p>第一天去了浅草寺……</p>",
  "images": [
    "note/12/2025/06/a1.jpg",
    "note/12/2025/06/b2.jpg"
  ],
  "coverImage": "note/12/2025/06/a1.jpg",
  "countryCode": "JP",
  "cityCode": "TYO",
  "itineraryId": 12,
  "tripId": null,
  "visibility": "PRIVATE"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | 1–50 字 |
| `content` | 是 | HTML，≤ 200 KB（UTF-8 字节） |
| `images` | 否 | `objectKey` 数组，≤ 20 张 |
| `coverImage` | 否 | 必须是 `images` 之一；缺省取第一张 |
| `countryCode` | 是 | — |
| `cityCode` | 是 | 主城市；必须属于该国家的 ENABLED 城市（历史停用城市不可新选） |
| `itineraryId` | 否 | 关联行程时，国家/城市默认继承，`tripId` 自动继承 |
| `tripId` | 否 | 不关联行程时手动归组；`null` = 未归组 |
| `visibility` | 否 | 默认 `PRIVATE` |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 11,
    "tripId": 101,
    "tripInherited": true,
    "countryStatusAfter": "GREEN"
  }
}
```

**说明**

- 内容入库前做 XSS 过滤与敏感词过滤
- `itineraryId` 与 `tripId` 同时传时以 `itineraryId` 为准（自动继承国家、城市与 `tripId`）
- 手动归组 `tripId` 时校验该旅行的 `countryCode` 与游记一致，否则 `2001`
- 传了 `itineraryId` 但与 `countryCode`/`cityCode` 不一致时，以行程的值为准并忽略入参（避免脏数据）
- 发布即点亮地图绿色，返回 `countryStatusAfter` 供前端刷新
- **默认私密**：未分享时仅本人可见

**错误**：`2001`（缺国家/城市）、`2002`（城市非法/停用）、`2003`（行程无权）、`2018`（图片 > 20 张）、`2020`（正文 > 200KB）

---

### 9.2 游记详情

`GET /notes/{id}` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 11,
    "title": "东京散步",
    "content": "<p>…</p>",
    "images": [
      { "objectKey": "note/12/2025/06/a1.jpg", "url": "https://…签名URL…" }
    ],
    "coverImage": "note/12/2025/06/a1.jpg",
    "countryCode": "JP",
    "countryName": "日本",
    "cityCode": "TYO",
    "cityName": "东京",
    "cityDisabled": false,
    "itineraryId": 12,
    "tripId": 101,
    "tripName": "日本 · 2025-06",
    "visibility": "PRIVATE",
    "share": {
      "active": true,
      "token": "s_8f2c…",
      "url": "/s/s_8f2c…",
      "expireAt": "2025-06-08T10:00:00"
    },
    "takedown": false,
    "createdAt": "2025-06-08T10:00:00",
    "updatedAt": "2025-06-08T10:00:00"
  }
}
```

**说明**

- 非本人访问返回 `2004`（不区分"不存在"与"无权"，防枚举）
- `takedown=true` 表示已被管理员下架：本人可看、不可编辑、不可分享
- 图片 URL 为 600 秒签名

**错误**：`2004`

---

### 9.3 编辑游记

`PUT /notes/{id}` ｜ 需鉴权

**请求**：字段同 9.1，均为可选；`content`、`images`、`visibility` 至少传一项

**响应**：返回完整游记结构（同 9.2）

**错误**：`2004`、`2002`、`2018`、`2020`、`2024`（内容已下架，不可编辑）

---

### 9.4 删除游记

`DELETE /notes/{id}` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "noteId": 11,
    "countryStatusAfter": "YELLOW",
    "sharesRevoked": 2
  }
}
```

**说明**：软删除；同时撤销该游记的所有分享令牌；返回删除后国家点亮状态。

**错误**：`2004`

---

### 9.5 创建/更新分享

`POST /notes/{id}/share` ｜ 需鉴权

**请求**

```json
{ "expireDays": 7 }
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `expireDays` | 否 | `7`（默认）、`30`、`0`（永久） |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "s_8f2c1d9e0a7b",
    "url": "/s/s_8f2c1d9e0a7b",
    "fullUrl": "https://travel.example.com/s/s_8f2c1d9e0a7b",
    "expireAt": "2025-06-08T10:00:00",
    "created": true
  }
}
```

**说明**

- 已有有效令牌时复用并返回 `created=false`；已过期或被撤销则新建
- 内容已下架 → `2024`
- 撤销后需重新调用本接口生成新令牌

**错误**：`2004`、`2024`、`2001`

---

### 9.6 撤销分享

`DELETE /notes/{id}/share` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "revoked": 2
  }
}
```

**说明**：撤销该游记的**全部**分享令牌（含历史已过期的）。

**错误**：`2004`

---

### 9.7 只读分享页

`GET /s/{token}` ｜ **无需鉴权**

**响应**（`Content-Type: application/json`，由前端渲染；也可由服务端直接返回 HTML）

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "title": "东京散步",
    "summary": "第一天去了浅草寺，第二天去了表参道……",
    "coverImageUrl": "https://…签名URL…",
    "images": ["https://…签名URL…"],
    "countryName": "日本",
    "cityName": "东京",
    "createdAt": "2025-06-08T10:00:00",
    "nicknameMasked": "小*"
  }
}
```

**白名单说明**

**返回**：标题、摘要（纯文本前 200 字）、封面、最多 6 张图片、国家名、城市名、日期、脱敏昵称。

**不返回**：正文全文、消费金额、`tripId`、旅行名称、用户 ID、邮箱、其他游记。

**签名 URL 有效期**：`min(令牌剩余有效期, 3600 秒)`。

**失效场景**（统一返回 `2007`，页面展示"分享已失效"）

- 令牌不存在 / 已撤销 / 已过期
- 游记已软删除
- 游记被管理员下架
- 用户已注销

---

## 10. 记账

### 10.1 新增消费

`POST /expenses` ｜ 需鉴权

**请求**

```json
{
  "countryCode": "JP",
  "cityCode": "TYO",
  "itineraryId": 12,
  "tripId": null,
  "currency": "JPY",
  "amount": "3500.00",
  "category": "FOOD",
  "expenseDate": "2025-06-02",
  "remark": "一兰拉面"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `countryCode` | **是** | 必选 |
| `cityCode` | 否 | 可选；传了 `itineraryId` 时缺省带出行程城市 |
| `itineraryId` | 否 | 关联行程 → 自动继承 `tripId` |
| `tripId` | 否 | 不关联行程时手动归组；`null` = 未归组 |
| `currency` | 是 | ISO 4217，如 `JPY`、`CNY` |
| `amount` | 是 | 字符串，两位小数；**允许负数**表示退款 |
| `category` | 是 | 固定 7 类，见第 14 章 |
| `expenseDate` | 是 | 不得晚于今天 |
| `remark` | 否 | ≤ 100 字 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 8,
    "currency": "JPY",
    "amount": "3500.00",
    "exchangeRateSnapshot": "0.048000",
    "amountCny": "168.00",
    "rateMissing": false,
    "tripId": 101,
    "tripInherited": true
  }
}
```

**说明**

- 传了 `itineraryId` 时，国家/城市/`tripId` 以行程为准；与入参不一致时以行程为准（避免脏数据）
- 手动归组 `tripId` 时校验该旅行的 `countryCode` 与消费一致，否则 `2001`
- 保存时按当前汇率表写入 `exchange_rate_snapshot`（**存值不存 ID**）
- 未配置该币种汇率时：允许保存，`exchangeRateSnapshot=null`、`amountCny=null`、`rateMissing=true`，前端提示"未配置汇率，未计入总花费"
- `amount` 为负数时，`amountCny` 同步为负，汇总时自然冲减

**错误**：`2001`（缺国家/金额/分类）、`2002`（城市非法）、`2003`（行程无权）

---

### 10.2 编辑消费

`PUT /expenses/{id}` ｜ 需鉴权

**请求**：字段同 10.1，均为可选

**响应**：同 10.1

**说明**

- 币种或金额变更时**重新计算汇率快照**（以当前汇率表为准）
- 只改备注/分类/日期时保留原快照

**错误**：`2005`、`2002`、`2001`

---

### 10.3 删除消费

`DELETE /expenses/{id}` ｜ 需鉴权

**响应**：`data: { "id": 8 }`

**说明**：软删除，汇总实时反映。

**错误**：`2005`

---

### 10.4 消费列表

`GET /expenses?countryCode=JP&tripId=101&from=2025-06-01&to=2025-06-30&page=1&size=20` ｜ 需鉴权

**查询参数**

| 参数 | 必填 | 说明 |
|---|---|---|
| `countryCode` | 否 | 过滤国家 |
| `cityCode` | 否 | 过滤城市 |
| `tripId` | 否 | 过滤旅行；传 `null` 语义用 `ungrouped=true` |
| `ungrouped` | 否 | `true` 时仅查 `trip_id IS NULL` |
| `from` / `to` | 否 | 消费日期区间（含端点） |
| `category` | 否 | 分类过滤 |
| `page` / `size` | 否 | 分页 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 12,
    "page": 1,
    "size": 20,
    "list": [
      {
        "id": 8,
        "countryCode": "JP",
        "countryName": "日本",
        "cityCode": "TYO",
        "cityName": "东京",
        "tripId": 101,
        "currency": "JPY",
        "amount": "3500.00",
        "exchangeRateSnapshot": "0.048000",
        "amountCny": "168.00",
        "rateMissing": false,
        "category": "FOOD",
        "expenseDate": "2025-06-02",
        "remark": "一兰拉面",
        "createdAt": "2025-06-02T12:00:00"
      }
    ]
  }
}
```

---

### 10.5 消费汇总

`GET /expenses/summary?groupBy=country,city,trip,date&countryCode=JP&from=2025-06-01&to=2025-06-30` ｜ 需鉴权

**查询参数**

| 参数 | 必填 | 说明 |
|---|---|---|
| `groupBy` | 否 | 逗号分隔，可选 `country`、`city`、`trip`、`date`；默认 `country` |
| `countryCode` | 否 | 范围过滤 |
| `tripId` | 否 | 范围过滤 |
| `from` / `to` | 否 | 日期区间 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "groupBy": ["country"],
    "currency": "CNY",
    "totalCny": "12500.00",
    "hasUnconfiguredCurrency": true,
    "unconfiguredCurrencies": ["KRW"],
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
}
```

**说明**

- 多维时 `key` 用 `|` 拼接，如 `JP|TYO|101`
- 未配置汇率的记录不计入 `totalCny`，只在 `unconfiguredCurrencies` 中列出
- 负数金额参与 SUM
- 默认视图为"按国家汇总"；切"按旅行拆分"传 `groupBy=trip`

---

## 11. 卡密与额度

### 11.1 兑换卡密

`POST /card/redeem` ｜ 需鉴权 ｜ **需要 `Idempotency-Key`**

**请求头**

```
Idempotency-Key: 0b5c8e41-2f3a-4d5b-9c6e-7f8a9b0c1d2e
```

**请求**

```json
{ "code": "TM-7K2P-9QX4-RT8M" }
```

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "cardType": "VIP",
    "value": 30,
    "vipExpireAtAfter": "2025-07-01T10:00:00",
    "packBalanceAfter": 50,
    "idempotentReplay": false
  }
}
```

**说明**

- 同类型直接累加：VIP 加天数（未过期则叠加，已过期从当下起算）；次数包**新建一个 `quota_pack` 批次**（`expireAt = now + 365 天`，先进先出扣减）
- 重复提交同一 `Idempotency-Key` → `idempotentReplay=true`，返回首次结果
- 并发兑换同一卡密：仅一次成功，其余返回 `2009`
- 成功写 `audit_log`

**错误**：`2008`、`2009`、`2010`、`3001`、`3002`、`2001`（缺幂等头）

---

### 11.2 查询我的额度

`GET /quota/me` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "plan": "FREE",
    "vipExpireAt": null,
    "freeUsedToday": 2,
    "freeLimit": 3,
    "packBalance": 50,
    "packExpiringSoon": {
      "count": 20,
      "expireAt": "2025-07-01T00:00:00"
    },
    "vipDailyLimit": 200,
    "vipDailyUsed": 0,
    "resetAt": "2025-06-03T00:00:00"
  }
}
```

**说明**

- `plan`：`FREE` / `VIP`
- VIP 有效期内 `freeUsedToday` 不递增
- `packBalance` 为所有**未过期**批次剩余次数之和（批次过期自动不计入）
- `packExpiringSoon`：30 天内即将过期的批次汇总，无则返回 `null`；用于前端提醒"您有 N 次额度将于 X 过期"
- `resetAt` 为北京时间次日零点

---

## 12. 后台管理

> 全部接口需 `role=ADMIN`，否则 `4002`。所有写操作写 `audit_log`。

### 12.1 读取 LLM 配置

`GET /admin/config/llm`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "provider": "qwen",
    "baseUrl": "https://dashscope.aliyuncs.com/api/v1",
    "model": "qwen-max",
    "apiKeyMasked": "sk-****************abcd",
    "timeoutSeconds": 30,
    "updatedAt": "2025-05-28T10:00:00"
  }
}
```

### 12.2 更新 LLM 配置

`PUT /admin/config/llm`

**请求**

```json
{
  "provider": "kimi",
  "baseUrl": "https://api.moonshot.cn/v1",
  "model": "moonshot-v1-32k",
  "apiKey": "",
  "timeoutSeconds": 30
}
```

| 字段 | 说明 |
|---|---|
| `apiKey` | **留空表示不修改**；非空则 AES 加密后覆盖 |

**响应**：同 12.1（返回更新后的配置）

**说明**

- 保存前做连通性探测（可选）；失败返回 `2001` 或 `5003`，旧配置保留
- 保存成功立即热加载，无需重启（对应 AC-04）

**错误**：`2001`、`4002`

---

### 12.3 OSS 配置

`GET /admin/config/oss` ｜ `PUT /admin/config/oss`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "provider": "oss",
    "endpoint": "oss-cn-hangzhou.aliyuncs.com",
    "region": "cn-hangzhou",
    "bucket": "travelmapper",
    "accessKeyMasked": "LTAI****************",
    "secretKeyMasked": "********************",
    "pathStyle": false,
    "publicDomain": "",
    "updatedAt": "2025-05-28T10:00:00"
  }
}
```

**PUT 请求字段**：同上，`accessKey` / `secretKey` 留空不修改。

---

### 12.4 搜索配置

`GET /admin/config/search` ｜ `PUT /admin/config/search`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "provider": "tavily",
    "apiKeyMasked": "tvly-************",
    "dailyLimitPerUser": 50,
    "timeoutSeconds": 5,
    "updatedAt": "2025-05-28T10:00:00"
  }
}
```

---

### 12.5 批量生成卡密

`POST /admin/cards/generate`

**请求**

```json
{
  "type": "VIP",
  "value": 30,
  "expireAt": "2026-12-31T23:59:59",
  "quantity": 100,
  "prefix": "TM"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `type` | 是 | `AI_QUOTA` / `VIP` |
| `value` | 是 | 次数包为次数（1–100000）；VIP 为天数（1–3650） |
| `expireAt` | 否 | 卡密自身失效时间；缺省永久有效 |
| `quantity` | 是 | 1–500 |
| `prefix` | 否 | 默认 `TM`；2–6 位大写字母 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "batchId": "B20250601100000",
    "quantity": 100,
    "codes": ["TM-7K2P-9QX4-RT8M", "…"]
  }
}
```

**说明**

- 返回完整卡密列表（上限 500，可接受）
- 数量大时前端仍应引导走导出接口

**错误**：`2001`、`4002`

---

### 12.6 卡密列表

`GET /admin/cards?batchId=B20250601100000&status=UNUSED&page=1&size=20`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 100,
    "page": 1,
    "size": 20,
    "list": [
      {
        "id": 5001,
        "batchId": "B20250601100000",
        "code": "TM-7K2P-9QX4-RT8M",
        "type": "VIP",
        "value": 30,
        "status": "UNUSED",
        "expireAt": "2026-12-31T23:59:59",
        "usedBy": null,
        "usedAt": null,
        "createdAt": "2025-06-01T10:00:00"
      }
    ]
  }
}
```

### 12.7 导出卡密

`GET /admin/cards/export?batchId=B20250601100000`

**响应头**

```
Content-Type: text/csv; charset=utf-8
Content-Disposition: attachment; filename="cards_B20250601100000_20250601100000.csv"
```

**文件内容**（UTF-8 with BOM）

```csv
code,type,value,expireAt,status,createdAt
TM-7K2P-9QX4-RT8M,VIP,30,2026-12-31 23:59:59,UNUSED,2025-06-01 10:00:00
TM-A1B2-C3D4-E5F6,AI_QUOTA,100,2026-12-31 23:59:59,UNUSED,2025-06-01 10:00:00
```

**说明**：字段顺序固定；时间格式 `yyyy-MM-dd HH:mm:ss`；Excel 直接打开不乱码。

---

### 12.8 作废卡密

`PUT /admin/cards/{id}/void`

**响应**：`data: { "id": 5001, "status": "VOID" }`

**错误**：`2001`（已使用不可作废）、`2008`

---

### 12.9 用户列表

`GET /admin/users?keyword=traveler&status=NORMAL&page=1&size=20`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 100,
    "page": 1,
    "size": 20,
    "list": [
      {
        "id": 12,
        "username": "traveler",
        "nickname": "小旅",
        "email": "traveler@example.com",
        "role": "USER",
        "status": "NORMAL",
        "vipExpireAt": null,
        "freeUsedToday": 1,
        "packBalance": 50,
        "createdAt": "2025-05-01T09:00:00"
      }
    ]
  }
}
```

**说明**

- 已注销用户显示为 `username=deleted_12`、`nickname=已注销用户`、`status=DELETED`
- 按需求：后台**不可检索**已注销用户的内容，但用户列表仍可见注销状态（仅账号壳）

---

### 12.10 封禁/解封用户

`PUT /admin/users/{id}/status`

**请求**

```json
{ "status": "BANNED", "reason": "发布违规内容" }
```

| `status` | 说明 |
|---|---|
| `NORMAL` | 解封 |
| `BANNED` | 封禁 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": 12,
    "status": "BANNED",
    "tokenVersion": 4
  }
}
```

**说明**：封禁后 `tokenVersion` 自增，该用户所有已签发 JWT 立即失效（返回 `1006`）。

---

### 12.11 调整额度/VIP

`PUT /admin/users/{id}/quota`

**请求（增加次数包）**

```json
{ "type": "AI_QUOTA", "delta": 100, "reason": "活动补偿" }
```

**请求（增加 VIP 天数）**

```json
{ "type": "VIP", "delta": 30, "reason": "活动补偿" }
```

| 字段 | 说明 |
|---|---|
| `type` | `AI_QUOTA` / `VIP` |
| `delta` | 可正可负；负数为扣减，扣减后余额不得小于 0（否则返回 `2001`） |
| `reason` | 必填，写入审计日志 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": 12,
    "type": "AI_QUOTA",
    "packBalanceAfter": 150,
    "vipExpireAtAfter": null
  }
}
```

**说明**

- `AI_QUOTA` 正数调整会**新建一条 `quota_pack` 批次**（`expireAt = now + 365 天`），与卡密兑换同一套入账逻辑；负数从最早过期的批次扣减
- `VIP` 正数调整：`vipExpireAt = GREATEST(now, 现有到期时间) + delta 天`
- 两类调整都写 `quota_ledger`（`source=ADMIN`）与 `audit_log`

**错误**：`2001`（`reason` 为空 / 扣减后余额小于 0）、`4002`

---

### 12.12 城市维护

`GET /admin/cities?countryCode=JP&status=ENABLED&keyword=东`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 1,
    "page": 1,
    "size": 20,
    "list": [
      {
        "id": 301,
        "countryCode": "JP",
        "cityCode": "TYO",
        "name": "东京",
        "level": "MAJOR",
        "status": "ENABLED",
        "referenced": true,
        "createdAt": "2025-05-01T09:00:00"
      }
    ]
  }
}
```

**新增城市** `POST /admin/cities`

> `countryCode` 必须已存在于国家字典（V1 由初始化脚本按 ISO 3166-1 全量导入，后台不提供国家增删）；不存在时返回 `2002`。

```json
{
  "countryCode": "JP",
  "cityCode": "KYO",
  "name": "京都",
  "level": "MAJOR"
}
```

**修改城市** `PUT /admin/cities/{id}`

```json
{ "name": "京都市", "status": "DISABLED" }
```

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": { "id": 302, "status": "DISABLED", "referenced": false }
}
```

**说明**

- `referenced=true` 的城市**不允许物理删除**，只能 `DISABLED`
- 停用不影响历史数据展示；新建数据时不再出现在选择列表
- `(countryCode, cityCode)` 唯一

**删除城市** `DELETE /admin/cities/{id}`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": { "id": 302, "deleted": true }
}
```

**说明**：仅未被任何行程/游记/消费引用的城市可物理删除；已被引用时返回 `2025`，请改用 `PUT` 停用。

**错误**：`2001`、`2025`（城市已被引用，不能删除）、`4002`

---

### 12.13 官方链接维护

`GET /admin/official-links?countryCode=JP`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "id": 1, "countryCode": "JP", "title": "日本国驻华大使馆", "url": "https://www.cn.emb-japan.go.jp/", "sortOrder": 0 }
  ]
}
```

**新增** `POST /admin/official-links`

```json
{ "countryCode": "JP", "title": "日本国家旅游局", "url": "https://www.jnto.go.jp/", "sortOrder": 1 }
```

**修改** `PUT /admin/official-links/{id}`

**删除** `DELETE /admin/official-links/{id}`

**说明**：每国最多 3 条；URL 必须 http/https 且 ≤ 500 字符。

**错误**：`2026`（该国已有 3 条）、`2001`

---

### 12.14 汇率维护

`GET /admin/exchange-rates`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "id": 1, "currency": "JPY", "rateToCny": "0.048000", "updatedAt": "2025-06-01T09:00:00" }
  ]
}
```

**新增/更新** `PUT /admin/exchange-rates`

```json
{ "currency": "JPY", "rateToCny": "0.048000" }
```

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "currency": "JPY",
    "rateToCny": "0.048000",
    "previousRate": "0.048500",
    "affectedUnconfiguredRecords": 3
  }
}
```

**说明**

本接口同时承担"新增币种"与"修改币种"，两者行为不同：

| 场景 | 判定 | 对历史消费的影响 |
|---|---|---|
| **新增币种**（该 `currency` 此前不存在） | INSERT | **回填**：将该币种所有 `exchangeRateSnapshot IS NULL` 的消费写入本次汇率，使其自动纳入汇总 |
| **修改币种**（已存在） | UPDATE | **不回填**：已保存的快照是历史事实，不得篡改 |

- `affectedUnconfiguredRecords`：本次回填的消费条数（修改场景恒为 `0`）
- 汇率精度 6 位小数；变更写 `audit_log`
- 修改后**立即生效**，仅影响此后新录入的消费

**示例（新增币种）**

```json
// 请求
{ "currency": "KRW", "rateToCny": "0.005300" }

// 响应
{
  "code": 0,
  "message": "ok",
  "data": {
    "currency": "KRW",
    "rateToCny": "0.005300",
    "previousRate": null,
    "created": true,
    "affectedUnconfiguredRecords": 7
  }
}
```

**错误**：`2001`（币种非 3 位大写字母、汇率 ≤ 0）

---

### 12.15 内容下架

`PUT /admin/notes/{id}/takedown`

**请求**

```json
{ "reason": "内容违规" }
```

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "noteId": 11,
    "takedown": true,
    "sharesRevoked": 2
  }
}
```

**说明**

- 下架后：本人详情可见但标记"已下架"，不可编辑、不可分享
- **立即撤销所有分享令牌**，分享页返回"分享已失效"
- 写 `audit_log`

**错误**：`2004`

---

### 12.16 内容恢复

`PUT /admin/notes/{id}/restore`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "noteId": 11,
    "takedown": false
  }
}
```

**说明**

- 恢复后本人可正常编辑
- **原分享令牌仍然失效**，需用户重新生成分享（与设计文档 7.12 一致）

**错误**：`2004`

---

## 13. SSE 事件协议

### 13.1 AI 流式润色

`POST /ai/polish/stream` ｜ 需鉴权 ｜ **消耗 1 次 AI 额度**

**请求头**

```
Accept: text/event-stream
Authorization: Bearer <JWT>
Content-Type: application/json
```

**请求体**

```json
{
  "requestId": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
  "noteId": 11,
  "text": "要润色的选中文本",
  "style": "vivid",
  "target": "selected"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `requestId` | 是 | 前端生成，用于取消 |
| `noteId` | 否 | 上下文；仅用于校验归属 |
| `text` | 是 | 选中文本，1–5000 字 |
| `style` | 否 | `vivid`（生动）/ `concise`（简洁）/ `elegant`（优雅）；默认 `vivid` |
| `target` | 否 | `selected`（默认）/ `full`（全文润色） |

**响应**：`Content-Type: text/event-stream; charset=utf-8`

```
event: meta
data: {"requestId":"a1b2...","quotaSource":"FREE"}

event: delta
data: {"delta":"在"}

event: delta
data: {"delta":"东京的"}

event: delta
data: {"delta":"清晨，"}

event: done
data: {"requestId":"a1b2...","totalChars":128}
```

**取消场景**

```
event: cancelled
data: {"requestId":"a1b2..."}
```

**错误场景**

```
event: error
data: {"code":2012,"message":"AI 服务不可用，本次额度已退回"}
```

### 13.2 事件类型汇总

| event | 出现次数 | 说明 |
|---|---|---|
| `meta` | 0 或 1 | 首帧，返回 `requestId` 与扣费来源 |
| `delta` | 0..N | 文本增量，前端按序拼接 |
| `done` | 0 或 1 | 正常结束 |
| `cancelled` | 0 或 1 | 用户主动取消，**不退额度** |
| `error` | 0 或 1 | 异常结束；按规则退回额度 |

### 13.3 前端处理要点

- 用 `fetch` + `ReadableStream`，**不要**用 `EventSource`（无法带 `Authorization` 头、无法 POST）
- 按 `\n\n` 切分事件，按 `event:` / `data:` 解析
- 收到 `delta` 逐字拼接渲染，实现打字机效果
- 「取消生成」按钮：先 `POST /api/ai/cancel`，再 `AbortController.abort()`
- 断网/关闭页面：不发取消接口；服务端按 6.2 的规则判定是否退回额度
- Nginx 侧需配置：

```nginx
location /api/ai/polish/stream {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 120s;
}
```

---

## 14. 枚举值汇总

### 14.1 国家状态

| 值 | 含义 |
|---|---|
| `GRAY` | 暗灰（前端默认，接口不返回） |
| `YELLOW` | 计划中（有已保存行程，无游记） |
| `GREEN` | 已完成（有游记） |

### 14.2 城市

| 枚举 | 值 |
|---|---|
| `level` | `CITY`（地级市）/ `MAJOR`（主要城市） |
| `status` | `ENABLED` / `DISABLED` |
| `cityLabel` | `PLANNED`（计划中）/ `COMPLETED`（已完成） |

### 14.3 行程与景点

| 枚举 | 值 |
|---|---|
| `itinerary.status` | `SAVED`（V1 只持久化已保存行程；草稿不落库） |
| `spot.source` | `AI` / `MANUAL` |

### 14.4 行前清单项

| `itemKey` | `label` |
|---|---|
| `VISA` | 签证已办 |
| `FLIGHT` | 机票已订 |
| `HOTEL` | 住宿已订 |
| `INSURANCE` | 保险已买 |
| `CURRENCY` | 货币已换 |
| `PLUG` | 插头转换器已备 |
| `SIM` | 电话卡/流量已办 |

### 14.5 消费分类

| 值 | 含义 |
|---|---|
| `TRANSPORT` | 交通 |
| `ACCOMMODATION` | 住宿 |
| `FOOD` | 餐饮 |
| `TICKET` | 门票 |
| `SHOPPING` | 购物 |
| `COMMUNICATION` | 通讯 |
| `OTHER` | 其他 |

### 14.6 游记

| 枚举 | 值 |
|---|---|
| `visibility` | `PRIVATE` / `SHARED` |

### 14.7 卡密

| 枚举 | 值 |
|---|---|
| `type` | `AI_QUOTA` / `VIP` |
| `status` | `UNUSED` / `USED` / `VOID` / `EXPIRED` |

### 14.8 用户

| 枚举 | 值 |
|---|---|
| `role` | `USER` / `ADMIN`（VIP 不是角色） |
| `plan` | `FREE` / `VIP`（由 `vipExpireAt` 实时派生，不落库为角色） |
| `status` | `NORMAL` / `BANNED` / `DELETED` |

### 14.9 额度来源

| 值 | 含义 |
|---|---|
| `VIP` | VIP 无限次（仅受日上限约束） |
| `PACK` | AI 次数包 |
| `FREE` | 每日免费额度 |

### 14.10 图片与内容限制

| 项 | 限制 |
|---|---|
| 单图大小 | ≤ 5 MB |
| 图片格式 | `image/jpeg`、`image/png`、`image/webp` |
| 禁止格式 | SVG 及所有非图片类型 |
| 每篇游记图片数 | ≤ 20 |
| 正文大小 | ≤ 200 KB |
| 分享页图片数 | ≤ 6（后端截断） |
| 手动景点/行程 | ≤ 30 |
| 每天景点数 | 生成时 ≤ 6；保存时 ≤ 10 |

---

## 15. 接口补充错误码

> 以下错误码原由本文档补充定义，**现已全部合并进设计文档第 6 章**（一致性修订版）。此处保留明细，供前端查阅每个码具体在哪些接口出现。

| 错误码 | 含义 | HTTP | 触发接口 |
|---|---|---|---|
| 1008 | 用户名或邮箱已存在 | 409 | `POST /auth/register` |
| 2015 | 旅行不存在或无权访问 | 404 | `/trips/*`、`POST /itinerary/save` |
| 2016 | 景点不存在或无权访问 | 404 | `/itinerary/spots/*` |
| 2017 | 旅行下存在挂载数据，需 `force=true` | 409 | `DELETE /trips/{id}` |
| 2018 | 图片数量超限（> 20 张） | 400 | `POST/PUT /notes` |
| 2019 | 单图超限（> 5 MB） | 400 | `POST /upload/presign` |
| 2020 | 正文超限（> 200 KB） | 400 | `POST/PUT /notes` |
| 2021 | 不支持的图片类型 | 400 | `POST /upload/presign` |
| 2022 | 清单项不在固定枚举内 | 400 | `PUT /prep/checklist` |
| 2023 | 分享令牌创建失败 | 500 | `POST /notes/{id}/share` |
| 2024 | 内容已下架，不可操作 | 403 | `PUT /notes/{id}`、`POST /notes/{id}/share` |
| 2025 | 城市已被引用，不能物理删除 | 409 | `DELETE /admin/cities/{id}` |
| 2026 | 该国官方链接已达上限（3 条） | 400 | `POST /admin/official-links` |

---

## 附录 A：接口清单速查

| 模块 | 方法 | 路径 | 鉴权 | 额度 |
|---|---|---|---|---|
| 认证 | POST | `/auth/register` | 否 | — |
| 认证 | POST | `/auth/send-code` | 否 | — |
| 认证 | POST | `/auth/login` | 否 | — |
| 认证 | POST | `/auth/reset-password` | 否 | — |
| 认证 | POST | `/auth/logout` | 是 | — |
| 用户 | GET | `/user/profile` | 是 | — |
| 用户 | PUT | `/user/profile` | 是 | — |
| 用户 | POST | `/user/logout-account` | 是 | — |
| 国家 | GET | `/countries` | 是 | — |
| 城市 | GET | `/cities` | 是 | — |
| 旅行 | GET | `/trips` | 是 | — |
| 旅行 | GET | `/trips/timeline` | 是 | 时间线 |
| 旅行 | PUT | `/trips/{id}/status` | 是 | 计划中/已完成 |
| 旅行 | GET | `/trips/{id}` | 是 | 详情聚合（行程/游记/最近消费） |
| 旅行 | PUT | `/trips/{id}` | 是 | — |
| 旅行 | DELETE | `/trips/{id}` | 是 | — |
| 地图 | GET | `/map/stats` | 是 | — |
| 地图 | GET | `/map/country/{countryCode}` | 是 | — |
| 行程 | POST | `/itinerary/generate` | 是 | **1 次** |
| 行程 | POST | `/ai/cancel` | 是 | — |
| 行程 | POST | `/itinerary/save` | 是 | — |
| 行程 | GET | `/itinerary/{id}` | 是 | — |
| 行程 | PUT | `/itinerary/{id}` | 是 | — |
| 行程 | DELETE | `/itinerary/{id}` | 是 | — |
| 行程 | POST | `/itinerary/{id}/regenerate` | 是 | **1 次** |
| 行程 | POST | `/itinerary/{id}/days/{dayId}/spots` | 是 | — |
| 行程 | PUT | `/itinerary/spots/{spotId}` | 是 | — |
| 行程 | DELETE | `/itinerary/spots/{spotId}` | 是 | — |
| 行程 | PUT | `/itinerary/spots/{spotId}/move` | 是 | — |
| 行程 | POST | `/itinerary/{id}/days` | 是 | — |
| 行程 | PUT | `/itinerary/{id}/days/{dayId}` | 是 | — |
| 行程 | DELETE | `/itinerary/{id}/days/{dayId}` | 是 | — |
| 行前 | GET | `/prep/checklist` | 是 | — |
| 行前 | PUT | `/prep/checklist` | 是 | — |
| 行前 | GET | `/prep/official-links` | 是 | — |
| 上传 | POST | `/upload/presign` | 是 | — |
| 游记 | POST | `/notes` | 是 | — |
| 游记 | GET | `/notes/{id}` | 是 | — |
| 游记 | PUT | `/notes/{id}` | 是 | — |
| 游记 | DELETE | `/notes/{id}` | 是 | — |
| 游记 | POST | `/notes/{id}/share` | 是 | — |
| 游记 | DELETE | `/notes/{id}/share` | 是 | — |
| 分享 | GET | `/s/{token}` | **否** | — |
| 润色 | POST | `/ai/polish/stream` | 是 | **1 次** |
| 记账 | POST | `/expenses` | 是 | — |
| 记账 | GET | `/expenses` | 是 | — |
| 记账 | PUT | `/expenses/{id}` | 是 | — |
| 记账 | DELETE | `/expenses/{id}` | 是 | — |
| 记账 | GET | `/expenses/summary` | 是 | — |
| 卡密 | POST | `/card/redeem` | 是 | — |
| 额度 | GET | `/quota/me` | 是 | — |
| 后台 | GET/PUT | `/admin/config/llm` | 管理员 | — |
| 后台 | GET/PUT | `/admin/config/oss` | 管理员 | — |
| 后台 | GET/PUT | `/admin/config/search` | 管理员 | — |
| 后台 | POST | `/admin/cards/generate` | 管理员 | — |
| 后台 | GET | `/admin/cards` | 管理员 | — |
| 后台 | GET | `/admin/cards/export` | 管理员 | — |
| 后台 | PUT | `/admin/cards/{id}/void` | 管理员 | — |
| 后台 | POST | `/admin/trips/recluster` | 管理员 | — |
| 后台 | GET | `/admin/users` | 管理员 | — |
| 后台 | PUT | `/admin/users/{id}/status` | 管理员 | — |
| 后台 | PUT | `/admin/users/{id}/quota` | 管理员 | — |
| 后台 | GET/POST | `/admin/cities` | 管理员 | — |
| 后台 | PUT | `/admin/cities/{id}` | 管理员 | — |
| 后台 | GET/POST | `/admin/official-links` | 管理员 | — |
| 后台 | PUT/DELETE | `/admin/official-links/{id}` | 管理员 | — |
| 后台 | GET/PUT | `/admin/exchange-rates` | 管理员 | — |
| 后台 | PUT | `/admin/notes/{id}/takedown` | 管理员 | — |
| 后台 | DELETE | `/admin/cities/{id}` | 管理员 | — |
| 后台 | PUT | `/admin/notes/{id}/restore` | 管理员 | — |

---

## 16. 修订记录

| 版本 | 修订内容 | 原因 |
|---|---|---|
| V1.0 | 初版：68 个接口 + 错误码 + SSE 协议 + 枚举 + 速查表 | 设计文档第 10 章计划 |
| V1.4 归档状态与时间线 | 新增 `trip.status`（PLANNED/COMPLETED）与 `PUT /trips/{id}/status`；新增 `GET /trips/timeline` | 手动确认归档状态并按时间展示（change-14） |
| V1.3 自动归组 | 行程按“同国家 + 30 天间隔”自动归组；游记/消费按日期自动归类，移除手动选择；新增 `POST /admin/trips/recluster` | 归组不再需要用户理解与操作（change-11） |
| V1.2 旅行聚合 | 新增 `GET /trips/{id}` 旅行详情聚合接口；记账/游记/行程支持带旅行上下文 | 行程、游记、记账同属一次旅行（change-09） |
| V1.1 认证验证码 | 注册验证与找回密码由长令牌改为 6 位邮箱验证码：新增 `POST /auth/send-code`；注册接口增加 `code`（校验通过才创建已激活账号）；`reset-password` 改为 `{email, code, newPassword}`；删除 `/auth/verify-email`、`/auth/resend-verify` 与 `/auth/forgot-password`；注册响应改为 `{userId}`；`1004` 文案改为“验证码无效或已过期” | 认证体验对齐常见网站（change-06） |
| 一致性修订 | ① 补 `GET /countries`、`/itinerary/{id}/days` 增删改、`DELETE /admin/cities/{id}`；② 6.5 引用编号 6.9→6.7；③ 抽屉 `ungrouped` 补入**行程**，明确多城市行程在每个城市下各出现一次；④ `expenses` 列表接口纳入；⑤ 修正 `date` 字段命名（原混用 `visitDate`）；⑥ 登录响应补 `plan`，明确 VIP 不是角色；⑦ 12.11 明确调增写入 `quota_pack`；⑧ 12.14 区分"新增币种回填"与"修改币种不回填"；⑨ 6.7 明确扣费前预校验手动景点上限；⑩ 6.8 明确草稿页不调接口；⑪ 6.3/9.1/10.1 补跨国家挂载校验与行程优先规则 | 四份文档交叉审查与深度语义校验 |

---

**文档状态：V1.0 接口契约（P0 完整，P1 记账/润色已纳入；一致性修订版）**  
**变更要求**：字段名、路径、错误码的任何改动都必须同步更新本文档并 commit；新增错误码同时回写设计文档第 6 章。  
**下一步**：按本契约搭骨架，先跑通「登录 → 生成行程 → 保存 → 地图点亮」最小链路。
