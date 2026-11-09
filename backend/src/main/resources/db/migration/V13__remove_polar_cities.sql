-- 兼容早期未发布的 V11：极地地区不预置城市。
-- 新建库不会存在这些城市；已有库中它们未被任何行程/游记/消费引用。
DELETE FROM `city`
WHERE `country_code` IN ('AQ', 'X3')
  AND `city_code` IN ('ANT', 'NPO');