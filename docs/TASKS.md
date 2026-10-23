# BookReader Mobile — Codex 작업표

## 몰입형 UI 첫 native 검증 및 후속 (2026-10-23)

`2b24f7d` 원격 build 39941859801: Android 66/shared 74/EPUB 16/실제 ENOSPC 3 PASS.
Device 39941859677: API26 57 중 5 FAIL, API34 7 FAIL, API36 6 FAIL; host는 모두 NOT_RUN.
[실패 원본과 전체 사례](evidence/reader-design/ci-2b24f7d/summary.json)를 보존한다.
신규 TXT 제스처 2개와 기존 TXT 메뉴/seek, Comic 제품 경로는 세 API에서 PASS다.

후속은 오류 상태의 비활성 도구 표시, 화면 설정 변경 중 도구 표시 상태 보존,
실제 50ms tap 주입, prefetched TXT semantics와 실제 화면의 측정을 구별하는 Main 관측,
첫 EPUB 이동 전에는 accepted/saved가 null일 수 있다는 저장 금지 계약을 반영한다.
설정 캡처는 compositor frame까지 기다려 실제 dialog를 기록한다.

EPUB 확대 전후의 raw progression은 scrollY/scrollHeight이므로, 본문 viewport 확대와
글자 줄바꿈 변경 후 같은 문단을 가리켜도 값이 달라질 수 있다. 저장된 전체 locator 불변과
같은 레이아웃에서의 기존 `.03` 비교를 유지한다. 폰트 재배치/그 설정으로 재열기에서는
독립 DOM 문단 위치와 장 끝 clamp로 예상 progression을 계산해 `.03`으로 검사하고,
기존 일부 표시보다 강한 **검색 문단 전체 표시** 조건을 추가한다. 허용 오차를 넓히지 않는다.
추가 실행에서 원본/현재 숫자와 DOM을 함께 기록해 이 판단을 확인하며 아직 PASS로 보고하지 않는다.


## 몰입형 독서 화면 개편 (2026-10-23, 검증 진행 중)

사용자가 요청한 디자인/좌우 터치 동작을 서브에이전트 없이 직접 구현한다.
TXT·EPUB·Comic 도구창은 중앙 탭으로 열고 닫는 overlay로 변경하며 본문 viewport를 유지한다.
남색 도구 패널/민트 강조/본문 색상 미리보기와 설정 구획을 공유한다.
EPUB은 Readium 3.1.2 InputListener로 링크·선택 처리 뒤 방향에 맞는 좌우 이동을 연결한다.
TXT 가장자리 탭/최신 callback, Comic RTL·확대·연속 보기의 중앙 탭을 보완한다.
기존 실제 복원·검색·북마크 assertion을 유지하고 물리 tap helper 및 회귀 3개를 추가한다(일반 native 57 + host 6/API).
`./gradlew :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug :androidApp:testDebugUnitTest :shared:testDebugUnitTest :epubAndroid:testDebugUnitTest --console=plain` exit 0.
`BOOKREADER_EVIDENCE_DIR=build/design-local ./scripts/ci-check.sh` 실행 중; 최종 소스 원격 API26/34/36 결과 및 생성 fixture 스크린샷을 후속 기록한다.
현재 native/host 실행은 NOT_RUN; 로컬 /dev/kvm·연결 기기가 없어 원격 CI로 검증한다.
versionCode 3/versionName 0.3.0. 최종 검증 후 기존 개발용 서명키를 재사용한 설치 APK를 생성한다.
이 변경으로 실기기·TalkBack·출시 gate 완료를 주장하지 않는다.


## 설치 패키지 수정 (2026-10-22)

사용자 설치 오류에 대해 기존 unsigned APK의 서명 누락을 재현했다.
동일 release 내용을 기존 개발용 키로 서명한 APK를 생성했고 v2/v3 서명·정적 감사 PASS다.
실제 기기 설치는 연결 기기 부재로 NOT_RUN이다. [설치용 APK 및 증거](INSTALLABLE_APK.md)를 우선한다.
이후 업데이트에서도 같은 키를 보존하며, 서명 충돌 때문에 기존 앱을 임의 삭제하지 않는다.


## 2026-10-22 현재: 검증된 소스 main 통합 및 unsigned 재생성 완료

`bb0f537`의 build 66/74/16 + 실제 ENOSPC 3, API26/34/36 각각 native 54 + 세 포맷 host 6이 모두 PASS다.
기존 `.03` 및 실제 `p179` 표시 assertion을 유지했고 ZIP digest·필수 XML·PID 교체 원본을 확인했다.
원격 main에 이력을 보존하여 반영했으며, 같은 소스로 unsigned APK/AAB 재생성·정적 감사도 PASS다.
[최종 결과·hash·원본·미완료 범위](RESUME_COMPLETION_2026-10-22.md)를 현재 기준으로 따른다.
API34의 과거 TXT 측정 재진입은 후속 두 소스에서 재발하지 않았으나 원인 미확정이다.
실기기 성능·TalkBack·16KB OS·OEM backup·전체 corpus·독립 리뷰·서명/출시 gate는 남아 있다.

아래 내용은 이전 검증 시점의 이력이다. 당시의 main 미통합·CI 대기 표시는 현재 상태가 아니다.


## 2026-10-22 현재: 환경 연결 복구, native 후속 검증

환경 연결과 Git/CI 다운로드를 실제 확인했다. `49d1a04` build 66/74/16 + ENOSPC 3 PASS.
Native는 API26/34 각각 54개 중 2 FAIL, API36 1 FAIL이며 host는 NOT_RUN이다.
probe의 기본 CSS 경로와 picker의 화면 밖 파일 탐색을 보완했다. API34 TXT 측정 재진입은 원인 미확정이다.
[현재 변경·실패 원본](NATIVE_UI_SYNCHRONIZATION.md)을 따른다. main 통합은 전체 native/host 통과 후 진행한다.
아래 절은 이전 검증 시점의 기록이다.


## 2026-10-18 최신: probe 설정 완료 신호와 picker 조회 교정

`9a96ac5` build는 66/74/16 + ENOSPC 3 PASS, 제품 EPUB/Comic 회귀는 세 API 모두 PASS다.
전체 suite는 API26 54개 중 2 FAIL, API34/36 각각 1 FAIL이며 host는 NOT_RUN이다.
추가했던 probe의 새 페이지 콜백 대기가 설정 변경 완료 신호로 부적합해 실제 textZoom/visual callback으로 교정했다.
API26 picker는 fixture 폴더 진입 후 파일 label 탐색 실패를 보완했다. 기존 전체 assertion과 54 + 6 검증 목록은 유지한다.
[변경과 원본](NATIVE_UI_SYNCHRONIZATION.md)을 따른다. main 통합은 전체 결과 확인 후 진행한다.

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

## 재개: 최신 CI 확인 및 EPUB 복원 관찰

`175e151`(제품 코드 `9dfb884`와 동일)에서 로컬 full CI를 다시 실행했다.
Android unit 66/shared JVM 74/EPUB unit 16, 실패·오류·skip 0이며 APK/test APK/lint/schema 5 PASS다.
현재 권한 환경에서 별도 실제 ENOSPC 3개도 exit 0, tmpfs 정리 exit 0으로 통과했다.
[이번 로컬 원본](evidence/archive-resume-validation/baseline-175e151/summary.json)과
[ENOSPC 원본](evidence/archive-resume-validation/enospc-175e151/check.log)을 보존한다.

GitHub API로 조회한 `9dfb884` build 39017137521은 SUCCESS이며 device 39017137527은
API34/36 job SUCCESS, API26 job FAIL이다. 이는 metadata 판정이며 아직 native XML/host proof를
회수하지 못했다. artifact redirect의 `productionresultssa4.blob.core.windows.net` 및
`productionresultssa11.blob.core.windows.net`가 현재 프록시에서 403으로 차단된다.
[원격 원본 metadata](evidence/archive-resume-validation/remote-metadata/)를 보존했고 필요한 호스트를
환경 설정 초안에 추가했다. 저장된 초안과 실행 중 네트워크 적용은 구분한다.

`EpubReaderFlowTest`에 자체 fixture의 search/recreation/font-size/reopen 시점의 저장·현재 Locator,
viewport/글자 크기/문단 client rect/visible paragraph ID 관찰을 추가했다.
기존 `.03` progression 비교, 실제 문단 표시, DB 불변 assertion은 유지한다.
`device-check.sh`가 실패 시에도 `epub-restore-observations.json`을 회수하고 원래 test exit를 보존한다.
`./gradlew --no-daemon --console=plain :androidApp:assembleDebugAndroidTest :androidApp:lintDebug`
exit 0, `bash -n scripts/device-check.sh`, `git diff --check` PASS다.
[컴파일·lint 로그](evidence/archive-resume-validation/observation-compile/compile-and-lint.log)를 보존한다.
새 native 관찰과 API26 원인 확인은 진행 중이다. 제품 오류 해결·전체 native PASS·main 통합은 아직 주장하지 않는다.

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


> 이 파일은 작업 계약이다. 현재 구현 상태가 아니다.  
> 2026-10-01 작성 시점의 모든 작업은 **TODO / 실행 증거 없음**이다.  
> 앱 버전 v1.0, 기준 계획서 문서 버전 v1.1.  
> 권위 있는 기능/안전 계약: `docs/BookReader_Mobile_v1_1_Implementation_Plan.md`.

## 상태 규칙

작업 상태: `TODO / IN_PROGRESS / DONE / BLOCKED`.
검증 결과: `PASS / FAIL / NOT_RUN / BLOCKED_ENV`.
코드가 작성되어도 필수 실행 검증이 안 됐으면 DONE으로 올리지 않는다.
독립 작업으로 넘어가야 할 때는 현재 미완료 이유/의존성/위험을 남기고 기준을 우회하지 않는다.
상태 변경 시 `WORKLOG.md`에 날짜, source SHA 또는 working tree 상태, 명령, exit code, 로그 위치를 기록한다.
이 파일의 체크 여부보다 실제 코드/테스트 증거가 우선한다.

## 전체 상태

| ID | 작업 | 상태 | 증거 |
|---|---|---|---|
| M00 | bootstrap-mobile-foundation | DONE | [WORKLOG](WORKLOG.md) — 실제 build/lint/unit/shared/schema 및 API36 5-case PASS |
| M01 | epub-and-encoding-feasibility | DONE | 안정 Readium 3.1.2 공개 API/실제 EPUB probe 5 및 Android encoding 3 PASS; 제품/출시 gate 별도 |
| M02 | managed-import-and-library | DONE | TXT 내부 알파: `2be610a` local/remote CI 41/45 및 Android 25·host 2·fixture PNG PASS, [WORKLOG](WORKLOG.md) |
| M03 | txt-scroll-progress-and-continue-bar | DONE | TXT 내부 알파: measured anchor/cache loss/Continue/recreation·cold launch/host force-stop PASS, [WORKLOG](WORKLOG.md) |
| M04 | durable-progress-and-race-tests | IN_PROGRESS | epoch/sequence·60초 연속 저장 PASS; 전체 SAFE/UI race·disk-full·reflow 기기 검증 잔여 |
| M05 | txt-pagination-and-reflow | IN_PROGRESS | 1349ce8 local/remote 52/57/3·actual 35/host 2 PASS; 설정 추가 조합 검증 잔여 |
| M06 | txt-search-bookmarks-and-encoding | IN_PROGRESS | f208a06 main 55/67/3·actual 38/host 2 PASS; 50MiB/준비 중 북마크 gate 잔여 |
| M07 | mobile-controls-and-accessibility | IN_PROGRESS | 격리 worktree에서 메뉴/슬라이더/창 설정 구현 및 검증 중 |
| M08 | epub-product-integration | TODO | 없음 |
| M09 | comic-reader-and-memory | IN_PROGRESS | [제품 보고서](M09_COMIC_PRODUCT.md), 최신 native/host 확인 중 |
| M10 | hardening-and-release-evidence | IN_PROGRESS | [QA](QA_MATRIX.md)·[성능](PERFORMANCE.md)·[보안](SECURITY.md)·unsigned 패키지 정적 감사 |
| M11 | release-candidate-audit | BLOCKED | [출시 감사](RELEASE_CANDIDATE_REPORT.md), 미해결/미실행 P0 gate |

## 공통 명령 계약

M00에서 `scripts/doctor.sh`와 `scripts/ci-check.sh`를 **실제로 구현**한다.
환경 검사와 실제 테스트의 명령을 같은 것으로 취급하지 않는다.

```bash
git status --short
git diff --check
./scripts/doctor.sh
./scripts/ci-check.sh
```

M00는 `./gradlew --version`, `./gradlew projects`, 각 모듈 `tasks --all`을 확인하여
사용한 AGP/KMP plugin에 실제 존재하는 shared/Android test task를 선택한다.
`androidApp:assembleDebug`, `androidApp:lintDebug`, Android unit/shared tests를 실행하는 계약을 만든다.
문서에 존재하지 않는 Gradle task를 성공했다고 기록하지 않는다.
instrumentation/process-death/실기기 성능은 별도 명령으로 `TESTING.md`에 설명한다.
새 기능 task가 들어오면 ci-check에 해당 테스트가 포함되는지 확인한다.
`|| true`, `ignoreFailures`, 빈 smoke, 테스트 삭제로 통과를 만들지 않는다.

## M00 환경 준비 출력

`DEPENDENCIES.md`: JDK/Gradle/AGP/Kotlin/Compose/KSP/Room/SQLite/Readium/SDK 정확 버전,
공식 근거, 설치/resolve 방법, 고정 이유와 실제 실행 결과.
`TESTING.md`: 빠른 검사, 실제 DB 테스트, UI/프로세스 테스트, 실기기 검사의 분리.
`WORKLOG.md`: 수행 내용과 실제 명령 결과.
CI는 가능한 자동 검사와 실제 Android 기기/에뮬레이터 검증의 범위를 구분한다.
새 모바일 저장소가 기본이다. PC 저장소 안에서는 `mobile/`에 격리하고 기존 PC 코드를 건드리지 않는다.

# M00 — `bootstrap-mobile-foundation`

**상태:** DONE

**선행 조건:** 없음

## 구현 범위
현재 저장소/상위 AGENTS/미커밋 변경을 확인한다. 안정 툴체인을 공식 호환표와 실제 resolve로 고정한다. androidApp/shared 모듈, 실제 실행 가능한 3탭 앱, Loading/Empty/Error, locator envelope/model, Room 실제 DB 생성·삽입·조회와 schema export, doctor/ci-check/기본 CI를 만든다.

## 제외 범위
TXT 본문/EPUB 전체/Comic/AI/동기화/iosApp/사용하지 않는 모듈. 기존 PC 앱 코드 변경.

## 완료 조건
- [x] 모든 Gradle/plugin/library 버전이 고정되고 wrapper가 생성되어 있다.
- [x] 실제 Android assemble, Android unit test, shared test, lint의 명령/결과가 기록되어 있다.
- [x] Room smoke는 fake repository가 아니라 실제 Room/SQLite 생성·쓰기·읽기를 포함한다.
- [x] Library는 빈 DB와 조회 오류를 구분하고 3탭이 작동한다.
- [x] DEPENDENCIES/TESTING/WORKLOG와 다음 M01 입력이 만들어져 있다.

## 산출물
docs/DEPENDENCIES.md, docs/TESTING.md, docs/WORKLOG.md, ADR-0001/0006, APK/테스트 로그 경로

## 중단/제약 처리
SDK/JDK/네트워크/에뮬레이터가 없으면 가능한 작성·정적 검사는 수행하고, 해당 실행 검증은 BLOCKED_ENV로 남긴다. 환경 부재를 숨기려고 빌드를 생략하는 CI를 만들지 않는다.

**2026-10-01 실행 현황:** 3탭/모델/repository/실제 Room 테스트/고정 버전과 공식 wrapper/스크립트/CI/문서를 작성했다. `doctor`·`ci-check` exit 78, Gradle 정보·task 목록 조회 4명령 exit 1이다. JDK compiler·Android SDK·adb 부재와 Gradle 다운로드 socket 차단으로 실제 task 목록·resolve/build/test/lint/schema를 확인하지 못했다. 필수 실행은 `NOT_RUN`(원인 `BLOCKED_ENV`)이며 APK·테스트 보고서·생성 schema는 없다. 완료 체크는 유지한다. 정확한 명령·로그·M01 gate는 [WORKLOG](WORKLOG.md), [TESTING](TESTING.md), `docs/evidence/build-command-results.json`에 있다. M01은 M00 필수 검증 해소 후 별도 착수한다.


**2026-10-04 최종 실행 현황:** source `3f04b8ba53d23846a454ee744c5e1fc33bea45e3`에서
`ci-check` exit 0, 실제 Android assemble/test APK compile/lint/unit 10 cases/shared 19 cases 및
Room KSP schema export가 PASS다. Lint의 활성 informational upgrade 안내 20건은 보존하며
그 외 warning/error는 fatal이다. 같은 source의 API36 emulator에서 필수 UI/설정/launcher
5 cases도 실제 PASS, failure/error/skipped 0이다. 정확한 명령·원격 run·XML·APK/schema hash는
[TESTING](TESTING.md), [WORKLOG](WORKLOG.md), `evidence/m00-validation/`을 따른다.
10월 1일 BLOCKED_ENV와 수정 전 실패는 이력이다. M01은 TODO이며 다음 별도 작업의 입력만 마련했다.
M00 결과는 Reader/import/이어읽기/process-death/Readium/실기기 성능 PASS가 아니다.


# M01 — `epub-and-encoding-feasibility`

**상태:** DONE

**선행 조건:** M00

**2026-10-04 현재 범위:** 사용자 요청으로 TXT 가져오기와 실제 독서를 우선한다. strict streaming decoder,
UTF BOM/UTF-16/CP949/EUC-KR 미리보기·수동 선택과 자체 작성 fixture를 구현했다.
기존 host 13 cases는 PASS였지만 실제 Android Charset 3 cases 중 CP949/EUC-KR 구분이 실패했다.
Android ICU alias의 확장 허용을 앱의 strict EUC-KR byte grammar로 제한했고 경계 회귀 포함 host 14 cases는 PASS다.
원래 Android assertion을 유지한 수정 뒤 실제 API36 Android 인코딩 3 cases는 모두 PASS다.
Readium/EPUB probe·ADR-0005는 보류하며 이 작업 전체를 DONE으로 올리지 않는다.

**2026-10-17 완료 근거:** 22526d3 API36 suite의 EPUB probe 5/제품 3 및 encoding 3 PASS. ADR-0005의 정확 고정 API·MIME/CSP/foreign resource/Locator/search 결과를 확인했다. M01 feasibility 완료이며 강화된 제품 exact restore 및 출시 승인과 구분한다. 위 10월 4일 보류 문구는 이력이다.

## 구현 범위
epubAndroid 어댑터와 격리된 debug/test probe를 만든다. Readium 안정 Android Navigator로 EPUB2/3/이미지/목차/Locator 저장·재사용/테마·크기 변경을 확인한다. publication script와 외부 리소스 차단 가능성을 실제로 검증한다. Android strict decoder의 UTF BOM/CP949 확장 문자/EUC-KR/BOM 없는 UTF-16 후보와 수동 선택을 검증한다.

## 제외 범위
완성 EPUB UI, 자체 전체 EPUB 렌더러/CFI 엔진, alpha Compose Navigator 채택, DRM.

## 완료 조건
- [x] Readium 고정 버전/라이선스/추가 모듈 필요성/Android host 형태가 ADR에 기록되어 있다.
- [x] 공개 Locator를 페이지 번호로 축약하지 않고 round-trip한다.
- [x] 정상 이미지/CSS/검색·위치 기능과 비신뢰 script/외부 URL 차단의 양립을 확인한다.
- [x] SDK가 제공하지 않는 보안 API를 추정하거나 성공으로 기재하지 않는다.
- [x] CP949 확장 문자 fixture가 실제 기기/Android test 경로에서 통과하거나 명확히 BLOCKED_ENV로 표시된다.

## 산출물
ADR-0005, encoding probe report, test fixtures manifest, EPUB blocker 목록

## 중단/제약 처리
EPUB 검증 실패를 은폐하지 않는다. M02–M07의 독립 작업은 진행할 수 있지만 M08/M11은 해당 blocker 해소가 필요하다.


# M02 — `managed-import-and-library`

**상태:** DONE — TXT 내부 알파 범위

**선행 조건:** M00; M01에서 결정한 포맷/decoder 계약 참고

**2026-10-09 완료 근거:** `2be610a`의 local CI6와 원격 build는 native 41 Android unit/45 shared JVM,
failure/error/skipped 0, lint 20 Hint 및 DB/migration/schema PASS다. 실제 ordinary Android 25(가져오기 13/encoding 3 포함)와
별도 host 2 stages 모두 PASS이며 Library/Reader 자체 fixture PNG 4개를 검증했다. 실제 SAF/VIEW/SEND/manual encoding,
승격 no-overwrite, journal checkpoint/partial bytes recovery, disk/permission/cancel/duplicate/limit·삭제 writer 무효화 회귀가 근거다.
Astra가 실제 identity/source/host proof를 검토하여 좁은 TXT 내부 알파를 승인했다. EPUB/Comic 제품 import 완료 주장은 아니다.
이전 실패는 보존하며 최초 DATABASE_UNAVAILABLE/SQLite BUSY의 내부 원인은 미확인이다.

## 구현 범위
SAF 및 외부 VIEW/SEND의 안전한 수신, 내부 관리 복사본, ImportJob journal, 해시, 포맷/한도 검사, 동일 바이트 중복 방지, 서재/책 정보/삭제/메타데이터 검색을 구현한다. 우선 TXT end-to-end에 연결하되 EPUB/Comic import에도 공통 staging 계약을 사용한다.

## 제외 범위
Reader 엔진 전체, 원본 URI 참조 모드, 원본 삭제, 일괄 import 고도화.

## 완료 조건
- [x] COPYING/VALIDATING/FINALIZING/COMMITTED 단계 fault injection 뒤 멱등 recovery.
- [x] 디스크 부족/권한 만료/취소/중복/미상 크기 URI에서 기존 서재 보존.
- [x] 원본 URI/파일명이 관리 파일 경로로 직접 사용되지 않음.
- [x] 삭제가 진행 중 save/index/decode와 충돌해 책을 재생성하지 않음.
- [x] 조회 실패와 빈 서재 구분; 앱 재실행 후 성공 import만 표시.

## 산출물
ADR-0002, import recovery tests, storage error UX, Library screenshots

## 중단/제약 처리
실제 provider/외부 intent 테스트가 없으면 파일 시스템 unit test만으로 SAF 검증 완료라고 하지 않는다.


# M03 — `txt-scroll-progress-and-continue-bar`

**상태:** DONE — TXT 내부 알파 범위

**선행 조건:** M02; UTF-8/UTF-16 decoder 준비

**2026-10-09 완료 근거:** `2be610a`의 local/remote 41/45 회귀와 actual ordinary Android 25/host 2 stages가 PASS다.
measured canonical 행 저장/cache loss/Continue/Activity 재생성, Ready 이전 기록 보호, 원본 누락 재시도와 EOF/surrogate 경계를
검증했다. 외부 force-stop 후 commit offset 11607/epoch 1/sequence 2를 cold library Continue가 복원했다.
Activity 재생성은 실제 process death와 별도 evidence이며 OEM 회전/실기기 성능은 후속 gate다. Astra가 좁은 TXT 내부 알파를 승인했다.
UTF-16 canonical cache는 [ADR-0003](adr/0003-locator-normalization-and-revision.md)의 내부 선택이다.
전체 TXT 페이지/검색/북마크·M04 SAFE/race/미커밋 flush 완료를 뜻하지 않는다.

## 구현 범위
canonical normalization/streaming prefix/sparse index, bounded scroll renderer, revision-aware progress read/write, Reader 상태 머신, 이어읽기 바를 연결한다. UTF-8 TXT import→본문→저장→재실행→서재→이어읽기까지 실제 경로를 만든다.

## 제외 범위
페이지 모드 전체, FTS, 전체 TXT String 로드, 모든 책별 타이포, UI polish.

## 완료 조건
- [x] TXT-01/03/04의 기본 canonical/경계 테스트.
- [x] Ready 전에 progress 쓰기가 발생하지 않음.
- [x] 단순 읽기 시도/실패가 lastRead/readOrder를 바꾸지 않음.
- [x] Cold launch는 서재; 따뜻한 Reader 복귀와 회전은 같은 위치. (Activity 재생성 기반 검증; OEM 실회전은 후속)
- [x] 캐시 삭제 뒤 원본과 기록이 유지되고 재구축 경로가 존재.

## 산출물
ADR-0003, first vertical-slice UI test, locator fixtures, internal-alpha 사용법

## 중단/제약 처리
5MiB/50MiB 성능은 측정값과 미측정 목표를 구분한다. 전체 TXT가 완성됐다고 아직 선언하지 않는다.


# M04 — `durable-progress-and-race-tests`

**상태:** IN_PROGRESS

**선행 조건:** M03

**2026-10-09 현재 범위:** persisted sessionEpoch/sequence·DB 조건부 commit·sampling/settled save 기반과
외부 force-stop 뒤 마지막 commit 복원은 PASS다. 전체 SAFE matrix, A/B rapid reopen, 60초 연속 이동,
REFLOWING 미커밋 flush 및 실기기 지연 수치는 미완료여서 M04 IN_PROGRESS를 유지한다.

**2026-10-11 후속 범위:** READY에서 수락한 미커밋 anchor를 REFLOWING 중 flush/close할 수 있게 수정했다.
임시 0·비사용자 callback 차단은 유지하며 새 회귀 4개를 필수 test manifest에 등록했다.
수정 전 4개 중 3개 실패로 결함을 재현했다. 새 실행 결과는 TESTING의 M04 절과
`docs/evidence/m04-reflow-validation/`에 기록한다. 이번 controller 단위 검사는 실제 ON_STOP/설정 변경 UI,
전체 SAFE-12 및 전체 M04 승인 근거로 확대하지 않는다. M04는 IN_PROGRESS다.


**추가 검증:** 실제 파일 DB의 지연 A→B/이전 epoch/역방향 sequence/duplicate sequence/삭제 후 save
5개 및 Main publisher-thread 회귀 1개를 추가했다. 로컬 full CI는 Android unit 46/shared JVM 50 PASS다.
`16e4615` ordinary Android 25는 PASS이나 host prepare는 worker UI 갱신으로 FAIL이었다.
UI 게시 Main 전환 후 `96c15aa` remote build(46/50)·actual ordinary 25/host 2 stages는 PASS다.
raw PID 종료와 마지막 commit 복원까지 확인했다. 범위별 [SAFE matrix](M04_SAFETY_MATRIX.md)의 잔여 gate 때문에 IN_PROGRESS를 유지한다.

**SAFE-09 후속:** 60초 가상 시간 controller sampling 검사와 실제 Reader 60초 gesture/Room observation 검사를 추가했다.
`78e45f5` local/remote unit 47/shared JVM 50 PASS이나 actual Android 26개 중 신규 1개 FAIL이다.
index/offset 값 캡처 수정 `14ce759`에서 actual ordinary 26 및 60초(60회 commit) PASS다.
host 준비 timeout 원본을 보존하고 fling/layout 안정화 뒤 expected를 캡처하도록 보완했다.
최종 `73276fe` local/remote 47/50, actual ordinary 26/host 2 stages PASS이며 SAFE-09 60,001ms/46회 commit을 확인했다.
[측정 보고서](M04_COMMIT_LATENCY.md)는 관찰 간격과 SQL write-ack/실기기 성능을 구분한다.

**SAFE-07 후속:** native SQLite page-limit FULL(13)의 update/insert/activation rollback·재개방·재시도
3개 실제 DB 회귀와 Reader 오류 UI/자동 재시도 회귀를 추가했다. `bf7589d` local/remote 47/53,
actual ordinary 27/host 2 stages PASS다. [범위 보고서](M04_SQLITE_FULL.md)를 따르며
OS filesystem ENOSPC/WAL·journal 부족 검증을 완료했다고 확대하지 않는다. M04는 IN_PROGRESS다.

**실제 ENOSPC 및 activation 보완:** `ebee772` local/remote 단위 48/53·전용 ENOSPC 3개,
actual ordinary 27/host 2 stages PASS다. 8MiB tmpfs에서 실제 errno 28/WAL FULL(13)의 record/file 보존과
공간 확보 후 reopen/retry를 확인했다. `a3ee33b` device REJECTED_STALE 실패 조사에서 중복 activation을
단위 테스트로 재현해 진입 직렬화/LAZY job 등록으로 수정했다. 원본 실패와 범위는
[TESTING](TESTING.md), [보고서](M04_FILESYSTEM_ENOSPC.md)를 따른다. M04는 IN_PROGRESS다.

**2026-10-17 SAFE-01 후속:** `0e2605e`의 actual Reader native SQLite 조회 오류/malformed locator
두 회귀가 오류·실패 retry 중 전체 기록/파일 보존 및 복구 후 measured restore/new epoch save를 검증했다.
local/remote 48/53, remote ENOSPC 3, actual ordinary 29/host 2 stages PASS다.
[범위 보고서](M04_PROGRESS_READ_ERRORS.md), [TESTING](TESTING.md)을 따른다.
bookmark entity/DAO/UI 부재로 해당 불변 gate는 NOT_RUN이며 전체 M04는 IN_PROGRESS다.

## 구현 범위
앱 수준 progress single writer, persisted sessionEpoch/sequence, 1초 sampling+250ms settled save, conditional transaction, flush barrier, 저장 실패 UI를 완성한다. 외부 host-driven 종료 테스트를 만든다.

## 제외 범위
offset 최대값 기반 최신 판정, lifecycle 단독 저장, 복구 실패 시 0% 덮어쓰기.

## 완료 조건
- [ ] SAFE-01/02/03/04/06/07/09/10/12 통과.
- [x] A/B 책과 같은 책의 빠른 닫기/재열기, 역방향 이동에서 의도한 최신 기록 유지. (`85653ad` actual API36, [보고서](M04_UI_RACES.md))
- [x] 60초 연속 스크롤에도 주기 commit이 존재. (`73276fe` actual API36: 60,001ms/46회, SQL latency·실기기 성능과 구분)
- [x] force-stop 뒤 마지막 commit 완료 locator 보존; 미커밋 상태까지 보존된다고 주장하지 않음.
- [x] 손상 locator/조회 오류는 기록 보존과 재시도 UI로 처리. (`0e2605e` actual UI 2 cases; bookmark는 별도 미구현/NOT_RUN)

## 산출물
ADR-0004, scripts/process-death-test.*, commit-latency report, race test results

## 중단/제약 처리
실제 process kill 없이 ActivityScenario.recreate만 통과한 것은 이 작업 전체 PASS가 아니다.


# M05 — `txt-pagination-and-reflow`

**상태:** IN_PROGRESS

**선행 조건:** M04 (실제 UI race 핵심 검증 PASS; 설정 reflow는 이 작업에서, bookmark 불변은 M06에서 보완하며 전체 M04는 IN_PROGRESS 유지)

## 구현 범위
같은 실제 text measurement를 사용하는 bounded 페이지 렌더링, 지역 page boundary cache, 타이포 설정/시스템 배율/창 크기 reflow, 페이지·스크롤 상호 전환을 구현한다.

## 제외 범위
평균 글자 폭 pagination, 전체 페이지 View 생성, 페이지 번호 영구 저장.

## 완료 조건
- [x] 각 page range가 연속 [start,end)이며 긴 문단/빈 줄/이모지의 내용 coverage 유지. (`1349ce8` 실제 TextMeasurer/hash)
- [x] 131072/1MiB 단일 문단 끝까지 접근 가능. (`1349ce8` 실제 coverage)
- [ ] font/size/line/letter/margin/insets/density/systemFontScale 변경마다 적절한 cache invalidation.
- [x] 100회 앞뒤 이동과 random jump 후에도 내용 중복/누락/드리프트 없음. (`f208a06` 실제 TextMeasurer 20지역×5회)
- [x] 설정 변경 후 기존 anchor가 같은 문장 또는 동일 페이지 범위 안에 존재. (`1349ce8` mode/size actual UI; 나머지 조합은 위 gate 유지)

## 산출물
layout-key spec, coverage/property tests, reflow UI screenshots

## 중단/제약 처리
정확한 총 페이지를 아직 모르면 숫자를 만들지 않고 %/계산 중을 표시한다.


# M06 — `txt-search-bookmarks-and-encoding`

**상태:** IN_PROGRESS
**선행 조건:** M05

## 구현 범위
literal 본문 검색, chunk overlap/결과 token/offset mapping, 북마크, 미완성 index 상태, 수동 인코딩/미리보기/해석 revision 보존, 검색 후 일회성 돌아가기를 완성한다.

## 제외 범위
정규식/형태소/서재 전체 본문 검색, 자동 문장 fuzzy relocation, 사용자 글꼴.

## 완료 조건
- [ ] TXT-05/06/09/10/12와 검색/북마크 integration 통과.
- [x] fragment 경계 query도 같은 canonical offset으로 이동.
- [ ] 전체 페이지 수 없음/인덱싱 중에도 유효 locator 북마크 저장 가능.
- [x] 인코딩 변경 시 이전 해석의 기록을 삭제하거나 새 offset으로 오적용하지 않음.
- [x] 오래된 검색 job 결과가 새 query/다른 책에 나타나지 않음.

## 산출물
encoding UX screenshots, search/bookmark tests, 50MiB TXT report

## 중단/제약 처리
인코딩 모호성을 정확히 해결했다는 근거가 없으면 자동 감지 성공이라고 주장하지 않는다.


# M07 — `mobile-controls-and-accessibility`

**상태:** IN_PROGRESS
**선행 조건:** M06

## 구현 범위
Reader overlay/탭/스와이프/preview slider/전체화면/Back, 창 밝기/keepScreenOn/회전 요청, 터치 우선순위, TalkBack와 시스템 배율을 제품 수준으로 정리한다.

## 제외 범위
볼륨키/사용자 탭 preset/모든 book override/통계.

## 완료 조건
- [ ] 슬라이더 취소/preview가 영구 진행도를 바꾸지 않음.
- [ ] 링크/선택/드래그/단순 탭이 이중 실행되지 않음.
- [ ] Reader 종료 후 밝기/keepScreenOn/화면 요청이 복원됨.
- [ ] 노치/시스템바/IME/Back 제스처/회전/창 resize에서 anchor 보존.
- [ ] TalkBack 실제 버튼과 본문 순서, 1.0/1.3/2.0 글자 배율 확인.

## 산출물
device-control/UI tests, accessibility checklist, API behavior notes

## 중단/제약 처리
큰 화면에서 OS가 회전 요청을 무시하는 경우를 앱 오류와 구분하고 레이아웃은 정상 유지한다.


# M08 — `epub-product-integration`

**상태:** IN_PROGRESS
**선행 조건:** M07 및 M01 EPUB gate 해소

**2026-10-17:** main 22526d3 제품 EPUB 통합. 실제 API36 52/기존 TXT host 2 PASS. EPUB 자체 외부 host와 추가 hardening은 후속 브랜치에서 검증 중이며 실기기/출시 완료가 아니다. [보고서](M08_EPUB_PRODUCT.md).

## 구현 범위
검증된 Readium adapter를 import/library/ReaderSession/progress/bookmark/search/TOC/설정/Back에 연결한다. 보안 resource 경로와 내부 링크 돌아오기를 제품 경로에서 재검증한다.

## 제외 범위
고급 FXL/DRM/interactive EPUB/새 alpha renderer/모든 CSS 호환.

## 완료 조건
- [ ] EPUB2/3/한글/이미지/중첩 목차/긴 chapter/깨진 resource 회귀.
- [ ] 페이지/스크롤·글자·테마 변경 및 프로세스 재시작의 Locator 보존.
- [ ] exact restore와 fallback 결과 별도 기록.
- [ ] 책 script/외부 resource/cross-book access가 최종 host에서도 차단됨.
- [ ] 악성/지원 불가 EPUB가 앱 startup crash loop를 일으키지 않음.

## 산출물
EPUB acceptance report, security trace, fixed-version SDK API notes

## 중단/제약 처리
M01의 테스트만 통과하고 실제 제품 WebView/host 경로를 검증하지 않았으면 완료 아님.


# M09 — `comic-reader-and-memory`

**상태:** IN_PROGRESS
**선행 조건:** M07, M02 import/security 계약

## 구현 범위
검증 ZIP manifest/natural sort, 정적 이미지 decode, 단일/연속/웹툰 preset, LTR/RTL/fit/zoom, entryKey+normalized anchor, 북마크/진행도를 연결한다.

## 제외 범위
RAR/CBR/재귀 압축/모든 bitmap full decode/OCR/영구 zoom 복원.

## 완료 조건
- [ ] Comic §35 전체 유형과 손상 이미지 한 장 건너뛰기.
- [ ] entryKey identity가 locale/RTL/fit/정렬 hint 변화에 안정.
- [ ] 긴 PNG/WebP·빠른 100장 이동에서 메모리 예산 준수 또는 명확한 안전 fallback.
- [ ] pinch/double tap/pan/page gesture 충돌 없음.
- [ ] ZIP traversal/중복 경로/선언 크기 조작/암호화/한도 초과 방어.

## 산출물
comic acceptance matrix, memory/PSS report, sort/entry manifest spec

## 중단/제약 처리
현재 한 장만 decode한다는 이유로 OOM 테스트를 생략하지 않는다.


# M10 — `hardening-and-release-evidence`

**상태:** IN_PROGRESS
**선행 조건:** M08 및 M09

## 구현 범위
DB migration/실패 복구, privacy/backup exclusion, ABI/16KB, 실기기 성능/메모리/접근성, dependency licenses/최종 manifest/오프라인 독서를 검증한다. 모든 P0의 증거를 통합한다.

## 제외 범위
앱 백업/클라우드/AI/새 편의 기능을 끼워넣기.

## 완료 조건
- [ ] SAFE-05/08/11과 전체 SAFE 회귀; 실제 migration schema test.
- [ ] 지원 Android 매트릭스와 16KB native 최종 패키지 점검.
- [ ] 최종 manifest 권한, cloud/D2D 규칙, 삭제/데이터 손실 안내가 구현과 일치.
- [ ] §25 수치의 실측 PASS/FAIL/NOT_RUN/BLOCKED_ENV와 기기/빌드/fixture 정보.
- [ ] 책 내용/개인 URI가 자동 업로드/공개 CI 로그에 포함되지 않음.

## 산출물
QA_MATRIX.md, PERFORMANCE.md, SECURITY.md, dependency/license report

## 중단/제약 처리
환경 부재 항목은 다음 테스트 담당자가 재현 가능한 명령과 조건을 남긴다. 출시 gate는 통과하지 않는다.


# M11 — `release-candidate-audit`

**상태:** BLOCKED
**선행 조건:** M10 및 모든 P0 gate 통과

## 구현 범위
전체 v1 지원 범위/DoD/blocker를 읽기 전용으로 먼저 감사한다. blocking 수정은 별도 작은 변경으로 검증한 뒤 패키징/README/알려진 제한을 정리한다. 사용자 승인 없이 원격 배포하지 않는다.

## 제외 범위
기능 추가/무단 main merge/스토어 제출/서명키 업로드.

## 완료 조건
- [ ] 원문에서 유지한 TXT/EPUB/ZIP의 필수 사용자 경로가 모두 존재.
- [ ] 미해결 P0 0, 필수 검증의 NOT_RUN/BLOCKED_ENV 0.
- [ ] 최종 APK/AAB와 테스트한 소스 SHA/의존성/설정이 일치.
- [ ] 알려진 제한/데이터 삭제/오프라인/인코딩/지원 포맷 안내가 정직함.
- [ ] 출시 판정과 코드 리뷰 판정, debug 빌드와 release 빌드 결과가 구분됨.

## 산출물
RELEASE_CANDIDATE_REPORT.md, signed/unsigned 산출물 구분, 최종 검사표

## 중단/제약 처리
실제 서명/스토어 계정 접근은 별도 권한과 사용자 승인 필요. 준비 문서와 실제 배포를 혼동하지 않는다.
