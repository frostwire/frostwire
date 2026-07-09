#!/usr/bin/env bash
# Install / upgrade a standalone IceBridge FORWARDER on a Linux host.
# Run ON the EC2 instance (or via: ssh host 'bash -s' < scripts/icebridge-ec2-install.sh)
# Expects icebridge.jar already uploaded to $INSTALL_DIR (default /opt/icebridge).
#
# Usage:
#   INSTALL_DIR=/opt/icebridge bash icebridge-ec2-install.sh
#   TOKEN_FILE=... ICEBRIDGE_RUDP_PORT=6889 ... bash icebridge-ec2-install.sh
#
# Control HTTP binds 127.0.0.1 only (see ControlServer). Do not open it in the
# security group; use SSH -L for ops. Mesh plane: TCP identity + UDP rUDP.

set -euo pipefail

INSTALL_DIR="${INSTALL_DIR:-/opt/icebridge}"
SERVICE_USER="${SERVICE_USER:-icebridge}"
RUDP_PORT="${ICEBRIDGE_RUDP_PORT:-6889}"
RELAY_PORT="${ICEBRIDGE_RELAY_PORT:-6888}"
# Host for rUDP / identity listeners. Control HTTP ignores this and stays on 127.0.0.1.
BIND_HOST="${ICEBRIDGE_HOST:-0.0.0.0}"
CONTROL_HTTP_PORT="${ICEBRIDGE_CONTROL_HTTP_PORT:-8081}"
ROLE="${ICEBRIDGE_ROLE:-FORWARDER}"
# Resolve to absolute path for systemd (relative "java" is unreliable under unit PATH).
JAVA_BIN_RAW="${JAVA_BIN:-java}"
if command -v "${JAVA_BIN_RAW}" >/dev/null 2>&1; then
  JAVA_BIN="$(command -v "${JAVA_BIN_RAW}")"
else
  JAVA_BIN="${JAVA_BIN_RAW}"
fi

if [[ ! -f "${INSTALL_DIR}/icebridge.jar" ]]; then
  echo "ERROR: ${INSTALL_DIR}/icebridge.jar not found. scp the multi-arch fat JAR first." >&2
  exit 1
fi

if ! command -v "${JAVA_BIN}" >/dev/null 2>&1 && [[ ! -x "${JAVA_BIN}" ]]; then
  echo "ERROR: java not found (${JAVA_BIN_RAW}). Install JDK 17+ (Amazon Corretto / Temurin)." >&2
  exit 1
fi

# Prefer realpath-style absolute for ExecStart
if [[ "${JAVA_BIN}" != /* ]]; then
  if command -v "${JAVA_BIN}" >/dev/null 2>&1; then
    JAVA_BIN="$(command -v "${JAVA_BIN}")"
  fi
fi
if [[ "${JAVA_BIN}" != /* ]]; then
  echo "ERROR: JAVA_BIN must resolve to an absolute path for systemd (got: ${JAVA_BIN})" >&2
  exit 1
fi

echo "==> Layout under ${INSTALL_DIR}"
mkdir -p "${INSTALL_DIR}"
cd "${INSTALL_DIR}"

if [[ ! -f icebridge-tokens.txt ]]; then
  echo "==> Generating control token (printed once)"
  "${JAVA_BIN}" -jar icebridge.jar --generate-token --auth-tokens-file icebridge-tokens.txt | tee icebridge-token.once
  chmod 600 icebridge-tokens.txt icebridge-token.once
fi

ENV_FILE="${INSTALL_DIR}/icebridge.env"
write_icebridge_env() {
  cat > "${ENV_FILE}" <<EOF
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
  chmod 600 "${ENV_FILE}"
}

if [[ -f "${ENV_FILE}" ]]; then
  # Preserve operator edits on upgrade unless FORCE_ENV=1.
  if [[ "${FORCE_ENV:-0}" == "1" ]]; then
    echo "==> FORCE_ENV=1 — rewriting ${ENV_FILE}"
    write_icebridge_env
  else
    echo "==> Keeping existing ${ENV_FILE} (FORCE_ENV=1 to replace from current ICEBRIDGE_* / defaults)"
  fi
else
  echo "==> Writing ${ENV_FILE}"
  write_icebridge_env
fi

# Source final env so health check uses the active control port
# shellcheck disable=SC1090
set -a
# shellcheck source=/dev/null
source "${ENV_FILE}"
set +a
CONTROL_HTTP_PORT="${ICEBRIDGE_CONTROL_HTTP_PORT:-${CONTROL_HTTP_PORT}}"
RELAY_PORT="${ICEBRIDGE_RELAY_PORT:-${RELAY_PORT}}"
RUDP_PORT="${ICEBRIDGE_RUDP_PORT:-${RUDP_PORT}}"

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

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable icebridge.service
  systemctl restart icebridge.service
  sleep 2
  systemctl --no-pager -l status icebridge.service || true
  echo "==> Health (localhost only — control binds 127.0.0.1):"
  curl -sS "http://127.0.0.1:${CONTROL_HTTP_PORT}/health" || true
  echo
else
  echo "==> Not root: wrote ${ENV_FILE} — run with sudo for systemd, or:"
  echo "    set -a; source ${ENV_FILE}; set +a"
  echo "    nohup ${JAVA_BIN} -jar ${INSTALL_DIR}/icebridge.jar > ${INSTALL_DIR}/icebridge.log 2>&1 &"
fi

echo "==> Security group checklist (AWS console / CLI):"
echo "    TCP  ${RELAY_PORT}  from 0.0.0.0/0   # identity handshake"
echo "    UDP  ${RUDP_PORT}   from 0.0.0.0/0   # rUDP mesh"
echo "    DO NOT open control HTTP ${CONTROL_HTTP_PORT} — binds 127.0.0.1 only; use:"
echo "      ssh -L 18081:127.0.0.1:${CONTROL_HTTP_PORT} <host>"
echo "    UDP  0-65535 outbound (or at least 6881/25401) for public DHT bootstrap"
echo "==> Done."
