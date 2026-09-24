#!/usr/bin/env bash
# Run the 25-node production IceBridge loopback simulation and generate its wire report.
# Extra arguments are forwarded to run-icebridge-simulation.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec "${SCRIPT_DIR}/run-icebridge-simulation.sh" --wire-only --wire-nodes=25 "$@"
