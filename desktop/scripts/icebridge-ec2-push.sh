#!/usr/bin/env bash
# Build multi-arch icebridge.jar on this machine and scp to an EC2 host.
#
# Usage:
#   ./scripts/icebridge-ec2-push.sh virginia1
#   ./scripts/icebridge-ec2-push.sh ec2-user@54.x.x.x /opt/icebridge
#
# Then on the host:
#   sudo bash /opt/icebridge/icebridge-ec2-install.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${1:?SSH host or user@host required}"
REMOTE_DIR="${2:-/opt/icebridge}"

cd "${ROOT}"
echo "==> Building icebridge.jar (includes Linux jlibtorrent natives)"
./gradlew icebridgeJar

JAR="${ROOT}/build/libs/icebridge.jar"
if ! jar tf "${JAR}" | grep -q 'lib/x86_64/.*\.so'; then
  echo "ERROR: fat JAR missing Linux x86_64 jlibtorrent .so — refuse to deploy" >&2
  exit 1
fi

echo "==> Ensuring remote dir ${REMOTE_DIR}"
ssh "${HOST}" "mkdir -p '${REMOTE_DIR}'"

echo "==> Uploading jar + install script"
scp "${JAR}" "${HOST}:${REMOTE_DIR}/icebridge.jar"
scp "${ROOT}/scripts/icebridge-ec2-install.sh" "${HOST}:${REMOTE_DIR}/icebridge-ec2-install.sh"
ssh "${HOST}" "chmod +x '${REMOTE_DIR}/icebridge-ec2-install.sh'"

echo "==> Upload complete."
echo "On the host, install/restart:"
echo "  ssh ${HOST} 'sudo INSTALL_DIR=${REMOTE_DIR} bash ${REMOTE_DIR}/icebridge-ec2-install.sh'"
echo
echo "Desktop smoke (after SG open):"
echo "  - Settings → IceBridge: enable, remote URL http://<public-ip>:8080 + token from host icebridge-token.once"
echo "  - OR pure DHT discovery: local child OK; wait ~60s for frostwire-relays-v1; verified peer in PeerDirectory"
echo "  - curl health via tunnel: ssh -L 18080:127.0.0.1:8080 ${HOST} then curl http://127.0.0.1:18080/health"
