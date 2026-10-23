# 备份与恢复

## 目标

- 数据库每日备份并同步到对象存储。
- Redis 开启 AOF，额度、卡密状态与幂等键不因重启丢失。
- RPO：数据库最多 1 天，Redis 最多几秒；RTO：手工恢复 2 小时内。
- 开发阶段演练一次，上线前再演练一次。

## 每日备份

```bash
# 生产/本地通用
./scripts/backup.sh
```

脚本读取 `.env`（或环境变量）：

| 变量 | 说明 |
|---|---|
| `DB_NAME` / `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD` | 数据库连接 |
| `DB_IN_CONTAINER=true` / `DB_CONTAINER` | 通过 `docker exec` 执行 `mysqldump` |
| `BACKUP_DIR` | 本地备份目录，默认 `./backups` |
| `S3_ENDPOINT` / `S3_BUCKET` / `S3_PREFIX` | 对象存储位置 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | 对象存储凭证 |
| `S3_MODE` | `auto`（优先 aws，其次 mc）、`aws`、`mc`、`curl`（仅开发 Mock） |

本地验证（使用外部 MySQL 容器 + S3Mock）：

```bash
DB_IN_CONTAINER=true DB_CONTAINER=tm-mysql \
S3_ENDPOINT=http://127.0.0.1:19090 S3_BUCKET=travelmapper S3_MODE=curl \
./scripts/backup.sh
```

脚本会：

1. `mysqldump --single-transaction --quick --routines --triggers` 导出。
2. `gzip -t` 校验压缩包。
3. 上传到 `s3://$S3_BUCKET/$S3_PREFIX/<db>_<timestamp>.sql.gz`。
4. 删除本地超过 7 天的备份。

建议用 cron 每日执行：

```cron
30 3 * * * cd /opt/travelmapper && ./scripts/backup.sh >> /var/log/travelmapper-backup.log 2>&1
```

## 恢复步骤

1. 停止应用，避免写入。
2. 恢复数据库：

```bash
gunzip -c backups/travelmapper_20250601_030000.sql.gz | \
  mysql -h 127.0.0.1 -P 3306 -u root -p travelmapper
```

或容器内：

```bash
gunzip -c backups/travelmapper_20250601_030000.sql.gz | \
  docker exec -i tm-mysql mysql -uroot -proot travelmapper
```

3. 确认 `flyway_schema_history` 存在且版本为最新；不需要重新执行迁移。
4. 恢复 Redis AOF：停止 Redis，把备份的 `appendonly.aof` / `appendonlydir` 放回数据目录，使用 `redis-server --appendonly yes` 启动。若 Redis 数据丢失，用户额度会回落到数据库 `quota_pack` 的真实值（设计以 DB 为准）。
5. 启动应用并检查：

```bash
curl -f http://127.0.0.1:8080/actuator/health
```

6. 抽查数据：

```sql
SELECT COUNT(*) FROM country;
SELECT COUNT(*) FROM sys_config;
SELECT COUNT(*) FROM user WHERE role = 'ADMIN';
```

## Redis AOF 验证

```bash
# Redis 必须以 --appendonly yes 启动
docker run -d --name tm-redis -p 16379:6379 redis:7-alpine redis-server --appendonly yes
./scripts/verify-redis-aof.sh
```

脚本会写入一个键、重启容器、再读回；值一致才算通过。

## 管理员密码重置（V1 无自助找回）

管理员账号由数据库预置，V1 不开放自助找回。遗忘时由运维执行：

1. 用 BCrypt 生成新密码哈希（cost 10）。
2. 执行：

```sql
UPDATE user
SET password_hash = '<new-bcrypt-hash>',
    token_version = token_version + 1,
    updated_at = NOW()
WHERE username = 'admin';
```

3. 删除 Redis 用户缓存（若已接入 JWT）：`DEL user:status:<id>`、`DEL user:tv:<id>`。
4. 用新密码登录；旧 JWT 因 `token_version` 变化立即失效。

## 演练记录模板

| 日期 | 备份文件 | 恢复目标 | 数据抽查 | 健康检查 | 耗时 | 结论 |
|---|---|---|---|---|---|---|
| 2026-10-22 | `travelmapper_20261022_215523.sql.gz` | `travelmapper_restore` | country=9、sys_config=25、admin=2（含 1 个测试管理员） | `/actuator/health` = UP | 启动后 3 秒 | 通过 |
