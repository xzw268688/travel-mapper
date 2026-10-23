#!/usr/bin/env bash
# TravelMapper 本地性能基准：P95 < 500ms。
# 用法见同目录 README.md。
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

API_BASE=${API_BASE:-http://localhost:8080}
ITERATIONS=${ITERATIONS:-200}
WARMUP=${WARMUP:-20}
THRESHOLD_MS=${THRESHOLD_MS:-500}
SKIP_SEED=${SKIP_SEED:-0}
SKIP_PRESIGN=${SKIP_PRESIGN:-0}
CLEANUP=${CLEANUP:-0}
PERF_ACCOUNT=${PERF_ACCOUNT:-perfuser}
PERF_PASSWORD=${PERF_PASSWORD:-Admin@123456}
MYSQL_CONTAINER=${MYSQL_CONTAINER:-tm-mysql}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-root}
MYSQL_DATABASE=${MYSQL_DATABASE:-travelmapper}

need() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "缺少依赖：$1" >&2
        exit 1
    }
}

need curl
need awk
need sort
need sed
need grep

mysql_exec() {
    docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
        "$MYSQL_CONTAINER" mysql -uroot "$MYSQL_DATABASE" "$@"
}

echo "TravelMapper 性能基准"
echo "API: $API_BASE | 迭代: $ITERATIONS | 预热: $WARMUP | P95 阈值: ${THRESHOLD_MS}ms"
echo

if [ "$SKIP_SEED" != "1" ]; then
    need docker
    echo "写入基准数据（perfuser：100 trips / 500 notes / 30000 expenses）…"
    mysql_exec < "$SCRIPT_DIR/seed_benchmark.sql"
    echo "数据准备完成。"
    echo
fi

login_response=$(curl -sS -X POST "$API_BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"account\":\"$PERF_ACCOUNT\",\"password\":\"$PERF_PASSWORD\"}")
TOKEN=$(printf '%s' "$login_response" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
    echo "登录失败：请确认已执行 seed，且 perfuser 密码与 admin 一致。" >&2
    printf '%s\n' "$login_response" >&2
    exit 1
fi

RESULTS=$(mktemp)
trap 'rm -f "$RESULTS"' EXIT
overall=0

preflight() {
    local name=$1 method=$2 path=$3 body=${4:-}
    local response status payload
    if [ "$method" = "POST" ]; then
        response=$(curl -sS -w '\n%{http_code}' -X POST "$API_BASE$path" \
            -H "Authorization: Bearer $TOKEN" \
            -H 'Content-Type: application/json' \
            -d "$body") || return 1
    else
        response=$(curl -sS -w '\n%{http_code}' "$API_BASE$path" \
            -H "Authorization: Bearer $TOKEN") || return 1
    fi
    status=${response##*$'\n'}
    payload=${response%$'\n'*}
    if [ "$status" != "200" ] || ! printf '%s' "$payload" | grep -qE '"code"[[:space:]]*:[[:space:]]*0'; then
        echo "预检失败 [$name] HTTP $status：$path" >&2
        printf '%s\n' "$payload" | head -c 400 >&2
        echo >&2
        return 1
    fi
}

measure() {
    local method=$1 path=$2 body=${3:-}
    if [ "$method" = "POST" ]; then
        curl -s -o /dev/null -w '%{http_code} %{time_total}' -X POST "$API_BASE$path" \
            -H "Authorization: Bearer $TOKEN" \
            -H 'Content-Type: application/json' \
            -d "$body"
    else
        curl -s -o /dev/null -w '%{http_code} %{time_total}' "$API_BASE$path" \
            -H "Authorization: Bearer $TOKEN"
    fi
}

bench() {
    local name=$1 method=$2 path=$3 body=${4:-}
    local times failures=0 i result status secs n p50 p95 max sorted
    times=$(mktemp)
    for ((i = 0; i < WARMUP; i++)); do
        measure "$method" "$path" "$body" >/dev/null 2>&1 || true
    done
    for ((i = 0; i < ITERATIONS; i++)); do
        if ! result=$(measure "$method" "$path" "$body" 2>/dev/null); then
            failures=$((failures + 1))
            continue
        fi
        status=${result%% *}
        secs=${result##* }
        if [ "${status:0:1}" != "2" ]; then
            failures=$((failures + 1))
            continue
        fi
        LC_ALL=C awk -v s="$secs" 'BEGIN { printf "%.3f\n", s * 1000 }' >> "$times"
    done

    n=$(wc -l < "$times" | tr -d ' ')
    if [ "$n" -gt 0 ]; then
        sorted="$times.sorted"
        sort -n "$times" > "$sorted"
        p50=$(awk -v n="$n" 'BEGIN { idx = int((n - 1) * 0.50) + 1 } NR == idx { print; exit }' "$sorted")
        p95=$(awk -v n="$n" 'BEGIN { idx = int((n - 1) * 0.95) + 1 } NR == idx { print; exit }' "$sorted")
        max=$(tail -n 1 "$sorted")
        rm -f "$sorted"
    else
        p50="—"
        p95="—"
        max="—"
    fi
    rm -f "$times"
    printf '%s|%s|%s|%s|%s|%s\n' "$name" "$n" "$p50" "$p95" "$max" "$failures" >> "$RESULTS"
}

if [ "$SKIP_PRESIGN" = "1" ]; then
    echo "已跳过图片直传签名（SKIP_PRESIGN=1）。"
elif preflight "图片直传签名" POST /api/upload/presign \
    '{"bizType":"NOTE_IMAGE","contentType":"image/png","size":1024}'; then
    bench "图片直传签名" POST /api/upload/presign \
        '{"bizType":"NOTE_IMAGE","contentType":"image/png","size":1024}'
else
    echo "图片直传签名预检失败：请先通过 /api/admin/config/oss 配置对象存储，或设置 SKIP_PRESIGN=1。" >&2
    overall=1
fi

if preflight "游记列表（地图抽屉）" GET /api/map/country/JP; then
    bench "游记列表（地图抽屉）" GET /api/map/country/JP
else
    overall=1
fi

if preflight "消费列表" GET '/api/expenses?page=1&size=20'; then
    bench "消费列表" GET '/api/expenses?page=1&size=20'
else
    overall=1
fi

if preflight "消费汇总（按国家）" GET '/api/expenses/summary?groupBy=country'; then
    bench "消费汇总（按国家）" GET '/api/expenses/summary?groupBy=country'
else
    overall=1
fi

if preflight "消费汇总（按旅行）" GET '/api/expenses/summary?groupBy=trip'; then
    bench "消费汇总（按旅行）" GET '/api/expenses/summary?groupBy=trip'
else
    overall=1
fi

printf '\n%-26s %6s %10s %10s %10s %6s %s\n' "场景" "样本" "P50" "P95" "Max" "失败" "结论"
while IFS='|' read -r name n p50 p95 max failures; do
    verdict="OK"
    if [ "$n" = "0" ] || [ "$failures" -gt 0 ]; then
        verdict="FAIL"
        overall=1
    elif [ "$p95" != "—" ] && ! awk -v p="$p95" -v t="$THRESHOLD_MS" 'BEGIN { exit !(p <= t) }'; then
        verdict="FAIL"
        overall=1
    fi
    printf '%-26s %6s %10s %10s %10s %6s %s\n' \
        "$name" "$n" "${p50}ms" "${p95}ms" "${max}ms" "$failures" "$verdict"
done < "$RESULTS"

if [ "$overall" -ne 0 ]; then
    echo
    echo "存在失败场景或 P95 超过 ${THRESHOLD_MS}ms。" >&2
else
    echo
    echo "全部场景 P95 < ${THRESHOLD_MS}ms。"
fi

if [ "$CLEANUP" = "1" ]; then
    echo "清理基准数据…"
    mysql_exec -e "
        SET @uid = (SELECT id FROM \`user\` WHERE username = 'perfuser');
        DELETE ns FROM \`note_share\` ns
        JOIN \`travel_note\` n ON ns.note_id = n.id WHERE n.user_id = @uid;
        DELETE FROM \`travel_note\` WHERE user_id = @uid;
        DELETE FROM \`expense\` WHERE user_id = @uid;
        DELETE FROM \`trip\` WHERE user_id = @uid;
        DELETE FROM \`user\` WHERE id = @uid;
    "
fi

exit "$overall"
