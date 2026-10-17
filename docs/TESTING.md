# 모바일 검증 계약

## 2026-10-17 EPUB probe·M07 후속·archive journal

main `4019766`까지 구현했다. [EPUB probe](M01_EPUB_PROBE.md)의 API36 EPUB 3개는 PASS지만
전체 device suite는 `6b59093` 2 FAIL/`46da7fa` 1 FAIL로 보존했다. host stage는 NOT_RUN이다.
창 설정 소유권(`b7a1898`)과 메뉴 테스트의 이전 fling baseline(`3b48430`)을 보완했고 새 원격 검증 중이다.
[archive journal](ARCHIVE_IMPORT_JOURNAL.md)은 schema 5/포맷별 한도/취소·중단 복구를 구현했다.
local full CI Android 60/shared 71/EPUB 8 PASS이며 archive 제품 UI 연결은 아직 없다.
남은 전체 계획 진행 요청은 계속 유효하다. 기존 M04–M07 전체·실기기·TalkBack·출시 gate를 자동 완료하지 않는다.


## M06 실제 검증 — 2026-10-17

`f208a06` main에서 local/remote unit 55/shared 67, remote ENOSPC 3, API36 ordinary 38/host 2 PASS다.
검색·북마크·해석 전환 및 M05 추가 설정/OS 배율·밀도 회귀를 실행했다.
[보고서](M06_READER_TOOLS.md), [summary](evidence/m06-validation/summary.json)가 최신 근거다.
M07은 `/workspace/bookreader-mobile-m07`에서 구현·검증 중이다. 남은 전체 계획 진행 요청은 계속 유효하다.
이전 절의 M06 미반영/미실행 안내보다 이 절을 우선한다. 전체 M04–M06와 출시 gate를 자동 완료로 올리지 않는다.


## 2026-10-17 M05 페이지·설정 핵심 검증 및 M06 착수

source `1349ce8ee502686bd217373257e96432a7ff0204`에서 local/remote unit 52/shared JVM 57,
remote ENOSPC 3, actual API36 ordinary 35/외부 host 2 stages 모두 PASS다.
Room schema 3의 전역/책별 설정, bounded 실제 측정 페이지, mode/size reflow를 구현했다.
`6552d56` local full CI의 간헐적 restoration timeout을 보존하고 READY/anchor 게시를 보완했다.
[보고서](M05_PAGINATION.md), [summary](evidence/m05-validation/summary.json)가 근거다.

M05는 IN_PROGRESS다. 글꼴/행간/자간/여백 및 실제 OS 배율·밀도 조합과 안정된 첫 페이지 PNG를 후속 검증한다.
M06은 `/workspace/bookreader-mobile-m06`의 `codex/m06-tools`에서 구현 중이며 아직 main 기능이 아니다.
검색·북마크(schema 4)·수동 인코딩 전환을 연결했고, 실제 UI 및 SAFE-01 bookmark 불변 회귀를 준비했다.
사용자 목표는 남은 계획 전체 진행이다. M04 전체와 실기기/출시 gate를 완료로 올리지 않고 계속 진행한다.


## M04 실제 UI 경쟁·취소 — 2026-10-17

source `85653ada7259c9c80329ca759e5fc43df870cfff`에서 local/remote unit 50/shared JVM 53,
remote 실제 ENOSPC 3, actual API36 ordinary 33/외부 host 2 stages 모두 PASS다.
취소 전 flush 대기와 Room DB Job의 연결 대기를 분리해 수정했다. A/B 6회(역방향 2회),
복원 중 close/reopen 5회, 실제 회전/ON_STOP의 pending anchor, 32MiB decode 중 삭제를 검증했다.
`14fb88c` device 1 FAIL/host NOT_RUN과 수정 전 unit timeout은 보존했다.
[범위 보고서](M04_UI_RACES.md), [summary](evidence/m04-ui-races-validation/summary.json),
[정확 명령](evidence/m04-ui-races-validation/commands.json)이 최신 근거다.

M04는 IN_PROGRESS다. M05 설정 reflow, M06 bookmark 불변, 실제 Android storage/fsync,
실기기 성능 및 신규 Astra 검토는 미완료다. 사용자의 남은 계획 전체 진행 요청에 따라 M05 구현을 이어간다.
로컬 기기는 없어 exit 78/BLOCKED_ENV이며 원격 emulator 결과와 구분한다.


2026-10-17 갱신. 결과는 `PASS / FAIL / NOT_RUN / BLOCKED_ENV`로 구분한다.
테스트 파일의 존재, 환경 검사 성공, Activity 재생성은 각각 실제 테스트 통과나 프로세스 종료 검증을 대신하지 않는다.

## M04 SAFE-01 실제 Reader 조회 오류·재시도 — 2026-10-17

source `0e2605e9a21be7fc72cfa6a72e36e89de406fe3d`, 검증 입력 112개 fingerprint
`c04c2b9d4a5500a1e6fe63d4e3629f8f721d230b22ccb3d7d91a1ed9e84db4b8`, source drift 0이다.
[summary](evidence/m04-read-error-validation/summary.json), [범위 보고서](M04_PROGRESS_READ_ERRORS.md)를 따른다.
production 코드/schema/migration은 바뀌지 않았으며 README를 보존했다.

| 검사 | 판정 | 증거/범위 |
|---|---|---|
| local test APK compile | exit 0/PASS | 새 실제 UI 검사 2개 컴파일 |
| full local CI | exit 0/PASS | Android unit 48/shared JVM 53, failure/error/skip 0, APK/lint/schema PASS |
| local device | exit 78/BLOCKED_ENV | authorized Android device 없음; 기기 PASS로 대체하지 않음 |
| remote build [39000328372](https://github.com/LaceyLuv/BookreaderM/actions/runs/39000328372) | PASS | native 48/53/ENOSPC 3, artifact `12088568841` |
| actual API36 [39000328613](https://github.com/LaceyLuv/BookreaderM/actions/runs/39000328613) | exit 0/PASS | ordinary 29/외부 host 2 stages, artifact `12088683868` |

정확 local 명령은 `source /workspace/toolchains/mobile-env.sh` 뒤 실행했다.

```bash
./gradlew --no-daemon --console=plain :androidApp:assembleDebugAndroidTest
BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-read-error-validation/local-ci ./scripts/ci-check.sh
BOOKREADER_DEVICE_EVIDENCE_DIR=docs/evidence/m04-read-error-validation/local-device ./scripts/device-check.sh
bash -n scripts/device-check.sh
```

기기 명령만 exit 78이며 나머지는 exit 0이다. [commands.json](evidence/m04-read-error-validation/commands.json),
local/native XML, 원격 artifact/server digest와 ZIP SHA-256, command results 및 host raw proof를 보존했다.
local CI 48/53은 `199e9b9` 입력으로 실행했다. 후속 변경은 Android test와 device-check의 예외 타입 검사 각 1줄뿐이며 unit/shared/production 입력은 동일하다. 최종 test APK 재컴파일도 exit 0이고, 최종 원격은 위 commit 전체를 checkout했다.

첫 `199e9b9` device run 38999665795는 ordinary 29 중 1 FAIL, host NOT_RUN이었다.
native probe의 SQLite code 1과 오류 단계 검사는 통과했지만 test line 488이 런타임 클래스 이름을
`SQLiteException`으로 고정해 실패했다. 공식 2.6.1 Android source의 실제 별칭은
`SQLiteException = android.database.SQLException`이다. native probe에서 얻은 클래스 이름과
Reader 진단을 비교하고 기기 report 검증도 Android의 `SQLException`을 기대하도록 수정했다.
오류 코드/기록 보존 검사는 완화하지 않았다. [원본 FAIL](evidence/m04-read-error-validation/remote-199e9b9-device-failed/summary.json)과
[공식 source 근거](evidence/m04-read-error-validation/sqlite-api-source.json)를 보존했다.

새 actual UI 검사 두 개:

- native DAO 조회를 열 이름의 가역적 변경으로 실패시켜 SQLite code **1**을 확인했다.
  `LOAD_PROGRESS / DATABASE_UNAVAILABLE / READ_PROGRESS`와 실제 오류/재시도 UI가 일치한다.
  기존 offset **5617**, epoch **1**, 모든 book/progress/read_order와 두 관리 파일을
  오류 및 실패한 재시도 2회 동안 보존했다. 복구 후 TextLayout 측정 **5617**,
  새 epoch **2**에서 사용자 swipe 위치 **7477** 저장 PASS다.
- malformed locator는 `INVALID_RECORD`로 표시하며 잘못된 원본 JSON 자체를 오류 동안 보존했다.
  테스트가 원래 JSON을 복원한 뒤 UI 재시도에서 offset **5805**로 돌아왔고,
  새 epoch **2**의 위치 **7727** 저장 PASS다. 앱의 자동 손상 복구를 뜻하지 않는다.

각 경로에서 복원 자체는 progress를 쓰지 않고 성공한 activation만 epoch/read_order를 한 번 올렸다.
다른 책 보존, 관리 파일 hash, fault 복원 및 실제 DAO 재조회까지 PASS다. fixture 본문/bytes/hash는
자체 생성 규칙으로 별도 재계산해 raw report와 대조했다. bookmark는 v2 schema/DAO/UI가 없어 NOT_RUN이다.

기존 회귀도 새 source에서 실행했다. 실제 연속 이동 60,002ms /
60 commits, 최대 관찰 공백 1,101ms다.
외부 host는 live PID 5794 → force-stop → absent → cold PID 5842 및
서재 Continue 복원을 확인했다. verification instrumentation은 별도 target process를 시작할 수 있다.
SQL write-ack/미커밋 화면 위치/전원 차단/실기기 성능 보증과 구분한다.

M04는 **IN_PROGRESS**다. 다음은 실제 UI A/B 및 같은 책 rapid close/reopen이다.
bookmark 불변, 삭제 중 decode/index, 실제 reflow/ON_STOP, Android 실제 storage ENOSPC/fsync,
실기기 성능 및 신규 Astra 검토도 NOT_RUN이다.

## M04 실제 ENOSPC 및 activation 중복 — 2026-10-11 최종

source `ebee7723eed8c7ca487736af52570ae1cfd3d4ba`, 검증 입력 111개 fingerprint
`112c4be06eba47de32ff750c02af7d909310a3bea3f7d3c33e560a528de669e5`, source drift 0이다.
[summary](evidence/m04-enospc-validation/summary.json), [범위 보고서](M04_FILESYSTEM_ENOSPC.md)를 따른다.

| 검사 | 판정 | 증거/범위 |
|---|---|---|
| activation 단위 수정 전 | exit 1/FAIL | callback 중복에서 activation expected 1 / actual 2; `activation-before-fix/` |
| 같은 회귀 수정 후 | exit 0/PASS | 정확히 1회 활성화·정상 epoch/offset 저장 |
| full local CI | exit 0/PASS | Android unit 48/shared JVM 53, failure/error/skip 0, APK/schema PASS |
| 실제 tmpfs ENOSPC local | exit 0/PASS | 별도 3개, 실제 errno 28·가용 공간 0·WAL FULL(13), record/file/retry/정리 |
| remote build [38129413116](https://github.com/LaceyLuv/BookreaderM/actions/runs/38129413116) | PASS | native 48/53/3, artifact `11698451773`, schema local bytes 일치 |
| actual API36 [38129413128](https://github.com/LaceyLuv/BookreaderM/actions/runs/38129413128) | exit 0/PASS | ordinary 27/host 2 stages, artifact `11698847810` |

정확 local 명령(`source /workspace/toolchains/mobile-env.sh` 이후):

```bash
./gradlew --no-daemon --console=plain :shared:tasks --all
./gradlew --no-daemon --console=plain :androidApp:testDebugUnitTest --tests org.bookreader.mobile.reader.TxtReaderControllerTest.restorationCallbackDuringPendingActivationStartsOnlyOneSession
./scripts/enospc-check.sh
BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-enospc-validation/final-local-ci ./scripts/ci-check.sh
bash -n scripts/enospc-check.sh
```

최종 명령은 모두 exit 0이다. 단위/ENOSPC local native는 `bacd79f`에서 실행했고, 이후 바뀐 검증 입력은 Android instrumentation test 한 파일뿐이다.
`ebee772`의 test APK compile도 exit 0이며 최종 remote는 모든 검사를 다시 실행했다.
첫 Gradle DSL/task discovery exit 1과 namespace root cache exit 1도 보존했다.
일반 `/workspace`를 지정한 filler는 실제 할당 전에 exit 1로 거부하고 파일을 만들지 않았다.
namespace가 없는 환경은 exit 78/BLOCKED_ENV이며 테스트 skip/PASS로 바꾸지 않는다.
원격 Ubuntu는 아래 명령으로 private mount namespace를 만든다. 전역 sysctl/AppArmor는 변경하지 않는다.

```bash
export GRADLE_USER_HOME="$HOME/.gradle"
sudo --preserve-env=HOME,JAVA_HOME,ANDROID_HOME,ANDROID_SDK_ROOT,PATH,GRADLE_USER_HOME ./scripts/enospc-check.sh
# API36 emulator의 별도 job
./scripts/device-check.sh
python3 scripts/check-test-results.py shared-enospc docs/evidence/m04-enospc-validation/remote-ebee772-build/native-shared-enospc
python3 scripts/check-test-results.py android-unit docs/evidence/m04-enospc-validation/remote-ebee772-build/native-android-unit
python3 scripts/check-test-results.py shared-jvm docs/evidence/m04-enospc-validation/remote-ebee772-build/native-shared-jvm
python3 scripts/check-test-results.py android-device docs/evidence/m04-enospc-validation/remote-ebee772-device/native-connected
python3 scripts/check-test-results.py android-host docs/evidence/m04-enospc-validation/remote-ebee772-device/host-process-proof
```

모두 exit 0이다. 실제 XML/JSON, exact raw host stage, workflow SHA, archive SHA-256/서버 digest를 확인했다.
host PID 5691→외부 force-stop→absent→cold 5739 및 마지막 commit offset 13285/epoch 1/sequence 3의
cold 서재 Continue 복원을 확인했다.
actual 60초 회귀는 60,067ms/60 commits,
최대 관찰 공백 1,050ms다. SQL write-ack/실기기 성능 수치로 확대하지 않는다.
기존 Reader page-limit FULL 오류 UI/기록·파일 보존/자동 retry도 PASS다. 이는 Android 실제 ENOSPC와 구분한다.

### 보존한 원격 실패

- `a3ee33b` build `38127759100`: 일반 47/53 PASS 후 uid_map 제한으로 ENOSPC exit 78/BLOCKED_ENV, 전용 실행 0개.
- `7f7c46b` build `38128112864`: 일반 47/53 PASS 후 root user namespace의 private checkout 접근 실패 exit 1, 전용 실행 0개.
- `a3ee33b` device `38127759101`: ordinary 27개 중 AndroidDatabaseOwnerTest 1개가 REJECTED_STALE로 FAIL. host NOT_RUN.
  원본만으로 정확한 스케줄 순서를 단정하지 않는다. 조사에서 callback/job 등록 사이 activation 중복을 별도 단위 재현했다.
- `bacd79f` build `38128654804`: 48/53/ENOSPC 3 PASS. device `38128654709`는 DB owner PASS지만 page-limit UI
  측정 53 vs stableOffset 6625로 1개 FAIL, host NOT_RUN이다. 오류 배너의 viewport 변경/reflow가 끝난 뒤
  독립 TextLayout 위치를 측정하도록 test 순서만 보완했다. expected를 controller 값으로 바꾸거나 assertion을 줄이지 않았다.

각 원본 XML/로그/summary는 `remote-a3ee33b-{build,device}`, `remote-7f7c46b-build`, `remote-bacd79f-{build,device}`에 있다.
최종 controller는 synchronized restored 진입과 LAZY job 등록 후 start를 사용하며 DB의 stale 거부 기준은 유지한다.
[commands.json](evidence/m04-enospc-validation/commands.json)에 초기/최종 명령과 exit를 연결했다.
**NOT_RUN:** Android ext4/f2fs ENOSPC, rollback journal 모드, fsync 장치 오류/전원 차단, full 상태 cold start,
SAFE-01 실제 DB 조회 오류/재시도 UI, 실기기 성능 및 신규 Astra 검토. M04는 IN_PROGRESS다.

## M04 SAFE-07 native SQLite FULL — 2026-10-11

clean main `685b6d2`에서 실제 엔진 용량 한도 오류 회귀를 추가했다. 최종 검증 source는
`bf7589dcc018c57fc43101a16198cc3afd12b9cd`, 입력 fingerprint
`504c4bb345f79b0f7f638bc0298c9d8eb7cadde96c81d0efefca73de7c4e0a00`, drift 0이다.
[summary](evidence/m04-sqlite-full-validation/summary.json), [범위 보고서](M04_SQLITE_FULL.md)를 따른다.

| 검사 | 결과 | 범위 |
|---|---|---|
| 새 실제 DB 3개 단독 | exit 0/PASS | update/insert/activation의 native code 13, 기록/다른 책/metadata rollback, reopen/retry |
| Android test APK | 수정 후 exit 0/PASS | 초기 잘못된 member import 컴파일 exit 1도 보존; 실제 UI 실행 아님 |
| full local CI | exit 0/PASS | Android unit 47/shared JVM 53, failures/errors/skips 0, APK/schema PASS, lint 20 Hint |
| remote build [38126287697](https://github.com/LaceyLuv/BookreaderM/actions/runs/38126287697) | PASS, native 47/53, 실패/오류/skip 0 | schema local bytes 일치; code 13 세 건 원본 XML 확인 |
| actual API36 [38126287699](https://github.com/LaceyLuv/BookreaderM/actions/runs/38126287699) | exit 0/PASS, native ordinary 27/host 2 stages | 실제 SQLite 오류 UI·기록/파일 보존·자동 retry·정리 PASS |
| OS filesystem ENOSPC 및 실기기 | NOT_RUN | native page-limit FULL과 구분 |

`source /workspace/toolchains/mobile-env.sh` 이후 정확 명령:

```bash
./scripts/doctor.sh
./gradlew --no-daemon --console=plain :shared:jvmTest --tests org.bookreader.mobile.repository.RoomProgressFullTest
./gradlew --no-daemon --console=plain :androidApp:assembleDebugAndroidTest
bash -n scripts/device-check.sh
BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-sqlite-full-validation/ci ./scripts/ci-check.sh
```

새 native 필수 identity는 shared 3개와 Android Reader 1개다. max_page_count 한도·테스트 전용 trigger·부가 table은
실제 writer의 transaction을 SQLITE_FULL로 실패시킨다. 수동 예외 throw/mock 결과 반환이 아니다.
Android는 앱 소유 writer 연결을 빌리고 finally에서 한도와 fixture schema를 복원하며 DB를 닫지 않는다.
새 `sqlite-full-report.log`는 native probe code 13/오류 표시/기록·파일 보존/자동 retry/정리 완료를 별도 확인한다.
connected 실패 때도 report를 수집하되 원래 exit를 보존한다. production 소스/schema 변경은 없다.

### `bf7589d` 실제 기기 증거

[기기 summary](evidence/m04-sqlite-full-validation/remote-bf7589d-device/summary.json)의 source SHA는 정확히 `bf7589d`다.
raw `sqlite-full-report.log`에서 한도 16 pages×4,096 bytes, 부가 할당 81,920 bytes,
native probe code 13/SQLException을 확인했다. 실제 저장 오류 UI가 나타난 동안 offset 4964/sequence 1과
다른 책/관리 파일 hash/읽기 순서를 보존했다. 한도 복원 후 추가 입력·flush 없이 pending 6625를 sequence 3으로
commit하고 saveError/diagnostic을 지웠다. 동일 trigger의 할당이 1행 성공하며 한도/schema 정리도 확인했다.
fixture bytes/hash를 독립 생성해 보고서와 일치함을 확인했다.

host raw는 정확한 두 stage start/pass/finish, command exit 0, PID 5794→외부 force-stop→absent→cold 5843,
마지막 commit offset 13285/epoch 1/sequence 3의 cold 서재 Continue 복원을 보인다.
기존 60초 회귀도 60,017ms/commit 60/최대 관찰 공백 1,067ms로 PASS다. OS/실기기 성능 측정으로 확대하지 않는다.

최종 추가 검증 명령(모두 exit 0):

```bash
source /workspace/toolchains/mobile-env.sh
BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-sqlite-full-validation/platform-ci ./scripts/ci-check.sh
# 원격 API36에서 같은 source로 실행
./scripts/device-check.sh
# 원격 artifact 보존 XML 확인
python3 scripts/check-test-results.py android-unit docs/evidence/m04-sqlite-full-validation/remote-bf7589d-build/native-android-unit
python3 scripts/check-test-results.py shared-jvm docs/evidence/m04-sqlite-full-validation/remote-bf7589d-build/native-shared-jvm
python3 scripts/check-test-results.py android-device docs/evidence/m04-sqlite-full-validation/remote-bf7589d-device/native-connected
python3 scripts/check-test-results.py android-host docs/evidence/m04-sqlite-full-validation/remote-bf7589d-device/host-process-proof
```

exact adb 명령/exit는 host-process-proof/summary.json, 각 raw .log와 wrapper results.txt에 있다.
실패 자료 및 취소한 이전 source는 보존한다. source SHA/archive hash/workflow 결과/native XML/JSON을 함께 검사했다.

## M04 SAFE-09 최종 결과 — source `73276fe`

`73276fe27da781dde807f05a85422ae18363642f`의 local/remote build와 actual device/host는 PASS다.
검증 fingerprint `0670f2c61c8c6522123f3bd0c7261172649eecdafe3679385946e409dfea561c`, 후속 문서 작성 시 drift 0이다.
[전체 summary](evidence/m04-continuous-validation/summary.json)와 [측정 보고서](M04_COMMIT_LATENCY.md)를 따른다.

| 수준 | 결과 | 근거 |
|---|---|---|
| local full CI | exit 0/PASS, Android unit 47/shared JVM 50 | host-ci/ 및 host-native-*; failures/errors/skips 0, lint 20 Hint, APK/schema PASS |
| remote build [38117532227](https://github.com/LaceyLuv/BookreaderM/actions/runs/38117532227) | PASS, native 47/50, failures/errors/skips 0 | remote-73276fe-build/ XML; schema local bytes 일치 |
| API36 ordinary [38117532253](https://github.com/LaceyLuv/BookreaderM/actions/runs/38117532253) | exit 0/PASS, native 26, failures/errors/skips 0 | remote-73276fe-device/native-connected/ |
| actual 60초 연속 touch | PASS: 60,001ms/입력 592/anchor 226/commit 46 | raw JSON, 최대 입력 공백 148ms, commit 관찰 공백 최대 2,034ms |
| actual external host | PASS, 2 stages | raw exact stage identity, PID 5672→absent→cold 5719, commit offset 13285/epoch 1/sequence 2 복원 |
| SQL write-ack/physical performance, 신규 Astra | NOT_RUN | emulator correctness·관찰 지표와 구분 |

실행 명령(모두 exit 0):

```bash
source /workspace/toolchains/mobile-env.sh
BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-continuous-validation/host-ci ./scripts/ci-check.sh
# 위 source로 원격 API36 job에서 실행
./scripts/device-check.sh
# 내려받은 실제 artifact의 보존 XML 재검증
python3 scripts/check-test-results.py android-unit docs/evidence/m04-continuous-validation/remote-73276fe-build/native-android-unit
python3 scripts/check-test-results.py shared-jvm docs/evidence/m04-continuous-validation/remote-73276fe-build/native-shared-jvm
python3 scripts/check-test-results.py android-device docs/evidence/m04-continuous-validation/remote-73276fe-device/native-connected
python3 scripts/check-test-results.py android-host docs/evidence/m04-continuous-validation/remote-73276fe-device/host-process-proof
```

host wrapper 내부의 정확 adb 명령/exit는 host-process-proof/summary.json, raw stage는 각 .log,
command exit는 results.txt에 있다. raw 두 stage의 start/pass/finish identity도 reader-process-check.verify_stage로 별도 확인했다.
JSON의 입력/commit 공백, 매 10초 이동/commit 존재, 증가 sequence를 원본에서 재계산했고 fixture bytes/hash도 독립 생성하여 일치했다.
workflow SHA/native identity/archive SHA-256는 각 remote summary에 보존했다. 모든 결과는 이 source에 한정한다.
아래 `78e45f5`/`14ce759` 실패 이력은 삭제하지 않는다. 전체 M04는 IN_PROGRESS다.

## M04 SAFE-09 연속 이동 — 2026-10-11

`0a66be5` clean main에서 60초 controller/실제 Reader 검증을 추가했다.
입력 fingerprint `d0bf070fa0e2cd318ef3946db01db752574a729be70dfff7a0dc584873d8ac4a`, drift 0이다.
[summary](evidence/m04-continuous-validation/summary.json)와 [측정 계약](M04_COMMIT_LATENCY.md)을 따른다.

| 실행 | 결과 | 범위 |
|---|---|---|
| controller 60초 단독 unit + Android test APK | PASS, exit 0 | 60초는 가상 시간; actual UI 실행 아님 |
| full local CI | PASS, exit 0 | Android unit 47/shared JVM 50, failures/errors/skips 0, APK/schema PASS, lint 20 Hint |
| remote build `38116198367`, source `78e45f5` | PASS, exit 0 | native Android unit 47/shared JVM 50, failures/errors/skips 0, schema local 일치 |
| `78e45f5` actual Android continuous touch | FAIL, exit 1 | ordinary 26 중 1 실패: 60,081ms/600 inputs이나 anchor 변화 0/commit 관찰 1 |
| `78e45f5` host | NOT_RUN | connected 실패로 후속 단계 미실행 |
| SQL write-ack latency 및 실기기 수치 | NOT_RUN | 관찰 간격과 구분 |

`source /workspace/toolchains/mobile-env.sh` 이후 정확 명령:

```bash
./scripts/doctor.sh
./gradlew --no-daemon --console=plain :androidApp:tasks --all
./gradlew --no-daemon --console=plain :androidApp:testDebugUnitTest --tests org.bookreader.mobile.reader.TxtReaderControllerTest.sixtySecondsOfUnsettledMovementSamplesEverySecondThenSettles :androidApp:assembleDebugAndroidTest
bash -n scripts/device-check.sh
BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-continuous-validation/ci ./scripts/ci-check.sh
```

위 명령은 모두 exit 0이다. 최종 CI에는 unit 정리 단계의 cancellation drain 보완과 device report 수집까지 포함한다.
새 native 필수 identity는 controller 60초 1개와 actual Reader 60초 1개다.
device-check는 실패한 기기 테스트 뒤에도 생성된 report 수집을 시도하며 원래 connected exit를 보존한다.
실제 테스트 통과 및 report status/기간/입력·commit 간격 검사 없이는 녹색으로 처리하지 않는다.

### 동일 fragment 내부 이동 누락 수정

`78e45f5`의 [원본 기기 실패](evidence/m04-continuous-validation/remote-78e45f5-device/summary.json)를 보존했다.
Compose Foundation 1.9.3 공식 source에서 LazyListMeasuredItem.offset의 제자리 변경과
LazyListMeasureResult의 visibleItemsInfo 재사용을 확인했다([URL/hash](evidence/m04-continuous-validation/foundation-api.json)).
Reader의 snapshotFlow가 같은 mutable item을 반환하여 작은 연속 이동의 emission을 억제했다.
first item의 index/offset을 immutable 값으로 캡처하도록 수정하며 60초 테스트/한도는 변경하지 않는다.
수정 후 로컬 full CI 및 실제 Android/host 결과는 아래 후속 증거로 확정한다.

## M04 DB 경쟁·host 준비 경로 — 2026-10-11 후속

`16e4615` 기준 working tree에서 실제 파일 Room DB 경쟁 5개와 import UI publisher-thread 회귀 1개를 추가했다.
검증 fingerprint `2cce4a46f213306e12cc9d54a6ec07f8f1d08991cc5445b7a7ac21acb5da31c1`, 입력 drift 0이다.
[summary](evidence/m04-race-validation/summary.json), native XML과 [SAFE matrix](M04_SAFETY_MATRIX.md)를 따른다.

| 실행 | 결과 | 근거 |
|---|---|---|
| 새 DB race 5개 단독 실행 | PASS, exit 0 | `targeted-tests.log`, `targeted-native-shared/`; 실제 DB close/open 포함 |
| UI publisher-thread 수정 전 회귀 | FAIL, exit 1 (결함 재현) | `worker-before-fix/`; worker에서 UI 게시 assertion 실패 |
| 수정 후 full local CI | PASS, exit 0 | Android unit 46/shared JVM 50, failures/errors/skips 0; APK 2개/schema PASS, lint 20 Hint |
| 이전 `16e4615` remote build `38109705786` | workflow success | 이전 source의 결과; 새 변경의 PASS로 사용하지 않음 |
| 이전 `16e4615` remote device `38109705802` | ordinary 25 PASS / host FAIL | native XML 직접 검증; prepare CalledFromWrongThreadException, force-stop/cold verify NOT_RUN |
| `96c15aa` remote build `38110471084` | PASS | native Android unit 46/shared JVM 50, failures/errors/skips 0; schema local 일치 |
| `96c15aa` remote device `38110471069` | PASS | native ordinary 25/실제 raw host 2 stages; 모두 성공, PID 5640→absent→5688 |

정확 실행 명령 (`source /workspace/toolchains/mobile-env.sh` 이후):

```bash
./scripts/doctor.sh
./gradlew --no-daemon --console=plain :shared:tasks --all
./gradlew --no-daemon --console=plain :shared:jvmTest --tests org.bookreader.mobile.repository.RoomProgressRaceTest
# production Main 게시 수정 전: exit 1
./gradlew --no-daemon --console=plain :androidApp:testDebugUnitTest --tests org.bookreader.mobile.ui.AppViewModelTest.workerImportProgressIsPublishedOnMainWithoutMovingIoWork
# 수정 후 exit 0, 내부 모든 명령/exit는 ci-check.log 및 ci/results.txt
BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-race-validation/ci ./scripts/ci-check.sh
python3 scripts/check-test-results.py android-device docs/evidence/m04-race-validation/previous-16e4615-device/native-connected
git diff --check
```

SAFE-02/03/04 및 06의 DB 저장 부분만 새 concurrency case로 검증했다. UI rapid close/reopen,
삭제 중 decode/index, 실제 disk-full, 60초 연속 이동/commit-latency, reflow/ON_STOP 통합과 신규 Astra 검토는 남아 있다.
새 actual host 원본에서 prepare와 cold verify의 exact start/pass/finish를 확인했다.
외부 adb가 살아 있는 PID 5640을 force-stop한 뒤 absent, cold launcher PID 5688을 확인했고,
마지막 commit offset 11607/epoch 1/sequence 2가 cold 서재 Continue로 복원됐다.
이전 worker UI 게시 실패 경로 수정 후 해당 host flow가 PASS임을 확인했다. 단일 실행으로 모든 timing 문제 제거를 주장하지 않는다.
증거는 `remote-96c15aa-device/native-connected/`, `host-process-proof/`, 각 native/host verifier log와 summary다.
이전 `16e4615` FAIL 원본은 `previous-16e4615-device/`에 그대로 있다.
원격 기기 명령은 `./scripts/device-check.sh`, host는 그 안에서
`python3 scripts/reader-process-check.py build/device-evidence/reader-host-process`로 실행됐고 wrapper exit는 0이다.
다운로드 후 아래 두 검증도 exit 0이다.

```bash
python3 scripts/check-test-results.py android-device docs/evidence/m04-race-validation/remote-96c15aa-device/native-connected
python3 scripts/check-test-results.py android-host docs/evidence/m04-race-validation/remote-96c15aa-device/host-process-proof
```

미커밋 화면 위치·실기기 latency는 검증 대상이 아니며, verification instrumentation은 또 다른 target process를 시작할 수 있다.

## M04 재배치 중 flush — 2026-10-11

기준 HEAD `03870c9c612694ca69528d0b5df7e44fa432a447`의 working tree에서 REFLOWING 중 미커밋 anchor 저장을 보완했다.
검증 입력 fingerprint는 `3cc61fc407e9a1441b862fba940e178dc849c22b0e240ef222487f35939b24cf`이며,
`source-before-ci.json`에 파일별 SHA-256을 기록했다. 실행 종료 후 입력 drift는 0이다.
[evidence summary](evidence/m04-reflow-validation/summary.json)와 native XML이 새 결과의 기준이다.

| 실행 | 결과 | 증거·한계 |
|---|---|---|
| `./scripts/doctor.sh` | PASS, exit 0 | Temurin 17.0.16+8, SDK 36/build-tools 35.0.0, Gradle 8.13 |
| 수정 전 신규 controller 테스트 4개 | FAIL, exit 1 (의도한 재현) | 3 failures/0 errors/0 skipped; `before-fix/gradle.log`, `before-fix/native-android-unit/` |
| `./scripts/ci-check.sh` | PASS, exit 0 | Android unit 45/shared JVM 45, failure/error/skipped 0; APK 2개, lint 20 Hint, Room schema PASS |
| `./scripts/device-check.sh` | BLOCKED_ENV, exit 78 | authorized device 0; `device/`, `device-check.log` |
| 실제 ON_STOP/설정 reflow·host force-stop·실기기 지연·Astra 신규 최종 검토 | NOT_RUN | 과거 기기/host PASS를 새 변경 결과로 재사용하지 않음 |

실행은 `source /workspace/toolchains/mobile-env.sh` 이후 아래 명령을 사용했다.

```bash
./scripts/doctor.sh
./gradlew --no-daemon --console=plain :androidApp:tasks --all
# production 수정 전: exit 1, 4 tests / 3 failures
./gradlew --no-daemon --console=plain :androidApp:testDebugUnitTest \
  --tests 'org.bookreader.mobile.reader.TxtReaderControllerTest.*reflow*' \
  --tests 'org.bookreader.mobile.reader.TxtReaderControllerTest.closeDuringReflowWaitsForPendingSaveBeforeFinishing' \
  --tests 'org.bookreader.mobile.reader.TxtReaderControllerTest.failedReflowSaveRetainsPendingAnchorForRetry'
# production 수정 후: exit 0
BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-reflow-validation/ci ./scripts/ci-check.sh
# exit 78: connected device 없음
BOOKREADER_DEVICE_EVIDENCE_DIR=docs/evidence/m04-reflow-validation/device ./scripts/device-check.sh
git diff --check
```

새 회귀는 같은 runBlocking event loop에서 사용자 이동→reflow를 지연 없이 실행하여 sampler/settled 타이밍에 의존하지 않는다.
close 테스트는 deferred로 save 완료를 지연하고 close/flush 완료가 그 뒤임을 확인한다.
controller 결과는 실제 DB commit-latency 측정이나 실제 Android lifecycle 실행이 아니다.
임시 0 무시·역방향 anchor 보존은 SAFE-12의 controller 부분 증거이며,
M04 전체 SAFE matrix/A-B rapid reopen/60초 연속 스크롤/실제 disk-full 및 기기 검증은 미완료다.
이 환경에는 기존 debug signer가 없으므로 새 APK를 이전 제공 APK와 동일 서명·데이터 유지 업데이트로 취급하지 않는다.

## M02/M03 현재 검증 — 2026-10-09 재개 working tree

TXT 가져오기·실제 스크롤 독서 내부 알파 `0.2.0`의 source는 `2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf`다.
local full CI6와 remote build `37879616525`는 native Android unit 41/shared JVM 45 cases, failure/error/skipped 0,
lint 20 Hint와 APK/schema 검사 PASS다. 실제 API36 device `37879616383`은 native ordinary 25 cases/0 failures·errors·skips와
별도 host 2 stages PASS다. 필수 identity/source/PID 종료 proof/raw host stage를 확인했고 PNG 4개의 hash/1080×1920 decode도 PASS다.
Astra의 독립 evidence 검토로 좁은 TXT 내부 알파 M02/M03 gate를 승인했다. M04 전체 및 EPUB/Readium M01은 미완료다.
이전 `3ce3386` 외부 VIEW Reader ERROR와 `cfe17f0` DATABASE_UNAVAILABLE는 아래 이력으로 보존한다.
Room 공유/단계 진단 변경 후 외부 VIEW와 owner/Reader 회귀가 PASS한 것은 검증 결과이며, 이전 SQLite BUSY 원인 확정은 아니다.

| 수준 | 현재 결과 | 검사 범위 / 증거 |
|---|---|---|
| 역사 source host Kotlin/JUnit | PASS | `fdd9ac1` encoding-only 13 cases; 최초 native CI XML 보존, `encoding-isolated/`는 명시한 역사 source 재실행 |
| Android ICU 차이 수정 host Kotlin/JUnit | PASS | 현재 strict EUC-KR grammar 포함 14 cases failure 0/exit 0; `encoding-icu-fix-isolated/` |
| 자체 작성 fixture manifest | PASS | 11개 actual byte size/SHA-256 일치, CC0-1.0; `fixtures/encoding/manifest.json` |
| Android unit 통합 중간 실행 | PASS | attempt 1 실제 XML 31 cases/failure·error·skipped 0, exit 0; `android-unit-attempt-1-summary.json`; 이후 source 변경으로 최종 CI 재실행 필요 |
| Shared 실제 DB 통합 중간 실행 | PASS | attempt 1 실제 XML 40 cases/failure·error·skipped 0, exit 0; `shared-tests-attempt-1-summary.json`; 추가 회귀 후 최종 CI 재실행 필요 |
| 전체 CI attempt 1 | FAIL | Android unit 36/shared 43 cases는 PASS이나 lint 2 errors로 exit 1; `local-ci-attempt-1/summary.json` |
| 전체 CI attempt 2 | PASS(중간) | Android unit 37/shared 43 cases, failure·error·skipped 0; lint 20 Hint·APK/schema PASS, source drift 2 files로 최종 검증에 사용하지 않음 |
| `fdd9ac1` assemble/lint/Android unit/shared DB/schema | PASS | source drift 0의 attempt 3, exit 0; Android unit 37/shared 43 cases failure·error·skipped 0, lint 20 Hint, 양쪽 APK/schema v2 PASS |
| 동일 source 원격 build | PASS | run `37167947509`, 실제 native XML Android unit 37/shared 43 failure·error·skipped 0, lint 20 Hint, schema 1/2 local bytes 일치 |
| `fdd9ac1` 실제 Android provider/intent/charset/Reader UI | FAIL | run `37167947545`, native connected 23 cases/14 failures/0 errors·skips; `remote-fdd9ac1-device/summary.json` |
| 수정 `9eb8f78` 최종 로컬 CI | PASS | attempt 4 exit 0/source drift 0, Android unit 38/shared 43 failure·error·skipped 0, lint 20 Hint, 양쪽 APK/schema PASS |
| 수정 `9eb8f78` 원격 build | PASS | run `37169215834`, 실제 native 38 Android unit/43 shared failure·error·skipped 0, lint 20 Hint, local byte 동일 schema 1/2 |
| `f3d4c38` 실제 Android 실행 | FAIL | native 23 cases/11 failures/0 errors·skips; encoding 3 모두 PASS, link denial/intent identity/asset fixture 실패 증거 보존 |
| `cfe17f0` 로컬 CI5 | PASS | 38 Android unit/43 shared failure·error·skipped 0, lint 20 Hint, 양쪽 APK/schema PASS, source drift 0 |
| 최종 asset fixture 수정 compile/lint | PASS | instrumentation Java 1 file만 변경, 실제 test APK compile/assemble +lint exit 0; production/unit/shared input hash는 CI5와 동일 |
| `cfe17f0` 원격 build | PASS | run `37171679446`, native 38 Android unit/43 shared failure·error·skipped 0, lint 20 Hint, schema 1/2 local 일치 |
| `cfe17f0` 실제 Android | FAIL | run `37171679395`, native 24 cases/1 failure/0 errors·skips; import 13·encoding 3 PASS, Reader progress read DATABASE_UNAVAILABLE |
| `3ce3386` observer fixture compile/lint | PASS | test-only observer/diagnostics 수정; exit 0/source drift 0, production/unit/shared inputs는 CI5와 동일 |
| 재개 기준 `3ce3386` 원격 build | PASS | run `37172491541`, native Android unit 38/shared 43 failure·error·skipped 0, lint 20 Hint, schema 1/2 일치; `remote-3ce3386-build/` |
| 재개 기준 `3ce3386` 실제 Android | FAIL | run `37172491508`, native 24/1 failure/0 errors·skips, 인코딩 3/두 ReaderFlow PASS, 외부 VIEW Reader ERROR; `remote-3ce3386-device/` |
| 2026-10-09 재개 doctor | PASS | exit 0; 이전 JDK/SDK/proxy TLS 유지, local adb 연결 기기 0; `resume-2026-10-09-baseline.json` |
| `2be610a` full local CI6 | PASS | actual exit 0, native Android unit 41/shared JVM 45 failure·error·skipped 0, lint 20 Hint, 앱/test APK/schema PASS, source drift 0; `local-ci-attempt-6/` |
| `2be610a` 원격 build | PASS | run `37879616525`, native 41 Android unit/45 shared JVM failure·error·skipped 0, lint 20 Hint, schema 1/2 local 일치; `remote-2be610a-build/` |
| `2be610a` actual Android | PASS | run `37879616383`, native ordinary 25 cases failure·error·skipped 0, owner·외부 VIEW·SAF/SEND·encoding·Reader 모두 PASS; `remote-2be610a-device/` |
| `2be610a` host force-stop/restore | PASS | exact raw 2 stages, PID 5624→외부 force-stop→absent→cold PID 5671, commit offset 11607/epoch 1/sequence 2 보존→서재 Continue |
| 자체 fixture PNG 4개 | PASS | `m02-library`, `m03-reader`, `txt-reader-generated-scroll`, `txt-reader-generated-host`; SHA-256 및 PNG 1080×1920 decode 확인 |
| `9eb8f78` device workers 1/2 | BLOCKED_ENV | 두 실행 모두 KVM read/write permission 문제로 tests 시작 전 exit 1; 앱 회귀 FAIL과 구분 |
| host-driven force-stop/relaunch/continue | PASS | `2be610a`의 외부 adb/runner 2 stages; 미커밋 위치 보존/전체 M04 SAFE PASS를 뜻하지 않음 |
| 실기기 5MiB/50MiB p95·메모리·OEM·TalkBack | NOT_RUN | 에뮬레이터 correctness와 별도 gate |
| EPUB/Readium | NOT_RUN | TXT 우선 작업에서 보류; Android 인코딩 결과로 대신하지 않음 |

현재 `/workspace/bookreader-mobile-0.2.0-debug.apk`는 `2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf` source다.
SHA-256 `021ec47de8311435f6becd0eb5b6e677ed22312b6ef3740b86d86b60d10a6e5e`, 17,027,692 bytes이며 versionName `0.2.0`/versionCode 2다.
실제 aapt/apksigner는 exit 0이고 기존 local signer `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 유지한다.
`final-local-apk.json`에 metadata를 기록했고 제공 복사본의 SHA-256도 일치한다. 이전 APK는
`/workspace/bookreader-mobile-0.2.0-debug-before-2be610a.apk`에 보존하며 `final-local-apk-before-2be610a.json`이 당시 metadata다.
CI6 source fingerprint는 `85556d8e9ae941153761b22791bc34fdc71e11aaee890e5da474df9d8f31e3a0`이며 실행 중 drift는 0이다.
동일 source의 actual Android/host gate는 PASS다. 기기 job이 설치 APK hash를 보존하지 않으므로 이 local debug APK를
기기에서 설치한 APK와 byte 동일하다고 주장하지 않는다. independently built CI APK는 별도 hash/서명이다.

[최종 device summary](evidence/m02-m03-validation/remote-2be610a-device/summary.json)는 native connected XML/host raw stage와 검증 결과를 기록한다.
원격 artifact `11593602844`의 실제 XML은 [native-connected](evidence/m02-m03-validation/remote-2be610a-device/native-connected/), raw host proof는
[host-process-proof](evidence/m02-m03-validation/remote-2be610a-device/host-process-proof/), 명령 기록은
[command-evidence](evidence/m02-m03-validation/remote-2be610a-device/command-evidence/), PNG는
[fixture-screenshots](evidence/m02-m03-validation/remote-2be610a-device/fixture-screenshots/)에 보존한다.
준비/복원 instrumentation의 exact start/pass/finish가 확인되고,
실제 외부 adb 종료/PID proof와 committed offset 11607/sequence 2/epoch 1 이후 cold library Continue 복원을 증명한다.
verification instrumentation이 또 다른 target process를 시작할 수 있어 launcher PID 지속 생존이나 미커밋 scroll 보존은 보장하지 않는다.
로컬 CI6의 native XML과 manifest는 `local-ci-attempt-6/`의 `native-*`/`native-evidence-manifest.json`,
원격 build의 native XML/schema는 `remote-2be610a-build/native-android-unit/`, `native-shared-jvm/`, `native-schemas/`에 보존한다.

다음은 이전 `cfe17f0`/`3ce3386`의 local 검증 이력이다.
변경 전 local 검증은 `local-ci-attempt-5/summary.json`의 full CI와 `local-asset-fixture-validation/summary.json`의
최종 test fixture compile/lint다. 각각 fingerprint는 `1b66992ce15236e6acc0b52f1465bde9af2f7d18fd94e6c9e25c2bd5eead7949`,
`94d162eadf2669eb62f376efec97f077f2e0377baf1f3387a786da3bde391040`이며 각 실행의 drift는 0이다.
최종 fixture 변경은 production/unit/shared test inputs를 바꾸지 않았음을 hash로 확인했다.
당시 version/signature와 source 관계는 `final-local-apk-before-2be610a.json`에 기록한다. `cfe17f0`의 actual ordinary는 아래 기록한 24/1 FAIL이며
이후 `3ce3386` ordinary 결과는 24/1 FAIL이며 host 2 stages는 NOT_RUN이다.

이후 `3ce33868503bac2dd0ecd3829a7bf1a77d562f90`는 Flow/host 테스트와 진단 helper만 수정하여 앱 APK 바이트는 그대로다.
`local-reader-observer-validation/summary.json`의 compile/assemble/lint는 actual exit 0, lint 20 Hint, 104-file drift 0이다.
fingerprint는 `a0cc88b0119f7b5db7df90c0f46d08e110cb27919fa9b89bf97840c1217136e3`이며 production/unit/shared/config input hash는 CI5와 같다.
test APK SHA-256은 `b3c07f6b39187777f686d34693122e6e30b7ce37f168353aa772df4532c2e3c4`다.
`remote-3ce3386-build/summary.json`의 실제 38/43-case XML은 PASS다.
`remote-3ce3386-device/summary.json`은 ordinary 24/1 FAIL을 기록한다. 외부 VIEW 실패는 import 완료 뒤
Reader ERROR이며 이전 phase-only 진단에서 내부 원인이 빠졌다. 측정 행·durable commit·cache loss·Continue·recreation
테스트가 PASS한 것은 해당 실행의 결과이며 외부 VIEW 실패를 상쇄하지 않는다. host는 NOT_RUN, PNG pull은 NOT_COLLECTED다.

다음은 이전 `cfe17f0` 실행의 진단 이력이다.
`remote-cfe17f0-build/summary.json`은 native 38/43 PASS를, `remote-cfe17f0-device/summary.json`은 native 24/1 FAIL을 보존한다.
실패 method는 `measuredCanonicalLineIsSavedAndContinueRestoresAfterCacheLossAndRecreation`이며 fixture `saved(book)` read가
repository의 DATABASE_UNAVAILABLE로 매핑되었다. 원본에 내부 exception이 없어 Room 초기화 contention/SQLITE_BUSY는
확인되지 않은 가설이다. test observer는 각 Flow/host stage의 행동 전에 DB 하나를 열고 stage 종료까지 유지하도록 바꿨다.
Error→Missing 전환, error retry, assertion 완화는 없고 Error 발생 시 raw DAO 진단을 첨부하고 계속 실패시킨다.
production DB/writer를 고쳤다고 주장하지 않는다. host는 NOT_RUN, screenshot pull은 NOT_COLLECTED다.

다음은 이전 `9eb8f782d996fea9c0f04006e39fa72d9a3979ff` 로컬 명령이다.
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-4 ./scripts/ci-check.sh`다.
`local-ci-attempt-4/summary.json`/native XML은 38 Android unit/43 shared, failure/error/skipped 0을 기록한다.
source fingerprint SHA-256은 `04cf3d59249644f9b3a44ac8dbcfd2d1a1feb76f49edc3f05ba045c6f6081e19`이며 drift 0이다.
당시 제공 APK SHA-256은 `2129f9c94914c1385f4cc5cbc900f51a851e4cffa8c8460a4f92d17bc0a32d10`이며 이후 최신 파일로 교체했다.
같은 source의 원격 build [37169215834](https://github.com/LaceyLuv/BookreaderM/actions/runs/37169215834) native artifact
`11290199967`의 XML/CI log를 다운로드하여 같은 38/43 count와 실패·건너뛰기 0을 확인했다.
`remote-9eb8f78-build/summary.json`/`workflow.json` 및 native reports에 기록했다. schema 1/2는 local과 byte 동일하다.

다음은 수정 전 `fdd9ac1` 로컬/원격 CI 기록이다. 당시 최종 명령은
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-3 ./scripts/ci-check.sh`다.
같은 production/test/build source는 commit `fdd9ac15182af4e4c1a21a814c5d9c2aa3393808`로 기록했다.
`local-ci-attempt-3/summary.json`, `results.txt`, 각 log와 native XML `reports/`에 exit/count/hash를 기록했다.
`local-ci-attempt-3-source-hashes.json`의 SHA-256은
`13a7a332f8f01f2f6b4ba1d32c4cbe74e765262f5d8ef98b84c4f266d3365f4d`이며 시작/종료 파일 hash 변경이 없다.
로컬 debug 앱 APK SHA-256은 `c35e01f01d08b44747c389d40665f799bd1f15ff70bff899f3cee945ed155720`이다.
당시 제공 복사본은 이후 수정 APK로 교체되었다. 이전 로컬 M00 debug signer를 유지하되
과거 CI signer와는 다르므로 기존 CI APK 설치본의 데이터 유지 업데이트를 보장하지 않는다.
이 로컬 APK를 미래 원격 APK와 같은 바이트라고 가정하지 않는다.

동일 source `fdd9ac1`의 원격 build [37167947509](https://github.com/LaceyLuv/BookreaderM/actions/runs/37167947509)도 success다.
job `111334760116`/artifact `11290761635`의 실제 native XML을 다운로드하여 위 37/43 count와 실패·건너뛰기 0을 확인했다.
증거는 `remote-fdd9ac1-build/workflow.json`, `summary.json`, `reports/` 및 원본 CI log에 있다.
schema 1/2 export는 local과 byte-for-byte 일치한다. 원격 APK는 별도의 byte hash/서명을 기록하며 local APK와 동일성을 가정하지 않는다.

격리 검사는 cached compiler/JUnit로 Android/공유 앱 소스 전체와 독립적으로 실행한다.
정확한 command/exit/source hash는 `encoding-isolated/results.json`에 기록했다.
이전 디렉터리를 재사용한 14-case 결과는 `encoding-icu-fix-isolated/`에 분리했다. `encoding-isolated/`는 Git에 보존된
원래 `fdd9ac1` source를 실제 다시 실행한 역사 증거이며 최초 실행 결과인 것처럼 재작성하지 않는다.
현재 source 검사는 아래 ICU 수정 디렉터리의 명령을 사용한다.

```bash
python3 docs/evidence/m02-m03-validation/encoding-icu-fix-isolated/run.py
```

전체 build/test는 기존 `ci-check.sh`가 담당한다. 신규 `scripts/required-tests.json`과 XML verifier가 실제 필수
test identity, 실패/error/skipped를 확인한다. empty XML, stale result 또는 missing case를 PASS로 처리하지 않는다.
신규 추가 case는 실행 manifest와 실제 source를 함께 확인한다.

ordinary Android 기기 검사는 AGP/UTP의 native connected XML을 사용한다. host-driven 검사는
`scripts/reader-process-check.py`가 준비 단계 instrumentation → 살아 있는 앱 PID 확인 → 외부 `adb force-stop`
→ 종료 확인 → 앱 재실행/서재/이어읽기 검증 단계 instrumentation을 순서대로 수행한다.
`TEST-reader-host-process.xml`은 helper가 실제 adb instrumentation의 정확한 start/pass/finish와 test identity를
검사한 뒤 만든 결과이며 native AGP XML이 아니다. ordinary connected cases와 2 host stages를 하나의 connected suite로 합치지 않는다.
`3ce3386`의 ordinary는 24개였고 Room owner 회귀를 추가한 재개 source의 필수 ordinary는 25개다.
ordinary connected 실행 뒤 host 검사를 위해 APK를 유지하는 AGP 8.11.1 공식 stable option
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`를 검증 스크립트에 적용했다.
공식 AGP source/API 확인은 `agp-test-lifecycle-api.json`, script-only 교정은 commit `d8e4ec9`에 기록했다.
앱/test source 및 로컬 제공 APK는 바꾸지 않았다. 기존 device 실행이 진행 중인 시점의 API 교정이며,
원본 실행의 host 실패를 관찰했다는 뜻이 아니다.
raw stage 로그, PID·명령·종료 proof를 XML과 함께 보존한다. 준비 instrumentation 종료 뒤 앱 프로세스가 없다면
host는 앱을 명시적으로 실행하여 실제 살아 있는 PID를 확보한 뒤 강제 종료하며 이 경로도 proof에 기록한다.
복원 확인 instrumentation은 launcher가 만든 프로세스와 다른 target 프로세스를 시작할 수 있다.
force-stop 이후 보존 기준은 마지막 DB commit 완료 locator다. 미커밋 화면 위치나 하나의 launcher 프로세스가
verification 끝까지 계속 살아 있었다고 보장하지 않는다.

`9eb8f78`의 두 KVM worker 차단 뒤 공식 `udevadm settle`의 bounded queue synchronization을 workflow에 추가했다.
API 근거는 `udev-synchronization-api.json`, workflow-only commit은 `f3d4c38e2bbe2bb50c98d5da4e5dfaaca0376c26`다.
KVM read/write 검사를 그대로 유지하며 실제 성공 전에는 permission 문제가 해결되었다고 주장하지 않는다.
이 실행의 앱/test/APK source는 `9eb8f78`과 같고 workflow만 다르다.
새 run `37169506738`에서는 실제 KVM setup 검사가 PASS하여 Android instrumentation 실행에 진입했다.
그 뒤 내려받은 native XML은 ordinary 23 cases/11 failures/0 errors/0 skipped다. 인코딩 3 cases는 모두 PASS이고
관리 파일의 SELinux link denial 및 intent/asset fixture 실패를 확인했다. `remote-f3d4c38-device/summary.json`에
원본 실패를 보존했다. 당시 host는 NOT_RUN이었으며 이후 `cfe17f0` ordinary와 최종 `2be610a` host 결과는 위 이력에 별도 기록한다.

```bash
./scripts/device-check.sh
python3 scripts/reader-process-check.py build/host-process-evidence
```

스크린샷은 CI의 자체 작성 fixture 화면만 `BOOKREADER_CAPTURE_FIXTURE_SCREENSHOTS=true`로 캡처한다.
로컬 기본값은 꺼져 있어 사용자의 책 화면을 자동으로 기록하지 않는다. 스크린샷은 실제 기능/종료 검증 결과를 대신하지 않는다.

| 신규 검사 소스 | 책임 |
|---|---|
| `TxtDecoderTest` | BOM·short reads·strict UTF/legacy malformed·CRLF/surrogate 경계·bounded prefix·late error·actual source byte limit·cancel/time budget |
| `AndroidTxtEncodingTest` | Android 실제 CP949 확장/EUC-KR 구분, UTF BOM/canonical 경계, 늦은 incomplete 문자 거부; 필수 3 cases |
| `ImportCoordinatorTest` | 실제 DB+journal과 filesystem port의 checkpoint fault/recovery·중복 progress 보존·권한과 무관한 관리 복사본·삭제 무효화 |
| `MigrationTest` | Room schema 1→2에서 기존 book/progress 유지; destructive fallback 없이 reopen |
| `RoomProgressWriterTest` | 세션/revision/sequence 조건부 저장과 stale write 거부; 실제 DB integration |
| `TxtCanonicalCacheTest` | bounded fragment coverage·5MiB 단일 문단·cache 손실 재구축·취소와 같은 길이 source/cache 변조 거부 |
| `DatabaseFailureDiagnosticTest`/writer/controller 회귀 | 고정 stage/오류 코드와 bounded 클래스 chain, 진단 callback 실패 시 기존 Error 결과 및 기록 보호 |
| `AndroidDatabaseOwnerTest` | 12 concurrent borrowers 동일 instance, library lease close 뒤 3회 fresh Reader/저장과 독립 observer; 직접 DAO fixture를 만들며 import 자체 테스트는 아님 |
| 강화한 외부 VIEW 회귀 | 실제 provider import→Reader READY 뒤 owner 동일성/library 조회로 import/Reader borrower 수명 검증 |
| Android provider/Reader integration | 실제 `TestDocumentsProvider`, 외부 intent, TXT import→scroll→저장→서재→이어읽기; `2be610a` ordinary 25 PASS; 외부 VIEW/SAF/SEND와 두 ReaderFlow 포함 |

수동 확인에는 [자체 작성 UTF-8 예제](../fixtures/encoding/bookreader-demo-ko-utf8.txt)를 기기 Downloads 등 선택 가능한
위치에 복사하고 `TXT 추가`에서 선택한다. 가져오기 완료의 `읽기`를 누르고 여러 화면을 스크롤한 뒤 서재로 돌아와
이어읽기를 연다. 외부 프로세스 종료 검증은 별도 host 명령과 DB commit 관찰 결과를 기록해야 한다.
어떤 검사도 개인 책 본문/URI를 로그로 출력하지 않는다.

아래 M00 섹션은 완료 당시의 증거와 검사 수준을 보존한 이력이다. M00의 case count와 APK identity를
신규 working tree 결과로 취급하지 않는다.

## M00 실행 증거

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

## M00 완료 당시 이후 작업의 검사 — 이력

M00는 Reader가 없으므로 진행도 저장 경쟁, 마지막 커밋 위치 복원, 이어읽기 및 process-death는 `NOT_RUN`이다.
M03/M04에서 실제 import→독서→저장→외부 종료→서재→이어읽기 경로와 함께 구현·실행한다.
M01의 EPUB Locator round-trip/위치 복원/publication script 및 외부 리소스 차단, CP949 확장 문자 검사는 별도다.
M10의 migration upgrade, 최종 APK native ABI/16KB, cloud/D2D 백업 동작 및 실기기 수치 성능도 M00 통과로 대체하지 않는다.

사용자 책 본문·개인 URI·서명키를 로그나 CI artifact에 넣지 않는다.
테스트는 자체 생성 메타데이터·locator를 사용하고 삭제/skip/ignoreFailures로 실패를 숨기지 않는다.

M00 완료 당시 M01은 `TODO`였다. 현재는 Android 인코딩 부분 PASS/Readium 미완료로 `IN_PROGRESS`다.
당시 다음 입력은 검증된 M00 source·정확 pin·Room v1 schema와 위 실행 증거였다.
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

### SAFE-09 Reader 수정 후 로컬 재검증

`source /workspace/toolchains/mobile-env.sh` 뒤
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-continuous-validation/fix-ci ./scripts/ci-check.sh`는 exit 0이다.
수정 후 native unit 47/shared JVM 50, failures/errors/skips 0, lint 20 Hint, APK/schema PASS다.
입력 fingerprint `480c6087db7ebd764497bf84534605af5f1880c87a21109777968ab7be493018`, drift 0이며
`fix-native-*`, `fix-ci/`, `fix-ci-result.txt`, `source-before-fix-ci.json`을 보존했다.

### `14ce759` actual SAFE-09 및 host 준비 결과

[device run 38116877735](https://github.com/LaceyLuv/BookreaderM/actions/runs/38116877735)는
ordinary 26 및 60초 JSON verifier PASS이나 host 준비 timeout으로 workflow FAIL(exit 65)이다.
[원본 summary](evidence/m04-continuous-validation/remote-14ce759-device/summary.json)의
60,033ms/anchor 224/commit 60/최대 관찰 공백 1,102ms는 실제 SAFE-09 기기 결과다.
외부 force-stop/서재 Continue 검사는 이 source에서 NOT_RUN이다.
준비 코드에 마지막 swipe 후 compose.waitForIdle와 commit 후 anchor/saveError 확인을 추가해 재실행한다.

host 준비 동기화 수정 후 `source /workspace/toolchains/mobile-env.sh` 및
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-continuous-validation/host-ci ./scripts/ci-check.sh`는 exit 0이다.
unit 47/50·failures/errors/skips 0·APK/schema PASS·lint 20 Hint, fingerprint
`0670f2c61c8c6522123f3bd0c7261172649eecdafe3679385946e409dfea561c`, drift 0을 `host-native-*`/`host-ci/`에 보존했다.

### SAFE-07 Android native 타입 확인 보완

공식 SQLite Android 2.6.1에서 SQLiteException은 android.database.SQLException의 typealias다.
Reader diagnostic은 플랫폼별 simpleName을 기록하므로 native probe가 실제 낸 runtime type과 비교한다.
`7b9d27a`의 remote build/device `38126142050`/`38126142063`은 이 보완으로 명시 취소했으며
cancelled 결과를 Android 성공/실패로 취급하지 않는다. source 및 실제 최신 결과는 SAFE-07 evidence summary를 따른다.

보완 후 정확 명령은 `source /workspace/toolchains/mobile-env.sh` 뒤
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-sqlite-full-validation/platform-ci ./scripts/ci-check.sh`이며 exit 0이다.
최종 local native 47/53, failures/errors/skips 0, lint 20 Hint, APK/schema PASS, fingerprint
`504c4bb345f79b0f7f638bc0298c9d8eb7cadde96c81d0efefca73de7c4e0a00`, drift 0이다.

### filesystem ENOSPC 원격 namespace 제약

`a3ee33b` build `38127759100`는 일반 47/53 PASS 후 새 ENOSPC 단계의 uid_map 생성에서 exit 78/BLOCKED_ENV다.
workflow 판정은 failure다. `remote-a3ee33b-build/blocked-env.log`에 원본이 있다.
Ubuntu runner의 기존 sudo 권한으로 같은 격리 스크립트를 실행하도록 build workflow만 보완했다.
전역 보안 설정을 변경하거나 검사를 skip하지 않는다. 최종 원격 결과는 상단 최신 절을 따른다.

### page-limit 오류 배너의 비동기 reflow 측정 보완

`bacd79f` build `38128654804`는 48/53/ENOSPC 3 PASS, device `38128654709`는 DB owner PASS이나
page-limit UI의 anchor 비교 1개 FAIL(53 vs 6625), host NOT_RUN이다. 원본은 `remote-bacd79f-{build,device}`에 있다.
오류 배너가 viewport를 바꾼 뒤 READY로 복귀하기 전에 측정할 수 있어 배너/READY를 먼저 기다리도록
test 순서만 수정했다. 전체 assertion을 유지한다. `./gradlew --no-daemon --console=plain :androidApp:assembleDebugAndroidTest`
exit 0은 APK compile 증거이며 actual PASS는 새 원격 실행 결과를 따른다.
