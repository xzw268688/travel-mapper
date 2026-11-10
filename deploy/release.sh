#!/usr/bin/env bash
# 本地构建并发布到服务器。服务器只接收产物，不跑 Maven/npm。
#
# 用法：
#   REMOTE=root@your-server ./deploy/release.sh
#   REMOTE=root@your-server REMOTE_DIR=/root/my-docker-apps ./deploy/release.sh
#
# 前置：本地已配置 SSH 免密；服务器上已有 java-app/、nginx-html/、docker-compose.yml。
set -euo pipefail

REMOTE="${REMOTE:?用法: REMOTE=root@server ./deploy/release.sh}"
REMOTE_DIR="${REMOTE_DIR:-/root/my-docker-apps}"

cd "$(dirname "$0")/.."

echo "==> 构建后端"
(cd backend && MAVEN_SKIP_RC=1 mvn -q -DskipTests package)

JAR_PATH="$(ls -1 backend/target/travelmapper-backend-*.jar | grep -v '\.original$' | head -1)"
echo "    $JAR_PATH"

echo "==> 构建前端"
(cd frontend && npm run build)

echo "==> 上传后端 jar"
scp "$JAR_PATH" "$REMOTE:$REMOTE_DIR/java-app/app.jar"

echo "==> 上传前端 dist"
tar -C frontend/dist -czf - . \
  | ssh "$REMOTE" "find '$REMOTE_DIR/nginx-html' -mindepth 1 -delete && tar -C '$REMOTE_DIR/nginx-html' -xzf -"

echo "==> 重启 java-app"
ssh "$REMOTE" "cd '$REMOTE_DIR' && docker compose up -d --build java-app && docker image prune -f"

echo "==> 等待健康检查"
for i in $(seq 1 30); do
  status="$(ssh "$REMOTE" "docker inspect -f '{{.State.Health.Status}}' java-app 2>/dev/null || echo unknown")"
  if [ "$status" = "healthy" ]; then
    echo "    healthy"
    exit 0
  fi
  if [ "$i" -eq 30 ]; then
    echo "    60 秒内未变为 healthy，查看日志：" >&2
    echo "    ssh $REMOTE 'docker logs --tail 100 java-app'" >&2
    exit 1
  fi
  sleep 2
done