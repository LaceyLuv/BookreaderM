# M00 검증 계약

2026-10-01. M00 구현은 작성했고 필수 실행 검증은 `BLOCKED_ENV`다. 결과는 `PASS / FAIL / NOT_RUN / BLOCKED_ENV`로 구분한다.
테스트 파일의 존재, 환경 검사 성공, Activity 재생성은 각각 실제 테스트 통과나 프로세스 종료 검증을 대신하지 않는다.

## 실행 증거

실제 plugin의 `tasks --all` 조회를 시도했지만 Gradle 다운로드 단계에서 exit 1이었다.
명령·exit code·로그는 [WORKLOG](WORKLOG.md)와 `docs/evidence/build-command-results.json`에 기록했다.
`doctor`와 `ci-check`는 JDK compiler·SDK 부재로 exit 78이었다. 이 환경에서 assemble/lint/unit/shared/DB smoke는
실행되지 않았고 APK·test report·export schema가 없다. adb/device도 없어 instrumentation은 `BLOCKED_ENV`다.

## 환경 복구 후 실행

JDK 17과 Android platform 36/build-tools 35.0.0/platform-tools, Gradle/Maven 저장소 접근이 필요하다.
아래 명령은 모바일 루트에서 실행한다.

```bash
./scripts/doctor.sh
./scripts/ci-check.sh
```

`ci-check.sh`는 `./gradlew --version`, `projects`, `:androidApp:tasks --all`, `:shared:tasks --all`을 실제 실행한다.
이 목록에 `assembleDebug / lintDebug / testDebugUnitTest / assembleDebugAndroidTest`, `jvmTest`가 있는지 확인한 다음
다음 후보 명령을 실행한다. 현재 환경에서 후보 task의 실제 plugin 제공 여부는 확인하지 못했다.
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

명령 실행 성공 후 확인할 경로는 아래와 같다. 현재 파일이 만들어졌다는 뜻은 아니다.

| 산출물 | 생성 예정 경로 |
|---|---|
| Debug 앱 APK | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
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
원격 CI와 기기 instrumentation 자체는 실행하지 않았다.

환경 없이 실행 가능한 스크립트 오류 전달 검사는 아래 명령이며 현재 exit 0이었다.
실제 성공·명령 실패·환경 차단·로그 쓰기 실패를 구분하는 검사로 앱 build/test를 대신하지 않는다.

```bash
bash -n scripts/doctor.sh scripts/ci-check.sh
sh -n gradlew
python3 docs/evidence/ci-helper-harness.py
```

작성된 테스트는 아직 실행 완료 증거가 아니다.

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
