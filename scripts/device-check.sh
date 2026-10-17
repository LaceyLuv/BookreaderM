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
host_class=org.bookreader.mobile.reader.TxtReaderHostProcessTest,org.bookreader.mobile.reader.EpubReaderHostProcessTest,org.bookreader.mobile.reader.ComicReaderHostProcessTest
fixture_output=/sdcard/Android/media/org.bookreader.mobile/bookreader-fixture-evidence
test_arguments=("-Pandroid.testInstrumentationRunnerArguments.notClass=$host_class")
if [[ "${BOOKREADER_CAPTURE_FIXTURE_SCREENSHOTS:-false}" == true ]]; then
  run_logged fixture-output-directory adb shell mkdir -p "$fixture_output"
  # Only generated fixture screenshots are removed; no app/library data is cleared.
  run_logged clear-old-fixture-screenshots adb shell rm -f \
    "$fixture_output/m02-library.png" "$fixture_output/m03-reader.png" \
    "$fixture_output/txt-reader-generated-scroll.png" "$fixture_output/txt-reader-generated-host.png" \
    "$fixture_output/txt-reader-generated-pages.png" "$fixture_output/txt-reader-generated-page-reflow.png"
  test_arguments+=("-Pandroid.testInstrumentationRunnerArguments.captureFixtureScreenshots=true"
    "-Pandroid.testInstrumentationRunnerArguments.additionalTestOutputDir=$fixture_output")
fi
connected_exit=0
run_logged connected-tests ./gradlew --no-daemon --console=plain --stacktrace \
  --rerun-tasks -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  "${test_arguments[@]}" :androidApp:connectedDebugAndroidTest || connected_exit=$?
# Preserve the generated measurement on assertion failure, without masking the test exit.
report_exit=0
run_logged continuous-progress-report adb shell run-as org.bookreader.mobile cat files/txt-continuous-report.json || report_exit=$?
full_report_exit=0
run_logged sqlite-full-report adb shell run-as org.bookreader.mobile cat files/txt-full-report.json || full_report_exit=$?
read_report_exit=0
for fixture in reader-query-error reader-locator-error reader-switch reader-reopen reader-reflow-stop reader-delete-decoding; do
  run_logged "$fixture-report" adb shell run-as org.bookreader.mobile cat "files/$fixture-report.json" || read_report_exit=$?
done
if (( connected_exit != 0 )); then exit "$connected_exit"; fi
if (( report_exit != 0 )); then exit "$report_exit"; fi
if (( full_report_exit != 0 )); then exit "$full_report_exit"; fi
if (( read_report_exit != 0 )); then exit "$read_report_exit"; fi
run_logged verified-test-results python3 scripts/check-test-results.py \
  android-device androidApp/build/outputs/androidTest-results/connected
run_logged verified-continuous-progress python3 - "$evidence_dir/continuous-progress-report.log" <<'PY'
from pathlib import Path
import json
import sys

report = json.loads(Path(sys.argv[1]).read_text())
assert report['status'] == 'PASS' and report['fixture_id'] == 'reader-continuous'
assert report['movement_duration_ms'] >= 60_000
assert report['max_input_gap_ms'] <= 1_000
assert len(report['anchors']) >= 120 and len(report['commits']) >= 12
assert report['max_observed_commit_gap_ms'] <= 5_000
assert all(a['sequence'] < b['sequence'] for a, b in zip(report['commits'], report['commits'][1:]))
print('PASS 60s actual touch with periodic Room observations; SQL write-ack/physical latency not measured')
print(json.dumps({key: report[key] for key in ('movement_duration_ms', 'max_observed_commit_gap_ms',
    'gesture_end_to_settled_observation_ms', 'final_sequence')}, sort_keys=True))
PY
# Verify the native capacity failure and actual UI recovery report independently of JUnit XML.
run_logged verified-sqlite-full python3 - "$evidence_dir/sqlite-full-report.log" <<'PY'
from pathlib import Path
import json
import sys
report = json.loads(Path(sys.argv[1]).read_text())
assert report['status'] == 'PASS' and report['fixture_id'] == 'reader-full'
assert report['native_probe_code'] == 13 and 0 < report['growth_bytes'] <= 1_048_576
assert all(report[key] is True for key in ('save_error_visible', 'records_preserved_on_failure',
    'managed_files_preserved', 'retry_cleared_error', 'other_book_preserved_after_retry',
    'fixture_limit_and_schema_restored'))
assert report['initial_offset'] != report['pending_offset'] == report['retry_offset']
assert report['retry_sequence'] > report['initial_sequence']
print('PASS native SQLite page-limit FULL, actual Reader error UI, preserved records and automatic retry; OS ENOSPC not measured')
PY
# Native query failure and malformed locator are separate real Reader paths.
run_logged verified-progress-read-errors python3 - "$evidence_dir" <<'PY_READ'
from pathlib import Path
import json
import sys
for fixture in ('reader-query-error', 'reader-locator-error'):
    report = json.loads((Path(sys.argv[1]) / f'{fixture}-report.log').read_text())
    assert report['status'] == 'PASS' and report['fixture_id'] == fixture
    assert report['failed_retries'] == 2
    assert report['initial_offset'] == report['restored_offset'] > 0
    assert report['restored_epoch'] == report['new_epoch'] == report['initial_epoch'] + 1
    assert report['new_offset'] != report['initial_offset']
    assert all(report[key] is True for key in ('error_ui_visible', 'all_records_preserved_on_error',
        'managed_files_preserved', 'retry_cleared_error', 'progress_unchanged_after_restore',
        'other_book_preserved_after_retry', 'save_after_retry', 'fixture_restored', 'bookmarks_preserved'))
    if fixture == 'reader-query-error':
        assert report['native_probe_code'] == 1 and report['native_probe_type'] == 'SQLException'
    print(f'PASS {fixture}: preserved records through failed retries, measured restoration and new-session save')
PY_READ
run_logged verified-reader-ui-races python3 - "$evidence_dir" <<'PY_RACES'
from pathlib import Path
import json
import sys
root = Path(sys.argv[1])
switch = json.loads((root / 'reader-switch-report.log').read_text())
reopen = json.loads((root / 'reader-reopen-report.log').read_text())
assert switch['status'] == reopen['status'] == 'PASS'
assert len(switch['events']) == 6 and len(switch['fixture_sha256']) == 2
for index, event in enumerate(switch['events']):
    assert event['iteration'] == index and event['close_waited_for_writer'] is True
    assert event['epoch'] > 1 and event['sequence'] > 0
    if event['backward']:
        assert event['committed_offset'] < event['previous_offset']
    else:
        assert event['committed_offset'] > event['previous_offset']
assert sum(e['backward'] for e in switch['events']) == 2
assert reopen['cancelled_openings'] == 5 and reopen['original_offset'] > 0
reflow = json.loads((root / 'reader-reflow-stop-report.log').read_text())
assert reflow['status'] == 'PASS'
assert 0 < reflow['pending_offset'] < reflow['original_offset']
assert reflow['initial_orientation'] != reflow['changed_orientation']
assert reflow['final_generation'] > reflow['initial_generation']
deletion = json.loads((root / 'reader-delete-decoding-report.log').read_text())
assert deletion['status'] == 'PASS' and deletion['fixture_bytes'] == 32 * 1024 * 1024
assert 0 < deletion['partial_utf16_length'] < deletion['fixture_bytes']
assert all(deletion[k] is True for k in ('book_and_progress_absent', 'managed_copy_absent',
    'partial_cache_removed', 'other_book_preserved'))
print('PASS actual A/B UI close flush barrier and same-book cancellation with native writer contention')
PY_RACES
# The excluded class is mandatory here: both exact methods execute around an
# external adb force-stop, independent of JUnit method ordering.
run_logged host-process-tests python3 scripts/reader-process-check.py "$evidence_dir/reader-host-process"
run_logged verified-host-results python3 scripts/check-test-results.py \
  android-host "$evidence_dir/reader-host-process"
for archive_format in epub comic; do
  run_logged "$archive_format-host-process-tests" python3 scripts/reader-process-check.py "$evidence_dir/$archive_format-host-process" "$archive_format"
  run_logged "verified-$archive_format-host-results" python3 scripts/check-test-results.py "android-host-$archive_format" "$evidence_dir/$archive_format-host-process"
done
if [[ "${BOOKREADER_CAPTURE_FIXTURE_SCREENSHOTS:-false}" == true ]]; then
  run_logged fixture-screenshots adb pull "$fixture_output" "$evidence_dir/fixture-screenshots"
  run_logged verified-fixture-screenshots python3 - "$evidence_dir/fixture-screenshots" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
for name in ('m02-library.png', 'm03-reader.png', 'txt-reader-generated-scroll.png', 'txt-reader-generated-host.png',
             'txt-reader-generated-pages.png', 'txt-reader-generated-page-reflow.png'):
    matches = list(root.rglob(name))
    if len(matches) != 1 or matches[0].read_bytes()[:8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit(f'FAIL missing or invalid generated-fixture screenshot: {name}')
    print(f'PASS actual generated-fixture screenshot {name}, bytes={matches[0].stat().st_size}')
PY
fi
printf 'NOT_RUN physical-device performance; emulator execution does not establish physical performance\n' | tee -a "$evidence_dir/results.txt"
