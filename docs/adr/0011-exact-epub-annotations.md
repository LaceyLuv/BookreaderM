# ADR0011 — EPUB 선택 범위와 공개 Readium Decoration

2026-11-07. 구현 결정. 기기 검증 결과는 SESSION_HANDOFF/WORKLOG의 실제 실행 증거를 따른다.

Readium Android 3.1.2의 `currentSelection()`은 현재 locator에 선택 문장과 전후 문맥을
더한다. DOM Range를 반환하지 않으며, Decoration의 텍스트 탐색은 중복/근사 문장에 매칭할 수 있다.
선택 문장만 저장하거나 progression만으로 형광펜을 붙이는 방법은 채택하지 않는다.

공개 `selectionActionModeCallback`에 형광펜·메모를 추가하고 공개 `currentSelection`,
`evaluateJavascript`, `applyDecorations`, `HtmlDecorationTemplate`을 사용한다.
엔진 소유 읽기 전용 스크립트는 실제 선택의 공통 조상에서 숫자 nth-child 경로,
UTF-16 시작/끝, 정확한 인용과 전후256단위 문맥을 얻는다. 이 범위 안에서 문맥을 포함한 인용이
유일해야 한다. SDK 선택 인용과도 동일해야 하며, 전체 SDK locator JSON의 다른 필드는 유지한다.
Readium은 빈 앞/뒤 문맥을 JSON에서 생략하므로 빈 anchor 문맥과 생략 필드만 동등하게 처리한다.
비어 있지 않은 문맥의 누락은 거부한다. SDK/본문을 패치하거나 HTML에 사용자 인용을 삽입하지 않는다. 출력 템플릿의 식별자는 검증된 ID다.

공통 조상의 텍스트는65,536 UTF-16/4,096 text node, 경로64단계, 선택4,096단위,
locator/anchor JSON 각각16,384단위로 제한한다. 일부 긴 조상·문맥 중복·선택 표현 차이는
지원하지 않으며 오류로 안내한다. 다른 문장을 찾아 대신 저장하지 않는다.
새 저장 직전 및 현재 장의 표시 직전에 DOM 위치/인용/문맥/유일성을 다시 검증한다.
원문 파일은 기존 관리 복사본 SHA/크기 검사를 거치며 contentRevision별로 기록한다.
문맥/DOM 검증 실패 시 장식은 지우고 기록을 보존한다. 다른 장으로 이동할 때는 기존 검증된
Readium locator 이동을 사용하고, 도착한 장에서 표시 전에 DOM 검증을 거친다.

Reader 생명주기가 조회/선택/그리기/쓰기 Job을 소유한다. host 교체는 선택·그리기를 취소하며
기존 메모 draft는 유지한다. 새 저장은 현재 host와 세션을 다시 확인한다. close는 모든 Job을 취소한다.
10초 제한은 실행 중인 WebView JS를 선점 중단하는 보장이 아니며, 스크립트 자체의 작업량 제한을 함께 둔다.

Room8→9는 epub_annotations만 추가한다. 기존13개 테이블 정의와 TXT/EPUB 진행도는 변경하지 않는다.
500개/책 내용 버전, 메모2,000단위,4색을 제공한다. DAO는 문자열을 읽기 전에 BLOB 바이트 한도를
검사해 NUL 뒤의 과대값도 거부한다. 미래 schema/알 수 없는 값/손상 row는 덮어쓰지 않는다.
IMMEDIATE transaction에서 book/revision/sessionEpoch와 이전 row 전체/changeId를 검사하고
CAS 충돌·취소·SQL 오류에는 기존 값을 유지한다. 기록 CRUD는 진행도를 쓰지 않는다.

확인한 공식 소스: Maven `org.readium.kotlin-toolkit:readium-navigator:3.1.2` sources JAR,
SHA256 `b3f121026ccc4155d46ea0d01e146352eea5045385bfb49d26511d6e0dbbe3ea`.
`SelectableNavigator.kt`, `epub/EpubNavigatorFragment.kt`의 currentSelection/evaluateJavascript/applyDecorations,
`R2BasicWebView.kt`의 ActionMode callback, `html/HtmlDecorationTemplate.kt`의 BOXES를 확인했다.
캐시 소스 위치 `/workspace/scratch/readium-3.1.2/navigator/`.

자체 생성 fixture에는 emoji 뒤 두 번째 같은 단어, 완전히 같은 별도 문단, 문맥까지 반복되는 긴 문단이 있다.
실제 Android 길게 누르기/메뉴, 독립 DOM Range와 실제 SDK 장식 사각형 비교,
메모 변경·글자 크기·재생성·재열기·장 이동·삭제, 모호함/저장 전 변경 거부,
외부 host force-stop 후 전체 anchor/locator/메모 복원으로 검증한다.
실기기/TalkBack/모든 EPUB 레이아웃·성능을 검증한 것으로 확대하지 않는다.

빈 문맥 생략은 공식 `readium-shared:3.1.2` AAR의 Locator.Text.fromJSON/toJSON과
JSONKt.optNullableString 바이트코드에서도 확인했다(AAR SHA256
`f04d5d109aefaae11c66c17483084dbe7bfb652b22851869aea23a1487edfd7f`).
경계 단위 검사는 빈 필드 생략 허용과 비어 있지 않은 문맥 누락 거부를 함께 요구한다.
