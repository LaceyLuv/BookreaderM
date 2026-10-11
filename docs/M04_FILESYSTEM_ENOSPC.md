# M04 SAFE-07 — 실제 파일시스템 ENOSPC

Linux의 별도 user/mount namespace 안에 새 8MiB tmpfs를 만들고, 그 안의 실제 Room/bundled SQLite WAL DB를
검사한다. production writer·schema·UI는 변경하지 않는다. SQLite page 한도나 trigger, mock 예외를 사용하지 않는다.
기존 page-limit 검사와 Android 오류 UI 검증은 [별도 보고서](M04_SQLITE_FULL.md)의 당시 결과다.

## 실패와 복구 계약

1. 자체 생성 TXT 두 개(각 2,700 bytes)와 두 책의 metadata/진행도를 생성한다. 마지막 정상 commit을 먼저 확인한다.
2. `journal_mode=wal` 및 제한하지 않은 `max_page_count`를 검사한다. `wal_checkpoint(TRUNCATE)` 성공과 WAL 0 bytes를
   확인해 기존 WAL 공간의 재사용으로 실패가 회피되지 않게 한다.
3. helper는 전용 mount source/type/8MiB 용량/대상 경로를 확인한 뒤, 최대 용량+한 블록만 쓰기를 시도한다.
   실제 OS `OSError.errno=28`과 남은 공간 0을 요구한다. 작업 폴더/공유 파일시스템을 채우지 않는다.
4. 실제 production writer의 기존 progress UPDATE, 첫 INSERT, 세션 ACTIVATE를 각각 실행한다.
   native `SQLITE_FULL(13)`, `DATABASE_UNAVAILABLE`, 정확한 실패 stage를 확인한다.
5. 공간 부족 상태에서 두 책 전체 metadata/진행도/global read_order와 TXT hash가 이전 값 그대로여야 한다.
   첫 저장 실패는 Missing을 유지한다. activation 실패는 epoch/읽기 순서를 증가시키지 않는다.
6. filler만 삭제한 뒤 DB를 close/reopen하여 이전 기록을 다시 확인한다. 동일 event/activation을 재시도하고,
   재개방 뒤 성공 기록과 다른 책/파일 보존을 확인한다. 역방향 offset 80→10도 정상 저장되어야 한다.

fixture마다 finally에서 filler 제거·DB close·자체 파일 정리를 수행한다. shell EXIT trap은 tmpfs를 unmount하고
빈 mountpoint를 제거하며 정리 실패도 nonzero로 처리한다. namespace root 매핑에도 Gradle cache/home은 원래 사용자의
경로를 유지한다. namespace를 사용할 수 없으면 exit 78/BLOCKED_ENV이며 PASS나 test skip으로 바꾸지 않는다.

## 실행과 증거

`source /workspace/toolchains/mobile-env.sh` 뒤 `./scripts/enospc-check.sh`를 실행한다.
별도 Kotlin `jvmEnospcTest` compilation/task이므로 일반 shared JVM 53개에 환경 의존 skip을 섞지 않는다.
전용 3개 identity는 `scripts/required-tests.json`의 `shared-enospc`에서 필수 검사하며, 매번 실제 실행한다.
GitHub build workflow도 이 명령을 필수 단계로 실행하고 원본 XML/로그를 업로드한다.

최신 판정은 [TESTING](TESTING.md)과 [증거](evidence/m04-enospc-validation/)를 따른다.
최초 Gradle DSL 컴파일 오류 및 namespace root의 cache 경로 오류도 보존한다. 둘 다 제품 실패가 아닌
새 harness 설정 실패이며 수정 후 실제 3개 테스트 결과와 구분한다.

## 한계와 다음 작업

검증 범위는 Linux tmpfs에서 실제 공간 할당이 실패한 **WAL 쓰기**와 application transaction 보존/복구다.
Android 내부 ext4/f2fs/OEM, 별도 rollback journal 모드, fsync 시점의 장치 오류, 전원 차단, 공간 부족 상태에서의
새 프로세스 기동은 검증하지 않았다. 공간 확보 **후** DB 재개방이며 process-death 시험으로 부르지 않는다.
이번 TXT는 test-owned 파일이며 Android SAF import/실제 Reader UI ENOSPC 시험으로 확대하지 않는다.
M04는 IN_PROGRESS다. 다음은 SAFE-01 실제 Reader 조회 오류/재시도 UI 또는 실제 Android 저장장치 ENOSPC다.
