# 🔌 接口文档：旅迹地图 V1.0（P0 契约）

> 基于《旅迹地图 V1.0 需求规格说明书（补丁版）》与《概要设计文档 V1.0（修订版）》编写。  
> 本文档是前后端并行开发的契约：字段名、错误码、状态码一旦确认，改动需同步更新本文档并 commit。  
> 范围：P0 全部接口 + P1 的记账与润色接口（设计文档已给出，一并纳入，便于前端提前排期）。  
> 不在本文档范围：后台 UI 交互细节、前端组件结构、数据库 DDL。

---

## 目录

1. 通用约定
2. 错误码
3. 认证与账号 `/auth`、`/user`、`/feedback`
4. 国家、城市与旅行 `/countries`、`/cities`、`/trips`
5. 地图与攻略 `/map`、`/country-guide`
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
| 2031 | 反馈不存在 | 404 | 反馈详情/处理 |
| 2032 | 举报不存在 | 404 | 举报详情/处理 |
| 2033 | 汇率数据源不可用 | 503 | 汇率一键更新 |
| 2034 | 公告不存在或未生效 | 404 | 用户公告详情/已读 |
| 5001 | 系统内部错误 | 500 | 未捕获异常 |
| 5002 | 数据库操作失败 | 500 | DB 异常 |
| 5003 | 存储服务不可用 | 503 | OSS/MinIO 异常 |
| 5004 | 限流拦截 | 429 | 接口级限流 |

> 另有 `1008`、`2015`～`2034` 等业务码，覆盖注册查重、旅行/景点 404、游记大小限制、内容下架、城市引用、反馈/举报/公告不存在等场景，明细见 **第 15 章**。

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

**说明**：连续失败 5 次锁定 15 分钟（Redis 计数），锁定期间返回 `1001` 或 `5004`。每次登录尝试（成功或失败）都会写入登录日志，可由管理员在 12.27 查看；登录日志写入失败不影响登录结果。

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

### 3.11 提交反馈

`POST /feedback` ｜ 需鉴权

**请求**

```json
{
  "type": "BUG",
  "title": "地图按钮无响应",
  "content": "在首页点击地点标记后，右侧抽屉没有打开，刷新后仍然如此。",
  "contact": "me@example.com",
  "pagePath": "/map",
  "appVersion": "0.1.0"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `type` | 是 | `FEATURE` / `BUG` / `UX` / `OTHER` |
| `title` | 是 | 1–50 字；入库前经过敏感词过滤 |
| `content` | 是 | 10–1000 字；入库前经过敏感词过滤 |
| `contact` | 否 | 联系方式，≤100 字 |
| `pagePath` | 否 | 提交时所在页面路径，≤255 字 |
| `appVersion` | 否 | 前端版本号，≤32 字 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "type": "BUG",
    "title": "地图按钮无响应",
    "content": "在首页点击地点标记后，右侧抽屉没有打开，刷新后仍然如此。",
    "contact": "me@example.com",
    "status": "NEW",
    "createdAt": "2025-06-01T10:00:00"
  }
}
```

**错误**：`1006`（未登录）、`2001`（字段校验失败）、`5004`（当天已提交 5 条）

**说明**

- 浏览器 UA 由服务端从 `User-Agent` 请求头记录，前端无需传
- 每用户每个自然日最多成功提交 5 条
- 新反馈状态固定为 `NEW`

---

### 3.12 我的反馈

`GET /feedback/mine?page=1&size=10` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 1,
    "page": 1,
    "size": 10,
    "list": [
      {
        "id": 12,
        "type": "BUG",
        "title": "地图按钮无响应",
        "content": "在首页点击地点标记后，右侧抽屉没有打开，刷新后仍然如此。",
        "contact": "me@example.com",
        "status": "NEW",
        "createdAt": "2025-06-01T10:00:00",
        "updatedAt": "2025-06-01T10:00:00"
      }
    ]
  }
}
```

**说明**：只返回当前用户本人的反馈，按创建时间倒序；响应不包含管理员内部备注与处理人信息。

**错误**：`1006`

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

### 4.2.1 旅行状态

`PUT /trips/{id}/status` ｜ 需鉴权

```json
{ "status": "COMPLETED" }
```

`DELETE /trips/{id}/status` ｜ 需鉴权（恢复自动判断）

**说明**：

- 手动状态只允许 `PLANNED` / `COMPLETED`，仅本人可改，他人返回 `2015`，非法值返回 `2001`。
- 响应包含 `status`、`effectiveStatus`、`statusManual`、`expired`、`effectiveStartDate`、`effectiveEndDate`。
- `effectiveStatus` 由后端自动计算：手动标记优先；否则有游记/消费且结束日期已过为 `COMPLETED`，在起止日期内为 `IN_PROGRESS`，其余为 `PLANNED`。
- 只有自动计划中、结束日期已过且没有记录时 `expired=true`。
- `PUT` 会设置 `statusManual=true`；`DELETE` 清除手动标记并重新计算。

---

### 4.2.2 时间线

`GET /trips/timeline` ｜ 需鉴权

返回本人全部归档，按起始日期正序（无日期用创建日期），字段包含名称、国家、日期、状态、行程/游记/消费数与花费。前端在地图与时间线之间切换；点击节点进入归档详情。

**响应示例**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 101,
      "name": "日本关西行",
      "countryCode": "JP",
      "countryName": "日本",
      "startDate": "2024-05-01",
      "endDate": "2024-05-05",
      "status": "PLANNED",
      "itineraryCount": 2,
      "noteCount": 1,
      "expenseCount": 8,
      "expenseCny": "3240.00"
    }
  ],
  "traceId": "..."
}
```

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
    },
    "newTripId": 102,
    "newTripName": "未命名旅行 · 2025-06"
  }
}
```

**错误**：`2017`（存在挂载数据且 `force` 不为 `true`）、`2015`

**说明**：`force=true` 时，原旅行若仍有挂载数据，系统会新建“未命名旅行 · yyyy-MM”并把行程/游记/消费迁移过去，原旅行软删除；`detached` 表示迁移数量。没有挂载数据时直接软删除，`newTripId`/`newTripName` 为 `null`。

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

- 删除旅行**不删除**挂载数据；`force=true` 时迁移到新的“未命名旅行 · yyyy-MM”
- 前端应先调 `force=false` 探测；收到 `2017` 后弹二次确认，用户确认后再用 `force=true` 重试
- 空旅行会被自动软删除，不再出现在列表、抽屉或时间线中

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

### 5.3 国家攻略（AI 流式）

国家抽屉的「AI 国家攻略」包含国家介绍、推荐景点、旅游须知三块内容。攻略按国家全局缓存 24 小时：缓存命中不扣额度、不调用 LLM；缓存未命中时点击生成才扣 1 次 AI 额度。

#### 5.3.1 读取缓存

`GET /country-guide/{countryCode}` ｜ 需鉴权 ｜ **不消耗额度**

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "available": true,
    "guide": {
      "countryCode": "JP",
      "countryName": "日本",
      "intro": "日本是位于东亚的岛国……",
      "attractions": [
        { "name": "富士山", "description": "日本象征，适合远眺与登山" }
      ],
      "notice": {
        "visa": "中国大陆普通护照通常需提前办理日本签证",
        "currency": "使用日元（JPY），大城市普遍支持信用卡",
        "powerPlug": "多为 A 型两脚扁插，电压 100V",
        "bestSeason": "3—5 月和 9—11 月较舒适",
        "notes": ["樱花季住宿紧张，建议提前预订"]
      },
      "cached": true
    }
  }
}
```

| 字段 | 规则 |
|---|---|
| `available` | 是否存在 24 小时内的国家级缓存 |
| `guide` | 无缓存时为 `null`；有缓存时 `cached=true` |
| 国家校验 | 国家不存在或已停用返回 `2002` |

#### 5.3.2 生成攻略（SSE）

`POST /country-guide/stream` ｜ 需鉴权 ｜ **缓存未命中时消耗 1 次 AI 额度**

**请求**

```json
{
  "requestId": "5f1c9e2a-3b7d-4c8e-9a10-2b3c4d5e6f70",
  "countryCode": "JP"
}
```

**SSE 事件**

| 事件 | data | 说明 |
|---|---|---|
| `meta` | `{"requestId":"…","quotaSource":"FREE","cached":false}` | 建立流时发送；缓存命中为 `quotaSource=CACHE`、`cached=true` |
| `delta` | `{"delta":"…"}` | 模型文本增量，前端按序拼接展示 |
| `done` | `{"requestId":"…","cached":false,"guide":{…}}` | 结束事件，携带解析后的完整结构化攻略 |
| `error` | `{"code":2012,"message":"…"}` | 失败；未输出任何 `delta` 时额度已自动退回 |

**关键说明**

- **一次生成三块内容**：国家介绍、推荐景点、旅游须知只扣 1 次额度。
- **24 小时国家缓存**：缓存对所有用户共享；第二个人打开同一国家直接返回缓存，不扣费、不调用 LLM。
- **并发保护**：同一国家正在生成时，后到请求返回 `3001`「该国攻略正在生成，请稍后重试」，不扣额度。
- **退款边界**：未输出任何 `delta` 即失败/断连 → 退回；已输出部分内容后失败/断连 → 不退回。
- **超时**：LLM 流式调用 120 秒，SSE 连接 130 秒。
- **错误**：`2001`（参数非法）、`2002`（国家不存在/停用）、`2011`（额度耗尽）、`2012`（AI 不可用）、`3001`（同国生成中）。

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
            "category": "寺庙",
            "ticketPrice": "免费",
            "highlights": "雷门大灯笼、仲见世商店街",
            "bestTime": "上午",
            "nearestStation": "浅草站",
            "reservationRequired": false,
            "area": "台东区",
            "warningNotes": "周末与节假日人流密集",
            "source": "AI"
          }
        ]
      }
    ],
    "guides": [
      { "title": "东京 5 日游攻略", "url": "https://example.com/tokyo", "source": "example.com" }
    ],
    "preparations": {
      "visa": "",
      "currency": "",
      "powerPlug": "",
      "bestSeason": "",
      "notes": []
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
- **preparations 已下线**：行程生成不再产出或缓存行前准备，响应中的 `preparations` 字段保留兼容但五项恒为空；签证、货币、插头、最佳季节与旅行提示改由 5.3「国家攻略」提供。历史行程详情仍返回库中已保存的旧内容。

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
        {
          "name": "浅草寺",
          "description": "…",
          "suggestedDuration": "2小时",
          "category": "寺庙",
          "ticketPrice": "免费",
          "highlights": "…",
          "bestTime": "上午",
          "nearestStation": "浅草站",
          "reservationRequired": false,
          "area": "台东区",
          "warningNotes": "…",
          "source": "AI"
        },
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
| `days[].spots[].category` | 否 | 类别，≤ 32 字 |
| `days[].spots[].ticketPrice` | 否 | 门票，原币文本（如「约 2000 円」），≤ 64 字 |
| `days[].spots[].highlights` | 否 | 亮点，≤ 255 字 |
| `days[].spots[].bestTime` | 否 | 最佳时段，≤ 64 字 |
| `days[].spots[].nearestStation` | 否 | 最近车站，≤ 128 字 |
| `days[].spots[].reservationRequired` | 否 | 是否需预约，缺省 `false` |
| `days[].spots[].area` | 否 | 区域，≤ 128 字 |
| `days[].spots[].warningNotes` | 否 | 避雷备注，≤ 255 字 |

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
- **手动规划复用本接口**：前端不调用 AI，`guides=[]`、`preparations` 为空、景点 `source=MANUAL`；保存不消耗额度，每天允许 0 个景点，归组与地图点亮规则与 AI 行程一致

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
            "suggestedDuration": "2小时",
            "category": "寺庙",
            "ticketPrice": "免费",
            "highlights": "…",
            "bestTime": "上午",
            "nearestStation": "浅草站",
            "reservationRequired": false,
            "area": "台东区",
            "warningNotes": "…"
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
- `tripDeleted=true` 表示这是该旅行最后一条内容，旅行已被自动软删除；前端提示并离开详情页
- 若旅行仍有游记/消费，则旅行保留，`tripDeleted=false`

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
  "category": "咖啡馆",
  "ticketPrice": "人均约 1200 円",
  "highlights": "手冲与自家烘焙豆",
  "bestTime": "下午",
  "nearestStation": "表参道站",
  "reservationRequired": false,
  "area": "港区",
  "warningNotes": "周末需排队",
  "insertIndex": 2
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `name` | 是 | 1–50 字 |
| `description` | 否 | ≤ 200 字 |
| `suggestedDuration` | 否 | ≤ 20 字 |
| `category` / `bestTime` | 否 | ≤ 32 / ≤ 64 字 |
| `ticketPrice` / `highlights` | 否 | 原币文本 ≤ 64 字 / ≤ 255 字 |
| `nearestStation` / `area` | 否 | ≤ 128 字 |
| `reservationRequired` | 否 | 布尔，缺省 `false` |
| `warningNotes` | 否 | ≤ 255 字 |
| `insertIndex` | 否 | 插入位置；缺省追加到末尾 |

> 扩展字段超长时服务端截断保存，不返回错误。

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
  "suggestedDuration": "1.5小时",
  "category": "咖啡馆",
  "ticketPrice": "人均约 1200 円",
  "highlights": "…",
  "bestTime": "下午",
  "nearestStation": "表参道站",
  "reservationRequired": true,
  "area": "港区",
  "warningNotes": "…"
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

### 7.1 获取默认模板

`GET /prep/checklist/template` ｜ 需鉴权

返回默认 10 项，`checked=false`，用于新建规划：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "itineraryId": null,
    "countryCode": null,
    "items": [
      {
        "id": null,
        "itemKey": "VISA",
        "label": "签证",
        "title": "签证",
        "description": "确认签证类型、所需材料和办理时长…",
        "daysBefore": 60,
        "checked": false,
        "custom": false,
        "sortOrder": 10
      }
    ]
  }
}
```

---

### 7.2 获取清单状态

`GET /prep/checklist?itineraryId=12` ｜ 需鉴权

响应与模板相同，但包含该行程已保存的勾选状态与自定义项。默认 10 项按固定顺序返回，自定义项按 `sortOrder, id` 追加在后。

**说明**

- 默认键：`VISA`、`FLIGHT`、`HOTEL`、`INSURANCE`、`SIM`、`CURRENCY`、`PLUG`、`APPS`、`PACKING`、`CUSTOMS`
- 自定义项 `itemKey` 以 `CUSTOM_` 开头，`custom=true`，可删除；默认项不可删除
- 未落库的默认项返回 `checked=false`
- 按“用户 + 行程”隔离，越权访问返回 `2003`

**错误**：`2003`

---

### 7.3 勾选/取消清单项

`PUT /prep/checklist` ｜ 需鉴权

```json
{
  "itineraryId": 12,
  "itemKey": "VISA",
  "checked": true
}
```

**说明**：upsert 语义，默认项与自定义项均支持；不消耗额度；不影响地图点亮。

**错误**：`2003`、`2022`（自定义项不存在或非法）

---

### 7.4 添加自定义项

`POST /prep/checklist/items` ｜ 需鉴权

```json
{
  "itineraryId": 12,
  "title": "租车",
  "description": "提前预订",
  "daysBefore": 10
}
```

返回完整 Item（含服务端生成的 `CUSTOM_...` 键、`custom=true`、`id`）。

**错误**：`2003`、`2001`（标题/天数/说明校验失败）

---

### 7.5 删除自定义项

`DELETE /prep/checklist/items/{itemId}` ｜ 需鉴权

**说明**：仅允许删除自定义项；默认项返回 `2001`。越权返回 `2003`。

---

### 7.6 批量保存清单

`PUT /prep/checklist/batch` ｜ 需鉴权

```json
{
  "itineraryId": 12,
  "items": [
    { "itemKey": "VISA", "title": "签证", "checked": true },
    {
      "itemKey": "CUSTOM_xxx",
      "title": "租车",
      "description": "提前预订",
      "daysBefore": 10,
      "checked": false,
      "custom": true
    }
  ]
}
```

**说明**：用于新建行程保存后的批量写入。同一事务内更新默认项、创建/更新自定义项、删除未提交的自定义项；默认项始终保留。返回完整清单。

---

### 7.7 获取官方链接

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
| `bizType` | 是 | `NOTE_IMAGE` 或 `NOTE_VIDEO` |
| `contentType` | 是 | 图片：`image/jpeg`、`image/png`、`image/webp`（**禁止 SVG**）；视频：`video/mp4`、`video/webm` |
| `size` | 是 | 图片 ≤ 5 MB（5242880 字节）；视频 ≤ 100 MB（104857600 字节） |

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
3. 上传成功后，把 `objectKey` 写入游记的 `images[]` 或 `videos[]`，并在正文中插入 `/objects/{objectKey}`

**说明**

- 图片签名有效期 300 秒；视频签名有效期 1800 秒
- 视频仅支持 mp4/webm，每篇游记最多 3 个；不做转码，浏览器直接播放原文件
- 不经过后端，节省 2 核 4G 的带宽与内存
- 前端仍应先做一次本地压缩与尺寸检查，降低失败率
- EXIF GPS 剥离在前端压缩阶段完成（canvas 重绘天然丢失 EXIF），后端无法二次校验
- 保存游记时后端会校验 objectKey 属于当前用户且对象真实存在

**错误**：`2021`（图片类型不支持）、`2019`（单图超 5MB）、`2030`（视频类型不支持）、`2029`（视频超 100MB）、`5003`（存储不可用）

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
  "videos": [
    "note/12/2025/06/c3.mp4"
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
| `videos` | 否 | `objectKey` 数组，≤ 3 个；仅 mp4/webm |
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
- `images`/`videos` 中的 objectKey 必须属于当前用户（`note/{userId}/`）且已真实上传，否则 `2027`

**错误**：`2001`（缺国家/城市）、`2002`（城市非法/停用）、`2003`（行程无权）、`2018`（图片 > 20 张）、`2020`（正文 > 200KB）、`2027`（媒体无效）、`2028`（视频 > 3 个）

---

### 9.1.1 游记列表

`GET /notes` ｜ 需鉴权

**查询参数**

| 参数 | 必填 | 说明 |
|---|---|---|
| `keyword` | 否 | 匹配标题或正文纯文本摘要 |
| `from` / `to` | 否 | 按创建时间筛选（`YYYY-MM-DD`，闭区间） |
| `page` / `size` | 否 | 默认 `1` / `10`，`size` ≤ 100 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 3,
    "page": 1,
    "size": 10,
    "list": [
      {
        "id": 11,
        "title": "东京散步",
        "summary": "正文前 80 字…",
        "coverImageUrl": "https://…600秒签名URL…",
        "countryCode": "JP",
        "countryName": "日本",
        "cityCode": "TYO",
        "cityName": "东京",
        "visibility": "PRIVATE",
        "takenDown": false,
        "createdAt": "2025-06-01T10:00:00",
        "updatedAt": "2025-06-01T10:00:00"
      }
    ]
  }
}
```

**说明**：只返回本人未删除游记；按创建时间倒序；签名 URL 不可用时 `coverImageUrl=null`。

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
    "videos": [
      { "objectKey": "note/12/2025/06/c3.mp4", "url": "https://…600秒签名URL…" }
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
- 图片与视频 URL 均为 600 秒签名；分享页不展示视频

**错误**：`2004`

---

### 9.3 编辑游记

`PUT /notes/{id}` ｜ 需鉴权

**请求**：字段同 9.1，均为可选；`content`、`images`、`videos`、`visibility` 至少传一项

**响应**：返回完整游记结构（同 9.2）

**错误**：`2004`、`2002`、`2018`、`2020`、`2024`（内容已下架，不可编辑）、`2027`（媒体无效）、`2028`（视频 > 3 个）

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

### 9.8 分享页举报

`POST /s/{token}/reports` ｜ **无需鉴权**（登录用户会自动记录举报人）

**请求**

```json
{
  "reason": "ABUSE",
  "description": "正文包含辱骂性内容"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `reason` | 是 | `SPAM` / `ABUSE` / `PORN` / `ILLEGAL` / `PRIVACY` / `OTHER` |
| `description` | 否 | ≤200 字；`OTHER` 时必填 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "reportId": 21,
    "duplicate": false,
    "createdAt": "2025-06-08T10:00:00"
  }
}
```

**说明**

- 举报时保存标题、纯文本摘要与游记更新时间的快照，仅管理员可见
- 登录用户对同一游记只保留一条待处理举报；重复提交返回 `duplicate=true`
- 匿名访客按「IP + 游记」24 小时去重，每 IP 每天最多 5 条；IP 不落库
- 作者本人举报自己的游记返回 `2001`

**错误**：`2001`（参数错误 / 自举报）、`2007`（分享失效）、`5004`（匿名每日限流）

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
| `amount` | 是 | 字符串，最多两位小数；**允许负数**表示退款。前端可输入 `10+2.5*2` 等表达式，由前端计算后提交最终金额 |
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
| `cityCode` | 否 | 范围过滤，需与 `countryCode` 一致 |
| `tripId` | 否 | 范围过滤 |
| `category` | 否 | 分类过滤 |
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

### 10.6 按日期分组消费

`GET /expenses/by-date` ｜ 需鉴权

返回当前用户全部匹配消费并按日期分组，每日合计不受分页影响。

**查询参数**：与 10.4 消费列表一致（`countryCode`、`cityCode`、`tripId`、`ungrouped`、`category`、`from`、`to`），无分页参数。

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "totalCny": "118.00",
    "hasUnconfiguredCurrency": true,
    "unconfiguredCurrencies": ["XYZ"],
    "groups": [
      {
        "date": "2025-06-02",
        "totalCny": "98.00",
        "originalCurrencies": [
          { "currency": "CNY", "total": "50.00" },
          { "currency": "JPY", "total": "1000.00" }
        ],
        "items": [
          {
            "id": 8,
            "countryCode": "JP",
            "countryName": "日本",
            "cityCode": "TYO",
            "cityName": "东京",
            "tripId": 101,
            "currency": "JPY",
            "amount": "1000.00",
            "exchangeRateSnapshot": "0.048000",
            "amountCny": "48.00",
            "rateMissing": false,
            "category": "FOOD",
            "expenseDate": "2025-06-02",
            "remark": "午餐",
            "createdAt": "2025-06-02T12:00:00"
          }
        ]
      }
    ]
  }
}
```

**说明**

- 日期倒序，组内按 ID 倒序
- 未配置汇率的记录仍出现在明细中，但不计入 `totalCny`
- `originalCurrencies` 为该日按币种汇总的原币金额
- 只返回本人未删除消费，越权不可见

**错误**：`2001`（日期/分类格式非法）

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

### 12.4.1 每日免费额度配置

`GET /admin/config/quota` ｜ `PUT /admin/config/quota`

**请求 / 响应**

```json
{ "freeLimit": 3 }
```

**说明**：`freeLimit` 为每用户每日免费 AI 次数（行程生成与润色共用），范围 `0–100`；`0` 表示不提供免费次数。保存后立即生效。

---

### 12.4.2 邮件 SMTP 配置

`GET /admin/config/mail` ｜ `PUT /admin/config/mail`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "enabled": true,
    "host": "smtp.example.com",
    "port": 587,
    "username": "mailer@example.com",
    "password": "****",
    "from": "no-reply@example.com",
    "smtpAuth": true,
    "starttls": true
  }
}
```

| 字段 | 说明 |
|---|---|
| `enabled` | 是否启用 SMTP；关闭时回退日志发送器 |
| `host` / `port` | SMTP 主机与端口（1–65535） |
| `username` / `password` | 认证信息；密码 AES 加密，读取仅掩码，PUT 留空保留 |
| `from` | 发件人邮箱；启用时必填且需包含 `@` |
| `smtpAuth` / `starttls` | SMTP 认证与 STARTTLS 开关 |

**说明**

- 保存后立即热加载，下一次发送验证码/重置密码邮件使用新配置
- 启用 SMTP 后开发环境不再在日志打印验证码
- 未配置 DB 值时使用环境变量 `MAIL_*` 作为默认值

**错误**：`2001`（启用时缺主机/发件人非法/端口越界）、`4002`

---

### 12.5 批量生成卡密

`POST /admin/cards/generate`

**请求**

```json
{
  "type": "VIP",
  "value": 30,
  "priceCny": 29.90,
  "expireAt": "2026-12-31T23:59:59",
  "quantity": 100,
  "prefix": "TM"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `type` | 是 | `AI_QUOTA` / `VIP` |
| `value` | 是 | 次数包为次数（1–100000）；VIP 为天数（1–3650） |
| `priceCny` | 否 | 售价（CNY，≥0，最多两位小数）；用于批次估算收入，不代表实际收款 |
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
        "priceCny": 29.90,
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
code,type,value,expireAt,status,createdAt,priceCny
TM-7K2P-9QX4-RT8M,VIP,30,2026-12-31 23:59:59,UNUSED,2025-06-01 10:00:00,29.90
TM-A1B2-C3D4-E5F6,AI_QUOTA,100,2026-12-31 23:59:59,UNUSED,2025-06-01 10:00:00,
```

**说明**：原有列顺序不变，`priceCny` 追加在末尾；时间格式 `yyyy-MM-dd HH:mm:ss`；Excel 直接打开不乱码。

---

### 12.7.1 批次统计

`GET /admin/cards/stats`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "batchId": "B20250601100000",
      "type": "VIP",
      "value": 30,
      "priceCny": 29.90,
      "createdAt": "2025-06-01T10:00:00",
      "total": 100,
      "used": 30,
      "unused": 62,
      "voided": 6,
      "expired": 2,
      "estimatedRevenueCny": 897.00
    }
  ]
}
```

**说明**：按批次聚合；估算收入 = 已用数量 × 售价，售价为空时返回 `null`（不按 0 计算）。

---

### 12.7.2 兑换记录

`GET /admin/cards/redemptions?userId=12&page=1&size=20`

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
        "cardId": 5001,
        "code": "TM-7K2P-9QX4-RT8M",
        "batchId": "B20250601100000",
        "type": "VIP",
        "value": 30,
        "priceCny": 29.90,
        "userId": 12,
        "username": "traveler",
        "usedAt": "2025-06-02T09:30:00"
      }
    ]
  }
}
```

**说明**：仅返回已使用卡密；`userId` 可选，用于用户详情筛选。

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

### 12.11.1 用户详情

`GET /admin/users/{id}`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "username": "traveler",
    "nickname": "旅行者",
    "email": "traveler@example.com",
    "role": "USER",
    "status": "NORMAL",
    "vipExpireAt": "2026-12-31T23:59:59",
    "freeUsedToday": 1,
    "packBalance": 20,
    "noteCount": 3,
    "itineraryCount": 5,
    "tripCount": 2,
    "createdAt": "2025-01-01T10:00:00",
    "recentRedemptions": [
      {
        "cardId": 5001,
        "code": "TM-7K2P-9QX4-RT8M",
        "batchId": "B20250601100000",
        "type": "VIP",
        "value": 30,
        "priceCny": 29.90,
        "userId": 12,
        "username": "traveler",
        "usedAt": "2025-06-02T09:30:00"
      }
    ]
  }
}
```

**说明**：已注销用户只返回匿名化资料，内容数量为 0、兑换记录为空。

---

### 12.11.2 设置 VIP 到期日

`PUT /admin/users/{id}/vip`

**请求**

```json
{ "expireAt": "2027-12-31T23:59:59" }
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `expireAt` | 否 | 目标到期时间；为空表示清除 VIP；设为过去时间表示立即收回 |

**响应**：`data: { "userId": 12, "vipExpireAt": "2027-12-31T23:59:59" }`

**说明**：该操作直接写 `vip_expire_at` 并记录 `audit_log`（`USER_VIP_SET`），**不写 `quota_ledger`**；按天数调整仍使用 12.11。

**错误**：`2001`（用户不存在或已注销）、`4002`

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

**一键更新预览** `GET /admin/exchange-rates/refresh-preview`

**一键更新执行** `POST /admin/exchange-rates/refresh`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "source": "exchangerate-api.com",
    "fetchedAt": "2025-06-01T10:00:00",
    "applied": false,
    "updatedCount": 1,
    "unchangedCount": 1,
    "unsupportedCount": 0,
    "items": [
      { "currency": "CNY", "oldRate": "1.000000", "newRate": "1.000000", "status": "UNCHANGED" },
      { "currency": "JPY", "oldRate": "0.048000", "newRate": "0.047500", "status": "CHANGED" }
    ]
  }
}
```

**说明**

- 只更新数据库中**已有**的币种，不新增币种、不回填历史消费
- `refresh-preview` 只预览不改库；`refresh` 按最新数据批量写入
- 数据源不支持的币种标记为 `UNSUPPORTED`，保持原值
- 批量更新写审计日志 `RATE_REFRESH`，包含数据源、更新数量和变更明细
- 数据源不可用返回 `2033`

**错误**：`2033`（汇率数据源不可用）、`4002`

---

### 12.14.1 内容列表

`GET /admin/notes?keyword=东京&status=TAKEDOWN&from=2025-06-01&to=2025-06-30&page=1&size=20`

**查询参数**

| 参数 | 必填 | 说明 |
|---|---|---|
| `keyword` | 否 | 匹配标题或作者用户名/昵称 |
| `status` | 否 | `NORMAL` / `TAKEDOWN` |
| `from` / `to` | 否 | 按创建时间筛选（`YYYY-MM-DD`） |

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
        "id": 11,
        "title": "东京散步",
        "authorId": 12,
        "authorName": "旅行者",
        "countryCode": "JP",
        "countryName": "日本",
        "cityCode": "TYO",
        "cityName": "东京",
        "visibility": "SHARED",
        "takenDown": true,
        "takedownReason": "违规内容",
        "createdAt": "2025-06-01T10:00:00",
        "updatedAt": "2025-06-02T10:00:00"
      }
    ]
  }
}
```

**说明**：只返回元信息，不返回正文；按创建时间倒序。

---

### 12.14.2 内容详情

`GET /admin/notes/{id}` ｜ 管理员

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 11,
    "title": "东京散步",
    "content": "<p>正文 HTML</p>",
    "contentText": "正文纯文本",
    "images": [{ "objectKey": "note/12/2025/06/a.jpg", "url": "https://…签名URL…" }],
    "videos": [{ "objectKey": "note/12/2025/06/a.mp4", "url": "https://…签名URL…" }],
    "coverImageUrl": "https://…签名URL…",
    "authorId": 12,
    "authorName": "旅行者",
    "countryCode": "JP",
    "countryName": "日本",
    "cityCode": "TYO",
    "cityName": "东京",
    "visibility": "PRIVATE",
    "hasActiveShare": false,
    "shareExpireAt": null,
    "takedown": false,
    "takedownReason": null,
    "takedownAt": null,
    "deleted": false,
    "createdAt": "2025-06-01T10:00:00",
    "updatedAt": "2025-06-01T10:00:00"
  }
}
```

**说明**

- 可查看任意游记，包括私密、已分享、已下架与作者软删除的记录
- 媒体 URL 为 600 秒签名 URL；不返回分享令牌
- 只读，不写审计日志

**错误**：`2004`（游记不存在）、`4002`（非管理员）

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

### 12.17 审计日志（操作日志）

`GET /admin/audit?action=CONFIG_UPDATE&userId=1&actorRole=ADMIN&targetType=CONFIG&from=2025-06-01&to=2025-06-30&page=1&size=20`

**查询参数**

| 参数 | 必填 | 说明 |
|---|---|---|
| `action` | 否 | 动作精确匹配，如 `CONFIG_UPDATE`、`NOTE_UPDATE`、`EXPENSE_CREATE` |
| `userId` | 否 | 操作人 ID |
| `actorRole` | 否 | 操作人角色：`ADMIN` / `USER` / `SYSTEM` / `ANONYMOUS` |
| `targetType` | 否 | `CARD` / `USER` / `NOTE` / `CONFIG` / `RATE` / `FEEDBACK` / `REPORT` / `TRIP` / `ITINERARY` / `EXPENSE` / `ANNOUNCEMENT` / `CACHE` |
| `from` / `to` | 否 | 按创建时间筛选（`YYYY-MM-DD`） |

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
        "id": 88,
        "userId": 12,
        "actorRole": "USER",
        "username": "traveler",
        "action": "NOTE_UPDATE",
        "targetType": "NOTE",
        "targetId": 31,
        "detail": "{\"changed\":[\"title\",\"visibility\"]}",
        "ip": "203.0.113.10",
        "createdAt": "2025-06-02T10:00:00"
      }
    ]
  }
}
```

**用户操作动作**

| 分组 | 动作 |
|---|---|
| 旅行 | `TRIP_UPDATE`（重命名）、`TRIP_STATUS`、`TRIP_DELETE` |
| 行程 | `ITINERARY_CREATE`（保存）、`ITINERARY_UPDATE`、`ITINERARY_DELETE`、`ITINERARY_GENERATE`（生成草稿或重新生成） |
| 游记 | `NOTE_CREATE`、`NOTE_UPDATE`、`NOTE_DELETE`、`NOTE_SHARE`、`NOTE_UNSHARE`、`NOTE_REPORT` |
| 消费 | `EXPENSE_CREATE`、`EXPENSE_UPDATE`、`EXPENSE_DELETE` |
| 清单 | `CHECKLIST_SAVE`（批量保存，`targetType=ITINERARY`） |
| 账号与反馈 | `USER_PROFILE_UPDATE`、`FEEDBACK_CREATE` |
| 公告 | `ANNOUNCEMENT_CREATE`、`ANNOUNCEMENT_UPDATE`、`ANNOUNCEMENT_PUBLISH`、`ANNOUNCEMENT_OFFLINE`、`ANNOUNCEMENT_DELETE` |
| 监控处置 | `USER_KICK`（强制下线）、`CACHE_CLEAR`（清理安全缓存） |
| 既有 | `CARD_REDEEM`、`ACCOUNT_DELETE` |

**说明**：

- 只读接口，无修改/删除能力；`detail` 在写入时已脱敏。该接口对应后台「日志管理 → 操作日志」页签。
- 用户操作按语义聚合：天、景点变更并入行程保存；清单只记批量保存。只记录成功操作，失败或被拒绝的请求不写日志。
- `detail` 只包含对象 ID、变更字段名、数量或枚举值，不包含标题、正文、金额、备注与联系方式原文。
- 匿名分享页举报的 `userId` 为空、`actorRole=ANONYMOUS`；无 HTTP 请求的系统写入为 `SYSTEM`。
- 日志写入失败只告警，不影响业务操作；`actor_role` 在写入时快照，不随用户后续升/降级变化。

---

### 12.18 反馈列表

`GET /admin/feedback?type=BUG&status=NEW&keyword=地图&from=2025-06-01&to=2025-06-30&page=1&size=20` ｜ 管理员

**查询参数**

| 参数 | 必填 | 说明 |
|---|---|---|
| `type` | 否 | `FEATURE` / `BUG` / `UX` / `OTHER` |
| `status` | 否 | `NEW` / `PROCESSING` / `RESOLVED` |
| `keyword` | 否 | 匹配标题、正文、联系方式、提交人用户名/昵称 |
| `from` / `to` | 否 | 按创建时间筛选（`YYYY-MM-DD`，闭区间） |
| `page` / `size` | 否 | 默认 `1` / `20` |

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
        "id": 12,
        "userId": 8,
        "submitter": "旅人小张",
        "type": "BUG",
        "title": "地图按钮无响应",
        "status": "NEW",
        "contact": "me@example.com",
        "createdAt": "2025-06-01T10:00:00",
        "updatedAt": "2025-06-01T10:00:00",
        "handledAt": null
      }
    ]
  }
}
```

**说明**：按创建时间倒序；已注销用户的 `submitter` 固定为「已注销用户」。

**错误**：`2001`（`type`/`status` 非法）、`4002`

---

### 12.19 反馈统计

`GET /admin/feedback/stats` ｜ 管理员

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 12,
    "newCount": 5,
    "processingCount": 4,
    "resolvedCount": 3
  }
}
```

**错误**：`4002`

---

### 12.20 反馈详情

`GET /admin/feedback/{id}` ｜ 管理员

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "userId": 8,
    "submitter": "旅人小张",
    "type": "BUG",
    "title": "地图按钮无响应",
    "content": "在首页点击地点标记后，右侧抽屉没有打开，刷新后仍然如此。",
    "contact": "me@example.com",
    "pagePath": "/map",
    "userAgent": "Mozilla/5.0 ...",
    "appVersion": "0.1.0",
    "status": "PROCESSING",
    "adminNote": "已复现，等待修复",
    "handledBy": 1,
    "handledByName": "平台管理员",
    "handledAt": "2025-06-01T11:00:00",
    "createdAt": "2025-06-01T10:00:00",
    "updatedAt": "2025-06-01T11:00:00"
  }
}
```

**错误**：`2031`（反馈不存在）、`4002`

---

### 12.21 更新反馈状态与内部备注

`PUT /admin/feedback/{id}` ｜ 管理员

**请求**

```json
{
  "status": "PROCESSING",
  "adminNote": "已复现，等待修复"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `status` | 否 | `NEW` / `PROCESSING` / `RESOLVED`；与 `adminNote` 至少提供一个 |
| `adminNote` | 否 | 仅管理员可见，≤1000 字；传空字符串表示清空 |

**响应**：返回更新后的反馈详情，字段同 12.20。

**错误**：`2001`（状态非法、备注超长或两者都未提供）、`2031`、`4002`

**说明**

- 成功更新后写入 `handledBy` / `handledAt`
- 每次成功更新写入审计日志 `audit_log`：`action=FEEDBACK_UPDATE`、`targetType=FEEDBACK`、`targetId=<反馈 ID>`，`detail` 只含反馈 ID、原状态、新状态与是否修改备注，不含正文或备注全文
- 状态允许在三态之间任意流转

---

### 12.22 举报列表

`GET /admin/reports?status=PENDING&reason=ABUSE&keyword=辱骂&from=2025-06-01&to=2025-06-30&page=1&size=20` ｜ 管理员

**查询参数**

| 参数 | 必填 | 说明 |
|---|---|---|
| `status` | 否 | `PENDING` / `ACCEPTED` / `REJECTED` |
| `reason` | 否 | `SPAM` / `ABUSE` / `PORN` / `ILLEGAL` / `PRIVACY` / `OTHER` |
| `keyword` | 否 | 匹配说明、标题快照、当前标题、作者用户名/昵称 |
| `from` / `to` | 否 | 按举报创建时间筛选（`YYYY-MM-DD`） |

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
        "id": 21,
        "noteId": 11,
        "reporterName": "匿名访客",
        "reason": "ABUSE",
        "status": "PENDING",
        "description": "正文包含辱骂性内容",
        "noteTitle": "东京散步",
        "currentNoteTitle": "东京散步",
        "authorName": "旅行者",
        "createdAt": "2025-06-08T10:00:00",
        "handledAt": null
      }
    ]
  }
}
```

**说明**：按举报时间倒序；举报人注销显示「匿名访客」，作者注销显示「已注销用户」。

**错误**：`2001`（`status`/`reason` 非法）、`4002`

---

### 12.23 举报统计

`GET /admin/reports/stats` ｜ 管理员

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 9,
    "pendingCount": 4,
    "acceptedCount": 3,
    "rejectedCount": 2
  }
}
```

**错误**：`4002`

---

### 12.24 举报详情

`GET /admin/reports/{id}` ｜ 管理员

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 21,
    "noteId": 11,
    "reporterUserId": null,
    "reporterName": "匿名访客",
    "reason": "ABUSE",
    "description": "正文包含辱骂性内容",
    "noteTitle": "东京散步",
    "noteContentText": "举报时的正文纯文本快照",
    "noteUpdatedAt": "2025-06-01T10:00:00",
    "status": "PENDING",
    "handledBy": null,
    "handledByName": null,
    "handledAt": null,
    "createdAt": "2025-06-08T10:00:00",
    "authorId": 12,
    "authorName": "旅行者",
    "currentNoteTitle": "东京散步",
    "currentNoteVisibility": "PRIVATE",
    "currentNoteUpdatedAt": "2025-06-08T09:00:00",
    "currentNoteTakedown": false,
    "currentNoteDeleted": false,
    "authorAcceptedReportCount": 1
  }
}
```

**错误**：`2032`、`4002`

---

### 12.25 采纳举报

`PUT /admin/reports/{id}/accept` ｜ 管理员

**请求**

```json
{ "noteReason": "含辱骂内容" }
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `noteReason` | 是 | 1–100 字，写入游记下架原因，作者可见 |

**响应**：返回更新后的举报详情，字段同 12.24。

**说明**

- 下架游记并撤销其全部分享令牌；该游记全部待处理举报一并置为 `ACCEPTED`
- 若游记已被作者删除，只把举报置为 `ACCEPTED`，不恢复内容
- 写入审计 `REPORT_ACCEPT`；下架本身另写 `NOTE_TAKEDOWN`
- 已处理举报再次操作返回 `2001`

**错误**：`2001`（原因非法或举报已处理）、`2032`、`4002`

---

### 12.26 驳回举报

`PUT /admin/reports/{id}/reject` ｜ 管理员

**响应**：返回更新后的举报详情，字段同 12.24。

**说明**：只把当前举报置为 `REJECTED`，游记与其他举报不受影响；写入审计 `REPORT_REJECT`。

**错误**：`2001`（举报已处理）、`2032`、`4002`

---

### 12.27 登录日志

`GET /admin/login-logs?username=traveler&userId=12&role=USER&success=false&from=2025-06-01&to=2025-06-30&page=1&size=20` ｜ 管理员

**查询参数**

| 参数 | 必填 | 说明 |
|---|---|---|
| `username` | 否 | 登录账号，模糊匹配 |
| `userId` | 否 | 用户 ID |
| `role` | 否 | `ADMIN` / `USER` |
| `success` | 否 | `true` 成功 / `false` 失败 |
| `from` / `to` | 否 | 按创建时间筛选（`YYYY-MM-DD`，闭区间） |
| `page` / `size` | 否 | 默认 `1` / `20` |

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
        "userId": 12,
        "username": "traveler",
        "role": "USER",
        "success": false,
        "reason": "BAD_CREDENTIALS",
        "ip": "203.0.113.10",
        "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...",
        "createdAt": "2025-06-02T10:00:00"
      }
    ]
  }
}
```

**失败原因**

| `reason` | 说明 |
|---|---|
| `SUCCESS` | 登录成功 |
| `USER_NOT_FOUND` | 账号不存在 |
| `BAD_CREDENTIALS` | 密码错误 |
| `ACCOUNT_BANNED` | 账号已封禁 |
| `ACCOUNT_DELETED` | 账号已注销 |
| `EMAIL_NOT_VERIFIED` | 邮箱未验证 |
| `RATE_LIMITED` | 失败次数过多被限流 |

**说明**：只读接口，无修改/删除能力；`username` 为登录时提交的账号快照，`userAgent` 截断至 255 字符。后台「日志管理」页以「操作日志 / 登录日志」两个页签展示两类日志，默认保留 90 天。

**错误**：`4002`

---

### 12.28 公告管理

`/admin/announcements` ｜ 管理员

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/announcements` | 分页列表；`status` / `type` / `keyword` / `page` / `size` |
| GET | `/admin/announcements/{id}` | 详情（含正文与阅读统计） |
| POST | `/admin/announcements` | 创建草稿 |
| PUT | `/admin/announcements/{id}` | 更新标题/正文/类型/置顶/生效时间 |
| PUT | `/admin/announcements/{id}/publish` | 发布；开始时间为空时设为当前时间 |
| PUT | `/admin/announcements/{id}/offline` | 下线 |
| DELETE | `/admin/announcements/{id}` | 软删除，保留已读记录 |

**请求示例**

```json
{
  "title": "系统维护通知",
  "content": "今晚 23:00 - 24:00 进行维护，\n期间可能短暂不可用。",
  "type": "NOTICE",
  "pinned": true,
  "publishAt": "2025-06-01T10:00:00",
  "expireAt": "2025-06-02T10:00:00"
}
```

**列表响应字段**：`id`、`title`、`type`、`pinned`、`status`（`DRAFT` / `PUBLISHED` / `OFFLINE`）、`publishAt`、`expireAt`、`readCount`、`totalUsers`、`readRate`（0–1）、`createdAt`、`updatedAt`；详情额外返回 `content` 与 `createdBy`。

**说明**：正文为纯文本并保留换行；阅读率分母为当前 `role=USER` 且状态正常的用户数；所有变更写 `ANNOUNCEMENT_*` 审计；结束时间必须晚于开始时间。

**错误**：`2001`、`2034`、`4002`

---

### 12.29 监控中心

`/admin/monitor/**` ｜ 管理员

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/monitor/online-users` | 在线用户；`keyword` / `role` / `ip` / `page` / `size` |
| DELETE | `/admin/monitor/online-users/{userId}` | 强制下线（`tokenVersion+1`），写 `USER_KICK` 审计 |
| GET | `/admin/monitor/server` | CPU、内存、磁盘、操作系统与 JVM 快照 |
| GET | `/admin/monitor/database` | HikariCP 连接池统计 |
| GET | `/admin/monitor/cache` | Redis 概览与已知缓存分组 |
| DELETE | `/admin/monitor/cache/{group}` | 清理安全缓存分组，写 `CACHE_CLEAR` 审计 |
| GET | `/admin/monitor/storage` | 对象存储配置与连通性探测 |
| GET | `/admin/monitor/storage/stats` | 按需统计对象数与总大小（上限 10,000，缓存 5 分钟） |
| GET | `/admin/monitor/api-docs` | OpenAPI 兼容清单（`paths` + `tags` / `summary`，缓存 60 秒），供前端渲染 |

**说明**：监控接口仅管理员可访问，不开放 Actuator metrics；在线状态存 Redis（5 分钟窗口、60 秒登记节流）；缓存分组不返回原始 value；`quota`、`user-auth`、`lock` 等关键分组不可清理；存储统计超出扫描上限时 `truncated=true`。

**错误**：`2001`（分组不可清理）、`4002`、`5003`（存储不可用）

---

## 13. SSE 事件协议

### 13.1 AI 流式润色

`POST /ai/polish/stream` ｜ 需鉴权 ｜ **消耗 1 次 AI 额度**

> 前端入口位于游记编辑器工具栏：选中文本后直接润色并原位替换；独立 `/polish` 页面已删除。协议与取消/退款规则不变。

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

### 13.2 国家攻略流

`POST /country-guide/stream` ｜ 需鉴权 ｜ **缓存未命中时消耗 1 次 AI 额度**

请求体只需 `requestId` 与 `countryCode`。事件顺序与润色一致，差异在于 `meta` 额外包含 `cached`，`done` 携带结构化攻略：

```
event: meta
data: {"requestId":"a1b2...","quotaSource":"FREE","cached":false}

event: delta
data: {"delta":"## 国家介绍\n"}

event: done
data: {"requestId":"a1b2...","cached":false,"guide":{"countryCode":"JP","intro":"…","attractions":[],"notice":{},"cached":false}}
```

- 缓存命中时只发送 `meta`（`quotaSource=CACHE`、`cached=true`）与 `done`（完整 `guide`），不扣额度。
- 未输出任何 `delta` 即失败时退回额度；已输出部分内容后失败不退回。
- 无法取消；关闭抽屉会中断连接，服务端按上述退款规则处理。

Nginx 需同样对 `/api/country-guide/stream` 关闭缓冲，读超时建议 130 秒。

### 13.3 事件类型汇总

| event | 出现次数 | 说明 |
|---|---|---|
| `meta` | 0 或 1 | 首帧，返回 `requestId` 与扣费来源 |
| `delta` | 0..N | 文本增量，前端按序拼接 |
| `done` | 0 或 1 | 正常结束 |
| `cancelled` | 0 或 1 | 用户主动取消，**不退额度** |
| `error` | 0 或 1 | 异常结束；按规则退回额度 |

### 13.4 前端处理要点

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

默认 10 项，顺序固定；自定义项 `itemKey` 以 `CUSTOM_` 开头。

| `itemKey` | 标题 | 出发前天数 |
|---|---|---|
| `VISA` | 签证 | 60 |
| `FLIGHT` | 机票 | 45 |
| `HOTEL` | 住宿 | 30 |
| `INSURANCE` | 保险 | 30 |
| `SIM` | 电话卡 / eSIM | 14 |
| `CURRENCY` | 换汇与银行卡 | 14 |
| `PLUG` | 转换头 + 插排 | 7 |
| `APPS` | App 准备 | 7 |
| `PACKING` | 必备物品 | 7 |
| `CUSTOMS` | 入境卡与海关 | 1 |
| `CUSTOM_*` | 用户自定义 | 自定义 |

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

### 14.11 用户反馈

| 枚举 | 值 |
|---|---|
| `type` | `FEATURE`（功能建议）/ `BUG`（问题反馈）/ `UX`（体验问题）/ `OTHER`（其他） |
| `status` | `NEW`（待处理）/ `PROCESSING`（处理中）/ `RESOLVED`（已处理） |

### 14.12 游记举报

| 枚举 | 值 |
|---|---|
| `reason` | `SPAM`（垃圾广告）/ `ABUSE`（辱骂攻击）/ `PORN`（色情低俗）/ `ILLEGAL`（违法信息）/ `PRIVACY`（侵犯隐私）/ `OTHER`（其他） |
| `status` | `PENDING`（待处理）/ `ACCEPTED`（已采纳）/ `REJECTED`（已驳回） |

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
| 2022 | 清单项非法或不存在 | 400 | `PUT /prep/checklist` |
| 2023 | 分享令牌创建失败 | 500 | `POST /notes/{id}/share` |
| 2024 | 内容已下架，不可操作 | 403 | `PUT /notes/{id}`、`POST /notes/{id}/share` |
| 2025 | 城市已被引用，不能物理删除 | 409 | `DELETE /admin/cities/{id}` |
| 2026 | 该国官方链接已达上限（3 条） | 400 | `POST /admin/official-links` |
| 2027 | 媒体对象无效或不属于当前用户 | 400 | `POST/PUT /notes` |
| 2028 | 视频数量超限（> 3 个） | 400 | `POST/PUT /notes` |
| 2029 | 单个视频超限（> 100 MB） | 400 | `POST /upload/presign` |
| 2030 | 不支持的视频类型 | 400 | `POST /upload/presign` |
| 2031 | 反馈不存在 | 404 | `GET/PUT /admin/feedback/{id}` |
| 2032 | 举报不存在 | 404 | `GET /admin/reports/{id}`、`PUT /admin/reports/{id}/accept` |
| 2033 | 汇率数据源不可用 | 503 | `GET /admin/exchange-rates/refresh-preview`、`POST /admin/exchange-rates/refresh` |

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
| 行程 | GET | `/trips/timeline` | 是 | 时间线（归档节点） |
| 旅行 | PUT | `/trips/{id}/status` | 是 | 计划中/已完成 |
| 旅行 | GET | `/trips/{id}` | 是 | 详情聚合（行程/游记/最近消费） |
| 旅行 | PUT | `/trips/{id}` | 是 | — |
| 旅行 | DELETE | `/trips/{id}` | 是 | — |
| 地图 | GET | `/map/stats` | 是 | — |
| 地图 | GET | `/map/country/{countryCode}` | 是 | — |
| 攻略 | GET | `/country-guide/{countryCode}` | 是 | — |
| 攻略 | POST | `/country-guide/stream` | 是 | 缓存未命中 **1 次** |
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
| 行前 | GET | `/prep/checklist/template` | 是 | 默认 10 项模板 |
| 行前 | GET | `/prep/checklist` | 是 | 按行程读取 |
| 行前 | PUT | `/prep/checklist` | 是 | 勾选/取消 |
| 行前 | POST | `/prep/checklist/items` | 是 | 添加自定义项 |
| 行前 | DELETE | `/prep/checklist/items/{itemId}` | 是 | 删除自定义项 |
| 行前 | PUT | `/prep/checklist/batch` | 是 | 批量保存 |
| 行前 | GET | `/prep/official-links` | 是 | — |
| 上传 | POST | `/upload/presign` | 是 | — |
| 游记 | POST | `/notes` | 是 | — |
| 游记 | GET | `/notes` | 是 | `keyword` / `from` / `to` / `page` / `size` |
| 游记 | GET | `/notes/{id}` | 是 | — |
| 游记 | PUT | `/notes/{id}` | 是 | — |
| 游记 | DELETE | `/notes/{id}` | 是 | — |
| 游记 | POST | `/notes/{id}/share` | 是 | — |
| 游记 | DELETE | `/notes/{id}/share` | 是 | — |
| 分享 | GET | `/s/{token}` | **否** | — |
| 分享 | POST | `/s/{token}/reports` | **否** | 匿名每 IP 每天 5 条 |
| 润色 | POST | `/ai/polish/stream` | 是 | **1 次** |
| 记账 | POST | `/expenses` | 是 | — |
| 记账 | GET | `/expenses` | 是 | — |
| 记账 | PUT | `/expenses/{id}` | 是 | — |
| 记账 | DELETE | `/expenses/{id}` | 是 | — |
| 记账 | GET | `/expenses/summary` | 是 | — |
| 记账 | GET | `/expenses/by-date` | 是 | 按日期分组 |
| 卡密 | POST | `/card/redeem` | 是 | — |
| 额度 | GET | `/quota/me` | 是 | — |
| 反馈 | POST | `/feedback` | 是 | 每用户每天 5 条 |
| 反馈 | GET | `/feedback/mine` | 是 | 本人历史 |
| 公告 | GET | `/announcements/active` | 是 | 只返回生效公告，含已读标记 |
| 公告 | POST | `/announcements/{id}/read` | 是 | 幂等标记已读 |
| 后台 | GET | `/admin/feedback` | 管理员 | `type` / `status` / `keyword` / `from` / `to` / `page` / `size` |
| 后台 | GET | `/admin/feedback/stats` | 管理员 | — |
| 后台 | GET | `/admin/feedback/{id}` | 管理员 | — |
| 后台 | PUT | `/admin/feedback/{id}` | 管理员 | 状态 + 内部备注 |
| 后台 | GET/PUT | `/admin/config/llm` | 管理员 | — |
| 后台 | GET/PUT | `/admin/config/quota` | 管理员 | — |
| 后台 | GET/PUT | `/admin/config/oss` | 管理员 | — |
| 后台 | GET/PUT | `/admin/config/search` | 管理员 | — |
| 后台 | GET/PUT | `/admin/config/mail` | 管理员 | — |
| 后台 | POST | `/admin/cards/generate` | 管理员 | — |
| 后台 | GET | `/admin/cards` | 管理员 | — |
| 后台 | GET | `/admin/cards/export` | 管理员 | — |
| 后台 | GET | `/admin/cards/stats` | 管理员 | — |
| 后台 | GET | `/admin/cards/redemptions` | 管理员 | `userId` / `page` / `size` |
| 后台 | PUT | `/admin/cards/{id}/void` | 管理员 | — |
| 后台 | POST | `/admin/trips/recluster` | 管理员 | — |
| 后台 | GET | `/admin/users` | 管理员 | — |
| 后台 | GET | `/admin/users/{id}` | 管理员 | — |
| 后台 | PUT | `/admin/users/{id}/vip` | 管理员 | — |
| 后台 | PUT | `/admin/users/{id}/status` | 管理员 | — |
| 后台 | PUT | `/admin/users/{id}/quota` | 管理员 | — |
| 后台 | GET/POST | `/admin/cities` | 管理员 | — |
| 后台 | PUT | `/admin/cities/{id}` | 管理员 | — |
| 后台 | GET/POST | `/admin/official-links` | 管理员 | — |
| 后台 | PUT/DELETE | `/admin/official-links/{id}` | 管理员 | — |
| 后台 | GET/PUT | `/admin/exchange-rates` | 管理员 | — |
| 后台 | GET | `/admin/exchange-rates/refresh-preview` | 管理员 | 一键更新预览 |
| 后台 | POST | `/admin/exchange-rates/refresh` | 管理员 | 一键更新并写入 |
| 后台 | GET | `/admin/notes` | 管理员 | `keyword` / `status` / `from` / `to` / `page` / `size` |
| 后台 | GET | `/admin/notes/{id}` | 管理员 | 完整内容，含软删除 |
| 后台 | GET | `/admin/reports` | 管理员 | `status` / `reason` / `keyword` / `from` / `to` / `page` / `size` |
| 后台 | GET | `/admin/reports/stats` | 管理员 | — |
| 后台 | GET | `/admin/reports/{id}` | 管理员 | — |
| 后台 | PUT | `/admin/reports/{id}/accept` | 管理员 | 采纳并下架 |
| 后台 | PUT | `/admin/reports/{id}/reject` | 管理员 | 驳回 |
| 后台 | PUT | `/admin/notes/{id}/takedown` | 管理员 | — |
| 后台 | DELETE | `/admin/cities/{id}` | 管理员 | — |
| 后台 | PUT | `/admin/notes/{id}/restore` | 管理员 | — |
| 后台 | GET | `/admin/audit` | 管理员 | `action` / `userId` / `actorRole` / `targetType` / `from` / `to` / `page` / `size` |
| 后台 | GET | `/admin/login-logs` | 管理员 | `username` / `userId` / `role` / `success` / `from` / `to` / `page` / `size` |
| 后台 | GET/POST | `/admin/announcements` | 管理员 | 列表 `status` / `type` / `keyword` / `page` / `size` |
| 后台 | GET/PUT/DELETE | `/admin/announcements/{id}` | 管理员 | 详情、更新、软删除 |
| 后台 | PUT | `/admin/announcements/{id}/publish` | 管理员 | 发布 |
| 后台 | PUT | `/admin/announcements/{id}/offline` | 管理员 | 下线 |
| 后台 | GET | `/admin/monitor/online-users` | 管理员 | `keyword` / `role` / `ip` / `page` / `size` |
| 后台 | DELETE | `/admin/monitor/online-users/{userId}` | 管理员 | 强制下线 |
| 后台 | GET | `/admin/monitor/server` | 管理员 | 服务器与 JVM |
| 后台 | GET | `/admin/monitor/database` | 管理员 | HikariCP |
| 后台 | GET | `/admin/monitor/cache` | 管理员 | Redis 概览与分组 |
| 后台 | DELETE | `/admin/monitor/cache/{group}` | 管理员 | 仅安全分组 |
| 后台 | GET | `/admin/monitor/storage` | 管理员 | 连通性 |
| 后台 | GET | `/admin/monitor/storage/stats` | 管理员 | 按需统计 |
| 后台 | GET | `/admin/monitor/api-docs` | 管理员 | OpenAPI JSON |

---

## 16. 通知公告（用户端）

### 16.1 查看生效公告

`GET /announcements/active` ｜ 需鉴权

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 1,
    "unread": 1,
    "list": [
      {
        "id": 3,
        "title": "系统维护通知",
        "content": "今晚 23:00 - 24:00 进行维护，\n期间可能短暂不可用。",
        "type": "NOTICE",
        "pinned": true,
        "publishAt": "2025-06-01T10:00:00",
        "expireAt": "2025-06-02T10:00:00",
        "read": false
      }
    ]
  }
}
```

**说明**：只返回 `status=PUBLISHED` 且当前时间落在开始/结束窗口内的公告，按「置顶优先、开始时间倒序」排列，最多 20 条；正文为纯文本并保留换行。

### 16.2 标记已读

`POST /announcements/{id}/read` ｜ 需鉴权

**响应**：`data: null`

**说明**：按用户与公告逐条记录，重复调用幂等；公告不存在或当前未生效返回 `2034`。

---

## 17. 修订记录

| 版本 | 修订内容 | 原因 |
|---|---|---|
| V1.11 监控与公告 | 新增 `/admin/monitor/**` 监控接口、`/admin/announcements/**` 公告管理与 `/announcements/**` 用户公告；新增错误码 `2034`；操作日志新增 `USER_KICK`、`CACHE_CLEAR` 与 `ANNOUNCEMENT_*` | 管理端可观测与用户通知（change: admin-monitoring / announcements） |
| V1.10 用户操作日志 | `GET /admin/audit` 增加 `actorRole` 筛选与 `actorRole` 响应字段；旅行/行程/游记/消费/清单/资料/反馈等用户操作写入 `audit_log` | 管理员可查看用户操作（change: admin-log-management） |
| V1.9 日志管理 | 新增 `GET /admin/login-logs`；登录接口记录成功/失败登录日志；后台「审计日志」升级为「日志管理」（操作日志 + 登录日志两个页签） | 账号安全追溯（change: admin-log-management） |
| V1.8 汇率一键更新 | 新增 `GET /admin/exchange-rates/refresh-preview` 与 `POST /admin/exchange-rates/refresh`；新增错误码 `2033`；使用公开汇率源刷新已有币种 | 减少手工逐条维护汇率 |
| V1.7 内容审核与举报 | 新增 `GET /admin/notes/{id}`、`POST /s/{token}/reports` 与后台举报列表/统计/详情/采纳/驳回 5 个接口；新增错误码 `2032` 与举报枚举；游记详情新增 `takedownReason` / `takedownAt`；标题纳入敏感词过滤 | 补齐内容治理闭环（change: note-moderation） |
| V1.6 用户反馈 | 新增 `POST /feedback`、`GET /feedback/mine` 与后台反馈列表/统计/详情/更新 4 个接口；新增错误码 `2031` 与反馈枚举；注销时匿名化反馈内容 | 建立用户反馈闭环（change: user-feedback） |
| V1.5 行程时间线（已撤回） | 时间线恢复为归档节点 `GET /trips/timeline` | 时间线按归档展示 |
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
