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
