#!/usr/bin/env bash
# Install / upgrade a standalone IceBridge FORWARDER on a Linux host.
# One step: rebuilds icebridge.jar from the enclosing checkout (unless
# --no-build), copies it to $INSTALL_DIR (default /opt/icebridge), then
# installs/restarts the systemd unit.
# Run ON the EC2 instance from the frostwire checkout, e.g.:
#   sudo INSTALL_DIR=/opt/icebridge bash desktop/scripts/icebridge-systemd-install.sh
# (or via: ssh host 'bash -s' < scripts/icebridge-systemd-install.sh)
#
# Usage:
#   sudo INSTALL_DIR=/opt/icebridge bash icebridge-systemd-install.sh [--no-build] [--jar=/path/to/icebridge.jar]
#   TOKEN_FILE=... ICEBRIDGE_RUDP_PORT=6889 ... bash icebridge-systemd-install.sh
#
# Control HTTP binds 127.0.0.1 only (see ControlServer). Do not open it in the
# security group; use SSH -L for ops. Mesh plane: TCP identity + UDP rUDP.

set -euo pipefail

NO_BUILD=0
JAR_SRC=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1 ;;
    --jar=*)
      JAR_SRC="${1#--jar=}"
      # Resolve now: the script cd's into INSTALL_DIR later.
      if [[ "${JAR_SRC}" != /* ]]; then
        JAR_SRC="$(pwd)/${JAR_SRC}"
      fi
      ;;
    -h|--help)
      echo "Usage: INSTALL_DIR=/opt/icebridge bash icebridge-systemd-install.sh [--no-build] [--jar=/path/to/icebridge.jar]"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DESKTOP_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

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

# Self-elevate: the install (INSTALL_DIR + systemd unit) needs root. Re-exec
# under sudo, passing our config through explicitly (sudoers may strip the
# environment), preserving args and working directory. No sudo binary, or
# already root: continue as-is (non-root falls back to env-file + manual
# nohup instructions at the end).
if [[ "$(id -u)" -ne 0 ]] && command -v sudo >/dev/null 2>&1 && [[ -f "$0" ]]; then
  echo "==> Need root for ${INSTALL_DIR} + systemd; re-running under sudo (may ask for your password)."
  # Gradle needs JAVA_HOME (sudo resets PATH and strips the environment);
  # derive it from the resolved java binary when the caller didn't export one.
  ELEVATED_JAVA_HOME="${JAVA_HOME:-}"
  if [[ -z "${ELEVATED_JAVA_HOME}" && "${JAVA_BIN:-}" == */* ]]; then
    ELEVATED_JAVA_HOME="$(cd "$(dirname "${JAVA_BIN}")/.." 2>/dev/null && pwd)"
  fi
  exec sudo \
    "INSTALL_DIR=${INSTALL_DIR}" \
    "SERVICE_USER=${SERVICE_USER}" \
    "JAVA_BIN=${JAVA_BIN:-java}" \
    "JAVA_HOME=${ELEVATED_JAVA_HOME}" \
    "FORCE_ENV=${FORCE_ENV:-0}" \
    "ICEBRIDGE_HOST=${ICEBRIDGE_HOST:-}" \
    "ICEBRIDGE_RUDP_PORT=${ICEBRIDGE_RUDP_PORT:-}" \
    "ICEBRIDGE_RELAY_PORT=${ICEBRIDGE_RELAY_PORT:-}" \
    "ICEBRIDGE_CONTROL_HTTP_PORT=${ICEBRIDGE_CONTROL_HTTP_PORT:-}" \
    "ICEBRIDGE_ROLE=${ICEBRIDGE_ROLE:-}" \
    "ICEBRIDGE_IDENTITY_FILE=${ICEBRIDGE_IDENTITY_FILE:-}" \
    "ICEBRIDGE_AUTH_TOKENS_FILE=${ICEBRIDGE_AUTH_TOKENS_FILE:-}" \
    "ICEBRIDGE_MAX_PEERS=${ICEBRIDGE_MAX_PEERS:-}" \
    "ICEBRIDGE_PEER_TTL_SEC=${ICEBRIDGE_PEER_TTL_SEC:-}" \
    "ICEBRIDGE_MAX_QPS_PER_KEY=${ICEBRIDGE_MAX_QPS_PER_KEY:-}" \
    "ICEBRIDGE_BOOTSTRAP=${ICEBRIDGE_BOOTSTRAP:-}" \
    "ICEBRIDGE_DHT=${ICEBRIDGE_DHT:-}" \
    bash "$0" "$@"
fi

# One-step: build the jar from the enclosing checkout (skipped with --no-build
# or an explicit --jar=...). Falls back to a jar already in INSTALL_DIR.
if [[ -z "${JAR_SRC}" && "${NO_BUILD}" -eq 0 && -x "${DESKTOP_ROOT}/gradlew" ]]; then
  echo "==> Building icebridge.jar from ${DESKTOP_ROOT}"
  (cd "${DESKTOP_ROOT}" && ./gradlew icebridgeJar)
  JAR_SRC="${DESKTOP_ROOT}/build/libs/icebridge.jar"
  if [[ -n "${SUDO_USER:-}" ]]; then
    chown -R "${SUDO_USER}" "${DESKTOP_ROOT}/build" 2>/dev/null || true
  fi
fi
if [[ -z "${JAR_SRC}" ]]; then
  JAR_SRC="${INSTALL_DIR}/icebridge.jar"
fi
if [[ ! -f "${JAR_SRC}" ]]; then
  echo "ERROR: no icebridge.jar (tried ${JAR_SRC}). Re-run without --no-build" >&2
  echo "  from a frostwire checkout, pass --jar=/path/to/icebridge.jar," >&2
  echo "  or scp one to ${INSTALL_DIR}/ first." >&2
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

if [[ "${JAR_SRC}" != "${INSTALL_DIR}/icebridge.jar" ]]; then
  echo "==> Installing jar -> ${INSTALL_DIR}/icebridge.jar"
  cp -f "${JAR_SRC}" "${INSTALL_DIR}/icebridge.jar"
fi

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
ExecStart=${JAVA_BIN} -jar ${INSTALL_DIR}/icebridge.jar --host ${ICEBRIDGE_HOST} --rudp-port ${ICEBRIDGE_RUDP_PORT} --relay-port ${ICEBRIDGE_RELAY_PORT} --control-http-port ${ICEBRIDGE_CONTROL_HTTP_PORT} --role ${ICEBRIDGE_ROLE} --identity-file ${ICEBRIDGE_IDENTITY_FILE} --auth-tokens-file ${ICEBRIDGE_AUTH_TOKENS_FILE} --max-peers ${ICEBRIDGE_MAX_PEERS} --peer-ttl-sec ${ICEBRIDGE_PEER_TTL_SEC} --max-qps-per-key ${ICEBRIDGE_MAX_QPS_PER_KEY} --dht --bootstrap
Restart=on-failure
RestartSec=5
LimitNOFILE=65535
# Bound journal volume under flood: per-packet drops already log at DEBUG,
# this caps any residual spam (~66 lines/sec) so one flooder can't evict
# the log history inside the 200M journal budget. Tune after EC2 baseline.
LogRateLimitIntervalSec=30s
LogRateLimitBurst=2000

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
  echo "    nohup ${JAVA_BIN} -jar ${INSTALL_DIR}/icebridge.jar --host ${ICEBRIDGE_HOST} --rudp-port ${ICEBRIDGE_RUDP_PORT} --relay-port ${ICEBRIDGE_RELAY_PORT} --control-http-port ${ICEBRIDGE_CONTROL_HTTP_PORT} --role ${ICEBRIDGE_ROLE} --identity-file ${ICEBRIDGE_IDENTITY_FILE} --auth-tokens-file ${ICEBRIDGE_AUTH_TOKENS_FILE} --max-peers ${ICEBRIDGE_MAX_PEERS} --peer-ttl-sec ${ICEBRIDGE_PEER_TTL_SEC} --max-qps-per-key ${ICEBRIDGE_MAX_QPS_PER_KEY} --dht --bootstrap > ${INSTALL_DIR}/icebridge.log 2>&1 &"
fi

echo "==> Security group checklist (AWS console / CLI):"
echo "    TCP  ${RELAY_PORT}  from 0.0.0.0/0   # identity handshake"
echo "    UDP  ${RUDP_PORT}   from 0.0.0.0/0   # rUDP mesh"
echo "    DO NOT open control HTTP ${CONTROL_HTTP_PORT} — binds 127.0.0.1 only; use:"
echo "      ssh -L 18081:127.0.0.1:${CONTROL_HTTP_PORT} <host>"
echo "    UDP  0-65535 outbound (or at least 6881/25401) for public DHT bootstrap"
echo "==> Done."
