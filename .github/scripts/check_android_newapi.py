#!/usr/bin/env python3
"""Fails when Android lint reports a NewApi issue (frostwire/frostwire#1400).

minSdk is 26, but Android compiles all of common/, which is also written for desktop JDKs. A call
to an API newer than minSdk compiles and passes every desktop test, then throws NoSuchMethodError,
NoSuchFieldError or NoClassDefFoundError on older phones. Lint's own abortOnError would also fail
on hundreds of unrelated translation warnings, so this checks the report for NewApi alone.

Usage: check_android_newapi.py android/build/reports/lint-results-plus1Debug.xml
"""
import os
import sys
import xml.etree.ElementTree as ET

report = sys.argv[1]
issues = [i for i in ET.parse(report).getroot().iter("issue") if i.get("id") == "NewApi"]
for issue in issues:
    location = next(issue.iter("location"), None)
    if location is None:
        print(f"::error::{issue.get('message')}")
        continue
    # GitHub annotations want a repository-relative path and a separate line field.
    path = os.path.relpath(location.get("file"), os.environ.get("GITHUB_WORKSPACE", os.getcwd()))
    print(f"::error file={path},line={location.get('line')}::{issue.get('message')}")
if issues:
    print(f"{len(issues)} NewApi issue(s): each API is missing below the level lint names (minSdk is 26).")
    sys.exit(1)
print("No NewApi issues.")
