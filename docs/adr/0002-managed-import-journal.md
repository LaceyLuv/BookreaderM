# ADR-0002 — 관리 복사본과 import journal

- 날짜: 2026-10-04; 2026-10-09 Room 소유권 보완
- 상태: TXT 내부 알파 구현·검증 채택; `2be610a` local/remote 41/45 및 actual Android 25/host 2 PASS
- 기준: 계획서 §12/§13, TASKS M02

## 선택

`shared`의 `ImportCoordinator`가 Room `import_jobs`와 내부 파일 port를 통해 가져오기를 조정한다.
Android provider와 URI grant 검사는 `AndroidManagedImportFiles`에 둔다. `content://`의 임시 읽기 권한만
사용하며 영구 grant와 광범위 저장소 권한을 요구하지 않는다. VIEW/SEND는 단일 URI로 해석한다.
SEND는 가져오기 확인을 거치고 VIEW는 가져오기 성공 후 읽기를 연다.
filename/URI는 내부 경로에 사용하지 않고 생성 UUID로 staging/final 경로를 만든다.

`NEW → COPYING → VALIDATING → FINALIZING → COMMITTED` journal을 사용한다. bounded stream으로 원본을
복사하며 실제 바이트 수와 SHA-256을 계산하고 256MiB를 강제한다. 성공한 복사본 전체에 strict TXT validation을
수행한다. 수동 인코딩을 기다리는 `VALIDATING` staging은 원본 grant가 사라진 뒤에도 재개할 수 있다.
미리보기는 확정 근거가 아니며 선택된 인코딩으로 전체 원본을 다시 검사한 뒤 commit한다.

관리 작업의 process-wide mutex 안에서 `Files.move(source, target)`의 기본 no-options 경로로 승격한다.
source는 NOFOLLOW_LINKS regular file이어야 하고 target은 없어야 하며 source와 target parent의 `st_dev`를 확인한다.
Android 13/16 libcore 공식 source는 no-replace 확인 뒤 같은 filesystem의 `rename` 경로를 사용한다.
private UUID 경로/전역 mutex/동일 filesystem 조건으로 EXDEV/EISDIR copy fallback을 피하며 양쪽 parent directory를 fsync한다.
`REPLACE_EXISTING`을 요청하지 않고, 기존 target 대체가 허용되는 `ATOMIC_MOVE`도 요청하지 않는다.
공개 API의 모든 경로가 항상 atomic이라는 주장이 아니라 이 관리 경로의 조건과 확인한 libcore 구현에 근거한다.
`FINALIZING` recovery는 실제 fingerprint 및 선택한 encoding/normalization을 재검사한다. book 표시와
COMMITTED 전이는 DB transaction으로 묶는다. 실제 동일 SHA-256+바이트 수 중복은 기존 book identity,
해석 revision 및 독서 기록을 유지한다. 참조된 파일을 cleanup으로 지우지 않는다.

앱 시작 시 recovery를 수행한다. 미완성 COPYING은 소스 재선택이 필요한 중단으로 처리하고, COMMITTED는
terminal 상태여서 나중에 삭제한 책을 다시 생성하지 않는다. 삭제는 먼저 DELETING으로 전환하여 writer를
무효화하고 관리 파일 제거 후 DB 기록을 제거한다. 중단된 삭제도 재개한다. 원본 URI에는 쓰거나 삭제하지 않는다.

Room v1→v2 migration은 기존 books/reading_progress를 보존하며 import_jobs와 app_metadata만 추가한다.
destructive fallback은 사용하지 않는다.

## Android Room 소유권 — 2026-10-09

서재 조회·import/recovery/delete·Reader progress access는 `AndroidDatabaseOwner.borrow(applicationContext)`로
동일한 process-lifetime Room instance/pool을 사용한다. synchronized owner가 최초 instance 생성과 공개를 맡으며,
borrower와 Android `LibrarySession.close`는 해당 DB를 닫지 않는다. Reader/ViewModel/Activity가 닫혀도 owner는 유지되고
프로세스 종료 때 OS가 자원을 회수한다. 실제 query/transaction은 Room과 기존 IO coroutine 경계를 따른다.

`createAndroidDatabase`는 별도 caller-owned fresh factory로 남긴다. 독립 DB/migration/닫힌 DB 오류 테스트는
이 fresh handle의 종료를 직접 책임지며 production owner를 닫지 않는다. library의 scoped lease는 조회 종료 뒤 해제하지만
공유 owner의 close와 같지 않다. import의 process-wide managed-file mutex 및 progress의 조건부 epoch/sequence commit은 유지한다.

이전에는 서재 load/import 작업/Reader operation마다 같은 파일의 fresh Room pool을 열고 닫았다. 하나의 앱 소유권을
명시하여 서로 독립된 pool 수명과 schema 초기화 경로를 줄인다. 이는 수명 정책 선택이며 최신 외부 VIEW 오류의
SQLite 초기화 contention/SQLITE_BUSY 원인을 입증한 결과가 아니다. 새 실제 Android 검증 전에는 오류 해결을 선언하지 않는다.

## 이유와 제약

원본 URI를 독서의 주 저장소로 쓰면 grant 만료·provider 종료가 독서 실패로 이어진다. 내부 영구 복사본을
사용하되 DB와 파일을 하나의 transaction으로 묶을 수 없으므로 durable journal과 멱등 recovery를 선택했다.
파일 이름 기반 중복, INSERT REPLACE, 불완전 cache를 책으로 노출하는 대안은 데이터 보존 계약과 맞지 않는다.

최초 hardlink 승격은 실제 Android SELinux의 `Os.link` denial로 실패했다. 위 공개 `Files.move` 경로로 수정했으며
공식 근거는 `files-move-api/provenance.json`과 Android 13/16 source excerpt에 보존한다.
승격 성공 및 기존 target을 덮어쓰지 않는 actual Android 회귀를 필수 manifest에 추가했다.

동일 프로세스 coordinator들은 mutex로 직렬화한다. 소유한 provider pipe/read는 CancellationSignal과 stream close,
bounded polling/시간 한도로 취소하며 실제 stall/cancel fixture로 검사한다. 비협조적인 원격 Binder/provider 호출 자체가
항상 이 한도 안에 중단된다고 보장하지 않는다. 외부 provider 호출의 중단 가능성과 앱이 소유한 stream의 취소를 구분한다.
DB 오류를 빈 서재로 바꾸지 않는다. 파일 시스템 unit 결과를 실제 SAF 검증으로
대체하지 않는다. checkpoint fault injection, provider/VIEW/SEND, 취소·한도·중복·권한 만료는 별도 실행 gate다.

## 증거

구현: `shared/.../importing/ImportCoordinator.kt`, `ImportDao.kt`, `Migrations.kt`,
`androidApp/.../importing/AndroidManagedImportFiles.kt`, `IncomingDocument.kt`.
실행 결과는 [TESTING](../TESTING.md), [WORKLOG](../WORKLOG.md), `docs/evidence/m02-m03-validation/`에 기록한다.
기존 `fdd9ac1`의 로컬 CI attempt 3 build/DB/migration은 PASS다. 최초 실제 Android provider/picker fixture는
기기 실행에서 실패했으며 원본 log/XML을 보존하고 fixture 수정 뒤 재검증을 기다린다.
이후 실제 `f3d4c38` 실행은 encoding 3 cases PASS, ordinary 23 cases 중 11 failures를 기록했다.
9개의 link denial과 launch intent/asset fixture 원인을 수정한 `cfe17f0`는 로컬 CI5 38/43 및 fixture-only compile/lint PASS다.
수정 뒤 `cfe17f0`의 실제 Android 가져오기 13 cases는 모두 PASS다. system picker/외부 VIEW·SEND/manual encoding,
asset offset/length와 승격 성공/no-overwrite도 포함한다. 전체 ordinary 24 cases 중 Reader 저장 확인 1개가 실패하여
host는 NOT_RUN이었다. 이후 `3ce3386` 원격 build `37172491541`은 38/43 PASS이고 실제 device `37172491508`은
24/1 FAIL이다. 두 ReaderFlow와 encoding 3는 PASS지만 `externalViewReceivesTemporaryGrantAndOpensActualReader`가
가져오기 성공 후 첫 READY 이전 Reader ERROR로 실패했다. 내부 exception은 미수집이며 host 2 stages는 NOT_RUN,
fixture PNG는 NOT_COLLECTED였다. 이 당시 대기 상태는 아래 `2be610a` 최종 PASS로 갱신한다.

`2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf` 변경 후 local full CI6는 actual exit 0,
Android unit 41/shared JVM 45 failure/error/skipped 0, lint 20 Hint 및 앱/test APK/schema PASS, source drift 0이다.
새 actual ordinary Android 25/host 2 stages/fixture PNG 4개는 device `37879616383`에서 PASS다.
외부 VIEW와 Room owner 회귀가 PASS했지만 이전 SQLite 오류의 내부 원인 확정 근거는 아니다.
원본 native XML/PID 종료 proof/raw host stage/PNG는 `../evidence/m02-m03-validation/remote-2be610a-device/`에 보존한다.
