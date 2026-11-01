#!/usr/bin/env bash
# 按“国家 + 30 天窗口”重算旅行归组。执行前会自动备份数据库。
# 用法：API_BASE=http://localhost:8080 bash scripts/maintenance/recluster-trips.sh
set -euo pipefail

API_BASE=${API_BASE:-http://localhost:8080}
ADMIN_ACCOUNT=${ADMIN_ACCOUNT:-admin}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-Admin@123456}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

echo "1/2 备份数据库…"
bash "$SCRIPT_DIR/../backup.sh"

echo "2/2 触发归组重算…"
TOKEN=$(curl -sS -X POST "$API_BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"account\":\"$ADMIN_ACCOUNT\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "登录失败：请检查管理员账号密码或后端地址。" >&2
  exit 1
fi

RESPONSE=$(curl -sS -X POST "$API_BASE/api/admin/trips/recluster" \
  -H "Authorization: Bearer $TOKEN")
printf '%s\n' "$RESPONSE"
printf '%s' "$RESPONSE" | grep -q '"code":0'
echo "重算完成。"
