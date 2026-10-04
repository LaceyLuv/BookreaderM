#!/usr/bin/env bash
set -euo pipefail
mobile_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$mobile_root"
evidence_dir="${BOOKREADER_EVIDENCE_DIR:-build/ci-evidence}"
mkdir -p "$evidence_dir"
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
: > "$evidence_dir/results.txt"
run_logged doctor ./scripts/doctor.sh
run_logged gradle-version ./gradlew --version
run_logged projects ./gradlew --no-daemon --console=plain projects
run_logged android-tasks ./gradlew --no-daemon --console=plain :androidApp:tasks --all
run_logged shared-tasks ./gradlew --no-daemon --console=plain :shared:tasks --all

# Discover from the selected plugins on each run. Missing tasks are a failure,
# never silently replaced with a no-op or skipped.
require_task() {
  local module="$1" task="$2" listing="$3"
  if awk '{print $1}' "$listing" | grep -Fx "$task" >/dev/null; then
    printf 'PASS discovered %s:%s\n' "$module" "$task"
  else
    printf 'FAIL required task %s:%s not present\n' "$module" "$task" >&2
    exit 65
  fi
}
for task in assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest; do
  require_task androidApp "$task" "$evidence_dir/android-tasks.log"
done
require_task shared jvmTest "$evidence_dir/shared-tasks.log"
run_logged clear-old-test-results python3 - <<'PY'
from pathlib import Path

# Only generated XML is removed. Missing declared test outputs make Gradle
# execute tests again; stale reports must not satisfy mandatory-case checks.
removed = 0
for folder in ('androidApp/build/test-results/testDebugUnitTest', 'shared/build/test-results/jvmTest'):
    for report in Path(folder).glob('TEST-*.xml'):
        report.unlink()
        removed += 1
print(f'Removed {removed} old generated unit/shared XML reports')
PY
run_logged build-and-tests ./gradlew --no-daemon --console=plain --stacktrace \
  :androidApp:assembleDebug :androidApp:lintDebug \
  :androidApp:testDebugUnitTest :androidApp:assembleDebugAndroidTest :shared:jvmTest
run_logged android-unit-results python3 scripts/check-test-results.py \
  android-unit androidApp/build/test-results/testDebugUnitTest
run_logged shared-test-results python3 scripts/check-test-results.py \
  shared-jvm shared/build/test-results/jvmTest

# Schema content must be produced by Room KSP, never hand-created to pass.
run_logged room-schema python3 - <<'PY'
from pathlib import Path
import json
import re

source = Path('shared/src/commonMain/kotlin/org/bookreader/mobile/database/BookReaderDatabase.kt').read_text()
version_match = re.search(r'\bversion\s*=\s*(\d+)\b', source)
if version_match is None:
    raise SystemExit('FAIL explicit current Room database version was not found')
version = int(version_match.group(1))
schema = Path(f'shared/schemas/org.bookreader.mobile.database.BookReaderDatabase/{version}.json')
if not schema.is_file():
    raise SystemExit(f'FAIL current Room schema export is missing: {schema}')
database = json.loads(schema.read_text())['database']
if database['version'] != version or not database['identityHash'] or not database['entities']:
    raise SystemExit(f'FAIL exported Room schema does not describe database version {version}')
print(f'PASS current Room schema version {version}: {schema}')
PY
printf 'NOT_RUN Android instrumentation requires connectedDebugAndroidTest on a device.\n'
