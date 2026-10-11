#!/usr/bin/env bash
# A private mount namespace bounds all exhaustion to one newly-created 8 MiB tmpfs.
set -euo pipefail
mobile_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$mobile_root"
if [[ ${1:-} != --namespace ]]; then
  # Mapping this user to namespace root must not move the Gradle cache to /root.
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
  if ! unshare --user --map-root-user --mount true; then
    printf 'BLOCKED_ENV: Linux user/mount namespaces are required. exit=78\n' >&2
    exit 78
  fi
  exec unshare --user --map-root-user --mount "$0" --namespace
fi
# Refuse the internal entry point in the host mount namespace.
[[ $(readlink /proc/self/ns/mnt) != "$(readlink /proc/1/ns/mnt)" ]]
mount --make-rprivate /
fixture_root="$(mktemp -d /tmp/bookreader-enospc.XXXXXX)"
cleanup() {
  local result=$?
  trap - EXIT
  if mountpoint -q "$fixture_root"; then
    if ! umount "$fixture_root"; then result=1; fi
  fi
  if ! rmdir "$fixture_root"; then result=1; fi
  printf 'ENOSPC namespace fixture cleanup exit=%s\n' "$result"
  exit "$result"
}
trap cleanup EXIT
mount -t tmpfs -o size=8m,mode=0700 bookreader-enospc "$fixture_root"
export BOOKREADER_ENOSPC_ROOT="$fixture_root"
findmnt -n -o SOURCE,FSTYPE,SIZE --target "$fixture_root"
./gradlew -Duser.home="$HOME" --no-daemon --console=plain --stacktrace :shared:jvmEnospcTest
python3 scripts/check-test-results.py shared-enospc shared/build/test-results/jvmEnospcTest
