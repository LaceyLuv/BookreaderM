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
run_logged build-and-tests ./gradlew --no-daemon --console=plain --stacktrace \
  :androidApp:assembleDebug :androidApp:lintDebug \
  :androidApp:testDebugUnitTest :androidApp:assembleDebugAndroidTest :shared:jvmTest

# Schema content must be produced by Room KSP, never hand-created to pass.
if [[ ! -d shared/schemas ]] || ! find shared/schemas -name '*.json' -type f -print -quit | grep -q .; then
  printf 'FAIL Room schema export missing after successful KSP build\n' >&2
  exit 66
fi
printf 'PASS Room schema export exists\n'
printf 'NOT_RUN Android instrumentation requires connectedDebugAndroidTest on a device.\n'
