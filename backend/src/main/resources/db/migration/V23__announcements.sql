-- 通知公告：管理员发布，登录用户在地图首页查看；正文为纯文本，保留换行。
-- 公告软删除；已读关系按公告与用户逐条记录。

CREATE TABLE IF NOT EXISTS `announcement` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`      VARCHAR(100)    NOT NULL,
  `content`    TEXT            NOT NULL COMMENT '纯文本正文，保留换行',
  `type`       VARCHAR(16)     NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE/ANNOUNCEMENT',
  `pinned`     TINYINT(1)      NOT NULL DEFAULT 0,
  `status`     VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE',
  `publish_at` BIGINT          NULL COMMENT '开始生效时间（epoch 毫秒）；为空表示发布即生效',
  `expire_at`  BIGINT          NULL COMMENT '结束生效时间（epoch 毫秒）；为空表示长期有效',
  `created_by` BIGINT UNSIGNED NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `publish_at`, `expire_at`),
  KEY `idx_pinned_time` (`pinned`, `publish_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知公告';

CREATE TABLE IF NOT EXISTS `announcement_read` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `announcement_id` BIGINT UNSIGNED NOT NULL,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `read_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_announcement_user` (`announcement_id`, `user_id`),
  KEY `idx_user_time` (`user_id`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告已读';