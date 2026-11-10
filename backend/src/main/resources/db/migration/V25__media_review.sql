-- 人工媒体审核：先发后审，带图片/视频的游记进入待审核列表。
-- 编辑媒体后由应用层重置为待审核；不影响游记可见性。

ALTER TABLE `travel_note`
  ADD COLUMN `media_reviewed_at` DATETIME NULL COMMENT '人工媒体审核时间；为空表示待审核',
  ADD COLUMN `media_reviewed_by` BIGINT UNSIGNED NULL COMMENT '审核管理员 ID',
  ADD KEY `idx_media_review` (`media_reviewed_at`);