#!/usr/bin/env bash
#
# 一条命令验证 docker-compose 五服务栈与关键业务链路。
#
# 用法：
#   ./scripts/verify.sh          # 起栈、跑链路、成功后清理
#   ./scripts/verify.sh --keep   # 成功后保留现场，便于手工查看
#
# 默认使用 13306/16379/11883/18883/18080/18081 作为宿主机端口，避免与本机 MySQL、
# Redis 等服务冲突；需要改端口时直接覆盖对应环境变量。
#
# 任一步骤失败：打印 docker compose 日志尾部，保留容器与数据卷，以非 0 退出。

set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PROJECT_NAME="${VERIFY_PROJECT:-zhizao-verify}"
export MYSQL_PORT="${MYSQL_PORT:-13306}"
export REDIS_PORT="${REDIS_PORT:-16379}"
export MQTT_PORT="${MQTT_PORT:-11883}"
# 设备入口是 TLS 8883，宿主机同样避开默认端口；缺证书时 mqtt 起不来
export MQTT_TLS_PORT="${MQTT_TLS_PORT:-18883}"
if [[ ! -f "$ROOT_DIR/deploy/mqtt/certs/server.crt" ]]; then
  "$ROOT_DIR/scripts/gen-mqtt-certs.sh" >/dev/null || { echo "FAIL  生成 MQTT TLS 证书失败" >&2; exit 1; }
fi
export BACKEND_PORT="${BACKEND_PORT:-18080}"
export FRONTEND_PORT="${FRONTEND_PORT:-18081}"
# CORS 白名单必须是浏览器实际访问前端的地址，验证栈默认用 127.0.0.1:前端端口
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://127.0.0.1:${FRONTEND_PORT}}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-verify-root}"
# 应用使用受限账号，口令与 root 分开；mysql-init 会按这两个值建号并授权
export DB_USERNAME="${DB_USERNAME:-zhizao_app}"
export DB_PASSWORD="${DB_PASSWORD:-verify-app-password}"
# Redis 开启 requirepass、Mosquitto 关闭匿名后均为 compose 必填项，这里用一次性验证值
export REDIS_PASSWORD="${REDIS_PASSWORD:-verify-redis}"
export MQTT_USERNAME="${MQTT_USERNAME:-verify-mqtt}"
export MQTT_PASSWORD="${MQTT_PASSWORD:-verify-mqtt-secret}"
export JWT_SECRET="${JWT_SECRET:-verify-only-jwt-secret-0123456789abcdef}"
# 每次验证使用一次性随机管理员口令，脚本内不保存固定明文口令
export APP_BOOTSTRAP_ADMIN_PASSWORD="${APP_BOOTSTRAP_ADMIN_PASSWORD:-$(openssl rand -base64 24)}"

COMPOSE=(docker compose -p "$PROJECT_NAME" -f docker-compose.yml)
BASE_URL="http://127.0.0.1:${BACKEND_PORT}"
FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}"
KEEP_SCENE=0
[[ "${1:-}" == "--keep" ]] && KEEP_SCENE=1

PASSED=0
FAILED=0

cleanup() {
  if [[ $FAILED -eq 0 && $KEEP_SCENE -eq 0 ]]; then
    echo "清理：docker compose -p ${PROJECT_NAME} down -v"
    "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1
  fi
}

fail() {
  FAILED=$((FAILED + 1))
  echo "FAIL  $1"
  [[ -n "${2:-}" ]] && echo "      $2"
  echo "--- 现场保留：docker compose -p ${PROJECT_NAME} logs --tail=200 ---"
  "${COMPOSE[@]}" logs --tail=80 2>&1
  echo "--- 清理命令：docker compose -p ${PROJECT_NAME} down -v ---"
  exit 1
}

pass() {
  PASSED=$((PASSED + 1))
  echo "PASS  $1"
}

# 调用后端接口，输出响应体；HTTP 非 2xx 也照常输出，交给调用方判断业务码。
api() {
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" "${BASE_URL}${path}" \
      -H 'Content-Type: application/json' \
      -H "Authorization: Bearer ${TOKEN:-}" \
      -d "$body"
  else
    curl -sS -X "$method" "${BASE_URL}${path}" \
      -H "Authorization: Bearer ${TOKEN:-}"
  fi
}

trap cleanup EXIT

echo "== 1. 起栈（含镜像构建） =="
if ! "${COMPOSE[@]}" up -d --build; then
  fail "docker compose up -d --build"
fi

echo "== 2. 等待后端健康 =="
HEALTH_OK=0
for _ in $(seq 1 90); do
  status="$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/actuator/health" || true)"
  body="$(curl -s "${BASE_URL}/actuator/health" || true)"
  if [[ "$status" == "200" && "$(printf '%s' "$body" | jq -r '.status // empty' 2>/dev/null)" == "UP" ]]; then
    HEALTH_OK=1
    break
  fi
  sleep 2
done
[[ $HEALTH_OK -eq 1 ]] || fail "后端 /actuator/health 未在 180 秒内变为 UP"
pass "后端健康检查 UP"

if curl -s -o /dev/null --max-time 10 "${FRONTEND_URL}"; then
  pass "前端静态站点可访问（${FRONTEND_URL}）"
else
  fail "前端不可访问（${FRONTEND_URL}）"
fi

echo "== 3. 登录（带浏览器 Origin，验证 CORS 白名单生效） =="
LOGIN_RAW="$(curl -sS -X POST "${BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -H "Origin: ${FRONTEND_URL}" \
  -w '\n%{http_code}' \
  -d '{"username":"admin","password":"'"${APP_BOOTSTRAP_ADMIN_PASSWORD}"'"}')"
LOGIN_STATUS="${LOGIN_RAW##*$'\n'}"
LOGIN="${LOGIN_RAW%$'\n'*}"
[[ "$LOGIN_STATUS" == "200" ]] \
  || fail "带 Origin 的登录请求返回 ${LOGIN_STATUS}，CORS 白名单未生效" "$LOGIN"
TOKEN="$(printf '%s' "$LOGIN" | jq -r '.data.token // empty')"
[[ "$(printf '%s' "$LOGIN" | jq -r '.code')" == "0" && -n "$TOKEN" ]] \
  || fail "管理员登录失败" "$LOGIN"
pass "管理员登录并取得 JWT（Origin=${FRONTEND_URL}）"

echo "== 4. 建计划 =="
PLAN_BODY='{"productCode":"P-VERIFY","productName":"验证产品","planQty":50,
"planStart":"2026-11-06","planEnd":"2026-11-20","priority":"HIGH","batch":"VERIFY",
"requiredDeviceId":1,"estHours":8}'
PLAN="$(api POST /api/plans "$PLAN_BODY")"
PLAN_ID="$(printf '%s' "$PLAN" | jq -r '.data.id // empty')"
[[ -n "$PLAN_ID" ]] || fail "创建计划失败" "$PLAN"
pass "创建计划 id=${PLAN_ID}"

echo "== 5. 下达工单 =="
DISPATCH="$(api POST "/api/plans/${PLAN_ID}/dispatch" '{"processId":1,"deviceId":1,"workshop":"一车间"}')"
WO_ID="$(printf '%s' "$DISPATCH" | jq -r '.data.id // empty')"
WO_STATUS="$(printf '%s' "$DISPATCH" | jq -r '.data.status // empty')"
[[ -n "$WO_ID" && "$WO_STATUS" == "PENDING" ]] || fail "下达工单失败" "$DISPATCH"
pass "下达工单 id=${WO_ID} 状态=${WO_STATUS}"

echo "== 6. 开工 =="
START="$(api POST "/api/work-orders/${WO_ID}/start")"
WO_STATUS="$(printf '%s' "$START" | jq -r '.data.status // empty')"
[[ "$WO_STATUS" == "IN_PRODUCTION" ]] || fail "开工失败" "$START"
pass "开工成功 状态=${WO_STATUS}"

echo "== 7. 部分报工（10/50，状态保持不变） =="
REPORT="$(api POST "/api/work-orders/${WO_ID}/report" \
  '{"qty":10,"qualifiedQty":9,"defectQty":1,"deviceId":1,"operator":"verify","idempotencyKey":"verify-'"${WO_ID}"'-1"}')"
WO_STATUS="$(printf '%s' "$REPORT" | jq -r '.data.status // empty')"
[[ "$WO_STATUS" == "IN_PRODUCTION" ]] || fail "部分报工后状态应为 IN_PRODUCTION" "$REPORT"
REPORTED="$(printf '%s' "$REPORT" | jq -r '.data.qtyDone // empty')"
[[ "$REPORTED" == "10" ]] || fail "部分报工累计数量应为 10" "$REPORT"
pass "部分报工成功 状态=${WO_STATUS} 累计=${REPORTED}/50"

echo "== 8. 报满转待质检 =="
REPORT2="$(api POST "/api/work-orders/${WO_ID}/report" \
  '{"qty":40,"qualifiedQty":40,"defectQty":0,"deviceId":1,"operator":"verify","idempotencyKey":"verify-'"${WO_ID}"'-2"}')"
WO_STATUS="$(printf '%s' "$REPORT2" | jq -r '.data.status // empty')"
[[ "$WO_STATUS" == "PENDING_QC" ]] || fail "报满后状态应为 PENDING_QC" "$REPORT2"
pass "报满转待质检 状态=${WO_STATUS}"

echo "== 9. 质检 =="
QC="$(api POST "/api/work-orders/${WO_ID}/qc" '{"result":"PASS","inspector":"verify"}')"
WO_STATUS="$(printf '%s' "$QC" | jq -r '.data.status // empty')"
[[ "$WO_STATUS" == "FINISHED" ]] || fail "质检失败" "$QC"
pass "质检通过 状态=${WO_STATUS}"

echo "== 10. 查状态历史 =="
HISTORY="$(api GET "/api/work-orders/${WO_ID}/history")"
HISTORY_COUNT="$(printf '%s' "$HISTORY" | jq -r '.data | length' 2>/dev/null)"
[[ "${HISTORY_COUNT:-0}" -ge 4 ]] || fail "状态历史记录不足" "$HISTORY"
pass "状态历史 ${HISTORY_COUNT} 条"

echo "== 11. 查遥测历史 =="
TELEMETRY="$(api GET "/api/devices/1/telemetry/history?limit=10")"
TELEMETRY_CODE="$(printf '%s' "$TELEMETRY" | jq -r '.code // empty')"
[[ "$TELEMETRY_CODE" == "0" ]] || fail "遥测历史查询失败" "$TELEMETRY"
TELEMETRY_COUNT="$(printf '%s' "$TELEMETRY" | jq -r '.data | length')"
pass "遥测历史查询成功 样本数=${TELEMETRY_COUNT}"

echo "== 12. 查指标 =="
METRICS="$(api GET "/api/metrics/overview")"
METRICS_CODE="$(printf '%s' "$METRICS" | jq -r '.code // empty')"
[[ "$METRICS_CODE" == "0" ]] || fail "指标查询失败" "$METRICS"
pass "指标查询成功（空分母项返回 null + reason）"

echo
echo "全部通过：${PASSED} 项 PASS，0 项 FAIL"
if [[ $KEEP_SCENE -eq 1 ]]; then
  echo "现场已保留：前端 ${FRONTEND_URL} / 后端 ${BASE_URL}"
  echo "清理请执行：docker compose -p ${PROJECT_NAME} down -v"
fi
