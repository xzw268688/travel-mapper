-- Optional table from TravelMapper DB V1.0 section 4.8.
-- Enable by adding classpath:db/optional to spring.flyway.locations.
CREATE TABLE IF NOT EXISTS `exchange_rate_history` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `currency`   CHAR(3)         NOT NULL,
  `old_rate`   DECIMAL(12,6)   NULL,
  `new_rate`   DECIMAL(12,6)   NOT NULL,
  `changed_by` BIGINT UNSIGNED NOT NULL,
  `changed_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_currency_time` (`currency`, `changed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率变更历史';
