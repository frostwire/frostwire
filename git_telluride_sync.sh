#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${SCRIPT_DIR}"
SUBMODULE_PATH="${REPO_ROOT}/telluride"

cd "${REPO_ROOT}"

if ! git rev-parse --show-toplevel >/dev/null 2>&1; then
    echo "Error: ${REPO_ROOT} is not a git repository."
    exit 1
fi

if [[ ! -d "${SUBMODULE_PATH}" ]]; then
    echo "Error: telluride submodule directory not found at ${SUBMODULE_PATH}."
    exit 1
fi

if [[ -n "$(git -C "${SUBMODULE_PATH}" status --porcelain)" ]]; then
    echo "Error: telluride has local changes. Commit, stash, or discard them before syncing."
    exit 1
fi

echo "Fetching telluride remote refs..."
git -C "${SUBMODULE_PATH}" fetch origin

echo "Checking out telluride master..."
git -C "${SUBMODULE_PATH}" checkout master

echo "Setting telluride upstream to origin/master..."
git -C "${SUBMODULE_PATH}" branch --set-upstream-to=origin/master master

echo "Fast-forwarding telluride to origin/master..."
git -C "${SUBMODULE_PATH}" pull --ff-only origin master

echo "Staging telluride submodule pointer in FrostWire..."
git add telluride

echo
echo "Resulting submodule status:"
git submodule status telluride

echo
echo "Current repo status:"
git status --short
