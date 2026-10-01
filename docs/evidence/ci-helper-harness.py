"""Reproduce M00 shell logger checks; this is not an application/DB test."""
import json
import pathlib
import subprocess
import tempfile
import tomllib

root = pathlib.Path(__file__).resolve().parents[2]
source = (root / "scripts/ci-check.sh").read_text()
function = source[source.index("run_logged() {"):source.index("\n: >")]
results = []
with tempfile.TemporaryDirectory(prefix="bookreader-ci-helper-") as temporary:
    cases = [
        ("success", "printf data; exit 0", 0),
        ("failure", "printf data; exit 7", 7),
        ("blocked", "printf data; exit 78", 78),
        ("log-failure", "printf data; exit 0", 1),
    ]
    for label, command, expected in cases:
        folder = pathlib.Path(temporary) / label
        folder.mkdir()
        if label == "log-failure":
            (folder / "case.log").symlink_to("/dev/full")
        script = (
            "set -euo pipefail\nevidence_dir=" + str(folder) + "\n"
            + function
            + '\nif run_logged case bash -c "' + command
            + '"; then actual=0; else actual=$?; fi\n'
            + 'printf "ACTUAL_EXIT=%s\\n" "$actual"\nexit "$actual"\n'
        )
        run = subprocess.run(["bash", "-c", script], capture_output=True, text=True)
        results.append({
            "case": label, "expected": expected, "actual": run.returncode,
            "stdout": run.stdout, "stderr": run.stderr,
        })
        if run.returncode != expected:
            raise AssertionError((label, expected, run))
tomllib.loads((root / "gradle/libs.versions.toml").read_text())
output = (
    "COMMAND python3 docs/evidence/ci-helper-harness.py\n"
    + json.dumps(results, indent=2)
    + "\nPASS four shell logger cases; version catalog TOML parse\nEXIT 0\n"
)
(root / "docs/evidence/ci-helper-harness.log").write_text(output)
print(output)
