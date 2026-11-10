#!/usr/bin/env bash
# 在服务器 /root/my-docker-apps 下执行（release.sh 会自动上传本文件）。
# 用法：bash rollback.sh <timestamp>
# timestamp 取 releases/ 目录里的文件名，例如 20261109231500。
set -euo pipefail

TS="${1:?用法: bash rollback.sh <timestamp>（取 releases/ 下的备份）}"
cd "$(dirname "$0")"

JAR="releases/app-$TS.jar"
DIST="releases/frontend-$TS.tgz"
DB="releases/db-$TS.sql.gz"

[ -f "$JAR" ] || { echo "缺少 $JAR" >&2; exit 1; }

echo "==> 回滚 jar 到 $TS"
cp -f "$JAR" java-app/app.jar

if [ -f "$DIST" ]; then
  echo "==> 回滚前端到 $TS"
  find nginx-html -mindepth 1 -delete
  tar -C nginx-html -xzf "$DIST"
fi

echo "==> 重建 java-app"
docker compose up -d --build java-app

echo
echo "回滚完成。数据库未自动回滚。"
if [ -f "$DB" ]; then
  echo "如需一并恢复数据库（会覆盖当前数据，谨慎）："
  echo "  gunzip -c $DB | docker exec -i mysql-db sh -c 'exec mysql -uroot -p\$MYSQL_ROOT_PASSWORD travelmapper'"
fi