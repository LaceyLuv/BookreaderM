# ADR-0003 — TXT 정규화, 해석 revision과 bounded cache

- 날짜: 2026-10-04
- 상태: TXT 내부 알파 구현·검증 채택; strict grammar host 14/actual Android encoding 3 및 `2be610a` Reader/host PASS
- 기준: 계획서 §9.3/§11.1/§18, TASKS M01 인코딩 부분/M03

## 선택

TXT locator는 정규화된 텍스트의 0-based UTF-16 코드 단위 Long offset이다. normalizationVersion 1은
선택한 charset으로 strict decode하고 첫 BOM만 제거한 뒤 CRLF/CR을 LF로 바꾼다. 공백 trim, 빈 줄 축약,
NFC는 하지 않는다. surrogate와 CRLF가 IO/text chunk 경계를 넘어도 같은 결과를 만든다.

`TxtDecoder`는 UTF BOM을 우선하고 BOM이 없으면 strict UTF-8만 자동 선택한다. 실패하면 bounded prefix의
UTF-8/UTF-16LE/UTF-16BE/CP949/EUC-KR 후보 미리보기와 수동 선택을 제공한다. BOM 없는 UTF-16과 legacy
charset을 자동으로 확정하지 않는다. malformed/unmappable 바이트는 REPORT이며 대체 문자를 만들어 진행하지 않는다.
UTF-32 BOM, conflicting BOM, binary header와 지원하지 않는 control payload는 거부한다.
지원하는 control 정책과 자체 작성 fixture/hash/license는 `fixtures/encoding/README.md` 및 manifest에 있다.

영구 encodingId는 `utf-8`, `utf-16le`, `utf-16be`, `cp949`, `euc-kr`이다. vendor Charset alias는 저장하지 않는다.
해석 revision은 아래 UTF-8 문자열의 SHA-256 lowercase hex다. 화면/글꼴/cache version은 revision을 바꾸지 않는다.

```text
bookreader-txt\n{sourceSha256}\n{encodingId}\n{normalizationVersion}\n
```

CP949는 실제 Android Charset 지원과 U+AC02=`81 41` fixture로 EUC-KR과 구분해야 한다.
실제 Android ICU는 EUC-KR alias에서도 이 CP949 확장 바이트를 허용하여 최초 기기 회귀가 실패했다.
표준 EUC-KR의 ASCII 또는 양쪽 바이트 A1–FE pair grammar를 앱에서 강제한 뒤 REPORT decode한다.
grammar는 read 경계의 lead/EOF를 검사하고 source byte offset을 보존한다. Android vendor alias가 확장을 거부한다는
주장은 하지 않으며 앱의 strict decoder 계약으로 구분한다. host JDK 지원은 Android 지원 증거가 아니다.
prefix가 유효해도 suffix 전체 검사를 통과하지 않으면 import를 commit하지 않는다.

## 내부 cache 형식 선택

현재 TXT 스크롤 경로는 파생 canonical UTF-16LE 파일과 fragment offset/length index를 쓴다.
계획 §18.3의 canonical UTF-8 byte cache 시작안 대신 UTF-16LE를 선택했다. canonical locator 단위와 같은
고정폭 위치로 seek할 수 있어 bounded 창 복원 시 추가 byte-offset mapping이 필요하지 않다.
이는 공개 locator/원본 보존 계약을 바꾸지 않으며 향후 UTF-8 cache로 변경해도 같은 revision을 유지할 수 있다.
대가는 ASCII 원본 대비 더 큰 파생 cache이며 디스크 부족은 해당 cache 실패로 처리한다.

원본은 그대로 남기고 streaming decoder가 최대 16Ki UTF-16 chunk를 만든다. cache는 grapheme/surrogate 경계를
검사한 fragment로 원문 내용을 덮으며 전체 파일/문단 String을 만들지 않는다. 본문 String LRU는 최대 8 fragment다.
완료 marker는 strict decode 및 원본 SHA-256 검사를 마친 뒤 마지막에 publish한다. 부분 prefix는 화면에 표시할 수
있지만 integrity/전체 decode 완료 전에는 progress writer를 활성화하지 않는다.
완료 cache 재개방 시 index topology·원본/cache digest를 검사한다. cache 삭제는 원본/영구 위치를 삭제하지 않으며 재구축한다.

## 제약과 증거

페이지 모드/정확한 총 페이지/본문 검색은 아직 범위 밖이다. cache byte offset을 영구 locator로 저장하지 않는다.
인코딩 변경 후 기존 revision의 기록을 새 offset에 적용하지 않는다. 기존 import 중복의 해석을 임의로 바꾸지 않는다.
제품 내 책별 인코딩 변경 및 revision별 기록 복귀 UI는 M06 범위다.

기존 host 13 cases와 수정 뒤 경계 grammar 회귀를 포함한 14 cases는 각각 exit 0/PASS다.
Android Charset 3 cases와 Reader/cache integration 결과는 [TESTING](../TESTING.md), [WORKLOG](../WORKLOG.md)를 따른다.
수정된 strict EUC-KR contract는 실제 API36 Android의 원래 3 assertions를 모두 통과했다.
5MiB/50MiB 실기기 p95 수치를 측정했다는 주장은 하지 않는다.

2026-10-09 재개 기준 `3ce3386` actual Android의 measured canonical line 저장/cache loss/Continue/recreation과
관리 원본 누락 시 commit 기록 보존 테스트는 PASS다. 전체 ordinary는 외부 VIEW Reader ERROR로 24/1 FAIL이며
그 실패의 내부 원인은 미확인이다. 앱 Room 공유/진단 변경 후 재검증 전 cache/reader 전체 완료를 주장하지 않는다.

최종 `2be610a` actual Android `37879616383`은 ordinary 25 cases/0 failures·errors·skips 및 host 2 stages PASS다.
외부 VIEW·Room owner·두 ReaderFlow·EOF/surrogate/encoding 회귀를 모두 검증했다. commit UTF-16 offset 11607의
외부 force-stop 뒤 서재 Continue 복원과 PNG 4개도 확인했다. 이는 TXT 내부 알파 M03 gate이며 페이지/검색/실기기 성능 완료는 아니다.
