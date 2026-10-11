# M04 SAFE-07 — SQLite 용량 오류와 기록 보존

## 범위

파일 기반 Room/bundled SQLite의 실제 `SQLITE_FULL`(13)을 사용한다. 테스트가 예외나 repository 오류 값을
직접 던지지 않는다. `PRAGMA max_page_count`를 현재 page count로 제한한 뒤, 테스트 전용 trigger가
정상 progress/activation 쓰기 뒤 같은 transaction 안에서 bounded `zeroblob`을 할당하게 한다.
부가 할당 크기는 `(page_count + 4) * page_size`, 최대 1MiB로 제한한다. 마지막 정상 기록은 그 전에 commit한다.
trigger/부가 테이블은 테스트 전용이며 production schema·migration·writer를 바꾸지 않는다.
공식 Room 2.8.3/SQLite 2.6.1 API URL과 source hash는
[evidence](evidence/m04-sqlite-full-validation/official-api.json)에 있다.

이 방식은 native SQLite의 DB page 한도 초과, transaction rollback, 앱 오류 처리·재시도를 검사한다.
파일시스템의 남은 공간을 소진하거나 ENOSPC를 재현한 것이 아니므로 WAL/journal 생성·fsync 단계의
실제 저장장치 부족, 전원 차단 및 OEM 동작까지 검증했다고 주장하지 않는다. 해당 OS/실기기 검증은 NOT_RUN이다.

## 검사

- JVM 실제 DB 3개: 기존 progress UPDATE, 첫 progress INSERT, 새 세션 활성화 중 metadata INSERT에서
  native code 13과 정확 실패 stage를 확인한다. 첫 save는 Missing을 유지하고, 기존 save는 전체 progress record를
  유지한다. activation은 먼저 수행한 book epoch/lastRead/readOrder 갱신까지 rollback해야 한다.
- 각 실패 뒤 두 책 metadata/진행도/global read_order와 부가 payload 0행을 확인하고 DB를 close/reopen해
  보존을 확인한다. 용량을 복원한 뒤 같은 event 또는 activation을 다시 요청해 성공을 검사한다.
  trigger를 그대로 유지하므로 용량 복원 뒤 동일한 실제 할당이 성공해야 한다. 이전 event 중복은 stale로 거부한다.
- 실제 Android Reader는 자체 생성 TXT 두 권을 import한다. 앱의 동일한 single writer connection에 한도를 적용하고
  native probe의 code 13을 확인한다. 실제 스크롤에서 저장 오류 UI/READY/마지막 durable 기록·다른 책·관리 파일 hash를
  확인한다. 한도 복원 뒤 새 사용자 입력이나 직접 flush 없이 자동 재시도가 pending anchor를 commit하고 오류를 지워야 한다.
  finally에서 원래 한도와 fixture schema를 복원하며 앱 소유 DB는 닫지 않는다.

## 증거와 판정

신규 JVM 3개는 native XML에서 PASS다. 최종 local/remote 및 Android 결과는
[TESTING](TESTING.md), [summary](evidence/m04-sqlite-full-validation/summary.json)를 따른다.
기기 JSON `sqlite-full-report.log`는 fixture hash/bytes, 기기 identity, page 한도, native probe code,
실패 시 기록·파일 보존, 오류 표시 및 자동 재시도 결과를 보존한다. 사용자 책 본문·URI는 기록하지 않는다.
동일 native test identity와 JSON verifier가 모두 통과해야 실제 Android PASS로 처리한다.
M04 전체와 SAFE-07 OS ENOSPC 범위는 이 검사만으로 완료 처리하지 않는다.

## `bf7589d` 확정 결과

검증 source `bf7589dcc018c57fc43101a16198cc3afd12b9cd`는 local/remote 단위 47/53,
actual API36 ordinary 27 및 외부 host 2 stages 모두 PASS다. 실패/오류/skip은 0이다.
[기기 run](https://github.com/LaceyLuv/BookreaderM/actions/runs/38126287699),
[원본 report](evidence/m04-sqlite-full-validation/remote-bf7589d-device/sqlite-full-report.log),
[native DB XML](evidence/m04-sqlite-full-validation/remote-bf7589d-build/native-shared-jvm/TEST-org.bookreader.mobile.repository.RoomProgressFullTest.xml)을 보존했다.

| 관찰 | 결과 |
|---|---|
| JVM fixture | 14 pages×4,096 bytes, growth 73,728 bytes; native code 13 세 건 |
| Android fixture | 16 pages×4,096 bytes, growth 81,920 bytes; native probe code 13/SQLException |
| 실패 전 마지막 commit | offset 4,964 / epoch 1 / sequence 1 |
| 오류 중 화면의 pending anchor | 6,625; saveError UI 표시, DB 기록은 이전 값 그대로 |
| 용량 복원 후 자동 retry | offset 6,625 / sequence 3; 오류/diagnostic 제거, 같은 trigger의 할당 1행 성공 |
| 다른 책·원본·metadata | 실패 때와 retry 뒤 보존; fixture 한도/schema 복원 PASS |

자체 생성 `reader-full`은 611,999 bytes, SHA-256
`2a60740d85202c12c334a9a0f17924b21ee56552e4473b4183dacc887b81fcfb`다.
두 번째 `reader-full-other` hash는 `410e497bd48ed1048c8880390d580b9b13ccdc2e9f650bf2dcdfe581e982e078`이다.
독립 생성한 두 fixture hash와 실제 import 보고가 같다. API36/x86_64 기기 fingerprint는
`google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`다.
