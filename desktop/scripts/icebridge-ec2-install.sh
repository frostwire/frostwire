#!/usr/bin/env bash
# Install / upgrade a standalone IceBridge FORWARDER on a Linux host.
# Run ON the EC2 instance (or via: ssh host 'bash -s' < scripts/icebridge-ec2-install.sh)
# Expects icebridge.jar already uploaded to $INSTALL_DIR (default /opt/icebridge).
#
# Usage:
#   INSTALL_DIR=/opt/icebridge bash icebridge-ec2-install.sh
#   TOKEN_FILE=... ICEBRIDGE_RUDP_PORT=6889 ... bash icebridge-ec2-install.sh

set -euo pipefail

INSTALL_DIR="${INSTALL_DIR:-/opt/icebridge}"
SERVICE_USER="${SERVICE_USER:-icebridge}"
RUDP_PORT="${ICEBRIDGE_RUDP_PORT:-6889}"
RELAY_PORT="${ICEBRIDGE_RELAY_PORT:-6888}"
# One bind host for mesh + control. Use 0.0.0.0 and lock control HTTP with the security group
# (or SSH tunnel). Do not expose TCP 8081 to the world.
BIND_HOST="${ICEBRIDGE_HOST:-0.0.0.0}"
CONTROL_HTTP_PORT="${ICEBRIDGE_CONTROL_HTTP_PORT:-8081}"
ROLE="${ICEBRIDGE_ROLE:-FORWARDER}"
JAVA_BIN="${JAVA_BIN:-java}"

if [[ ! -f "${INSTALL_DIR}/icebridge.jar" ]]; then
  echo "ERROR: ${INSTALL_DIR}/icebridge.jar not found. scp the multi-arch fat JAR first." >&2
  exit 1
fi

if ! command -v "${JAVA_BIN}" >/dev/null 2>&1; then
  echo "ERROR: java not found. Install JDK 17+ (Amazon Corretto / Temurin)." >&2
  exit 1
fi

echo "==> Layout under ${INSTALL_DIR}"
mkdir -p "${INSTALL_DIR}"
cd "${INSTALL_DIR}"

if [[ ! -f icebridge-tokens.txt ]]; then
  echo "==> Generating control token (printed once)"
  ${JAVA_BIN} -jar icebridge.jar --generate-token --auth-tokens-file icebridge-tokens.txt | tee icebridge-token.once
  chmod 600 icebridge-tokens.txt icebridge-token.once
fi

cat > "${INSTALL_DIR}/icebridge.env" <<EOF
ICEBRIDGE_HOST=${BIND_HOST}
ICEBRIDGE_RUDP_PORT=${RUDP_PORT}
ICEBRIDGE_RELAY_PORT=${RELAY_PORT}
ICEBRIDGE_CONTROL_HTTP_PORT=${CONTROL_HTTP_PORT}
ICEBRIDGE_ROLE=${ROLE}
ICEBRIDGE_IDENTITY_FILE=${INSTALL_DIR}/identity.dat
ICEBRIDGE_AUTH_TOKENS_FILE=${INSTALL_DIR}/icebridge-tokens.txt
ICEBRIDGE_BOOTSTRAP=true
ICEBRIDGE_DHT=true
ICEBRIDGE_MAX_PEERS=10000
ICEBRIDGE_PEER_TTL_SEC=300
ICEBRIDGE_MAX_QPS_PER_KEY=30.0
EOF
chmod 600 "${INSTALL_DIR}/icebridge.env"

# systemd unit (requires root)
UNIT=/etc/systemd/system/icebridge.service
if [[ "$(id -u)" -eq 0 ]]; then
  id -u "${SERVICE_USER}" >/dev/null 2>&1 || useradd --system --home "${INSTALL_DIR}" --shell /usr/sbin/nologin "${SERVICE_USER}"
  chown -R "${SERVICE_USER}:${SERVICE_USER}" "${INSTALL_DIR}"

  cat > "${UNIT}" <<EOF
[Unit]
Description=FrostWire IceBridge FORWARDER (DHT + rUDP mesh)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${SERVICE_USER}
WorkingDirectory=${INSTALL_DIR}
EnvironmentFile=${INSTALL_DIR}/icebridge.env
ExecStart=${JAVA_BIN} -jar ${INSTALL_DIR}/icebridge.jar
Restart=on-failure
RestartSec=5
LimitNOFILE=65535
# Fail closed: do not open control to the world via SG; also bind check is app-level.

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable icebridge.service
  systemctl restart icebridge.service
  sleep 2
  systemctl --no-pager -l status icebridge.service || true
  echo "==> Health (localhost):"
  curl -sS "http://127.0.0.1:${CONTROL_HTTP_PORT}/health" || true
  echo
else
  echo "==> Not root: wrote ${INSTALL_DIR}/icebridge.env — run with sudo for systemd, or:"
  echo "    set -a; source ${INSTALL_DIR}/icebridge.env; set +a"
  echo "    nohup ${JAVA_BIN} -jar ${INSTALL_DIR}/icebridge.jar > ${INSTALL_DIR}/icebridge.log 2>&1 &"
fi

echo "==> Security group checklist (AWS console / CLI):"
echo "    TCP  ${RELAY_PORT}  from 0.0.0.0/0   # identity handshake"
echo "    UDP  ${RUDP_PORT}   from 0.0.0.0/0   # rUDP mesh"
echo "    TCP  ${CONTROL_HTTP_PORT} from your IP only (or none — use SSH tunnel)"
echo "    UDP  0-65535 outbound (or at least 6881/25401) for public DHT bootstrap"
echo "==> Done."
