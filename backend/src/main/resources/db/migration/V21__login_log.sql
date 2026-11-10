-- 登录日志：记录每次登录尝试（成功/失败），供管理员追溯账号安全事件。
-- 只追加；不保存密码、令牌等敏感内容。

CREATE TABLE IF NOT EXISTS `login_log` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NULL COMMENT '解析到的用户；账号不存在时为 NULL',
  `username`   VARCHAR(64)     NOT NULL COMMENT '登录时提交的账号快照（邮箱已归一化）',
  `role`       VARCHAR(16)     NULL COMMENT 'ADMIN / USER；已解析用户时记录',
  `success`    TINYINT(1)      NOT NULL COMMENT '1=成功，0=失败',
  `reason`     VARCHAR(32)     NULL COMMENT 'SUCCESS/USER_NOT_FOUND/BAD_CREDENTIALS/ACCOUNT_BANNED/ACCOUNT_DELETED/EMAIL_NOT_VERIFIED/RATE_LIMITED',
  `ip`         VARCHAR(45)     NULL COMMENT '支持 IPv6',
  `user_agent` VARCHAR(255)    NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_username_time` (`username`, `created_at`),
  KEY `idx_success_time` (`success`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志';