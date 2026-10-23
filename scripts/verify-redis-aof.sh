#!/usr/bin/env bash
set -euo pipefail

CONTAINER="${REDIS_CONTAINER:-tm-redis}"
KEY="tm:aof:check"
VALUE="$(date +%s)"

AOF="$(docker exec "$CONTAINER" redis-cli config get appendonly | tail -1 | tr -d '\r')"
if [ "$AOF" != "yes" ]; then
  echo "Redis AOF is disabled. Start Redis with: redis-server --appendonly yes" >&2
  exit 2
fi

docker exec "$CONTAINER" redis-cli set "$KEY" "$VALUE" >/dev/null
docker restart "$CONTAINER" >/dev/null

for _ in $(seq 1 30); do
  if docker exec "$CONTAINER" redis-cli ping 2>/dev/null | grep -q PONG; then
    break
  fi
  sleep 1
done

ACTUAL="$(docker exec "$CONTAINER" redis-cli get "$KEY" | tr -d '\r')"
if [ "$ACTUAL" = "$VALUE" ]; then
  echo "AOF verification passed: $KEY survived restart"
else
  echo "AOF verification failed: expected [$VALUE], got [$ACTUAL]" >&2
  exit 1
fi
