#!/usr/bin/env python3
"""Prints a Markdown coverage summary from a JaCoCo XML report.

Usage: coverage_summary.py <title> <jacoco.xml> [package-prefix-depth]

Writes the total line, branch and method coverage, then the same per package group (package
names cut to the given depth, default 3: com.frostwire.search), largest first. CI appends the
output to $GITHUB_STEP_SUMMARY.
"""
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
    title, path = sys.argv[1], sys.argv[2]
    depth = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    # JaCoCo reports reference a DTD; don't fetch it.
    parser = ET.XMLParser()
    root = ET.parse(path, parser=parser).getroot()

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


if __name__ == "__main__":
    main()
