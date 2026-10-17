# ADR-0004 — 조회·복원 이후 세션과 조건부 진행도 저장

- 날짜: 2026-10-04; 2026-10-09 소유권·진단 보완
- 상태: M03에 필요한 기반 구현; M04 전체 검증 미완료
- 기준: 계획서 진행도 안전 계약, TASKS M03/M04

## 선택

Reader는 progress `Found / Missing / Error`를 먼저 읽고 canonical 위치를 실제 TextLayout의 행 시작으로 복원한다.
조회 실패·손상 locator·유효하지 않은 surrogate 경계·본문 revision/integrity 실패 시 기존 기록을 보존한다.
유효한 본문 표시·복원 및 전체 strict 검증 완료 이후에만 `startReadySession`을 호출한다. 단순 열기 시도는
lastRead/readOrder를 바꾸지 않는다. 성공한 세션 활성화는 DB transaction에서 READY/revision과 읽은 progress
snapshot을 다시 확인하며 persisted sessionEpoch를 증가시킨다.

각 save event는 bookId/contentRevision/sessionEpoch/sequence를 캡처한다. DB는 READY book/currentRevision과
activeSessionEpoch를 확인하고 같은 epoch에서 더 작은/같은 sequence 및 이전 epoch를 거부한다.
offset 크기로 최신성을 판정하지 않으므로 뒤로 이동한 위치도 새로운 이벤트면 저장할 수 있다.
DELETING book이나 다른 revision/오래된 세션은 기록을 재생성하지 않는다.

현재 Reader controller는 실제 사용자 스크롤 이후의 canonical 행 offset만 저장한다. 1초 sampling과 250ms
settled save를 함께 쓰고, mutex로 저장/flush를 직렬화한다. layout callback과 Ready 전에는 쓰지 않는다.
서재 복귀와 lifecycle stop에서 flush를 시도하며 실패는 UI에 표시한다. 2026-10-11 보완으로 `persistStable`은
READY 또는 REFLOWING에서 마지막 안정 anchor를 저장한다. REFLOWING의 anchor는 READY에서 수락한 실제 사용자 이동으로
고정되며 재배치 callback은 변경할 수 없다. 새 session 활성화·조회/본문 검증·최초 복원 gate는 그대로 유지한다. 새 프로세스는 서재에서 시작하고,
이어읽기는 마지막 성공한 세션의 책을 여는 Reader 밖 하단 바로 제공한다.

## 이유와 검증 한계

진행도 조회 오류를 Missing으로 취급하거나 복원 전에 0%를 저장하면 마지막 성공 기록을 잃는다.
offset maximum, 하나의 debounce, Activity 종료에만 의존하는 저장은 역방향/연속 이동/오래된 작업에 안전하지 않다.
조건부 DB commit과 세션 order를 선택하여 이 문제를 차단한다.

세션별 조건부 DB writer와 Reader controller 연결은 M03을 위한 구현이다. `2be610a`의 host-driven force-stop 복원은 PASS다.
전체 M04 SAFE matrix와 60초 연속 스크롤, A/B 빠른 전환 및 save 지연 수치는 별도 실행해야 한다.
Activity 재생성은 process death가 아니다.
외부 종료 후 복원은 마지막 commit 완료 위치가 기준이며 미커밋 화면 위치까지 보존한다고 주장하지 않는다.
실행 결과는 [TESTING](../TESTING.md), [WORKLOG](../WORKLOG.md), `docs/evidence/m02-m03-validation/`를 따른다.

실제 `cfe17f0` device 실행에서 measured scroll flow의 fixture progress read는 DATABASE_UNAVAILABLE로 실패했다.
내부 exception이 원본에 없어 Room initialization contention/SQLITE_BUSY는 미확인 가설이다. `3ce3386`는 테스트의
DB observer를 각 Flow/host stage 시작 전 한 번 열어 종료까지 유지하고, Error 시 raw DAO 진단을 첨부하되 실패를 유지한다.
Error→Missing 전환/오류 retry/측정·durable assertion 완화 또는 production writer 변경은 없다.
해당 실행·향후 실제 DB 오류 조사와 M04 전체 concurrency matrix는 follow-up이며 production DB 버그를 해결했다고 기록하지 않는다.

## 재개 시 소유권·진단과 최신 실행 — 2026-10-09

`3ce3386` actual Android `37172491508`에서 두 ReaderFlow는 모두 PASS다. 따라서 앞선 `cfe17f0` observer의
DATABASE_UNAVAILABLE 실패와 당시 외부 VIEW 실패를 구분한다. 당시 ordinary 24/1 FAIL의 유일한 실패는 import 성공 뒤
첫 Reader READY 이전 ERROR이며 phase-only 진단에 실제 원인이 없다. host 2 stages는 ordinary gate 실패로 NOT_RUN이다.

Android progress adapter는 [ADR-0002](0002-managed-import-journal.md)의 앱 소유 Room instance를 빌려 사용하며
operation 종료에 닫지 않는다. 해당 access의 mutex와 DB transaction/epoch/sequence 검사는 유지한다.
진단 callback은 실패를 fixed operation stage와 최대 4개·각 64자 이하 예외 클래스 chain으로 snapshot한다.
Reader 진단에는 고정 stage 및 기존 result code를 포함하며 메시지·stack trace·SQL·책 ID/본문·URI·파일 경로를 담지 않는다.
repository의 Error/Missing 구분, caller cancellation, 기존 기록 보호와 실패 결과는 유지한다. 진단 callback의 실패로
repository 결과가 바뀌지 않도록 처리한다. 잘못된 기록이나 stale event를 정상으로 바꾸거나 오류 retry를 추가하지 않는다.

Room 공유와 sanitized 진단은 원인을 확인할 수 있게 만드는 `2be610a` 변경이다. 변경 후 full CI/actual Android/host는 PASS다.
이전 SQLite BUSY 내부 원인이나 production 문제의 원인 해결은 아직 증명하지 않았다. M04 전체 SAFE matrix는 별도 gate다.

`2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf` 변경 후 local full CI6는 actual exit 0,
Android unit 41/shared JVM 45 failure/error/skipped 0, lint 20 Hint 및 앱/test APK/schema PASS, source drift 0이다.
새 actual ordinary Android 25/host 2 stages/fixture PNG 4개는 device `37879616383`에서 PASS다.
raw host는 PID 5624→외부 force-stop→absent→cold PID 5671 및 commit offset 11607/epoch 1/sequence 2 이후
cold library Continue 복원을 확인했다. Activity 재생성과 별도 actual external process 종료 증거다.
해당 성공은 이전 SQLite 오류의 내부 원인 확정이나 M04 전체 SAFE/race/미커밋 flush 완료 근거가 아니다.


## M04 일부 보완 — 2026-10-11 재배치 중 flush

READY에서 아직 저장되지 않은 사용자 이동 직후 `beginReflow`가 settled job을 취소하고 phase를 바꾸면,
기존 READY-only 조건은 `flush`/`close`에서도 해당 위치를 버렸다. 저장 가능 phase에 REFLOWING을 포함하여
이미 검증된 session과 stableOffset을 기존 writer mutex/epoch/sequence 경로로 commit한다.
새 위치 입력은 여전히 READY에서만 허용한다. 사용자 이동이 없으면 새 기록을 만들지 않고 같은 offset은 중복 저장하지 않는다.
실패 시 lastCommittedOffset을 갱신하지 않아 다음 flush에서 같은 anchor와 증가한 sequence로 재시도한다.

소유권 검토: ViewModel의 readerMutex가 close→open을 직렬화하고, factory가 공유하는 AndroidReaderProgressAccess가
앱 Reader들의 DB 접근/flush를 직렬화한다. controller는 parentScope 자식 lifetime의 sampler/settled/open/restore 작업을 소유하며,
close는 sampler/settled를 취소하고 flush를 기다린 뒤 open/restore를 join하고 lifetime을 취소한다.
이번 수정은 소유권·취소 범위를 변경하지 않는다. 호출자 취소, process kill, ON_STOP callback의 실제 기기 완료 보장까지
확대하지 않는다. host/기기 재검증 및 전체 M04 matrix는 후속 gate다.

새 controller 테스트 4개는 재배치 중 역방향 미커밋 anchor와 임시 0 무시, 지연 save에 대한 close 대기,
사용자 이동 없는 close, 저장 실패와 재시도를 다룬다. `scripts/required-tests.json`에도 identity를 등록한다.
정확 실행 결과·native XML·수정 전 실패 증거는 [TESTING](../TESTING.md)의 M04 항목과
`docs/evidence/m04-reflow-validation/`를 따른다. mock save 오류는 실제 disk-full 검증이 아니다.


## 2026-10-11 — 지연 event와 실제 DB 재개방 검증

`RoomProgressRaceTest`에 실제 파일 DB 기반 5개 case를 추가했다. A 지연→B, 이전 세션 event,
역방향 sequence, 동시 duplicate sequence, DELETING/삭제 완료 뒤 event를 deferred barrier로 제어한다.
12회 새 세션 및 DB 재개방 후 epoch/sequence/locator가 유지됨을 검사한다. DB writer 조건 자체는 변경하지 않았다.
범위와 잔여 통합 gate는 [M04 safety matrix](../M04_SAFETY_MATRIX.md)를 따른다.

직전 `16e4615` actual host 준비 실패는 progress DB 오류가 아니라 import progress의 worker UI 게시 경로에서
발생한 CalledFromWrongThreadException이다. StateFlow의 thread safety와 Compose test inline observer의
Main view 소유권은 별개다. UI 게시만 Main으로 옮기고 IO 및 suspend cancellation을 유지한다.
수정 전 unit 재현 및 raw host 실패를 보존한다. device/host 재실행 결과는 TESTING을 따른다.


수정 source `96c15aa`는 local/remote build unit 46/shared JVM 50와 actual ordinary Android 25 및
외부 host 2 stages를 통과했다. 원본 host는 PID 5640 종료/absent/cold PID 5688과 commit
offset 11607/epoch 1/sequence 2 보존·서재 Continue 복원을 확인한다. `16e4615` prepare 실패 원본도 보존했다.
이전 실패 경로 수정 후 이번 flow가 통과했다는 범위로 기록하며, 전체 M04 SAFE gate는 미완료다.


## 2026-10-11 — SAFE-09 연속 이동 검증 계약

60초 가상 시간에 100ms 간격 anchor를 주입하는 controller 회귀를 추가한다. 1초 sampling 60개와
250ms settled save를 구분하여 debounce-only 구현이면 실패하게 한다.
Android에서는 실제 pointer를 60초 이상 유지하는 Reader 회귀와 독립 Room observer를 사용한다.
실제 입력 시각/측정 anchor/commit sequence를 fixture-only JSON에 기록한다.
저장 SQL/앱 writer 구현은 변경하지 않는다. 입력 최대 공백 1초와 commit 관찰 최대 공백 5초는 emulator 검사 한도다.
[측정 보고서](../M04_COMMIT_LATENCY.md)에 관찰 간격과 SQL acknowledgement latency의 차이,
실기기 및 미측정 항목을 명시한다. 최종 결과는 TESTING/native XML/raw JSON을 따른다.

실제 `78e45f5` 기기 회귀는 입력 600회에도 anchor 변화 0회로 실패했다. pinned Foundation 1.9.3은
작은 scroll에서 LazyListMeasuredItem.offset을 제자리 변경하고 visibleItemsInfo를 재사용한다.
snapshotFlow에 item 객체를 반환하면 같은 item 내부의 이동이 distinct 비교에서 사라진다.
first item의 index/offset을 immutable 값으로 캡처하여 각 측정 이동을 관찰한다.
sampling/settled 간격, READY/복원 gate, SQL 조건부 writer와 테스트 기준은 유지한다.
실패 자료를 보존하고 같은 Android 회귀를 다시 실행하며 실제 결과 전에는 SAFE-09 PASS로 처리하지 않는다.

최종 source `73276fe` actual 60초 회귀는 anchor 226회/commit 관찰 46회/최대 관찰 공백 2,034ms로 PASS다.
ordinary 26 및 외부 host 2 stages도 PASS다. host 준비는 마지막 fling/layout 안정화 후 expected를 캡처한다.
`78e45f5` 이동 누락 FAIL과 `14ce759` host 준비 timeout 원본은 보존하며, emulator 검사 범위를 넘겨
SQL acknowledgement/실기기 성능이나 전체 M04 완료로 확대하지 않는다.

## 2026-10-11 — SAFE-07 native SQLite 용량 오류 검증

안전하게 반복 가능한 실제 엔진 실패를 얻기 위해 fixture DB의 max_page_count를 제한하고 테스트 전용
AFTER trigger의 bounded 할당으로 native SQLITE_FULL(13)을 발생시킨다. 수동 예외 주입과 구분한다.
JVM은 commit/activation의 rollback과 reopen·동일 event 재시도를 확인하고 Android는 실제 Reader의
오류 표시·기록/원본 보존·자동 재시도를 검증한다. finally에서 한도와 fixture schema를 복원한다.
앱 소유 Room pool은 닫지 않는다. production writer/schema 변경 없이 검증을 추가한다.
이 선택은 OS filesystem ENOSPC나 WAL/fsync 실패를 대체하지 않으며 범위는 [SAFE-07 보고서](../M04_SQLITE_FULL.md)를 따른다.

source `bf7589d`의 local/remote unit 47/53, actual ordinary 27/host 2 stages가 통과했다.
native DB 세 실패의 code 13, actual Reader 오류 표시·기록/파일 보존·용량 복원 후 자동 retry와
fixture 한도/schema 정리까지 확인했다. 이 증거는 native page-limit FULL 범위이며 OS ENOSPC gate는 남는다.

### 2026-10-11: 실제 filesystem ENOSPC 검증 분리

page-limit FULL은 OS 공간 부족을 대신하지 않으므로 별도 8MiB tmpfs의 bounded 할당으로 실제 errno 28을 만든다.
production WAL writer의 기존/첫 저장과 activation에서 code 13 및 전체 record/file 보존을 확인한다.
환경 의존 검사는 별도 Kotlin compilation/필수 identity로 실행하며 namespace 부재는 BLOCKED_ENV다.
기본 JVM suite의 skip으로 숨기거나 실제 Android storage/fsync/전원 차단 보증으로 확대하지 않는다.
자세한 근거와 재현 명령은 [보고서](../M04_FILESYSTEM_ENOSPC.md), [TESTING](../TESTING.md)을 따른다.

### 2026-10-11: restoration activation 등록의 원자성

기기 DB owner 회귀의 REJECTED_STALE 조사에서 완료 callback이 job 대입 전에 겹치면 두 epoch를 만드는 경로를
확인했다. 새 단위 테스트가 수정 전 activation 2회로 실패한다. restored() 진입을 직렬화하고 LAZY job을
필드에 등록한 뒤 시작해 동시/즉시 재진입에서 기존 작업을 보게 한다. DB stale 거부 기준은 유지한다.
기기 실패 raw만으로 특정 scheduling 순서를 확정하지 않으며 수정 후 원격 검증은 TESTING을 따른다.

최종 `ebee772`는 local/remote 48/53 및 실제 ENOSPC 3, actual 27/host 2 stages PASS다.
세부 source/evidence/미검증 경계는 [TESTING](../TESTING.md)의 최신 절을 따른다.


### 2026-10-17: SAFE-01 실제 Reader 조회 실패 증거

production API에 fault hook을 추가하지 않고, 테스트 전용 schema의 가역적 query-column rename으로
native SQLite 조회 오류를 만들었다. malformed JSON도 실제 repository decode 경로에서 별도 검증한다.
앱 소유 pool은 유지하고 finally에서 fault를 복원한다. 반복 UI retry 중 기록/metadata/file 보존,
복구 후 측정 위치 복원 및 정확히 1회 activation/new epoch save를 검사한다.
`0e2605e` actual ordinary 29/host 2 stages와 local/remote unit 48/53, remote ENOSPC 3 PASS다.
bookmark 미구현 범위는 NOT_RUN이다. [보고서](../M04_PROGRESS_READ_ERRORS.md)와 [TESTING](../TESTING.md)을 따른다.
