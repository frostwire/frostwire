#!/usr/bin/env bash
# Build or install an operator-trusted IceBridge artifact on Linux/systemd.
# Build UNPRIVILEGED first, then stage the chosen artifact under a root-owned,
# non-writable-by-others directory. This script never builds or runs a JAR as root.
# Run the trusted checkout's script, never a copy in a service-writable tree:
#   ./desktop/scripts/icebridge-systemd-install.sh --build
#   sudo bash desktop/scripts/icebridge-systemd-install.sh --jar=/trusted/icebridge.jar
# --no-build is accepted for old automation. Installation always requires --jar.
#
# Code: /opt/icebridge (root). Config: /etc/icebridge (root, tokens root:service).
# State: /var/lib/icebridge (service). Existing root config wins over installer
# environment on upgrades. Edit icebridge.env there and restart to apply changes.
# Legacy /opt/icebridge/icebridge.env is parsed as strict allowlisted DATA, never
# sourced. Default legacy identity/token paths migrate; custom paths, symlinks,
# hardlinks, unknown keys or shell syntax require manual operator review. Legacy
# caches are deliberately not imported. Rotate old tokens if compromise is possible.
# Failed migration leaves the service stopped and directories locked down. Review
# the rejected data and rerun this trusted installer; do not start the old unit.
# Prior program directories are retained as DATA under root-only
# ${INSTALL_DIR}.previous.XXXXXXXX/data; operators may archive/remove them later.
#
# All tokens are full node-administrator credentials, NOT isolated tenant tokens.
# Control stays loopback-only; use SSH forwarding, never open its security group.
# This installer does not kill port owners or PID-file targets. A foreign listener
# must be resolved by its operator; IceBridge binding fails without harming it.
#
# Provisional containment, NOT a measured capacity claim: heap 256 MiB, direct
# buffers 128 MiB, total MemoryMax 768 MiB leaves ~384 MiB for JVM/native libtorrent,
# stacks and code. TasksMax 256, NOFILE 8192, sessions 1024, lean fanout by default.
# Tune in a root-owned systemd drop-in only after RSS/native/delivery measurements.
# No MemoryDenyWriteExecute (JIT/JNI need executable mappings); /tmp is private but
# executable for native extraction. State and private temp are the only write areas.

set -euo pipefail
umask 077

fail() { printf 'ERROR: %s\n' "$*" >&2; return 1; }

layout() {
  INSTALL_DIR="${INSTALL_DIR:-/opt/icebridge}"
  CONFIG_DIR="${CONFIG_DIR:-/etc/icebridge}"
  STATE_DIR="${STATE_DIR:-/var/lib/icebridge}"
  SERVICE_USER="${SERVICE_USER:-icebridge}"
  JAVA_BIN="${JAVA_BIN:-/usr/bin/java}"
  local path
  for path in "${INSTALL_DIR}" "${CONFIG_DIR}" "${STATE_DIR}" "${JAVA_BIN}"; do
    [[ "${path}" =~ ^/[a-zA-Z0-9_./+-]+$ && "${path}" != */ && "${path}" != *//*
       && "/${path}/" != */../* && "/${path}/" != */./* ]] || fail "Use simple absolute paths without dot components" || return
  done
  [[ "${SERVICE_USER}" =~ ^[a-z_][a-z0-9_-]*$ && "${SERVICE_USER}" != root ]] || fail "Invalid service user" || return
  [[ "${CONFIG_DIR}/" != "${INSTALL_DIR}/"* && "${INSTALL_DIR}/" != "${CONFIG_DIR}/"*
     && "${STATE_DIR}/" != "${INSTALL_DIR}/"* && "${INSTALL_DIR}/" != "${STATE_DIR}/"*
     && "${STATE_DIR}/" != "${CONFIG_DIR}/"* && "${CONFIG_DIR}/" != "${STATE_DIR}/"* ]] || fail "Code, config and state must be separate" || return
}

# Emits canonical EnvironmentFile assignments. No eval, source, expansion or
# arbitrary exported keys. Limits apply before read so a line cannot grow forever.
normalize_env() {
  local file="$1" line key value size total=0
  local LC_ALL=C
  [[ -f "${file}" && ! -L "${file}" ]] || fail "Config must be a regular non-symlink file" || return
  size=$(wc -c < "${file}")
  (( size <= 65536 )) || fail "Config exceeds 64 KiB" || return
  (( $(tr -d '\000' < "${file}" | wc -c) == size )) || fail "Config contains NUL bytes" || return
  while IFS= read -r -n 4097 line || [[ -n "${line}" ]]; do
    total=$((total + ${#line} + 1))
    (( total <= 65537 )) || fail "Config exceeds 64 KiB while reading" || return
    (( ${#line} <= 4096 )) || fail "Config line exceeds 4096 characters" || return
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" =~ ^[[:space:]]*# ]] && continue
    [[ "${line}" =~ ^(ICEBRIDGE_[A-Z0-9_]+)=(.*)$ ]] || fail "Expected allowlisted KEY=value data" || return
    key="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[2]}"
    if [[ "${value}" == \"*\" || "${value}" == \'*\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    case "${key}" in
      ICEBRIDGE_HOST)
        [[ "${value}" =~ ^[a-zA-Z0-9_.:-]+$ ]] || fail "Invalid ${key}" || return ;;
      ICEBRIDGE_ROLE)
        [[ "${value}" == FORWARDER || "${value}" == CLIENT || "${value}" == BOTH ]] || fail "Invalid ${key}" || return ;;
      ICEBRIDGE_RUDP_PORT|ICEBRIDGE_RELAY_PORT|ICEBRIDGE_CONTROL_HTTP_PORT)
        [[ "${value}" =~ ^[0-9]{1,5}$ ]] && (( 10#${value} <= 65535 )) || fail "Invalid ${key}" || return
        [[ "${key}" != ICEBRIDGE_CONTROL_HTTP_PORT || 10#${value} -gt 0 ]] || fail "Control port must be positive" || return ;;
      ICEBRIDGE_MAX_PEERS|ICEBRIDGE_MAX_SESSIONS|ICEBRIDGE_PEER_TTL_SEC|ICEBRIDGE_MESH_FANOUT|ICEBRIDGE_SEARCH_PEER_FANOUT|ICEBRIDGE_MESH_HOP_TTL|ICEBRIDGE_SEARCH_TTL|ICEBRIDGE_SOFT_MAX|ICEBRIDGE_LEAF_UP_CONNECTIONS)
        [[ "${value}" =~ ^[0-9]{1,9}$ ]] && (( 10#${value} > 0 )) || fail "Invalid ${key}" || return ;;
      ICEBRIDGE_MAX_QPS_PER_KEY)
        [[ "${value}" =~ ^[0-9]{1,6}(\.[0-9]{1,6})?$ && "${value}" =~ [1-9] ]] || fail "Invalid ${key}" || return ;;
      ICEBRIDGE_BOOTSTRAP|ICEBRIDGE_DHT|ICEBRIDGE_SEARCH_APP)
        [[ "${value}" == true || "${value}" == false ]] || fail "Invalid ${key}" || return ;;
      ICEBRIDGE_IDENTITY_FILE)
        case "${value}" in
          identity.dat|./identity.dat|"${INSTALL_DIR}/identity.dat"|"${STATE_DIR}/identity.dat") value="${STATE_DIR}/identity.dat" ;;
          *) fail "Custom legacy identity path requires manual migration"; return 1 ;;
        esac ;;
      ICEBRIDGE_AUTH_TOKENS_FILE)
        case "${value}" in
          icebridge-tokens.txt|./icebridge-tokens.txt|"${INSTALL_DIR}/icebridge-tokens.txt"|"${CONFIG_DIR}/icebridge-tokens.txt") value="${CONFIG_DIR}/icebridge-tokens.txt" ;;
          *) fail "Custom legacy token path requires manual migration"; return 1 ;;
        esac ;;
      *) fail "Unsupported config key: ${key}"; return 1 ;;
    esac
    printf '%s=%s\n' "${key}" "${value}"
  done < "${file}"
}

default_env() {
  local entry key value
  for entry in HOST=0.0.0.0 RUDP_PORT=6889 RELAY_PORT=6888 CONTROL_HTTP_PORT=8081 \
      ROLE=FORWARDER MAX_PEERS=10000 MAX_SESSIONS=1024 PEER_TTL_SEC=300 \
      MAX_QPS_PER_KEY=30.0 BOOTSTRAP=true DHT=true SEARCH_APP=true \
      MESH_FANOUT=6 SEARCH_PEER_FANOUT=8 MESH_HOP_TTL=3 SEARCH_TTL=2 \
      SOFT_MAX=3 LEAF_UP_CONNECTIONS=3; do
    key="ICEBRIDGE_${entry%%=*}"; value="${entry#*=}"
    printf '%s=%s\n' "${key}" "${!key:-${value}}"
  done
  printf 'ICEBRIDGE_IDENTITY_FILE=%s/identity.dat\n' "${STATE_DIR}"
  printf 'ICEBRIDGE_AUTH_TOKENS_FILE=%s/icebridge-tokens.txt\n' "${CONFIG_DIR}"
}

render_unit() {
  cat <<EOF
[Unit]
Description=FrostWire IceBridge relay
After=network-online.target
Wants=network-online.target
StartLimitIntervalSec=60
StartLimitBurst=3

[Service]
Type=simple
User=${SERVICE_USER}
Group=${SERVICE_USER}
WorkingDirectory=${STATE_DIR}
EnvironmentFile=${CONFIG_DIR}/icebridge.env
Environment=ICEBRIDGE_LOAD_DOT_ENV=false
ExecStart=${JAVA_BIN} -Xms64m -Xmx256m -XX:MaxDirectMemorySize=128m -XX:ActiveProcessorCount=2 -Duser.home=${STATE_DIR} -jar ${INSTALL_DIR}/icebridge.jar
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
KillMode=control-group
UMask=0077
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=${STATE_DIR}
PrivateTmp=true
PrivateDevices=true
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictSUIDSGID=true
RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6 AF_NETLINK
CapabilityBoundingSet=
AmbientCapabilities=
MemoryMax=768M
TasksMax=256
LimitNOFILE=8192
LimitCORE=0
LogRateLimitIntervalSec=30s
LogRateLimitBurst=2000

[Install]
WantedBy=multi-user.target
EOF
}

# Linux stat, with every ancestor checked: a root-owned leaf under a writable
# parent is not a trusted destination. Never follow symlinks while repairing.
trusted_dir() {
  local path="$1" mode
  while :; do
    [[ -d "${path}" && ! -L "${path}" && $(stat -c %u -- "${path}") == 0 ]] || fail "Untrusted directory: ${path}" || return
    mode=$(stat -c %a -- "${path}")
    (( (8#${mode} & 0022) == 0 )) || fail "Writable directory: ${path}" || return
    [[ "${path}" == / ]] && break
    path=$(dirname -- "${path}")
  done
}

regular_file() {
  [[ -f "$1" && ! -L "$1" && $(stat -c %h -- "$1") == 1 ]] || fail "Expected regular non-linked file: $1"
}

trusted_file() {
  local mode
  trusted_dir "$(dirname -- "$1")" || return
  regular_file "$1" || return
  [[ $(stat -c %u -- "$1") == 0 ]] || fail "Expected root-owned file: $1" || return
  mode=$(stat -c %a -- "$1")
  (( (8#${mode} & 0022) == 0 )) || fail "Writable trusted file: $1"
}

# Source names are locked down by their root-owned parent first. A process may
# still hold a writable file descriptor, so bound the actual copy, not just stat.
snapshot_data() {
  regular_file "$1" || return
  head -c 65537 -- "$1" > "$2" || return
  (( $(wc -c < "$2") <= 65536 )) || fail "Migration data exceeds 64 KiB"
}

main() {
  layout
  local jar="" build=0 root script_path staged_jar
  root=$(cd "$(dirname -- "$0")/.." && pwd)
  script_path=$(realpath -e -- "$0")
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --jar=*) jar="${1#--jar=}" ;;
      --jar)
        shift
        [[ $# -gt 0 ]] || fail "--jar requires a path" || return
        jar="$1"
        ;;
      --build) build=1 ;;
      --no-build) ;;
      --help|-h) printf 'Usage: bash icebridge-systemd-install.sh --build\n'; printf '       sudo bash icebridge-systemd-install.sh --jar=/trusted/prebuilt.jar\n'; return ;;
      *) fail "Unknown option"; return 1 ;;
    esac
    shift
  done
  if (( build )); then
    [[ "${EUID}" -ne 0 ]] || fail "--build must run as an unprivileged user; do not use sudo" || return
    [[ -z "${jar}" ]] || fail "--build cannot be combined with --jar" || return
    printf 'Building IceBridge as %s...\n' "$(id -un)"
    (cd "${root}" && ./gradlew icebridgeJar)
    jar="${root}/build/libs/icebridge.jar"
    [[ -f "${jar}" && ! -L "${jar}" ]] || fail "Build completed without ${jar}" || return
    printf 'Built %s\n' "${jar}"
    staged_jar="/root/icebridge-build/icebridge.jar"
    printf 'Stage the artifact in a root-owned directory, then install it with:\n'
    printf '  sudo install -D -o root -g root -m 0644 -- "%s" "%s"\n' "${jar}" "${staged_jar}"
    printf '  sudo "%s" --jar="%s"\n' "${script_path}" "${staged_jar}"
    return
  fi
  [[ "${EUID}" -eq 0 ]] || fail "Install requires root; build first as an unprivileged user. No automatic elevation." || return
  export PATH=/usr/sbin:/usr/bin:/sbin:/bin
  [[ -n "${jar}" ]] || fail "Explicit --jar required; never reuse a service-owned installed JAR" || return
  jar=$(realpath -e -- "${jar}")
  [[ "${jar}" != "${INSTALL_DIR}/"* && "${jar}" != "${STATE_DIR}/"* ]] || fail "Artifact must come from outside the old service layout" || return
  trusted_file "${jar}"
  JAVA_BIN=$(readlink -f -- "${JAVA_BIN}")
  trusted_dir "$(dirname -- "${JAVA_BIN}")"
  regular_file "${JAVA_BIN}"
  [[ -x "${JAVA_BIN}" ]] || fail "Java must be an executable" || return
  local mode
  mode=$(stat -c %a -- "${JAVA_BIN}")
  (( (8#${mode} & 0022) == 0 )) || fail "Java must not be writable by the service or other users" || return
  local dir
  for dir in "${INSTALL_DIR}" "${CONFIG_DIR}" "${STATE_DIR}"; do
    trusted_dir "$(dirname -- "${dir}")"
    [[ ! -L "${dir}" ]] || fail "Refusing symlink directory: ${dir}" || return
  done
  trusted_dir /etc/systemd/system
  [[ ! -L /etc/systemd/system/icebridge.service ]] || fail "Refusing symlink unit" || return
  if [[ -e /etc/systemd/system/icebridge.service ]]; then
    trusted_file /etc/systemd/system/icebridge.service
  fi
  # Only stop the named root-managed unit. Never infer ownership from a port/PID.
  if systemctl cat icebridge.service >/dev/null 2>&1; then
    systemctl stop icebridge.service
  fi
  id -u "${SERVICE_USER}" >/dev/null 2>&1 || useradd --system --user-group --home-dir "${STATE_DIR}" --shell /usr/sbin/nologin "${SERVICE_USER}"
  [[ $(id -u "${SERVICE_USER}") != 0 ]] || fail "Service account must not have uid 0" || return
  # Revoke directory access before inspecting legacy children. Even a surviving
  # service process must not swap these paths between validation and copying.
  install -d -o root -g root -m 0700 "${INSTALL_DIR}"
  trusted_dir "${INSTALL_DIR}"
  if [[ -e "${CONFIG_DIR}" ]]; then trusted_dir "${CONFIG_DIR}"; fi
  install -d -o root -g "${SERVICE_USER}" -m 0750 "${CONFIG_DIR}"
  install -d -o root -g root -m 0700 "${STATE_DIR}"
  local stage
  stage=$(mktemp -d "${CONFIG_DIR}/.install.XXXXXXXX")
  # stage lives under a root-only-writable parent, never under service state.
  trap "rm -rf -- '${stage}'" EXIT
  default_env > "${stage}/defaults"
  normalize_env "${stage}/defaults" > "${stage}/env"
  local old_env="${CONFIG_DIR}/icebridge.env"
  if [[ ! -e "${old_env}" && ! -L "${old_env}" ]]; then old_env="${INSTALL_DIR}/icebridge.env"; fi
  if [[ -e "${old_env}" || -L "${old_env}" ]]; then
    snapshot_data "${old_env}" "${stage}/old-env"
    # Defaults precede preserved assignments, so the operator's last value wins.
    normalize_env "${stage}/old-env" >> "${stage}/env"
  fi
  awk -F= '!($1 in values) { keys[++n]=$1 } { values[$1]=$0 } END { for (i=1;i<=n;i++) print values[keys[i]] }' "${stage}/env" > "${stage}/final-env"
  local target="${CONFIG_DIR}/icebridge-tokens.txt"
  if [[ -e "${target}" || -L "${target}" ]]; then
    snapshot_data "${target}" "${stage}/tokens"
  elif [[ -e "${INSTALL_DIR}/icebridge-tokens.txt" || -L "${INSTALL_DIR}/icebridge-tokens.txt" ]]; then
    snapshot_data "${INSTALL_DIR}/icebridge-tokens.txt" "${stage}/tokens"
  else
    # OS randomness, not operator-supplied executable code; never print credentials.
    openssl rand -hex 32 > "${stage}/tokens"
  fi
  chown root:"${SERVICE_USER}" "${stage}/tokens"
  chmod 0640 "${stage}/tokens"
  target="${STATE_DIR}/identity.dat"
  if [[ ! -e "${target}" && ! -L "${target}" && ( -e "${INSTALL_DIR}/identity.dat" || -L "${INSTALL_DIR}/identity.dat" ) ]]; then
    # Stage outside service-owned state, then rename. No following destination links.
    snapshot_data "${INSTALL_DIR}/identity.dat" "${stage}/identity.dat"
    chown "${SERVICE_USER}:${SERVICE_USER}" "${stage}/identity.dat"
    chmod 0600 "${stage}/identity.dat"
    mv -T -- "${stage}/identity.dat" "${target}"
  elif [[ -e "${target}" || -L "${target}" ]]; then
    regular_file "${target}"
  fi
  install -o root -g root -m 0644 "${jar}" "${stage}/icebridge.jar"
  render_unit > "${stage}/unit"
  chmod 0644 "${stage}/unit"
  chmod 0600 "${stage}/final-env"
  mv -T -- "${stage}/tokens" "${CONFIG_DIR}/icebridge-tokens.txt"
  mv -T -- "${stage}/final-env" "${CONFIG_DIR}/icebridge.env"
  # Do not leave service-owned scripts, libraries or old env files in the active
  # code tree. The backup is never sourced, built, added to a classpath or run.
  local previous
  previous=$(mktemp -d "${INSTALL_DIR}.previous.XXXXXXXX")
  mv -T -- "${INSTALL_DIR}" "${previous}/data"
  install -d -o root -g root -m 0755 "${INSTALL_DIR}"
  mv -T -- "${stage}/icebridge.jar" "${INSTALL_DIR}/icebridge.jar"
  mv -T -- "${stage}/unit" /etc/systemd/system/icebridge.service
  chown "${SERVICE_USER}:${SERVICE_USER}" "${STATE_DIR}"
  systemctl daemon-reload
  systemctl enable icebridge.service
  systemctl start icebridge.service
  printf 'Installed root-owned code/config and separate service state. Inspect systemctl status icebridge.service.\n'
  printf 'Control is loopback-only. Preserve config edits in %s/icebridge.env; restart to apply.\n' "${CONFIG_DIR}"
  printf 'Prior install retained as data only in %s/data\n' "${previous}"
  rm -rf -- "${stage}"
  trap - EXIT
}

# Tests may source THIS trusted script to exercise only the parser/unit renderer.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then main "$@"; fi
