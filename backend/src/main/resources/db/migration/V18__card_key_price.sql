-- 卡密售价：用于批次估算收入（已用 × 售价），不代表实际收款。

ALTER TABLE `card_key`
  ADD COLUMN `price_cny` DECIMAL(10, 2) NULL COMMENT '售价（CNY），可选' AFTER `value`;