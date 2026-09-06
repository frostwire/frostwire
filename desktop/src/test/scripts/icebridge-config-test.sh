#!/usr/bin/env bash
# Pure installer parser/rendering fixtures. Never invokes installer main or a JVM.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
source "${REPO}/desktop/scripts/icebridge-systemd-install.sh"
work=$(mktemp -d "${TMPDIR:-/tmp}/icebridge-config.XXXXXXXX")
trap 'rm -rf -- "${work}"' EXIT
checks=0

assert() {
  "$@" || { printf 'FAILED: %s\n' "$*" >&2; exit 1; }
  checks=$((checks + 1))
}

reject() {
  printf '%s\n' "$1" > "${work}/input"
  if normalize_env "${work}/input" > "${work}/output" 2> "${work}/error"; then
    printf 'FAILED: accepted invalid config fixture\n' >&2
    exit 1
  fi
  checks=$((checks + 1))
}

# Abort if a future edit accidentally calls installation/privileged tools here.
systemctl() { exit 90; }
sudo() { exit 91; }
useradd() { exit 92; }
chown() { exit 93; }
layout
default_env > "${work}/defaults"
normalize_env "${work}/defaults" > "${work}/normalized"
assert cmp -s "${work}/defaults" "${work}/normalized"

printf '%s\n' '# legacy fixture' 'ICEBRIDGE_ROLE="BOTH"' \
  'ICEBRIDGE_IDENTITY_FILE=/opt/icebridge/identity.dat' \
  "ICEBRIDGE_AUTH_TOKENS_FILE='./icebridge-tokens.txt'" \
  'ICEBRIDGE_BOOTSTRAP=false' 'ICEBRIDGE_DHT=false' > "${work}/legacy"
normalize_env "${work}/legacy" > "${work}/normalized"
assert grep -Fxq 'ICEBRIDGE_IDENTITY_FILE=/var/lib/icebridge/identity.dat' "${work}/normalized"
assert grep -Fxq 'ICEBRIDGE_AUTH_TOKENS_FILE=/etc/icebridge/icebridge-tokens.txt' "${work}/normalized"
assert grep -Fxq 'ICEBRIDGE_ROLE=BOTH' "${work}/normalized"
assert grep -Fxq 'ICEBRIDGE_DHT=false' "${work}/normalized"
assert grep -Fxq 'ICEBRIDGE_BOOTSTRAP=false' "${work}/normalized"

reject 'ICEBRIDGE_HOST=$(false)'
reject 'ICEBRIDGE_HOST=`false`'
reject 'ICEBRIDGE_HOST=localhost;false'
reject 'JAVA_TOOL_OPTIONS=-javaagent:fixture.jar'
reject 'ICEBRIDGE_LOAD_DOT_ENV=true'
reject 'ICEBRIDGE_AUTH_TOKEN=fixture-only'
reject 'ICEBRIDGE_IDENTITY_FILE=/different/identity.dat'
reject 'ICEBRIDGE_AUTH_TOKENS_FILE=/different/tokens.txt'
reject 'ICEBRIDGE_DHT=maybe'
reject 'ICEBRIDGE_CONTROL_HTTP_PORT=0'
reject 'ICEBRIDGE_RUDP_PORT=65536'
reject 'ICEBRIDGE_MAX_SESSIONS=0'
reject 'ICEBRIDGE_MAX_QPS_PER_KEY=NaN'
reject 'ICEBRIDGE_ROLE="BOTH'
reject $'ICEBRIDGE_DHT=true\nexport ICEBRIDGE_HOST=localhost'
reject "$(printf '%4097s' x)"
reject "$(printf '%65537s' x)"
printf 'ICEBRIDGE_DHT=tr\000ue\n' > "${work}/nul"
if normalize_env "${work}/nul" >/dev/null 2>&1; then exit 1; fi
checks=$((checks + 1))

# Symlink inputs fail before reading data.
ln -s "${work}/legacy" "${work}/link"
if normalize_env "${work}/link" >/dev/null 2>&1; then exit 1; fi
checks=$((checks + 1))

# The installer targets GNU stat; adapt only its link-count query for macOS fixtures.
if [[ "$(uname -s)" == Darwin ]]; then
  stat() {
    [[ "$1" == -c && "$2" == %h && "$3" == -- ]] || return 99
    command stat -f %l "$4"
  }
fi
assert snapshot_data "${work}/legacy" "${work}/snapshot"
assert cmp -s "${work}/legacy" "${work}/snapshot"
ln "${work}/legacy" "${work}/hardlink"
if snapshot_data "${work}/hardlink" "${work}/snapshot" >/dev/null 2>&1; then exit 1; fi
checks=$((checks + 1))
if snapshot_data "${work}/link" "${work}/snapshot" >/dev/null 2>&1; then exit 1; fi
checks=$((checks + 1))
printf '%65537s' x > "${work}/oversize"
if snapshot_data "${work}/oversize" "${work}/snapshot" >/dev/null 2>&1; then exit 1; fi
checks=$((checks + 1))
assert test "$(wc -c < "${work}/snapshot" | tr -d ' ')" = 65537

render_unit > "${work}/unit"
assert grep -Fxq 'WorkingDirectory=/var/lib/icebridge' "${work}/unit"
assert grep -Fxq 'EnvironmentFile=/etc/icebridge/icebridge.env' "${work}/unit"
assert grep -Fxq 'Environment=ICEBRIDGE_LOAD_DOT_ENV=false' "${work}/unit"
assert grep -Fxq 'ReadWritePaths=/var/lib/icebridge' "${work}/unit"
assert grep -Fxq 'ProtectSystem=strict' "${work}/unit"
assert grep -Fxq 'User=icebridge' "${work}/unit"
assert grep -Fxq 'UMask=0077' "${work}/unit"
assert grep -Fxq 'MemoryMax=768M' "${work}/unit"
if grep '^ExecStart=.*--' "${work}/unit"; then exit 1; fi
checks=$((checks + 1))

if (STATE_DIR=/opt/icebridge/state; layout) >/dev/null 2>&1; then exit 1; fi
checks=$((checks + 1))
if (INSTALL_DIR=/opt/../icebridge; layout) >/dev/null 2>&1; then exit 1; fi
checks=$((checks + 1))
if (SERVICE_USER=root; layout) >/dev/null 2>&1; then exit 1; fi
checks=$((checks + 1))
printf 'PASS: %s isolated parser/layout/unit checks; no installation or JVM executed\n' "${checks}"
