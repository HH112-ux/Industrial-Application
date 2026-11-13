#!/usr/bin/env bash
#
# MySQL 逻辑备份：一致性快照 + gzip + SHA-256 + 过期清理。
#
# 用法：
#   ./scripts/db-backup.sh
#   BACKUP_DIR=/srv/backup BACKUP_RETENTION_DAYS=30 ./scripts/db-backup.sh
#
# 依赖 compose 中正在运行的 mysql 服务（docker compose up -d mysql）。
# 口令不经过命令行参数，容器内读取 MYSQL_ROOT_PASSWORD 写入 MYSQL_PWD，避免出现在 ps 与日志里。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE=(docker compose -f docker-compose.yml)
DB_NAME="${DB_NAME:-zhizao_xietong}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || { echo "DB_NAME 只允许字母、数字与下划线：$DB_NAME" >&2; exit 1; }
[[ "$BACKUP_RETENTION_DAYS" =~ ^[0-9]+$ ]] || { echo "BACKUP_RETENTION_DAYS 必须是整数：$BACKUP_RETENTION_DAYS" >&2; exit 1; }

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$@"
  else
    shasum -a 256 "$@"
  fi
}

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR" 2>/dev/null || true
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$BACKUP_DIR/${DB_NAME}-${STAMP}.sql.gz"
PARTIAL="${TARGET}.part"
trap 'rm -f "$PARTIAL"' EXIT

echo "备份 ${DB_NAME} -> ${TARGET}"
"${COMPOSE[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF --databases "$1"' \
  -- "$DB_NAME" | gzip -c > "$PARTIAL"

# 先校验压缩完整性再落正式文件名，避免半截文件被当成可用备份
gzip -t "$PARTIAL"
if ! gzip -dc "$PARTIAL" | grep -q '^CREATE DATABASE'; then
  echo "备份内容中没有 CREATE DATABASE，疑似导出失败：${PARTIAL}" >&2
  exit 1
fi
if ! gzip -dc "$PARTIAL" | grep -q '^CREATE TABLE'; then
  echo "警告：库内没有表（空库备份），文件仍保留：${TARGET}"
fi
mv "$PARTIAL" "$TARGET"
( cd "$BACKUP_DIR" && sha256_of "$(basename "$TARGET")" > "$(basename "$TARGET").sha256" )

find "$BACKUP_DIR" -maxdepth 1 -type f \
  \( -name "${DB_NAME}-*.sql.gz" -o -name "${DB_NAME}-*.sql.gz.sha256" \) \
  -mtime +"$BACKUP_RETENTION_DAYS" -print -delete

echo "完成：${TARGET}（$(du -h "$TARGET" | cut -f1)，保留 ${BACKUP_RETENTION_DAYS} 天）"
cat "${TARGET}.sha256"
