#!/usr/bin/env python3
"""Prints a Markdown coverage summary from a JaCoCo XML report.

Usage: coverage_summary.py <title> <jacoco.xml> [--depth N] [--fail-under-line PERCENT]

Writes the total line, branch and method coverage, then the same per package group (package
names cut to the depth, default 3: com.frostwire.search), largest first. CI appends the output to
$GITHUB_STEP_SUMMARY. With --fail-under-line, exits 1 when total line coverage is below PERCENT,
for builds whose plugin has no coverage gate of its own (Android).
"""
import argparse
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict


def counters(element):
    result = {}
    for counter in element.findall("counter"):
        result[counter.get("type")] = (int(counter.get("covered")), int(counter.get("missed")))
    return result


def percent(covered, missed):
    total = covered + missed
    return f"{100.0 * covered / total:.1f}%" if total else "n/a"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("title")
    parser.add_argument("path")
    parser.add_argument("--depth", type=int, default=3)
    parser.add_argument("--fail-under-line", type=float, default=None)
    args = parser.parse_args()
    title, path, depth = args.title, args.path, args.depth
    root = ET.parse(path).getroot()

    groups = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    for package in root.findall("package"):
        name = ".".join(package.get("name").split("/")[:depth])
        for kind, (covered, missed) in counters(package).items():
            groups[name][kind][0] += covered
            groups[name][kind][1] += missed

    total = counters(root)
    print(f"### {title}\n")
    print("| | Lines | Branches | Methods |")
    print("|---|---:|---:|---:|")
    print(
        f"| **Total** | **{percent(*total.get('LINE', (0, 0)))}** "
        f"| **{percent(*total.get('BRANCH', (0, 0)))}** "
        f"| **{percent(*total.get('METHOD', (0, 0)))}** |"
    )
    ordered = sorted(groups.items(), key=lambda item: -sum(item[1]["LINE"]))
    for name, kinds in ordered:
        print(
            f"| `{name}` | {percent(*kinds['LINE'])} | {percent(*kinds['BRANCH'])} "
            f"| {percent(*kinds['METHOD'])} |"
        )
    lines = total.get("LINE", (0, 0))
    print(f"\n{lines[0]:,} of {lines[0] + lines[1]:,} lines covered.\n")
    if args.fail_under_line is not None and lines[0] + lines[1]:
        line_percent = 100.0 * lines[0] / (lines[0] + lines[1])
        if line_percent < args.fail_under_line:
            print(f"Line coverage {line_percent:.1f}% is below the {args.fail_under_line}% minimum.")
            sys.exit(1)


if __name__ == "__main__":
    main()
