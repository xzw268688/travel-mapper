-- 行前清单：支持默认 10 项与自定义项，按行程保存。

ALTER TABLE `trip_checklist`
  MODIFY COLUMN `item_key` VARCHAR(64) NOT NULL COMMENT '默认项键或 CUSTOM_<uuid>';

ALTER TABLE `trip_checklist`
  ADD COLUMN `title` VARCHAR(64) NULL AFTER `checked`,
  ADD COLUMN `description` VARCHAR(255) NULL AFTER `title`,
  ADD COLUMN `days_before` INT NULL AFTER `description`,
  ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 AFTER `days_before`,
  ADD COLUMN `custom` TINYINT(1) NOT NULL DEFAULT 0 AFTER `sort_order`;

-- 旧七项回填标题、提前天数与排序；勾选状态保持不变。
UPDATE `trip_checklist` SET title = '签证', days_before = 60, sort_order = 10 WHERE item_key = 'VISA';
UPDATE `trip_checklist` SET title = '机票', days_before = 45, sort_order = 20 WHERE item_key = 'FLIGHT';
UPDATE `trip_checklist` SET title = '住宿', days_before = 30, sort_order = 30 WHERE item_key = 'HOTEL';
UPDATE `trip_checklist` SET title = '保险', days_before = 30, sort_order = 40 WHERE item_key = 'INSURANCE';
UPDATE `trip_checklist` SET title = '电话卡 / eSIM', days_before = 14, sort_order = 50 WHERE item_key = 'SIM';
UPDATE `trip_checklist` SET title = '换汇与银行卡', days_before = 14, sort_order = 60 WHERE item_key = 'CURRENCY';
UPDATE `trip_checklist` SET title = '转换头 + 插排', days_before = 7, sort_order = 70 WHERE item_key = 'PLUG';