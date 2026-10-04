#!/usr/bin/env python3
"""Run both real instrumentation stages around an external adb force-stop."""
from pathlib import Path
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PACKAGE = "org.bookreader.mobile"
CLASS = PACKAGE + ".reader.TxtReaderHostProcessTest"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"


def verify_stage(output: str, method: str) -> None:
    statuses = []
    fields = {}
    for line in output.splitlines():
        match = re.fullmatch(r"INSTRUMENTATION_STATUS: ([^=]+)=(.*)", line.strip())
        if match:
            fields[match[1]] = match[2]
        match = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line.strip())
        if match:
            statuses.append((int(match[1]), fields))
            fields = {}
    expected = (CLASS, method)
    if len(statuses) != 2 or [code for code, _ in statuses] != [1, 0]:
        raise ValueError(f"stage did not start and pass exactly one test: {method}")
    for _, fields in statuses:
        if (fields.get("class"), fields.get("test")) != expected:
            raise ValueError(f"unexpected instrumentation identity: {fields}")
        if fields.get("numtests") != "1" or fields.get("current") != "1":
            raise ValueError(f"unexpected instrumentation count: {fields}")
    if not re.search(r"^INSTRUMENTATION_CODE: -1\s*$", output, re.MULTILINE):
        raise ValueError("instrumentation did not finish successfully")
    if not re.search(r"OK \(1 test\)", output):
        raise ValueError("JUnit did not report one successful test")


def run(evidence: Path) -> None:
    evidence.mkdir(parents=True, exist_ok=True)
    (evidence / "TEST-reader-host-process.xml").unlink(missing_ok=True)
    cases = []
    commands = []

    def adb(label, *args):
        command = ["adb", *args]
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=180)
        (evidence / f"{label}.log").write_text(result.stdout)
        commands.append({"label": label, "argv": command, "exit_code": result.returncode})
        if result.returncode:
            raise ValueError(f"adb {label} exit={result.returncode}")
        return result.stdout.strip()

    def stage(method):
        args = ["shell", "am", "instrument", "-w", "-r", "-e", "class", f"{CLASS}#{method}"]
        if os.environ.get("BOOKREADER_CAPTURE_FIXTURE_SCREENSHOTS") == "true":
            args += ["-e", "captureFixtureScreenshots", "true", "-e", "additionalTestOutputDir",
                     f"/sdcard/Android/media/{PACKAGE}/bookreader-fixture-evidence"]
        output = adb(method, *args, RUNNER)
        verify_stage(output, method)
        cases.append((CLASS, method))
        print(f"PASS {CLASS}.{method}", flush=True)

    try:
        stage("prepareDurableReader")
        # This is fixture-only expected state, written after the actual durable Room acknowledgement.
        proof = adb("committed-fixture-proof", "shell", "run-as", PACKAGE, "cat",
                    "files/txt-host-committed.properties")
        if not all(f"{key}=" in proof for key in ("bookId", "revision", "utf16Offset", "sequence", "epoch")):
            raise ValueError("prepare stage did not persist its expected fixture proof")
        # Instrumentation completion may end its target process. Launch a live app
        # first so force-stop demonstrably kills an existing process.
        adb("live-launch-before-stop", "shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
        previous_pid = adb("live-pid-before-stop", "shell", "pidof", PACKAGE)
        if not re.fullmatch(r"\d+", previous_pid):
            raise ValueError("expected exactly one live app PID before force-stop")
        adb("external-force-stop", "shell", "am", "force-stop", PACKAGE)
        # pidof exits 1 when correctly absent; query through shell's explicit test instead.
        gone = adb("pid-after-force-stop", "shell", "sh", "-c",
                   f"'if pidof {PACKAGE}; then exit 1; else echo absent; fi'")
        if gone != "absent":
            raise ValueError("app process remained after external force-stop")
        adb("cold-launch", "shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
        new_pid = adb("cold-pid", "shell", "pidof", PACKAGE)
        if not re.fullmatch(r"\d+", new_pid) or new_pid == previous_pid:
            raise ValueError("cold launcher did not create a different app process")
        stage("verifyColdLibraryContinue")
        summary = {"status": "PASS", "old_pid": previous_pid, "cold_launcher_pid": new_pid,
                   "claim": "durably acknowledged TXT anchor survives external force-stop; cold library Continue restores it",
                   "limitation": "verification instrumentation may start another target process; this does not prove uncommitted scroll durability",
                   "commands": commands, "cases": cases}
        (evidence / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        suite = ET.Element("testsuite", name=CLASS, tests="2", failures="0", errors="0", skipped="0")
        for classname, method in cases:
            ET.SubElement(suite, "testcase", classname=classname, name=method)
        ET.ElementTree(suite).write(evidence / "TEST-reader-host-process.xml", encoding="utf-8", xml_declaration=True)
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        (evidence / "summary.json").write_text(json.dumps({"status": "FAIL", "error": str(error), "commands": commands}, indent=2) + "\n")
        raise


if __name__ == "__main__":
    try:
        run(Path(sys.argv[1]))
    except (IndexError, OSError, ValueError, subprocess.TimeoutExpired) as error:
        print(f"FAIL host process check: {error}", file=sys.stderr)
        sys.exit(65)
