#!/usr/bin/env bash
# Run the isolated IceBridge simulation tests, benchmark, live viewer, and HTML report.
#
# Usage (from desktop/ or anywhere):
#   ./scripts/run-icebridge-simulation.sh
#   ./scripts/run-icebridge-simulation.sh --no-ui --no-open
#   ./scripts/run-icebridge-simulation.sh --seed=20260922 --pace-ms=20

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

for argument in "$@"; do
  if [[ "${argument}" =~ [[:space:]] ]]; then
    echo "Simulation arguments cannot contain whitespace: ${argument}" >&2
    exit 2
  fi
done

echo "Running IceBridge simulation regression tests..."
./gradlew simulationTest verifySimulationIsolation

echo "Launching IceBridge network benchmark..."
if [[ $# -gt 0 ]]; then
  ./gradlew runIceBridgeSimulation -Psimulation.args="$*"
else
  ./gradlew runIceBridgeSimulation
fi
