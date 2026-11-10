-- 操作日志增加操作人角色快照，用于区分管理员、用户、系统与匿名操作。
-- 角色在写入时记录，不随用户后续升/降级变化。

ALTER TABLE `audit_log`
  ADD COLUMN `actor_role` VARCHAR(16) NULL COMMENT 'ADMIN/USER/SYSTEM/ANONYMOUS' AFTER `user_id`,
  ADD KEY `idx_actor_time` (`actor_role`, `created_at`);

-- 历史记录回填：有用户按当前角色，无用户视为系统写入。
UPDATE `audit_log` a
LEFT JOIN `user` u ON u.id = a.user_id
SET a.actor_role = CASE
  WHEN a.user_id IS NULL THEN 'SYSTEM'
  WHEN u.role IS NULL THEN 'SYSTEM'
  ELSE u.role
END
WHERE a.actor_role IS NULL;