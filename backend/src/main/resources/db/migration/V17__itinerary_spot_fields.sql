-- 景点扩展字段：类别、门票、亮点、最佳时段、最近车站、需预约、区域、避雷备注。
-- 旧数据全部为空/未预约，展示层按空值降级，不影响既有行程。

ALTER TABLE `itinerary_spot`
  ADD COLUMN `category`            VARCHAR(32)  NULL COMMENT '类别，如博物馆/寺庙/自然' AFTER `suggested_duration`,
  ADD COLUMN `ticket_price`        VARCHAR(64)  NULL COMMENT '门票，存原币文本，如 约 2000 円' AFTER `category`,
  ADD COLUMN `highlights`          VARCHAR(255) NULL COMMENT '亮点' AFTER `ticket_price`,
  ADD COLUMN `best_time`           VARCHAR(64)  NULL COMMENT '最佳时段' AFTER `highlights`,
  ADD COLUMN `nearest_station`     VARCHAR(128) NULL COMMENT '最近车站' AFTER `best_time`,
  ADD COLUMN `reservation_required` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否需预约' AFTER `nearest_station`,
  ADD COLUMN `area`                VARCHAR(128) NULL COMMENT '区域' AFTER `reservation_required`,
  ADD COLUMN `warning_notes`       VARCHAR(255) NULL COMMENT '避雷备注' AFTER `area`;