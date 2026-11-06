# ADR0010 — 정확한 TXT 선택 구간과 별도 형광펜 기록

2026-11-06. TXT 구현·자동 검증 완료; 검증 결과는 READER_0_7.md와 SESSION_HANDOFF.md에 기록한다. EPUB 연결은 다음 단위다.

## 결정과 이유

기존 Compose1.9.3 SelectionContainer는 선택 좌표 callback을 공개하지 않는다. 같은 문장을 검색해 위치를 추정하면
반복된 문장에 잘못 표시할 수 있다. 내부 API나 reflection을 사용하지 않는다.
본문의 Text를 BasicTextField로 교체하면 선택 autoscroll/측정/페이지 마지막 줄이 바뀔 수 있으므로 이 단위에서는 교체하지 않는다.
현재 canonical anchor부터 최대4,096 UTF-16 단위의 읽기 전용 선택 창을 제공한다. 공개 TextFieldValue.selection과
창의 고정 base offset으로 정확한 시작·끝을 얻는다. 역방향 선택도 정렬하되 surrogate 중간·빈 선택·범위 초과는 거부한다.
원문의 Text/SelectionContainer는 유지하며 검증된 구간에 배경/글자 색상 SpanStyle만 적용한다. 글꼴/폭/줄 수는 바꾸지 않는다.

## 저장 및 복원 계약

Room7→8은 txt_annotations 한 테이블만 추가한다. 기존12개 테이블, Book/진행도/북마크/목차/설정 값을 덮어쓰지 않는다.
키는 bookId/contentRevision/id, 값은 schemaVersion1/startOffset/endOffset/quote/color/note/changeId다.
contentRevision이 다른 기록은 보존하되 새 해석에 표시하지 않는다. 저장할 때 원문의 정확한 구간을 다시 읽어 quote와 대조한다.
읽을 때도 모든 현재 기록의 형식·버전·한도와 원문 구간을 검사하며 불일치/미래 값은 실패로 처리한다. 비슷한 문장으로 옮기지 않는다.
그리기 시에도 현재 표시한 부분의 원문을 재대조한다. 겹치는 형광펜은 저장 목록의 startOffset/id 순서로 색상을 적용한다.

Reader가 소유하는 load/selection/write Job은 reset/종료에서 취소한다. 선택 창을 닫으면 window token을 무효화한다.
쓰기 transaction은 READY TXT/동일 revision/sessionEpoch/읽었던 row(changeId 포함)를 검사하고, 호출자 취소를 transaction 안에서 재검사한다.
동시 수정·오래된 세션·읽기 오류가 있으면 기존 기록을 덮어쓰지 않는다. 편집/삭제는 진행도 writer를 호출하지 않는다.
본문 이동만 기존 navigateTo→실제 측정→진행도 저장 경로를 사용한다.

## 한도와 범위

현재 내용 버전당500개, quote4,096/메모2,000 UTF-16 단위. DB는501개까지 읽어 초과를 검출한다.
DB는 text 길이와 BLOB 바이트 길이를 함께 사전 검사한다. SQLite length(TEXT)가 NUL 뒤를 세지 않는 경우도 메모리 한도를 우회할 수 없다.
원문 대조는 순차적이며 각 읽기가4,096단위를 넘지 않는다. 메모는 유효 Unicode와 줄바꿈/탭을 허용한다.
선택 창은 원본 변경을 받지 않는다. 파일/본문/사용자 URI를 공개 로그에 넣지 않으며 자체 생성 fixture만 사용한다.
사용자가 본문을 읽던 위치에 표시되는 구간에서만 새 선택을 시작한다. 본문 자체의 기본 길게 누르기 복사는 유지한다.
실기기 선택 손잡이/접근성/성능은 자동 에뮬레이터 검증과 구별한다. EPUB는 Readium 공개 Selection.locator/Decoration의 별도 연결이 필요하다.

## 검증 대상

실제 SQLite1–7→8 migration/검증 실패 rollback, 원본 raw 기록 보존, 색상·메모 CRUD/동시 수정/미래 값/500개 한도/SQL 오류.
반복 문장·emoji·역방향 선택·overflow/범위 오류와 clipped span의 원문 일치.
Reader 작업 취소/잘못된 원문/오래된 선택 창/쓰기 충돌. Android 실제 길게 누르기와 접근성 SetSelection 후 정확한 두 번째 중복 문장 저장,
실제 TextLayoutResult의 형광펜 범위, 글자 크기/페이지 전환·서재 재열기·Activity 재생성과 별도의 외부 force-stop 후 복원.
