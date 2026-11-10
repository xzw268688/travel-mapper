-- 账号安全自助：TOTP 二次验证与一次性恢复码。
-- TOTP 密钥只存 AES-GCM 密文；恢复码只存 BCrypt 哈希，使用后物理删除。

ALTER TABLE `user`
  ADD COLUMN `totp_secret` VARCHAR(255) NULL COMMENT 'AES-GCM 密文；为空表示未绑定',
  ADD COLUMN `totp_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已确认绑定二次验证',
  ADD COLUMN `totp_confirmed_at` DATETIME NULL COMMENT '二次验证确认时间';

CREATE TABLE IF NOT EXISTS `user_recovery_code` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `code_hash`  VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希',
  `used_at`    DATETIME        NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TOTP 一次性恢复码';