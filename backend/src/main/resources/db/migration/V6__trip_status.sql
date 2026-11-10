-- 归档状态：计划中 / 已完成，默认计划中。
ALTER TABLE `trip`
  ADD COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'PLANNED'
  COMMENT 'PLANNED / COMPLETED' AFTER `end_date`;
