#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  set +a
fi

DB_NAME="${DB_NAME:-travelmapper}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-travelmapper}"
DB_PASSWORD="${DB_PASSWORD:-travelmapper}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
S3_ENDPOINT="${S3_ENDPOINT:-http://127.0.0.1:9000}"
S3_BUCKET="${S3_BUCKET:-travelmapper}"
S3_PREFIX="${S3_PREFIX:-backups}"
S3_MODE="${S3_MODE:-auto}"

mkdir -p "$BACKUP_DIR"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"

echo "==> dumping $DB_NAME to $FILE"
if [ "${DB_IN_CONTAINER:-false}" = "true" ]; then
  CONTAINER="${DB_CONTAINER:-tm-mysql}"
  docker exec "$CONTAINER" mysqldump \
    -u"$DB_USERNAME" -p"$DB_PASSWORD" \
    --single-transaction --quick --routines --triggers --set-gtid-purged=OFF \
    "$DB_NAME" | gzip > "$FILE"
else
  mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" -p"$DB_PASSWORD" \
    --single-transaction --quick --routines --triggers --set-gtid-purged=OFF \
    "$DB_NAME" | gzip > "$FILE"
fi

gzip -t "$FILE"
echo "==> dump verified: $(du -h "$FILE" | cut -f1)"

OBJECT_KEY="${S3_PREFIX%/}/$(basename "$FILE")"

upload_aws() {
  AWS_ACCESS_KEY_ID="${S3_ACCESS_KEY:-}" AWS_SECRET_ACCESS_KEY="${S3_SECRET_KEY:-}" \
    aws --endpoint-url "$S3_ENDPOINT" s3 cp "$FILE" "s3://$S3_BUCKET/$OBJECT_KEY"
}

upload_mc() {
  mc alias set tmbackup "$S3_ENDPOINT" "${S3_ACCESS_KEY:-}" "${S3_SECRET_KEY:-}" >/dev/null
  mc cp "$FILE" "tmbackup/$S3_BUCKET/$OBJECT_KEY"
}

upload_curl_dev() {
  # Dev-only fallback for unauthenticated S3 mocks (S3Mock / anonymous MinIO).
  curl -fsS -X PUT --data-binary @"$FILE" "$S3_ENDPOINT/$S3_BUCKET/$OBJECT_KEY" >/dev/null
}

case "$S3_MODE" in
  aws) upload_aws ;;
  mc) upload_mc ;;
  curl) upload_curl_dev ;;
  auto)
    if command -v aws >/dev/null 2>&1; then
      upload_aws
    elif command -v mc >/dev/null 2>&1; then
      upload_mc
    else
      upload_curl_dev
    fi
    ;;
  *)
    echo "Unknown S3_MODE: $S3_MODE (expected aws, mc, curl or auto)" >&2
    exit 2
    ;;
esac

echo "==> uploaded: s3://$S3_BUCKET/$OBJECT_KEY"
find "$BACKUP_DIR" -type f -name '*.sql.gz' -mtime +7 -delete
echo "==> local backups older than 7 days removed"
