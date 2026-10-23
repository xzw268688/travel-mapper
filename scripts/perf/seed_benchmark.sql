-- TravelMapper 性能基准数据（仅用于本地/性能环境）
-- 数据归属 perfuser，可重复执行：每次先清空该用户数据再写入。
-- 依赖：V1/V2 迁移已执行，admin 用户存在（复用其 BCrypt 密码，便于脚本登录）。

SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO `user` (`username`, `email`, `password_hash`, `nickname`, `role`, `status`, `email_verified`, `token_version`)
SELECT 'perfuser', 'perfuser@example.com', `password_hash`, 'perfuser', 'USER', 'NORMAL', 1, 1
FROM `user`
WHERE `username` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'perfuser');

UPDATE `user` u
JOIN `user` a ON a.`username` = 'admin'
SET u.`password_hash` = a.`password_hash`,
    u.`status` = 'NORMAL',
    u.`email_verified` = 1,
    u.`deleted` = 0
WHERE u.`username` = 'perfuser';

SET @uid = (SELECT `id` FROM `user` WHERE `username` = 'perfuser' LIMIT 1);

DELETE ns FROM `note_share` ns
JOIN `travel_note` n ON ns.`note_id` = n.`id`
WHERE n.`user_id` = @uid;
DELETE FROM `travel_note` WHERE `user_id` = @uid;
DELETE FROM `expense` WHERE `user_id` = @uid;
DELETE FROM `trip` WHERE `user_id` = @uid;

-- 100 个旅行
INSERT INTO `trip` (`user_id`, `country_code`, `name`, `start_date`, `end_date`)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
SELECT @uid,
       ELT(1 + MOD(n, 5), 'JP', 'TH', 'FR', 'US', 'CN'),
       CONCAT('Perf trip ', n),
       DATE_ADD('2021-01-01', INTERVAL n * 7 DAY),
       DATE_ADD('2021-01-01', INTERVAL n * 7 + 4 DAY)
FROM seq;

DROP TEMPORARY TABLE IF EXISTS perf_trip_map;
CREATE TEMPORARY TABLE perf_trip_map (
    rn           INT PRIMARY KEY,
    trip_id      BIGINT UNSIGNED NOT NULL,
    country_code CHAR(2) NOT NULL
);
INSERT INTO perf_trip_map (rn, trip_id, country_code)
SELECT ROW_NUMBER() OVER (ORDER BY `id`) - 1, `id`, `country_code`
FROM `trip`
WHERE `user_id` = @uid;

-- 500 篇游记（正文尽量短，模拟列表/抽屉查询）
INSERT INTO `travel_note`
    (`user_id`, `trip_id`, `country_code`, `city_code`, `title`, `content`, `content_text`, `visibility`, `created_at`)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 500
)
SELECT @uid,
       m.trip_id,
       m.country_code,
       CASE m.country_code
           WHEN 'JP' THEN 'TYO'
           WHEN 'TH' THEN 'BKK'
           WHEN 'FR' THEN 'PAR'
           WHEN 'US' THEN 'NYC'
           ELSE 'BJS'
       END,
       CONCAT('Perf note ', n),
       CONCAT('<p>Performance benchmark note ', n, '</p>'),
       CONCAT('Performance benchmark note ', n),
       'PRIVATE',
       DATE_SUB(NOW(), INTERVAL n DAY)
FROM seq
JOIN perf_trip_map m ON m.rn = MOD(seq.n, 100);

-- 30000 条消费，覆盖 7 个分类、5 个国家与 100 个旅行
INSERT INTO `expense`
    (`user_id`, `trip_id`, `country_code`, `city_code`, `currency`, `amount`,
     `exchange_rate_snapshot`, `category`, `expense_date`, `remark`)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 30000
)
SELECT @uid,
       m.trip_id,
       m.country_code,
       CASE m.country_code
           WHEN 'JP' THEN 'TYO'
           WHEN 'TH' THEN 'BKK'
           WHEN 'FR' THEN 'PAR'
           WHEN 'US' THEN 'NYC'
           ELSE 'BJS'
       END,
       CASE m.country_code
           WHEN 'JP' THEN 'JPY'
           WHEN 'TH' THEN 'THB'
           WHEN 'FR' THEN 'EUR'
           WHEN 'US' THEN 'USD'
           ELSE 'CNY'
       END,
       ROUND(50 + MOD(n * 37, 5000) / 10, 2),
       CASE m.country_code
           WHEN 'JP' THEN 0.048000
           WHEN 'TH' THEN 0.200000
           WHEN 'FR' THEN 7.800000
           WHEN 'US' THEN 7.200000
           ELSE 1.000000
       END,
       ELT(1 + MOD(n, 7),
           'TRANSPORT', 'ACCOMMODATION', 'FOOD', 'TICKET',
           'SHOPPING', 'COMMUNICATION', 'OTHER'),
       DATE_ADD('2022-01-01', INTERVAL MOD(n, 1200) DAY),
       CONCAT('perf expense ', n)
FROM seq
JOIN perf_trip_map m ON m.rn = MOD(seq.n, 100);

ANALYZE TABLE `expense`, `travel_note`, `trip`;
