# M00 작업 기록

## 2026-10-01 — M00 BLOCKED

대상은 `/workspace/bookreader-mobile` 독립 모바일 프로젝트다. 초기 `/workspace/.git`은 비어 있는
읽기 전용 경로여서 유효한 Git 저장소·브랜치·source SHA가 없었으며, 기존 PC 코드나 미커밋 파일은 없었다.
상위·현재 AGENTS.md도 없었다. 초기 조사 명령/결과는 이 작업 대화에 남아 있다.
제공 ZIP의 9개 원본 파일을 모바일 경로에 보존하고 필수 template/개정 계획서/TASKS를 읽었다.
`AGENTS.template.md`, 개정 계획서, 원문 참고 계획서 및 다른 제공 문서는 덮어쓰지 않는다.
`TASKS.md`에서는 M00 상태와 그 증거만 갱신한다.

진행 중 재검사 로그: `docs/evidence/verification/handoff-environment.txt`.
2026-10-01T01:05:47Z에 `/workspace`와 모바일 루트에서 각각
`git rev-parse --show-toplevel`, `git branch --show-current`, `git status --short`는 exit `128`이었다.
현재 구조와 `/AGENTS.md`, `/workspace/AGENTS.md`, 모바일 `AGENTS.md` 부재도 그 로그에 기록했다.
이는 그 시점의 재검사이며 최초 조사나 이후 Git 초기화 결과를 대신하지 않는다.

독립 Git 저장소는 브랜치 `m00-bootstrap-mobile-foundation`에 초기화했다.
commit/source SHA는 없고 추가한 파일은 untracked working tree다. 사용자 파일을 stage/commit/reset하지 않았다.
M00 실제 코드·테스트는 작성했으나 필수 실행 검증이 **BLOCKED_ENV**여서 작업 상태는 **BLOCKED**다.
미실행 검증을 PASS나 DONE으로 기록하지 않는다.

이번 범위는 Android Compose 3탭, KMP 모델/locator/repository, 실제 Room KMP DB와 테스트,
고정 툴체인/wrapper/환경·CI 스크립트 및 인계 문서다.
import·Reader·이어읽기·EPUB/Comic 제품 기능은 구현 범위 밖이다.

## 구현 파일과 사용자 경로

- `androidApp/src/main/.../MainActivity.kt`, `ui/BookReaderApp.kt`, `ui/AppViewModel.kt`, `ui/AndroidStores.kt`:
  새 프로세스의 서재 → 실제 DB Loading/Empty/Content/Error → 오류 재시도,
  검색/설정 탭 전환, 불러온 메타데이터 문자 그대로 검색, SYSTEM/LIGHT/DARK 테마 저장, Back→서재.
  DB query handle은 각 load의 코루틴이 소유하고 `finally`로 닫는다. 데모 책이나 가짜 읽기 버튼을 넣지 않았다.
- `shared/src/commonMain/.../{model,locator,repository,database}`:
  Book/ReadingProgress, version/contentRevision 포맷별 locator, 오류를 빈 상태와 구분하는 repository,
  Room v1 books/reading_progress 테이블과 FK·중복 방지·ABORT 삽입.
- `shared/src/{androidMain,jvmMain}/.../database`:
  Android의 private durable database와 JVM file-backed bundled SQLite builder.
  JVM target은 테스트 실행용이며 Desktop 제품 앱을 추가하지 않는다.
- `shared/src/{commonTest,jvmTest}`, `androidApp/src/{test,androidTest}`:
  locator/model/실제 DB·데이터 보존 및 ViewModel/검색/Compose/테마/Activity 테스트를 작성했다.
  실제 실행 상태는 아래 증거와 분리한다.
- `androidApp/src/main/AndroidManifest.xml`, `res/xml/{backup_rules,data_extraction_rules}.xml`:
  추가 permission 없음, cloud/D2D backup 제외 선언. XML 검사는 OS/OEM에서의 차단 증거가 아니다.
- 루트 Gradle 설정·wrapper·버전 catalog, `scripts/doctor.sh`, `scripts/ci-check.sh`, `.github/workflows/`와
  `AGENTS.md`, README, DEPENDENCIES/TESTING/WORKLOG/ADR-0001/0006을 기반 산출물로 마련한다.

원본 파일 byte 비교에서 `TASKS.md` 끝 빈 줄 제거를 발견하여 원본 M01 이후 bytes를 복원했다.
첫 검사 `FAIL`(exit 1): `docs/evidence/verification/source-integrity-initial.txt`.
복원 후 `PASS`(exit 0): `docs/evidence/verification/source-integrity.txt`.
최종 비교는 첨부 8개 원문 파일의 byte/hash 동일성과 TASKS M01 이후 byte 보존을 확인한다.

## 고정 의존성과 결정

Gradle 8.13, AGP 8.11.1, Kotlin/Compose compiler 2.2.20, KSP 2.2.20-2.0.3,
Room 2.8.3, SQLite 2.6.1, Compose BOM 2025.10.00를 정확히 고정했다.
JDK 17, minSdk 26, compile/targetSdk 36, build-tools 35.0.0을 선택했다.
이 조합의 실제 dependency resolve/build 성공은 확인되지 않았다.
공식 자료 확인 범위와 안정 Room 2 계열 선택 이유는 [DEPENDENCIES](DEPENDENCIES.md)/ADR-0006을 따른다.
공식 Gradle 8.13 wrapper 원본을 공급했고 JAR/bin SHA-256을 고정했다.

## 실행 결과

명령은 별도 표시가 없으면 `/workspace/bookreader-mobile`에서 실행했다.
현재 로그의 stack trace는 Gradle 자체 다운로드 차단이며 애플리케이션 test failure가 아니다.

| 명령 | exit | 판정 | 로그 |
|---|---:|---|---|
| `./scripts/doctor.sh` | 78 | BLOCKED_ENV — Java 21 JRE만 있고 javac/Android SDK 없음 | `docs/evidence/doctor.log` |
| `./scripts/ci-check.sh` | 78 | BLOCKED_ENV — doctor 단계에서 중단 | `docs/evidence/ci-check.log` |
| `./gradlew --version` | 1 | BLOCKED_ENV — 배포 다운로드 socket Operation not permitted | `docs/evidence/gradle-version.log` |
| `JAVA_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080' ./gradlew --version` | 1 | BLOCKED_ENV — proxy 지정 후에도 socket Operation not permitted | `docs/evidence/gradle-version-proxy.log` |
| `./gradlew --no-daemon --console=plain projects` | 1 | BLOCKED_ENV — 같은 다운로드 차단 | `docs/evidence/projects.log` |
| `./gradlew --no-daemon --console=plain :androidApp:tasks --all` | 1 | BLOCKED_ENV — 실제 task 목록 미확인 | `docs/evidence/android-tasks.log` |
| `./gradlew --no-daemon --console=plain :shared:tasks --all` | 1 | BLOCKED_ENV — 실제 shared task 목록 미확인 | `docs/evidence/shared-tasks.log` |
| Android XML/manifest 정적 검사(전체 명령은 로그 안에 보존) | 0 | PASS — XML 파싱, permission·backup 제외 선언만 검사 | `docs/evidence/verification/android-static-check.txt` |
| `bash -n scripts/doctor.sh scripts/ci-check.sh` | 0 | PASS — Bash 문법만 검사 | `docs/evidence/shell-syntax.log` |
| `sh -n gradlew` | 0 | PASS — 공식 wrapper script 문법만 검사 | `docs/evidence/wrapper-syntax.log` |
| `python3 docs/evidence/ci-helper-harness.py` | 0 | PASS — 성공·exit7·exit78·로그 실패 4개 코드 전달 및 catalog 파싱 | `docs/evidence/ci-helper-harness.log` |
| `python3 build/verification/source-integrity.py` | 0 | PASS — 원본 8파일 및 TASKS M01 이후 byte 보존 | `docs/evidence/verification/source-integrity.txt` |
| `python3 build/verification/final-static-check.py` | 0 | PASS — 작성한 텍스트 소스/설정/문서 51개 whitespace 검사, Markdown hard break 허용 | `docs/evidence/verification/final-static-check.json` |
| `git diff --check` | 0 | PASS — tracked diff만 검사; 새 untracked 코드 검증을 대신하지 않음 | root read-only 검사; 별도 whitespace 검사 필요 |

실제 명령 배열·exit는 `docs/evidence/build-command-results.json`에도 보존했다.
초기 `ci-check` 로그는 doctor exit 78을 `FAIL doctor`로 표기했고 `docs/evidence/ci-check-initial.log`에 보존했다.
logger를 보완한 실제 재실행도 exit 78이며 `docs/evidence/ci-check.log`에 `BLOCKED_ENV doctor`로 기록한다.
command 실패와 tee 로그 실패를 모두 전달하며 환경 차단을 성공으로 무시하지 않는다.
초기 일반 network curl의 exit 7과 추가 network permission 시도의 tool `aborted by user` 결과는 대화에 남아 있다.
성공한 network build 실행이나 명시적 사용자 권한 거부·자동 승인 검토 거절로 해석하지 않는다.

| 실행 검증 | 상태 | 이유 |
|---|---|---|
| dependency resolve/Android assemble/lint/unit/shared tests | NOT_RUN | BLOCKED_ENV: Gradle 다운로드·JDK compiler·Android SDK 필요 |
| 실제 Room/SQLite smoke | NOT_RUN | BLOCKED_ENV: 작성한 JVM integration test를 실행하지 못함 |
| Room schema export | NOT_RUN | BLOCKED_ENV: KSP 미실행; schema 파일을 손으로 만들지 않음 |
| Android instrumentation/UI·prefs·launcher | NOT_RUN | BLOCKED_ENV: APK compile 및 adb/device 필요 |
| host-driven process-death, Reader 위치 복원/이어읽기 | NOT_RUN | M03/M04의 import/Reader/writer 경로가 아직 없음 |
| Readium 호환성/EPUB 복원·보안, Android CP949 | NOT_RUN | M01 범위 |
| 실기기 performance/TalkBack/font scales/OEM backup/ABI·16KB/migration upgrade | NOT_RUN | 기기 및 후속 작업 증거 필요 |
| 원격 GitHub workflow | NOT_RUN | push·원격 실행 요청 없음 |

현재 APK, unit/instrumentation test report, 실제 DB 파일 및 Room schema export는 **없다**.
환경 복구 후 생성할 경로와 명령은 [TESTING](TESTING.md)에 따로 기록했다.
로그 존재를 APK/테스트 성공 증거로 바꾸지 않는다.

## 해소해야 할 위험

고정 버전 조합의 dependency/API/task 실제 호환성을 아직 확인하지 못해 compile/lint 결함이 남을 수 있다.
JVM DB smoke 테스트는 실제 Room과 bundled SQLite를 쓰도록 작성했지만 실행 전에는 데이터 보존 PASS가 아니다.
Android manifest 백업 제외는 선언 검사만 통과했고 OS/OEM cloud/D2D 동작은 미검증이다.
새 Android UI의 실제 설치/탭/오류·테마/접근성도 instrumentation과 기기 확인이 필요하다.
EPUB Locator payload round-trip 모델은 SDK 위치 복원이나 publication 보안 증거를 대신하지 않는다.

최종 Astra 읽기 전용 검토에서 추가로 확인된 소스 결함은 없었다.
이는 build/런타임/테스트 PASS가 아니며 M00 **BLOCKED** 판정과 미실행 검증을 유지한다.
CI action은 공식 tag의 확인된 commit SHA로 고정했고 근거는 `docs/evidence/ci-action-pins.json`에 있다.

인계 ZIP은 `/workspace/bookreader-mobile-M00.zip`이며 SHA-256 sidecar는 같은 이름에 `.sha256`을 붙인다.
소스·원문 문서·공식 wrapper·실제 로그를 포함하고 `.git`/Gradle·Kotlin cache/일반 build cache/개인 키를 제외한다.
`build/verification`과 `build/ci-evidence`의 실제 검사 로그만 의도적으로 포함한다. APK 없는 소스 인계다.

## M01 착수 조건

1. M00 실제 dependency resolve와 Android assemble/lint/unit/shared/Room smoke가 통과하고 schema export가 확인되어야 한다.
   JDK 17·Android SDK 36/build-tools 35.0.0·Gradle/Maven 접근을 준비한 뒤 실제 task 목록을 먼저 확인한다.
   기기/에뮬레이터에서 서재 첫 화면·3탭·상태 구분·테마를 실제 실행하여 사용자 경로도 확인한다.
   환경으로 막힌 필수 검증은 먼저 해소하며 M00를 DONE으로 바꾸지 않는다.
2. 새 작업을 명시적으로 선택한 뒤 `epubAndroid`와 격리된 debug/test probe만 추가한다.
   M00에서 미리 사용하지 않는 모듈이나 Readium UI를 생성하지 않는다.
3. 안정 Readium Android Navigator의 정확 버전·라이선스·SDK/compiler 요구와 실제 API를 확인하여 ADR-0005에 기록한다.
   계획서의 초기 후보는 Readium 3.4.0이다. M00 제품 dependency에는 Readium을 넣지 않았으며,
   M01 probe에서 그 후보의 실제 요구·해석·호환성을 확인하고 최종 pin을 결정한다.
4. 재배포 가능한 EPUB2/3·이미지/CSS/TOC/locator 및 악성 script/외부 URL fixture,
   CP949 확장 문자·EUC-KR·UTF BOM·BOM 없는 UTF-16 fixture와 Android 실행 환경을 준비한다.
5. SDK locator 전체 round-trip·프로세스 재시작/테마/글자 크기 변경 뒤 위치 복원,
   trusted engine 동작과 publication script/외부 요청 차단의 양립을 각각 증거로 남긴다.
   exact restore와 fallback을 구분하고, 존재하지 않는 보안 API를 추정하지 않는다.

M01 자체 blocker는 M08/M11의 EPUB gate를 막는다. M01 보안/복원 증거 없이 EPUB 지원 완료를 선언하지 않는다.

## 2026-10-01 — GitHub BookreaderM 게시 준비

사용자가 GitHub에 `BookreaderM` 새 저장소를 생성하고 현재 모바일 구현을 commit/push하도록 명시적으로 요청했다.
기존 `m00-bootstrap-mobile-foundation` 브랜치와 사용자 Git 작성자 설정을 유지하여 M00 기반 구현의 첫 commit을 준비한다.
위의 untracked/commit 없음/원격 실행 요청 없음 기록은 M00 구현 당시의 상태이며 이 후속 요청 이전 이력이다.
원격 저장소 생성과 push 성공은 이 준비 기록에서 주장하지 않는다.

Git에서 제외되는 `build/verification`과 `build/ci-evidence`의 기존 작은 비공개정보 없는 결과 로그를
`docs/evidence/verification/`과 `docs/evidence/ci-evidence/`에 복사해 commit에 포함한다.
위 실행표의 명령은 실행 당시 원문을 보존하며, 결과 로그 경로만 commit에 포함되는 복사본으로 연결한다.
인계 ZIP의 기존 `build/` 로그 위치는 당시 산출물 이력이다. 이후 `ci-check`의 신규 로그 출력은 계속 `build/ci-evidence/`다.
애플리케이션 코드와 테스트를 변경하거나 검증을 재실행하지 않았으며, M00의 `BLOCKED` 상태와 M01 선행 조건을 유지한다.

게시 준비 검사: staged 파일 84개에 대한 비밀키/토큰 패턴 및 비공개 파일명 검사 결과 0건(`PASS`, exit 0).
`git diff --cached --check`는 exit 2였다. 보존된 원문·공식 문서 및 ADR-0006의 Markdown hard break,
원문 TASKS와 공식 wrapper의 끝 빈 줄, 기존 Gradle 설정의 끝 빈 줄이 보고되었다.
원문 보존과 이번 게시 범위에 따라 이 공백은 수정하지 않았으며 해당 검사를 `PASS`로 표시하지 않는다.

## 2026-10-01 — 사용자 생성 GitHub 저장소 이력 병합

사용자가 생성한 `https://github.com/LaceyLuv/BookreaderM`의 `main` 초기 commit
`29688de167a37e8ab0ce53ab321988526693cd05`를 fetch 후 확인했다. 원격에는 `README.md`만 있었고
`# BookreaderM` 제목과 `북리더 모바일` 설명이었다. 로컬 M00 첫 commit
`df31f2c21ed30799acdbf310cc8bfbdb2c39ecca`와 이 원격 이력을 `--allow-unrelated-histories`로 병합한다.
README add/add 충돌은 원격 제목·설명을 맨 위에 보존하고 로컬 M00 본문 전체를 이어 붙여 해결했다.
M00 소스·테스트·설정·원문 문서를 변경하지 않았으며 원격 초기 commit 이력을 보존한다.
게시할 기본 브랜치에 맞추어 로컬 임시 브랜치 이름을 `main`으로 바꾸고 일반 push를 준비한다.
이 기록 시점에는 push 성공이나 GitHub Actions 통과를 주장하지 않는다. M00 `BLOCKED` 판정은 유지한다.

## 2026-10-03 — M00 필수 실행 검증 재개

기준 source는 `0a3802e`이며 작업 디렉터리는 `/workspace/bookreader-mobile`이다.
10월 1일의 차단/원격 게시 준비 기록은 당시 이력으로 보존한다.
이번 작업도 M00에 한정하며 원문 계획서·template·reference와 TASKS M01 이후 bytes를 보존한다.

JDK 17·Android SDK 36/build-tools 35.0.0을 설치한 뒤 실제 doctor·Gradle version/projects 및
Android/shared task discovery가 exit 0으로 통과했다. 명령별 로그와 결과는
`docs/evidence/m00-validation/ci/`에 있다. catalog/plugin/library pin을 변경하지 않았다.
KSP plugin 조회의 일시 실패는 공식 Maven POM·HTTP 및 repository 재조회로 진단했으며
버전 부재로 판정하지 않았다. 전체 build/test/schema 및 resolved graph 결과는 확인 중이다.

10월 1일 원격 기본 CI run `36805008404`의 실제 로그를 10월 3일 재확인해 SDK 설정 성공과 debug APK packaging을 확인했다.
이후 `:androidApp:compileDebugAndroidTestKotlin`은
`BookReaderScreenTest.kt:4:33 Unresolved reference assertDoesNotExist`로 실패했다.
공식 Compose API에서 이 assertion은 `SemanticsNodeInteraction` member임을 확인하고
잘못된 extension import 한 줄을 제거했다. 테스트 case/assertion 삭제·skip 없이 재검증한다.
원격 실패 excerpt는 `docs/evidence/m00-validation/ci-36805008404-failure-excerpt.log`에 보존했다.
수정 소스·기기 workflow·doctor/SDK pin은 `4abc4bb94fc843fcc843bfeffcdc43164704d7df`로 게시됐다.
`gh --log-failed`의 Azure 403/빈 결과는 성공 증거로 취급하지 않고 connector의 실제 로그를 확인했다.

기기 검증은 `scripts/device-check.sh`와 별도 GitHub KVM API36 x86_64 workflow로 실행한다.
신규 XML에서 5개 필수 case의 실행과 failure/error/skipped 부재를 확인해야 UI `PASS`다.
현재 로컬 기기 환경 차단은 `docs/evidence/device-local-blocker/results.txt`에 기록한다.
전체 자동 검사와 실제 instrumentation 결과 확인 전까지 M00 `DONE`을 주장하지 않는다.
M01 Readium/encoding probe나 M02 제품 구현에는 착수하지 않았다.

### 첫 로컬 전체 실행 — FAIL 보존

첫 전체 실행은 아래 명령으로 exit 1이었다. 세션용 환경 파일은 저장소 밖 설치 경로다.

```bash
source /workspace/toolchains/mobile-env.sh
BOOKREADER_EVIDENCE_DIR=docs/evidence/m00-validation/ci ./scripts/ci-check.sh \
  > docs/evidence/m00-validation/ci-check.log 2>&1
```

Android unit XML은 10 cases,
shared XML은 18 cases 중 1 failure를 기록했다. 실제 실패는
`RoomDatabaseSmokeTest.unavailableDatabaseReadIsErrorAndPreservesDiskRecord`의 닫힌 DB 조회에서
`JobCancellationException`이 전달된 것이다. 실패 XML과 명령 로그는
`docs/evidence/m00-validation/attempt-1-shared-failure/`에 보존했다.
Debug 앱·instrumentation APK compile과 KSP schema export는 실행됐지만 lint 완료 보고서는 없었다.
실패 원인 수정 후 전체 검사를 재실행하며 첫 실패를 PASS로 덮어쓰지 않는다.

닫힌 DB 실패는 Room의 자체 query scope 취소와 호출자 코루틴 취소를 구분하여 수정한다.
repository의 세 CancellationException 처리에서 호출자 `currentCoroutineContext().ensureActive()`를
먼저 검사하므로 실제 호출자 취소는 전파되고, 호출자가 살아 있으면 `DATABASE_UNAVAILABLE` 오류다.
기존 닫힌 DB·reopen·기록 보존 assertion을 유지하며 실제 DB의 호출자 취소 회귀 case를 추가했다.
추가 case를 포함한 최종 shared 실행 건수는 재실행 XML에서 확인한다.

### Lint 실행 — FAIL 보존

이후 실제 `:androidApp:lintDebug`는 exit 1, 23 errors였다.
20건은 고정 의존성의 새 버전 알림(`AndroidGradlePluginVersion` 3건,
`GradleDependency` 9건, `NewerVersionAvailable` 8건)이고,
3건은 `ObsoleteSdkInt`·`MonochromeLauncherIcon`·`UseKtx` 소스/리소스 진단이다.
실제 실패 로그·HTML/XML은 `docs/evidence/m00-validation/attempt-1-lint-failure/`에 보존한다.
소스 진단을 수정하고 고정 버전 알림의 처리 범위를 검토한 뒤 lint 전체를 재실행한다.

### 소스 수정 후 shared 회귀 — PASS

`./gradlew --no-daemon --console=plain :shared:jvmTest` 재실행은 exit 0이었다.
`docs/evidence/m00-validation/shared-regression.log`와 실제 XML에서 Room DB 12,
locator 6, book model 1, 합계 19 cases의 failure/error/skipped가 모두 0임을 확인했다.
최초 18 cases는 유지했고 실제 호출자 cancellation 회귀 1개를 추가했다.
전체 ci-check/lint와 기기 실행의 최종 결과는 별도로 확인한다.

소스 lint 진단은 minSdk26에 불필요한 launcher icon `-v26` qualifier 제거 및 monochrome 추가로 처리한다.
`AndroidStores.write`의 직접 SharedPreferences `commit()`은 저장 실패 Boolean을 확인해야 하므로 유지한다.
Unit을 반환하는 KTX edit로 바꾸어 실패 신호를 잃지 않도록 이 함수에만 `UseKtx`를 억제하며
다른 lint 경고의 기본 실패 정책은 유지한다. 이 선택은 저장 성공을 가장하지 않는 계약에 따른다.

### 고정 버전 알림의 lint 정책

읽기 전용 Astra 검토 후 `AndroidGradlePluginVersion`, `GradleDependency`,
`NewerVersionAvailable` 세 upgrade-only 진단만 활성화된 `informational`로 분류한다.
선택한 stable pin은 공식 호환표와 실제 graph로 검증하며 최신 버전 알림만으로 자동 변경하지 않는다.
알림을 삭제·disable하지 않고 최종 보고서의 실제 건수와 내용을 남긴다.
그 외 lint warning/error는 기존 fatal 정책을 유지한다. 따라서 최종 exit 0도 '발견 사항 0건'을 의미하지 않는다.

수정 전 source `4abc4bb94fc843fcc843bfeffcdc43164704d7df`의 기본 GitHub CI run
`37163324747`도 실제 connector 로그에서 같은 shared 닫힌 DB case의 실패를 확인했다.
이 실행은 shared 18 cases 중 1 failure이며 로컬 수정 후 19 cases 결과와 구분한다.
같은 source의 device run `37163324713`은 진행 중이며 실제 XML/로그 확인 전 PASS로 기록하지 않는다.

로컬 `./scripts/device-check.sh`를 adb 설치 후 재실행했다.
source/doctor/adb-version/attached-devices 명령은 exit 0이었지만 authorized device 목록은 비어 있어
최종 exit 78 (`BLOCKED_ENV`)이었다. `docs/evidence/device-local-blocker/resumed/`의 실제 로그를 따른다.
로컬 `/dev/kvm` 부재와 별개로 원격 device workflow의 SDK/KVM 준비 성공을 확인했으며 실제 UI 결과는 대기한다.

## 2026-10-04 — 최종 로컬 전체 검증 PASS

이 절은 `bd1ba033` 실행 당시의 이력이며 아래 로그·보고서·summary는
`docs/evidence/m00-validation/attempt-2-local-pass-remote-failure/`에 분리 보존했다.
현재 `3f04b8ba` 최종 결과와 구분한다.

전체 ci-check는 source `bd1ba0332e292029bd09677bb0db9ddbe29dfd43`에서 위와 같은
`BOOKREADER_EVIDENCE_DIR` 명령으로 exit 0이었다.
`docs/evidence/m00-validation/attempt-2-local-pass-remote-failure/ci/results.txt`는 doctor/version/projects/두 모듈 task discovery와
build-and-tests 모두 `PASS`를 기록한다. APK assemble/test APK compile·lint·Android unit/shared tests·schema 검사가 포함된다.

`docs/evidence/m00-validation/attempt-2-local-pass-remote-failure/reports/`의 실제 XML을 확인했다. Android unit은 10 cases, shared는 19 cases
(Room DB 12/locator 6/book model 1)이며 failure/error/skipped 모두 0이다.
Shared 최종 task는 수정 후 실제 19 cases가 실행된 isolated regression 결과를 up-to-date로 재사용했고
Android unit은 최종 전체 실행에서 다시 수행됐다. 로그의 up-to-date 표기와 실제 XML을 함께 남긴다.
Lint는 error/warning 0, 활성 informational upgrade 안내 20건(3/9/8)이다.
이는 finding 0건이나 의존성 최신 버전 채택을 의미하지 않는다.

Room KSP가 생성한 `shared/schemas/org.bookreader.mobile.database.BookReaderDatabase/1.json`은
version1, books/reading_progress 두 테이블 및 FK를 포함하며 committed schema와 동일하다.
Debug 앱 APK는 17,085,394 bytes, Android test APK는 1,081,831 bytes다.
정확 SHA-256/경로·schema hash는 `docs/evidence/m00-validation/attempt-2-local-pass-remote-failure/final-validation-summary.json`에 있다.
APK와 unit/lint HTML은 generated build 경로이며 Git에는 작은 XML/summary/로그 증거와 schema를 보존한다.

최신 source의 원격 build run `37163680043`, device run `37163680032`는 확인 중이다.
이전 source `4abc4bb`의 device run `37163324713`에서 5 cases PASS가 관찰됐지만
최신 source의 승인 증거로 대체하지 않는다. 최신 5-case 결과 전 M00 DONE은 보류한다.

### 다음 작업 M01의 현재 입력과 범위

M01 상태는 `TODO`이며 구현에 착수하지 않았다. 다음 명시적 M01 작업의 입력은 다음과 같다.

1. 검증 source `3f04b8ba`의 androidApp/shared 경계와 정확 pin 툴체인,
   M00 CI·19 shared/10 Android unit·최종 5 instrumentation 결과 및 generated Room v1 schema.
   최신 기기/원격 CI gate가 통과한 M00를 완료 상태로 전달한다.
2. Readium stable Android Navigator 후보 3.4.0의 실제 release·라이선스·SDK/compiler 요구/API 확인.
   M00 graph에는 Readium이 없으므로 이 후보가 현재 조합에서 검증됐다고 가정하지 않는다.
3. 필요한 `epubAndroid` adapter와 격리 debug/test probe만 추가하고 ADR-0005에 exact pin과 host 형태를 기록한다.
   Readium payload는 common domain에 노출하지 않으며 locator 전체 round-trip/재사용·복원을 검사한다.
4. 재배포 가능한 EPUB2/3·이미지/CSS/목차/locator 및 publication script/외부 URL fixture.
   trusted engine script는 동작하면서 비신뢰 publication script와 외부 요청을 차단할 수 있는지 실제 관찰한다.
   exact restore와 chapter fallback을 구분하고 없는 보안 API를 만들지 않는다.
5. Android strict decoder의 UTF BOM/CP949 확장/EUC-KR/BOM 없는 UTF-16 fixture와 수동 선택 probe.
   실제 Android 실행 결과를 남기며 JVM charset 지원만으로 대상 Android PASS를 주장하지 않는다.

M00의 cold-launch/3탭/상태/테마 case는 Reader/import/이어읽기나 host-driven process-death 증거가 아니다.
그 제품 경로와 지속 writer/종료 검증은 M02–M04에서 별도로 구현한다.

### 동일 source의 원격 결과 — UI PASS / 기본 CI FAIL

Source `bd1ba033`의 실제 device run `37163680032`는 success, 필수 UI/설정/launcher 5 cases PASS다.
반면 기본 CI run `37163680043`은 shared 19 cases 중
`callerCancellationIsNotConvertedToDatabaseError` 1 failure를 기록했다.
로컬에서 통과한 신규 호출자 cancellation 회귀가 원격 스케줄링에서 실패한 환경 차이를 조사한다.
실제 실패를 숨기거나 회귀 test를 약화하지 않고 호출자 cancellation 전달 경로를 수정한다.
기존 `final-validation-summary.json`의 로컬 PASS는 해당 실행의 유효한 결과이며
원격 실패를 대체하지 않는다. 수정 후 새 source에서 전체 CI와 기기 UI를 모두 다시 검증한다.
최종 M00 DONE은 이 회귀 해소 전 보류한다.

호출자 cancellation의 원격 실패는 Room DAO의 synchronous fast path가 사전에 취소된 호출자에게
suspend 없이 값을 반환할 수 있어 catch 경로에 진입하지 않은 데서 발생했다.
두 public repository read 함수의 첫 문장에 `currentCoroutineContext().ensureActive()`를 추가하여
DAO 호출 전에 사전 취소를 전파한다. 기존 catch·닫힌 DB/reopen/data 보존·호출자 취소 assertion은 유지한다.
수정 결과는 새 source의 실제 19-case/전체 CI와 기기 실행으로 확인한다.

### 호출자 entry guard 수정 후 새 source 로컬 PASS

Source `3f04b8ba53d23846a454ee744c5e1fc33bea45e3`에서 전체 ci-check를 같은 명령으로 다시 실행해
exit 0을 확인했다. 이번 shared JVM task는 실제 새로 실행됐고 Android unit도 재실행됐다.
최종 XML은 shared19/Android10 cases, failure/error/skipped 모두 0이다.
Lint는 20 informational Hint, warning/error 0이며 generated schema는 committed v1과 동일하다.
현재 `final-validation-summary.json`/`final-reports/`는 이 source의 새 결과이고
앞선 bd1ba 로컬 PASS·원격 FAIL 이력은 `attempt-2-local-pass-remote-failure/`에 분리 보존한다.
새 Debug APK SHA-256은 `c9188f03cb9f242af9b1a05a1b0ac284091b4d63872114aa420afec8ee36e737`이다.
새 source의 remote build `37164061596`와 device `37164061580` 결과는 확인 중이다.

같은 source `3f04b8ba`의 원격 기본 CI
[37164061596](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061596)도
2026-10-04 00:13:57 UTC에 success로 완료됐다. 로컬과 원격의 전체 실행 성공을 각각 확인했다.
실제 원격 report/log와 artifact metadata는 `docs/evidence/m00-validation/build-37164061596/`에 보존한다.
Device `37164061580`의 최종 필수 XML 검증만 대기한다.

최종 원격 build artifact `11288751988`의 다운로드 digest와 실제 29-case XML을 확인했다.
로컬과 동일하게 Android10/shared19, failure/error/skipped 0 및 lint 20 Hint이고 exported schema도 byte 동일하다.
실제 CI Debug APK는 16,629,841 bytes, `build/deliverables/m00-3f04b8b/androidApp-debug.apk`,
SHA-256 `716739c3471b4a21d689396e30b2f850b0bd393abc7ed2681506ec91a8edee82`다.
[artifact 링크](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061596/artifacts/11288751988)와
`build-37164061596/artifact-inspection.json`에 원격 bytes/hash를 보존했다.
로컬 APK `c9188f03...`와 원격 APK hash를 분리 기록하고 동일 바이트 빌드 재현성은 검증 완료하지 않는다.

## 2026-10-04 — M00 DONE 및 인계

Source `3f04b8ba53d23846a454ee744c5e1fc33bea45e3`의 실제 API36 Google APIs x86_64 device run
[37164061580](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061580)이 성공했다.
Downloaded actual instrumentation XML에서 필수 5 cases 모두 실행됐고 failure/error/skipped가 없음을 확인했다.
기기 fingerprint/API/ABI·원격 run/job/artifact metadata·실제 XML·필수 명령 결과와 발췌/hash는
`docs/evidence/m00-validation/device-37164061580/` 및 `verified-summary.json`에 보존한다.
원본 전체 job log는 Git에서 제외된 `build/device-ci-artifacts/37164061580/job-111323264829.log`에
바이트 그대로 보존하며 이 파일의 SHA-256을 `verified-summary.json`에 기록한다.
원격 전체 로그는 위 GitHub run의 job 화면에서도 확인할 수 있다.
3탭·검색·테마, Loading/Empty/Error와 retry, 실제 SharedPreferences 정상/손상 설정,
서재 cold launch와 Activity recreation의 최소 사용자 경로를 검사했다.

로컬 fullci exit0·Android unit10/shared19·실제 DB/schema·lint 및 같은 source의 기기5cases가 충족되어
TASKS의 M00만 `DONE`과 완료 체크로 갱신했다. 20 informational 버전 안내는 보고서에 남아 있다.
최신 소스의 원격 기본 CI는 [37164061596](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061596)이며
최종 원격 결과도 success이며 로컬 결과와 실제 원격 XML/log를 각각 보존한다.
문서/evidence 후속 commit은 검증한 앱/테스트/build source를 바꾸지 않는다.
첨부 원문/template/reference와 TASKS M01 이후 bytes는 보존했다.
M01은 TODO이며 위 입력을 사용할 다음 별도 작업이다.
Host-driven process-death·Reader 위치 복원/이어읽기·Readium 보안/호환성·Android CP949·실기기 성능·
OEM backup·migration upgrade·native ABI/16KB는 이번 M00 결과로 검증 완료하지 않는다.

## 2026-10-04 — TXT 가져오기·실제 독서 우선 구현 착수

사용자가 TXT 추가와 실제 읽기를 우선했으므로 M01의 인코딩 부분, M02 관리 import와 M03 TXT 스크롤/이어읽기를
진행한다. Readium/EPUB는 보류하며 기존 M00 원격 증거와 계획서/첨부 원문을 유지한다.
Baseline commit은 `c5d04b2301d08bc25c791f91e68a802a4309212b`이고 현재 변경은 미커밋 working tree다.
검증 기록은 `docs/evidence/m02-m03-validation/`에 분리한다. 이 변경을 원격 commit/push하지 않았다.

구현 범위는 Room v1→v2 additive migration과 ImportJob journal/managed copy/recovery/중복·삭제,
Android SAF 및 단일 content URI VIEW/SEND 수신·확인, strict streaming decoder와 수동 인코딩 미리보기,
canonical UTF-16LE 파생 cache/유한 text window/실제 행 측정 스크롤 Reader, 조건부 progress writer와 하단 이어읽기다.
핵심 변경 경로는 shared의 `database/importing/repository`, Android의 `importing/encoding/reader/ui` 및
MainActivity/manifest다. 세부 선택은 ADR-0002/0003/0004에 기록했다.

TXT normalizationVersion 1은 첫 BOM만 제거하고 CRLF/CR을 LF로 바꾸며 공백/NFC를 바꾸지 않는다.
영구 revision은 source SHA-256/명시한 encodingId/normalizationVersion의 안정 SHA-256이다.
cache의 fixed-width UTF-16LE 형식은 계획 §18.3의 UTF-8 cache 시작안과 다른 되돌릴 수 있는 내부 선택이다.
locator 좌표는 UTF-16 Long으로 유지하며 cache 소실은 원본/기록 소실이 아니다.
prefix는 조기 표시할 수 있으나 strict 전체 decode/원본·cache integrity와 복원 성공 전에는 writer를 활성화하지 않는다.

격리 encoding 구현은 Kotlin 2.2.20 cached compiler와 실제 JUnit 4.13.2로 검사했다.
정확한 실행 명령은 `python3 docs/evidence/m02-m03-validation/encoding-isolated/run.py`이며 exit 0/PASS다.
내부 compiler/JUnit의 exact argv와 exit code, source SHA-256은 같은 디렉터리 `results.json`에 기록했다.
`compile.log` exit 0, `junit.log`는 13 cases/failure 0/exit 0이다. UTF BOM/short read/CRLF·surrogate 경계,
CP949 확장 vs EUC-KR, 늦은 malformed/suffix preview, byte limit/cancel/time budget을 검사했다.
이는 encoding-only host 검사이며 Android 실제 Charset 또는 전체 앱 빌드 PASS로 확대하지 않는다.

자체 작성 `fixtures/encoding/` 11 files의 actual byte size/SHA-256은 manifest와 일치해 PASS다.
CP949 fixture는 U+AC02/`81 41`이며 Android `AndroidTxtEncodingTest`에 동일 바이트를 사용했다.
`bookreader-demo-ko-utf8.txt`는 21,544 bytes, CC0-1.0, SHA-256
`d64f9bff6cd4e789631ce5df2f3882a0bcd86db5f616fe4513b4704ef173cc68`이다. 수동 import/여러 화면 읽기용이며 앱에 자동 삽입하지 않는다.

현재 M01/M02/M03/M04는 IN_PROGRESS다. M01 전체 EPUB gate와 M04 전체 SAFE/60초 이동/경쟁/종료·지연 matrix는
이 기반 구현으로 완료하지 않는다. 새 전체 assemble/lint/unit/shared DB/schema, Android provider/intent/Reader·charset,
외부 force-stop/재실행은 실행 결과 대기(NOT_RUN)다. 실기기 5MiB/50MiB p95·메모리/OEM/접근성은 NOT_RUN이다.
테스트 결과가 들어오면 실제 source identity·exact command·exit·XML case count를 별도 항목으로 추가한다.

### 통합 중간 Android unit 실행 PASS — 최종 CI와 구분

`./gradlew --no-daemon --console=plain :androidApp:testDebugUnitTest`는 exit 0이다.
actual XML은 31 cases/failure 0/error 0/skipped 0이며 M00 10, decoder 13, cache/controller 8 cases다.
`docs/evidence/m02-m03-validation/android-unit-attempt-1-summary.json` 및 `android-unit-attempt-1.log`에 기록했다.
이후 reader 소스/테스트 수정이 계속되어 이 31-case 결과를 최종 working tree 검증으로 취급하지 않는다.
최종 full CI와 Android 기기/외부 프로세스 종료 결과를 따로 실행·확인한다.

실제 Room KSP 중간 compile에서 schema v2 export를 생성했다. `initial-schema-export.json`은 v1/v2
identity/hash와 table 목록을 기록한다. 이는 DB upgrade 실행 검증이나 전체 앱 compile 성공을 대신하지 않는다.

`./gradlew --no-daemon --console=plain :shared:jvmTest` 중간 실행도 exit 0이다.
실제 XML은 40 cases/failure 0/error 0/skipped 0이며 locator/model, 기존 DB 보존, import/recovery,
schema migration, progress writer를 포함한다. `shared-tests-attempt-1-summary.json`과 해당 log에 기록했다.
이후 guard 회귀를 추가하므로 이 count는 최종 CI count가 아니다.

### 전체 CI 중간 시도와 고정 source 최종 실행 대기

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-1 ./scripts/ci-check.sh`는
exit 1/FAIL이다. 실제 Android unit 36/shared 43 cases의 failure/error/skipped는 0이지만 lint의
`UsableSpace`, `UseKtx` 2 errors가 전체 검증을 실패시켰다. 20 Hint는 고정 dependency update 안내다.
로그/XML/lint/hash는 `local-ci-attempt-1/summary.json`, `reports/`, console log에 보존했다.
`UseKtx`는 실제 `String.toUri` API로 수정했다. `UsableSpace`는 conservative free-space preflight의 작은 helper에만
`@SuppressLint("UsableSpace")`를 적용했다. 이 검사에는 지금 이용 가능한 free bytes만 사용하고 clearable cache를 포함한
StorageManager allocatable capacity, reclaim/allocation은 사용하지 않는 정책적 이유가 있다. 모든 lint를 끄거나
ignoreFailures로 실패를 숨긴 변경이 아니다. suppression의 범위와 이유는 해당 helper에 주석으로 남겼다.

같은 evidence dir 형식의 `local-ci-attempt-2` 전체 실행은 exit 0이다. Android unit 37/shared 43 cases,
failure/error/skipped 0, lint 20 Hint/error·warning 0, 앱/test APK와 schema v2 검사가 PASS다.
하지만 실행 중 `TxtCanonicalCache.kt`, `TxtReaderControllerTest.kt`가 SHA corruption 회귀 수정으로 변경되었다.
`local-ci-attempt-2/summary.json`에 source drift 2 files를 기록했으므로 최종 source 검증으로 사용하지 않는다.

변경을 고정한 `BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-3 ./scripts/ci-check.sh`
최종 실행을 기다린다. 시작 source hash는 `local-ci-attempt-3-source-hashes.json`에 있으며 실제 완료/변경 없는지를
확인한 뒤 최종 로컬 결과를 기록한다. 원격 ordinary Android 23 cases 및 host 2 stages는 아직 NOT_RUN이다.
host XML은 실제 adb instrumentation 결과/PID/force-stop proof를 검증한 helper 결과이고 ordinary AGP XML과 구분한다.
새 debug APK의 서명 인증서는 과거 local/CI signer가 다른 사실과 함께 검증하며 기존 앱 삭제/데이터 지우기로
서명 충돌을 해결하지 않는다. 최종 artifact hash·signer·source identity는 결과 확인 후 추가한다.

### 고정 source 최종 로컬 CI PASS — 기기/외부 종료 대기

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-3 ./scripts/ci-check.sh`는
최종 exit 0/PASS다. 실제 Android unit 37/shared 43 cases의 failure/error/skipped는 모두 0이다.
lint는 informational Hint 20건만 남았고 warning/error는 0이다. 앱/test APK compile·assemble 및
Room v1 보존/v2 export·실제 migration/DB 회귀를 확인했다. 모든 단계의 exact command/exit/log와
native XML은 `local-ci-attempt-3/results.txt`, 각 log/`reports/`, `summary.json`에 보존했다.

시작/종료 source/config/fixture hash drift는 0이며 `local-ci-attempt-3-source-hashes.json` SHA-256은
`13a7a332f8f01f2f6b4ba1d32c4cbe74e765262f5d8ef98b84c4f266d3365f4d`다.
로컬 debug 앱 APK SHA-256은 `c35e01f01d08b44747c389d40665f799bd1f15ff70bff899f3cee945ed155720`,
test APK는 `8fcb30f2b3e1ec225458c7d8a9f43361f06a40aae6cac2527ed911ea68d37f54`다.
이 APK hash는 로컬 artifact의 identity이며 이후 원격 APK와 동일 바이트라고 주장하지 않는다.
실제 Android ordinary 23 cases/host-driven 2 stages와 CP949 대상 기기 지원은 아직 NOT_RUN이다.
M02/M03을 이 로컬 PASS만으로 DONE으로 올리지 않는다.

검증한 production/test/build source는 `fdd9ac15182af4e4c1a21a814c5d9c2aa3393808`로 commit/push되어
원격 ordinary Android/host 검증을 기다린다. 이 이후 documentation/evidence 정리는 검증한 앱 source를 바꾸지 않는다.
세션 제공 로컬 APK는 `/workspace/bookreader-mobile-0.2.0-debug.apk`이며 위 `c35e01f0...` hash를 사용한다.
로컬 signer는 이전 local M00과 같고 과거 CI signer와 다르다. 기존 CI 설치본에 대한 데이터 유지 업데이트를
보장하지 않으며 앱 삭제/데이터 지우기 대안을 제시하지 않는다. GitHub release는 요청·생성하지 않았다.
`local-ci-attempt-3/final-local-apk.json`의 실제 aapt/apksigner 명령은 각각 exit 0이다. versionName `0.2.0`, versionCode 2,
17,023,395 bytes, signer SHA-256 `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 확인했다.
같은 source의 원격 build `37167947509`, Android device `37167947545` 실행이 진행 중이다. 아직 성공으로 기록하지 않는다.

### 리뷰에서 명시한 취소·flush 한계

provider stall/cancel 검사는 앱이 소유한 pipe/read와 CancellationSignal/stream close 경로를 검증한다.
비협조적인 원격 Binder/provider 호출이 반드시 앱의 시간 한도 안에 중단된다는 주장은 하지 않는다.
또한 Reader REFLOWING 중 close/ON_STOP은 마지막 commit을 보호하지만 reflow 직전 미커밋 이동까지 flush하지 못할 수 있다.
M04의 flush barrier 잔여 사항이며, 현재 host 종료 검증도 마지막 acknowledged commit 보존을 검사한다.

### 동일 source 원격 build PASS — device/host 대기

Source `fdd9ac15182af4e4c1a21a814c5d9c2aa3393808`의 원격 build
[37167947509](https://github.com/LaceyLuv/BookreaderM/actions/runs/37167947509)는 success다.
job `111334760116`, artifact `11290761635`의 실제 native XML·CI log를 다운로드하여 Android unit 37/shared 43,
failure/error/skipped 모두 0을 확인했다. lint는 20 Hint이며 schema 1/2 export는 local과 byte-for-byte 일치한다.
`remote-fdd9ac1-build/workflow.json`, `summary.json`, 각 verified log와 reports/actual CI log에 증거를 기록했다.
원격 APK의 byte hash/인증서를 local과 별도로 검증했으며 원격 원본 artifact는 Git 외부에 보존한다.
actual Android ordinary 23 cases 및 host 2 stages는 아직 결과 대기이며 원격 build 성공으로 대신하지 않는다.

### 기기 검증 lifecycle option 교정 — 앱/APK 변경 없음

AGP 8.11.1 공식 source에서 connected 테스트 후 앱/test APK 유지 option을 확인하여 검증 스크립트에
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`를 적용했다.
근거는 `agp-test-lifecycle-api.json`이며 script-only commit `d8e4ec9`로 기록·게시했다.
이 수정은 다음 host 검사에 필요한 설치 상태를 유지하는 검증 lifecycle 교정이다.
검증한 production/test source 및 제공 로컬 APK를 바꾸지 않는다. 기존 `fdd9ac1` device 실행은 아직 진행 중이며
그 실행의 host 실패를 관찰했다고 기록하지 않는다. 실제 실행 결과는 별도 확인 후 추가한다.

### 최초 실제 Android 실행 FAIL — ICU EUC-KR 차이 수정

이후 원래 source `fdd9ac1`의 device run `37167947545`가 실제 FAIL로 끝났다. 다운로드한 native connected XML은
23 cases/14 failures/0 errors/0 skipped다. 실제 failure/log와 workflow identity는 `remote-fdd9ac1-device/`에 보존했다.
`AndroidTxtEncodingTest.androidCp949ExtensionFixtureIsDistinctFromEucKr`는 Android ICU의 EUC-KR alias가 CP949의
U+AC02=`81 41` 바이트를 받아들여 `EUC_KR must reject CP949 extension bytes` assertion이 실패했다.
host JDK 13-case PASS를 Android 지원 판정으로 확대하지 않은 이유가 실제로 확인되었다.
다른 provider/picker fixture failures는 해당 owner가 별도 조사·수정한다. host 2 stages는 아직 NOT_RUN이다.

`TxtDecoder`의 EUC-KR 경로에서 vendor alias decode 전에 표준 ASCII 또는 양쪽 A1–FE byte pair grammar를
검사한다. prefix/read 사이 lead carry, 완료 EOF의 dangling lead, malformed pair의 source byte offset을 보존한다.
자동 선택 순서, encodingId, normalizationVersion, 원본 관리 바이트를 바꾸지 않았다. 기존 Android 3 assertions를 유지하고
unit `eucKrGrammarCarriesPairedBytesAndRejectsCp949ExtensionRangesAtExactOffsets` 1개를 추가했다.

현재 source의 isolated compile/JUnit은 `encoding-icu-fix-isolated/run.py` exit 0, 14 cases/failure 0다.
처음 새 실행에 기존 isolated output dir를 재사용했으므로 새 14-case proof를 위 새 디렉터리에 분리했다.
이전 `encoding-isolated/`는 immutable Git `fdd9ac1` source를 실제 재실행한 13-case 역사 proof로 명시했다.
새 rerun을 원래 실행 log처럼 제시하지 않으며 최초 전체 native CI 13-case XML과 첫 기기 FAIL artifact는 그대로 보존한다.
fixture 실제 bytes/hash 11개는 변하지 않았다. 전체 38 Android unit/43 shared 및 원래 Android 23 cases/host 2 stages의
수정 source 통합 재검증은 결과 대기다. 테스트를 약화하여 EUC-KR/CP949 overlap을 허용하지 않는다.

### 수정 source `9eb8f78` 최종 로컬 CI PASS — 원격 재검증 대기

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-4 ./scripts/ci-check.sh`는
actual exit 0/PASS다. Android unit 38(encoding 14)/shared 43 cases의 failure/error/skipped는 모두 0이다.
lint는 20 Hint이고 warning/error는 0이며 앱/test APK, Java instrumentation compile 및 schema 검사가 PASS다.
103 source/config/fixture 파일의 drift는 0이다. 실제 log/XML 및 summary는 `local-ci-attempt-4/`에 기록했다.
source fingerprint SHA-256은 `04cf3d59249644f9b3a44ac8dbcfd2d1a1feb76f49edc3f05ba045c6f6081e19`다.

strict EUC-KR byte grammar, provider fixture Java 변환 및 DocumentsUI 선택 경로를 수정한 source는
`9eb8f782d996fea9c0f04006e39fa72d9a3979ff`로 commit/push되어 실제 Android 재검증을 기다린다.
제공 로컬 APK `/workspace/bookreader-mobile-0.2.0-debug.apk`는 새 파일로 교체했고 SHA-256은
`2129f9c94914c1385f4cc5cbc900f51a851e4cffa8c8460a4f92d17bc0a32d10`이다.
test APK는 `f2f5d53b053c92d45ae9d69618b68f2cc8da2286a7a000d28e48aef537447ab1`이다.
최신 `final-local-apk.json`은 이 `9eb8f78` 후보의 size/version/hash/동일 local signer를 기록한다.
과거 `fdd9ac1` APK와 최초 device 실패는 앞선 각 source의 기록으로 남긴다. 새 실제 Android 23 cases/host 2 stages는 아직 NOT_RUN이다.

### 수정 source 첫 device worker 환경 차단

`9eb8f78`의 첫 원격 device worker는 KVM read/write permission 문제로 tests 시작 전에 exit 1이다.
이 결과는 BLOCKED_ENV이며 Android test assertions가 실패한 결과로 취급하지 않는다.
같은 commit을 새 worker에서 다시 실행하며 checks/테스트/환경 사전 조건을 약화하지 않는다.
실제 ordinary 23 cases/host 2 stages는 아직 NOT_RUN이다. 이전 `d8e4ec9`는 script-only 교정이라 앱 source가
옛 decoder/provider fixture와 동일하며 그 실패의 native archive도 별도로 보존한다.

후속 문서 commit에 `fixtures/encoding/README.md` 설명이 포함되면 `fixtures/**` CI trigger가 다시 실행될 수 있다.
이 파일의 prose 변경은 실제 fixture bytes/runtime/test/build source를 바꾸지 않는다. 최종 source/evidence 비교는
앱 source 동등성과 각 실행 commit을 구분하여 기록한다.

### 두 번째 KVM 차단과 workflow synchronization 교정

같은 `9eb8f78` source의 두 번째 device worker도 KVM permission 사전 검사에서 BLOCKED_ENV다.
두 worker 모두 실제 Android tests/host stages를 실행하지 않았다. source regression 실패와 구분한다.
공식 bounded `udevadm settle` 동작을 확인하여 workflow-only commit
`f3d4c38e2bbe2bb50c98d5da4e5dfaaca0376c26`로 queue synchronization을 추가했다.
근거는 `udev-synchronization-api.json`이며 KVM read/write checks를 유지한다. queue synchronization이 원인이라는
판정이나 문제가 해결되었다는 주장은 새 실제 실행 결과를 확인하기 전까지 하지 않는다.
앱/test source 및 로컬 제공 APK는 `9eb8f78`과 동일하다. workflow 변경 후 새 기기 실행을 기다린다.

### 수정 source 원격 build PASS / 새 KVM setup PASS

Source `9eb8f782d996fea9c0f04006e39fa72d9a3979ff`의 원격 build
[37169215834](https://github.com/LaceyLuv/BookreaderM/actions/runs/37169215834)는 success다.
artifact `11290199967`(15,121,863 bytes)의 native XML/CI log를 다운로드하여 Android unit 38(encoding 14)/shared 43,
failure/error/skipped 모두 0을 확인했다. lint 20 Hint, schema 1/2의 local byte 일치도 PASS다.
actual evidence는 `remote-9eb8f78-build/summary.json`, `workflow.json`, native reports와 verified log에 기록한다.

Workflow-only `f3d4c38`의 새 device run `37169506738`는 실제 KVM setup read/write 검사에 PASS하여
Android instrumentation에 진입했다. 이는 environment preflight PASS이며 ordinary 23 cases/host 2 stages의
app correctness PASS가 아니다. 실제 테스트 종료/원본 artifact 검증을 기다린다. 앞선 환경 차단·기기 FAIL은 유지한다.

`d8e4ec9`의 실제 native device artifact도 `remote-d8e4ec9-device/summary.json`에 추가 보존했다.
이 script-only source는 기존 앱/fixture를 유지하며 observed ordinary 23 cases/14 failures/0 errors/0 skipped다.
ordinary gate에서 실패하여 host stages는 NOT_RUN이다. 이 과거 실행을 최신 수정 source의 결과로 취급하지 않는다.

### 실제 Android 인코딩 PASS / 관리 파일 승격·fixture 실패 교정

`f3d4c38` device run `37169506738`의 다운로드한 native XML은 ordinary 23 cases/11 failures/0 errors/0 skipped다.
`AndroidTxtEncodingTest`의 기존 3 assertions는 모두 PASS이며 Android ICU alias 앞의 strict EUC-KR grammar가
CP949 확장 바이트를 거부함을 실제 Android에서 확인했다. native ICU alias 자체가 거부한다고 주장하지 않는다.
실패 stack, focused logcat, workflow identity는 `remote-f3d4c38-device/summary.json`과 원본 reports에 보존했다.
ordinary gate 실패로 host 2 stages는 NOT_RUN이며 fixture screenshot은 수집하지 않았다.

관리 파일 승격의 `Os.link`는 실제 Android SELinux AVC denial 9건으로 실패했다. 공개
`Files.move(source, target)`의 기본 no-options 경로로 교정했다. process-wide managed mutex, private UUID 경로,
NOFOLLOW_LINKS regular source, 기존 target 부재, source/target parent 동일 `st_dev`를 확인하고 양쪽 parent를 fsync한다.
Android 13/16 libcore source의 no-replace guard → rename → EXDEV/EISDIR에 한정된 copy fallback을 확인했다.
이 관리 경로의 조건에서 fallback을 피하며 `REPLACE_EXISTING`이나 target 교체를 허용하는 `ATOMIC_MOVE`는 요청하지 않는다.
공식 source URL/hash/excerpt는 `files-move-api/`에 보존하고 ADR-0002를 실제 경로에 맞췄다.

외부 intent 처리의 불필요한 `setIntent`는 ActivityScenario가 추적하는 launch intent identity를 바꾸어
lifecycle cleanup을 무시하게 했다. 전달된 incoming intent 처리 흐름을 유지하면서 해당 호출을 제거했다.
asset segment fixture는 API 36의 read `openAssetFileDescriptor`가 typed dispatch를 거쳐 2-argument
`ContentProvider.openAssetFile`을 호출한다는 공식 source를 확인했다. fixture의 기존 cancellation 3-argument override만으로는
이 경로를 처리하지 못하므로 2-argument override를 추가하고 공통 helper에 위임했다. `asset-provider-api/`에 근거를 남긴다.
이 교정은 assertion을 약화하거나 실제 provider 테스트를 제거하는 변경이 아니다.

### 교정 source `cfe17f0` 로컬 CI5·최종 fixture compile PASS — 원격 대기

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-5 ./scripts/ci-check.sh`는 actual exit 0/PASS다.
Android unit 38/shared 43 cases의 failure/error/skipped는 모두 0이며 lint는 20 Hint, 양쪽 APK/schema 검사는 PASS다.
실행 시작/종료 source drift는 0이고 fingerprint SHA-256은
`1b66992ce15236e6acc0b52f1465bde9af2f7d18fd94e6c9e25c2bd5eead7949`다. native XML과 log는 `local-ci-attempt-5/`에 보존한다.

그 뒤 `TestAssetProvider.java` 1개만 바꾸었으며 production/Android unit/shared test/config input hash는 CI5와 동일하다.
`./gradlew --no-daemon --console=plain --stacktrace :androidApp:assembleDebugAndroidTest :androidApp:lintDebug`가
actual exit 0/PASS, lint 20 Hint이며 final fingerprint
`94d162eadf2669eb62f376efec97f077f2e0377baf1f3387a786da3bde391040`의 실행 중 drift도 0이다.
`local-asset-fixture-validation/summary.json`은 이 source 동등성과 실제 compile/assemble/lint 결과를 구분한다.
새 승격 성공/no-overwrite 회귀를 포함한 ordinary 필수 identity는 24개이며 host는 별도 2 stages다.

수정 source는 `cfe17f0b9d2efbc39d6817c5e9b1470539587e09`로 commit/push했다. 제공 로컬 APK
`/workspace/bookreader-mobile-0.2.0-debug.apk`의 SHA-256은
`63f4174da6012253a30ce37059e72b5d5c40ce955063cf7e7161dab4eb9ac65a`, 크기는 17,023,931 bytes다.
최종 test APK SHA-256은 `75fa96cba2497b6b76cd9ec4f7ba9f9badaef972d273c94b24649dd32a3d3608`이다.
`final-local-apk.json`에 실제 aapt/apksigner exit 0, versionName 0.2.0/versionCode 2와 이전 local M00과 동일한 signer를 기록했다.
과거 CI signer와 다르므로 기존 CI 설치본의 데이터 유지 업데이트를 보장하거나 데이터 삭제를 제안하지 않는다.
최신 source의 원격 build, actual ordinary Android 24 cases/host 2 stages는 아직 결과 대기다.
M02/M03은 IN_PROGRESS이며 실기기/OEM 성능·접근성, M04 미커밋 이동 flush, EPUB/Readium은 별도 잔여 gate다.

### `cfe17f0` 원격 build PASS / 실제 가져오기 PASS / Reader 관찰 실패

원격 build [37171679446](https://github.com/LaceyLuv/BookreaderM/actions/runs/37171679446)의 실제 native XML은
Android unit 38/shared 43 cases, failure/error/skipped 모두 0이다. lint 20 Hint, schema 1/2 local 일치와 APK 서명을 확인했고
`remote-cfe17f0-build/summary.json` 및 원본 reports에 보존했다.
실제 device [37171679395](https://github.com/LaceyLuv/BookreaderM/actions/runs/37171679395)는 native ordinary
24 cases/1 failure/0 errors/0 skipped다. 가져오기 13 cases(system picker·VIEW·SEND·manual·asset segment·승격 guard 포함)와
encoding 3 cases는 모두 PASS다. 새 Files.move 경로의 실제 성공/no-overwrite를 확인했다.

단독 실패는 `TxtReaderFlowTest.measuredCanonicalLineIsSavedAndContinueRestoresAfterCacheLossAndRecreation`의
fixture `saved(book)` read가 repository DATABASE_UNAVAILABLE로 매핑된 것이다. 원래 내부 exception을 로그에 남기지 않아
Room initialization contention/SQLITE_BUSY는 아직 가설이며 production DB 결함이나 원인 해결을 단정하지 않는다.
`remote-cfe17f0-device/summary.json`에 exact method/stack과 native XML을 보존했다. ordinary gate 실패로 host는 NOT_RUN이고
최종 screenshot pull은 NOT_COLLECTED다.

### test-only DB observer 수명·진단 교정 — 재검증 대기

`3ce33868503bac2dd0ecd3829a7bf1a77d562f90`는 Flow/host fixture와 raw DAO 진단 helper 3 test files만 변경했다.
각 Flow/host stage의 행동 전에 DB observer 하나를 열고 stage 종료까지 유지한다. Error를 Missing으로 바꾸거나 retry하지 않는다.
Error 발생 시 raw DAO 진단을 첨부하고 실패시키며 측정 행·durable commit·복원 assertions와 production writer는 유지한다.
Astra는 이 observer 소유권과 오류 보존을 검토했다. 실제 DB error 원인 조사와 M04 concurrency 검증은 follow-up이다.

`./gradlew --no-daemon --console=plain --stacktrace :androidApp:assembleDebugAndroidTest :androidApp:lintDebug`는
actual exit 0/PASS, lint 20 Hint다. `local-reader-observer-validation/summary.json`은 104-file drift 0과
fingerprint `a0cc88b0119f7b5db7df90c0f46d08e110cb27919fa9b89bf97840c1217136e3`를 기록한다.
production/Android unit/shared/config input hash는 CI5와 같아 실제 38/43 proof를 유지한다.
test APK SHA-256은 `b3c07f6b39187777f686d34693122e6e30b7ce37f168353aa772df4532c2e3c4`이며
제공 앱 APK SHA-256은 이전과 같은 `63f4174da6012253a30ce37059e72b5d5c40ce955063cf7e7161dab4eb9ac65a`다.
새 source의 full remote build 및 actual ordinary 24/host 2는 아직 결과 대기다. M02/M03 IN_PROGRESS를 유지한다.


## 2026-10-09 — TXT 내부 알파 재개 기준과 Room 소유권 교정

HEAD/remote main은 `3ce33868503bac2dd0ecd3829a7bf1a77d562f90`로 확인했고 10월 4일 미커밋 docs/evidence와
`ManagedImportAndroidTest` 진단 변경을 보존했다. 원문 계획/첨부 template은 변경하지 않는다.
`resume-2026-10-09-baseline.json`은 재개 시 환경/실행 상태를 기록한다. 기존 Temurin 17.0.16+8,
SDK API36/build-tools 35.0.0/Gradle wrapper 8.13 및 상속된 proxy TLS 옵션·`/workspace/toolchains/cacerts`를 유지했다.
`./scripts/doctor.sh`는 exit 0/PASS이고 local adb 연결 기기는 0이다. 이 baseline 시점의 새 Gradle 실행은 NOT_RUN이었다.
네트워크 정책은 unrestricted 상태를 추정하지 않았고 키/토큰/인증서 파일을 evidence에 복사하지 않았다.

### 재개 전 최신 실제 원격 결과

원격 build [37172491541](https://github.com/LaceyLuv/BookreaderM/actions/runs/37172491541)는 PASS다.
`remote-3ce3386-build/summary.json`과 native XML은 Android unit 38/shared JVM 43 cases,
failure/error/skipped 모두 0, lint 20 Hint 및 committed schema 1/2 bytes 일치를 기록한다.
actual device [37172491508](https://github.com/LaceyLuv/BookreaderM/actions/runs/37172491508)는 ordinary 24 cases/1 failure/0 errors/0 skips다.
`remote-3ce3386-device/summary.json`과 원본 XML/log는 유일한 실패
`ManagedImportAndroidTest.externalViewReceivesTemporaryGrantAndOpensActualReader`를 보존한다.
import Idle/library Content인 상태에서 첫 reader_ready 이전 readerPhase ERROR다. 실제 내부 exception은 당시 진단에 없다.
이전 `cfe17f0` 실패였던 measured canonical line 저장/cache loss/Continue/recreation과 missing managed copy flow는
둘 다 PASS이며 Android encoding 3도 PASS다. 과거 가져오기 13 PASS와 구분하여 최신 외부 VIEW failure를 기록한다.
ordinary mandatory gate 실패로 host 2 stages는 NOT_RUN, 최종 fixture screenshot pull은 NOT_COLLECTED다.
Room 초기화 contention/SQLITE_BUSY는 미확인 가설이며 원인 해결을 주장하지 않는다.

### 재개 working tree 변경과 잔여 gate

서재·import/recovery/delete·Reader progress access가 synchronized `AndroidDatabaseOwner`의 process-lifetime
Room instance/pool을 공유하고 borrower가 닫지 않도록 소유권을 명시했다. fresh `createAndroidDatabase`는
독립 테스트의 caller-owned 경로로 남는다. repository/Reader는 fixed 단계·기존 오류 코드·최대 4개/64자 클래스 chain의
sanitized snapshot을 제공한다. private 책 본문/URI/path/SQL/예외 메시지·stack trace를 production 진단에 담지 않는다.
Error/Missing·cancellation·조회/복원 후 저장 gate·epoch/sequence 조건부 commit과 실패 보존은 유지한다.
세부 선택과 불확실성은 ADR-0002/0004에 남긴다. 이 변경만으로 최신 외부 VIEW 실패 해결을 선언하지 않는다.

재개 후 full `ci-check`(assemble/lint/unit/shared/schema 및 양쪽 APK), actual ordinary Android 필수 전체,
별도 host force-stop 2 stages와 자체 fixture PNG 4개를 실제 실행하여 결과를 기다린다. 현재 상태는 M02/M03 IN_PROGRESS다.
M04 전체 SAFE/race/60초 주기 저장·REFLOWING 미커밋 flush, physical 성능/OEM/TalkBack/16KB,
EPUB/Readium M01은 미완료다. TXT 내부 알파 `0.2.0`을 세 포맷 최종 v1.0으로 표시하지 않는다.

재개 시점 제공 `/workspace/bookreader-mobile-0.2.0-debug.apk`는 이전 `cfe17f0` 앱 source로,
SHA-256 `63f4174da6012253a30ce37059e72b5d5c40ce955063cf7e7161dab4eb9ac65a`이며 새 미빌드 Room/진단 변경을 포함하지 않는다.
새 빌드 후 실제 version/size/hash/signature를 다시 기록해야 한다. 기존 local signer
`aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 유지하며 CI의 독립 debug signer와 구분한다.
서명이 다른 설치본에 대한 데이터 유지 업데이트를 보장하거나 uninstall/data clear를 안내하지 않는다.

소스 승인·고정 뒤 full CI6 실행을 시작했다. 새 필수 검사는 Android unit 41/shared JVM 45,
ordinary Android 25 및 별도 host 2 stages다. 종료 결과/실제 XML/source drift 확인 전 PASS로 기록하지 않는다.
`AndroidDatabaseOwnerTest`는 직접 생성한 DAO fixture에서 12 concurrent borrower의 동일 owner,
library lease 종료 후 3회 새 Reader 활성화/저장 및 독립 caller-owned observer를 검사한다.
실제 import borrower 수명은 강화한 외부 VIEW provider→import→Reader READY 회귀가 함께 담당한다.
owner 테스트 자체가 실제 import를 수행한다고 해석하지 않는다.


### `2be610a` full CI6 PASS 및 새 로컬 APK

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-6 ./scripts/ci-check.sh`는 actual exit 0/PASS다.
`local-ci-attempt-6/summary.json`, `results.txt`, log와 native XML은 Android unit 41/shared JVM 45 cases,
failure/error/skipped 모두 0, lint 20 Hint, app/test APK 및 generated Room schema 검사 PASS를 기록한다.
source fingerprint `85556d8e9ae941153761b22791bc34fdc71e11aaee890e5da474df9d8f31e3a0`의 실행 중 drift는 0이다.
같은 소스는 `2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf`로 commit/push했다.

제공 `/workspace/bookreader-mobile-0.2.0-debug.apk`를 새 로컬 빌드로 교체하고 복사본 실제 SHA-256을 확인했다.
SHA-256은 `021ec47de8311435f6becd0eb5b6e677ed22312b6ef3740b86d86b60d10a6e5e`, 17,027,692 bytes다.
`final-local-apk.json`의 실제 aapt/apksigner exit는 0이며 versionName `0.2.0`/versionCode 2와 이전 local M00과 동일한
signer `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 확인했다.
이전 APK는 `/workspace/bookreader-mobile-0.2.0-debug-before-2be610a.apk`, 이전 metadata는
`final-local-apk-before-2be610a.json`에 보존했다. 새 APK는 Room 공유/진단 변경을 포함하지만 actual 기기 gate PASS는 아직 아니다.
새 원격 build, ordinary Android 25/별도 host force-stop 2 stages/자체 fixture PNG 4개 결과를 기다린다.
M02/M03 IN_PROGRESS와 M04/EPUB/실기기/OEM/16KB 잔여 gate를 유지하며 SQLite 오류 원인 확정이나 최종 v1 완료를 주장하지 않는다.


동일 source의 원격 build [37879616525](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616525)는 PASS다.
`remote-2be610a-build/summary.json`과 다운로드한 native reports는 Android unit 41/shared JVM 45,
failure/error/skipped 모두 0, lint 20 Hint 및 schema 1/2 local bytes 일치를 기록한다.
원격 app APK SHA-256 `00941a5d21ba9cd7a86c45cddf18a42bda5b422bbf6c7e94c867439273b8df06`의 apksigner exit는 0이고
CI signer `980adc346f1c476d4a98ef096b0782bbe153871296fb17d7b94f94ff6fee70d5`는 제공 local signer와 다르다.
원격 APK를 제공 local APK와 동일 바이트/서명이라고 주장하지 않는다.
기기 workflow `37879616383`는 success지만 actual ordinary/host proof/PNG 검증과 Astra 승인 전 milestone DONE은 보류한다.


### `2be610a` actual Android/host/fixture 최종 PASS — TXT 내부 알파 승인

실제 device [37879616383](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616383)의 source는
`2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf`다. native ordinary 25 cases의 failure/error/skipped는 모두 0이며,
외부 VIEW·실제 SAF/SEND·Android encoding 3·Room owner·Reader measured anchor/cache loss/Continue/recreation 회귀가 PASS다.
원격 artifact `11593602844`의 native XML은 `remote-2be610a-device/native-connected/`, 요약과 workflow metadata는
`summary.json`/`workflow.json`, 실제 검증 출력은 `android-device-verified.log`/`android-host-verified.log`에 보존한다.

별도 host `prepareDurableReader`/`verifyColdLibraryContinue`는 exact raw instrumentation start/pass/finish 및 2-stage manifest PASS다.
외부 adb가 실제 살아 있는 PID 5624를 force-stop했고 이후 PID absent를 확인한 뒤 cold launcher PID 5671을 확보했다.
마지막 commit UTF-16 offset 11607/sessionEpoch 1/sequence 2를 서재 하단 Continue가 복원했다.
raw stage/PID/committed proof 및 helper XML은 `remote-2be610a-device/host-process-proof/`, 실제 명령/exit 기록은
`remote-2be610a-device/command-evidence/`에 보존한다. 모든 stage/외부 종료/launch 명령 exit는 0이다.
기기 검증 명령은 `./scripts/device-check.sh`, host는 `python3 scripts/reader-process-check.py build/device-evidence/reader-host-process` 계약의
실제 순서로 수행했다. verification instrumentation은 또 다른 target process를 시작할 수 있으며 미커밋 화면 위치 보존은 증명하지 않는다.

`remote-2be610a-device/fixture-screenshots/`의 `m02-library.png`, `m03-reader.png`,
`txt-reader-generated-scroll.png`, `txt-reader-generated-host.png`는 실제 PNG 1080×1920 decode 및 SHA-256 검증 PASS다.
각 hash/bytes는 `summary.json`에 기록하고 root가 4개 화면도 확인했다. 사용자의 개인 책 대신 자체 작성 fixture만 캡처했다.
로컬 CI6 native XML/manifest와 원격 build native XML/schema도 ignore되지 않는 `native-*` 경로에 보존했다.

Astra가 실제 source/필수 identity/host PID 종료 proof/raw stage/PNG를 독립 검토하여 좁은 TXT 내부 알파에 blocker가 없음을 승인했다.
M02/M03을 TXT 내부 알파 범위 DONE으로 변경하고 M04는 입증된 force-stop criterion만 체크하며 IN_PROGRESS를 유지한다.
M01 Readium/EPUB, M04 전체 SAFE/race/A-B rapid reopen/60초 저장·REFLOWING 미커밋 flush,
physical 성능/OEM/TalkBack/16KB 및 TXT 페이지/본문 검색/북마크·Comic은 후속이다.
Room 소유권 변경 후 회귀 PASS와 이전 SQLITE_BUSY 내부 원인 입증은 구분한다. 원래 내부 원인은 여전히 미확인이다.

제공 local debug APK는 같은 검증 source의 빌드이며 hash `021ec47de8311435f6becd0eb5b6e677ed22312b6ef3740b86d86b60d10a6e5e`,
0.2.0/versionCode 2, signer `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`다.
기기 job은 설치 APK hash를 보존하지 않으므로 이 local APK와 actual device 설치본의 byte 동일성을 주장하지 않는다.
CI 독립 debug signer와 구분하며 서명이 다른 기존 설치본을 uninstall/data clear하도록 안내하지 않는다.
최종 제품 경로는 `TXT 추가`→picker→관리 복사본→`읽기`; 외부 VIEW는 import 후 바로 Reader,
SEND는 확인→commit→`읽기`다. 새 프로세스는 서재부터 시작한다. 앱은 TXT 내부 알파이지 최종 세 포맷 v1.0이 아니다.
