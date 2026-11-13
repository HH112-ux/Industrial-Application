#!/usr/bin/env bash
#
# 从 db-backup.sh 产出的 .sql.gz 恢复 MySQL。
#
# 用法：
#   ./scripts/db-restore.sh backups/zhizao_xietong-20260101-030000.sql.gz --yes
#
# 恢复会覆盖同名库中的同名表，属破坏性操作：不带 --yes 直接拒绝执行。
# 备份文件带 .sha256 时先校验，校验不通过不恢复。
# 恢复前请先停止 backend，避免应用写入与恢复过程交错。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE=(docker compose -f docker-compose.yml)

usage() {
  echo "用法：$0 <backup.sql.gz> --yes" >&2
  exit 2
}

FILE="${1:-}"
[[ -n "$FILE" ]] || usage
shift
[[ -f "$FILE" ]] || { echo "备份文件不存在：$FILE" >&2; exit 1; }

ASSUME_YES=0
for arg in "$@"; do
  case "$arg" in
    --yes) ASSUME_YES=1 ;;
    *) usage ;;
  esac
done
if [[ $ASSUME_YES -ne 1 ]]; then
  echo "拒绝执行：恢复会覆盖现有数据，确认后重新执行并加 --yes" >&2
  exit 2
fi

gzip -t "$FILE"
if [[ -f "${FILE}.sha256" ]]; then
  echo "校验 SHA-256：${FILE}.sha256"
  if command -v sha256sum >/dev/null 2>&1; then
    ( cd "$(dirname "$FILE")" && sha256sum -c "$(basename "${FILE}").sha256" )
  else
    ( cd "$(dirname "$FILE")" && shasum -a 256 -c "$(basename "${FILE}").sha256" )
  fi
fi

# dump 由 mysqldump --databases 生成，库名写在文件里；先核对目标库，避免拿其他库的备份静默覆盖
DUMP_DB="$(gzip -dc "$FILE" | awk -F'`' '/^CREATE DATABASE/{if (!db) db=$2} END{print db}')"
TARGET_DB="${DB_NAME:-zhizao_xietong}"
if [[ -z "$DUMP_DB" ]]; then
  echo "无法从备份文件识别目标库（缺少 CREATE DATABASE）" >&2
  exit 1
fi
if [[ "$DUMP_DB" != "$TARGET_DB" && "${RESTORE_ALLOW_OTHER_DB:-0}" != "1" ]]; then
  echo "拒绝执行：备份目标库为 ${DUMP_DB}，当前库为 ${TARGET_DB}；确认要跨库恢复请设置 RESTORE_ALLOW_OTHER_DB=1" >&2
  exit 1
fi
echo "目标库：${DUMP_DB}"

# 恢复前强制刷新一份当前数据，避免误用旧备份后无法回退
echo "恢复前自动备份当前库："
BACKUP_DIR="${RESTORE_PRE_BACKUP_DIR:-$ROOT_DIR/backups}" "$ROOT_DIR/scripts/db-backup.sh"

echo "开始恢复：${FILE}"
gunzip -c "$FILE" | "${COMPOSE[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'

echo "恢复完成，库内表数量："
"${COMPOSE[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema NOT IN (\"mysql\",\"information_schema\",\"performance_schema\",\"sys\")"'
