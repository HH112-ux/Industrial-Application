#!/usr/bin/env bash
#
# 从 db-backup.sh 产出的 .sql.gz 恢复 MySQL。
#
# 用法：
#   ./scripts/db-restore.sh backups/zhizao_xietong-20260101-030000.sql.gz --yes
#
# 恢复会覆盖同名库中的同名表，属破坏性操作：不带 --yes 直接拒绝执行。
# 备份文件带 .sha256 时先校验，校验不通过不恢复。
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

# 恢复前强制刷新一份当前数据，避免误用旧备份后无法回退
echo "恢复前自动备份当前库："
BACKUP_DIR="${RESTORE_PRE_BACKUP_DIR:-$ROOT_DIR/backups}" "$ROOT_DIR/scripts/db-backup.sh"

echo "开始恢复：${FILE}"
gunzip -c "$FILE" | "${COMPOSE[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'

echo "恢复完成，库内表数量："
"${COMPOSE[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema NOT IN (\"mysql\",\"information_schema\",\"performance_schema\",\"sys\")"'
