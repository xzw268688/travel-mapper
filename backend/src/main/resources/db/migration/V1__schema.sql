-- TravelMapper V1.0 schema. Mirrors TravelMapper DB V1.0 section 4.
-- MySQL 8.0, utf8mb4_0900_ai_ci.

CREATE TABLE IF NOT EXISTS `user` (
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
  `token_version`  INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '封禁/改密/注销时 +1，使旧 JWT 失效',
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

CREATE TABLE IF NOT EXISTS `email_token` (
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

CREATE TABLE IF NOT EXISTS `country` (
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

CREATE TABLE IF NOT EXISTS `city` (
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

CREATE TABLE IF NOT EXISTS `trip` (
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

CREATE TABLE IF NOT EXISTS `itinerary` (
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

CREATE TABLE IF NOT EXISTS `itinerary_day` (
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

CREATE TABLE IF NOT EXISTS `itinerary_spot` (
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

CREATE TABLE IF NOT EXISTS `country_official_link` (
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

CREATE TABLE IF NOT EXISTS `trip_checklist` (
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

CREATE TABLE IF NOT EXISTS `travel_note` (
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
  `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_country` (`user_id`, `country_code`, `deleted`),
  KEY `idx_user_trip` (`user_id`, `trip_id`, `deleted`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_takedown` (`takedown`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游记';

CREATE TABLE IF NOT EXISTS `note_share` (
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

CREATE TABLE IF NOT EXISTS `expense` (
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

CREATE TABLE IF NOT EXISTS `exchange_rate` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `currency`    CHAR(3)         NOT NULL,
  `rate_to_cny` DECIMAL(12,6)   NOT NULL COMMENT '1 单位外币 = 多少 CNY',
  `updated_by`  BIGINT UNSIGNED NULL,
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_currency` (`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定汇率表';

CREATE TABLE IF NOT EXISTS `card_key` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `batch_id`   VARCHAR(32)     NOT NULL COMMENT '批次号，用于按批导出',
  `code`       VARCHAR(32)     NOT NULL COMMENT '如 TM-7K2P-9QX4-RT8M',
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

CREATE TABLE IF NOT EXISTS `quota_pack` (
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

CREATE TABLE IF NOT EXISTS `quota_ledger` (
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

CREATE TABLE IF NOT EXISTS `sys_config` (
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

CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED NULL COMMENT '操作人；系统任务为 NULL',
  `action`      VARCHAR(64)     NOT NULL COMMENT '如 CARD_REDEEM / USER_BAN / NOTE_TAKEDOWN',
  `target_type` VARCHAR(32)     NULL COMMENT 'CARD / USER / NOTE / CONFIG / RATE',
  `target_id`   BIGINT UNSIGNED NULL,
  `detail`      JSON            NULL COMMENT '脱敏后的操作详情',
  `ip`          VARCHAR(45)     NULL COMMENT '支持 IPv6',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_action_time` (`action`, `created_at`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志';
