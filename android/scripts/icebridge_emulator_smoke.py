#!/usr/bin/env python3
"""
Headless IceBridge / LocalIndex smoke on an Android emulator.

Reuses frostwire_launcher.py (build, AVD boot, install, launch) then asserts
logcat + on-device files for the distributed stack.

Usage (from android/ or repo root):
  python3 scripts/icebridge_emulator_smoke.py
  python3 scripts/icebridge_emulator_smoke.py --avd Android_15_-_Pixel_9a
  python3 scripts/icebridge_emulator_smoke.py --skip-build --serial emulator-5554

Exit codes:
  0 = all required checks passed
  1 = build/install/launch failure
  2 = device/AVD failure
  3 = smoke assertions failed
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

# Import launcher helpers (same directory parent).
ANDROID_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ANDROID_DIR))

import frostwire_launcher as fw  # noqa: E402

PACKAGE = fw.FW_PACKAGE

# Required log patterns after cold launch (any one of each group is enough).
REQUIRED_PATTERNS = {
    "identity": [
        re.compile(r"AndroidRelayStack: identity loaded", re.I),
        re.compile(r"preloadIdentityFromDisk: nodeId=", re.I),
        re.compile(r"IdentityKeys: Loading identity", re.I),
        re.compile(r"IdentityKeys: Generated new identity", re.I),
        re.compile(r"PoW identity mined:", re.I),
        re.compile(r"Identity installed at", re.I),
    ],
    "icebridge_server": [
        re.compile(r"AndroidRelayStack: IceBridgeServer started", re.I),
        re.compile(r"IceBridge started: identity=", re.I),
    ],
    "stack_wired": [
        re.compile(r"LOCAL and DISTRIBUTED search engines wired", re.I),
        re.compile(r"AndroidRelayStack: started successfully", re.I),
        re.compile(r"EngineForegroundService::startRelayStack: AndroidRelayStack started", re.I),
        re.compile(r"ensureRelayStack: started", re.I),
    ],
}

# Must NOT appear for a healthy Android embed (dual-bind regression).
FORBIDDEN_PATTERNS = [
    re.compile(
        r"Failed to start IncomingRelayServer[\s\S]{0,200}EADDRINUSE|bind failed: EADDRINUSE",
        re.I,
    ),
    re.compile(r"AndroidKeyStoreKeyFactorySpi", re.I),
    re.compile(r"To generate a key pair in Android Keystore", re.I),
]


def adb(serial: str, *args: str, check: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["adb", "-s", serial, *args],
        capture_output=True,
        text=True,
        check=check,
    )


def clear_logcat(serial: str) -> None:
    adb(serial, "logcat", "-c")


def dump_logcat(serial: str) -> str:
    r = adb(serial, "logcat", "-d", "-v", "brief")
    return r.stdout or ""


def wait_for_patterns(
    serial: str,
    required: dict[str, list[re.Pattern]],
    timeout_sec: int = 120,
    poll_sec: float = 2.0,
) -> tuple[dict[str, bool], str]:
    """Poll logcat until all required groups match or timeout."""
    matched = {k: False for k in required}
    log = ""
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        log = dump_logcat(serial)
        for name, patterns in required.items():
            if matched[name]:
                continue
            for p in patterns:
                if p.search(log):
                    matched[name] = True
                    print(f"  [ok] {name}")
                    break
        if all(matched.values()):
            break
        time.sleep(poll_sec)
    return matched, log


def check_forbidden(log: str) -> list[str]:
    hits = []
    for p in FORBIDDEN_PATTERNS:
        if p.search(log):
            hits.append(p.pattern)
    return hits


def check_identity_file(serial: str) -> bool:
    # identity.dat under app-private files/libtorrent/
    r = adb(
        serial,
        "shell",
        f"run-as {PACKAGE} sh -c 'ls -la files/libtorrent/identity.dat 2>/dev/null'",
    )
    out = (r.stdout or "") + (r.stderr or "")
    if "identity.dat" in out and "No such file" not in out:
        # Prefer non-zero size
        m = re.search(r"\s(\d+)\s+.*identity\.dat", out)
        if m and int(m.group(1)) > 0:
            print(f"  [ok] identity.dat present ({m.group(1)} bytes)")
            return True
        print(f"  [warn] identity.dat listing: {out.strip()}")
        return "identity.dat" in out
    print(f"  [fail] identity.dat missing: {out.strip()}")
    return False


def check_index_db(serial: str) -> None:
    r = adb(
        serial,
        "shell",
        f"run-as {PACKAGE} sh -c 'ls -la databases/frostwire-shared-torrents.db 2>/dev/null'",
    )
    out = (r.stdout or "").strip()
    if "frostwire-shared-torrents.db" in out:
        print(f"  [ok] LocalIndex DB present: {out.splitlines()[-1] if out else out}")
    else:
        print("  [warn] LocalIndex DB not found yet (ok on first boot before stack)")


def parse_args():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--avd", default=None, help="AVD name if no emulator is running")
    p.add_argument("--serial", default=None, help="adb serial")
    p.add_argument("--skip-build", action="store_true")
    p.add_argument("--wipe-data", action="store_true")
    p.add_argument("--timeout", type=int, default=150, help="Seconds to wait for log patterns")
    p.add_argument("--keep-running", action="store_true", help="Do not kill emulator on exit")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    print("=== FrostWire IceBridge emulator smoke ===")
    try:
        serial = fw.headless_run(
            avd=args.avd,
            serial=args.serial,
            skip_build=args.skip_build,
            no_launch=False,
            wipe_data=args.wipe_data,
        )
    except SystemExit as e:
        return int(e.code) if e.code is not None else 1

    print(f"Target: {serial}")
    print("Clearing logcat and restarting app for a clean smoke window…")
    clear_logcat(serial)
    adb(serial, "shell", "am", "force-stop", PACKAGE)
    time.sleep(1)
    fw.launch_app(serial)

    print(f"Waiting up to {args.timeout}s for stack log patterns…")
    matched, log = wait_for_patterns(serial, REQUIRED_PATTERNS, timeout_sec=args.timeout)

    print("On-device files:")
    # Give first-run PoW a bit more time if identity still mining
    identity_ok = check_identity_file(serial)
    if not identity_ok:
        print("  (retry identity after 30s — first-run PoW may still be mining)")
        time.sleep(30)
        identity_ok = check_identity_file(serial)
        log = dump_logcat(serial)
        for name, patterns in REQUIRED_PATTERNS.items():
            if matched.get(name):
                continue
            for p in patterns:
                if p.search(log):
                    matched[name] = True
                    print(f"  [ok late] {name}")
                    break
    check_index_db(serial)

    forbidden = check_forbidden(log)
    if forbidden:
        print("  [fail] forbidden patterns:")
        for f in forbidden:
            print(f"    - {f}")

    print("\n=== Results ===")
    failed = []
    for name, ok in matched.items():
        status = "PASS" if ok else "FAIL"
        print(f"  {status}  log:{name}")
        if not ok:
            failed.append(f"log:{name}")
    if identity_ok:
        print("  PASS  identity.dat")
    else:
        print("  FAIL  identity.dat")
        failed.append("identity.dat")
    if forbidden:
        print("  FAIL  no-EADDRINUSE")
        failed.append("EADDRINUSE")
    else:
        print("  PASS  no-EADDRINUSE")

    if failed:
        print(f"\nSmoke FAILED: {', '.join(failed)}")
        # Dump last relevant lines for debugging
        print("\n--- logcat excerpt (IceBridge / Relay / Identity) ---")
        for line in log.splitlines():
            if re.search(r"IceBridge|AndroidRelay|Identity|EADDRINUSE|IncomingRelay", line, re.I):
                print(line)
        return 3

    print("\nSmoke PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
