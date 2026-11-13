#!/usr/bin/env bash
#
# 生成 MQTT TLS 用的自签 CA 与服务端证书（产物不提交仓库）。
#
# 用法：
#   ./scripts/gen-mqtt-certs.sh
#   MQTT_CERT_SAN="DNS:mqtt,IP:192.168.1.10" ./scripts/gen-mqtt-certs.sh
#   MQTT_CERT_FORCE=1 ./scripts/gen-mqtt-certs.sh    # 覆盖已有证书，设备需重新导入 CA
#
# 产物：
#   deploy/mqtt/ca/     ca.crt、ca.key（CA 私钥，不挂进 broker 容器）
#   deploy/mqtt/certs/  server.crt、server.key（只把该目录挂给 broker）
# 设备侧只需导入 ca.crt，无需客户端证书。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${MQTT_CERT_DIR:-$ROOT_DIR/deploy/mqtt/certs}"
CA_DIR="${MQTT_CA_DIR:-$ROOT_DIR/deploy/mqtt/ca}"
DAYS="${MQTT_CERT_DAYS:-3650}"
# 默认绑定容器名与本机回环；设备通过 IP/域名访问时用 MQTT_CERT_SAN 覆盖
SAN="${MQTT_CERT_SAN:-DNS:mqtt,DNS:localhost,IP:127.0.0.1}"

command -v openssl >/dev/null 2>&1 || { echo "未找到 openssl，请先安装" >&2; exit 1; }

umask 077
mkdir -p "$OUT_DIR" "$CA_DIR"
chmod 700 "$OUT_DIR" "$CA_DIR"

if [[ -f "$CA_DIR/ca.crt" && -f "$OUT_DIR/server.crt" && -f "$OUT_DIR/server.key" && "${MQTT_CERT_FORCE:-0}" != "1" ]]; then
  echo "证书已存在：${OUT_DIR}、${CA_DIR}"
  echo "如需重新生成请设置 MQTT_CERT_FORCE=1（设备需要重新导入新的 ca.crt）"
  exit 0
fi

echo "生成 CA -> ${CA_DIR}，服务端证书 -> ${OUT_DIR}"
openssl req -x509 -newkey rsa:2048 -sha256 -days "$DAYS" -nodes \
  -keyout "$CA_DIR/ca.key" -out "$CA_DIR/ca.crt" \
  -subj "/CN=zhizao-mqtt-ca"

openssl req -newkey rsa:2048 -sha256 -nodes \
  -keyout "$OUT_DIR/server.key" -out "$OUT_DIR/server.csr" \
  -subj "/CN=mqtt"

cat > "$OUT_DIR/server.ext" <<EOF
subjectAltName = ${SAN}
extendedKeyUsage = serverAuth
EOF

openssl x509 -req -in "$OUT_DIR/server.csr" \
  -CA "$CA_DIR/ca.crt" -CAkey "$CA_DIR/ca.key" -CAcreateserial \
  -out "$OUT_DIR/server.crt" -days "$DAYS" -sha256 -extfile "$OUT_DIR/server.ext"

rm -f "$OUT_DIR/server.csr" "$OUT_DIR/server.ext" "$CA_DIR/ca.srl"
chmod 600 "$CA_DIR/ca.key" "$OUT_DIR/server.key"
chmod 644 "$CA_DIR/ca.crt" "$OUT_DIR/server.crt"

echo "完成。设备需导入 ${CA_DIR}/ca.crt，并连接 8883 端口。"
openssl x509 -in "$OUT_DIR/server.crt" -noout -subject -ext subjectAltName
