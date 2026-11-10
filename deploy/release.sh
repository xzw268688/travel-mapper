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

echo "==> 校验 CI 状态"
if [ "${SKIP_CI_CHECK:-0}" = "1" ]; then
  echo "    ⚠ 已设置 SKIP_CI_CHECK=1，跳过 CI 校验（仅用于 GitHub 故障或紧急回滚）" >&2
else
  if ! command -v gh >/dev/null 2>&1; then
    echo "    未找到 gh，无法校验 CI。安装 GitHub CLI 或设置 SKIP_CI_CHECK=1。" >&2
    exit 1
  fi
  if ! gh auth status >/dev/null 2>&1; then
    echo "    gh 未登录，无法校验 CI。运行 gh auth login 或设置 SKIP_CI_CHECK=1。" >&2
    exit 1
  fi
  SHA="$(git rev-parse HEAD)"
  ROW="$(gh run list --workflow=ci.yml --commit "$SHA" --limit 1 \
    --json status,conclusion,url --jq '.[0] // empty | [.status,.conclusion,.url] | @tsv' 2>/dev/null || true)"
  if [ -z "$ROW" ]; then
    echo "    当前 commit $SHA 没有 ci.yml 运行记录。请先推送并等待 CI 完成。" >&2
    exit 1
  fi
  IFS=$'\t' read -r CI_STATUS CI_CONCLUSION CI_URL <<< "$ROW"
  if [ "$CI_STATUS" != "completed" ] || [ "$CI_CONCLUSION" != "success" ]; then
    echo "    CI 未通过（status=$CI_STATUS conclusion=$CI_CONCLUSION）" >&2
    [ -n "$CI_URL" ] && echo "    运行记录：$CI_URL" >&2
    exit 1
  fi
  echo "    CI 绿灯：$CI_URL"
fi

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