#!/usr/bin/env bash
# Build multi-arch icebridge.jar on this machine and scp to an EC2 host.
#
# Usage:
#   ./scripts/icebridge-ec2-push.sh virginia1
#   ./scripts/icebridge-ec2-push.sh ec2-user@54.x.x.x /opt/icebridge
#
# Remote UI install (on host):
#   ssh host 'sudo INSTALL_DIR=/opt/icebridge bash /opt/icebridge/icebridge-ec2-install.sh'
#
# Control HTTP is loopback-only (127.0.0.1). Desktop remote control needs an SSH tunnel,
# not http://PUBLIC_IP:controlPort. Pure DHT discovery uses TCP relay + UDP mesh ports only.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${1:?SSH host or user@host required}"
REMOTE_DIR="${2:-/opt/icebridge}"
CONTROL_HTTP_PORT="${ICEBRIDGE_CONTROL_HTTP_PORT:-8081}"
JAVA_BIN="${JAVA_BIN:-java}"

cd "${ROOT}"
echo "==> Building icebridge.jar (includes Linux jlibtorrent natives)"
./gradlew icebridgeJar

JAR="${ROOT}/build/libs/icebridge.jar"
if [[ ! -f "${JAR}" ]]; then
  echo "ERROR: ${JAR} missing after build" >&2
  exit 1
fi

# zip/java tools only — no JDK `jar` binary required
has_native() {
  local pattern="$1"
  if command -v unzip >/dev/null 2>&1; then
    unzip -Z1 "${JAR}" 2>/dev/null | grep -qE "${pattern}"
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "${JAR}" "${pattern}" <<'PY'
import re, sys, zipfile
jar, pat = sys.argv[1], sys.argv[2]
rx = re.compile(pat)
with zipfile.ZipFile(jar) as z:
    sys.exit(0 if any(rx.search(n) for n in z.namelist()) else 1)
PY
  elif command -v jar >/dev/null 2>&1; then
    jar tf "${JAR}" | grep -qE "${pattern}"
  else
    echo "ERROR: need unzip, python3, or jar to verify natives in fat JAR" >&2
    return 2
  fi
}

if ! has_native 'lib/x86_64/.*\.so'; then
  echo "ERROR: fat JAR missing Linux x86_64 jlibtorrent .so — refuse to deploy" >&2
  exit 1
fi
# Optional arm64 (Graviton); warn only — most EC2 amzn/ubuntu x86_64
if ! has_native 'lib/aarch64/.*\.so' && ! has_native 'lib/arm64/.*\.so'; then
  echo "WARNING: no Linux arm64 .so in JAR (OK for x86_64 hosts; fail on Graviton)" >&2
fi

echo "==> Ensuring remote dir ${REMOTE_DIR} (uses sudo if needed)"
# Create a writeable REMOTE_DIR; fall back to sudo+chown for /opt/* paths.
ssh "${HOST}" "REMOTE_DIR=$(printf %q "${REMOTE_DIR}") bash -s" <<'REMOTE'
set -euo pipefail
if mkdir -p "${REMOTE_DIR}" 2>/dev/null && [[ -w "${REMOTE_DIR}" ]]; then
  exit 0
fi
if command -v sudo >/dev/null 2>&1; then
  sudo mkdir -p "${REMOTE_DIR}"
  sudo chown "$(id -u):$(id -g)" "${REMOTE_DIR}"
  exit 0
fi
echo "ERROR: cannot create writable ${REMOTE_DIR} (no sudo)" >&2
exit 1
REMOTE

echo "==> Uploading jar + install script"
scp "${JAR}" "${HOST}:${REMOTE_DIR}/icebridge.jar"
scp "${ROOT}/scripts/icebridge-ec2-install.sh" "${HOST}:${REMOTE_DIR}/icebridge-ec2-install.sh"
ssh "${HOST}" "chmod +x '${REMOTE_DIR}/icebridge-ec2-install.sh'"

echo "==> Upload complete."
echo "On the host, install/restart:"
echo "  ssh ${HOST} 'sudo INSTALL_DIR=${REMOTE_DIR} bash ${REMOTE_DIR}/icebridge-ec2-install.sh'"
echo
echo "Desktop smoke (after SG open for TCP identity + UDP mesh only):"
echo "  - Pure DHT (preferred): keep desktop local child; do NOT seed host-cache with EC2 IP;"
echo "    wait ~60s for frostwire-relays-v1; expect verified peer in PeerDirectory"
echo "  - Remote control plane: control binds 127.0.0.1 ONLY — use tunnel, not public IP:"
echo "      ssh -L 18081:127.0.0.1:${CONTROL_HTTP_PORT} ${HOST}"
echo "      Settings → IceBridge USE_REMOTE URL http://127.0.0.1:18081 + token from host icebridge-token.once"
echo "  - Health: curl http://127.0.0.1:18081/health  (while tunnel is up)"
