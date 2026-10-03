#!/usr/bin/env bash
set -euo pipefail
mobile_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$mobile_root"
blocked=0
pass() { printf 'PASS %s\n' "$*"; }
block() { printf 'BLOCKED_ENV %s\n' "$*" >&2; blocked=1; }

printf 'BookReader Mobile M00 environment doctor\n'
if command -v java >/dev/null 2>&1; then
  java -version
else
  block 'java is missing; install Temurin JDK 17.0.16+8.'
fi
if command -v javac >/dev/null 2>&1; then
  compiler_version="$(javac -version 2>&1)"
  printf '%s\n' "$compiler_version"
  # Java may print inherited JAVA_TOOL_OPTIONS before its version line.
  # Read the compiler line while preserving those options and diagnostics.
  compiler_release="$(printf '%s\n' "$compiler_version" | awk '$1 == "javac" { print $2 }')"
  if [[ "$compiler_release" == 17.* ]]; then
    pass 'JDK 17 compiler available'
  else
    block 'Active javac must be JDK 17; set JAVA_HOME and PATH.'
  fi
else
  block 'javac is missing; a JRE alone cannot build this project.'
fi
if [[ -x gradlew && -s gradle/wrapper/gradle-wrapper.jar ]]; then
  if printf '%s\n' '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f  gradle/wrapper/gradle-wrapper.jar' | sha256sum --check --status; then
    pass 'Authentic Gradle 8.13 wrapper JAR checksum'
  else
    block 'Wrapper checksum mismatch; restore the official wrapper.'
  fi
else
  block 'Gradle wrapper launch script/JAR missing.'
fi
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" || ! -d "$sdk_root" ]]; then
  block 'Set ANDROID_HOME to an installed Android SDK.'
else
  for component in platforms/android-36/android.jar build-tools/35.0.0/aapt2 platform-tools/adb; do
    if [[ -f "$sdk_root/$component" ]]; then
      pass "SDK component $component"
    else
      block "Missing SDK component $component"
    fi
  done
fi
if command -v adb >/dev/null 2>&1; then
  pass 'adb available; connected-device checks remain separate.'
else
  printf 'NOT_RUN Android instrumentation: adb/device unavailable.\n'
fi
printf 'NOT_RUN Dependency resolution/build: doctor is only an environment check.\n'
if (( blocked )); then
  printf 'BLOCKED_ENV Install JDK 17 and the pinned SDK; then run ci-check.\n' >&2
  exit 78
fi
