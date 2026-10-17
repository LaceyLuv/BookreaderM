# M04 진행도 안전성 검증 현황 — 2026-10-17

M04는 **IN_PROGRESS**다. 아래는 검사 수준별 증거이며 하나의 수준 통과를 전체 시나리오 통과로 취급하지 않는다.
최신 로컬 결과와 소스 fingerprint는 [summary](evidence/m04-ui-races-validation/summary.json),
정확 명령/exit는 [TESTING](TESTING.md), 제품 계약은 [개정 계획 부록 B](BookReader_Mobile_v1_1_Implementation_Plan.md)를 따른다.

| SAFE | 확보한 검사 | 남은 검증 |
|---|---|---|
| 01 조회 오류 | controller Error 이후 activate/save 금지; DB 손상/미지원 locator 보존; `0e2605e` actual Reader native query error/malformed locator의 반복 retry·전체 기록/파일 보존·측정 위치 복원·새 epoch save PASS ([보고서](M04_PROGRESS_READ_ERRORS.md)) | bookmark entity/DAO/UI 미구현으로 불변 검사는 NOT_RUN; 모든 native 오류 종류를 검증한 것은 아님 |
| 02 A 지연 저장→B | `RoomProgressRaceTest.delayedBookASaveCannotChangeNewlyActiveBookB`: A event 전달을 보류하고 B를 활성화/commit한 뒤 A 전달; 두 레코드 및 B metadata, DB 재개방 확인 | `85653ad` actual A/B 6회·닫기 writer barrier PASS; [UI 보고서](M04_UI_RACES.md) |
| 03 이전 세션 | `rapidSameBookSessionsRejectDelayedOldEventsAcrossDatabaseReopens`: 12회 새 epoch 활성화/commit 뒤 이전 event 전달; 매회 DB 재개방과 locator/epoch/sequence/metadata 확인 | `85653ad` actual 복원 중 close/reopen 5회·해제 후 불변·정상 재열기 PASS |
| 04 역방향 순서 경쟁 | `delayedForwardEventCannotOverwriteNewerBackwardSequence`, `concurrentDuplicateSequenceCommitsExactlyOnce`: 지연된 forward 거부, 같은 sequence 동시 save는 정확히 하나만 commit | `85653ad` actual 역방향 2회 및 pending save barrier PASS |
| 06 삭제 중 작업 | `delayedSavesDuringAndAfterDeletionNeverRecreateBookOrTouchOtherBook`: DELETING/삭제 완료 후 지연 save 거부, 다른 책 불변, DB 재개방 | `85653ad` 실제 32MiB decode/index 중 model 삭제·partial 정리·reopen 거부 PASS; visible 삭제 버튼 flow와 구분 |
| 07 disk-full | native SQLite page-limit FULL(13): 기존 update/첫 insert/activation 3개 rollback·reopen·retry PASS ([보고서](M04_SQLITE_FULL.md)); `ebee772` actual Reader page-limit 오류 UI·기록/관리 파일·자동 retry PASS; Linux 8MiB tmpfs 실제 ENOSPC/WAL 전용 3개 local/remote PASS ([보고서](M04_FILESYSTEM_ENOSPC.md)) | Android ext4/f2fs·fsync·rollback journal은 NOT_RUN |
| 09 60초 연속 이동 | 가상 60초 sampling 60회/settled save PASS; 값 캡처 수정 뒤 `73276fe` actual API36 60,001ms/anchor 226/commit 46/최대 관찰 공백 2,034ms PASS ([보고서](M04_COMMIT_LATENCY.md)); `78e45f5` FAIL 보존; 최신 `0e2605e` 60,002ms/60 commits/최대 관찰 공백 1,101ms 회귀 PASS | SQL write-ack/실기기 5MiB·50MiB 성능 별도 NOT_RUN |
| 10 외부 종료 | `0e2605e` ordinary 29/host 2 stages PASS; PID 5794→외부 force-stop→absent→cold PID 5842, 마지막 commit의 서재 Continue 복원 | 미커밋 화면 위치·전원 차단/저장장치 손상은 보장하지 않음 |
| 12 reflow | `16e4615`의 신규 controller 4개: pending backward flush/close 대기/임시 0 차단/오류 재시도 | 실제 설정·ON_STOP 중 reflow와 임시 callback 경로 |

## 실제 DB 경쟁 테스트의 경계

[RoomProgressRaceTest](../shared/src/jvmTest/kotlin/org/bookreader/mobile/repository/RoomProgressRaceTest.kt)는
파일 기반 Room/bundled SQLite를 사용한다. deferred barrier는 immutable event가 writer에 **도착하는 시점**을 제어하며,
SQL transaction 내부가 정지한 것처럼 주장하지 않는다. duplicate sequence case는 두 실제 save를 같은 gate에서 시작한다.
대기 시간에 기대는 sleep은 없고, structured child를 종료/취소한 뒤 caller-owned DB를 닫는다.
각 case는 DB close/open 이후 전체 persisted record 또는 책의 부재까지 확인한다.
이 검사는 앱의 UI close/reopen, 프로세스 kill, 실제 디스크 부족, 실기기 성능을 대체하지 않는다.

## 새로 관측한 host 준비 실패

`16e4615`의 [device run 38109705802](https://github.com/LaceyLuv/BookreaderM/actions/runs/38109705802)는
ordinary 25개를 통과했으나 prepareDurableReader가 `CalledFromWrongThreadException`으로 실패했다.
raw stack은 IO provider progress callback → AppViewModel StateFlow update → Compose test inline continuation → worker에서 view layout을 보여준다.
[원본 실패](evidence/m04-race-validation/previous-16e4615-device/host-process-proof/prepareDurableReader.log)와 native ordinary XML을 보존했다.
외부 force-stop 및 cold verify 단계는 그 실행에서 NOT_RUN이다.

AppViewModel은 suspend progress callback의 UI 상태 게시만 `Dispatchers.Main.immediate`로 전환한다.
파일 작업은 IO에 남고 새 독립 coroutine/Job을 만들지 않아 부모 import의 취소·완료 순서를 유지한다.
새 unit은 실제 별도 worker와 inline collector로 수정 전 publisher-thread 실패를 재현하며,
수정 후 Main 게시/IO 작업 유지/오류 phase 보존을 확인한다. 이것만으로 actual host 성공을 선언하지 않는다.


수정 후 `96c15aa` [device run 38110471069](https://github.com/LaceyLuv/BookreaderM/actions/runs/38110471069)는
ordinary 25 및 actual host 2 stages를 통과했다. source SHA, native XML, exact raw stage/PID 종료·재시작 proof는
[새 device summary](evidence/m04-race-validation/remote-96c15aa-device/summary.json)에 연결된다.
로컬·원격 build 모두 unit 46/shared JVM 50 PASS다. 그 결과는 이번 DB 경쟁 검사를 뒷받침하지만
SAFE-01/02/03/04/06/07/12의 위 잔여 통합·장애 gate 및 SAFE-09 실기기 측정 및 Astra 신규 최종 검토를 완료한 것은 아니다.

## activation 중복 후속

`a3ee33b` actual DB owner REJECTED_STALE FAIL과 host NOT_RUN을 보존했다.
중복 callback의 activation 2회를 별도 단위 재현해 synchronized 진입/LAZY job 선등록으로 보완했다.
local 48/53/ENOSPC 3 및 최종 `ebee772` remote 48/53/ENOSPC 3, actual 27/host 2 stages PASS다.
이는 전체 UI rapid reopen/close 중 activation 취소의 완료 판정은 아니다.
