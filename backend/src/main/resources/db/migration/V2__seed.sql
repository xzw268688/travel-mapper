-- Repeatable-by-hand seed data. Flyway ensures single execution; INSERT IGNORE
-- keeps manual re-runs safe and never overwrites operator-edited values.

INSERT IGNORE INTO `exchange_rate` (`currency`, `rate_to_cny`) VALUES
('CNY', 1.000000),
('USD', 7.200000),
('JPY', 0.048000),
('EUR', 7.800000),
('GBP', 9.100000),
('HKD', 0.920000),
('TWD', 0.225000),
('THB', 0.200000),
('SGD', 5.350000),
('MYR', 1.530000),
('KRW', 0.005300),
('AUD', 4.700000),
('CAD', 5.250000),
('CHF', 8.100000),
('NZD', 4.300000),
('AED', 1.960000),
('MOP', 0.890000),
('VND', 0.000285),
('IDR', 0.000450),
('PHP', 0.126000);

INSERT IGNORE INTO `country` (`code`, `name_zh`, `name_en`, `continent`, `status`, `sort_order`) VALUES
('CN', '中国', 'China', 'ASIA', 'ENABLED', 1),
('JP', '日本', 'Japan', 'ASIA', 'ENABLED', 2),
('TH', '泰国', 'Thailand', 'ASIA', 'ENABLED', 3),
('KR', '韩国', 'South Korea', 'ASIA', 'ENABLED', 4),
('SG', '新加坡', 'Singapore', 'ASIA', 'ENABLED', 5),
('FR', '法国', 'France', 'EUROPE', 'ENABLED', 10),
('IT', '意大利', 'Italy', 'EUROPE', 'ENABLED', 11),
('US', '美国', 'United States', 'NORTH_AMERICA', 'ENABLED', 20),
('AU', '澳大利亚', 'Australia', 'OCEANIA', 'ENABLED', 30);

INSERT IGNORE INTO `city` (`country_code`, `city_code`, `name`, `name_en`, `level`, `status`, `sort_order`) VALUES
('CN', 'BJS', '北京', 'Beijing', 'CITY', 'ENABLED', 1),
('CN', 'SHA', '上海', 'Shanghai', 'CITY', 'ENABLED', 2),
('CN', 'CAN', '广州', 'Guangzhou', 'CITY', 'ENABLED', 3),
('CN', 'SZX', '深圳', 'Shenzhen', 'CITY', 'ENABLED', 4),
('CN', 'CDU', '成都', 'Chengdu', 'CITY', 'ENABLED', 5),
('CN', 'HGH', '杭州', 'Hangzhou', 'CITY', 'ENABLED', 6),
('JP', 'TYO', '东京', 'Tokyo', 'MAJOR', 'ENABLED', 1),
('JP', 'OSA', '大阪', 'Osaka', 'MAJOR', 'ENABLED', 2),
('JP', 'KYO', '京都', 'Kyoto', 'MAJOR', 'ENABLED', 3),
('JP', 'SPK', '札幌', 'Sapporo', 'MAJOR', 'ENABLED', 4),
('TH', 'BKK', '曼谷', 'Bangkok', 'MAJOR', 'ENABLED', 1),
('TH', 'CNX', '清迈', 'Chiang Mai', 'MAJOR', 'ENABLED', 2),
('TH', 'HKT', '普吉', 'Phuket', 'MAJOR', 'ENABLED', 3);

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `encrypted`, `masked`, `remark`) VALUES
('llm.provider',         'qwen',                                               0, 0, '当前 LLM 供应商'),
('llm.base-url',         'https://dashscope.aliyuncs.com/compatible-mode/v1', 0, 0, 'OpenAI 兼容端点'),
('llm.model',            'qwen-max',                                          0, 0, '模型名'),
('llm.api-key',          '',                                                 1, 1, 'AES 加密存储'),
('llm.timeout-seconds',  '30',                                               0, 0, '单次调用超时'),
('llm.max-retry',        '1',                                                0, 0, 'JSON 解析失败重试次数'),
('search.provider',         'tavily',        0, 0, '搜索供应商'),
('search.api-key',          '',              1, 1, 'AES 加密存储'),
('search.daily-limit',      '50',            0, 0, '每用户每日搜索上限'),
('search.timeout-seconds',  '5',             0, 0, '搜索独立超时'),
('search.cache-hours',      '24',            0, 0, 'guides 缓存时长'),
('search.domain-whitelist', '',              0, 0, '外链域名白名单，逗号分隔；V1 只记录不强制拦截'),
('storage.provider',      'minio',                    0, 0, 'oss / minio'),
('storage.endpoint',      'http://127.0.0.1:9000',    0, 0, '服务端点'),
('storage.region',        '',                         0, 0, 'MinIO 可空'),
('storage.bucket',        'travelmapper',             0, 0, '桶名'),
('storage.access-key',    '',                         1, 1, 'AES 加密存储'),
('storage.secret-key',    '',                         1, 1, 'AES 加密存储'),
('storage.path-style',    'true',                     0, 0, 'MinIO=true，云 OSS=false'),
('storage.public-domain', '',                         0, 0, '可选 CDN 域名'),
('quota.free-limit',      '3',   0, 0, '每日免费 AI 次数'),
('quota.vip-daily',       '200', 0, 0, 'VIP 日上限兜底'),
('quota.pack-valid-days', '365', 0, 0, '次数包有效期'),
('share.default-days',    '7',   0, 0, '分享令牌默认有效期'),
('spot.manual-limit',     '30',  0, 0, '每条行程手动景点上限');

-- Password: Admin@123456 (BCrypt cost 10). Rotate on first production deployment.
INSERT IGNORE INTO `user`
(`username`, `email`, `password_hash`, `nickname`, `role`, `status`, `email_verified`) VALUES
('admin', 'admin@travelmapper.local',
 '$2a$10$BOS9PybsPtIABy7GW34P2OrlHFfKvyyNVZ7GOd4y4DHjd.k1TZk3K',
 '平台管理员', 'ADMIN', 'NORMAL', 1);
