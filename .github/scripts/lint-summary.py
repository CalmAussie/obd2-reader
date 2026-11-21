#!/usr/bin/env python3
"""Puts the Android Lint results on the run's summary page.

Usage: lint-summary.py <lint-results.xml>

Each error (and fatal) becomes an annotation on its file and line, so a pull request shows it
next to the code. Warnings are counted by check, with the full report kept as a download.
Exits 0 always: whether the build fails is up to Gradle (lint stops the build on an error).
"""
import collections
import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.environ.get("GITHUB_WORKSPACE", os.getcwd()).rstrip("/") + "/"


def relative(path):
    return path[len(ROOT):] if path.startswith(ROOT) else path


def escape(text):
    # Workflow commands end at a newline, and % starts an escape.
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main():
    if len(sys.argv) != 2 or not os.path.exists(sys.argv[1]):
        print("No lint report was written.")
        return
    issues = ET.parse(sys.argv[1]).getroot().findall("issue")
    errors, warnings = [], collections.Counter()
    for issue in issues:
        severity = issue.get("severity", "")
        if severity in ("Error", "Fatal"):
            errors.append(issue)
        else:
            warnings[issue.get("id", "?")] += 1

    def place(issue):
        loc = issue.find("location")
        if loc is None:
            return "", ""
        return relative(loc.get("file", "")), loc.get("line", "")

    for issue in errors[:40]:
        path, line = place(issue)
        props = [f"file={path}"] if path else []
        props += [f"line={line}"] if line else []
        props.append(f"title=Lint {issue.get('id')}")
        print(f"::error {','.join(props)}::{escape(issue.get('message', ''))}")

    lines = [f"### Android Lint: {len(errors)} error(s), {sum(warnings.values())} warning(s)", ""]
    for issue in errors:
        path, line = place(issue)
        where = f"{path}:{line}" if line else path
        lines.append(f"- **{issue.get('id')}** {where}: {issue.get('message', '')}")
    if warnings:
        lines += ["", "Warnings by check (they don't stop the build; the full report is in the lint-report download):", ""]
        lines += [f"- {check}: {count}" for check, count in warnings.most_common()]
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    text = "\n".join(lines) + "\n"
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text)
    print(text)


if __name__ == "__main__":
    main()
