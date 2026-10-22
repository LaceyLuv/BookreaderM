# Native UI 검증의 비동기 경계 — 2026-10-18

## 2026-10-22 현재: 검증된 소스 main 통합 및 unsigned 재생성 완료

`bb0f537`의 build 66/74/16 + 실제 ENOSPC 3, API26/34/36 각각 native 54 + 세 포맷 host 6이 모두 PASS다.
기존 `.03` 및 실제 `p179` 표시 assertion을 유지했고 ZIP digest·필수 XML·PID 교체 원본을 확인했다.
원격 main에 이력을 보존하여 반영했으며, 같은 소스로 unsigned APK/AAB 재생성·정적 감사도 PASS다.
[최종 결과·hash·원본·미완료 범위](RESUME_COMPLETION_2026-10-22.md)를 현재 기준으로 따른다.
API34의 과거 TXT 측정 재진입은 후속 두 소스에서 재발하지 않았으나 원인 미확정이다.
실기기 성능·TalkBack·16KB OS·OEM backup·전체 corpus·독립 리뷰·서명/출시 gate는 남아 있다.

아래 내용은 이전 검증 시점의 이력이다. 당시의 main 미통합·CI 대기 표시는 현재 상태가 아니다.


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

후속 검증 소스: `9a96ac5442ff5ed5969533d3311e1e4f6442dcdc`.
[build 39105037444](https://github.com/LaceyLuv/BookreaderM/actions/runs/39105037444),
[device 39105037355](https://github.com/LaceyLuv/BookreaderM/actions/runs/39105037355) 결과 확인 중이다.

## 9a96ac5 결과와 실제 설정 완료 신호

[원본과 요약](evidence/epub-reflow-navigation/ci-9a96ac5/summary.json)을 보존했다.
Build는 66/74/16 + ENOSPC 3 PASS다. API26은 54개 중 2 FAIL, API34/36은 각각 54개 중 1 FAIL,
세 API host는 NOT_RUN이다. EPUB 제품 flow와 Comic flow는 세 API 모두 PASS다.

새로 추가한 probe의 `renderedPages > beforePreferences` 조건은 잘못된 대기 조건이었다.
Readium 3.1.2의 `onSettingsChange` → `R2EpubPageFragment.setFontSize`는 WebSettings.textZoom을 바꾸며,
위치가 그대로인 경우 새 페이지 콜백은 보장하지 않는다. 실제 settings 값과 표시 WebView의 textZoom 130을
확인한 뒤 `postVisualStateCallback` 완료를 기다리도록 교정했다. Locator .03 assertion은 유지한다.

API26 picker의 구조 로그는 fixture 폴더 진입 및 file_list/nameplate가 존재하지만 파일 선택 label 탐색이
끝나지 않았음을 보여 준다. 해당 테스트 동안 `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS`를 명시하고 종료 시
원래 flags를 복원한다. 살아 있는 window의 text 검색도 함께 사용해 RecyclerView의 캐시된 자식 목록에만
의존하지 않는다. 실제 URI grant·파일명·본문 확인은 그대로다. 실패 진단은 개인 문서명을 기록하지 않는
구조 정보와 기존 fixture screenshot 정책을 사용한다.

후속 테스트 APK compile/lint exit 0 PASS. 다음 push의 세 API native/host 결과를 확인한다.

후속 검증 소스: `49d1a0421b4170a12678dcfaaf0ac6143b43754e`.
[build 39106038482](https://github.com/LaceyLuv/BookreaderM/actions/runs/39106038482),
[device 39106038494](https://github.com/LaceyLuv/BookreaderM/actions/runs/39106038494) 결과 확인 중이다.

`49d1a04` build 원본 회수 및 ZIP SHA-256 검증 완료: Android 66/shared 74/EPUB 16/실제 ENOSPC 3 PASS.
Device matrix는 아직 실행 중이며 전체 성공 판정은 보류한다.

## 2026-10-22 연결 복구 후 49d1a04 결과

환경은 connected이며 Git 조회·GitHub API·CI ZIP 다운로드가 실제 성공했다.
[원본과 요약](evidence/epub-reflow-navigation/ci-49d1a04/summary.json)의 소스 SHA,
ZIP digest, 필수 테스트 이름을 확인했다. Build 66/74/16 + ENOSPC 3 PASS.
API26/34는 각각 54개 중 2 FAIL, API36은 54개 중 1 FAIL이며 host는 모두 NOT_RUN이다.

- 공통 probe 실패: 앞서 분석에서 `R2PagerAdapter.setFontSize`의
  `if (config.useReadiumCssFontSize) return`을 놓쳤다. 기본값은 true이며 실제 경로는
  `EpubSettings.toCss`의 `--USER__fontSize`다. textZoom=130 대기는 잘못된 조건이었다.
  기본 설정을 바꾸지 않고 현재 DOM의 CSS 130% 적용을 기다린 뒤 visual callback을 확인한다.
  기존 .03 Locator 비교와 모든 publication 검증을 유지한다.
- API26 picker: 보이는 목록에 제목 노드 4개가 있지만 제공자는 파일 5개를 반환한다.
  fixture는 다른 파일 뒤로 정렬될 수 있다. 실제 `dir_list`의 접근성 스크롤로 화면 밖 행도 탐색한다.
  정확한 파일명 선택, 실제 URI grant, 관리 복사본 및 본문 assertion을 유지한다.
- API34 TXT: 초기 reader_ready 대기 중 Compose 측정 재진입 예외가 발생했다.
  원인과 재현 조건은 아직 확정하지 못했다. assertion을 완화하거나 예외를 삼키지 않고
  후속 전체 matrix에서 재발 여부를 확인한다. 해결 완료로 기록하지 않는다.

실패 시 기존 fixture 진단을 host로 가져오기 전에 device-check가 종료하던 경로를 보완했다.
진단 다운로드 실패와 관계없이 원래 connected test 실패 exit code를 반환한다.
후속 APK/test APK compile 및 lint exit 0 PASS. 로컬 로그는
[evidence/epub-reflow-navigation/local-native-css-scroll-check.log](evidence/epub-reflow-navigation/local-native-css-scroll-check.log).

후속 소스 `277469db4d2030b3bbb30d5401dc9b44ea692eb5`:
[build 39755195304](https://github.com/LaceyLuv/BookreaderM/actions/runs/39755195304),
[device 39755195311](https://github.com/LaceyLuv/BookreaderM/actions/runs/39755195311) 검증 중이다.

## 277469d 결과 및 API26 실제 문서 터치

[277469d 원본](evidence/epub-reflow-navigation/ci-277469d/summary.json): build 66/74/16 + ENOSPC 3 PASS.
API34/36 각각 native 54 + TXT/EPUB/Comic host 6 PASS이며 source/digest/XML/PID 종료·교체를 확인했다.
API26은 54개 중 picker 1 FAIL, host NOT_RUN이다. 모든 API의 SDK probe 및 제품 EPUB 회귀가 PASS다.
API34 TXT 측정 재진입은 이번 실행에서 재발하지 않았지만 원인 해결을 주장하지 않는다.

API26 스크린샷과 구조 원본은 스크롤 후 목표 파일이 표시되지만 제목과 그 상위 행 모두
clickable=false임을 보여 준다. 접근성 clickable 조상을 찾는 방식만으로는 파일을 열지 못한다.
정확한 파일명으로 찾은 visible/enabled 노드에 clickable 조상이 없으면, 노드와 창의 교차 영역 중앙에
실제 touch DOWN/UP을 보낸다. 좌표를 하드코딩하지 않으며 두 이벤트 모두 주입 성공을 확인한다.
실제 DocumentsUI 선택·URI grant·파일명·본문 검증은 그대로다.
후속 테스트 APK compile/lint exit 0 PASS. main 통합은 후속 전체 matrix 성공 후 진행한다.

후속 소스 `bb0f53787d2e26a0fcc321af31cf033d935eaed6`:
[build 39757163283](https://github.com/LaceyLuv/BookreaderM/actions/runs/39757163283),
[device 39757163184](https://github.com/LaceyLuv/BookreaderM/actions/runs/39757163184) 검증 중이다.
