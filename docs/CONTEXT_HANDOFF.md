# BookReader Mobile 새 컨텍스트 인계 — 2026-10-17

## 2026-10-17 EPUB probe·M07 후속·archive journal

main `4019766`까지 구현했다. [EPUB probe](M01_EPUB_PROBE.md)의 API36 EPUB 3개는 PASS지만
전체 device suite는 `6b59093` 2 FAIL/`46da7fa` 1 FAIL로 보존했다. host stage는 NOT_RUN이다.
창 설정 소유권(`b7a1898`)과 메뉴 테스트의 이전 fling baseline(`3b48430`)을 보완했고 새 원격 검증 중이다.
[archive journal](ARCHIVE_IMPORT_JOURNAL.md)은 schema 5/포맷별 한도/취소·중단 복구를 구현했다.
local full CI Android 60/shared 71/EPUB 8 PASS이며 archive 제품 UI 연결은 아직 없다.
남은 전체 계획 진행 요청은 계속 유효하다. 기존 M04–M07 전체·실기기·TalkBack·출시 gate를 자동 완료하지 않는다.


## 2026-10-17 M07 main 반영 및 EPUB 선행 probe

`a183271` main에 메뉴·취소 가능한 위치 slider·탭/swipe·창 설정을 반영했다.
local/remote build unit 59/shared 67/remote ENOSPC 3 PASS, device run 39007531744는 실행 중이다.
[보고서](M07_MOBILE_CONTROLS.md)와 `docs/evidence/m07-validation`을 따른다.
`/workspace/bookreader-mobile-epub`의 `codex/m01-epub`에서 Readium 3.1.2 격리 probe를 구현 중이다.
최신 3.4.0은 현재 compileSdk/Kotlin보다 높은 요구를 명시하므로 기존 도구와 호환되는 안정 버전을 검증한다.
EPUB 제품 가져오기/독서로 연결한 상태가 아니다. 전체 남은 계획 진행 목표는 계속 유효하다.


## 2026-10-17 M06 main 검증 및 M07 진행

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


## 2026-10-17 SAFE-01 실제 Reader 조회 오류·재시도

source `0e2605e9a21be7fc72cfa6a72e36e89de406fe3d`에서 native SQLite 조회 오류와 malformed locator의 실제 Reader UI 회귀 2개를 추가했다.
실패한 재시도 2회 동안 전체 progress/book/read_order 및 두 관리 파일을 보존하고,
장애 해제 후 이전 commit의 TextLayout 위치 복원·정확히 1회 activation·새 epoch 저장을 확인했다.
production 코드/schema 변경은 없다. local(초기 source)/최종 remote unit 48/shared JVM 53, remote ENOSPC 3,
actual API36 ordinary 29/외부 host 2 stages 모두 PASS다. 최초 199e9b9의 예외 타입 이름 assertion FAIL과 host NOT_RUN은 원본 보존했다. local device는 기기 부재로 exit 78/BLOCKED_ENV다.
[TESTING](TESTING.md), [보고서](M04_PROGRESS_READ_ERRORS.md),
[evidence](evidence/m04-read-error-validation/summary.json)가 최신 근거다.

M04는 IN_PROGRESS다. **다음 우선 작업은 실제 UI A/B 및 같은 책 rapid close/reopen**이다.
SAFE-01 bookmark 불변은 현재 entity/DAO/UI 미구현으로 NOT_RUN이며 M06 이후 검증한다.
삭제 중 decode/index, 실제 reflow/ON_STOP, Android ext4/f2fs ENOSPC/fsync, 실기기 성능 및 신규 Astra 검토도 남는다.
이하 이전 절의 '다음 SAFE-01' 안내보다 이 절을 우선한다.

## 2026-10-11 실제 ENOSPC 및 activation 중복 최종 검증

최종 source `ebee7723eed8c7ca487736af52570ae1cfd3d4ba`의 remote Android unit 48/shared JVM 53,
별도 ENOSPC 3개, actual API36 ordinary 27 및 외부 host 2 stages 모두 PASS다.
local 48/53/3은 동일 production/test 입력의 `bacd79f`에서 실행했고, 후속 UI test APK도 compile PASS다.
8MiB tmpfs의 실제 errno 28/여유 공간 0에서 production WAL 쓰기가 SQLite FULL(13)로 실패함을 확인했다.
update/첫 insert/activation의 전체 기록·다른 책·TXT hash 보존, 공간 확보 후 DB reopen·동일 요청 retry도 PASS다.

원격 기기 회귀에서 발견한 REJECTED_STALE를 조사해 중복 restoration callback의 activation 2회를
단위 테스트로 재현했다. restored() 진입 직렬화 및 LAZY job 등록 후 시작으로 수정했고 기존 기기 회귀도 PASS다.
`a3ee33b` device FAIL/host NOT_RUN과 두 CI namespace 실패는 원본 보존했다.
`bacd79f` page-limit UI 측정 실패도 보존했으며 오류 배너의 reflow 완료 후 측정하게 보완했다.
관리자 CI는 private mount namespace, 비권한 local은 user+mount namespace를 사용한다. 전역 보안 설정은 바꾸지 않는다.

[TESTING](TESTING.md), [범위 보고서](M04_FILESYSTEM_ENOSPC.md),
[evidence](evidence/m04-enospc-validation/summary.json)가 최신 근거다.
M04는 IN_PROGRESS다. 다음 우선 작업은 SAFE-01 actual Reader DB 조회 오류/재시도 UI다.
기존 managed-copy 누락 재시도와 DB 조회 오류 검사를 구분한다. UI A/B·같은 책 rapid reopen,
삭제 중 decode/index, reflow/ON_STOP, Android 실제 저장장치 ENOSPC/fsync, 실기기 성능 및 신규 Astra도 남아 있다.

## 2026-10-11 SAFE-07 native SQLite FULL 최종 검증

검증 source `bf7589dcc018c57fc43101a16198cc3afd12b9cd`는 local/remote Android unit 47/shared JVM 53,
actual API36 ordinary 27 및 외부 host 2 stages 모두 PASS다. 신규 DB 3개는 native SQLITE_FULL(13)의
update/첫 insert/activation rollback·재개방·동일 event 재시도를 확인했다.
실제 Reader에서도 오류 UI, 마지막 commit/다른 책/관리 파일 보존 및 공간 복원 후 자동 재시도를 검증했다.
실패 시 offset 4964/sequence 1을 보존하고, 용량 복원 뒤 pending 6625를 sequence 3으로 commit하며 오류가 사라졌다.
테스트 한도와 부가 schema도 복원됐다. production writer/UI/schema 변경은 없다.

실제 60초 연속 저장(60,017ms/60 commits)과 host PID 5794→absent→cold 5843,
offset 13285/epoch 1/sequence 3의 서재 Continue 복원도 회귀 PASS다.
[TESTING](TESTING.md), [범위 보고서](M04_SQLITE_FULL.md), [SAFE matrix](M04_SAFETY_MATRIX.md),
[evidence](evidence/m04-sqlite-full-validation/summary.json)가 최신 근거다.

native page-limit FULL은 OS filesystem ENOSPC와 구분한다. M04는 IN_PROGRESS다.
다음 우선 작업은 격리된 제한 용량 파일시스템에서 실제 ENOSPC/WAL·journal 부족 시 기록 보존 검증,
또는 SAFE-01 실제 Reader 조회 오류/재시도 UI다. UI A/B·같은 책 rapid reopen, 삭제 중 decode/index,
reflow/ON_STOP, 실기기 성능 및 신규 Astra 최종 검토도 남아 있다.

## 2026-10-11 SAFE-09 최종 검증

검증 source는 `73276fe27da781dde807f05a85422ae18363642f`다. main에 commit/push했으며
local/remote CI는 Android unit 47/shared JVM 50, API36 ordinary 26 및 actual host 2 stages 모두 PASS다.
실제 60,001ms 연속 touch에서 anchor 226회/commit 46회, 최대 commit 관찰 공백 2,034ms를 확인했다.
host는 live PID 5672→force-stop→absent→cold PID 5719, offset 13285/epoch 1/sequence 2를 서재 Continue로 복원했다.

60초 회귀가 LazyList의 mutable item 비교로 같은 fragment 내부 이동을 놓치는 결함을 재현했다.
Reader는 snapshotFlow 안에서 index/offset을 값으로 캡처하도록 수정했다. host 준비는 마지막 fling/layout이
안정된 뒤 예상 위치를 캡처한다. `78e45f5` continuous FAIL과 `14ce759` host 준비 FAIL도 원본 보존했다.
[TESTING](TESTING.md), [측정 보고서](M04_COMMIT_LATENCY.md), [SAFE matrix](M04_SAFETY_MATRIX.md),
[evidence](evidence/m04-continuous-validation/summary.json)가 최신 근거다.

SAFE-09 emulator correctness는 PASS, M04는 IN_PROGRESS다. 다음 우선 작업은 SAFE-07 실제 SQLite disk-full
및 마지막 commit/다른 책 보존 검증이다. UI A/B·같은 책 rapid reopen, 삭제 중 decode/index, reflow/ON_STOP,
SQL write-ack/실기기 p95·메모리, 신규 Astra 최종 검토는 미완료다.

## 2026-10-11 DB 경쟁 및 host 준비 후속

실제 파일 DB race 5개(지연 A→B/이전 세션/역방향 sequence/동시 duplicate/삭제 중·후 save)를 추가했다.
12회 세션 변경·DB reopen과 전체 persisted record 보존을 검사하며 UI navigation을 검사한 것으로 취급하지 않는다.
직전 `16e4615` remote ordinary 25는 PASS, host prepare는 CalledFromWrongThreadException으로 FAIL이다.
raw stack의 import progress→worker UI 게시 경로를 수정하고 수정 전 실패하는 회귀를 추가했다.
검증 source는 `96c15aa6d074b0b6d96579e1deb54d6fbc3413ec`다. local/remote CI는 Android unit 46/shared JVM 50 PASS,
remote device `38110471069`는 ordinary 25/actual host 2 stages PASS다. raw PID 5640→absent→cold 5688,
commit offset 11607/epoch 1/sequence 2의 서재 Continue 복원을 확인했다. 이전 host FAIL과 구분한다.
이 절 이후 SAFE-09 실제 연속 이동은 상단 후속에서 검증했다. 다음 우선 작업은 SAFE-07 실제 SQLite disk-full 보존 검증이다.
최신 근거는 [TESTING](TESTING.md), [SAFE matrix](M04_SAFETY_MATRIX.md),
[evidence](evidence/m04-race-validation/summary.json)에 있다. 신규 Astra 검토와 전체 M04는 미완료다.

## 2026-10-11 후속 작업

`03870c9` clean main을 새 환경에 clone한 뒤 M04의 REFLOWING 미커밋 flush를 보완했다.
`persistStable`은 READY에서 수락된 stableOffset을 REFLOWING에서도 저장하며,
재배치 callback의 임시 0·비사용자 이동은 여전히 anchor를 변경하지 못한다.
새 필수 controller 회귀 4개와 수정 전 실패 증거가 추가됐다.
정확한 최신 검증 결과는 [TESTING](TESTING.md)의 M04 절 및
[evidence summary](evidence/m04-reflow-validation/summary.json)를 따른다.
아래 10월 9일 PASS는 과거 결과이며 새 변경의 device/host PASS를 의미하지 않는다.

아래 reflow 후속 당시 잔여 작업은 전체 SAFE matrix, A/B rapid close/reopen, 60초 연속 이동, 실제 disk-full,
실제 기기의 reflow/ON_STOP 및 commit-latency다. 신규 Astra 최종 검토는 NOT_RUN이다.
로컬 기기 검증은 authorized device가 없어 exit 78/BLOCKED_ENV다.
새 환경에 `/workspace/toolchains/mobile-env.sh`를 준비했으며 기존 APK/서명키는 가져오지 않았다.
새 debug build를 이전 제공 APK와 같은 signer라고 간주하지 않는다.

## 붙여넣기용 재개 프롬프트

```text
/workspace/bookreader-mobile의 docs/CONTEXT_HANDOFF.md를 읽고 이어서 작업해 주세요.
먼저 /AGENTS.md, /workspace/AGENTS.md, 저장소 AGENTS.md와 git status/HEAD/remote를 다시 확인하고 사용자 변경을 보존하세요.
최신 TASKS/TESTING/WORKLOG와 실제 코드를 읽으세요. M00 및 TXT 내부 알파 M02/M03은 DONE,
M01은 encoding 부분만 검증됐고 Readium/EPUB는 보류입니다. 다음 우선 작업은 M04 잔여 안전 계약입니다.
구현은 Sol 6.1 high, 최종 검토는 Astra를 사용하는 사용자 요청을 유지하세요.
기존 README를 보존하고 승인된 범위의 commit/push는 진행하되 force push/원격 merge/release는 하지 마세요.
기존 PASS를 새 변경의 PASS로 재사용하지 말고 관련 검증을 실제 실행한 뒤 증거와 미실행 항목을 남기세요.
```

## 요청·권한·기준

- 저장소: [LaceyLuv/BookreaderM](https://github.com/LaceyLuv/BookreaderM), 로컬 `/workspace/bookreader-mobile`.
- 작성 전 상태는 clean `main`, 문서 기준 HEAD `d5a4e32d12876028a9e09c0b167a3eb44e6ed4a3` (`docs: complete TXT import and reader validation`). 이 인계 파일 자신의 후속 커밋 SHA는 예측하지 않는다.
- 실제 최종 검증 소스는 `2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf`다. 위 HEAD는 검증 결과를 정리한 문서 커밋이다.
- 최초 M00 한 작업 요청 이후 사용자가 **TXT 추가와 실제 읽기를 우선하는 범위 확장**을 승인했다. Readium/EPUB 보류는 그 우선순위에 따른다.
- 사용자는 구현 `Sol 6.1 high`, 최종 검토 `Astra`를 요청했고 Git commit/push를 승인했다. 기존 README 보존, destructive reset/clean·force push·원격 merge·release/스토어 배포는 이 승인에 포함되지 않는다.
- Android-first/local-first 모바일 독립 저장소다. PC 포팅, iOS/AI/동기화/통계/백업 UI로 범위를 넓히지 않는다.
- 권위 순서: 사용자 지시와 실제 [AGENTS.md](../AGENTS.md), [개정 계획](BookReader_Mobile_v1_1_Implementation_Plan.md), [TASKS](TASKS.md), 실제 소스·실행 증거. [원문](reference/BookReader_Mobile_v1_Plan.md)은 이력이다.
- [README_START_HERE](../README_START_HERE.md) 등 최초 패키지 문서의 “미구현” 표시는 10월 1일 기록이다. 현재 상태는 아래와 최신 [TESTING](TESTING.md)/[WORKLOG](WORKLOG.md)를 따른다.

## 현재 상태와 사용자 경로

| 작업 | 현재 판정 | 범위·제한 |
|---|---|---|
| M00 | DONE | Android/KMP/Room/CI 기반과 당시 실제 검증 완료 |
| M01 | IN_PROGRESS | strict encoding host 14·실제 Android encoding 3 PASS; Readium/EPUB probe·ADR-0005 보류 |
| M02 | DONE | TXT 내부 알파의 안전 import·관리 복사본·서재·삭제/recovery |
| M03 | DONE | TXT 내부 알파의 스크롤 독서·canonical anchor·복원·이어읽기 |
| M04 | IN_PROGRESS | epoch/sequence·DB race·60초 연속 저장 및 과거 force-stop 복원 검증; 최신 host 결과와 잔여 gate는 상단 참조 |
| M05–M11 | TODO | TXT 페이지/본문 검색/북마크, 접근성, EPUB, Comic, hardening/출시 감사 후속 |

- 앱은 `0.2.0`/versionCode 2 **TXT 내부 알파**다. 세 포맷 최종 v1.0 완료 판정이 아니다.
- `TXT 추가` → 시스템 picker → 앱 내부 관리 복사본 commit → `읽기`. 외부 `ACTION_VIEW`는 안전 import 후 Reader, `ACTION_SEND`는 사용자 확인 → commit → `읽기`다.
- 일반 새 프로세스는 서재에서 시작한다. Reader 밖 하단 이어읽기 바로 마지막 성공한 책을 연다. 따뜻한 Reader 복귀·Activity 재생성 복원은 별도 경로다.
- UTF BOM/strict UTF-8 및 수동 UTF-16LE/BE·CP949·EUC-KR 선택을 제공한다. BOM 없는 UTF-16/legacy charset을 자동 확정하지 않는다. 제품 내 책별 인코딩 변경 UI는 M06이다.

## 반드시 유지할 안전 계약

- 영구 원본은 앱 내부 관리 복사본이다. source URI는 출처이며 원본을 삭제하지 않는다. import는 journal의 COPYING/VALIDATING/FINALIZING/COMMITTED와 멱등 recovery·no-overwrite 승격을 유지한다. [ADR-0002](adr/0002-managed-import-journal.md)
- locator는 version/contentRevision을 갖는다. TXT 좌표는 정규화 후 0-based UTF-16 코드 단위 `Long` offset이며 페이지 번호/캐시 byte offset은 영구 위치가 아니다. 첫 BOM 제거·CRLF/CR→LF만 수행하고 trim/빈 줄 축약/NFC는 하지 않는다. [ADR-0003](adr/0003-locator-normalization-and-revision.md)
- canonical cache는 파생 UTF-16LE/fragment index다. bounded streaming·전체 strict decode/hash 검증·완료 marker를 유지한다. 캐시 삭제는 원본/기록 삭제가 아니다.
- 진행도는 `Found / Missing / Error`를 구분한다. 조회·본문 integrity 검증·실제 위치 복원 전에 저장하거나 오류를 0%/Missing으로 바꾸지 않는다. 단순 열기 실패가 lastRead/readOrder를 바꾸지 않는다.
- 이벤트마다 bookId/revision/sessionEpoch/sequence를 캡처하고 DB가 오래된 epoch/sequence·다른 revision·DELETING을 거부한다. 최대 offset 비교나 debounce 하나로 최신성을 판정하지 않는다. [ADR-0004](adr/0004-progress-writer-session-order.md)
- 서재/import/Reader는 앱 소유 `AndroidDatabaseOwner` Room instance/pool을 빌리며 borrower가 닫지 않는다. 독립 test용 `createAndroidDatabase`는 caller-owned다. DB 오류를 빈 서재로 처리하거나 destructive migration fallback을 추가하지 않는다.
- production 진단은 고정 단계/기존 result code와 최대 4개·각 64자 이하 예외 클래스 chain만 기록한다. 책 본문/개인 URI/path/SQL/예외 메시지/stack trace/토큰/서명키를 공개 로그·CI에 넣지 않는다.

## 확정된 검증·증거

아래는 `2be610a`에서 **실제로 실행한 과거 결과**다. 새 코드 변경의 검증으로 자동 승계하지 않는다. ordinary Android 25 cases와 host 2 stages는 서로 다른 검사다.

| 실행 | 결과 | 실제 증거 |
|---|---|---|
| 로컬 full CI6 `./scripts/ci-check.sh` | exit 0/PASS | Android unit **41**, shared JVM **45**, failure/error/skipped **0**, lint **20 Hint**, 앱/test APK·DB/migration/schema PASS, source drift 0; [summary](evidence/m02-m03-validation/local-ci-attempt-6/summary.json) |
| 원격 build [37879616525](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616525) | PASS | native 41/45·failure/error/skipped 0, lint 20 Hint, schema 1/2 local bytes 일치; [summary](evidence/m02-m03-validation/remote-2be610a-build/summary.json) |
| 실제 API36 device [37879616383](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616383), `./scripts/device-check.sh` | exit 0/PASS | ordinary **25**·failure/error/skipped **0**, 별도 host **2 stages**·명령 exit 0, 자체 fixture PNG **4개** hash/1080×1920 decode PASS; [summary](evidence/m02-m03-validation/remote-2be610a-device/summary.json) |

- 증거 기준 디렉터리는 `docs/evidence/m02-m03-validation/`이며 실제 파일은 깊은 원격 artifact 경로 대신 아래 보존 경로에서 읽을 수 있다.
- 로컬 native XML/manifest: [local-ci-attempt-6](evidence/m02-m03-validation/local-ci-attempt-6/)의 `native-android-unit/`, `native-shared-jvm/`, `native-evidence-manifest.json`.
- 원격 build XML/schema: [remote-2be610a-build](evidence/m02-m03-validation/remote-2be610a-build/)의 `native-android-unit/`, `native-shared-jvm/`, `native-schemas/`.
- 기기 원본: [native-connected](evidence/m02-m03-validation/remote-2be610a-device/native-connected/), [host-process-proof](evidence/m02-m03-validation/remote-2be610a-device/host-process-proof/), [command-evidence](evidence/m02-m03-validation/remote-2be610a-device/command-evidence/), [fixture-screenshots](evidence/m02-m03-validation/remote-2be610a-device/fixture-screenshots/).
- host는 실제 살아 있던 PID 5624 → 외부 adb force-stop → PID absent → cold launcher PID 5671을 확인했다. 마지막 commit offset **11607**, epoch **1**, sequence **2**를 cold 서재 Continue로 복원했다. Activity recreation과 구분하며 미커밋 화면 위치 보존을 뜻하지 않는다. verification instrumentation이 다른 target process를 시작할 수 있다.
- Astra가 source/필수 test identity/raw host stage/PID 종료 proof/PNG를 독립 검토해 좁은 TXT 내부 알파 M02/M03 gate를 승인했다.
- 과거 FAIL/BLOCKED_ENV를 삭제하지 않는다. 특히 `cfe17f0` DATABASE_UNAVAILABLE와 `3ce3386` 외부 VIEW Reader ERROR는 이력이다. Room 공유 후 회귀 PASS는 최초 SQLite BUSY 내부 원인 확정/해결 증명이 아니다.
- **NOT_RUN:** 실기기 5MiB/50MiB p95·메모리, OEM 실제 회전, TalkBack, 최종 native ABI/16KB, EPUB/Readium. emulator correctness와 출시 gate를 혼동하지 않는다.

## 제공 APK·서명

- 현재 로컬 파일: `/workspace/bookreader-mobile-0.2.0-debug.apk` (새 clone에 자동 포함되는 파일이 아님).
- SHA-256 `021ec47de8311435f6becd0eb5b6e677ed22312b6ef3740b86d86b60d10a6e5e`, **17,027,692 bytes**, 동일 검증 source `2be610a`의 debug 빌드. [실제 metadata](evidence/m02-m03-validation/final-local-apk.json)
- 기존 로컬 M00 signer `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 유지한다. CI 독립 signer `980adc346f1c476d4a98ef096b0782bbe153871296fb17d7b94f94ff6fee70d5`와 다르다.
- 기기 job에 설치 APK hash가 없어 제공 APK와 device 설치본의 **byte 동일성은 미확인**이다. 동일 source PASS와 artifact 동일성을 구분한다.
- 서명 불일치 시 uninstall/data clear를 권장하지 않는다. 기존 사용자 책/기록을 보호한다. 이전 로컬 APK는 `/workspace/bookreader-mobile-0.2.0-debug-before-2be610a.apk`에 보존되어 있다.

## 소스 탐색 지도

아래 `androidApp/...`와 `shared/...`는 실제 패키지 `org/bookreader/mobile` 아래 파일이다. 링크는 저장소 상대 경로다.

| 책임 | 먼저 읽을 파일 |
|---|---|
| 시작·UI·import 연결 | [MainActivity](../androidApp/src/main/kotlin/org/bookreader/mobile/MainActivity.kt), [AppViewModel](../androidApp/src/main/kotlin/org/bookreader/mobile/ui/AppViewModel.kt), [BookReaderApp](../androidApp/src/main/kotlin/org/bookreader/mobile/ui/BookReaderApp.kt), [AndroidBookManagement](../androidApp/src/main/kotlin/org/bookreader/mobile/ui/AndroidBookManagement.kt) |
| URI 수신·관리 파일·journal | [IncomingDocument](../androidApp/src/main/kotlin/org/bookreader/mobile/importing/IncomingDocument.kt), [AndroidManagedImportFiles](../androidApp/src/main/kotlin/org/bookreader/mobile/importing/AndroidManagedImportFiles.kt), [ImportCoordinator](../shared/src/commonMain/kotlin/org/bookreader/mobile/importing/ImportCoordinator.kt), [ImportDao](../shared/src/commonMain/kotlin/org/bookreader/mobile/database/ImportDao.kt) |
| strict encoding·cache·Reader | [TxtDecoder](../androidApp/src/main/kotlin/org/bookreader/mobile/encoding/TxtDecoder.kt), [TxtCanonicalCache](../androidApp/src/main/kotlin/org/bookreader/mobile/reader/TxtCanonicalCache.kt), [TxtReaderController](../androidApp/src/main/kotlin/org/bookreader/mobile/reader/TxtReaderController.kt), [TxtReaderScreen](../androidApp/src/main/kotlin/org/bookreader/mobile/reader/TxtReaderScreen.kt) |
| DB 소유권·progress·migration | [AndroidDatabaseFactory](../shared/src/androidMain/kotlin/org/bookreader/mobile/database/AndroidDatabaseFactory.kt), [AndroidStores](../androidApp/src/main/kotlin/org/bookreader/mobile/ui/AndroidStores.kt), [RoomProgressWriter](../shared/src/commonMain/kotlin/org/bookreader/mobile/repository/RoomProgressWriter.kt), [ProgressWriterDao](../shared/src/commonMain/kotlin/org/bookreader/mobile/database/ProgressWriterDao.kt), [Migrations](../shared/src/commonMain/kotlin/org/bookreader/mobile/database/Migrations.kt) |
| 회귀·필수 identity·CI | [controller unit](../androidApp/src/test/kotlin/org/bookreader/mobile/reader/TxtReaderControllerTest.kt), [실제 DB writer](../shared/src/jvmTest/kotlin/org/bookreader/mobile/repository/RoomProgressWriterTest.kt), [ReaderFlow](../androidApp/src/androidTest/kotlin/org/bookreader/mobile/reader/TxtReaderFlowTest.kt), [host stages](../androidApp/src/androidTest/kotlin/org/bookreader/mobile/reader/TxtReaderHostProcessTest.kt), [required-tests](../scripts/required-tests.json), [build workflow](../.github/workflows/mobile-ci.yml), [device workflow](../.github/workflows/mobile-device-tests.yml) |

## 다음 우선 작업: M04

1. [TASKS M04](TASKS.md)·개정 계획 부록 B SAFE matrix·ADR-0004와 controller/writer를 읽고 앱 수준 single writer·작업/취소 소유권·flush barrier의 현재 구현을 확인한다.
2. `persistStable`은 READY 및 REFLOWING에서 검증된 stableOffset을 저장한다. **REFLOWING 미커밋 flush는 10월 11일 controller 수정/회귀 대상으로 보완했으며**, 실제 기기의 close/ON_STOP·재배치 UI 검증을 이어간다. 임시 0 locator/복원 callback이 기존 위치를 덮어쓰면 안 된다.
3. SAFE-01 조회 오류, 02 A 저장 지연→B, 03 이전 세션 지연 write, 04 역방향 sequence race, 06 삭제 중 save/decode/index, 07 disk-full, 09 60초 연속 이동, 10 외부 종료, 12 reflow를 실제 matrix로 검증한다. 기존 일부 회귀 PASS만으로 전체 matrix를 체크하지 않는다.
4. A/B 책 및 같은 책 빠른 close/reopen, 손상 locator/조회 오류 기록 보존·재시도 UI, 1초 sampling+250ms settled save·60초 연속 스크롤 중 주기 commit을 검증한다. commit-latency report는 측정 기기/빌드/fixture·미측정 항목을 명시한다.
5. 관련 unit·실제 DB·Android·외부 host 검증을 실행하고 ADR-0004/TASKS/TESTING/WORKLOG에 source SHA 또는 working-tree fingerprint, 정확 명령/exit code·native XML/raw proof·미실행 blocker를 남긴 뒤 Astra 최종 검토를 받는다.

M01 EPUB 재착수는 M00 기반 확인과 사용자 우선순위에 맞는 별도 작업으로 한다. 현재 Readium은 **미추가**, 문서 후보 3.4.0은 확정 pin이 아니다. 공식 stable Android Navigator/API·라이선스·host 형태·Locator round-trip·정상 이미지/CSS/검색/위치와 publication script/외부 resource 차단의 양립을 실제 probe하여 ADR-0005로 결정한다. 완성 EPUB UI/자체 렌더러·CFI/alpha Compose Navigator/DRM은 M01 제외 범위다. M08/M11은 이 gate 해소 전 완료할 수 없다.

## 환경과 이후 검증 절차

- 환경은 컨텍스트마다 재점검한다. 존재하면 `source /workspace/toolchains/mobile-env.sh`를 활용하되 이전 proxy TLS 옵션/`/workspace/toolchains/cacerts`를 임의로 덮어쓰거나 키/인증서 파일을 evidence에 복사하지 않는다. 새 clone에서는 로컬 경로 존재를 가정하지 않는다.
- pins: Temurin **17.0.16+8**, Gradle **8.13**, AGP **8.11.1**, Kotlin/Compose compiler **2.2.20**, KSP **2.2.20-2.0.3**, Room **2.8.3**, bundled SQLite **2.6.1**, Compose BOM **2025.10.00**(UI 1.9.3), Coroutines **1.10.2**, Serialization **1.9.0**, min/compile/target SDK **26/36/36**, build-tools **35.0.0**. [DEPENDENCIES](DEPENDENCIES.md)·[catalog](../gradle/libs.versions.toml)·[wrapper](../gradle/wrapper/gradle-wrapper.properties) 참조.
- 다음 작업의 기본 명령은 아래와 같다. device-check는 테스트 전용 emulator/device를 사용한다. 이전 로컬 baseline의 adb 연결 기기는 0이었다.

```bash
git status --short
git diff --check
./scripts/doctor.sh
./scripts/ci-check.sh
./scripts/device-check.sh
```

- ci-check는 실제 task discovery→assemble/lint/Android unit/test APK compile/shared JVM/Room schema를 수행한다. device-check는 ordinary connected XML 검증 후 `reader-process-check.py`의 외부 force-stop 2 stages와 raw proof를 검증한다. host만 별도 실행할 때는 `python3 scripts/reader-process-check.py build/host-process-evidence`를 사용한다.
- 새 case는 required-tests manifest/XML verifier와 실제 source를 함께 확인한다. 빈/stale XML·missing identity·skip·실패 숨김으로 PASS를 만들지 않는다. screenshot 기본값은 off이며 CI 캡처는 자체 생성 fixture만 허용한다.
- 결과는 `PASS / FAIL / NOT_RUN / BLOCKED_ENV`로 구분하고, doctor PASS·test 파일 존재·Activity recreation을 새 build/device/process-death PASS로 보고하지 않는다.

이번 인계 작성은 이 Markdown 파일 추가만 수행했다. 앱/테스트 소스 변경과 테스트 재실행은 없으며, 위 PASS는 연결된 기존 실행 증거의 기록이다.
