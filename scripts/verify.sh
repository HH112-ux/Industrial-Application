#!/usr/bin/env bash
#
# 一条命令验证 docker-compose 五服务栈与关键业务链路。
#
# 用法：
#   ./scripts/verify.sh          # 起栈、跑链路、成功后清理
#   ./scripts/verify.sh --keep   # 成功后保留现场，便于手工查看
#
# 默认使用 13306/16379/11883/18080/18081 作为宿主机端口，避免与本机 MySQL、
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
export BACKEND_PORT="${BACKEND_PORT:-18080}"
export FRONTEND_PORT="${FRONTEND_PORT:-18081}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-verify-root}"
export JWT_SECRET="${JWT_SECRET:-verify-only-jwt-secret-0123456789abcdef}"

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

echo "== 3. 登录 =="
LOGIN="$(curl -sS -X POST "${BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}')"
TOKEN="$(printf '%s' "$LOGIN" | jq -r '.data.token // empty')"
[[ "$(printf '%s' "$LOGIN" | jq -r '.code')" == "0" && -n "$TOKEN" ]] \
  || fail "管理员登录失败" "$LOGIN"
pass "管理员登录并取得 JWT"

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

echo "== 7. 报工 =="
REPORT="$(api POST "/api/work-orders/${WO_ID}/report" \
  '{"qty":10,"qualifiedQty":9,"defectQty":1,"deviceId":1,"operator":"verify","idempotencyKey":"verify-'"${WO_ID}"'"}')"
WO_STATUS="$(printf '%s' "$REPORT" | jq -r '.data.status // empty')"
[[ "$WO_STATUS" == "PENDING_QC" ]] || fail "报工失败" "$REPORT"
pass "报工成功 状态=${WO_STATUS}"

echo "== 8. 质检 =="
QC="$(api POST "/api/work-orders/${WO_ID}/qc" '{"result":"PASS","inspector":"verify"}')"
WO_STATUS="$(printf '%s' "$QC" | jq -r '.data.status // empty')"
[[ "$WO_STATUS" == "FINISHED" ]] || fail "质检失败" "$QC"
pass "质检通过 状态=${WO_STATUS}"

echo "== 9. 查状态历史 =="
HISTORY="$(api GET "/api/work-orders/${WO_ID}/history")"
HISTORY_COUNT="$(printf '%s' "$HISTORY" | jq -r '.data | length' 2>/dev/null)"
[[ "${HISTORY_COUNT:-0}" -ge 3 ]] || fail "状态历史记录不足" "$HISTORY"
pass "状态历史 ${HISTORY_COUNT} 条"

echo "== 10. 查遥测历史 =="
TELEMETRY="$(api GET "/api/devices/1/telemetry/history?limit=10")"
TELEMETRY_CODE="$(printf '%s' "$TELEMETRY" | jq -r '.code // empty')"
[[ "$TELEMETRY_CODE" == "0" ]] || fail "遥测历史查询失败" "$TELEMETRY"
TELEMETRY_COUNT="$(printf '%s' "$TELEMETRY" | jq -r '.data | length')"
pass "遥测历史查询成功 样本数=${TELEMETRY_COUNT}"

echo "== 11. 查指标 =="
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
