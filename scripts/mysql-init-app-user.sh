#!/bin/sh
#
# 创建后端使用的受限数据库账号（幂等，每次 compose up 都由 mysql-init 执行）。
#
# 权限按应用的真实 SQL 需求核定：
#   DML：SELECT / INSERT / UPDATE / DELETE            业务读写与逻辑删除
#   DDL：CREATE / ALTER / DROP / INDEX / REFERENCES   Flyway 建表、改列、删列/索引、建外键
#        CREATE VIEW                                  V14 等迁移创建的只读汇总视图
# 不授予 GRANT OPTION、FILE、PROCESS、SUPER、CREATE USER 等实例级权限，也不授予其他库的权限；
# 凭据泄露或注入时的爆炸半径限于 zhizao_xietong 一个库。
#
# root 只在建号/授权时使用；应用运行与备份/恢复脚本的口令互不复用。
set -eu

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${APP_DB_USERNAME:?APP_DB_USERNAME is required}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD is required}"

DB_HOST="${DB_HOST:-mysql}"
DB_NAME="${MYSQL_DATABASE:-zhizao_xietong}"

# 旧版 .env 可能仍是 DB_USERNAME=root；若继续执行会改写 root@'%' 的口令与授权，必须显式拒绝
if [ "$APP_DB_USERNAME" = "root" ]; then
  echo "DB_USERNAME 不能是 root，请改为受限账号（如 zhizao_app）后重试" >&2
  exit 1
fi
# 应用口令与 root 口令相同会让泄露面重新扩大到 root，直接拒绝
if [ "$APP_DB_PASSWORD" = "$MYSQL_ROOT_PASSWORD" ]; then
  echo "DB_PASSWORD 不能与 MYSQL_ROOT_PASSWORD 相同" >&2
  exit 1
fi

# 库名会拼进反引号标识符，只允许字母、数字与下划线
case "$DB_NAME" in
  *[!A-Za-z0-9_]*)
    echo "MYSQL_DATABASE 只允许字母、数字与下划线：${DB_NAME}" >&2
    exit 1
    ;;
esac

# 账号与口令会拼进 SQL 字符串字面量：转义反斜杠与单引号，避免特殊字符导致语法错误
sql_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e "s/'/''/g"
}
USER_SQL="$(sql_escape "$APP_DB_USERNAME")"
PASSWORD_SQL="$(sql_escape "$APP_DB_PASSWORD")"

# 口令只通过 MYSQL_PWD 传入，不出现在命令行与进程列表
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
{
  # 转义依赖反斜杠作为转义符；仅在本次初始化会话关闭 NO_BACKSLASH_ESCAPES，
  # 避免该模式下含反斜杠的口令被写成双反斜杠，导致后端认证失败
  printf "SET SESSION sql_mode = '';\n"
  printf "CREATE USER IF NOT EXISTS '%s'@'%%' IDENTIFIED BY '%s';\n" "$USER_SQL" "$PASSWORD_SQL"
  printf "ALTER USER '%s'@'%%' IDENTIFIED BY '%s';\n" "$USER_SQL" "$PASSWORD_SQL"
  # 先收回历史遗留的宽授权，再按最小权限重新授予，保证重复执行后权限一致
  printf "REVOKE ALL PRIVILEGES, GRANT OPTION FROM '%s'@'%%';\n" "$USER_SQL"
  printf "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, CREATE VIEW ON \`%s\`.* TO '%s'@'%%';\n" \
    "$DB_NAME" "$USER_SQL"
} | mysql --protocol=TCP -h "$DB_HOST" -uroot

echo "数据库账号已就绪：${APP_DB_USERNAME}（仅 ${DB_NAME} 库的 DML 与迁移所需 DDL）"
