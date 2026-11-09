#!/usr/bin/env bash
# 智造协同 · 一键启动脚本（后端 + 前端 + 移动端）
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

# ===== 从 .env 文件加载配置（不硬编码任何密码） =====
if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "错误: DB_PASSWORD 未设置。"
  echo "请复制 .env.example 为 .env 并填写真实密码: cp .env.example .env"
  exit 1
fi

DB_USERNAME="${DB_USERNAME:-root}"
JDK17="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
REDIS_SERVER="/opt/homebrew/opt/redis/bin/redis-server"

check_port() { nc -z localhost "$1" >/dev/null 2>&1; }

echo "════════ 智造协同 · 一键启动 ════════"

# 1. MySQL
if check_port 3306; then
  echo "[1/5] MySQL      已运行 ✓"
else
  echo "[1/5] MySQL      未运行，请先执行: brew services start mysql"
  exit 1
fi

# 2. Redis
if check_port 6379; then
  echo "[2/5] Redis      已运行 ✓"
else
  echo "[2/5] Redis      未运行，正在启动..."
  "$REDIS_SERVER" --daemonize yes 2>/dev/null || brew services start redis
  sleep 1
  check_port 6379 && echo "       Redis 已启动 ✓" || echo "       Redis 启动失败，请手动执行: brew services start redis"
fi

# 3. 后端
if check_port 8080; then
  echo "[3/5] 后端       已运行 ✓"
else
  echo "[3/5] 后端       启动中（日志 logs/backend.log，首次约 15-30 秒）..."
  (cd "$ROOT_DIR/zhizao-xietong" && \
     JAVA_HOME="$JDK17" DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
     nohup mvn spring-boot:run > "$LOG_DIR/backend.log" 2>&1 &)
fi

# 4. 前端
if check_port 5173; then
  echo "[4/5] 前端       已运行 ✓"
else
  echo "[4/5] 前端       启动中（日志 logs/frontend.log）..."
  (cd "$ROOT_DIR/zhizao-xietong-web" && nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 &)
fi

# 5. 移动端
if check_port 5174; then
  echo "[5/5] 移动端     已运行 ✓"
else
  echo "[5/5] 移动端     启动中（日志 logs/mobile.log）..."
  (cd "$ROOT_DIR/mobile-app" && nohup npm run dev:h5 > "$LOG_DIR/mobile.log" 2>&1 &)
fi

echo ""
echo "════════ 启动完成 ════════"
echo "  管理端:        http://localhost:5173"
echo "  移动端:        http://localhost:5174"
echo "  Swagger 文档:  http://localhost:8080/swagger-ui/index.html"
echo ""
echo "  登录账号: admin（初始口令见后端启动日志，或由 APP_BOOTSTRAP_ADMIN_PASSWORD 注入）"
echo "  后端首次启动需约 15-30 秒，请稍候再访问"
echo "  停止服务: ./stop.sh"
