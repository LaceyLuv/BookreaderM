# M04 실제 UI 경쟁·취소 검증 — 2026-10-17

검증 source는 `85653ada7259c9c80329ca759e5fc43df870cfff`다. 이 범위는 PASS이며 전체 M04는 IN_PROGRESS다.
[원본 evidence summary](evidence/m04-ui-races-validation/summary.json)를 기준으로 한다.

## 발견한 결함과 수정

닫기에서 flush를 먼저 기다리면 활성화가 보유한 progress mutex와 실제 Room writer 대기 때문에
활성화를 취소하지 못했다. 신규 controller 회귀는 수정 전 3초 timeout으로 FAIL이다.
`14fb88c`에서 opening/restoration을 먼저 취소했으나 actual Android의 첫 close/reopen은 30초 timeout으로 FAIL했다.
이때 ordinary 33개 중 1개 실패, host는 NOT_RUN이며 실패 XML/report를 보존했다.

고정 Room 2.8.3의 공식 `DBUtil.android.kt`/`RoomDatabase.android.kt`는 DAO 실행 때 DB 소유 Job을
포함한 query context로 전환한다. 호출자 취소가 해당 연결 대기를 즉시 중단시키지 않는 조건을
단위 회귀로 모델링했다. `ReaderActivationGate`는 ViewModel 소유 sibling 작업과 취소 가능한 await를
분리한다. admission mutex로 실제 Room에 들어가는 활성화는 하나로 제한하고 취소된 대기 요청은 해제한다.
연결 획득 후 및 transaction commit 직전에 원래 Reader 요청의 생존 여부를 확인한다.
availability 복구와 activation은 한 transaction으로 묶어 취소되면 모두 rollback한다.
앱 소유 DB를 닫거나 library/progress를 초기화하지 않는다. 이미 수락한 저장은 기존 flush barrier를 따른다.

## 실제 Android 범위

- A/B를 6회 번갈아 열고 실제 swipe·역방향 2회를 실행했다. writer가 점유된 동안 닫기가 기다리고,
  해제 후 측정한 위치가 저장되며 다른 책 전체 metadata/progress/file이 보존된다.
  B는 A flush 뒤 열리므로 이것만으로 A 이벤트가 B 이후 도착하는 DB 경쟁을 주장하지 않는다. 그 경로는 기존 DB race 검사다.
- 같은 책의 복원 중 실제 열기/닫기 5회를 수행했다. writer 해제 후에도 취소된 요청이 epoch/lastRead/readOrder/progress를
  바꾸지 않는다. 정상 재열기에서만 epoch가 정확히 1 증가하고 이전 offset 5149를 실제 TextLayout으로 복원한다.
- 실제 portrait→landscape와 Activity CREATED(ON_STOP)를 실행했다. writer 점유 중 수락한 역방향 anchor 5617
  (이전 commit 7500)을 같은 epoch 1로 commit하고 resume 후 측정된 첫 행에서 확인했다.
  이는 process death나 ON_STOP만을 유일한 저장 trigger로 분리한 검사가 아니다.
- 자체 생성 32MiB TXT의 실제 decode/index가 16384 UTF-16까지 진행된 시점에 AppViewModel 삭제 경로를 호출했다.
  book/progress/관리 복사본/partial cache가 제거되고 다른 책은 보존된다. 삭제 시작 후 reopen도 거부한다.
  보이는 Reader 삭제 버튼을 누른 검사는 아니며, 이미 큐에 든 navigation은 별도 기존 단위 검사가 담당한다.

## 실행 결과와 제한

| 검사 | 결과 |
|---|---|
| 로컬 full CI | Android unit 50 / shared JVM 53, 실패·오류·skip 0, APK/test APK/lint/schema PASS |
| 원격 build 39004200808 | 50 / 53 / 실제 ENOSPC 3 PASS, artifact 12090456186 |
| 원격 API36 device 39004200823 | ordinary 33 / 외부 host 2 stages PASS, artifact 12089554762 |
| 외부 host | PID 5870→force-stop→absent→cold PID 5916, 마지막 commit의 서재 Continue 복원 |
| 로컬 device | authorized device 없음, exit 78/BLOCKED_ENV |

아티팩트 ZIP SHA-256와 GitHub digest, workflow source SHA, 필수 test identity/native XML,
새 UI 보고서의 자체 fixture hash와 원본 host 단계를 별도 확인했다. README는 변경하지 않았다.
정확 명령은 [commands.json](evidence/m04-ui-races-validation/commands.json)에 있다.

M05 타이포/페이지 reflow와 M06 북마크 불변은 해당 구현 이후에 검증한다.
Android 실제 저장장치 ext4/f2fs·fsync, 실기기 성능/메모리, 신규 Astra 검토는 NOT_RUN이다.
사용자의 남은 계획 전체 진행 요청에 따라 이 미완료 상태를 유지하면서 M05 구현으로 이어간다.
