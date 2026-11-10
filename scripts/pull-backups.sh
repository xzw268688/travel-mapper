#!/usr/bin/env bash
# 把生产服务器上的数据库备份下载到本地，保留 30 天。
# 用法：REMOTE=root@server ./scripts/pull-backups.sh
set -euo pipefail

REMOTE="${REMOTE:?用法: REMOTE=root@server ./scripts/pull-backups.sh}"
REMOTE_DIR="${REMOTE_DIR:-/root/my-docker-apps/backups}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_DIR="${LOCAL_DIR:-$ROOT_DIR/backups/prod}"

mkdir -p "$LOCAL_DIR"
scp -q "$REMOTE:$REMOTE_DIR/db-*.sql.gz" "$LOCAL_DIR/"
find "$LOCAL_DIR" -maxdepth 1 -name 'db-*.sql.gz' -mtime +30 -delete

count="$(find "$LOCAL_DIR" -maxdepth 1 -name 'db-*.sql.gz' | wc -l | tr -d ' ')"
echo "已下载到 $LOCAL_DIR，共 $count 个备份（保留 30 天）"