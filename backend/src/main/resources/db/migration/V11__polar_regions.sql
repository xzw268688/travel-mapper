-- 由 scripts/generate-polar-regions-migration.mjs 生成，请勿手工修改。
-- 数据源：scripts/data/polar-regions.json
-- 南极洲与北极地区；世界国家数据由 V10__all_world_countries.sql 维护。
INSERT IGNORE INTO `country`
  (`code`, `name_zh`, `name_en`, `continent`, `status`, `sort_order`)
VALUES
  ('AQ', '南极洲', 'Antarctica', 'ANTARCTICA', 'ENABLED', 1300),
  ('X3', '北极地区', 'Arctic', NULL, 'ENABLED', 1301);
