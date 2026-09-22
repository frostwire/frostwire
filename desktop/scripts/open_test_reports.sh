#!/usr/bin/env bash
# Open the latest Gradle test report, simulation benchmark, or an explicit HTML report.
#
# Usage:
#   ./scripts/open_test_reports.sh
#   ./scripts/open_test_reports.sh --simulation
#   ./scripts/open_test_reports.sh path/to/report.html

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ "${1:-}" == "--simulation" ]]; then
  pointer="${ROOT}/build/reports/icebridge-simulation/latest-report.txt"
  if [[ ! -f "${pointer}" ]]; then
    echo "No simulation report found. Run ./scripts/run-icebridge-simulation.sh first." >&2
    exit 1
  fi
  report="$(<"${pointer}")"
elif [[ -n "${1:-}" ]]; then
  report="$1"
else
  report="${ROOT}/build/reports/tests/test/index.html"
fi

if [[ ! -f "${report}" ]]; then
  echo "Report not found: ${report}" >&2
  echo "Run ./gradlew test or ./scripts/run-icebridge-simulation.sh first." >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin) open "${report}" ;;
  Linux)
    if command -v xdg-open >/dev/null 2>&1; then
      xdg-open "${report}"
    else
      echo "Report: ${report}"
    fi
    ;;
  *) echo "Report: ${report}" ;;
esac
