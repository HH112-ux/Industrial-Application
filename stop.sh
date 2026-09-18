#!/usr/bin/env bash
# 智造协同 · 停止脚本（按端口停止后端/前端/移动端）
set -uo pipefail

echo "════════ 智造协同 · 停止服务 ════════"
for port in 8080 5173 5174; do
  pids=$(lsof -ti tcp:"$port" 2>/dev/null)
  if [ -n "$pids" ]; then
    kill $pids 2>/dev/null
    echo "  端口 $port: 已停止 (PID $pids)"
  else
    echo "  端口 $port: 无运行服务"
  fi
done
echo "════════ 完成 ════════"
