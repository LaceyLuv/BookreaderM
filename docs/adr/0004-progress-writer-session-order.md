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
서재 복귀와 lifecycle stop에서 flush를 시도하며 실패는 UI에 표시한다. 현재 `persistStable`은 READY에서만 쓰므로
REFLOWING 중 close/ON_STOP에서는 마지막 commit을 보호하지만 reflow 직전의 아직 미커밋 이동을 flush하지 못할 수 있다.
이 최신 미커밋 이동의 flush barrier 보장은 M04에서 해결·검증할 잔여 사항이다. 새 프로세스는 서재에서 시작하고,
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
