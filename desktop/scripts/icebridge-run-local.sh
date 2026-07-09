#!/usr/bin/env bash
# Run a standalone IceBridge FORWARDER from the desktop/ tree (laptop or EC2).
#
# Defaults: bind 0.0.0.0, role FORWARDER, DHT on, TCP identity 6888, UDP mesh 6889,
# control HTTP 8080. Builds icebridge.jar if missing, then runs via java -jar
# (preferred over long-lived Gradle).
#
# Usage (from anywhere):
#   ./scripts/icebridge-run-local.sh
#   ./scripts/icebridge-run-local.sh --colo          # ports 7000/7001 for dual-run with desktop
#   ./scripts/icebridge-run-local.sh --gradle        # force ./gradlew icebridge
#   ./scripts/icebridge-run-local.sh --generate-token
#   ./scripts/icebridge-run-local.sh --background
#   ICEBRIDGE_RELAY_PORT=7000 ./scripts/icebridge-run-local.sh
#
# Optional desktop/.env is loaded for unset ICEBRIDGE_* keys only (exported env wins).
# EC2: open TCP 6888 + UDP 6889 (or colo ports) in the security group; keep 8080 private.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

USE_GRADLE=0
BACKGROUND=0
GENERATE_TOKEN=0
COLO=0

usage() {
  awk 'NR==1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "$0"
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage 0 ;;
    --gradle) USE_GRADLE=1 ;;
    --jar) USE_GRADLE=0 ;;
    --background|-d) BACKGROUND=1 ;;
    --generate-token) GENERATE_TOKEN=1 ;;
    --colo|--colocated)
      COLO=1
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage 1
      ;;
  esac
  shift
done

# Load .env for keys not already in the environment (do not override exports).
if [[ -f "${ROOT}/.env" ]]; then
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ -z "${line}" || "${line}" =~ ^[[:space:]]*# ]] && continue
    if [[ "${line}" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
      key="${BASH_REMATCH[1]}"
      val="${BASH_REMATCH[2]}"
      val="${val%\"}"
      val="${val#\"}"
      val="${val%\'}"
      val="${val#\'}"
      if [[ -z "${!key+x}" ]]; then
        export "${key}=${val}"
      fi
    fi
  done < "${ROOT}/.env"
fi

if [[ "${COLO}" -eq 1 ]]; then
  # Avoid clashing with desktop defaults (identity 6888, child rUDP 6889).
  export ICEBRIDGE_RELAY_PORT="${ICEBRIDGE_RELAY_PORT:-7000}"
  export ICEBRIDGE_RUDP_PORT="${ICEBRIDGE_RUDP_PORT:-7001}"
  export ICEBRIDGE_CONTROL_HTTP_PORT="${ICEBRIDGE_CONTROL_HTTP_PORT:-18080}"
fi

export ICEBRIDGE_HOST="${ICEBRIDGE_HOST:-0.0.0.0}"
export ICEBRIDGE_ROLE="${ICEBRIDGE_ROLE:-FORWARDER}"
export ICEBRIDGE_RELAY_PORT="${ICEBRIDGE_RELAY_PORT:-6888}"
export ICEBRIDGE_RUDP_PORT="${ICEBRIDGE_RUDP_PORT:-6889}"
export ICEBRIDGE_CONTROL_HTTP_PORT="${ICEBRIDGE_CONTROL_HTTP_PORT:-8080}"
export ICEBRIDGE_BOOTSTRAP="${ICEBRIDGE_BOOTSTRAP:-true}"
export ICEBRIDGE_DHT="${ICEBRIDGE_DHT:-true}"
export ICEBRIDGE_IDENTITY_FILE="${ICEBRIDGE_IDENTITY_FILE:-${ROOT}/identity.dat}"
export ICEBRIDGE_AUTH_TOKENS_FILE="${ICEBRIDGE_AUTH_TOKENS_FILE:-${ROOT}/icebridge-tokens.txt}"
export ICEBRIDGE_MAX_PEERS="${ICEBRIDGE_MAX_PEERS:-10000}"
export ICEBRIDGE_PEER_TTL_SEC="${ICEBRIDGE_PEER_TTL_SEC:-300}"
export ICEBRIDGE_MAX_QPS_PER_KEY="${ICEBRIDGE_MAX_QPS_PER_KEY:-30.0}"

JAR="${ROOT}/build/libs/icebridge.jar"
JAVA_BIN="${JAVA_BIN:-java}"

echo "==> IceBridge run-local"
echo "    host=${ICEBRIDGE_HOST} role=${ICEBRIDGE_ROLE}"
echo "    relay(TCP)=${ICEBRIDGE_RELAY_PORT} rudp(UDP)=${ICEBRIDGE_RUDP_PORT} control=${ICEBRIDGE_CONTROL_HTTP_PORT}"
echo "    dht=${ICEBRIDGE_DHT} bootstrap=${ICEBRIDGE_BOOTSTRAP}"
echo "    identity=${ICEBRIDGE_IDENTITY_FILE}"
echo "    tokens=${ICEBRIDGE_AUTH_TOKENS_FILE}"
echo "    SG / firewall: TCP ${ICEBRIDGE_RELAY_PORT}, UDP ${ICEBRIDGE_RUDP_PORT}; keep TCP ${ICEBRIDGE_CONTROL_HTTP_PORT} private"

if [[ ! -f "${JAR}" ]]; then
  echo "==> Building icebridge.jar"
  ./gradlew icebridgeJar
fi

if [[ ! -f "${JAR}" ]]; then
  echo "ERROR: ${JAR} missing after build" >&2
  exit 1
fi

if [[ "${GENERATE_TOKEN}" -eq 1 ]] || [[ ! -f "${ICEBRIDGE_AUTH_TOKENS_FILE}" ]]; then
  if [[ ! -f "${ICEBRIDGE_AUTH_TOKENS_FILE}" ]]; then
    echo "==> No tokens file; generating one (printed once)"
  else
    echo "==> Generating additional token (printed once)"
  fi
  token_once="${ICEBRIDGE_AUTH_TOKENS_FILE}.once"
  ${JAVA_BIN} -jar "${JAR}" \
    --generate-token \
    --auth-tokens-file "${ICEBRIDGE_AUTH_TOKENS_FILE}" \
    | tee "${token_once}"
  chmod 600 "${ICEBRIDGE_AUTH_TOKENS_FILE}" "${token_once}" 2>/dev/null || true
  if [[ "${GENERATE_TOKEN}" -eq 1 ]]; then
    echo "==> Token generation done; not starting server (--generate-token)."
    exit 0
  fi
fi

run_server() {
  if [[ "${USE_GRADLE}" -eq 1 ]]; then
    echo "==> Starting via ./gradlew icebridge"
    exec ./gradlew icebridge
  fi
  echo "==> Starting via java -jar (foreground)"
  exec ${JAVA_BIN} -jar "${JAR}"
}

if [[ "${BACKGROUND}" -eq 1 ]]; then
  LOG="${ROOT}/icebridge.log"
  echo "==> Starting in background; log=${LOG}"
  if [[ "${USE_GRADLE}" -eq 1 ]]; then
    nohup ./gradlew icebridge >>"${LOG}" 2>&1 &
  else
    nohup ${JAVA_BIN} -jar "${JAR}" >>"${LOG}" 2>&1 &
  fi
  echo $! >"${ROOT}/icebridge.pid"
  echo "    pid=$(cat "${ROOT}/icebridge.pid")"
  echo "    health: curl -sS http://127.0.0.1:${ICEBRIDGE_CONTROL_HTTP_PORT}/health"
  exit 0
fi

run_server
