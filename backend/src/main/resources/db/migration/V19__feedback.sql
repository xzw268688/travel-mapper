-- 用户反馈：登录用户提交，管理员在后台筛选与处理。
-- 不存 IP；不做软删除，反馈没有用户删除入口。

CREATE TABLE IF NOT EXISTS `feedback` (
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