-- 兼容早期未发布的 V11 数据：统一极地地区的名称与属性。
-- 新建库由 V11__polar_regions.sql 写入正确值，本迁移对新建库是幂等的。
UPDATE `country`
SET `name_zh` = '南极洲', `name_en` = 'Antarctica', `continent` = 'ANTARCTICA',
    `status` = 'ENABLED', `sort_order` = 1300
WHERE `code` = 'AQ';

UPDATE `country`
SET `name_zh` = '北极地区', `name_en` = 'Arctic', `continent` = NULL,
    `status` = 'ENABLED', `sort_order` = 1301
WHERE `code` = 'X3';