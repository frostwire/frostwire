#!/bin/bash
# Cherry-picks a ticket agent's branch onto master, adds its changelog line and runs the gates.
# usage: [NOPICK=1] [PREFIX=all] merge.sh <branch> <ticket#> <changelog-line-file> <TestClass>...
#   NOPICK=1   skip the cherry-pick (after resolving a conflict by hand)
#   PREFIX=all use the [all] subject prefix when common/ changed
# Pushing stays a separate, deliberate step.
set -euo pipefail
BR=$1; N=$2; CL=$(cd "$(dirname "$3")" && pwd)/$(basename "$3"); shift 3
cd "${FROSTWIRE_REPO:-$HOME/workspace/frostwire}"
for c in $([ -n "${NOPICK:-}" ] || git cherry master "$BR" | awk '/^\+/{print $2}'); do
  if ! git cherry-pick "$c" >/dev/null; then
    echo "CONFLICT on $c"; git status --short | grep -v '^??'; exit 1
  fi
done
python3 -I - "$CL" <<'PY'
import sys
line = open(sys.argv[1]).read().rstrip('\n')
p = 'desktop/changelog.txt'
s = open(p).read().split('\n')
assert s[0].endswith('UNRELEASED'), 'top changelog section is not UNRELEASED'
s.insert(1, line)
open(p, 'w').write('\n'.join(s))
PY
git add desktop/changelog.txt
git commit -q -F - <<MSG
[${PREFIX:-desktop}] Changelog: frostwire/frostwire#$N

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
anthropic/claude-opus-5-5
MSG
cd desktop
TF=(); for t in "$@"; do TF+=(--tests "$t"); done
./gradlew -q compileJava spotlessCheck test "${TF[@]}" 2>&1 | tail -30
echo GRADLE_OK
