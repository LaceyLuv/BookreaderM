# M06 TXT 검색·북마크·해석 변경 — 2026-10-17

검증 source는 `f208a069f7712a89f610f77750e464332b3ff876`다. main에 구현했고 실행한 범위는 PASS다.
전체 M06은 50MiB 보고서와 인덱싱 중 북마크 허용 조건 검토가 남아 IN_PROGRESS다.
[원본 결과와 요약](evidence/m06-validation/summary.json)을 보존했다.

## 구현과 보존 계약

- literal KMP 검색은 canonical UTF-16의 16Ki 창을 순차 처리한다. query 최대 256 UTF-16,
  batch 50, 화면 결과 최대 1000이며 취소/새 query 결과 token을 검사한다. 대소문자 무시는 ASCII만이다.
  경계 검색 `경계검색😀`는 offset 16382에서 시작하며 실제 화면 이동·일회성 돌아오기를 검증했다.
- Room schema 4 북마크는 book/revision/locator/label을 별도 저장한다. 페이지당 50개, stale session·삭제 후 쓰기를 거부한다.
  malformed/조회 오류는 raw row를 보존하며 성공적으로 읽기 전에는 쓰기를 허용하지 않는다.
- 수동 인코딩은 원본 전체 strict decode와 변경 전후 hash 검증 후 하나의 transaction으로 해석을 전환한다.
  기존 해석의 progress/bookmark를 보존하고 돌아오면 해당 기록을 복원한다. 신규 해석만 0에서 시작한다.
  원본 bytes는 변경하지 않으며 잘못된 UTF-16/검증 취소/transaction 취소는 원래 book과 기록을 보존한다.
- SAFE-01 실제 Reader 조회 실패/손상 locator에서 두 책의 모든 bookmark columns 보존을 추가 검증했다.

## 실행 결과

| 범위 | 결과 |
|---|---|
| local full CI | Android unit 55/shared JVM 67, APK/test APK/lint/schema PASS |
| [remote build](https://github.com/LaceyLuv/BookreaderM/actions/runs/39006450888) | 55/67/실제 ENOSPC 3 PASS |
| [remote API36](https://github.com/LaceyLuv/BookreaderM/actions/runs/39006450905) | ordinary 38/외부 host 2 stages PASS |
| native UI | 검색 경계·돌아오기·북마크 reopen, UTF8↔UTF16LE 기록 복원, 무효 인코딩 원본 보존 PASS |
| M05 확장 | SERIF/size/line/letter/margin, 실제 OS fontScale·density 변경 후 anchor/record 보존 PASS |
| 페이지 엔진 | fixture별 임의 지역 20개×5회 앞뒤 이동, coverage/hash 및 bounded cache PASS |

모든 위 suite는 failures/errors/skipped 0이다. artifact ZIP SHA256을 GitHub digest와 비교했고 원본 XML과
필수 test identity, source SHA, schema bytes, ENOSPC errno28/native13, 외부 PID 교체를 확인했다.
페이지 PNG 두 장을 직접 확인했다. 첫 페이지는 READY 본문과 활성 이전/다음 버튼을 보여 준다.
확장 배율 화면은 본문 reflow가 보이며 큰 배율의 제어 영역 도달성은 M07에서 추가 검증한다.

초기 bookmark 조회 오류 fixture의 SQLite column metadata 잔류 실패는 `initial-failures` 아래 보존한다.
실제 SQL WHERE column 오류로 교체해 실패·복구를 검증했다. 테스트를 삭제하거나 skip하지 않았다.

## 미실행·후속

50MiB 실기기 시간/PSS, 실제 TalkBack, OS 저장장치 fsync 장애와 신규 독립 최종 검토는 NOT_RUN이다.
현재 Reader는 strict 전체 decode/진행도 복원 완료 후 READY가 되므로 준비 중 bookmark 버튼을 비활성화한다.
불완전 검증 본문에 durable locator를 쓰지 않는 정책과 계획의 인덱싱 중 bookmark 조건을 별도로 검토한다.
M07 메뉴/제스처/취소 슬라이더/창 설정 및 접근성을 이어서 구현한다.
