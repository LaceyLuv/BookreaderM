#!/usr/bin/env python3
"""Require real successful XML cases; compilation and an empty suite are not evidence."""
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET


def verify(suite: str, report_root: Path, manifest: dict) -> tuple[int, set]:
    required = manifest[suite]
    if not isinstance(required, list) or not required or any(
        not isinstance(case, list) or len(case) != 2 or
        not all(isinstance(part, str) and part for part in case)
        for case in required
    ):
        raise ValueError(f"invalid or empty mandatory-case list for {suite}")
    expected = {tuple(case) for case in required}
    if len(expected) != len(required):
        raise ValueError(f"duplicate mandatory-case list entry for {suite}")
    reports = sorted(report_root.rglob("TEST-*.xml"))
    if not reports:
        raise ValueError(f"no {suite} XML reports in {report_root}")
    passed = set()
    count = 0
    for report in reports:
        root = ET.parse(report).getroot()
        cases = list(root.iter("testcase"))
        if any(int(root.attrib.get(key, 0)) for key in ("failures", "errors", "skipped")):
            raise ValueError(f"unsuccessful suite summary in {report}")
        if "tests" in root.attrib and int(root.attrib["tests"]) != len(cases):
            raise ValueError(f"test count does not match cases in {report}")
        for case in cases:
            identity = (
                case.attrib.get("classname", ""),
                re.sub(r"\[jvm\]$", "", case.attrib.get("name", "")),
            )
            if not all(identity):
                raise ValueError(f"test identity missing in {report}")
            if any(case.find(status) is not None for status in ("failure", "error", "skipped")):
                raise ValueError(f"unsuccessful or skipped {suite} test: {identity}")
            if identity in passed:
                raise ValueError(f"duplicate {suite} test result: {identity}")
            passed.add(identity)
            count += 1
    missing = expected - passed
    if missing:
        raise ValueError(f"mandatory {suite} cases were not executed: {sorted(missing)}")
    return count, passed


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: check-test-results.py SUITE REPORT_ROOT", file=sys.stderr)
        return 64
    suite, folder = sys.argv[1:]
    try:
        manifest = json.loads(Path(__file__).with_name("required-tests.json").read_text())
        count, passed = verify(suite, Path(folder), manifest)
    except (KeyError, ValueError, OSError, ET.ParseError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 65
    print(f"PASS {count} actual {suite} tests; all {len(manifest[suite])} mandatory cases executed")
    for classname, name in sorted(passed):
        print(f"PASS {classname}.{name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
