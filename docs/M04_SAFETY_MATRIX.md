# M04 진행도 안전성 검증 현황 — 2026-10-11

M04는 **IN_PROGRESS**다. 아래는 검사 수준별 증거이며 하나의 수준 통과를 전체 시나리오 통과로 취급하지 않는다.
최신 로컬 결과와 소스 fingerprint는 [summary](evidence/m04-race-validation/summary.json),
정확 명령/exit는 [TESTING](TESTING.md), 제품 계약은 [개정 계획 부록 B](BookReader_Mobile_v1_1_Implementation_Plan.md)를 따른다.

| SAFE | 확보한 검사 | 남은 검증 |
|---|---|---|
| 01 조회 오류 | controller의 Error 이후 activate/save 금지; 실제 DB의 손상/미지원 locator 원본 보존 회귀 | 실제 Reader 오류·재시도 UI와 bookmark 불변을 함께 확인 |
| 02 A 지연 저장→B | `RoomProgressRaceTest.delayedBookASaveCannotChangeNewlyActiveBookB`: A event 전달을 보류하고 B를 활성화/commit한 뒤 A 전달; 두 레코드 및 B metadata, DB 재개방 확인 | 실제 UI A/B 빠른 전환과 controller 종료/작업 취소 |
| 03 이전 세션 | `rapidSameBookSessionsRejectDelayedOldEventsAcrossDatabaseReopens`: 12회 새 epoch 활성화/commit 뒤 이전 event 전달; 매회 DB 재개방과 locator/epoch/sequence/metadata 확인 | 실제 Reader의 같은 책 close/reopen 및 활성화 중 취소 |
| 04 역방향 순서 경쟁 | `delayedForwardEventCannotOverwriteNewerBackwardSequence`, `concurrentDuplicateSequenceCommitsExactlyOnce`: 지연된 forward 거부, 같은 sequence 동시 save는 정확히 하나만 commit | UI 이벤트 캡처부터 DB까지의 지연·역방향 통합 |
| 06 삭제 중 작업 | `delayedSavesDuringAndAfterDeletionNeverRecreateBookOrTouchOtherBook`: DELETING/삭제 완료 후 지연 save 거부, 다른 책 불변, DB 재개방 | save와 decode/index/file cleanup 동시 실행 전체 경로 |
| 07 disk-full | 기존 저장 오류 UI 및 닫힌 DB 오류 보존 회귀 | 실제 SQLite/storage disk-full; 오류 주입은 disk-full 증거가 아님 |
| 09 60초 연속 이동 | 기존 controller의 약 1.5초 sampling/역방향 settled save 검사 | 60초 연속 이동·주기 실제 commit과 측정 기기/빌드/fixture가 있는 latency report |
| 10 외부 종료 | `96c15aa` ordinary 25/host 2 stages PASS; PID 5640→외부 force-stop→absent→cold PID 5688, commit offset 11607/epoch 1/sequence 2 복원 | 미커밋 화면 위치·전원 차단/저장장치 손상은 보장하지 않음 |
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
SAFE-01/02/03/04/06/07/09/12의 위 잔여 통합·장애·측정 gate 및 Astra 신규 최종 검토를 완료한 것은 아니다.
