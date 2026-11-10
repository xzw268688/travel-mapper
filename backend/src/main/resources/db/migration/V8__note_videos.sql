ALTER TABLE `travel_note`
  ADD COLUMN `videos_json` JSON NULL
  COMMENT '视频 objectKey 数组，≤3 个' AFTER `images_json`;