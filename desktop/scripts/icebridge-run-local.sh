#!/usr/bin/env bash
# Run a standalone IceBridge FORWARDER from the desktop/ tree (laptop or EC2).
#
# Defaults: bind 0.0.0.0, role FORWARDER, DHT on, TCP identity 6888, UDP mesh 6889,
# control HTTP 8081 (loopback only inside the JVM). Builds icebridge.jar if missing,
# never kills existing processes (including old PID-file targets), then runs via
# java -jar. Stop an old instance through its owner, or choose different ports.
# Default (no flags) starts the server detached and tails the log — Ctrl+C
# stops the tail only; the server keeps running.
#
# Usage (from anywhere):
#   ./scripts/icebridge-run-local.sh
#   ./scripts/icebridge-run-local.sh --colo          # ports 7000/7001 for dual-run with desktop
#   ./scripts/icebridge-run-local.sh --gradle        # force ./gradlew icebridge (foreground only)
#   ./scripts/icebridge-run-local.sh --generate-token
#   ./scripts/icebridge-run-local.sh --background    # java -jar only (not --gradle)
#   ICEBRIDGE_RELAY_PORT=7000 ./scripts/icebridge-run-local.sh
#
# Optional desktop/.env is loaded for unset ICEBRIDGE_* keys only (exported env wins).
# EC2: open TCP 6888 + UDP 6889 (or colo ports) in the security group; keep control private
# (already loopback-only). Use SSH -L for /health from elsewhere.
#
# Logs (stdout): successful mesh events at INFO — HELLO / HELLO_ACK, RELAY hops,
# SEARCH / TELEMETRY (PING) / other MeshProtocolId traffic.

set -euo pipefail
umask 077

if [[ "${EUID}" -eq 0 ]]; then
  echo "ERROR: run-local builds and launches code; run it as an unprivileged user." >&2
  exit 1
fi

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
    if [[ "${line}" =~ ^(ICEBRIDGE_[A-Z0-9_]+)=(.*)$ ]]; then
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
  export ICEBRIDGE_CONTROL_HTTP_PORT="${ICEBRIDGE_CONTROL_HTTP_PORT:-18081}"
fi

export ICEBRIDGE_HOST="${ICEBRIDGE_HOST:-0.0.0.0}"
export ICEBRIDGE_ROLE="${ICEBRIDGE_ROLE:-FORWARDER}"
export ICEBRIDGE_RELAY_PORT="${ICEBRIDGE_RELAY_PORT:-6888}"
export ICEBRIDGE_RUDP_PORT="${ICEBRIDGE_RUDP_PORT:-6889}"
export ICEBRIDGE_CONTROL_HTTP_PORT="${ICEBRIDGE_CONTROL_HTTP_PORT:-8081}"
export ICEBRIDGE_BOOTSTRAP="${ICEBRIDGE_BOOTSTRAP:-true}"
export ICEBRIDGE_DHT="${ICEBRIDGE_DHT:-true}"
export ICEBRIDGE_IDENTITY_FILE="${ICEBRIDGE_IDENTITY_FILE:-${ROOT}/identity.dat}"
export ICEBRIDGE_AUTH_TOKENS_FILE="${ICEBRIDGE_AUTH_TOKENS_FILE:-${ROOT}/icebridge-tokens.txt}"
export ICEBRIDGE_MAX_PEERS="${ICEBRIDGE_MAX_PEERS:-10000}"
export ICEBRIDGE_MAX_SESSIONS="${ICEBRIDGE_MAX_SESSIONS:-1024}"
export ICEBRIDGE_MESH_FANOUT="${ICEBRIDGE_MESH_FANOUT:-6}"
export ICEBRIDGE_SEARCH_PEER_FANOUT="${ICEBRIDGE_SEARCH_PEER_FANOUT:-8}"
export ICEBRIDGE_SEARCH_TTL="${ICEBRIDGE_SEARCH_TTL:-2}"
export ICEBRIDGE_PEER_TTL_SEC="${ICEBRIDGE_PEER_TTL_SEC:-300}"
export ICEBRIDGE_MAX_QPS_PER_KEY="${ICEBRIDGE_MAX_QPS_PER_KEY:-30.0}"

# Mode-specific pidfile so default and --colo background runs track separately.
PIDFILE="${ROOT}/icebridge.pid"
if [[ "${COLO}" -eq 1 ]]; then
  PIDFILE="${ROOT}/icebridge-colo.pid"
fi

JAR="${ROOT}/build/libs/icebridge.jar"
JAVA_BIN="${JAVA_BIN:-java}"
if command -v "${JAVA_BIN}" >/dev/null 2>&1; then
  JAVA_BIN="$(command -v "${JAVA_BIN}")"
fi

echo "==> IceBridge run-local"
echo "    host=${ICEBRIDGE_HOST} role=${ICEBRIDGE_ROLE}"
echo "    relay(TCP)=${ICEBRIDGE_RELAY_PORT} rudp(UDP)=${ICEBRIDGE_RUDP_PORT} control=${ICEBRIDGE_CONTROL_HTTP_PORT} (loopback)"
echo "    dht=${ICEBRIDGE_DHT} bootstrap=${ICEBRIDGE_BOOTSTRAP}"
echo "    identity=${ICEBRIDGE_IDENTITY_FILE}"
echo "    tokens=${ICEBRIDGE_AUTH_TOKENS_FILE}"
echo "    SG / firewall: TCP ${ICEBRIDGE_RELAY_PORT}, UDP ${ICEBRIDGE_RUDP_PORT}; control is 127.0.0.1:${ICEBRIDGE_CONTROL_HTTP_PORT}"

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
    echo "==> No tokens file; generating one in the restricted tokens file"
  else
    echo "==> Generating additional token in the restricted tokens file"
  fi
  "${JAVA_BIN}" -jar "${JAR}" \
    --generate-token \
    --auth-tokens-file "${ICEBRIDGE_AUTH_TOKENS_FILE}" \
    > /dev/null
  if [[ "${GENERATE_TOKEN}" -eq 1 ]]; then
    echo "==> Token generation done; not starting server (--generate-token)."
    exit 0
  fi
fi

if [[ "${BACKGROUND}" -eq 1 && "${USE_GRADLE}" -eq 1 ]]; then
  echo "ERROR: --background cannot be combined with --gradle (Gradle PID is not the JVM;" >&2
  echo "       orphans remain after kill). Use java -jar background, or foreground --gradle." >&2
  exit 1
fi

run_server() {
  echo "==> Starting via ./gradlew icebridge (foreground — Ctrl+C stops it)"
  exec ./gradlew icebridge
}

# Launch the JVM detached from this terminal: its own process group (job
# control) + nohup, so Ctrl+C or closing the terminal never reaches it.
# Sets SERVER_PID and LOG. Fails fast when the JVM dies on startup.
start_detached() {
  LOG="${ROOT}/icebridge.log"
  set -m
  # shellcheck disable=SC2086
  nohup "${JAVA_BIN}" -jar "${JAR}" >>"${LOG}" 2>&1 &
  SERVER_PID=$!
  set +m
  echo "${SERVER_PID}" >"${PIDFILE}"
  sleep 1
  if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
    echo "ERROR: server (pid ${SERVER_PID}) died on startup; last log lines:" >&2
    tail -n 30 "${LOG}" >&2 || true
    rm -f "${PIDFILE}"
    return 1
  fi
}

server_started_notice() {
  echo "==> Server running (pid ${SERVER_PID}); log=${LOG}"
  echo "==> Safe to press Ctrl+C or close this terminal — the server keeps running."
  echo "    reattach log: tail -f ${LOG}"
  echo "    health: curl -sS http://127.0.0.1:${ICEBRIDGE_CONTROL_HTTP_PORT}/health"
}

wait_for_health() {
  local i=""
  for i in $(seq 1 10); do
    if curl -sf --max-time 1 \
        "http://127.0.0.1:${ICEBRIDGE_CONTROL_HTTP_PORT}/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

# A PID file or port is not proof of ownership. Let the JVM fail its bind
# rather than terminating somebody else's service or a reused process ID.

if [[ "${USE_GRADLE}" -eq 1 ]]; then
  run_server
fi

# Default and --background: detached start, so terminal Ctrl+C / close never
# reaches the server. Default then follows the log; Ctrl+C stops the tail only.
start_detached || exit 1
server_started_notice
if wait_for_health; then
  echo "    health: OK"
else
  echo "    health: not yet responding; showing log anyway"
fi

if [[ "${BACKGROUND}" -eq 1 ]]; then
  exit 0
fi

echo "==> Following log (Ctrl+C detaches; server keeps running):"
TAIL_PID=""
detach_and_exit() {
  kill "${TAIL_PID}" 2>/dev/null || true
  echo ""
  echo "==> Detached (Ctrl+C). Server keeps running (pid ${SERVER_PID})."
  echo "    reattach log: tail -f ${LOG}"
  echo "    health: curl -sS http://127.0.0.1:${ICEBRIDGE_CONTROL_HTTP_PORT}/health"
  exit 0
}
trap detach_and_exit INT
tail -n 30 -F "${LOG}" &
TAIL_PID=$!
wait "${TAIL_PID}" || true
trap - INT
detach_and_exit
