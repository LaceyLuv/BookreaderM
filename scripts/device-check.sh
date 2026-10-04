#!/usr/bin/env bash
set -euo pipefail
mobile_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$mobile_root"
evidence_dir="${BOOKREADER_DEVICE_EVIDENCE_DIR:-build/device-evidence}"
mkdir -p "$evidence_dir"
: > "$evidence_dir/results.txt"
run_logged() {
  local label="$1"
  shift
  printf 'COMMAND'
  printf ' %q' "$@"
  printf '\n'
  if "$@" 2>&1 | tee "$evidence_dir/$label.log"; then
    printf 'PASS %s exit=0\n' "$label" | tee -a "$evidence_dir/results.txt"
  else
    local pipeline_exit=("${PIPESTATUS[@]}")
    local command_exit="${pipeline_exit[0]}" log_exit="${pipeline_exit[1]}"
    local result=FAIL effective_exit="$command_exit"
    if (( command_exit == 78 )); then result=BLOCKED_ENV; fi
    if (( effective_exit == 0 )); then effective_exit="$log_exit"; fi
    printf '%s %s command_exit=%s log_exit=%s\n' \
      "$result" "$label" "$command_exit" "$log_exit" | tee -a "$evidence_dir/results.txt"
    return "$effective_exit"
  fi
}
if ! command -v adb >/dev/null || ! command -v python3 >/dev/null; then
  printf 'BLOCKED_ENV adb and python3 are required for actual device execution\n' | tee -a "$evidence_dir/results.txt"
  exit 78
fi
run_logged source git rev-parse HEAD
run_logged source-status git status --short
run_logged doctor ./scripts/doctor.sh
run_logged adb-version adb version
run_logged attached-devices adb devices -l
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  mapfile -t devices < <(awk '$2 == "device" {print $1}' "$evidence_dir/attached-devices.log")
  if (( ${#devices[@]} != 1 )); then
    printf 'BLOCKED_ENV exactly one authorized device or ANDROID_SERIAL is required\n' | tee -a "$evidence_dir/results.txt"
    exit 78
  fi
  export ANDROID_SERIAL="${devices[0]}"
fi
run_logged device-state adb get-state
run_logged device-build adb shell getprop ro.build.fingerprint
run_logged device-api adb shell getprop ro.build.version.sdk
run_logged device-abi adb shell getprop ro.product.cpu.abi
run_logged android-tasks ./gradlew --no-daemon --console=plain :androidApp:tasks --all
if ! awk '{print $1}' "$evidence_dir/android-tasks.log" | grep -Fx connectedDebugAndroidTest >/dev/null; then
  printf 'FAIL required task androidApp:connectedDebugAndroidTest not present\n' | tee -a "$evidence_dir/results.txt"
  exit 65
fi
# Force fresh execution; a compiled test APK or cached result is not device evidence.
# Delete only generated connected-result XML so an old successful case cannot
# satisfy the required-case check after a new incomplete instrumentation run.
run_logged clear-old-results python3 - <<'PY'
from pathlib import Path
reports = Path('androidApp/build/outputs/androidTest-results/connected')
removed = 0
for report in reports.rglob('TEST-*.xml'):
    report.unlink()
    removed += 1
print(f'Removed {removed} old generated connected-test XML reports')
PY
host_class=org.bookreader.mobile.reader.TxtReaderHostProcessTest
fixture_output=/sdcard/Android/media/org.bookreader.mobile/bookreader-fixture-evidence
test_arguments=("-Pandroid.testInstrumentationRunnerArguments.notClass=$host_class")
if [[ "${BOOKREADER_CAPTURE_FIXTURE_SCREENSHOTS:-false}" == true ]]; then
  run_logged fixture-output-directory adb shell mkdir -p "$fixture_output"
  # Only generated fixture screenshots are removed; no app/library data is cleared.
  run_logged clear-old-fixture-screenshots adb shell rm -f \
    "$fixture_output/m02-library.png" "$fixture_output/m03-reader.png" \
    "$fixture_output/txt-reader-generated-scroll.png" "$fixture_output/txt-reader-generated-host.png"
  test_arguments+=("-Pandroid.testInstrumentationRunnerArguments.captureFixtureScreenshots=true"
    "-Pandroid.testInstrumentationRunnerArguments.additionalTestOutputDir=$fixture_output")
fi
run_logged connected-tests ./gradlew --no-daemon --console=plain --stacktrace \
  --rerun-tasks "${test_arguments[@]}" :androidApp:connectedDebugAndroidTest
run_logged verified-test-results python3 scripts/check-test-results.py \
  android-device androidApp/build/outputs/androidTest-results/connected
# The excluded class is mandatory here: both exact methods execute around an
# external adb force-stop, independent of JUnit method ordering.
run_logged host-process-tests python3 scripts/reader-process-check.py "$evidence_dir/reader-host-process"
run_logged verified-host-results python3 scripts/check-test-results.py \
  android-host "$evidence_dir/reader-host-process"
if [[ "${BOOKREADER_CAPTURE_FIXTURE_SCREENSHOTS:-false}" == true ]]; then
  run_logged fixture-screenshots adb pull "$fixture_output" "$evidence_dir/fixture-screenshots"
  run_logged verified-fixture-screenshots python3 - "$evidence_dir/fixture-screenshots" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
for name in ('m02-library.png', 'm03-reader.png', 'txt-reader-generated-scroll.png', 'txt-reader-generated-host.png'):
    matches = list(root.rglob(name))
    if len(matches) != 1 or matches[0].read_bytes()[:8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit(f'FAIL missing or invalid generated-fixture screenshot: {name}')
    print(f'PASS actual generated-fixture screenshot {name}, bytes={matches[0].stat().st_size}')
PY
fi
printf 'NOT_RUN physical-device performance; emulator execution does not establish physical performance\n' | tee -a "$evidence_dir/results.txt"
