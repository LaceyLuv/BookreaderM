# Native UI 검증의 비동기 경계 — 2026-10-18

`dc06d3e61f8af45d0d03258b9941bf7dc714d851`의 [build 39103709384](https://github.com/LaceyLuv/BookreaderM/actions/runs/39103709384)는
Android 66/shared 74/EPUB 16/실제 ENOSPC 3 PASS다.
[device 39103709383](https://github.com/LaceyLuv/BookreaderM/actions/runs/39103709383)는 전체 FAIL이다.
[소스·ZIP digest·XML·logcat 원본 및 요약](evidence/epub-reflow-navigation/ci-dc06d3e/summary.json)을 보존한다.

| API | 일반 native | host |
| --- | --- | --- |
| 26 | 54개 실행, Comic semantics 스레드 충돌 및 SDK probe 복원 비교 2 FAIL | NOT_RUN |
| 34 | 54개 실행, EPUB currentLocator 지연 알림 비교 1 FAIL | NOT_RUN |
| 36 | 54개 실행, DocumentsUI 파일 탐색 1 FAIL | NOT_RUN |

API26의 EPUB 제품 전체 flow는 이제 동일한 높이 516에서 검색/재생성/글자 크기/재열기를 통과했다.
글자 크기는 16 → 16.8896px, progression은 .935033 → .962330이며 .03 이내다.
[관찰 요약](evidence/epub-reflow-navigation/ci-dc06d3e/epub-viewport-summary.json)에서 모든 단계의 실제 p179 표시를 확인한다.
API36도 제품 EPUB 전체 flow는 PASS다. 이 개별 성공을 전체 suite 성공으로 바꾸어 적지 않는다.

## 후속 변경

- **Compose:** 고정 버전 1.9.3의 `fetchOneOrThrow`는 Main에서 선택한 노드를 다시 test thread의
  `printToString`으로 읽는다. 이 출력은 자식/부모 semantics도 조회하며, 실제 실패의 SnapshotStateObserver는
  test thread와 Main의 동시 접근을 보고했다. Comic test는 collection 조회로 선택하고 config/action/bounds를
  Main에서만 읽는다. 버튼은 실제 semantics action을 실행하며 swipe/pinch/double tap/cancel은
  UiAutomation의 실제 MotionEvent로 주입한다. 기존 동작·entryKey·DB·원본 불변 assertion을 유지한다.
- **EPUB 제품 관찰:** API34에서는 DOM의 scrollY와 p179 표시가 이미 맞지만 currentLocator는 아직 0이었다.
  SDK의 100ms debounce가 끝나기 전에 읽는 경로를 막도록 기존 href/.03 조건을 최대 10초 기다린 뒤
  같은 assertion을 실행하고 관찰을 기록한다. 허용 오차를 넓히지 않는다.
- **SDK probe:** 사전 로드에도 증가하는 loadedPages/currentLocator 초기값 대신 실제 onPageChanged를 기다린다.
  preference 변경 후 표시 콜백을 기다리고, debug fixture의 recreation은 전체 EpubPreferences도 직렬화·복원한다.
  기존 완전 Locator round-trip 및 .03 복원 assertion을 유지한다.
- **DocumentsUI:** label 노드가 ACTION_CLICK 요청을 수락했다는 사실만으로 폴더가 열렸다고 볼 수 없다.
  보이고 활성화된 실제 clickable 조상만 클릭한다. 기존 실제 provider 선택·URI grant·본문 표시 assertion을
  유지하고, 실패하면 제한된 개수의 visible node 목록을 XML 예외에 남긴다.

의존성 버전·테스트 개수·skip 정책을 바꾸지 않았다. [확인한 고정 버전 소스 hash](evidence/epub-reflow-navigation/ci-dc06d3e/diagnostic-sources.json)를 남긴다.
후속 테스트 APK compile/lint exit 0 PASS. 세 API matrix 결과를 확인한 뒤에만 main 통합을 진행한다.
