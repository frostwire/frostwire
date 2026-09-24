#!/usr/bin/env bash
# Run the isolated IceBridge simulation tests, benchmark, live viewer, and HTML report.
#
# Usage (from desktop/ or anywhere):
#   ./scripts/run-icebridge-simulation.sh
#   ./scripts/run-icebridge-simulation.sh --no-ui --no-open
#   ./scripts/run-icebridge-simulation.sh --seed=20260922 --pace-ms=20
#   ./scripts/run-icebridge-simulation.sh --wire-only --wire-nodes=6
#   ./scripts/run-icebridge-simulation.sh --wire-only --wire-nodes=25

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

WIRE_ONLY=0
WIRE_NODES=3
SIMULATION_ARGS=()
for argument in "$@"; do
  if [[ "${argument}" =~ [[:space:]] ]]; then
    echo "Simulation arguments cannot contain whitespace: ${argument}" >&2
    exit 2
  fi
  case "${argument}" in
    --wire-only) WIRE_ONLY=1 ;;
    --wire-nodes=*) WIRE_NODES="${argument#--wire-nodes=}" ;;
    *) SIMULATION_ARGS+=("${argument}") ;;
  esac
done
if ! [[ "${WIRE_NODES}" =~ ^[0-9]+$ ]] || (( 10#${WIRE_NODES} < 2 || 10#${WIRE_NODES} > 25 )); then
  echo "--wire-nodes must be between 2 and 25" >&2
  exit 2
fi

echo "Running IceBridge simulation regression tests..."
./gradlew simulationTest verifySimulationIsolation

echo "Running production IceBridge stacks on distinct loopback ports..."
./gradlew runIceBridgeLoopback -Ploopback.nodes="${WIRE_NODES}"

if [[ "${WIRE_ONLY}" -eq 1 ]]; then
  exit 0
fi

echo "Launching IceBridge network benchmark..."
if [[ ${#SIMULATION_ARGS[@]} -gt 0 ]]; then
  ./gradlew runIceBridgeSimulation -Psimulation.args="${SIMULATION_ARGS[*]}"
else
  ./gradlew runIceBridgeSimulation
fi
