-- 自动推导旅行状态所需的手动标记、历史状态回填、空旅行清理与孤儿数据归组。

ALTER TABLE `trip`
  ADD COLUMN `status_manual` TINYINT(1) NOT NULL DEFAULT 0
  COMMENT '1 = 用户手动设置状态，优先于自动推导';

-- 只有旧状态与“按记录/日期推导的完成状态”不一致时才保留为手动：
-- 旧 COMPLETED 但自动判断不是已完成，或旧 PLANNED 但自动判断已完成。
UPDATE `trip` t
LEFT JOIN (
  SELECT trip_id, MIN(d) AS record_start, MAX(d) AS record_end
  FROM (
    SELECT trip_id, DATE(created_at) AS d
    FROM travel_note
    WHERE deleted = 0 AND trip_id IS NOT NULL
    UNION ALL
    SELECT trip_id, expense_date AS d
    FROM expense
    WHERE deleted = 0 AND trip_id IS NOT NULL
  ) records
  GROUP BY trip_id
) r ON r.trip_id = t.id
SET t.status_manual = CASE
    WHEN t.status = 'COMPLETED' AND NOT (
      r.record_start IS NOT NULL AND (
        (t.end_date IS NOT NULL AND t.end_date < CURDATE())
        OR (t.end_date IS NULL AND r.record_end <= CURDATE())
      )
    ) THEN 1
    WHEN t.status = 'PLANNED' AND (
      r.record_start IS NOT NULL AND (
        (t.end_date IS NOT NULL AND t.end_date < CURDATE())
        OR (t.end_date IS NULL AND r.record_end <= CURDATE())
      )
    ) THEN 1
    ELSE 0
  END
WHERE t.deleted = 0;

-- 清理没有任何内容的旅行。
UPDATE `trip` t
SET t.deleted = 1
WHERE t.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM itinerary i WHERE i.trip_id = t.id AND i.deleted = 0
  )
  AND NOT EXISTS (
    SELECT 1 FROM travel_note n WHERE n.trip_id = t.id AND n.deleted = 0
  )
  AND NOT EXISTS (
    SELECT 1 FROM expense e WHERE e.trip_id = t.id AND e.deleted = 0
  );

-- 把现有未归组数据按“用户 + 国家”归入“未命名旅行 · yyyy-MM”。
DROP PROCEDURE IF EXISTS migrate_orphan_trips;

DELIMITER $$
CREATE PROCEDURE migrate_orphan_trips()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE v_user BIGINT;
  DECLARE v_country CHAR(2);
  DECLARE v_earliest DATE;
  DECLARE v_start DATE;
  DECLARE v_end DATE;
  DECLARE v_name VARCHAR(30);
  DECLARE v_new_trip BIGINT;

  DECLARE cur CURSOR FOR
    SELECT user_id, country_code FROM (
      SELECT user_id, country_code FROM itinerary
      WHERE trip_id IS NULL AND deleted = 0
      UNION
      SELECT user_id, country_code FROM travel_note
      WHERE trip_id IS NULL AND deleted = 0
      UNION
      SELECT user_id, country_code FROM expense
      WHERE trip_id IS NULL AND deleted = 0
    ) src;

  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_user, v_country;
    IF done = 1 THEN
      LEAVE read_loop;
    END IF;

    SELECT MIN(d) INTO v_earliest FROM (
      SELECT start_date AS d FROM itinerary
      WHERE user_id = v_user AND country_code = v_country
        AND trip_id IS NULL AND deleted = 0
      UNION ALL
      SELECT DATE(created_at) AS d FROM travel_note
      WHERE user_id = v_user AND country_code = v_country
        AND trip_id IS NULL AND deleted = 0
      UNION ALL
      SELECT expense_date AS d FROM expense
      WHERE user_id = v_user AND country_code = v_country
        AND trip_id IS NULL AND deleted = 0
    ) dates;

    SELECT MIN(start_date), MAX(end_date) INTO v_start, v_end
    FROM itinerary
    WHERE user_id = v_user AND country_code = v_country
      AND trip_id IS NULL AND deleted = 0;

    SET v_name = CONCAT('未命名旅行 · ', DATE_FORMAT(IFNULL(v_earliest, CURDATE()), '%Y-%m'));

    INSERT INTO trip (user_id, country_code, name, start_date, end_date, status, status_manual)
    VALUES (v_user, v_country, v_name, v_start, v_end, 'PLANNED', 0);

    SET v_new_trip = LAST_INSERT_ID();

    UPDATE itinerary SET trip_id = v_new_trip
    WHERE user_id = v_user AND country_code = v_country
      AND trip_id IS NULL AND deleted = 0;

    UPDATE travel_note SET trip_id = v_new_trip
    WHERE user_id = v_user AND country_code = v_country
      AND trip_id IS NULL AND deleted = 0;

    UPDATE expense SET trip_id = v_new_trip
    WHERE user_id = v_user AND country_code = v_country
      AND trip_id IS NULL AND deleted = 0;
  END LOOP;
  CLOSE cur;
END$$

DELIMITER ;

CALL migrate_orphan_trips();
DROP PROCEDURE migrate_orphan_trips;