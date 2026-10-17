# M04 SAFE-01 Reader 조회 오류와 재시도 — 2026-10-17

M04는 IN_PROGRESS다. 실제 Android Reader의 DB 조회 오류 및 손상 locator 처리에 관한 검증이다.
최종 결과는 [TESTING](TESTING.md)과 [evidence](evidence/m04-read-error-validation/summary.json)를 따른다.

## 장애 방식과 보존 계약

`TxtReaderFlowTest`의 새 필수 검사 두 개는 자체 생성 TXT 두 권을 실제 import하고,
실제 화면 스크롤로 0보다 큰 위치를 commit한 뒤 서재로 돌아간다.

- `nativeProgressQueryFailureShowsRetryWithoutChangingRecords`: 앱 소유 Room writer에서
  `reading_progress.bookId` 열을 임시 이름으로 바꾼다. 실제 DAO의 WHERE 조회가 native
  SQLite code 1로 실패하도록 만들며 수동 Error 반환이나 borrowed DB close는 사용하지 않는다.
  열 이름 외 모든 progress 값은 동일하다. finally에서 이름을 복원한다.
- `malformedLocatorShowsRetryWithoutReplacingStoredRecord`: 해당 fixture row의 locatorJson만
  잘못된 JSON으로 바꾼다. real Room repository가 INVALID_RECORD를 반환하며, 오류 중에
  앱이 이 값을 초기 위치나 다른 기록으로 덮어쓰지 않는지 검사한다. 복구는 테스트가 원래 JSON을
  돌려놓은 뒤 실제 UI 재시도로 한다. 앱의 자동 손상 복구 기능을 주장하지 않는다.

실제 오류 UI와 재시도 버튼, LOAD_PROGRESS 진단, native 경로의 READ_PROGRESS 단계를 확인한다.
실패한 재시도 두 번마다 1.25초(정상 sampling 주기보다 긴 관찰 구간)를 두고 모든 progress의
9개 저장 열, 모든 book 모델 값(epoch/lastRead/readOrder 포함), 전역 read_order, 두 관리 파일의
SHA-256이 유지되는지 확인한다. 오류 중 본문 UI와 stable anchor는 생기지 않아야 한다.

장애를 해제하고 UI 재시도로 READY에 진입하면 실제 TextLayout의 첫 표시 줄 좌표가 이전 commit과
일치해야 한다. 원래 progress 전체 값과 다른 책은 그대로이며, 성공한 복원만 epoch/read_order를
각 1회 증가시킨다. 이어 실제 swipe가 새 epoch로 저장되는지도 확인한다.
앱 소유 pool을 닫지 않는다. 독립 observer pool은 기존 fixture lifecycle에서 닫으며
실패 경로에서도 fault를 복원하고 실제 DAO 조회가 다시 가능한지 확인한다.

## 범위 제한

이 장애는 테스트 DB의 가역적 schema 불일치와 malformed JSON이다. 실제 I/O 장치 고장,
DB 파일 물리 손상, lock timeout 또는 모든 SQLite 오류를 대표하지 않는다.
현재 Room v2에는 bookmark entity/DAO/UI가 없다. SAFE-01 bookmark 불변 검증은 NOT_RUN이며
M06 구현 이후에 진행해야 한다. unsupported locator의 기존 JVM 검사는 이번 실제 UI 검사의 대상이 아니다.
실기기 성능, 신규 Astra 검토 및 전체 SAFE matrix 승인도 별도다.
production 코드·schema·migration 변경 없이 테스트와 CI 증거 검증을 추가했다.

## 재현

테스트 전용 API36 emulator에서 `./scripts/device-check.sh`를 실행한다.
ordinary 필수 manifest는 기존 27개와 신규 2개, 총 29개다. 별도 host 2 stages는 외부 adb force-stop을 포함한다.
스크립트는 native XML 및 두 JSON report를 검증하며, 오류/skip/누락을 성공으로 처리하지 않는다.
`reader-query-error`, `reader-locator-error`, `reader-read-other`는 TestDocumentsProvider의 자체 생성 문장
6,000줄이다. 각 파일의 bytes/SHA-256 및 API/build fingerprint는 raw report에 보존한다.

## 최초 실행 실패와 수정

`199e9b9`의 actual API36 run 38999665795는 ordinary 29개 중 새 native query 검사 한 개가
line 488에서 실패했다. malformed locator를 포함한 나머지 28개는 통과했으며 host stages는 NOT_RUN이다.
원인은 예외 클래스 이름의 고정 문자열이었다. 공식 AndroidX SQLite 2.6.1의 Android 구현은
`public actual typealias SQLiteException = android.database.SQLException`이다.
따라서 Kotlin `is SQLiteException` 검사는 통과하지만 런타임 simpleName은 `SQLException`이다.

`0e2605e`는 native probe에서 관측한 타입을 실제 Reader 진단과 대조하도록 변경했다.
CI report verifier도 같은 Android 타입을 검사한다. native code 1, 진단 단계, 전체 기록·파일 보존,
복원 위치와 새 세션 저장의 검사는 그대로다. 실패 원본/XML 및 fixture 복원 결과를
[evidence](evidence/m04-read-error-validation/remote-199e9b9-device-failed/summary.json)에 보존했다.
[공식 source 기록](evidence/m04-read-error-validation/sqlite-api-source.json)도 함께 남겼다.
