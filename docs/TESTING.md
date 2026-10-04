# M00 검증 계약

2026-10-04 갱신. 결과는 `PASS / FAIL / NOT_RUN / BLOCKED_ENV`로 구분한다.
테스트 파일의 존재, 환경 검사 성공, Activity 재생성은 각각 실제 테스트 통과나 프로세스 종료 검증을 대신하지 않는다.

## 현재 실행 증거

Source `3f04b8ba53d23846a454ee744c5e1fc33bea45e3`의 전체 `ci-check`는 exit 0 (`PASS`)이다.
실제 task discovery, debug 앱/test APK assemble, lint, Android unit, shared JVM tests와
Room schema export를 확인했다. 실제 XML은 Android unit 10/shared 19 cases,
failure/error/skipped 모두 0이다. Lint XML은 informational `Hint` 20건만 기록하며
`AndroidGradlePluginVersion` 3/`GradleDependency` 9/`NewerVersionAvailable` 8건이다.
고정 pin의 upgrade 안내는 활성화된 상태로 남겼고 다른 warning/error는 fatal이다.

| 증거 | 경로 |
|---|---|
| 전체 명령별 exit/log | `docs/evidence/m00-validation/ci/results.txt`, `ci/*.log`, `ci-check.log` |
| 결과·source SHA·APK/schema hash | `docs/evidence/m00-validation/final-validation-summary.json` |
| Android unit 실행 XML | `docs/evidence/m00-validation/final-reports/androidApp/` |
| shared/실제 Room 실행 XML | `docs/evidence/m00-validation/final-reports/shared/` |
| lint XML/HTML | `docs/evidence/m00-validation/final-reports/lint-results-debug.*` |
| 최초 shared/lint 실패 이력 | `docs/evidence/m00-validation/attempt-1-shared-failure/`, `attempt-1-lint-failure/` |

동일 source `3f04b8ba`의 실제 API36 Google APIs x86_64 instrumentation 5 cases도 `PASS`다.
실행 `37164061580`의 XML은 failure/error/skipped가 없고 필수 5개 case를 모두 포함한다.
기기 identity·필수 명령 결과/발췌·원격 metadata·실제 XML/hash는
`docs/evidence/m00-validation/device-37164061580/` 및 `verified-summary.json`에 있다.
원본 전체 job log는 Git 제외 경로 `build/device-ci-artifacts/37164061580/job-111323264829.log`에
보존하고 SHA-256은 위 summary에 기록한다. 원격 run의 job 화면에서도 전체 로그를 확인할 수 있다.
원격 기본 CI `37164061596`도 success이며 같은 29-case XML과 schema를 확인했다.
10월 1일 환경 차단 및 수정 전 원격 CI 실패는 WORKLOG와 기존 로그에 보존했다.

## 로컬/CI 실행

JDK 17과 Android platform 36/build-tools 35.0.0/platform-tools, Gradle/Maven 저장소 접근이 필요하다.
아래 명령은 모바일 루트에서 실행한다.

```bash
./scripts/doctor.sh
./scripts/ci-check.sh
```

`ci-check.sh`는 `./gradlew --version`, `projects`, `:androidApp:tasks --all`, `:shared:tasks --all`을 실제 실행한다.
이 목록에 `assembleDebug / lintDebug / testDebugUnitTest / assembleDebugAndroidTest`, `jvmTest`가 있는지 확인한 다음
다음 명령을 실행한다. 2026-10-03 실제 plugin 제공 여부를 확인했다.
누락되면 실패하며 임의 no-op task로 대체하지 않는다.

```bash
./gradlew --no-daemon --console=plain --stacktrace \
  :androidApp:assembleDebug :androidApp:lintDebug \
  :androidApp:testDebugUnitTest :androidApp:assembleDebugAndroidTest :shared:jvmTest
```

`shared:jvmTest`는 common tests와 실제 file-backed Room DB smoke의 실행 경로다.
`assembleDebugAndroidTest`는 instrumentation APK 컴파일이며 기기 테스트 실행을 의미하지 않는다.
기기 연결 후 실제 `:androidApp:tasks --all`에서 `connectedDebugAndroidTest`를 확인한 뒤 실행한다.

```bash
./gradlew --no-daemon --console=plain :androidApp:connectedDebugAndroidTest
```

명령 실행 후 확인할 경로는 아래와 같다. 최종 성공 여부는 XML/log/schema 확인 결과를 따른다.

| 산출물 | 생성 경로 |
|---|---|
| Debug 앱 APK | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| 같은 source의 실제 GitHub CI 앱 APK | `build/deliverables/m00-3f04b8b/androidApp-debug.apk` |
| Android unit XML/HTML | `androidApp/build/test-results/testDebugUnitTest/`, `androidApp/build/reports/tests/testDebugUnitTest/` |
| shared XML/HTML | `shared/build/test-results/jvmTest/`, `shared/build/reports/tests/jvmTest/` |
| lint | `androidApp/build/reports/lint-results-debug.html` |
| 기기 UI 결과 | `androidApp/build/outputs/androidTest-results/`, `androidApp/build/reports/androidTests/` |
| Room processor export | `shared/schemas/` |
| 다음 ci-check 명령 증거 | `build/ci-evidence/` |

현재 시도의 원본 실행 로그는 `docs/evidence/`에 있으며 이후 ci-check 로그에 덮어쓰이지 않는다.
GitHub 게시를 위해 기존 환경·원문 보존·정적 검사 로그는 `docs/evidence/verification/`,
기존 ci-check 출력 snapshot은 `docs/evidence/ci-evidence/`에 복사해 commit에 포함한다.
`build/ci-evidence/`는 앞으로 실행할 ci-check의 실제 출력 경로다.
GitHub workflow는 필수 checks 및 instrumentation APK compile을 실행하도록 작성했다.
10월 1일 기본 원격 CI run `36805008404`는 SDK 설정과 debug APK packaging 뒤 Android test Kotlin compile에서 실패했다.
`assertDoesNotExist`의 잘못된 extension import를 공식 Compose API와 대조해 제거했고
수정 후 최신 source의 instrumentation APK compile 및 실제 5-case 실행은 PASS다.
실패 실행과 수정 후 실행을 구분하여 WORKLOG에 기록한다.

실제 연결 기기/에뮬레이터가 있는 환경에서는 아래 wrapper를 실행한다.

```bash
./scripts/device-check.sh
```

이 스크립트는 source SHA·working tree, device fingerprint/API/ABI, 실제 task 목록을 기록하고
`--rerun-tasks :androidApp:connectedDebugAndroidTest`를 실행한다. 이전 generated XML을 지운 뒤
새 XML에서 실패/error/skipped가 없는지와 필수 case 5개가 모두 실행됐는지 확인한다.
`build/device-evidence/`의 로그와 `androidApp/build/outputs/androidTest-results/connected/` XML을 함께 보존한다.
`.github/workflows/mobile-device-tests.yml`은 GitHub Ubuntu 24.04 KVM에서 API 36 Google APIs x86_64
에뮬레이터로 같은 명령을 실행한다. workflow 작성·APK compile은 UI PASS 증거가 아니다.
로컬 adb 부재의 최초 차단은 `docs/evidence/device-local-blocker/results.txt`에 보존했다.
adb 설치 후 재실행도 연결된 authorized device가 없어 exit 78이었다.
현재 증거는 `docs/evidence/device-local-blocker/resumed/results.txt`이며 로컬 `/dev/kvm`도 없다.
SDK emulator37.1.11/command-line tools19.0 및 emulator runner v2.34.0 commit의 공식 근거는
`docs/evidence/device-local-blocker/toolchain-pins.json`에 있다. Synthetic verifier 검사는 UI 실행이 아니다.

환경 없이 실행 가능한 스크립트 오류 전달 검사는 아래 명령이며 현재 exit 0이었다.
실제 성공·명령 실패·환경 차단·로그 쓰기 실패를 구분하는 검사로 앱 build/test를 대신하지 않는다.

```bash
bash -n scripts/doctor.sh scripts/ci-check.sh
sh -n gradlew
python3 docs/evidence/ci-helper-harness.py
```

아래 테스트는 M00에서 실제 통과했으며 각 테스트의 책임은 다음과 같다.

| 소스 | 현재 검사 내용 |
|---|---|
| `shared/src/commonTest/.../LocatorCodecTest.kt` | TXT/EPUB/Comic envelope round-trip, UTF-16 Long offset, 미지원/손상 locator 거부 |
| `shared/src/commonTest/.../BookModelTest.kt` | 내부 관리 상대 경로 모델 계약 |
| `shared/src/jvmTest/.../RoomDatabaseSmokeTest.kt` | 실제 파일 DB 생성·삽입·조회·재개방, FK, 중복 삽입 시 기존 progress 보존, 손상/미지원/revision 불일치 기록 보존, 닫힌 DB 오류 구분 |
| `androidApp/src/test/.../AppViewModelTest.kt` | Loading/Empty/Error/retry, open 실패, 취소 시 close, 탭/테마 실패 처리 |
| `androidApp/src/test/.../LibrarySearchTest.kt` | 메타데이터 부분 문자열과 `%`/`_` literal 검색 |
| `androidApp/src/androidTest/.../BookReaderScreenTest.kt` | 3탭/검색/테마/각 상태·재시도 Compose UI |
| `androidApp/src/androidTest/.../AndroidThemeStoreTest.kt` | 실제 SharedPreferences 저장·조회와 손상 설정 보존 |
| `androidApp/src/androidTest/.../MainActivityLaunchTest.kt` | 서재 첫 화면, Activity recreation 중 탭 유지; process-death 검사는 아님 |

schema는 Room processor의 실제 export가 성공해야 증거가 된다.
`exportSchema=true` 설정만으로 schema 생성 완료를 기록하지 않는다.
다음 버전 migration 검증은 M10 범위다.

## 검사 수준

| 수준 | 검증 책임 | 다른 검증을 대신하지 않는 범위 |
|---|---|---|
| doctor | JDK/SDK/wrapper/기기 환경의 사전 검사 | dependency resolve, build, test 결과 |
| common tests | 버전·revision이 있는 포맷별 locator 직렬화와 오류 거부 | Readium 실제 복원, TXT renderer |
| 실제 DB integration | Room/bundled SQLite open, insert, query, FK, 데이터 보존 | SAF/provider, migration upgrade, native 기기 호환 |
| Android unit | UI 상태와 navigation/설정 로직 | 기기 UI, Android text measurement |
| Android instrumentation | Activity/Compose 경로와 실제 기기 DB | host가 제어하는 프로세스 종료, 실기기 성능 |
| host-driven process | 외부 adb/runner의 종료·재실행 | 미커밋 상태 보존, 전원 차단 |
| 실기기 | TalkBack/배율/OEM/성능/ABI·16KB | 소스 리뷰만으로 판정 불가 |

## 이후 작업의 검사

M00는 Reader가 없으므로 진행도 저장 경쟁, 마지막 커밋 위치 복원, 이어읽기 및 process-death는 `NOT_RUN`이다.
M03/M04에서 실제 import→독서→저장→외부 종료→서재→이어읽기 경로와 함께 구현·실행한다.
M01의 EPUB Locator round-trip/위치 복원/publication script 및 외부 리소스 차단, CP949 확장 문자 검사는 별도다.
M10의 migration upgrade, 최종 APK native ABI/16KB, cloud/D2D 백업 동작 및 실기기 수치 성능도 M00 통과로 대체하지 않는다.

사용자 책 본문·개인 URI·서명키를 로그나 CI artifact에 넣지 않는다.
테스트는 자체 생성 메타데이터·locator를 사용하고 삭제/skip/ignoreFailures로 실패를 숨기지 않는다.

M01은 `TODO`다. 다음 입력은 검증된 M00 source·정확 pin·Room v1 schema와 위 실행 증거다.
Readium stable 후보 3.4.0의 소비 요구/API/라이선스를 새로 확인한 뒤 필요한 Android adapter와
격리된 debug/test probe를 만든다. locator round-trip/복원, trusted engine와 비신뢰 publication
script·외부 URL 차단, Android strict encoding/CP949 fixture를 각각 실제 실행한다.
M00 PASS를 Readium 호환성이나 Reader/import/process-death PASS로 확대하지 않는다.

같은 source의 원격 기본 CI `37164061596`도 success이며 다운로드한 실제 보고서는
`docs/evidence/m00-validation/build-37164061596/reports/`에 있다.
원격 Android unit10/shared19 cases의 failure/error/skipped는 0이고 lint는 20 Hint다.
실제 CI APK는 [GitHub artifact](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061596/artifacts/11288751988)와
위 `build/deliverables/`에 있으며 크기는 16,629,841 bytes, SHA-256은
`716739c3471b4a21d689396e30b2f850b0bd393abc7ed2681506ec91a8edee82`다.
로컬 APK와 CI APK의 hash는 각각 기록하며 동일 바이트 재현성을 주장하지 않는다.
