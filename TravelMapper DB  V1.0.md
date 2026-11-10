# 🗄️ 数据库设计文档：旅迹地图 V1.0

> 基于《需求规格说明书（补丁版）》与《概要设计文档 V1.0（修订版）》编写。  
> 范围：完整表结构 DDL、索引策略、初始化数据、核心查询示例、数据保留策略、备份与恢复。  
> 目标数据库：**MySQL 8.0**（InnoDB / utf8mb4）。  
> DDL 可直接执行；执行顺序无强依赖（不使用物理外键）。

---

## 目录

1. 设计约定
2. ER 关系总览
3. 表清单与删除策略
4. 建表 DDL
5. 索引策略说明
6. 初始化数据
7. 核心查询示例
8. 数据保留、脱敏与定时任务
9. 备份与恢复
10. 变更记录

---

## 1. 设计约定

### 1.1 通用规范

| 项 | 约定 |
|---|---|
| 存储引擎 | InnoDB |
| 字符集 | `utf8mb4` |
| 排序规则 | `utf8mb4_0900_ai_ci` |
| 主键 | `BIGINT UNSIGNED AUTO_INCREMENT`，命名为 `id` |
| 表名 | 小写下划线，单数，如 `travel_note` |
| 字段名 | 小写下划线，如 `country_code` |
| 时间字段 | `created_at`、`updated_at`，类型 `DATETIME` |
| 软删除 | `deleted TINYINT NOT NULL DEFAULT 0`，1 表示已删 |
| 金额 | `DECIMAL(12,2)`，禁止 `FLOAT` / `DOUBLE` |
| 汇率 | `DECIMAL(12,6)` |
| 枚举 | `VARCHAR(16)` + 应用层校验（不用 MySQL `ENUM`，便于扩展） |
| 布尔 | `TINYINT(1)`，0/1 |
| JSON | MySQL `JSON` 类型，仅用于结构不固定的字段 |
| 时区 | 全系统固定北京时间（`Asia/Shanghai`）：`DATETIME` 存北京时间墙钟值且不带时区；`DATE` 存北京时间自然日；应用、JDBC 会话与数据库默认时区均统一为 +08:00，业务切日不依赖数据库默认时区 |

### 1.2 DTO 与字段映射

API 使用 camelCase，数据库使用 snake_case，由 MyBatis-Plus 的 `map-underscore-to-camel-case` 自动映射。无需手写转换。

### 1.3 不使用物理外键

**决策**：所有表之间不建 `FOREIGN KEY`，只在应用层保证引用完整性，并建立相应索引。

**理由**：

1. 全库软删除：外键无法表达"`deleted=1` 时不校验"，会阻碍删除流程。
2. 账号注销需要批量置空 `trip_id`、软删多表，有外键会导致锁等待与顺序依赖。
3. 单人维护，外键的收益（防脏数据）小于维护成本（迁移、批量操作、测试数据构造）。
4. 单机 MySQL 8.0，不涉及跨库分片，外键并非必需。

**代价与补偿**：应用层必须在 Service 层校验归属；`trip_id` 允许为 NULL 表示"未归组"；定期跑一致性巡检 SQL（见 7.6）。

### 1.4 `deleted` 过滤约定

- 所有业务查询**默认**带 `deleted = 0`
- 软删除表的索引把 `deleted` 放在前部或与 `user_id` 组合，避免全表扫描
- 唯一索引不含 `deleted`（见 5.3 的冲突处理）

### 1.5 MySQL 版本要求与 5.7 兼容说明

**本文档 DDL 以 MySQL 8.0 为目标**，使用了以下 8.0 特性：

| 特性 | 最低版本 | 出现位置 |
|---|---|---|
| `utf8mb4_0900_ai_ci` 排序规则 | 8.0 | 所有表 |
| `INSERT ... VALUES (...) AS new ON DUPLICATE KEY UPDATE` 别名语法 | 8.0.19 | 7.8 清单 upsert |
| JSON 类型 | 5.7 | `guides_json` / `preparations_json` / `images_json` / `detail` |
| STORED 生成列 | 5.7.6 | `expense.amount_cny` |

**若开发机为 MySQL 5.7**（本机检测到 `mysql-5.7.44`），需做两处替换才能执行：

```sql
-- 1) 排序规则：把 utf8mb4_0900_ai_ci 全部替换为 utf8mb4_unicode_ci
-- 2) 7.8 的 upsert 改为传统写法：
INSERT INTO trip_checklist
  (user_id, country_code, itinerary_id, item_key, checked, deleted)
VALUES (?, ?, ?, ?, ?, 0)
ON DUPLICATE KEY UPDATE
  checked = VALUES(checked),
  deleted = 0;
```

**建议**：开发环境直接升级到 **MySQL 8.0**（Docker 一行命令即可），避免 5.7 与生产不一致。若坚持用 5.7，请在 `docs/deploy.md` 中记录该差异，并在建表脚本中维护两套版本。

### 1.6 子表使用物理删除

`itinerary_day`、`itinerary_spot` 不设 `deleted` 字段，重新生成时**物理删除**旧子行再插入新行。`feedback` 与 `note_report` 同样不设 `deleted`，因为反馈与举报没有用户删除入口。

**理由**：两者存在 `UNIQUE(itinerary_id, day_index)` 约束；若软删除，重新插入同一天序号会冲突，而把 `deleted` 加进唯一键又会导致多条已删行互相冲突。子行没有"恢复"需求（父行程软删除即整体不可见），物理删除最简单可靠。

---

## 2. ER 关系总览

```
                          ┌──────────────┐
                          │  email_token │
                          └──────┬───────┘
                                 │ user_id
                                 ▼
┌──────────┐   user_id     ┌──────────┐
│   trip   │◄──────────────│   user   │
└────┬─────┘               └────┬─────┘
     │ trip_id                  │ user_id
     │                          │
     │        ┌─────────────────┼──────────────────┐
     │        │                 │                  │
     ▼        ▼                 ▼                  ▼
┌──────────────┐        ┌──────────────┐   ┌──────────────┐
│  itinerary   │        │ travel_note  │   │   expense    │
└──────┬───────┘        └──────┬───────┘   └──────────────┘
       │ 1:N                   │ 1:N
       ▼                       ▼
┌──────────────┐        ┌──────────────┐
│itinerary_day │        │  note_share  │
└──────┬───────┘        └──────────────┘
       │ 1:N
       ▼
┌──────────────┐        ┌──────────────────┐
│itinerary_spot│        │  trip_checklist  │
└──────────────┘        └──────────────────┘

┌──────────┐        ┌──────────────┐      ┌────────────┐
│ country  │──1:N──▶│     city     │      │exchange_rate│
└──────────┘        └──────────────┘      └────────────┘
┌──────────┐   ┌────────────┐   ┌──────────────┐   ┌──────────┐
│card_key  │──▶│quota_pack  │──▶│quota_ledger  │   │sys_config│
└──────────┘   └────────────┘   └──────────────┘   └──────────┘
┌────────────────────────┐  ┌────────────┐
│country_official_link   │  │ audit_log  │
└────────────────────────┘  └────────────┘
```

**补充关系**

- `country` 1:N `city`（`country.code = city.country_code`）
- `card_key` 1:N `quota_pack`（一次兑换生成一个批次；管理员调整时 `card_key_id` 为 NULL）
- `quota_pack` 是次数包**余额的真实来源**；`quota_ledger` 是审计流水

**关系说明**

| 关系 | 基数 | 说明 |
|---|---|---|
| `user` → `trip` | 1:N | 用户多次旅行 |
| `user` → `itinerary` | 1:N | 用户多条行程 |
| `user` → `travel_note` | 1:N | 用户多篇游记 |
| `user` → `expense` | 1:N | 用户多笔消费 |
| `user` → `trip_checklist` | 1:N | 用户多行清单项 |
| `trip` → `itinerary` / `travel_note` / `expense` | 1:N | 均可为 NULL（未归组） |
| `itinerary` → `itinerary_day` | 1:N | 一天一行 |
| `itinerary_day` → `itinerary_spot` | 1:N | 一天多个景点 |
| `travel_note` → `note_share` | 1:N | 一篇游记可有多条历史令牌 |
| `itinerary` → `trip_checklist` | 1:N | 按行程维度勾选 |
| `city` | 独立字典表 | 通过 `(country_code, city_code)` 被引用 |

---

## 3. 表清单与删除策略

共 **21 张表**（另有 1 张可选历史表）。

| # | 表名 | 中文名 | 软删除 | 说明 |
|---|---|---|---|---|
| 1 | `user` | 用户 | ✅ | 用户与管理员共用，`role` 区分 |
| 2 | `email_token` | 邮箱令牌 | ❌ | 用 `used` 标记一次性 |
| 3 | `country` | 国家字典 | ❌ | 国家名、洲别；接口 `countryName` 的来源 |
| 4 | `city` | 城市 | ❌ | 只停用不删除 |
| 5 | `trip` | 旅行 | ✅ | 多次旅行归组 |
| 6 | `itinerary` | 行程 | ✅ | 仅持久化已保存行程 |
| 7 | `itinerary_day` | 行程天 | ❌ | 物理删除（重新生成/增删天时替换） |
| 8 | `itinerary_spot` | 景点 | ❌ | 物理删除 |
| 9 | `country_official_link` | 国家官方链接 | ❌ | 管理员可硬删除 |
| 10 | `trip_checklist` | 行前清单 | ✅ | 注销时置 `deleted=1` |
| 11 | `travel_note` | 游记 | ✅ | 含下架标记 `takedown`（与 `deleted` 独立） |
| 12 | `note_share` | 分享令牌 | ❌ | 用 `revoked` 标记 |
| 13 | `expense` | 消费 | ✅ | 汇率快照随行 |
| 14 | `exchange_rate` | 汇率 | ❌ | 只更新，不删除 |
| 15 | `card_key` | 卡密 | ❌ | 用 `status` 状态机 |
| 16 | `quota_pack` | 次数包批次 | ❌ | 支撑 365 天过期与先进先出扣减 |
| 17 | `quota_ledger` | 额度流水 | ❌ | 只追加，不清洗 |
| 18 | `sys_config` | 系统配置 | ❌ | 键值表 |
| 19 | `audit_log` | 操作日志（含用户操作） | ❌ | 只追加，30 天保留；`actor_role` 写入时快照 |
| 20 | `feedback` | 用户反馈 | ❌ | 用户提交、管理员处理；注销时清空内容 |
| 21 | `note_report` | 游记举报 | ❌ | 分享页提交、管理员处理；保留快照 |
| 22 | `exchange_rate_history` | 汇率变更历史 | ❌ | **可选**，见 6.3 |
| 23 | `login_log` | 登录日志 | ❌ | 只追加，90 天保留 |
| 24 | `announcement` | 通知公告 | ✅ | 软删除；生效时间存 epoch 毫秒 |
| 25 | `announcement_read` | 公告已读 | ❌ | 公告+用户唯一；删除公告时保留 |

---

## 4. 建表 DDL

> 建议一次性执行本节的建表语句；若数据库已存在，先确认表名无冲突。

### 4.1 用户与认证

```sql
-- 用户表（含管理员）
CREATE TABLE `user` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`       VARCHAR(32)     NOT NULL COMMENT '登录名，注销后改为 deleted_{id}',
  `email`          VARCHAR(128)    NOT NULL COMMENT '注销后改为 deleted_{id}@deleted.local',
  `password_hash`  VARCHAR(100)    NOT NULL COMMENT 'BCrypt，注销后置空字符串',
  `nickname`       VARCHAR(32)     NOT NULL DEFAULT '' COMMENT '注销后改为 已注销用户',
  `avatar_preset`  VARCHAR(32)     NOT NULL DEFAULT 'preset_01',
  `role`           VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN；VIP 由 vip_expire_at 派生，不入角色',
  `status`         VARCHAR(16)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL / BANNED / DELETED',
  `email_verified` TINYINT(1)      NOT NULL DEFAULT 0,
  `vip_expire_at`  DATETIME        NULL,
  `token_version`  INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '封禁/改密/改邮箱/注销时 +1，使旧 JWT 失效',
  `totp_secret`    VARCHAR(255)    NULL COMMENT 'AES-GCM 密文；为空表示未绑定',
  `totp_enabled`   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已确认绑定二次验证',
  `totp_confirmed_at` DATETIME     NULL COMMENT '二次验证确认时间',
  `last_login_at`  DATETIME        NULL,
  `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_status` (`status`),
  KEY `idx_vip_expire` (`vip_expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户与管理员';

-- 邮箱验证 / 找回密码令牌
CREATE TABLE `email_token` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `email`      VARCHAR(128)    NOT NULL,
  `token`      CHAR(64)        NOT NULL COMMENT '随机串，唯一',
  `type`       VARCHAR(16)     NOT NULL COMMENT 'VERIFY / RESET',
  `expire_at`  DATETIME        NOT NULL COMMENT '签发后 30 分钟',
  `used`       TINYINT(1)      NOT NULL DEFAULT 0,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_user_type` (`user_id`, `type`),
  KEY `idx_expire_used` (`expire_at`, `used`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱验证与重置令牌';

-- TOTP 一次性恢复码：只存 BCrypt 哈希，使用后物理删除
CREATE TABLE `user_recovery_code` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `code_hash`  VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希',
  `used_at`    DATETIME        NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TOTP 一次性恢复码';
```

### 4.2 城市与旅行

```sql
-- 国家字典
CREATE TABLE `country` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`         CHAR(2)         NOT NULL COMMENT 'ISO 3166-1 alpha-2，如 JP',
  `name_zh`      VARCHAR(64)     NOT NULL,
  `name_en`      VARCHAR(64)     NULL,
  `continent`    VARCHAR(16)     NULL COMMENT 'ASIA/EUROPE/AFRICA/NORTH_AMERICA/SOUTH_AMERICA/OCEANIA',
  `status`       VARCHAR(16)     NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
  `sort_order`   INT             NOT NULL DEFAULT 0,
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='国家字典';

-- 城市字典
CREATE TABLE `city` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `country_code` CHAR(2)         NOT NULL COMMENT 'ISO 3166-1 alpha-2，如 JP',
  `city_code`    VARCHAR(8)      NOT NULL COMMENT '国家内唯一，如 TYO',
  `name`         VARCHAR(64)     NOT NULL COMMENT '中文名',
  `name_en`      VARCHAR(64)     NULL,
  `level`        VARCHAR(16)     NOT NULL DEFAULT 'MAJOR' COMMENT 'CITY(地级市) / MAJOR(主要城市)',
  `status`       VARCHAR(16)     NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
  `sort_order`   INT             NOT NULL DEFAULT 0,
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_country_city` (`country_code`, `city_code`),
  KEY `idx_country_status_sort` (`country_code`, `status`, `sort_order`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='城市字典';

-- 旅行（tripId 归组）
CREATE TABLE `trip` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `country_code` CHAR(2)         NOT NULL,
  `name`         VARCHAR(64)     NOT NULL COMMENT '默认「国家 · yyyy-MM」，可改名',
  `start_date`   DATE            NULL COMMENT '派生值：关联行程的最小 start_date，行程增删改后重算',
  `end_date`     DATE            NULL COMMENT '派生值：关联行程的最大 end_date',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_country` (`user_id`, `country_code`, `deleted`, `start_date`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='旅行归组';
```

### 4.3 行程

```sql
-- 行程主表（仅已保存行程）
CREATE TABLE `itinerary` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT UNSIGNED NOT NULL,
  `trip_id`           BIGINT UNSIGNED NULL COMMENT 'NULL = 未归组',
  `country_code`      CHAR(2)         NOT NULL,
  `title`             VARCHAR(64)     NOT NULL,
  `start_date`        DATE            NOT NULL,
  `end_date`          DATE            NOT NULL,
  `guides_json`       JSON            NULL COMMENT '最多 3 条 {title,url,source}',
  `preparations_json` JSON            NULL COMMENT '五项：visa/currency/powerPlug/bestSeason/notes',
  `status`            VARCHAR(16)     NOT NULL DEFAULT 'SAVED',
  `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_country` (`user_id`, `country_code`, `deleted`, `status`),
  KEY `idx_user_trip` (`user_id`, `trip_id`, `deleted`),
  KEY `idx_trip` (`trip_id`),
  KEY `idx_user_dates` (`user_id`, `start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已保存行程';

-- 行程每天
CREATE TABLE `itinerary_day` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `itinerary_id` BIGINT UNSIGNED NOT NULL,
  `day_index`    INT             NOT NULL COMMENT '从 1 开始',
  `visit_date`   DATE            NOT NULL COMMENT '避免使用保留字 date',
  `city_code`    VARCHAR(8)      NOT NULL,
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_itin_day` (`itinerary_id`, `day_index`),
  KEY `idx_itinerary` (`itinerary_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行程天（物理删除）';

-- 景点
CREATE TABLE `itinerary_spot` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `day_id`             BIGINT UNSIGNED NOT NULL,
  `sort_order`         INT             NOT NULL DEFAULT 0 COMMENT '天内排序，从 0 开始',
  `source`             VARCHAR(8)      NOT NULL DEFAULT 'AI' COMMENT 'AI / MANUAL',
  `name`               VARCHAR(64)     NOT NULL,
  `description`        VARCHAR(512)    NULL,
  `suggested_duration` VARCHAR(32)     NULL,
  `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_day_sort` (`day_id`, `sort_order`),
  KEY `idx_day_source` (`day_id`, `source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='景点（物理删除）';
```

### 4.4 行前准备

```sql
-- 国家官方链接映射
CREATE TABLE `country_official_link` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `country_code` CHAR(2)         NOT NULL,
  `title`        VARCHAR(64)     NOT NULL,
  `url`          VARCHAR(512)    NOT NULL COMMENT '必须 http/https，≤500 字符',
  `sort_order`   INT             NOT NULL DEFAULT 0,
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_country_sort` (`country_code`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='国家官方链接（每国 ≤3 条，应用层限制）';

-- 行前清单勾选（固定 7 项）
CREATE TABLE `trip_checklist` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `country_code` CHAR(2)         NOT NULL,
  `itinerary_id` BIGINT UNSIGNED NOT NULL,
  `item_key`     VARCHAR(16)     NOT NULL COMMENT 'VISA/FLIGHT/HOTEL/INSURANCE/CURRENCY/PLUG/SIM',
  `checked`      TINYINT(1)      NOT NULL DEFAULT 0,
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_itin_item` (`user_id`, `itinerary_id`, `item_key`),
  KEY `idx_user_country` (`user_id`, `country_code`),
  KEY `idx_itinerary` (`itinerary_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行前清单，按用户+国家+行程';
```

### 4.5 游记与分享

```sql
-- 游记
CREATE TABLE `travel_note` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `trip_id`         BIGINT UNSIGNED NULL COMMENT 'NULL = 未归组',
  `country_code`    CHAR(2)         NOT NULL,
  `city_code`       VARCHAR(8)      NOT NULL COMMENT '主城市',
  `title`           VARCHAR(64)     NOT NULL,
  `content`         MEDIUMTEXT      NOT NULL COMMENT '已过 XSS/敏感词过滤的 HTML，≤200KB',
  `content_text`    VARCHAR(600)    NULL COMMENT '纯文本摘要，供分享页取前 200 字',
  `images_json`     JSON            NULL COMMENT 'objectKey 数组，≤20 个',
  `cover_image`     VARCHAR(255)    NULL COMMENT '必须是 images_json 之一',
  `visibility`      VARCHAR(16)     NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE / SHARED',
  `takedown`        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '管理员下架标记',
  `takedown_reason` VARCHAR(255)    NULL,
  `takedown_at`     DATETIME        NULL,
  `takedown_by`     BIGINT UNSIGNED NULL,
  `media_reviewed_at` DATETIME      NULL COMMENT '人工媒体审核时间；为空表示待审核',
  `media_reviewed_by` BIGINT UNSIGNED NULL COMMENT '审核管理员 ID',
  `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_country` (`user_id`, `country_code`, `deleted`),
  KEY `idx_user_trip` (`user_id`, `trip_id`, `deleted`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_takedown` (`takedown`, `deleted`),
  KEY `idx_media_review` (`media_reviewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游记';

-- 分享令牌
CREATE TABLE `note_share` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `note_id`    BIGINT UNSIGNED NOT NULL,
  `token`      VARCHAR(64)     NOT NULL COMMENT '分享 URL 中的随机串',
  `expire_at`  DATETIME        NULL COMMENT 'NULL = 永久有效',
  `revoked`    TINYINT(1)      NOT NULL DEFAULT 0,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_at` DATETIME        NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_note_revoked` (`note_id`, `revoked`),
  KEY `idx_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游记分享令牌';
```

### 4.6 记账

```sql
-- 消费记录
CREATE TABLE `expense` (
  `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`                 BIGINT UNSIGNED NOT NULL,
  `trip_id`                 BIGINT UNSIGNED NULL COMMENT 'NULL = 未归组',
  `country_code`            CHAR(2)         NOT NULL COMMENT '必选',
  `city_code`               VARCHAR(8)      NULL COMMENT '可选',
  `currency`                CHAR(3)         NOT NULL COMMENT 'ISO 4217',
  `amount`                  DECIMAL(12,2)   NOT NULL COMMENT '原币金额，可为负（退款）',
  `exchange_rate_snapshot`  DECIMAL(12,6)   NULL COMMENT '保存时的汇率值，不存 ID；NULL=未配置',
  `amount_cny`              DECIMAL(12,2)   GENERATED ALWAYS AS (
                              CASE WHEN `exchange_rate_snapshot` IS NULL THEN NULL
                                   ELSE ROUND(`amount` * `exchange_rate_snapshot`, 2) END
                            ) STORED COMMENT '折算 CNY，自动计算',
  `category`                VARCHAR(16)     NOT NULL COMMENT 'TRANSPORT/ACCOMMODATION/FOOD/TICKET/SHOPPING/COMMUNICATION/OTHER',
  `expense_date`            DATE            NOT NULL,
  `remark`                  VARCHAR(100)    NULL,
  `created_at`              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`                 TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_country_date` (`user_id`, `deleted`, `country_code`, `expense_date`),
  KEY `idx_user_trip` (`user_id`, `trip_id`, `deleted`),
  KEY `idx_user_city` (`user_id`, `city_code`, `deleted`),
  KEY `idx_user_currency` (`user_id`, `currency`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消费记录';

-- 汇率表（当前生效值）
CREATE TABLE `exchange_rate` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `currency`    CHAR(3)         NOT NULL,
  `rate_to_cny` DECIMAL(12,6)   NOT NULL COMMENT '1 单位外币 = 多少 CNY',
  `updated_by`  BIGINT UNSIGNED NULL,
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_currency` (`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定汇率表';
```

**关于 `amount_cny` 生成列**：用 `STORED` 生成列而不是应用层写入，可保证"金额 × 快照"永远一致，避免编辑时漏更新。汇总时直接 `SUM(amount_cny)` 即可，`SUM` 会自动忽略 NULL。

**关于 `exchange_rate_snapshot` 存值不存 ID**：汇率表变更后，历史消费的折算不受影响；这正是需求 BR-005 要求的"汇率变更不回溯"。

### 4.7 商业化与配置

```sql
-- 卡密
CREATE TABLE `card_key` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `batch_id`   VARCHAR(32)     NOT NULL COMMENT '批次号，用于按批导出',
  `code`       VARCHAR(32)     NOT NULL COMMENT '如 TM-7K2P-9QX4-RT8M；前缀最长 6 + 分隔符 + 16 位分组随机码，最长 26',
  `type`       VARCHAR(16)     NOT NULL COMMENT 'AI_QUOTA / VIP',
  `value`      INT             NOT NULL COMMENT 'AI_QUOTA 为次数，VIP 为天数',
  `status`     VARCHAR(16)     NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED/USED/VOID/EXPIRED',
  `expire_at`  DATETIME        NULL COMMENT 'NULL = 永久有效',
  `used_by`    BIGINT UNSIGNED NULL,
  `used_at`    DATETIME        NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_batch_status` (`batch_id`, `status`),
  KEY `idx_status_expire` (`status`, `expire_at`),
  KEY `idx_used_by` (`used_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡密';

-- 次数包批次（支撑 365 天过期 + 先进先出扣减）
CREATE TABLE `quota_pack` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `card_key_id`   BIGINT UNSIGNED NULL COMMENT '来源卡密；管理员直接调整为 NULL',
  `granted_count` INT             NOT NULL COMMENT '本批次授予次数',
  `used_count`    INT             NOT NULL DEFAULT 0 COMMENT '本批次已用次数',
  `expire_at`     DATETIME        NOT NULL COMMENT 'granted 后 365 天',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_expire` (`user_id`, `expire_at`, `used_count`),
  KEY `idx_card` (`card_key_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='次数包批次';

-- 额度流水（只追加）
CREATE TABLE `quota_ledger` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `type`       VARCHAR(16)     NOT NULL COMMENT 'AI_QUOTA / VIP',
  `delta`      INT             NOT NULL COMMENT '正数增加，负数扣减',
  `source`     VARCHAR(24)     NOT NULL COMMENT 'REDEEM/ADMIN/CONSUME/REFUND/CANCEL',
  `ref_id`     VARCHAR(64)     NULL COMMENT 'card_key.id 或 AI requestId',
  `remark`     VARCHAR(255)    NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_ref` (`ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='额度与VIP流水（只追加）';

-- 系统配置（LLM / 搜索 / 存储）
CREATE TABLE `sys_config` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `config_key`   VARCHAR(64)     NOT NULL COMMENT 'llm.* / search.* / storage.*',
  `config_value` TEXT            NULL COMMENT '敏感项为 AES 密文',
  `encrypted`    TINYINT(1)      NOT NULL DEFAULT 0,
  `masked`       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '页面是否掩码展示',
  `remark`       VARCHAR(255)    NULL,
  `updated_by`   BIGINT UNSIGNED NULL,
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态配置';

-- 审计日志（只追加）
CREATE TABLE `audit_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED NULL COMMENT '操作人；系统任务或匿名请求为 NULL',
  `actor_role`  VARCHAR(16)     NULL COMMENT 'ADMIN/USER/SYSTEM/ANONYMOUS，写入时快照',
  `action`      VARCHAR(64)     NOT NULL COMMENT '如 CARD_REDEEM / USER_BAN / NOTE_UPDATE',
  `target_type` VARCHAR(32)     NULL COMMENT 'CARD / USER / NOTE / CONFIG / RATE / FEEDBACK / REPORT / TRIP / ITINERARY / EXPENSE',
  `target_id`   BIGINT UNSIGNED NULL,
  `detail`      JSON            NULL COMMENT '脱敏后的操作详情，只含 ID/字段名/数量/枚举',
  `ip`          VARCHAR(45)     NULL COMMENT '支持 IPv6',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_actor_time` (`actor_role`, `created_at`),
  KEY `idx_action_time` (`action`, `created_at`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

-- 登录日志（只追加）：记录每次登录尝试，成功与失败都保留。
-- 不保存密码、令牌；username 为登录时提交的账号快照（邮箱已归一化）。
CREATE TABLE `login_log` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NULL COMMENT '解析到的用户；账号不存在时为 NULL',
  `username`   VARCHAR(64)     NOT NULL COMMENT '登录时提交的账号快照',
  `role`       VARCHAR(16)     NULL COMMENT 'ADMIN / USER；已解析用户时记录',
  `success`    TINYINT(1)      NOT NULL COMMENT '1=成功，0=失败',
  `reason`     VARCHAR(32)     NULL COMMENT 'SUCCESS / USER_NOT_FOUND / BAD_CREDENTIALS / ACCOUNT_BANNED / ACCOUNT_DELETED / EMAIL_NOT_VERIFIED / RATE_LIMITED',
  `ip`         VARCHAR(45)     NULL COMMENT '支持 IPv6',
  `user_agent` VARCHAR(255)    NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_username_time` (`username`, `created_at`),
  KEY `idx_success_time` (`success`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志';
```

### 4.7.1 用户反馈

```sql
-- 用户反馈：登录用户提交，管理员在后台筛选与处理。
-- 不存 IP；不做软删除，反馈没有用户删除入口。
CREATE TABLE `feedback` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED NOT NULL,
  `type`        VARCHAR(16)     NOT NULL COMMENT 'FEATURE / BUG / UX / OTHER',
  `title`       VARCHAR(50)     NOT NULL,
  `content`     TEXT            NOT NULL,
  `contact`     VARCHAR(100)    NULL,
  `page_path`   VARCHAR(255)    NULL,
  `user_agent`  VARCHAR(512)    NULL,
  `app_version` VARCHAR(32)     NULL,
  `status`      VARCHAR(16)     NOT NULL DEFAULT 'NEW' COMMENT 'NEW / PROCESSING / RESOLVED',
  `admin_note`  VARCHAR(1000)   NULL,
  `handled_by`  BIGINT UNSIGNED NULL,
  `handled_at`  DATETIME        NULL,
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_type_time` (`type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户反馈';
```

### 4.7.2 游记举报

```sql
-- 游记举报：分享页匿名/登录用户提交，管理员在后台处理。
-- 不存 IP；不做软删除。
CREATE TABLE `note_report` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `note_id`           BIGINT UNSIGNED NOT NULL,
  `reporter_user_id`  BIGINT UNSIGNED NULL COMMENT '登录举报人；匿名为 NULL',
  `reason`            VARCHAR(16)     NOT NULL COMMENT 'SPAM/ABUSE/PORN/ILLEGAL/PRIVACY/OTHER',
  `description`       VARCHAR(200)    NULL,
  `note_title`        VARCHAR(50)     NOT NULL COMMENT '举报时的标题快照',
  `note_content_text` TEXT            NULL COMMENT '举报时的纯文本摘要快照',
  `note_updated_at`   DATETIME        NULL COMMENT '举报时的游记更新时间',
  `status`            VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
  `handled_by`        BIGINT UNSIGNED NULL,
  `handled_at`        DATETIME        NULL,
  `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_note_time` (`note_id`, `created_at`),
  KEY `idx_reporter_note` (`reporter_user_id`, `note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游记举报';
```

### 4.8 可选：汇率变更历史

> 若希望"汇率变更留痕"在库内可直接查询（而不只依赖 `audit_log`），可启用此表。V1 可选。

```sql
CREATE TABLE `exchange_rate_history` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `currency`   CHAR(3)         NOT NULL,
  `old_rate`   DECIMAL(12,6)   NULL,
  `new_rate`   DECIMAL(12,6)   NOT NULL,
  `changed_by` BIGINT UNSIGNED NOT NULL,
  `changed_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_currency_time` (`currency`, `changed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率变更历史';
```

### 4.9 通知公告

```sql
-- 通知公告：正文为纯文本，保留换行；生效时间存 epoch 毫秒，避免数据库时区差异。
CREATE TABLE `announcement` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`      VARCHAR(100)    NOT NULL,
  `content`    TEXT            NOT NULL COMMENT '纯文本正文，保留换行',
  `type`       VARCHAR(16)     NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE/ANNOUNCEMENT',
  `pinned`     TINYINT(1)      NOT NULL DEFAULT 0,
  `status`     VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE',
  `publish_at` BIGINT          NULL COMMENT '开始生效时间（epoch 毫秒）',
  `expire_at`  BIGINT          NULL COMMENT '结束生效时间（epoch 毫秒）',
  `created_by` BIGINT UNSIGNED NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `publish_at`, `expire_at`),
  KEY `idx_pinned_time` (`pinned`, `publish_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知公告';

-- 公告已读：按公告与用户逐条记录；重复打开幂等。
CREATE TABLE `announcement_read` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `announcement_id` BIGINT UNSIGNED NOT NULL,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `read_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_announcement_user` (`announcement_id`, `user_id`),
  KEY `idx_user_time` (`user_id`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告已读';
```

---

## 5. 索引策略说明

### 5.1 索引设计原则

1. **行级隔离优先**：几乎所有用户数据查询都带 `user_id`，因此复合索引以 `user_id` 打头。
2. **软删除前置**：`deleted` 紧跟 `user_id` 或放在常用过滤位置，避免扫描已删数据。
3. **覆盖高频过滤**：`country_code`、`expense_date`、`status` 等出现在索引中。
4. **不建冗余索引**：`idx_user_country` 已覆盖 `(user_id, ...)` 前缀，不再单独建 `idx_user_id`。

### 5.2 关键索引与对应查询

| 表 | 索引 | 服务的查询 |
|---|---|---|
| `user` | `uk_username` / `uk_email` | 登录、注册查重 |
| `user` | `idx_status` | 后台按状态筛选 |
| `trip` | `idx_user_country` | 抽屉里的旅行列表、手动归组下拉 |
| `itinerary` | `idx_user_country` | 地图统计、抽屉组装 |
| `itinerary` | `idx_user_trip` | 按旅行分组统计 |
| `itinerary_day` | `uk_itin_day` | 按天序号更新、防重复 |
| `itinerary_spot` | `idx_day_sort` | 天内排序读取 |
| `itinerary_spot` | `idx_day_source` | 手动景点计数（≤30 校验） |
| `trip_checklist` | `uk_user_itin_item` | upsert 命中 |
| `travel_note` | `idx_user_country` | 地图绿色判定、抽屉 |
| `travel_note` | `idx_takedown` | 后台下架列表 |
| `note_share` | `uk_token` | 分享页按 token 查 |
| `expense` | `idx_user_country_date` | 抽屉花费、日期区间汇总 |
| `expense` | `idx_user_trip` | 按旅行拆分统计 |
| `quota_pack` | `idx_user_expire` | 扣减时按过期时间升序找可用批次 |
| `country` | `uk_code` / `idx_status_sort` | 国家下拉、名称解析 |
| `card_key` | `uk_code` | 兑换查码 |
| `sys_config` | `uk_key` | 配置读取 |
| `audit_log` | `idx_user_time` / `idx_action_time` | 审计检索 |
| `feedback` | `idx_user_time` | 用户本人的反馈历史 |
| `feedback` | `idx_status_time` / `idx_type_time` | 后台按状态/分类筛选，按时间倒序 |
| `note_report` | `idx_status_time` | 后台按举报状态筛选，按时间倒序 |
| `note_report` | `idx_note_time` | 同一游记的举报列表与联动处理 |
| `note_report` | `idx_reporter_note` | 登录用户待处理举报去重 |
| `announcement` | `idx_status_time` / `idx_pinned_time` | 用户端生效公告、置顶排序 |
| `announcement_read` | `uk_announcement_user` | 已读幂等；按用户查已读集合 |

### 5.3 唯一索引与软删除的冲突处理

唯一索引**不含** `deleted`，因此软删除后重新插入相同业务键会冲突。各表处理方式：

| 表 | 唯一键 | 处理方式 |
|---|---|---|
| `user` | `username` / `email` | 注销时改写为 `deleted_{id}` / `deleted_{id}@deleted.local`，天然释放原值 |
| `city` | `(country_code, city_code)` | 城市不软删除，只停用 |
| `itinerary_day` | `(itinerary_id, day_index)` | 子表物理删除，无冲突 |
| `trip_checklist` | `(user_id, itinerary_id, item_key)` | upsert 时显式 `deleted = 0`，复用同一行 |
| `card_key` | `code` | 不软删除，只改 `status` |
| `note_share` | `token` | 不删除，只改 `revoked` |

### 5.4 索引维护提醒

- 单表数据量：V1 目标 < 10 万行/表，索引数量控制在 5 个以内
- `expense` 的四个索引已覆盖主要维度，新增报表需求时先看能否复用
- 若后续按 `country_code` 做全局统计（非用户维度），再评估加 `idx_country_date`

---

## 6. 初始化数据

### 6.1 行前清单项（不建表）

固定 7 项由后端枚举定义，**不入库**：

| `item_key` | 中文标签 |
|---|---|
| `VISA` | 签证已办 |
| `FLIGHT` | 机票已订 |
| `HOTEL` | 住宿已订 |
| `INSURANCE` | 保险已买 |
| `CURRENCY` | 货币已换 |
| `PLUG` | 插头转换器已备 |
| `SIM` | 电话卡/流量已办 |

`trip_checklist` 只存用户**实际勾选过**的行；未勾选的项由接口补默认值。

### 6.2 汇率初始数据

> **示例值，上线前必须由管理员按实际汇率更新**。CNY 固定为 1.000000。

```sql
INSERT INTO `exchange_rate` (`currency`, `rate_to_cny`) VALUES
('CNY', 1.000000),
('USD', 7.200000),
('JPY', 0.048000),
('EUR', 7.800000),
('GBP', 9.100000),
('HKD', 0.920000),
('TWD', 0.225000),
('THB', 0.200000),
('SGD', 5.350000),
('MYR', 1.530000),
('KRW', 0.005300),
('AUD', 4.700000),
('CAD', 5.250000),
('CHF', 8.100000),
('NZD', 4.300000),
('AED', 1.960000),
('MOP', 0.890000),
('VND', 0.000285),
('IDR', 0.000450),
('PHP', 0.126000);
```

### 6.3 系统配置初始数据

```sql
-- LLM 配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `encrypted`, `masked`, `remark`) VALUES
('llm.provider',        'qwen',                                              0, 0, '当前 LLM 供应商'),
('llm.base-url',        'https://dashscope.aliyuncs.com/compatible-mode/v1', 0, 0, 'OpenAI 兼容端点'),
('llm.model',           'qwen-max',                                         0, 0, '模型名'),
('llm.api-key',         '',                                                 1, 1, 'AES 加密存储'),
('llm.timeout-seconds', '30',                                               0, 0, '单次调用超时'),
('llm.max-retry',       '1',                                                0, 0, 'JSON 解析失败重试次数'),
-- 搜索配置
('search.provider',        'tavily',        0, 0, '搜索供应商'),
('search.api-key',         '',              1, 1, 'AES 加密存储'),
('search.daily-limit',     '50',            0, 0, '每用户每日搜索上限'),
('search.timeout-seconds', '5',             0, 0, '搜索独立超时'),
('search.cache-hours',     '24',            0, 0, 'guides 缓存时长'),
('search.domain-whitelist','',              0, 0, '外链域名白名单，逗号分隔；V1 只记录不强制拦截'),
-- 存储配置（开发默认 MinIO）
('storage.provider',      'minio',                    0, 0, 'oss / minio'),
('storage.endpoint',      'http://127.0.0.1:9000',    0, 0, '服务端点'),
('storage.region',        '',                         0, 0, 'MinIO 可空'),
('storage.bucket',        'travelmapper',             0, 0, '桶名'),
('storage.access-key',    'minioadmin',               1, 1, 'AES 加密存储'),
('storage.secret-key',    'minioadmin',               1, 1, 'AES 加密存储'),
('storage.path-style',    'true',                     0, 0, 'MinIO=true，云 OSS=false'),
('storage.public-domain', '',                         0, 0, '可选 CDN 域名'),
-- 业务参数
('quota.free-limit',   '3',   0, 0, '每日免费 AI 次数'),
('quota.vip-daily',    '200', 0, 0, 'VIP 日上限兜底'),
('quota.pack-valid-days','365', 0, 0, '次数包有效期'),
('share.default-days', '7',   0, 0, '分享令牌默认有效期'),
('spot.manual-limit',  '30',  0, 0, '每条行程手动景点上限');
```

> `llm.api-key`、`search.api-key`、`storage.access-key`、`storage.secret-key` 初始为空或占位值，**由管理员在后台填写**，写入时 AES 加密。

### 6.4 国家与城市初始数据

完整初始化按 SRS 补丁版执行（GeoNames 导出，中国地级市 + 国外常用旅游城市，预计 2–3 小时）。**国家表先于城市表导入**。

- `country` 导入**全量 ISO 3166-1 国家/地区**（约 250 条，含常用 195 个），一次性脚本写入；V1 后台不提供国家增删改，避免与城市引用产生悬空数据
- 若确需新增国家，走运维脚本补数据；后台只维护 `city`
- 以下是格式样例，全量数据通过导入脚本写入：

```sql
-- 国家字典（样例）
INSERT INTO `country` (`code`, `name_zh`, `name_en`, `continent`, `status`, `sort_order`) VALUES
('CN', '中国',     'China',        'ASIA', 'ENABLED', 1),
('JP', '日本',     'Japan',        'ASIA', 'ENABLED', 2),
('TH', '泰国',     'Thailand',     'ASIA', 'ENABLED', 3),
('KR', '韩国',     'South Korea',  'ASIA', 'ENABLED', 4),
('SG', '新加坡',   'Singapore',    'ASIA', 'ENABLED', 5),
('FR', '法国',     'France',       'EUROPE', 'ENABLED', 10),
('IT', '意大利',   'Italy',        'EUROPE', 'ENABLED', 11),
('US', '美国',     'United States','NORTH_AMERICA', 'ENABLED', 20),
('AU', '澳大利亚', 'Australia',    'OCEANIA', 'ENABLED', 30);

INSERT INTO `city` (`country_code`, `city_code`, `name`, `name_en`, `level`, `status`, `sort_order`) VALUES
-- 中国（地级市）
('CN', 'BJS', '北京', 'Beijing',   'CITY', 'ENABLED', 1),
('CN', 'SHA', '上海', 'Shanghai',  'CITY', 'ENABLED', 2),
('CN', 'CAN', '广州', 'Guangzhou', 'CITY', 'ENABLED', 3),
('CN', 'SZX', '深圳', 'Shenzhen',  'CITY', 'ENABLED', 4),
('CN', 'CDU', '成都', 'Chengdu',   'CITY', 'ENABLED', 5),
('CN', 'HGH', '杭州', 'Hangzhou',  'CITY', 'ENABLED', 6),
-- 日本（主要城市）
('JP', 'TYO', '东京', 'Tokyo',  'MAJOR', 'ENABLED', 1),
('JP', 'OSA', '大阪', 'Osaka',  'MAJOR', 'ENABLED', 2),
('JP', 'KYO', '京都', 'Kyoto',  'MAJOR', 'ENABLED', 3),
('JP', 'SPK', '札幌', 'Sapporo','MAJOR', 'ENABLED', 4),
-- 泰国
('TH', 'BKK', '曼谷', 'Bangkok', 'MAJOR', 'ENABLED', 1),
('TH', 'CNX', '清迈', 'Chiang Mai', 'MAJOR', 'ENABLED', 2),
('TH', 'HKT', '普吉', 'Phuket',  'MAJOR', 'ENABLED', 3);
```

**导入顺序**：`country` → `city` → `exchange_rate` → `sys_config` → 管理员账号。

**一致性校验**（导入后执行，应返回 0 行）：

```sql
SELECT DISTINCT c.country_code
FROM city c
LEFT JOIN country ct ON ct.code = c.country_code
WHERE ct.code IS NULL;
```

**城市码约定**

- 取 IATA 三字码（有则用），无则自定义 3 位大写字母
- 同一国家内唯一，跨国家可重复（如多地都有 `PAR` 语义时按国家区分，由 `(country_code, city_code)` 约束）
- 不使用纯数字码，便于日志排查

### 6.5 管理员账号初始数据

管理员由数据库预置，**不开放注册**。密码为 BCrypt 哈希，初始化时用一次性脚本生成：

```sql
-- 示例：密码明文 Admin@123456（首次登录后必须修改）
INSERT INTO `user` (`username`, `email`, `password_hash`, `nickname`, `role`, `status`, `email_verified`)
VALUES (
  'admin',
  'admin@travelmapper.local',
  '$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH',
  '平台管理员',
  'ADMIN',
  'NORMAL',
  1
);
```

**操作要求**

- 密码哈希必须由 `BCryptPasswordEncoder` 生成，不可手写
- 初始化脚本不进入 Git 生产分支；写入部署文档的"首次部署"章节
- 管理员 V1 不支持自助找回密码，遗忘时走运维重置（见部署文档）

---

## 7. 核心查询示例

> 所有示例省略了参数绑定语法细节，以 `?` 表示占位符。

### 7.1 地图三态统计

```sql
-- 行程聚合（只看已保存、未删除）
SELECT country_code,
       COUNT(*) AS itinerary_count,
       MAX(CASE WHEN end_date >= CURDATE() THEN 1 ELSE 0 END) AS has_unexpired
FROM itinerary
WHERE user_id = ? AND deleted = 0 AND status = 'SAVED'
GROUP BY country_code;

-- 游记聚合（含私密）
SELECT country_code, COUNT(*) AS note_count
FROM travel_note
WHERE user_id = ? AND deleted = 0
GROUP BY country_code;
```

应用层合并：`note_count > 0` → `GREEN`；否则 `itinerary_count > 0` → `YELLOW`（`has_unexpired = 0` 时标 `expired=true`）；都无则不返回。

### 7.1.1 重算旅行日期区间

```sql
-- 行程保存/编辑/增删天/删除后调用
UPDATE trip t
SET start_date = (SELECT MIN(i.start_date) FROM itinerary i
                  WHERE i.trip_id = t.id AND i.deleted = 0),
    end_date   = (SELECT MAX(i.end_date)   FROM itinerary i
                  WHERE i.trip_id = t.id AND i.deleted = 0)
WHERE t.id = ?;
```

**说明**：`trip.start_date/end_date` 是派生字段，只为抽屉排序与展示；若无关联行程则置 NULL。也可在查询时用 MIN/MAX 聚合，但会让抽屉查询多一次聚合，故选择写入时维护。

### 7.2 国家抽屉：旅行列表

```sql
SELECT t.id, t.name, t.start_date, t.end_date,
       (SELECT COUNT(*) FROM itinerary i
         WHERE i.trip_id = t.id AND i.deleted = 0) AS itinerary_count,
       (SELECT COUNT(*) FROM travel_note n
         WHERE n.trip_id = t.id AND n.deleted = 0) AS note_count
FROM trip t
WHERE t.user_id = ? AND t.country_code = ? AND t.deleted = 0
ORDER BY t.start_date DESC, t.created_at DESC;
```

### 7.3 按旅行拆分花费

```sql
SELECT COALESCE(trip_id, 0) AS trip_key,
       SUM(amount_cny)      AS total_cny,
       SUM(CASE WHEN exchange_rate_snapshot IS NULL THEN 1 ELSE 0 END) AS missing_rate_count
FROM expense
WHERE user_id = ? AND country_code = ? AND deleted = 0
GROUP BY COALESCE(trip_id, 0);
```

`SUM(amount_cny)` 自动忽略 NULL，未配置汇率的记录不污染总额；`missing_rate_count` 用于前端提示。

### 7.4 按国家 + 分类汇总（饼图）

```sql
SELECT country_code,
       category,
       SUM(amount_cny) AS total_cny
FROM expense
WHERE user_id = ? AND deleted = 0
  AND expense_date BETWEEN ? AND ?
GROUP BY country_code, category;
```

### 7.5 手动景点计数（≤30 校验）

```sql
SELECT COUNT(*) AS manual_count
FROM itinerary_spot s
JOIN itinerary_day d ON d.id = s.day_id
WHERE d.itinerary_id = ? AND s.source = 'MANUAL';
```

若需并发安全，套一层事务并加行锁：

```sql
SELECT COUNT(*) FROM itinerary_spot s
JOIN itinerary_day d ON d.id = s.day_id
WHERE d.itinerary_id = ? AND s.source = 'MANUAL'
FOR UPDATE;
```

### 7.6 数据一致性巡检

```sql
-- 1) 孤儿天：所属行程已不存在
SELECT d.id FROM itinerary_day d
LEFT JOIN itinerary i ON i.id = d.itinerary_id
WHERE i.id IS NULL;

-- 2) 孤儿景点：所属天已不存在
SELECT s.id FROM itinerary_spot s
LEFT JOIN itinerary_day d ON d.id = s.day_id
WHERE d.id IS NULL;

-- 3) 挂到已删除旅行上的数据（应已置 NULL，异常则修复）
SELECT 'itinerary' AS t, id FROM itinerary WHERE deleted = 0 AND trip_id IS NOT NULL
  AND trip_id NOT IN (SELECT id FROM trip WHERE deleted = 0)
UNION ALL
SELECT 'note', id FROM travel_note WHERE deleted = 0 AND trip_id IS NOT NULL
  AND trip_id NOT IN (SELECT id FROM trip WHERE deleted = 0)
UNION ALL
SELECT 'expense', id FROM expense WHERE deleted = 0 AND trip_id IS NOT NULL
  AND trip_id NOT IN (SELECT id FROM trip WHERE deleted = 0);
```

### 7.6.1 次数包可用余额（未过期批次之和）

```sql
SELECT COALESCE(SUM(granted_count - used_count), 0) AS balance,
       MIN(expire_at) AS nearest_expire_at
FROM quota_pack
WHERE user_id = ?
  AND used_count < granted_count
  AND expire_at > NOW();
```

### 7.6.2 次数包扣减（先进先出，行锁）

```sql
-- 事务内，先锁住最早过期的可用批次
SELECT id, granted_count, used_count
FROM quota_pack
WHERE user_id = ?
  AND used_count < granted_count
  AND expire_at > NOW()
ORDER BY expire_at ASC, id ASC
LIMIT 1
FOR UPDATE;

-- 命中后
UPDATE quota_pack
SET used_count = used_count + 1
WHERE id = ? AND used_count < granted_count;
-- 影响行数必须为 1；为 0 说明并发被抢，重试一次
```

**回滚（AI 调用失败）**

```sql
UPDATE quota_pack
SET used_count = used_count - 1
WHERE id = ? AND used_count > 0;
```

幂等由应用层保证：先读 `quota:deduct:{userId}:{requestId}`，存在才回滚，回滚后删键。

### 7.6.3 新增币种汇率时回填历史空快照

```sql
-- 仅在「该 currency 此前不存在」时执行，且必须在同一事务内
INSERT INTO exchange_rate (currency, rate_to_cny, updated_by)
VALUES (?, ?, ?);

UPDATE expense
SET exchange_rate_snapshot = ?
WHERE currency = ?
  AND exchange_rate_snapshot IS NULL
  AND deleted = 0;
-- amount_cny 是 STORED 生成列，会自动重算
-- 影响行数即接口返回的 affectedUnconfiguredRecords
```

**修改**已有币种汇率时**只执行 UPDATE exchange_rate**，不执行上面的 expense 回填。

### 7.7 卡密兑换（条件更新）

```sql
-- 影响行数 = 1 才算成功，= 0 说明已被抢用或状态不对
UPDATE card_key
SET status = 'USED', used_by = ?, used_at = NOW()
WHERE id = ? AND status = 'UNUSED'
  AND (expire_at IS NULL OR expire_at > NOW());
```

### 7.8 清单 upsert

```sql
INSERT INTO trip_checklist
  (user_id, country_code, itinerary_id, item_key, checked, deleted)
VALUES (?, ?, ?, ?, ?, 0) AS new
ON DUPLICATE KEY UPDATE
  checked = new.checked,
  deleted = 0;
```

> `AS new` 别名语法需 MySQL 8.0.19+；更早版本用 `VALUES(checked)`（将于未来移除）。

### 7.9 分享页令牌校验

```sql
SELECT s.id, s.expire_at, s.revoked,
       n.id AS note_id, n.deleted AS note_deleted, n.takedown,
       u.status AS user_status
FROM note_share s
JOIN travel_note n ON n.id = s.note_id
JOIN `user` u      ON u.id = n.user_id
WHERE s.token = ?;
```

应用层判定：`revoked=1` / `expire_at < NOW()` / `note_deleted=1` / `takedown=1` / `user_status='DELETED'` 任一成立 → `2007`。

---

## 8. 数据保留、脱敏与定时任务

### 8.1 保留策略

| 数据 | 保留期 | 说明 |
|---|---|---|
| 用户、行程、游记、消费、旅行、清单、景点 | 至用户删除或注销 | 注销后软删除 + 匿名化 |
| `quota_ledger` | 永久 | 财务与审计依据 |
| `card_key` | 永久 | 已使用不可回滚 |
| `audit_log` | 30 天 | 到期物理清理；管理员与用户操作共用 |
| `login_log` | 90 天 | 到期物理清理；失败尝试同样保留 |
| `announcement` | 永久 | 管理员软删除；生效时间实时计算 |
| `announcement_read` | 永久 | 删除公告时保留，用于历史阅读统计 |
| `feedback` | 至用户注销 | 注销时清空标题、正文、联系方式与内部备注，保留记录骨架 |
| `note_report` | 至用户注销 | 注销时举报人置空；作者注销时清空标题与纯文本快照 |
| `email_token` | 过期后 30 天清理 | 防表膨胀 |
| `note_share` | 撤销/过期后 30 天清理 | 保留短期排查窗口 |
| `user`（已注销） | 永久保留空壳 | `username=deleted_{id}`，不可检索内容 |
| `quota_pack` | 注销时物理删除 | 余额随账号失效；过期 90 天后由定时任务清理 |

### 8.2 注销脱敏要求（与设计文档 7.9 一致）

| 表 | 处理 |
|---|---|
| `user` | `status='DELETED'`、`deleted=1`、`username='deleted_{id}'`、`email='deleted_{id}@deleted.local'`、`nickname='已注销用户'`、`password_hash=''`、`vip_expire_at=NULL`、`token_version=token_version+1` |
| `itinerary` | `deleted=1`、`title='已注销行程'`、`guides_json=NULL`、`preparations_json=NULL` |
| `itinerary_spot` | `name=''`、`description=NULL`（随父行程不可见） |
| `travel_note` | `deleted=1`、`title='已注销游记'`、`content=''`、`content_text=NULL`、`images_json=NULL`、`cover_image=NULL` |
| `quota_pack` | **物理删除**该用户全部批次（余额随账号失效） |
| `expense` | `deleted=1`、`remark=NULL` |
| `trip` | `deleted=1`、`name='已注销旅行'` |
| `note_share` | `revoked=1`、`revoked_at=NOW()`、`expire_at=NOW()` |
| `trip_checklist` | `deleted=1` |
| `feedback` | `title=''`、`content=''`、`contact=NULL`、`admin_note=NULL`；保留 `type`、`status` 与时间 |
| `note_report` | `reporter_user_id=NULL`；该作者游记相关的 `note_title=''`、`note_content_text=NULL`；保留原因、状态与时间 |
| `email_token` | 全部 `used=1` |
| `announcement_read` | 保留；公告为平台内容，不随用户注销删除，统计口径按当前正常用户数计算 |
| `quota_ledger` / `audit_log` / `login_log` / `card_key` | 保留；`audit_log.detail` 脱敏且不含标题/正文/金额/备注，`actor_role` 为写入时快照；`login_log.username` 为登录快照，按 90 天保留期清理，不保存密码或令牌 |

> 全部操作在**单个事务**内完成；`token_version` 自增使旧 JWT 立即失效。

### 8.3 定时任务

| 频率 | 任务 | SQL / 动作 |
|---|---|---|
| 每小时 | 清理过期邮箱令牌 | `DELETE FROM email_token WHERE expire_at < DATE_SUB(NOW(), INTERVAL 30 DAY)` |
| 每日 03:00 | 卡密过期标记 | `UPDATE card_key SET status='EXPIRED' WHERE status='UNUSED' AND expire_at IS NOT NULL AND expire_at < NOW()` |
| 每日 03:05 | 清理已过期次数包批次 | `DELETE FROM quota_pack WHERE expire_at < DATE_SUB(NOW(), INTERVAL 90 DAY)`（保留 90 天便于对账，余额计算已按 `expire_at > NOW()` 过滤） |
| 每日 03:10 | 清理审计日志 | `DELETE FROM audit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY)` |
| 每日 03:15 | 清理登录日志 | `DELETE FROM login_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY)` |
| 每日 03:20 | 清理失效分享令牌 | `DELETE FROM note_share WHERE revoked=1 OR (expire_at IS NOT NULL AND expire_at < DATE_SUB(NOW(), INTERVAL 30 DAY))` |
| 每日 02:00 | 数据库备份 | 见第 9 章 |
| 每周日 | 一致性巡检 | 执行 7.6 的 SQL，异常告警 |

> 实现：`RetentionCleanupService`。Cron 可通过 `app.retention.email-token-cron`、`card-expire-cron`、`quota-pack-cron`、`audit-log-cron`、`login-log-cron`、`share-token-cron` 覆盖，默认值与上表一致。每个任务独立调度并捕获异常，单个任务失败不影响其他任务；数据库备份与一致性巡检仍由运维脚本承担，不在应用内实现。

---

## 9. 备份与恢复

### 9.1 每日备份

```bash
#!/bin/bash
# /opt/travelmapper/backup.sh
set -euo pipefail

DATE=$(date +%F)
BACKUP_DIR=/data/backup
mkdir -p "$BACKUP_DIR"

mysqldump \
  -h 127.0.0.1 -u "${DB_USER}" -p"${DB_PASSWORD}" \
  --single-transaction \
  --routines --triggers \
  --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  travelmapper | gzip > "$BACKUP_DIR/travelmapper_${DATE}.sql.gz"

# 同步到云 OSS（使用 ossutil / s3cmd 均可）
ossutil cp "$BACKUP_DIR/travelmapper_${DATE}.sql.gz" oss://travelmapper-backup/

# 本地保留 7 天
find "$BACKUP_DIR" -name 'travelmapper_*.sql.gz' -mtime +7 -delete
```

**crontab**

```cron
0 2 * * * /opt/travelmapper/backup.sh >> /var/log/travelmapper-backup.log 2>&1
```

### 9.2 恢复步骤

```bash
# 1. 停止应用，避免写入
systemctl stop travelmapper

# 2. 解压并导入
gunzip < travelmapper_2025-06-01.sql.gz | mysql -u root -p travelmapper

# 3. 启动应用
systemctl start travelmapper
```

**注意**

- 导入前确认目标库已存在：`CREATE DATABASE travelmapper DEFAULT CHARSET utf8mb4;`
- Redis 恢复：确认 `appendonly.aof` 完好，启动时自动重放；额度计数以 Redis 为准
- RTO 目标：手工恢复 ≤ 2 小时；RPO：数据库 ≤ 1 天，Redis ≤ 几秒

### 9.3 演练要求

| 时机 | 内容 |
|---|---|
| 开发阶段 | 完整走一次备份 → 清库 → 恢复 → 校验核心数据 |
| 上线前 | 再走一次，记录实际耗时；写入 `docs/deploy.md` |
| 上线后 | 每季度抽查一次备份文件可解压、可导入 |

---

## 10. 变更记录

| 日期 | 表/章节 | 变更内容 | 原因 |
|---|---|---|---|
| 初版 | 全部 | 建立 17 张核心表 + 1 张可选历史表（一致性修订后为 19 + 1） | 对应设计文档第 3 章 ER |
| 初版 | `expense` | `amount_cny` 采用 STORED 生成列 | 避免应用层漏算，保证金额与快照一致 |
| 初版 | 全部 | 不使用物理外键，应用层校验 | 软删除、注销批量操作、测试数据构造 |
| 初版 | `itinerary_day` / `itinerary_spot` | 子表物理删除，不设 `deleted` | 避免 `UNIQUE(itinerary_id, day_index)` 与软删除冲突 |
| 初版 | `itinerary_day` | `date` 改名为 `visit_date` | 规避 MySQL 关键字 |
| 初版 | `sys_config` | 合并设计文档的 `config_value_encrypted` 模式，改为 `config_value` + `encrypted` 标志 | 非敏感项（provider/endpoint）不应加密 |
| 初版 | `user` | 增加 `token_version` | 支撑封禁/改密/注销后 JWT 立即失效 |
| 初版 | `travel_note` | 增加 `content_text` 冗余字段 | 分享页摘要无需实时剥离 HTML |
| 初版 | 第 6.2 节 | 汇率示例值明确标注需人工更新 | 避免上线后沿用测试汇率 |
| 初版 | 第 1.5 节 | 增加 MySQL 版本要求与 5.7 兼容说明 | 本机开发环境为 MySQL 5.7.44，与 8.0 DDL 不兼容 |
| 一致性修订 | 新增 `country` 表 | 补齐国家字典，作为接口 `countryName` 的唯一来源；导入顺序改为 country → city | 此前四份文档均无国家名称来源 |
| 一致性修订 | 新增 `quota_pack` 表 | 次数包按批次记录 `granted/used/expire_at`，支撑 365 天过期与先进先出扣减；`quota_pack` 为余额真实来源，`quota_ledger` 仅为审计流水 | 单一 Redis 计数器无法实现"自兑换起 365 天过期" |
| 一致性修订 | 新增 7.6.1～7.6.3 | 补次数包余额/扣减/回滚 SQL、新增币种回填历史空快照 SQL | 落实 BR-002 与 AC-16 |
| 一致性修订 | 新增定时任务 | 清理过期 90 天以上的次数包批次 | 防表膨胀，余额计算已按过期过滤 |
| 一致性修订 | 第 3 章 | `travel_note` 说明中明确 `takedown` 与 `deleted` 是两个独立字段 | 管理员下架 ≠ 用户删除 |
| 二次校验 | `user.role` | 角色收敛为 `USER`/`ADMIN`，VIP 由 `vip_expire_at` 派生，不再作为角色值 | 避免 VIP 到期后角色与过期时间不一致 |
| 二次校验 | `itinerary_day` | 确认 API 字段 `date` ↔ DB 列 `visit_date`，需 DTO 显式映射 | MySQL 关键字规避，接口侧不暴露差异 |
| 二次校验 | `card_key.code` | 长度由 `VARCHAR(24)` 放宽到 `VARCHAR(32)` | 6 位前缀 + 分隔符 + 16 位分组（19）= 26 字符，原长度放不下 |
| 二次校验 | 注销与额度 | 注销时物理删除 `quota_pack`；兑换与管理调增写入批次 | 确保次数包资产与账号生命周期一致 |
| 二次校验 | `sys_config` | 新增 `search.domain-whitelist` | 对应需求"域名白名单后台可配，V1 不强制拦截" |
| 二次校验 | `trip` | `start_date/end_date` 明确为**派生字段**，新增 7.1.1 重算 SQL | 行程增删改后旅行日期区间必须同步，否则抽屉展示不符 |
| 二次校验 | `country` | 明确全量 ISO 3166-1 导入、后台不提供国家增删 | 避免管理员新增城市时国家悬空 |
| 用户反馈 | 新增 `feedback` 表 | 记录分类、标题、正文、联系方式、页面上下文、状态与内部备注；不存 IP、不做软删除；注销时清空内容与内部备注 | 建立用户反馈闭环（change: user-feedback） |
| 内容审核与举报 | 新增 `note_report` 表 | 记录举报原因、说明、标题/纯文本/版本时间快照、状态与处理人；不存 IP、不做软删除；注销时匿名化举报人并清空快照 | 建立内容治理闭环（change: note-moderation） |

---

**文档状态：V1.0 数据库设计（可用于建表）**  
**下一步**：执行第 4 章 DDL → 导入第 6 章初始化数据 → 跑第 7.6 节巡检 SQL 确认无脏数据 → 开始「登录 → 生成行程 → 保存 → 地图点亮」最小链路。
