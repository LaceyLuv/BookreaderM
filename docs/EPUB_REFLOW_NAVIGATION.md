# EPUB 검색 이동과 화면 교체 경쟁 — 2026-10-18

## 2026-10-22 현재: 검증된 소스 main 통합 및 unsigned 재생성 완료

`bb0f537`의 build 66/74/16 + 실제 ENOSPC 3, API26/34/36 각각 native 54 + 세 포맷 host 6이 모두 PASS다.
기존 `.03` 및 실제 `p179` 표시 assertion을 유지했고 ZIP digest·필수 XML·PID 교체 원본을 확인했다.
원격 main에 이력을 보존하여 반영했으며, 같은 소스로 unsigned APK/AAB 재생성·정적 감사도 PASS다.
[최종 결과·hash·원본·미완료 범위](RESUME_COMPLETION_2026-10-22.md)를 현재 기준으로 따른다.
API34의 과거 TXT 측정 재진입은 후속 두 소스에서 재발하지 않았으나 원인 미확정이다.
실기기 성능·TalkBack·16KB OS·OEM backup·전체 corpus·독립 리뷰·서명/출시 gate는 남아 있다.

아래 내용은 이전 검증 시점의 이력이다. 당시의 main 미통합·CI 대기 표시는 현재 상태가 아니다.


제품 수정 소스: `98d7f0faa5af37fdce8ac3d8d3b00c87eab05bdb`. 후속 테스트 동기화 보완 소스는 `1622bb6c73fc2ccfa9c1eca7161b92236b8ba797`이다.

검색 결과를 누를 때 검색창과 키보드가 닫히면서 viewport가 바뀐다. 기존 controller는
REFLOWING 상태의 `navigate` 요청을 버렸으며, 이동 직후 host가 교체되는 경우에도
`attach`에서 jump target을 지웠다. 새 host가 마지막 accepted 위치로 돌아가면 검색 이동이 저장되지 않는다.
`8ca750e` API26/34는 모두 검색 click 뒤 저장 progression 대기에서 20초 timeout이었다.
[실패 원본](evidence/epub-reflow-navigation/native-failures-8ca750e.json)을 보존한다.

## 변경

- `pendingJump`에 아직 화면 표시가 확인되지 않은 요청 하나를 둔다. 초기 host 위치는 이를 우선한다.
- READY host의 정상 `go`와 REFLOWING host의 교체 경로 모두 이 요청을 보존한다.
- 현재 host와 generation의 실제 page callback을 받고 progress가 READY일 때만 `userAnchor`를 호출한다.
  요청을 받는 시점에는 accepted/DB 위치를 바꾸지 않는다. 이전 generation callback은 진입점에서 거부한다.
- 최신 요청이 이전 요청을 대체하며, 실제 사용자 이동은 보류 요청을 취소한다.
  완료된 위치의 설정 변경·recreation 복원은 새 사용자 이동으로 저장하지 않는다.
- API34 TXT 측정 실패는 merged semantics에 실제 layout 노드가 숨은 경우였다.
  측정 helper만 unmerged tree의 viewport/text layout을 사용하도록 바꾸고, 줄·anchor 일치 검증은 유지한다.

## 검증

기존 실제 검색 UI test는 click handler 직전에 reflow를 강제한다. 새 native test는
SDK 검색으로 얻은 full Locator를 요청한 직후, 같은 Main callback에서 reflow를 강제한다.
두 경로 모두 렌더 전 accepted 불변, 렌더 후 p179 표시 및 실제 저장 위치를 확인한다.
새 test는 저장된 search highlight도 비교한다. 일반 필수 native 목록은 54개, 외부 host는 총 6단계다.
기존 .03 progression 오차, font 증가, 실제 문단, DB 불변 assertion은 유지했다.
검색 timeout에서도 현재 phase/generation/초기·현재 Locator/viewport 관찰을 보존한다.

로컬 Android unit 66/테스트 APK compile/lint PASS, 명령 exit 0:

```bash
source /workspace/toolchains/mobile-env.sh
./gradlew --no-daemon --console=plain :androidApp:assembleDebugAndroidTest :androidApp:lintDebug :androidApp:testDebugUnitTest
```

원격 [build 39101699055](https://github.com/LaceyLuv/BookreaderM/actions/runs/39101699055)는 PASS,
[device 39101699053](https://github.com/LaceyLuv/BookreaderM/actions/runs/39101699053)는 FAIL이다.
전체 artifact ZIP digest와 소스 hash를 확인하고 [원본과 검증 요약](evidence/epub-reflow-navigation/ci-98d7f0f/summary.json)을 보존했다.

| API | 일반 native | host process death |
| --- | --- | --- |
| 26 | 54개 실행, 1 FAIL: 검색 후 표시 WebView 조회 | NOT_RUN |
| 34 | 54개 실행, 1 FAIL: CSP probe의 이미지 DOM 조회 | NOT_RUN |
| 36 | 54 PASS, failure/error/skip 0 | TXT/EPUB/Comic 총 6 PASS, PID 교체 확인 |

새 검색 직후 reflow 회귀는 세 API 모두 PASS다. API34/36의 기존 EPUB 전체 flow도 PASS다.
로컬 전체 필수 검사와 원격 XML 모두 Android 66/shared 74/EPUB 16/실제 ENOSPC 3 PASS다.

### 후속 테스트 동기화 보완

API34 logcat의 `naturalWidth` TypeError는 `one.xhtml` DOM이 아직 완성되기 전에 조회한 증거다.
SDK의 `loadedPages > 0`은 사전 로드된 `two.xhtml` 콜백으로도 만족하므로 대상 장의 준비 신호가 아니다.
공유 test helper가 보이는 대상 WebView와 해당 문서의 `readyState === complete`를 같은 JS 평가에서 확인한다.
준비 전 상태만 기존 10초 한도 내에서 기다리고, 준비 후 false/null/JS 오류는 그대로 실패시킨다.
CSP 위반 이벤트·이미지 naturalWidth·문단 표시·.03 progression assertion은 유지한다.

API26은 검색 저장 대기 직후 보이는 WebView가 없었다. 검색 저장 대기는 기존 href/progression에 더해
실제 요청의 highlight와 controller READY도 요구한다. 문단 검사는 현재 READY host의 준비된 문서를 조회한다.
테스트 APK compile/lint exit 0 PASS. [build 39102758695](https://github.com/LaceyLuv/BookreaderM/actions/runs/39102758695)는
원본 XML 기준 66/74/16/ENOSPC 3 PASS다. [device 39102758575](https://github.com/LaceyLuv/BookreaderM/actions/runs/39102758575)의
API26에서 후속 progression 비교 실패를 확인했다. API34/36은 각각 ordinary 54 + host 6 PASS이며
[소스별 원본과 요약](evidence/epub-reflow-navigation/ci-1622bb6/summary.json)을 보존했다.

### API26: 키보드와 비교 기준 viewport

`1622bb6`의 검색 관찰은 높이 234, progression .9706122448979592이며,
재생성 관찰은 높이 516, progression .9350331316623451이다. 두 viewport 모두 p179가 실제로 보이고
DB Locator는 변하지 않았다. 이 .035579 차이는 문단 유실의 증거가 아니라 키보드가 차지한 높이 차이와 일치한다.
10초 준비 대기 보완 후 DOM 조회는 정상 동작했고, 기존 .03 assertion이 이 비교 조건 차이를 드러냈다.

후속 flow는 검색 UI를 열기 전 viewport 크기를 기억하고 결과 선택 전 실제 키보드를 닫는다.
controller READY 및 같은 viewport 크기를 10초 내 확인한 다음 검색 요청을 보낸다.
기존 .03 수치 검증, font 증가, 실제 p179, DB 동일성 검증을 모두 유지한다.
검색 요청 직전/직후의 강제 host 교체 회귀도 유지하며, 이 변경은 비교 기준을 고정하는 테스트 설정 변경이다.
후속 소스는 `dc06d3e61f8af45d0d03258b9941bf7dc714d851`이다.
[build 39103709384](https://github.com/LaceyLuv/BookreaderM/actions/runs/39103709384)와
[device 39103709383](https://github.com/LaceyLuv/BookreaderM/actions/runs/39103709383)는 API26/34/36 모두 FAIL이다.
API26의 제품 EPUB flow는 PASS이며 새 실패와 후속 변경은 [Native UI 동기화 보고서](NATIVE_UI_SYNCHRONIZATION.md)를 따른다.
세 API의 일반 native 및 host 결과 확인 전 main을 통합하지 않는다.

## 패키지와 검증 범위

동일 소스의 unsigned APK/AAB 생성과 APK 정적 감사는 PASS다.
[원본 hash/감사](evidence/epub-reflow-navigation/release-98d7f0f/)와
`/workspace/artifacts/BookreaderM/98d7f0f/`의 실제 패키지를 연결한다.
실제 서명 상태는 NOT_SIGNED이며, release 기기 실행·물리 성능·16KB OS·TalkBack·출시 승인은 미완료다.
이전 WebView native crash와 과거 전체 suite 실패 이력은 보존하며 이번 변경으로 모두 해결됐다고 주장하지 않는다.
