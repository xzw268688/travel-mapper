-- 游记举报：分享页匿名/登录用户提交，管理员在后台处理。
-- 不存 IP；不做软删除。

CREATE TABLE IF NOT EXISTS `note_report` (
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