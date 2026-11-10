-- 修复被 V13 清理、但仍被历史行程/游记/消费引用的极地城市。
-- 新建库中不存在这些引用，因此本迁移不会插入任何城市；
-- 旧库只在确有历史引用时恢复城市，保证旧数据仍可正常展示。
INSERT INTO `city` (`country_code`, `city_code`, `name`, `name_en`, `level`, `status`, `sort_order`)
SELECT 'AQ', 'ANT', '南极半岛', NULL, 'MAJOR', 'ENABLED', 1000
WHERE (
        EXISTS (SELECT 1 FROM `itinerary_day` d
                JOIN `itinerary` i ON i.id = d.itinerary_id
                WHERE d.city_code = 'ANT' AND i.country_code = 'AQ')
        OR EXISTS (SELECT 1 FROM `travel_note` n
                   WHERE n.city_code = 'ANT' AND n.country_code = 'AQ')
        OR EXISTS (SELECT 1 FROM `expense` e
                   WHERE e.city_code = 'ANT' AND e.country_code = 'AQ')
    )
  AND NOT EXISTS (SELECT 1 FROM `city` WHERE country_code = 'AQ' AND city_code = 'ANT');

INSERT INTO `city` (`country_code`, `city_code`, `name`, `name_en`, `level`, `status`, `sort_order`)
SELECT 'X3', 'NPO', '北极点', NULL, 'MAJOR', 'ENABLED', 1000
WHERE (
        EXISTS (SELECT 1 FROM `itinerary_day` d
                JOIN `itinerary` i ON i.id = d.itinerary_id
                WHERE d.city_code = 'NPO' AND i.country_code = 'X3')
        OR EXISTS (SELECT 1 FROM `travel_note` n
                   WHERE n.city_code = 'NPO' AND n.country_code = 'X3')
        OR EXISTS (SELECT 1 FROM `expense` e
                   WHERE e.city_code = 'NPO' AND e.country_code = 'X3')
    )
  AND NOT EXISTS (SELECT 1 FROM `city` WHERE country_code = 'X3' AND city_code = 'NPO');