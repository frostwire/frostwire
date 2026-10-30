#!/usr/bin/env python3
# Created by Angel Leon (@gubatron)
# Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
# Licensed under the GNU General Public License v3.
"""Read and triage FrostWire Android's Crashlytics issues through the Firebase Crashlytics API.

Auth: the caller's gcloud login (`gcloud auth print-access-token`); the account needs access to
the Firebase project. Project and app id default to android/google-services.json.

Read:
  crashlytics.py versions [--days N]
  crashlytics.py top [--version "3.2.2 (9090778)"] [--hours N | --days N] [--state OPEN]
                     [--types FATAL,ANR,NON_FATAL] [--limit N]
  crashlytics.py issue ISSUE_ID
  crashlytics.py events ISSUE_ID [--version V] [--count N] [--threads all] [--hours N | --days N]
Write (the pipeline's "close once the ticket exists" step):
  crashlytics.py note ISSUE_ID "GitHub frostwire/frostwire#1370"
  crashlytics.py set-state ISSUE_ID... CLOSED|OPEN|MUTED

Prints only what a public ticket may carry: stacks, device model, OS, app version, process
state, memory. Never user ids, installation ids, custom keys, logs or breadcrumbs.
"""

import argparse
import datetime
import json
import os
import subprocess
import sys
import urllib.parse

API = "https://firebasecrashlytics.googleapis.com/v1alpha"


def repo_root():
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.abspath(os.path.join(here, "..", "..", ".."))


def default_app():
    path = os.path.join(repo_root(), "android", "google-services.json")
    with open(path) as f:
        data = json.load(f)
    project = data["project_info"]["project_id"]
    for client in data["client"]:
        if client["client_info"]["android_client_info"]["package_name"] == "com.frostwire.android":
            return project, client["client_info"]["mobilesdk_app_id"]
    raise SystemExit("com.frostwire.android not found in " + path)


class Crashlytics:
    def __init__(self, project, app):
        self.project = project
        self.base = f"{API}/projects/{project}/apps/{app}"
        self.token = subprocess.run(
            ["gcloud", "auth", "print-access-token"], capture_output=True, text=True, check=True
        ).stdout.strip()

    def call(self, method, path, params=None, body=None):
        # curl, not urllib: python.org builds on macOS ship without CA certificates.
        url = self.base + path
        if params:
            url += "?" + urllib.parse.urlencode(params, doseq=True)
        cmd = ["curl", "-sS", "-X", method, url,
               "-H", "Authorization: Bearer " + self.token,
               "-H", "x-goog-user-project: " + self.project]
        if body is not None:
            cmd += ["-H", "Content-Type: application/json", "--data", json.dumps(body)]
        out = subprocess.run(cmd, capture_output=True, text=True)
        if out.returncode != 0:
            raise SystemExit(f"{method} {path}: curl failed: {out.stderr.strip()}")
        data = json.loads(out.stdout or "{}")
        if "error" in data:
            raise SystemExit(f'{method} {path}: HTTP {data["error"].get("code")}: {data["error"].get("message")}')
        return data


def interval(args):
    # The API rejects a start time without an end time.
    end = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
    hours = args.hours if args.hours else 24 * (args.days or 7)
    start = end - datetime.timedelta(hours=hours)
    fmt = "%Y-%m-%dT%H:%M:%SZ"
    return {"filter.interval.startTime": start.strftime(fmt), "filter.interval.endTime": end.strftime(fmt)}


def metric(group):
    return (group.get("metrics") or [{}])[0]


def cmd_versions(c, args):
    report = c.call("GET", "/reports/topVersions", {"pageSize": 20, **interval(args)})
    for g in report.get("groups", []):
        m = metric(g)
        print(f'{g.get("version", {}).get("displayName", "?"):24} events={m.get("eventsCount", 0):>6} '
              f'users={m.get("impactedUsersCount", 0):>5}')


def cmd_top(c, args):
    params = {"pageSize": args.limit, **interval(args)}
    if args.version:
        params["filter.version.displayNames"] = args.version
    if args.state:
        params["filter.issue.states"] = args.state
    if args.types:
        params["filter.issue.errorTypes"] = args.types.split(",")
    report = c.call("GET", "/reports/topIssues", params)
    groups = report.get("groups", [])
    print(f"{len(groups)} issues")
    for g in groups:
        i, m = g.get("issue", {}), metric(g)
        print(f'{i.get("errorType", "?"):9} events={m.get("eventsCount", 0):>5} users={m.get("impactedUsersCount", 0):>4} '
              f'{i.get("id", "")}  {i.get("title", "")} | {i.get("subtitle", "")}')
        print(f'          first {i.get("firstSeenVersion", "?")}  last {i.get("lastSeenVersion", "?")}  '
              f'signals {[s.get("signal") for s in i.get("signals", [])]}')


def cmd_issue(c, args):
    i = c.call("GET", f"/issues/{args.issue}")
    for key in ("id", "errorType", "state", "title", "subtitle", "firstSeenVersion", "lastSeenVersion",
                "firstSeenTime", "lastSeenTime", "notesCount", "uri"):
        print(f"{key}: {i.get(key)}")
    for v in i.get("variants", [])[:12]:
        print(f'variant {v.get("id")}  {v.get("uri", "")}')


def print_frames(frames, limit):
    for f in (frames or [])[:limit]:
        loc = f'{f.get("file")}:{f.get("line")}' if f.get("file") else (f.get("library") or "native")
        print(f'      at {f.get("symbol")} ({loc}){"  <- blamed" if f.get("blamed") else ""}')


def cmd_events(c, args):
    params = {"filter.issue.id": args.issue, "pageSize": args.count, **interval(args)}
    if args.version:
        params["filter.version.displayNames"] = args.version
    for e in c.call("GET", "/events", params).get("events", [])[: args.count]:
        dev, osys, ver = e.get("device", {}), e.get("operatingSystem", {}), e.get("version", {})
        issue = e.get("issue") or {}
        print(f'== {e.get("issueTitle") or issue.get("title")} | {e.get("issueSubtitle") or issue.get("subtitle")}')
        print(f'   {e.get("eventTime")}  app {ver.get("displayName")}  {dev.get("displayName")}  '
              f'{osys.get("displayName")}  process {e.get("processState")}')
        mem = e.get("memory") or {}
        if mem:
            print(f'   memory used {mem.get("used")} free {mem.get("free")}')
        for ex in e.get("exceptions") or []:
            print(f'   EXCEPTION {ex.get("type")}: {ex.get("exceptionMessage") or ""}')
            print_frames(ex.get("frames"), 40)
        for t in e.get("threads") or []:
            interesting = t.get("crashed") or t.get("blamed") or t.get("name") == "main"
            if args.threads == "all" or interesting:
                print(f'   THREAD {t.get("name")} {t.get("state") or ""}'
                      f'{" crashed" if t.get("crashed") else ""}{" blamed" if t.get("blamed") else ""}')
                print_frames(t.get("frames"), 40)


def cmd_note(c, args):
    note = c.call("POST", f"/issues/{args.issue}/notes", body={"body": args.text})
    print(f'note {note.get("name", "?")} by {note.get("author", "?")}')


def cmd_set_state(c, args):
    for issue in args.issues:
        i = c.call("PATCH", f"/issues/{issue}", {"updateMask": "state"}, {"state": args.state})
        print(f'{issue}: {i.get("state")}')


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--project")
    p.add_argument("--app")
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("versions", "top"):
        s = sub.add_parser(name)
        s.add_argument("--hours", type=int)
        s.add_argument("--days", type=int)
        if name == "top":
            s.add_argument("--version")
            s.add_argument("--state", default="OPEN")
            s.add_argument("--types")
            s.add_argument("--limit", type=int, default=50)
    sub.add_parser("issue").add_argument("issue")
    s = sub.add_parser("events")
    s.add_argument("issue")
    s.add_argument("--version")
    s.add_argument("--count", type=int, default=1)
    s.add_argument("--threads", choices=["interesting", "all"], default="interesting")
    s.add_argument("--hours", type=int)
    s.add_argument("--days", type=int)
    s = sub.add_parser("note")
    s.add_argument("issue")
    s.add_argument("text")
    s = sub.add_parser("set-state")
    s.add_argument("issues", nargs="+")
    s.add_argument("state", choices=["OPEN", "CLOSED", "MUTED"])
    args = p.parse_args()
    project, app = default_app()
    c = Crashlytics(args.project or project, args.app or app)
    {"versions": cmd_versions, "top": cmd_top, "issue": cmd_issue, "events": cmd_events,
     "note": cmd_note, "set-state": cmd_set_state}[args.cmd](c, args)


if __name__ == "__main__":
    sys.exit(main())
