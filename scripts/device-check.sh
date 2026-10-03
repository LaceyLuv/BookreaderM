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
run_logged connected-tests ./gradlew --no-daemon --console=plain --stacktrace \
  --rerun-tasks :androidApp:connectedDebugAndroidTest
run_logged verified-test-results python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

expected = {
    ('org.bookreader.mobile.ui.BookReaderScreenTest', 'threeTabsMetadataSearchAndThemeSelectionWork'),
    ('org.bookreader.mobile.ui.BookReaderScreenTest', 'loadingEmptyAndErrorAreDistinctAndErrorHasRetry'),
    ('org.bookreader.mobile.ui.AndroidThemeStoreTest', 'selectedThemePersistsAcrossStoreInstances'),
    ('org.bookreader.mobile.ui.AndroidThemeStoreTest', 'invalidStoredThemeReportsErrorAndPreservesOriginal'),
    ('org.bookreader.mobile.ui.MainActivityLaunchTest', 'launcherStartsAtLibraryAndActivityRecreationKeepsSelectedTab'),
}
reports = sorted(Path('androidApp/build/outputs/androidTest-results/connected').rglob('TEST-*.xml'))
if not reports:
    raise SystemExit('FAIL no connected instrumentation XML reports')
passed = set()
count = 0
for report in reports:
    for case in ET.parse(report).getroot().iter('testcase'):
        count += 1
        identity = (case.attrib.get('classname', ''), case.attrib.get('name', ''))
        if any(case.find(status) is not None for status in ('failure', 'error', 'skipped')):
            raise SystemExit(f'FAIL unsuccessful or skipped instrumentation test: {identity}')
        passed.add(identity)
missing = expected - passed
if missing:
    raise SystemExit(f'FAIL mandatory M00 device tests were not executed: {sorted(missing)}')
print(f'PASS {count} actual instrumentation tests; all {len(expected)} mandatory M00 cases executed')
for classname, name in sorted(passed):
    print(f'PASS {classname}.{name}')
PY
printf 'NOT_RUN host-driven process death and physical-device performance; outside M00 device suite\n' | tee -a "$evidence_dir/results.txt"
