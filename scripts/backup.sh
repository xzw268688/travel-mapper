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

# 备份包含用户邮箱、密码哈希与游记内容，默认必须加密。
# required: 优先 age，其次 gpg；off: 仅显式允许时输出明文。
BACKUP_ENCRYPTION="${BACKUP_ENCRYPTION:-required}"
BACKUP_AGE_RECIPIENT="${BACKUP_AGE_RECIPIENT:-}"
BACKUP_GPG_RECIPIENT="${BACKUP_GPG_RECIPIENT:-}"
BACKUP_ALLOW_PLAINTEXT="${BACKUP_ALLOW_PLAINTEXT:-false}"

mkdir -p "$BACKUP_DIR"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"

echo "==> dumping $DB_NAME"
if [ "${DB_IN_CONTAINER:-false}" = "true" ]; then
  CONTAINER="${DB_CONTAINER:-tm-mysql}"
  docker exec -e MYSQL_PWD="$DB_PASSWORD" "$CONTAINER" mysqldump \
    -u"$DB_USERNAME" \
    --single-transaction --quick --routines --triggers --set-gtid-purged=OFF \
    "$DB_NAME" | gzip > "$FILE"
else
  MYSQL_PWD="$DB_PASSWORD" mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" \
    --single-transaction --quick --routines --triggers --set-gtid-purged=OFF \
    "$DB_NAME" | gzip > "$FILE"
fi

gzip -t "$FILE"
echo "==> dump verified: $(du -h "$FILE" | cut -f1)"

UPLOAD_FILE=""
case "$BACKUP_ENCRYPTION" in
  required)
    if [ -n "$BACKUP_AGE_RECIPIENT" ] && command -v age >/dev/null 2>&1; then
      UPLOAD_FILE="$FILE.age"
      age -r "$BACKUP_AGE_RECIPIENT" -o "$UPLOAD_FILE" "$FILE"
    elif [ -n "$BACKUP_GPG_RECIPIENT" ] && command -v gpg >/dev/null 2>&1; then
      UPLOAD_FILE="$FILE.gpg"
      gpg --yes --batch --encrypt --recipient "$BACKUP_GPG_RECIPIENT" --output "$UPLOAD_FILE" "$FILE"
    else
      echo "ERROR: 备份必须加密，但未配置可用的 age/gpg 收件人。" >&2
      echo "       设置 BACKUP_AGE_RECIPIENT 或 BACKUP_GPG_RECIPIENT；" >&2
      echo "       仅本地验证时可显式设置 BACKUP_ENCRYPTION=off BACKUP_ALLOW_PLAINTEXT=true。" >&2
      rm -f "$FILE"
      exit 3
    fi
    ;;
  age)
    if [ -z "$BACKUP_AGE_RECIPIENT" ] || ! command -v age >/dev/null 2>&1; then
      echo "ERROR: 使用 age 加密需要 age 命令与 BACKUP_AGE_RECIPIENT" >&2
      rm -f "$FILE"
      exit 3
    fi
    UPLOAD_FILE="$FILE.age"
    age -r "$BACKUP_AGE_RECIPIENT" -o "$UPLOAD_FILE" "$FILE"
    ;;
  gpg)
    if [ -z "$BACKUP_GPG_RECIPIENT" ] || ! command -v gpg >/dev/null 2>&1; then
      echo "ERROR: 使用 gpg 加密需要 gpg 命令与 BACKUP_GPG_RECIPIENT" >&2
      rm -f "$FILE"
      exit 3
    fi
    UPLOAD_FILE="$FILE.gpg"
    gpg --yes --batch --encrypt --recipient "$BACKUP_GPG_RECIPIENT" --output "$UPLOAD_FILE" "$FILE"
    ;;
  off)
    if [ "$BACKUP_ALLOW_PLAINTEXT" != "true" ]; then
      echo "ERROR: 明文备份需要显式设置 BACKUP_ALLOW_PLAINTEXT=true（仅限本地验证）。" >&2
      rm -f "$FILE"
      exit 3
    fi
    UPLOAD_FILE="$FILE"
    ;;
  *)
    echo "Unknown BACKUP_ENCRYPTION: $BACKUP_ENCRYPTION (expected required, age, gpg or off)" >&2
    rm -f "$FILE"
    exit 2
    ;;
esac

if [ ! -s "$UPLOAD_FILE" ]; then
  echo "ERROR: 加密备份为空：$UPLOAD_FILE" >&2
  rm -f "$FILE" "$UPLOAD_FILE"
  exit 3
fi
if [ "$UPLOAD_FILE" != "$FILE" ]; then
  # 加密成功后立即删除明文转储。
  rm -f "$FILE"
  echo "==> encrypted backup created: $(du -h "$UPLOAD_FILE" | cut -f1)"
fi

OBJECT_KEY="${S3_PREFIX%/}/$(basename "$UPLOAD_FILE")"

upload_aws() {
  AWS_ACCESS_KEY_ID="${S3_ACCESS_KEY:-}" AWS_SECRET_ACCESS_KEY="${S3_SECRET_KEY:-}" \
    aws --endpoint-url "$S3_ENDPOINT" s3 cp "$UPLOAD_FILE" "s3://$S3_BUCKET/$OBJECT_KEY"
}

upload_mc() {
  mc alias set tmbackup "$S3_ENDPOINT" "${S3_ACCESS_KEY:-}" "${S3_SECRET_KEY:-}" >/dev/null
  mc cp "$UPLOAD_FILE" "tmbackup/$S3_BUCKET/$OBJECT_KEY"
}

upload_curl_dev() {
  # Dev-only fallback for unauthenticated S3 mocks (S3Mock / anonymous MinIO).
  curl -fsS -X PUT --data-binary @"$UPLOAD_FILE" "$S3_ENDPOINT/$S3_BUCKET/$OBJECT_KEY" >/dev/null
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
find "$BACKUP_DIR" -type f \
  \( -name '*.sql.gz' -o -name '*.sql.gz.age' -o -name '*.sql.gz.gpg' \) \
  -mtime +7 -delete
echo "==> local backups older than 7 days removed"