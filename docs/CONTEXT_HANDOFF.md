# BookReader Mobile 새 컨텍스트 인계 — 2026-10-17

## 2026-10-18 최신: Native UI 비동기 경계 보완

`dc06d3e` build 원본은 66/74/16 + ENOSPC 3 PASS다. device는 API26 54개 중 2 FAIL,
API34/36 각각 54개 중 1 FAIL이며 세 API host는 NOT_RUN이다. API26의 제품 EPUB flow는 동일 viewport에서 PASS다.
새 실패는 Comic semantics 스레드 접근, SDK probe 전환, Readium 지연 위치 알림, 시스템 picker 탐색이다.
기존 assertion·54개 manifest·6개 host를 유지한 후속 변경과 원본은 [Native UI 동기화](NATIVE_UI_SYNCHRONIZATION.md)에 정리했다.
main 통합은 아직 하지 않았다. 아래 내용은 이전 시점 이력이다.

## 2026-10-18 최신: API26 키보드 높이와 복원 비교 기준

`1622bb6` build는 원본 XML 기준 66/74/16 + 실제 ENOSPC 3 PASS다.
API34/36은 각각 ordinary 54 + host 6 PASS다. API26은 54개 중 기존 EPUB flow의 .03 progression 비교 1 FAIL이다.
검색 당시 viewport 높이 234와 재생성 후 516에서 p179는 모두 표시됐고 DB 위치도 불변이었다.
progression .970612 → .935033의 차이를 확인해, 검색 결과 선택 전 키보드를 닫고
원래 viewport 크기와 READY를 확인하도록 테스트 설정을 보완했다.
문단·font·DB·.03 assertion과 검색 요청 전/후 강제 reflow 회귀는 유지한다.
후속 테스트 APK compile/lint exit 0 PASS. push 후 전체 matrix를 확인한다.
[상세 관찰·CI·증거](EPUB_REFLOW_NAVIGATION.md)를 따른다. main 통합은 전체 native/host 통과 후 진행한다.

## 2026-10-18 최신: EPUB 대상 문서 준비 동기화 보완

제품 수정 `98d7f0f`의 CI 원본을 회수했다. build PASS(66/74/16 + ENOSPC 3),
API36 ordinary 54 + host 6 PASS다. API26/34는 각각 54개 실행 중 1 FAIL이며 host는 NOT_RUN이다.
새 검색 직후 reflow 회귀는 세 API 모두 PASS, API34의 기존 EPUB flow와 TXT 회귀도 PASS다.
API26은 표시 WebView 조회, API34는 미완성 DOM의 이미지 조회에서 실패했다.
[원본·분석·후속 변경](EPUB_REFLOW_NAVIGATION.md)에 근거를 남긴다.

후속 변경은 테스트의 대상 문서/현재 host 준비를 기다리고 검색 저장 highlight 일치를 추가한다.
기존 시간 한도·CSP·이미지·문단·progression·DB assertion을 유지한다.
테스트 APK compile/lint exit 0 PASS. 후속 native matrix를 검증한다. main 통합은 세 API의 ordinary 54 + host 6 모두 PASS가 선행 조건이다.
unsigned 패키지 정적 감사는 PASS지만 서명·실기기·출시 검증은 미완료다.

## 2026-10-18 EPUB 검색 이동과 화면 교체 경쟁 수정

`8ca750e`의 차단됐던 artifact를 회수해 ZIP digest를 검증했다.
API26은 53개 중 검색 저장 timeout 1 FAIL, API34는 같은 timeout 및 TXT 측정 semantics 조회 총 2 FAIL,
API36은 ordinary 53 PASS다. [실패 XML·관찰·logcat 원본](evidence/epub-reflow-navigation/)을 보존했다.
검색 실패는 설정 변경 이전 단계이므로 지난 설정 대기 보완과 구분한다.

`EpubReaderController.navigate`는 REFLOWING 중 요청을 버렸고 `attach`는 미완료 jump target을 초기화했다.
검색/키보드 닫힘으로 host가 교체되는 경로에서 요청을 잃지 않도록 별도 `pendingJump`를 보존한다.
새 host는 보류 요청에서 시작하되, 현재 host/generation의 실제 표시 callback과 READY 확인 뒤에만
`progress.userAnchor`를 호출한다. 이전 generation 콜백은 진입점에서 거부하며 새 사용자 이동은 보류 요청을 취소한다.
완료된 위치의 순수 font/recreation 복원은 사용자 이동으로 새로 저장하지 않는다.

기존 검색 UI test는 실제 click handler 직전에 강제로 reflow하고, 새 native test는 요청 직후 강제로 reflow한다.
두 경우 모두 렌더 전 accepted 위치 불변, 이후 실제 p179 표시 및 저장 text anchor를 확인한다.
기존 .03 오차/문단/DB 불변 검증을 유지했다. ordinary 필수 목록은 54개로 갱신했고 host는 총 6단계다.
실패 시에도 search timeout 관찰을 남기며 새 독립 회귀 관찰 파일을 device-check가 회수한다.
TXT 측정 helper는 unmerged semantics의 실제 viewport/text layout을 사용하며 줄·anchor 일치 assertion은 유지한다.

`./gradlew --no-daemon --console=plain :androidApp:assembleDebugAndroidTest :androidApp:lintDebug :androidApp:testDebugUnitTest`
exit 0, shell 문법/변경 코드 whitespace PASS다. native 재검증은 push 후 진행한다.
이 단계에서 제품 오류 해결·main 통합·최종 release 검증 완료를 주장하지 않는다.

## 2026-10-18 최신: 설정 대기 보완 검증 및 실패 원본 접근 차단

검증 소스는 `8ca750efe07f12c5f7f0d79980868919ba9f88b7`, 현재 브랜치는 `codex/comic-product`다.
설정 테스트가 저장 완료·예상 설정·새 hostVersion·READY를 기다리며 실제 글자 크기 증가도 확인한다.
로컬 full CI **Android 66/shared 74/EPUB 16 + 실제 ENOSPC 3 PASS**, APK/test APK/lint/schema 5 PASS다.
원격 build 39100038796은 SUCCESS, device 39100038799는 **API26 FAIL / API34 FAIL / API36 SUCCESS**다.
이는 최신 job metadata이며 새 native XML의 건수/실패 원인은 아직 확인하지 못했다.
새 artifact 호스트 `productionresultssa0.blob.core.windows.net`(device),
`productionresultssa7.blob.core.windows.net`(build)의 프록시 403으로 원본 접근이 **BLOCKED_ENV**다.
두 호스트를 환경 초안에 추가했다. 환경 설정에서 검토·저장·게시한 뒤 다운로드를 다시 진행한다.

이전 `8bced5f`의 세 API 각각 **53 ordinary + 6 host PASS**는 원본 검증된 별도 성공 이력이다.
이번 실패를 이전 성공으로 덮어쓰지 않으며, 새 실패가 과거와 같은 원인이라고 단정하지 않는다.
`8ca750e` unsigned release APK/AAB 생성·APK 정적 감사 PASS, 실제 서명은 **NOT_SIGNED**다.
release 실행/물리 성능/16KB OS/TalkBack/출시 승인은 미완료다. **main 통합은 하지 않았다.**
[최신 summary](evidence/archive-resume-verified/summary-8ca750e.json),
[패키지 hash·감사](evidence/archive-resume-verified/release-8ca750e/),
[전체 검증 보고서](RESUME_VALIDATION_2026-10-18.md)를 따른다. 아래 절은 이전 시점 이력이다.

## 2026-10-18 원본 회수 완료 및 설정 적용 대기 보완

원격 artifact 다운로드가 성공했다. ZIP SHA256을 GitHub digest와 비교했고,
`8bced5f`의 source/필수 case 목록/native XML/host PID 원본을 검증했다.
**API26·34·36 각각 ordinary 53 + TXT/EPUB/Comic 외부 host 총 6단계 PASS**, failure/error/skip 0이다.
원격 build Android 66/shared 74/EPUB 16/실제 ENOSPC 3도 PASS다.
[검증 summary](evidence/archive-resume-verified/summary-8bced5f.json)와
[원본](evidence/archive-resume-verified/)을 보존한다. 이전 403 blocker는 이번 다운로드에서 해소됐다.

세 API 모두 recreation 뒤 16px, font-size/reopen 뒤 16.8896px이며 모든 관찰에서 p179가 표시됐다.
글자 크기 변경 뒤 progression은 약 .0273~.0275 달라졌고 기존 .03 비교도 통과했다.
과거 `9dfb884` API26은 검색 결과 클릭 후 저장 progression 대기 20초 초과,
`c1ab039` API34는 progression 비교 FAIL, API36은 SDK probe 중 instrumentation process crash였다.
이전 실패를 지우거나 이번 성공으로 원인 해결이라고 바꾸지 않는다.

코드에서 설정 저장 중 기존 READY node가 남는 대기 조건 문제를 확인했다.
EPUB 두 설정 테스트가 저장 완료/예상 설정/hostVersion 증가/복원 READY를 함께 기다리게 했다.
실제 문단 computed font-size 증가 검증도 추가했으며 기존 .03/문단 표시/DB 불변 검증은 유지했다.
제품 코드는 변경하지 않았다. 이 수정으로 과거 검색 timeout/crash가 해결됐다고 주장하지 않는다.
`./gradlew --no-daemon --console=plain :androidApp:assembleDebugAndroidTest :androidApp:lintDebug`
exit 0, 변경 Kotlin whitespace 검사 PASS. 새 native 검증은 push 후 진행한다.
원본 로그의 CRLF/공백은 보존하며 문서·코드 whitespace 검사와 구분한다.
main 통합은 보완된 테스트 결과 확인 후 판단한다.

## 2026-10-18 재개 후 최신 상태

사용자가 후속 개발 재개를 승인했다. 현재 저장소는 `/workspace/BookreaderM`,
`codex/comic-product`이며 관찰 코드 소스 `8bced5f`를 push했다.
최신 build 39098185637 및 device 39098185673의 API26/34/36 job은 모두 **SUCCESS(metadata)**다.
로컬 unit 66/74/16, ENOSPC 3, APK/lint도 PASS다.
원격 원본은 프록시 403으로 **BLOCKED_ENV**, 로컬 API26은 APK 설치 시간 초과로 **0 tests/BLOCKED_ENV**다.
관찰 추가로 제품 오류가 해결됐다고 판단하지 않으며 main 통합은 상세 증거 확인 전까지 보류한다.
[검증 보고서와 정확 명령·다음 절차](RESUME_VALIDATION_2026-10-18.md)를 우선한다.
아래 중단 지시/실행 중 표기는 이전 세션 이력이다.

## 최신: 사용자 요청으로 현재 작업을 정리하고 다음 세션으로 인계

**[SESSION_HANDOFF.md](SESSION_HANDOFF.md)를 먼저 읽는다.** 사용자는 현재 작업까지만 마무리하고 전달서를 작성하도록 요청했다. 새 구현은 중단하고 코드/증거/원격 진행 상태를 보존했다.

- main 제품 기준은 EPUB 22526d3이며, 후속 Comic·precise EPUB·공통 seek·그리드 코드는 `codex/comic-product`의 9dfb884 및 문서 merge에 있다. 전체 native 검증 전 main 제품 통합은 하지 않았다.
- 최신 local full CI는 Android 66/shared 74/EPUB 16, APK/test APK/lint/schema 5 PASS다. 원본은 `docs/evidence/m10-local-validation/source-9dfb884/`.
- a5a7a31 device 39016613293은 53 중 52 PASS/1 FAIL(EPUB progression expected .9264910536779324 vs actual .9565038378966562, tolerance .03). 실제 문단과 precise locator 관계 확인이 필요하다. host 6단계 NOT_RUN.
- 9dfb884 build 39017137521은 workflow SUCCESS(artifact 미회수), API26/34/36 device 39017137527은 실행 중이다. 이전 c1ab039 matrix는 전체 FAIL(API26 job SUCCESS, API34/36 FAIL; artifact 미회수)이다. 다음 세션은 이 결과 확인부터 시작한다. 원격 작업은 취소하지 않았다.
- unsigned release c1ab039 APK/AAB와 정적 감사 PASS를 별도 hash로 보존했다. 최종 소스 release 실행·물리 성능/16KB/TalkBack·독립 리뷰·출시 승인은 미완료다.

아래 절은 이전 시점 이력이다. 최신 전달서의 범위/실패/브랜치 정보를 우선한다.


## 2026-10-17 강화된 제품 검증 및 M10 후속

- main 3940a35는 EPUB 기존 API36 52/TXT host 2 PASS 및 문서다. 후속 강화 검증 964f8cc에서 글자 크기 변경 후 검색 문단이 viewport 밖으로 이동하는 실제 FAIL을 발견했다. progression 근접 결과를 exact restore 완료로 취급하지 않는다.
- codex/comic-product a5a7a31은 SDK 텍스트/첫 표시 요소 locator 보존, 3a629a4는 목록 상태 캡처와 EPUB aggregate markup/positions 한도를 보완했다. 원격 결과 확인 중이다.
- 9dfb884는 세 포맷 공통 위치 메뉴, Comic 돌아가기, 서재 그리드, EPUB publisher style 기본 유지/명시적 opt-out을 추가했다. local unit 66/shared 74/EPUB 16, app/test APK/lint PASS; 새 native 미확인.
- c1ab039 Android 26/34/36 매트릭스 실행 중. EPUB/Comic 각 외부 host 2단계는 아직 PASS라고 주장하지 않는다.
- c1ab039 unsigned release APK/AAB 실제 빌드·APK 정적 감사 PASS, 별도 source/hash 보존. QA/PERFORMANCE/SECURITY/RELEASE_CANDIDATE_REPORT를 후속 worktree에서 작성했다. 물리 기기/16KB 실행/TalkBack/최종 release 승인/신규 독립 리뷰는 미완료다.
- 207d2a1(Comic 이동 중 baseline), 361b3f9(TXT LazyColumn 측정), 964f8cc(EPUB 실제 문단)의 전체 native FAIL 및 logcat 원본을 보존했다. 기존 성공 증거를 지우지 않는다.


## 2026-10-17 main EPUB 제품 통합 — 최신

main `22526d3`에 검증된 EPUB 제품 연결을 fast-forward로 반영했다. native API36 **52 cases + 기존 TXT 외부 host 2 stages PASS**, skip/failure/error 0이며 [원본](evidence/m01-epub-validation/remote-22526d3-device/)을 보존했다. `14c8c42`의 commit acknowledgement 보호도 native 49/host 2 PASS다. [EPUB 제품 보고서](M08_EPUB_PRODUCT.md)를 따른다.

Comic은 `/workspace/bookreader-mobile-comic-product`에서 진행한다. `207d2a1` 52개 중 51 PASS/1 회전 baseline FAIL을 확인했다. 스크롤 안정화 후 비교하도록 교정했고, 연속 이미지 확대 화면·핀치/RTL·실제 검색 문단 viewport 확인과 EPUB/Comic 각각의 외부 host 2단계를 추가했다. `361b3f9` 및 `964f8cc` 원격 검증 중이며 후속 resource/UI bounds를 점검하고 있다. 이전 worktree와 실패 원본을 삭제하지 않는다.

`14a79be` unsigned release APK/AAB가 빌드됐고 APK 정적 감사 PASS다. 실제 서명·배포·물리 성능·16KB 기기·TalkBack 및 독립 신규 review는 NOT_RUN이다. M04–M11 전체/출시 gate를 완료로 올리지 않고 남은 계획을 계속 진행한다.

## 2026-10-17 M07 후속 및 EPUB/Comic 제품 브랜치

- main `7eaa0f5`: 먼 TXT seek placeholder 보완, Reader composition 소유권, 복원 scrollToItem의 명시적 Main 복귀, 실제 bounded EPUB 검색 hook 및 1080×1600 Comic 100장 fixture. local APK/lint/관련 unit PASS. main 원격 build PASS, device 39013554915 실행 중.
- `f7483e6` actual API36 49개 중 46 PASS/3 FAIL. M07 메뉴와 6회 왕복 seek는 PASS. EPUB search service 미적용·DocumentsUI 항목 실패·IO snapshot 경로의 UI 재측정 오류는 원본 보존했다. 새 수정의 전체 native PASS/host PASS를 아직 주장하지 않는다.
- `/workspace/bookreader-mobile-reader`, `codex/archive-reader`의 `eb73378`: 제품 EPUB import/Fragment host/설정/TOC/search/bookmark/복원 연결. bcc2081 local full CI 64 Android unit/71 shared/13 EPUB와 remote build PASS. native 39013633781 및 후속 39014076167은 실행 중. 아직 main 제품 기능 아님.
- `/workspace/bookreader-mobile-comic-product`, `codex/comic-product`의 `207d2a1`: Comic 제품 import/단일·연속·웹툰 preset/방향·핏/zoom/북마크/위치 연결. local Android unit 64/shared 72, APK/test APK/lint PASS. native 39014175686 실행 중. 연속 보기 pinch UX·추가 SAFE 및 실제 복원 검증은 계속 필요하다. 아직 main 제품 기능 아님.
- [APK 정적 감사](M10_PACKAGE_AUDIT.md)는 위 브랜치의 정확 APK hash/4 ABI/ELF·ZIP 16KB/manifest 검사를 구분한다. 물리 16KB 기기·성능·TalkBack·release/서명·전체 P0는 NOT_RUN이며 M10/M11 완료가 아니다.
- 남은 전체 계획 진행 목표는 계속 유효하다. 실패/원본 evidence 및 모든 기존 worktree를 보존하고 결과를 확인한 뒤 main에 검증된 변경을 통합한다.

## 2026-10-17 archive 진행도·bounded SDK 검색 기반

main `6ee9d4f`까지 local full CI Android 64/shared 71/archive·EPUB·Comic 13 PASS다.
[M08 기반 보고서](M08_ARCHIVE_PROGRESS.md)에 저장 gate와 검색 자원 한도를 기록했다.
제품 host는 `/workspace/bookreader-mobile-reader`에서 별도 진행 중이며 아직 main 사용자 경로가 아니다.
M07 seek diagnostic/device 및 Comic 실제 decode는 원격 결과를 기다리는 중이다. 이전 native FAIL은 유지한다.


## 2026-10-17 추가 검증 및 Comic decode 기반

main `30853c6` local full CI: Android 60/shared 71/archive·EPUB·Comic 11 PASS, schema 5 및 APK/lint PASS.
[M09 기반 보고서](M09_COMIC_ENGINE.md)는 manifest/정적 decode·예산·취소 소유권 범위를 설명한다.
제품 Comic UI/gesture는 미구현이다. 실제 decoder 2개와 M07 diagnostic native 회귀는 실행 후 별도 기록한다.
`3b48430` device 43 중 M07 seek timeout 1 FAIL, `3d8091f` 44 중 seek와 resource assertion 2 FAIL을 원본 보존했다.
SDK Publication.get은 query/fragment를 제거한 local fallback을 제공하므로 object null과 실제 bytes 차단을 구분한다.
`d9f98eb`는 Material Slider 단독 회귀와 엄격한 seek 목표·phase 진단을 추가했다. 실패 조건을 완화하지 않는다.
`/workspace/bookreader-mobile-reader`의 archive progress owner는 local 4개 PASS이며 아직 main에 반영되지 않았다.
남은 전체 계획 목표를 유지하고 우선 M07 native 실패를 해결한다.


## 2026-10-17 EPUB probe·M07 후속·archive journal

main `4019766`까지 구현했다. [EPUB probe](M01_EPUB_PROBE.md)의 API36 EPUB 3개는 PASS지만
전체 device suite는 `6b59093` 2 FAIL/`46da7fa` 1 FAIL로 보존했다. host stage는 NOT_RUN이다.
창 설정 소유권(`b7a1898`)과 메뉴 테스트의 이전 fling baseline(`3b48430`)을 보완했고 새 원격 검증 중이다.
[archive journal](ARCHIVE_IMPORT_JOURNAL.md)은 schema 5/포맷별 한도/취소·중단 복구를 구현했다.
local full CI Android 60/shared 71/EPUB 8 PASS이며 archive 제품 UI 연결은 아직 없다.
남은 전체 계획 진행 요청은 계속 유효하다. 기존 M04–M07 전체·실기기·TalkBack·출시 gate를 자동 완료하지 않는다.


## 2026-10-17 M07 main 반영 및 EPUB 선행 probe

`a183271` main에 메뉴·취소 가능한 위치 slider·탭/swipe·창 설정을 반영했다.
local/remote build unit 59/shared 67/remote ENOSPC 3 PASS, device run 39007531744는 실행 중이다.
[보고서](M07_MOBILE_CONTROLS.md)와 `docs/evidence/m07-validation`을 따른다.
`/workspace/bookreader-mobile-epub`의 `codex/m01-epub`에서 Readium 3.1.2 격리 probe를 구현 중이다.
최신 3.4.0은 현재 compileSdk/Kotlin보다 높은 요구를 명시하므로 기존 도구와 호환되는 안정 버전을 검증한다.
EPUB 제품 가져오기/독서로 연결한 상태가 아니다. 전체 남은 계획 진행 목표는 계속 유효하다.


## 2026-10-17 M06 main 검증 및 M07 진행

`f208a06` main에서 local/remote unit 55/shared 67, remote ENOSPC 3, API36 ordinary 38/host 2 PASS다.
검색·북마크·해석 전환 및 M05 추가 설정/OS 배율·밀도 회귀를 실행했다.
[보고서](M06_READER_TOOLS.md), [summary](evidence/m06-validation/summary.json)가 최신 근거다.
M07은 `/workspace/bookreader-mobile-m07`에서 구현·검증 중이다. 남은 전체 계획 진행 요청은 계속 유효하다.
이전 절의 M06 미반영/미실행 안내보다 이 절을 우선한다. 전체 M04–M06와 출시 gate를 자동 완료로 올리지 않는다.


## 2026-10-17 M05 페이지·설정 핵심 검증 및 M06 착수

source `1349ce8ee502686bd217373257e96432a7ff0204`에서 local/remote unit 52/shared JVM 57,
remote ENOSPC 3, actual API36 ordinary 35/외부 host 2 stages 모두 PASS다.
Room schema 3의 전역/책별 설정, bounded 실제 측정 페이지, mode/size reflow를 구현했다.
`6552d56` local full CI의 간헐적 restoration timeout을 보존하고 READY/anchor 게시를 보완했다.
[보고서](M05_PAGINATION.md), [summary](evidence/m05-validation/summary.json)가 근거다.

M05는 IN_PROGRESS다. 글꼴/행간/자간/여백 및 실제 OS 배율·밀도 조합과 안정된 첫 페이지 PNG를 후속 검증한다.
M06은 `/workspace/bookreader-mobile-m06`의 `codex/m06-tools`에서 구현 중이며 아직 main 기능이 아니다.
검색·북마크(schema 4)·수동 인코딩 전환을 연결했고, 실제 UI 및 SAFE-01 bookmark 불변 회귀를 준비했다.
사용자 목표는 남은 계획 전체 진행이다. M04 전체와 실기기/출시 gate를 완료로 올리지 않고 계속 진행한다.


## M04 실제 UI 경쟁·취소 — 2026-10-17

source `85653ada7259c9c80329ca759e5fc43df870cfff`에서 local/remote unit 50/shared JVM 53,
remote 실제 ENOSPC 3, actual API36 ordinary 33/외부 host 2 stages 모두 PASS다.
취소 전 flush 대기와 Room DB Job의 연결 대기를 분리해 수정했다. A/B 6회(역방향 2회),
복원 중 close/reopen 5회, 실제 회전/ON_STOP의 pending anchor, 32MiB decode 중 삭제를 검증했다.
`14fb88c` device 1 FAIL/host NOT_RUN과 수정 전 unit timeout은 보존했다.
[범위 보고서](M04_UI_RACES.md), [summary](evidence/m04-ui-races-validation/summary.json),
[정확 명령](evidence/m04-ui-races-validation/commands.json)이 최신 근거다.

M04는 IN_PROGRESS다. M05 설정 reflow, M06 bookmark 불변, 실제 Android storage/fsync,
실기기 성능 및 신규 Astra 검토는 미완료다. 사용자의 남은 계획 전체 진행 요청에 따라 M05 구현을 이어간다.
로컬 기기는 없어 exit 78/BLOCKED_ENV이며 원격 emulator 결과와 구분한다.


## 2026-10-17 SAFE-01 실제 Reader 조회 오류·재시도

source `0e2605e9a21be7fc72cfa6a72e36e89de406fe3d`에서 native SQLite 조회 오류와 malformed locator의 실제 Reader UI 회귀 2개를 추가했다.
실패한 재시도 2회 동안 전체 progress/book/read_order 및 두 관리 파일을 보존하고,
장애 해제 후 이전 commit의 TextLayout 위치 복원·정확히 1회 activation·새 epoch 저장을 확인했다.
production 코드/schema 변경은 없다. local(초기 source)/최종 remote unit 48/shared JVM 53, remote ENOSPC 3,
actual API36 ordinary 29/외부 host 2 stages 모두 PASS다. 최초 199e9b9의 예외 타입 이름 assertion FAIL과 host NOT_RUN은 원본 보존했다. local device는 기기 부재로 exit 78/BLOCKED_ENV다.
[TESTING](TESTING.md), [보고서](M04_PROGRESS_READ_ERRORS.md),
[evidence](evidence/m04-read-error-validation/summary.json)가 최신 근거다.

M04는 IN_PROGRESS다. **다음 우선 작업은 실제 UI A/B 및 같은 책 rapid close/reopen**이다.
SAFE-01 bookmark 불변은 현재 entity/DAO/UI 미구현으로 NOT_RUN이며 M06 이후 검증한다.
삭제 중 decode/index, 실제 reflow/ON_STOP, Android ext4/f2fs ENOSPC/fsync, 실기기 성능 및 신규 Astra 검토도 남는다.
이하 이전 절의 '다음 SAFE-01' 안내보다 이 절을 우선한다.

## 2026-10-11 실제 ENOSPC 및 activation 중복 최종 검증

최종 source `ebee7723eed8c7ca487736af52570ae1cfd3d4ba`의 remote Android unit 48/shared JVM 53,
별도 ENOSPC 3개, actual API36 ordinary 27 및 외부 host 2 stages 모두 PASS다.
local 48/53/3은 동일 production/test 입력의 `bacd79f`에서 실행했고, 후속 UI test APK도 compile PASS다.
8MiB tmpfs의 실제 errno 28/여유 공간 0에서 production WAL 쓰기가 SQLite FULL(13)로 실패함을 확인했다.
update/첫 insert/activation의 전체 기록·다른 책·TXT hash 보존, 공간 확보 후 DB reopen·동일 요청 retry도 PASS다.

원격 기기 회귀에서 발견한 REJECTED_STALE를 조사해 중복 restoration callback의 activation 2회를
단위 테스트로 재현했다. restored() 진입 직렬화 및 LAZY job 등록 후 시작으로 수정했고 기존 기기 회귀도 PASS다.
`a3ee33b` device FAIL/host NOT_RUN과 두 CI namespace 실패는 원본 보존했다.
`bacd79f` page-limit UI 측정 실패도 보존했으며 오류 배너의 reflow 완료 후 측정하게 보완했다.
관리자 CI는 private mount namespace, 비권한 local은 user+mount namespace를 사용한다. 전역 보안 설정은 바꾸지 않는다.

[TESTING](TESTING.md), [범위 보고서](M04_FILESYSTEM_ENOSPC.md),
[evidence](evidence/m04-enospc-validation/summary.json)가 최신 근거다.
M04는 IN_PROGRESS다. 다음 우선 작업은 SAFE-01 actual Reader DB 조회 오류/재시도 UI다.
기존 managed-copy 누락 재시도와 DB 조회 오류 검사를 구분한다. UI A/B·같은 책 rapid reopen,
삭제 중 decode/index, reflow/ON_STOP, Android 실제 저장장치 ENOSPC/fsync, 실기기 성능 및 신규 Astra도 남아 있다.

## 2026-10-11 SAFE-07 native SQLite FULL 최종 검증

검증 source `bf7589dcc018c57fc43101a16198cc3afd12b9cd`는 local/remote Android unit 47/shared JVM 53,
actual API36 ordinary 27 및 외부 host 2 stages 모두 PASS다. 신규 DB 3개는 native SQLITE_FULL(13)의
update/첫 insert/activation rollback·재개방·동일 event 재시도를 확인했다.
실제 Reader에서도 오류 UI, 마지막 commit/다른 책/관리 파일 보존 및 공간 복원 후 자동 재시도를 검증했다.
실패 시 offset 4964/sequence 1을 보존하고, 용량 복원 뒤 pending 6625를 sequence 3으로 commit하며 오류가 사라졌다.
테스트 한도와 부가 schema도 복원됐다. production writer/UI/schema 변경은 없다.

실제 60초 연속 저장(60,017ms/60 commits)과 host PID 5794→absent→cold 5843,
offset 13285/epoch 1/sequence 3의 서재 Continue 복원도 회귀 PASS다.
[TESTING](TESTING.md), [범위 보고서](M04_SQLITE_FULL.md), [SAFE matrix](M04_SAFETY_MATRIX.md),
[evidence](evidence/m04-sqlite-full-validation/summary.json)가 최신 근거다.

native page-limit FULL은 OS filesystem ENOSPC와 구분한다. M04는 IN_PROGRESS다.
다음 우선 작업은 격리된 제한 용량 파일시스템에서 실제 ENOSPC/WAL·journal 부족 시 기록 보존 검증,
또는 SAFE-01 실제 Reader 조회 오류/재시도 UI다. UI A/B·같은 책 rapid reopen, 삭제 중 decode/index,
reflow/ON_STOP, 실기기 성능 및 신규 Astra 최종 검토도 남아 있다.

## 2026-10-11 SAFE-09 최종 검증

검증 source는 `73276fe27da781dde807f05a85422ae18363642f`다. main에 commit/push했으며
local/remote CI는 Android unit 47/shared JVM 50, API36 ordinary 26 및 actual host 2 stages 모두 PASS다.
실제 60,001ms 연속 touch에서 anchor 226회/commit 46회, 최대 commit 관찰 공백 2,034ms를 확인했다.
host는 live PID 5672→force-stop→absent→cold PID 5719, offset 13285/epoch 1/sequence 2를 서재 Continue로 복원했다.

60초 회귀가 LazyList의 mutable item 비교로 같은 fragment 내부 이동을 놓치는 결함을 재현했다.
Reader는 snapshotFlow 안에서 index/offset을 값으로 캡처하도록 수정했다. host 준비는 마지막 fling/layout이
안정된 뒤 예상 위치를 캡처한다. `78e45f5` continuous FAIL과 `14ce759` host 준비 FAIL도 원본 보존했다.
[TESTING](TESTING.md), [측정 보고서](M04_COMMIT_LATENCY.md), [SAFE matrix](M04_SAFETY_MATRIX.md),
[evidence](evidence/m04-continuous-validation/summary.json)가 최신 근거다.

SAFE-09 emulator correctness는 PASS, M04는 IN_PROGRESS다. 다음 우선 작업은 SAFE-07 실제 SQLite disk-full
및 마지막 commit/다른 책 보존 검증이다. UI A/B·같은 책 rapid reopen, 삭제 중 decode/index, reflow/ON_STOP,
SQL write-ack/실기기 p95·메모리, 신규 Astra 최종 검토는 미완료다.

## 2026-10-11 DB 경쟁 및 host 준비 후속

실제 파일 DB race 5개(지연 A→B/이전 세션/역방향 sequence/동시 duplicate/삭제 중·후 save)를 추가했다.
12회 세션 변경·DB reopen과 전체 persisted record 보존을 검사하며 UI navigation을 검사한 것으로 취급하지 않는다.
직전 `16e4615` remote ordinary 25는 PASS, host prepare는 CalledFromWrongThreadException으로 FAIL이다.
raw stack의 import progress→worker UI 게시 경로를 수정하고 수정 전 실패하는 회귀를 추가했다.
검증 source는 `96c15aa6d074b0b6d96579e1deb54d6fbc3413ec`다. local/remote CI는 Android unit 46/shared JVM 50 PASS,
remote device `38110471069`는 ordinary 25/actual host 2 stages PASS다. raw PID 5640→absent→cold 5688,
commit offset 11607/epoch 1/sequence 2의 서재 Continue 복원을 확인했다. 이전 host FAIL과 구분한다.
이 절 이후 SAFE-09 실제 연속 이동은 상단 후속에서 검증했다. 다음 우선 작업은 SAFE-07 실제 SQLite disk-full 보존 검증이다.
최신 근거는 [TESTING](TESTING.md), [SAFE matrix](M04_SAFETY_MATRIX.md),
[evidence](evidence/m04-race-validation/summary.json)에 있다. 신규 Astra 검토와 전체 M04는 미완료다.

## 2026-10-11 후속 작업

`03870c9` clean main을 새 환경에 clone한 뒤 M04의 REFLOWING 미커밋 flush를 보완했다.
`persistStable`은 READY에서 수락된 stableOffset을 REFLOWING에서도 저장하며,
재배치 callback의 임시 0·비사용자 이동은 여전히 anchor를 변경하지 못한다.
새 필수 controller 회귀 4개와 수정 전 실패 증거가 추가됐다.
정확한 최신 검증 결과는 [TESTING](TESTING.md)의 M04 절 및
[evidence summary](evidence/m04-reflow-validation/summary.json)를 따른다.
아래 10월 9일 PASS는 과거 결과이며 새 변경의 device/host PASS를 의미하지 않는다.

아래 reflow 후속 당시 잔여 작업은 전체 SAFE matrix, A/B rapid close/reopen, 60초 연속 이동, 실제 disk-full,
실제 기기의 reflow/ON_STOP 및 commit-latency다. 신규 Astra 최종 검토는 NOT_RUN이다.
로컬 기기 검증은 authorized device가 없어 exit 78/BLOCKED_ENV다.
새 환경에 `/workspace/toolchains/mobile-env.sh`를 준비했으며 기존 APK/서명키는 가져오지 않았다.
새 debug build를 이전 제공 APK와 같은 signer라고 간주하지 않는다.

## 붙여넣기용 재개 프롬프트

```text
/workspace/bookreader-mobile의 docs/CONTEXT_HANDOFF.md를 읽고 이어서 작업해 주세요.
먼저 /AGENTS.md, /workspace/AGENTS.md, 저장소 AGENTS.md와 git status/HEAD/remote를 다시 확인하고 사용자 변경을 보존하세요.
최신 TASKS/TESTING/WORKLOG와 실제 코드를 읽으세요. M00 및 TXT 내부 알파 M02/M03은 DONE,
M01은 encoding 부분만 검증됐고 Readium/EPUB는 보류입니다. 다음 우선 작업은 M04 잔여 안전 계약입니다.
구현은 Sol 6.1 high, 최종 검토는 Astra를 사용하는 사용자 요청을 유지하세요.
기존 README를 보존하고 승인된 범위의 commit/push는 진행하되 force push/원격 merge/release는 하지 마세요.
기존 PASS를 새 변경의 PASS로 재사용하지 말고 관련 검증을 실제 실행한 뒤 증거와 미실행 항목을 남기세요.
```

## 요청·권한·기준

- 저장소: [LaceyLuv/BookreaderM](https://github.com/LaceyLuv/BookreaderM), 로컬 `/workspace/bookreader-mobile`.
- 작성 전 상태는 clean `main`, 문서 기준 HEAD `d5a4e32d12876028a9e09c0b167a3eb44e6ed4a3` (`docs: complete TXT import and reader validation`). 이 인계 파일 자신의 후속 커밋 SHA는 예측하지 않는다.
- 실제 최종 검증 소스는 `2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf`다. 위 HEAD는 검증 결과를 정리한 문서 커밋이다.
- 최초 M00 한 작업 요청 이후 사용자가 **TXT 추가와 실제 읽기를 우선하는 범위 확장**을 승인했다. Readium/EPUB 보류는 그 우선순위에 따른다.
- 사용자는 구현 `Sol 6.1 high`, 최종 검토 `Astra`를 요청했고 Git commit/push를 승인했다. 기존 README 보존, destructive reset/clean·force push·원격 merge·release/스토어 배포는 이 승인에 포함되지 않는다.
- Android-first/local-first 모바일 독립 저장소다. PC 포팅, iOS/AI/동기화/통계/백업 UI로 범위를 넓히지 않는다.
- 권위 순서: 사용자 지시와 실제 [AGENTS.md](../AGENTS.md), [개정 계획](BookReader_Mobile_v1_1_Implementation_Plan.md), [TASKS](TASKS.md), 실제 소스·실행 증거. [원문](reference/BookReader_Mobile_v1_Plan.md)은 이력이다.
- [README_START_HERE](../README_START_HERE.md) 등 최초 패키지 문서의 “미구현” 표시는 10월 1일 기록이다. 현재 상태는 아래와 최신 [TESTING](TESTING.md)/[WORKLOG](WORKLOG.md)를 따른다.

## 현재 상태와 사용자 경로

| 작업 | 현재 판정 | 범위·제한 |
|---|---|---|
| M00 | DONE | Android/KMP/Room/CI 기반과 당시 실제 검증 완료 |
| M01 | IN_PROGRESS | strict encoding host 14·실제 Android encoding 3 PASS; Readium/EPUB probe·ADR-0005 보류 |
| M02 | DONE | TXT 내부 알파의 안전 import·관리 복사본·서재·삭제/recovery |
| M03 | DONE | TXT 내부 알파의 스크롤 독서·canonical anchor·복원·이어읽기 |
| M04 | IN_PROGRESS | epoch/sequence·DB race·60초 연속 저장 및 과거 force-stop 복원 검증; 최신 host 결과와 잔여 gate는 상단 참조 |
| M05–M11 | TODO | TXT 페이지/본문 검색/북마크, 접근성, EPUB, Comic, hardening/출시 감사 후속 |

- 앱은 `0.2.0`/versionCode 2 **TXT 내부 알파**다. 세 포맷 최종 v1.0 완료 판정이 아니다.
- `TXT 추가` → 시스템 picker → 앱 내부 관리 복사본 commit → `읽기`. 외부 `ACTION_VIEW`는 안전 import 후 Reader, `ACTION_SEND`는 사용자 확인 → commit → `읽기`다.
- 일반 새 프로세스는 서재에서 시작한다. Reader 밖 하단 이어읽기 바로 마지막 성공한 책을 연다. 따뜻한 Reader 복귀·Activity 재생성 복원은 별도 경로다.
- UTF BOM/strict UTF-8 및 수동 UTF-16LE/BE·CP949·EUC-KR 선택을 제공한다. BOM 없는 UTF-16/legacy charset을 자동 확정하지 않는다. 제품 내 책별 인코딩 변경 UI는 M06이다.

## 반드시 유지할 안전 계약

- 영구 원본은 앱 내부 관리 복사본이다. source URI는 출처이며 원본을 삭제하지 않는다. import는 journal의 COPYING/VALIDATING/FINALIZING/COMMITTED와 멱등 recovery·no-overwrite 승격을 유지한다. [ADR-0002](adr/0002-managed-import-journal.md)
- locator는 version/contentRevision을 갖는다. TXT 좌표는 정규화 후 0-based UTF-16 코드 단위 `Long` offset이며 페이지 번호/캐시 byte offset은 영구 위치가 아니다. 첫 BOM 제거·CRLF/CR→LF만 수행하고 trim/빈 줄 축약/NFC는 하지 않는다. [ADR-0003](adr/0003-locator-normalization-and-revision.md)
- canonical cache는 파생 UTF-16LE/fragment index다. bounded streaming·전체 strict decode/hash 검증·완료 marker를 유지한다. 캐시 삭제는 원본/기록 삭제가 아니다.
- 진행도는 `Found / Missing / Error`를 구분한다. 조회·본문 integrity 검증·실제 위치 복원 전에 저장하거나 오류를 0%/Missing으로 바꾸지 않는다. 단순 열기 실패가 lastRead/readOrder를 바꾸지 않는다.
- 이벤트마다 bookId/revision/sessionEpoch/sequence를 캡처하고 DB가 오래된 epoch/sequence·다른 revision·DELETING을 거부한다. 최대 offset 비교나 debounce 하나로 최신성을 판정하지 않는다. [ADR-0004](adr/0004-progress-writer-session-order.md)
- 서재/import/Reader는 앱 소유 `AndroidDatabaseOwner` Room instance/pool을 빌리며 borrower가 닫지 않는다. 독립 test용 `createAndroidDatabase`는 caller-owned다. DB 오류를 빈 서재로 처리하거나 destructive migration fallback을 추가하지 않는다.
- production 진단은 고정 단계/기존 result code와 최대 4개·각 64자 이하 예외 클래스 chain만 기록한다. 책 본문/개인 URI/path/SQL/예외 메시지/stack trace/토큰/서명키를 공개 로그·CI에 넣지 않는다.

## 확정된 검증·증거

아래는 `2be610a`에서 **실제로 실행한 과거 결과**다. 새 코드 변경의 검증으로 자동 승계하지 않는다. ordinary Android 25 cases와 host 2 stages는 서로 다른 검사다.

| 실행 | 결과 | 실제 증거 |
|---|---|---|
| 로컬 full CI6 `./scripts/ci-check.sh` | exit 0/PASS | Android unit **41**, shared JVM **45**, failure/error/skipped **0**, lint **20 Hint**, 앱/test APK·DB/migration/schema PASS, source drift 0; [summary](evidence/m02-m03-validation/local-ci-attempt-6/summary.json) |
| 원격 build [37879616525](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616525) | PASS | native 41/45·failure/error/skipped 0, lint 20 Hint, schema 1/2 local bytes 일치; [summary](evidence/m02-m03-validation/remote-2be610a-build/summary.json) |
| 실제 API36 device [37879616383](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616383), `./scripts/device-check.sh` | exit 0/PASS | ordinary **25**·failure/error/skipped **0**, 별도 host **2 stages**·명령 exit 0, 자체 fixture PNG **4개** hash/1080×1920 decode PASS; [summary](evidence/m02-m03-validation/remote-2be610a-device/summary.json) |

- 증거 기준 디렉터리는 `docs/evidence/m02-m03-validation/`이며 실제 파일은 깊은 원격 artifact 경로 대신 아래 보존 경로에서 읽을 수 있다.
- 로컬 native XML/manifest: [local-ci-attempt-6](evidence/m02-m03-validation/local-ci-attempt-6/)의 `native-android-unit/`, `native-shared-jvm/`, `native-evidence-manifest.json`.
- 원격 build XML/schema: [remote-2be610a-build](evidence/m02-m03-validation/remote-2be610a-build/)의 `native-android-unit/`, `native-shared-jvm/`, `native-schemas/`.
- 기기 원본: [native-connected](evidence/m02-m03-validation/remote-2be610a-device/native-connected/), [host-process-proof](evidence/m02-m03-validation/remote-2be610a-device/host-process-proof/), [command-evidence](evidence/m02-m03-validation/remote-2be610a-device/command-evidence/), [fixture-screenshots](evidence/m02-m03-validation/remote-2be610a-device/fixture-screenshots/).
- host는 실제 살아 있던 PID 5624 → 외부 adb force-stop → PID absent → cold launcher PID 5671을 확인했다. 마지막 commit offset **11607**, epoch **1**, sequence **2**를 cold 서재 Continue로 복원했다. Activity recreation과 구분하며 미커밋 화면 위치 보존을 뜻하지 않는다. verification instrumentation이 다른 target process를 시작할 수 있다.
- Astra가 source/필수 test identity/raw host stage/PID 종료 proof/PNG를 독립 검토해 좁은 TXT 내부 알파 M02/M03 gate를 승인했다.
- 과거 FAIL/BLOCKED_ENV를 삭제하지 않는다. 특히 `cfe17f0` DATABASE_UNAVAILABLE와 `3ce3386` 외부 VIEW Reader ERROR는 이력이다. Room 공유 후 회귀 PASS는 최초 SQLite BUSY 내부 원인 확정/해결 증명이 아니다.
- **NOT_RUN:** 실기기 5MiB/50MiB p95·메모리, OEM 실제 회전, TalkBack, 최종 native ABI/16KB, EPUB/Readium. emulator correctness와 출시 gate를 혼동하지 않는다.

## 제공 APK·서명

- 현재 로컬 파일: `/workspace/bookreader-mobile-0.2.0-debug.apk` (새 clone에 자동 포함되는 파일이 아님).
- SHA-256 `021ec47de8311435f6becd0eb5b6e677ed22312b6ef3740b86d86b60d10a6e5e`, **17,027,692 bytes**, 동일 검증 source `2be610a`의 debug 빌드. [실제 metadata](evidence/m02-m03-validation/final-local-apk.json)
- 기존 로컬 M00 signer `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 유지한다. CI 독립 signer `980adc346f1c476d4a98ef096b0782bbe153871296fb17d7b94f94ff6fee70d5`와 다르다.
- 기기 job에 설치 APK hash가 없어 제공 APK와 device 설치본의 **byte 동일성은 미확인**이다. 동일 source PASS와 artifact 동일성을 구분한다.
- 서명 불일치 시 uninstall/data clear를 권장하지 않는다. 기존 사용자 책/기록을 보호한다. 이전 로컬 APK는 `/workspace/bookreader-mobile-0.2.0-debug-before-2be610a.apk`에 보존되어 있다.

## 소스 탐색 지도

아래 `androidApp/...`와 `shared/...`는 실제 패키지 `org/bookreader/mobile` 아래 파일이다. 링크는 저장소 상대 경로다.

| 책임 | 먼저 읽을 파일 |
|---|---|
| 시작·UI·import 연결 | [MainActivity](../androidApp/src/main/kotlin/org/bookreader/mobile/MainActivity.kt), [AppViewModel](../androidApp/src/main/kotlin/org/bookreader/mobile/ui/AppViewModel.kt), [BookReaderApp](../androidApp/src/main/kotlin/org/bookreader/mobile/ui/BookReaderApp.kt), [AndroidBookManagement](../androidApp/src/main/kotlin/org/bookreader/mobile/ui/AndroidBookManagement.kt) |
| URI 수신·관리 파일·journal | [IncomingDocument](../androidApp/src/main/kotlin/org/bookreader/mobile/importing/IncomingDocument.kt), [AndroidManagedImportFiles](../androidApp/src/main/kotlin/org/bookreader/mobile/importing/AndroidManagedImportFiles.kt), [ImportCoordinator](../shared/src/commonMain/kotlin/org/bookreader/mobile/importing/ImportCoordinator.kt), [ImportDao](../shared/src/commonMain/kotlin/org/bookreader/mobile/database/ImportDao.kt) |
| strict encoding·cache·Reader | [TxtDecoder](../androidApp/src/main/kotlin/org/bookreader/mobile/encoding/TxtDecoder.kt), [TxtCanonicalCache](../androidApp/src/main/kotlin/org/bookreader/mobile/reader/TxtCanonicalCache.kt), [TxtReaderController](../androidApp/src/main/kotlin/org/bookreader/mobile/reader/TxtReaderController.kt), [TxtReaderScreen](../androidApp/src/main/kotlin/org/bookreader/mobile/reader/TxtReaderScreen.kt) |
| DB 소유권·progress·migration | [AndroidDatabaseFactory](../shared/src/androidMain/kotlin/org/bookreader/mobile/database/AndroidDatabaseFactory.kt), [AndroidStores](../androidApp/src/main/kotlin/org/bookreader/mobile/ui/AndroidStores.kt), [RoomProgressWriter](../shared/src/commonMain/kotlin/org/bookreader/mobile/repository/RoomProgressWriter.kt), [ProgressWriterDao](../shared/src/commonMain/kotlin/org/bookreader/mobile/database/ProgressWriterDao.kt), [Migrations](../shared/src/commonMain/kotlin/org/bookreader/mobile/database/Migrations.kt) |
| 회귀·필수 identity·CI | [controller unit](../androidApp/src/test/kotlin/org/bookreader/mobile/reader/TxtReaderControllerTest.kt), [실제 DB writer](../shared/src/jvmTest/kotlin/org/bookreader/mobile/repository/RoomProgressWriterTest.kt), [ReaderFlow](../androidApp/src/androidTest/kotlin/org/bookreader/mobile/reader/TxtReaderFlowTest.kt), [host stages](../androidApp/src/androidTest/kotlin/org/bookreader/mobile/reader/TxtReaderHostProcessTest.kt), [required-tests](../scripts/required-tests.json), [build workflow](../.github/workflows/mobile-ci.yml), [device workflow](../.github/workflows/mobile-device-tests.yml) |

## 다음 우선 작업: M04

1. [TASKS M04](TASKS.md)·개정 계획 부록 B SAFE matrix·ADR-0004와 controller/writer를 읽고 앱 수준 single writer·작업/취소 소유권·flush barrier의 현재 구현을 확인한다.
2. `persistStable`은 READY 및 REFLOWING에서 검증된 stableOffset을 저장한다. **REFLOWING 미커밋 flush는 10월 11일 controller 수정/회귀 대상으로 보완했으며**, 실제 기기의 close/ON_STOP·재배치 UI 검증을 이어간다. 임시 0 locator/복원 callback이 기존 위치를 덮어쓰면 안 된다.
3. SAFE-01 조회 오류, 02 A 저장 지연→B, 03 이전 세션 지연 write, 04 역방향 sequence race, 06 삭제 중 save/decode/index, 07 disk-full, 09 60초 연속 이동, 10 외부 종료, 12 reflow를 실제 matrix로 검증한다. 기존 일부 회귀 PASS만으로 전체 matrix를 체크하지 않는다.
4. A/B 책 및 같은 책 빠른 close/reopen, 손상 locator/조회 오류 기록 보존·재시도 UI, 1초 sampling+250ms settled save·60초 연속 스크롤 중 주기 commit을 검증한다. commit-latency report는 측정 기기/빌드/fixture·미측정 항목을 명시한다.
5. 관련 unit·실제 DB·Android·외부 host 검증을 실행하고 ADR-0004/TASKS/TESTING/WORKLOG에 source SHA 또는 working-tree fingerprint, 정확 명령/exit code·native XML/raw proof·미실행 blocker를 남긴 뒤 Astra 최종 검토를 받는다.

M01 EPUB 재착수는 M00 기반 확인과 사용자 우선순위에 맞는 별도 작업으로 한다. 현재 Readium은 **미추가**, 문서 후보 3.4.0은 확정 pin이 아니다. 공식 stable Android Navigator/API·라이선스·host 형태·Locator round-trip·정상 이미지/CSS/검색/위치와 publication script/외부 resource 차단의 양립을 실제 probe하여 ADR-0005로 결정한다. 완성 EPUB UI/자체 렌더러·CFI/alpha Compose Navigator/DRM은 M01 제외 범위다. M08/M11은 이 gate 해소 전 완료할 수 없다.

## 환경과 이후 검증 절차

- 환경은 컨텍스트마다 재점검한다. 존재하면 `source /workspace/toolchains/mobile-env.sh`를 활용하되 이전 proxy TLS 옵션/`/workspace/toolchains/cacerts`를 임의로 덮어쓰거나 키/인증서 파일을 evidence에 복사하지 않는다. 새 clone에서는 로컬 경로 존재를 가정하지 않는다.
- pins: Temurin **17.0.16+8**, Gradle **8.13**, AGP **8.11.1**, Kotlin/Compose compiler **2.2.20**, KSP **2.2.20-2.0.3**, Room **2.8.3**, bundled SQLite **2.6.1**, Compose BOM **2025.10.00**(UI 1.9.3), Coroutines **1.10.2**, Serialization **1.9.0**, min/compile/target SDK **26/36/36**, build-tools **35.0.0**. [DEPENDENCIES](DEPENDENCIES.md)·[catalog](../gradle/libs.versions.toml)·[wrapper](../gradle/wrapper/gradle-wrapper.properties) 참조.
- 다음 작업의 기본 명령은 아래와 같다. device-check는 테스트 전용 emulator/device를 사용한다. 이전 로컬 baseline의 adb 연결 기기는 0이었다.

```bash
git status --short
git diff --check
./scripts/doctor.sh
./scripts/ci-check.sh
./scripts/device-check.sh
```

- ci-check는 실제 task discovery→assemble/lint/Android unit/test APK compile/shared JVM/Room schema를 수행한다. device-check는 ordinary connected XML 검증 후 `reader-process-check.py`의 외부 force-stop 2 stages와 raw proof를 검증한다. host만 별도 실행할 때는 `python3 scripts/reader-process-check.py build/host-process-evidence`를 사용한다.
- 새 case는 required-tests manifest/XML verifier와 실제 source를 함께 확인한다. 빈/stale XML·missing identity·skip·실패 숨김으로 PASS를 만들지 않는다. screenshot 기본값은 off이며 CI 캡처는 자체 생성 fixture만 허용한다.
- 결과는 `PASS / FAIL / NOT_RUN / BLOCKED_ENV`로 구분하고, doctor PASS·test 파일 존재·Activity recreation을 새 build/device/process-death PASS로 보고하지 않는다.

이번 인계 작성은 이 Markdown 파일 추가만 수행했다. 앱/테스트 소스 변경과 테스트 재실행은 없으며, 위 PASS는 연결된 기존 실행 증거의 기록이다.
