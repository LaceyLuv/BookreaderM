# M00 작업 기록

## 키보드 전환과 본문 viewport 분리 (2026-10-23)

`9f99b2c` build 39944478518 PASS. Device 39944478507: API36 native 57 + host 6 PASS,
API26/34는 각각 native 57 중 1 FAIL이며 host NOT_RUN이다.
[두 번째 원본](evidence/reader-design/ci-9f99b2c/summary.json)을 보존한다.
API26은 검색창을 닫은 뒤 EPUB host가 재복원되어 문단 확인을 마치지 못했고,
API34는 TXT 검색 입력 당시 REFLOWING이어서 검색 시작이 보류된 채 재개되지 않았다.

Compose 1.9.3 공식 소스에서 safeDrawing = systemBars.union(ime).union(displayCutout)를 확인했다.
Reader 뒤쪽 본문은 IME를 제외한 시스템 바/컷아웃 여백만 쓰고, Activity의 soft input은
독서 중 ADJUST_NOTHING으로 설정한 뒤 종료 시 원래 값을 복원한다. 검색 Dialog 자체의
키보드 처리는 유지한다. TXT는 재배치 중 받은 최신 검색어를 READY 이후 시작하며,
기존 경계 검색 native에 REFLOWING 중 실제 SetText를 전달하는 회귀를 추가했다.
Android 8/9 탐색 바에는 명시적 남색 배경을 주어 흰 시스템 버튼 대비를 확보하고 종료 시 복원한다.

재배치 숫자 원본은 API34/36에서 progression 0.915608 → 약 0.95043,
scrollHeight 8099 → 13786/13789, 같은 p179 전체 표시와 원본 locator 불변을 확인했다.
이 수치는 앞 절의 레이아웃별 geometry 비교가 필요한 이유를 뒷받침한다.
설정 캡처 직전 Compose frame도 진행시켜 화면 이름과 실제 dialog 표시를 일치시킨다.
다음 소스의 전체 native/host 결과는 아직 검증 중이다.


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


## 2026-10-17 M06 검증

`f208a06` main에서 local/remote unit 55/shared 67, remote ENOSPC 3, API36 ordinary 38/host 2 PASS다.
검색·북마크·해석 전환 및 M05 추가 설정/OS 배율·밀도 회귀를 실행했다.
[보고서](M06_READER_TOOLS.md), [summary](evidence/m06-validation/summary.json)가 최신 근거다.
M07은 `/workspace/bookreader-mobile-m07`에서 구현·검증 중이다. 남은 전체 계획 진행 요청은 계속 유효하다.
이전 절의 M06 미반영/미실행 안내보다 이 절을 우선한다. 전체 M04–M06와 출시 gate를 자동 완료로 올리지 않는다.


## 2026-10-01 — M00 BLOCKED

대상은 `/workspace/bookreader-mobile` 독립 모바일 프로젝트다. 초기 `/workspace/.git`은 비어 있는
읽기 전용 경로여서 유효한 Git 저장소·브랜치·source SHA가 없었으며, 기존 PC 코드나 미커밋 파일은 없었다.
상위·현재 AGENTS.md도 없었다. 초기 조사 명령/결과는 이 작업 대화에 남아 있다.
제공 ZIP의 9개 원본 파일을 모바일 경로에 보존하고 필수 template/개정 계획서/TASKS를 읽었다.
`AGENTS.template.md`, 개정 계획서, 원문 참고 계획서 및 다른 제공 문서는 덮어쓰지 않는다.
`TASKS.md`에서는 M00 상태와 그 증거만 갱신한다.

진행 중 재검사 로그: `docs/evidence/verification/handoff-environment.txt`.
2026-10-01T01:05:47Z에 `/workspace`와 모바일 루트에서 각각
`git rev-parse --show-toplevel`, `git branch --show-current`, `git status --short`는 exit `128`이었다.
현재 구조와 `/AGENTS.md`, `/workspace/AGENTS.md`, 모바일 `AGENTS.md` 부재도 그 로그에 기록했다.
이는 그 시점의 재검사이며 최초 조사나 이후 Git 초기화 결과를 대신하지 않는다.

독립 Git 저장소는 브랜치 `m00-bootstrap-mobile-foundation`에 초기화했다.
commit/source SHA는 없고 추가한 파일은 untracked working tree다. 사용자 파일을 stage/commit/reset하지 않았다.
M00 실제 코드·테스트는 작성했으나 필수 실행 검증이 **BLOCKED_ENV**여서 작업 상태는 **BLOCKED**다.
미실행 검증을 PASS나 DONE으로 기록하지 않는다.

이번 범위는 Android Compose 3탭, KMP 모델/locator/repository, 실제 Room KMP DB와 테스트,
고정 툴체인/wrapper/환경·CI 스크립트 및 인계 문서다.
import·Reader·이어읽기·EPUB/Comic 제품 기능은 구현 범위 밖이다.

## 구현 파일과 사용자 경로

- `androidApp/src/main/.../MainActivity.kt`, `ui/BookReaderApp.kt`, `ui/AppViewModel.kt`, `ui/AndroidStores.kt`:
  새 프로세스의 서재 → 실제 DB Loading/Empty/Content/Error → 오류 재시도,
  검색/설정 탭 전환, 불러온 메타데이터 문자 그대로 검색, SYSTEM/LIGHT/DARK 테마 저장, Back→서재.
  DB query handle은 각 load의 코루틴이 소유하고 `finally`로 닫는다. 데모 책이나 가짜 읽기 버튼을 넣지 않았다.
- `shared/src/commonMain/.../{model,locator,repository,database}`:
  Book/ReadingProgress, version/contentRevision 포맷별 locator, 오류를 빈 상태와 구분하는 repository,
  Room v1 books/reading_progress 테이블과 FK·중복 방지·ABORT 삽입.
- `shared/src/{androidMain,jvmMain}/.../database`:
  Android의 private durable database와 JVM file-backed bundled SQLite builder.
  JVM target은 테스트 실행용이며 Desktop 제품 앱을 추가하지 않는다.
- `shared/src/{commonTest,jvmTest}`, `androidApp/src/{test,androidTest}`:
  locator/model/실제 DB·데이터 보존 및 ViewModel/검색/Compose/테마/Activity 테스트를 작성했다.
  실제 실행 상태는 아래 증거와 분리한다.
- `androidApp/src/main/AndroidManifest.xml`, `res/xml/{backup_rules,data_extraction_rules}.xml`:
  추가 permission 없음, cloud/D2D backup 제외 선언. XML 검사는 OS/OEM에서의 차단 증거가 아니다.
- 루트 Gradle 설정·wrapper·버전 catalog, `scripts/doctor.sh`, `scripts/ci-check.sh`, `.github/workflows/`와
  `AGENTS.md`, README, DEPENDENCIES/TESTING/WORKLOG/ADR-0001/0006을 기반 산출물로 마련한다.

원본 파일 byte 비교에서 `TASKS.md` 끝 빈 줄 제거를 발견하여 원본 M01 이후 bytes를 복원했다.
첫 검사 `FAIL`(exit 1): `docs/evidence/verification/source-integrity-initial.txt`.
복원 후 `PASS`(exit 0): `docs/evidence/verification/source-integrity.txt`.
최종 비교는 첨부 8개 원문 파일의 byte/hash 동일성과 TASKS M01 이후 byte 보존을 확인한다.

## 고정 의존성과 결정

Gradle 8.13, AGP 8.11.1, Kotlin/Compose compiler 2.2.20, KSP 2.2.20-2.0.3,
Room 2.8.3, SQLite 2.6.1, Compose BOM 2025.10.00를 정확히 고정했다.
JDK 17, minSdk 26, compile/targetSdk 36, build-tools 35.0.0을 선택했다.
이 조합의 실제 dependency resolve/build 성공은 확인되지 않았다.
공식 자료 확인 범위와 안정 Room 2 계열 선택 이유는 [DEPENDENCIES](DEPENDENCIES.md)/ADR-0006을 따른다.
공식 Gradle 8.13 wrapper 원본을 공급했고 JAR/bin SHA-256을 고정했다.

## 실행 결과

명령은 별도 표시가 없으면 `/workspace/bookreader-mobile`에서 실행했다.
현재 로그의 stack trace는 Gradle 자체 다운로드 차단이며 애플리케이션 test failure가 아니다.

| 명령 | exit | 판정 | 로그 |
|---|---:|---|---|
| `./scripts/doctor.sh` | 78 | BLOCKED_ENV — Java 21 JRE만 있고 javac/Android SDK 없음 | `docs/evidence/doctor.log` |
| `./scripts/ci-check.sh` | 78 | BLOCKED_ENV — doctor 단계에서 중단 | `docs/evidence/ci-check.log` |
| `./gradlew --version` | 1 | BLOCKED_ENV — 배포 다운로드 socket Operation not permitted | `docs/evidence/gradle-version.log` |
| `JAVA_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080' ./gradlew --version` | 1 | BLOCKED_ENV — proxy 지정 후에도 socket Operation not permitted | `docs/evidence/gradle-version-proxy.log` |
| `./gradlew --no-daemon --console=plain projects` | 1 | BLOCKED_ENV — 같은 다운로드 차단 | `docs/evidence/projects.log` |
| `./gradlew --no-daemon --console=plain :androidApp:tasks --all` | 1 | BLOCKED_ENV — 실제 task 목록 미확인 | `docs/evidence/android-tasks.log` |
| `./gradlew --no-daemon --console=plain :shared:tasks --all` | 1 | BLOCKED_ENV — 실제 shared task 목록 미확인 | `docs/evidence/shared-tasks.log` |
| Android XML/manifest 정적 검사(전체 명령은 로그 안에 보존) | 0 | PASS — XML 파싱, permission·backup 제외 선언만 검사 | `docs/evidence/verification/android-static-check.txt` |
| `bash -n scripts/doctor.sh scripts/ci-check.sh` | 0 | PASS — Bash 문법만 검사 | `docs/evidence/shell-syntax.log` |
| `sh -n gradlew` | 0 | PASS — 공식 wrapper script 문법만 검사 | `docs/evidence/wrapper-syntax.log` |
| `python3 docs/evidence/ci-helper-harness.py` | 0 | PASS — 성공·exit7·exit78·로그 실패 4개 코드 전달 및 catalog 파싱 | `docs/evidence/ci-helper-harness.log` |
| `python3 build/verification/source-integrity.py` | 0 | PASS — 원본 8파일 및 TASKS M01 이후 byte 보존 | `docs/evidence/verification/source-integrity.txt` |
| `python3 build/verification/final-static-check.py` | 0 | PASS — 작성한 텍스트 소스/설정/문서 51개 whitespace 검사, Markdown hard break 허용 | `docs/evidence/verification/final-static-check.json` |
| `git diff --check` | 0 | PASS — tracked diff만 검사; 새 untracked 코드 검증을 대신하지 않음 | root read-only 검사; 별도 whitespace 검사 필요 |

실제 명령 배열·exit는 `docs/evidence/build-command-results.json`에도 보존했다.
초기 `ci-check` 로그는 doctor exit 78을 `FAIL doctor`로 표기했고 `docs/evidence/ci-check-initial.log`에 보존했다.
logger를 보완한 실제 재실행도 exit 78이며 `docs/evidence/ci-check.log`에 `BLOCKED_ENV doctor`로 기록한다.
command 실패와 tee 로그 실패를 모두 전달하며 환경 차단을 성공으로 무시하지 않는다.
초기 일반 network curl의 exit 7과 추가 network permission 시도의 tool `aborted by user` 결과는 대화에 남아 있다.
성공한 network build 실행이나 명시적 사용자 권한 거부·자동 승인 검토 거절로 해석하지 않는다.

| 실행 검증 | 상태 | 이유 |
|---|---|---|
| dependency resolve/Android assemble/lint/unit/shared tests | NOT_RUN | BLOCKED_ENV: Gradle 다운로드·JDK compiler·Android SDK 필요 |
| 실제 Room/SQLite smoke | NOT_RUN | BLOCKED_ENV: 작성한 JVM integration test를 실행하지 못함 |
| Room schema export | NOT_RUN | BLOCKED_ENV: KSP 미실행; schema 파일을 손으로 만들지 않음 |
| Android instrumentation/UI·prefs·launcher | NOT_RUN | BLOCKED_ENV: APK compile 및 adb/device 필요 |
| host-driven process-death, Reader 위치 복원/이어읽기 | NOT_RUN | M03/M04의 import/Reader/writer 경로가 아직 없음 |
| Readium 호환성/EPUB 복원·보안, Android CP949 | NOT_RUN | M01 범위 |
| 실기기 performance/TalkBack/font scales/OEM backup/ABI·16KB/migration upgrade | NOT_RUN | 기기 및 후속 작업 증거 필요 |
| 원격 GitHub workflow | NOT_RUN | push·원격 실행 요청 없음 |

현재 APK, unit/instrumentation test report, 실제 DB 파일 및 Room schema export는 **없다**.
환경 복구 후 생성할 경로와 명령은 [TESTING](TESTING.md)에 따로 기록했다.
로그 존재를 APK/테스트 성공 증거로 바꾸지 않는다.

## 해소해야 할 위험

고정 버전 조합의 dependency/API/task 실제 호환성을 아직 확인하지 못해 compile/lint 결함이 남을 수 있다.
JVM DB smoke 테스트는 실제 Room과 bundled SQLite를 쓰도록 작성했지만 실행 전에는 데이터 보존 PASS가 아니다.
Android manifest 백업 제외는 선언 검사만 통과했고 OS/OEM cloud/D2D 동작은 미검증이다.
새 Android UI의 실제 설치/탭/오류·테마/접근성도 instrumentation과 기기 확인이 필요하다.
EPUB Locator payload round-trip 모델은 SDK 위치 복원이나 publication 보안 증거를 대신하지 않는다.

최종 Astra 읽기 전용 검토에서 추가로 확인된 소스 결함은 없었다.
이는 build/런타임/테스트 PASS가 아니며 M00 **BLOCKED** 판정과 미실행 검증을 유지한다.
CI action은 공식 tag의 확인된 commit SHA로 고정했고 근거는 `docs/evidence/ci-action-pins.json`에 있다.

인계 ZIP은 `/workspace/bookreader-mobile-M00.zip`이며 SHA-256 sidecar는 같은 이름에 `.sha256`을 붙인다.
소스·원문 문서·공식 wrapper·실제 로그를 포함하고 `.git`/Gradle·Kotlin cache/일반 build cache/개인 키를 제외한다.
`build/verification`과 `build/ci-evidence`의 실제 검사 로그만 의도적으로 포함한다. APK 없는 소스 인계다.

## M01 착수 조건

1. M00 실제 dependency resolve와 Android assemble/lint/unit/shared/Room smoke가 통과하고 schema export가 확인되어야 한다.
   JDK 17·Android SDK 36/build-tools 35.0.0·Gradle/Maven 접근을 준비한 뒤 실제 task 목록을 먼저 확인한다.
   기기/에뮬레이터에서 서재 첫 화면·3탭·상태 구분·테마를 실제 실행하여 사용자 경로도 확인한다.
   환경으로 막힌 필수 검증은 먼저 해소하며 M00를 DONE으로 바꾸지 않는다.
2. 새 작업을 명시적으로 선택한 뒤 `epubAndroid`와 격리된 debug/test probe만 추가한다.
   M00에서 미리 사용하지 않는 모듈이나 Readium UI를 생성하지 않는다.
3. 안정 Readium Android Navigator의 정확 버전·라이선스·SDK/compiler 요구와 실제 API를 확인하여 ADR-0005에 기록한다.
   계획서의 초기 후보는 Readium 3.4.0이다. M00 제품 dependency에는 Readium을 넣지 않았으며,
   M01 probe에서 그 후보의 실제 요구·해석·호환성을 확인하고 최종 pin을 결정한다.
4. 재배포 가능한 EPUB2/3·이미지/CSS/TOC/locator 및 악성 script/외부 URL fixture,
   CP949 확장 문자·EUC-KR·UTF BOM·BOM 없는 UTF-16 fixture와 Android 실행 환경을 준비한다.
5. SDK locator 전체 round-trip·프로세스 재시작/테마/글자 크기 변경 뒤 위치 복원,
   trusted engine 동작과 publication script/외부 요청 차단의 양립을 각각 증거로 남긴다.
   exact restore와 fallback을 구분하고, 존재하지 않는 보안 API를 추정하지 않는다.

M01 자체 blocker는 M08/M11의 EPUB gate를 막는다. M01 보안/복원 증거 없이 EPUB 지원 완료를 선언하지 않는다.

## 2026-10-01 — GitHub BookreaderM 게시 준비

사용자가 GitHub에 `BookreaderM` 새 저장소를 생성하고 현재 모바일 구현을 commit/push하도록 명시적으로 요청했다.
기존 `m00-bootstrap-mobile-foundation` 브랜치와 사용자 Git 작성자 설정을 유지하여 M00 기반 구현의 첫 commit을 준비한다.
위의 untracked/commit 없음/원격 실행 요청 없음 기록은 M00 구현 당시의 상태이며 이 후속 요청 이전 이력이다.
원격 저장소 생성과 push 성공은 이 준비 기록에서 주장하지 않는다.

Git에서 제외되는 `build/verification`과 `build/ci-evidence`의 기존 작은 비공개정보 없는 결과 로그를
`docs/evidence/verification/`과 `docs/evidence/ci-evidence/`에 복사해 commit에 포함한다.
위 실행표의 명령은 실행 당시 원문을 보존하며, 결과 로그 경로만 commit에 포함되는 복사본으로 연결한다.
인계 ZIP의 기존 `build/` 로그 위치는 당시 산출물 이력이다. 이후 `ci-check`의 신규 로그 출력은 계속 `build/ci-evidence/`다.
애플리케이션 코드와 테스트를 변경하거나 검증을 재실행하지 않았으며, M00의 `BLOCKED` 상태와 M01 선행 조건을 유지한다.

게시 준비 검사: staged 파일 84개에 대한 비밀키/토큰 패턴 및 비공개 파일명 검사 결과 0건(`PASS`, exit 0).
`git diff --cached --check`는 exit 2였다. 보존된 원문·공식 문서 및 ADR-0006의 Markdown hard break,
원문 TASKS와 공식 wrapper의 끝 빈 줄, 기존 Gradle 설정의 끝 빈 줄이 보고되었다.
원문 보존과 이번 게시 범위에 따라 이 공백은 수정하지 않았으며 해당 검사를 `PASS`로 표시하지 않는다.

## 2026-10-01 — 사용자 생성 GitHub 저장소 이력 병합

사용자가 생성한 `https://github.com/LaceyLuv/BookreaderM`의 `main` 초기 commit
`29688de167a37e8ab0ce53ab321988526693cd05`를 fetch 후 확인했다. 원격에는 `README.md`만 있었고
`# BookreaderM` 제목과 `북리더 모바일` 설명이었다. 로컬 M00 첫 commit
`df31f2c21ed30799acdbf310cc8bfbdb2c39ecca`와 이 원격 이력을 `--allow-unrelated-histories`로 병합한다.
README add/add 충돌은 원격 제목·설명을 맨 위에 보존하고 로컬 M00 본문 전체를 이어 붙여 해결했다.
M00 소스·테스트·설정·원문 문서를 변경하지 않았으며 원격 초기 commit 이력을 보존한다.
게시할 기본 브랜치에 맞추어 로컬 임시 브랜치 이름을 `main`으로 바꾸고 일반 push를 준비한다.
이 기록 시점에는 push 성공이나 GitHub Actions 통과를 주장하지 않는다. M00 `BLOCKED` 판정은 유지한다.

## 2026-10-03 — M00 필수 실행 검증 재개

기준 source는 `0a3802e`이며 작업 디렉터리는 `/workspace/bookreader-mobile`이다.
10월 1일의 차단/원격 게시 준비 기록은 당시 이력으로 보존한다.
이번 작업도 M00에 한정하며 원문 계획서·template·reference와 TASKS M01 이후 bytes를 보존한다.

JDK 17·Android SDK 36/build-tools 35.0.0을 설치한 뒤 실제 doctor·Gradle version/projects 및
Android/shared task discovery가 exit 0으로 통과했다. 명령별 로그와 결과는
`docs/evidence/m00-validation/ci/`에 있다. catalog/plugin/library pin을 변경하지 않았다.
KSP plugin 조회의 일시 실패는 공식 Maven POM·HTTP 및 repository 재조회로 진단했으며
버전 부재로 판정하지 않았다. 전체 build/test/schema 및 resolved graph 결과는 확인 중이다.

10월 1일 원격 기본 CI run `36805008404`의 실제 로그를 10월 3일 재확인해 SDK 설정 성공과 debug APK packaging을 확인했다.
이후 `:androidApp:compileDebugAndroidTestKotlin`은
`BookReaderScreenTest.kt:4:33 Unresolved reference assertDoesNotExist`로 실패했다.
공식 Compose API에서 이 assertion은 `SemanticsNodeInteraction` member임을 확인하고
잘못된 extension import 한 줄을 제거했다. 테스트 case/assertion 삭제·skip 없이 재검증한다.
원격 실패 excerpt는 `docs/evidence/m00-validation/ci-36805008404-failure-excerpt.log`에 보존했다.
수정 소스·기기 workflow·doctor/SDK pin은 `4abc4bb94fc843fcc843bfeffcdc43164704d7df`로 게시됐다.
`gh --log-failed`의 Azure 403/빈 결과는 성공 증거로 취급하지 않고 connector의 실제 로그를 확인했다.

기기 검증은 `scripts/device-check.sh`와 별도 GitHub KVM API36 x86_64 workflow로 실행한다.
신규 XML에서 5개 필수 case의 실행과 failure/error/skipped 부재를 확인해야 UI `PASS`다.
현재 로컬 기기 환경 차단은 `docs/evidence/device-local-blocker/results.txt`에 기록한다.
전체 자동 검사와 실제 instrumentation 결과 확인 전까지 M00 `DONE`을 주장하지 않는다.
M01 Readium/encoding probe나 M02 제품 구현에는 착수하지 않았다.

### 첫 로컬 전체 실행 — FAIL 보존

첫 전체 실행은 아래 명령으로 exit 1이었다. 세션용 환경 파일은 저장소 밖 설치 경로다.

```bash
source /workspace/toolchains/mobile-env.sh
BOOKREADER_EVIDENCE_DIR=docs/evidence/m00-validation/ci ./scripts/ci-check.sh \
  > docs/evidence/m00-validation/ci-check.log 2>&1
```

Android unit XML은 10 cases,
shared XML은 18 cases 중 1 failure를 기록했다. 실제 실패는
`RoomDatabaseSmokeTest.unavailableDatabaseReadIsErrorAndPreservesDiskRecord`의 닫힌 DB 조회에서
`JobCancellationException`이 전달된 것이다. 실패 XML과 명령 로그는
`docs/evidence/m00-validation/attempt-1-shared-failure/`에 보존했다.
Debug 앱·instrumentation APK compile과 KSP schema export는 실행됐지만 lint 완료 보고서는 없었다.
실패 원인 수정 후 전체 검사를 재실행하며 첫 실패를 PASS로 덮어쓰지 않는다.

닫힌 DB 실패는 Room의 자체 query scope 취소와 호출자 코루틴 취소를 구분하여 수정한다.
repository의 세 CancellationException 처리에서 호출자 `currentCoroutineContext().ensureActive()`를
먼저 검사하므로 실제 호출자 취소는 전파되고, 호출자가 살아 있으면 `DATABASE_UNAVAILABLE` 오류다.
기존 닫힌 DB·reopen·기록 보존 assertion을 유지하며 실제 DB의 호출자 취소 회귀 case를 추가했다.
추가 case를 포함한 최종 shared 실행 건수는 재실행 XML에서 확인한다.

### Lint 실행 — FAIL 보존

이후 실제 `:androidApp:lintDebug`는 exit 1, 23 errors였다.
20건은 고정 의존성의 새 버전 알림(`AndroidGradlePluginVersion` 3건,
`GradleDependency` 9건, `NewerVersionAvailable` 8건)이고,
3건은 `ObsoleteSdkInt`·`MonochromeLauncherIcon`·`UseKtx` 소스/리소스 진단이다.
실제 실패 로그·HTML/XML은 `docs/evidence/m00-validation/attempt-1-lint-failure/`에 보존한다.
소스 진단을 수정하고 고정 버전 알림의 처리 범위를 검토한 뒤 lint 전체를 재실행한다.

### 소스 수정 후 shared 회귀 — PASS

`./gradlew --no-daemon --console=plain :shared:jvmTest` 재실행은 exit 0이었다.
`docs/evidence/m00-validation/shared-regression.log`와 실제 XML에서 Room DB 12,
locator 6, book model 1, 합계 19 cases의 failure/error/skipped가 모두 0임을 확인했다.
최초 18 cases는 유지했고 실제 호출자 cancellation 회귀 1개를 추가했다.
전체 ci-check/lint와 기기 실행의 최종 결과는 별도로 확인한다.

소스 lint 진단은 minSdk26에 불필요한 launcher icon `-v26` qualifier 제거 및 monochrome 추가로 처리한다.
`AndroidStores.write`의 직접 SharedPreferences `commit()`은 저장 실패 Boolean을 확인해야 하므로 유지한다.
Unit을 반환하는 KTX edit로 바꾸어 실패 신호를 잃지 않도록 이 함수에만 `UseKtx`를 억제하며
다른 lint 경고의 기본 실패 정책은 유지한다. 이 선택은 저장 성공을 가장하지 않는 계약에 따른다.

### 고정 버전 알림의 lint 정책

읽기 전용 Astra 검토 후 `AndroidGradlePluginVersion`, `GradleDependency`,
`NewerVersionAvailable` 세 upgrade-only 진단만 활성화된 `informational`로 분류한다.
선택한 stable pin은 공식 호환표와 실제 graph로 검증하며 최신 버전 알림만으로 자동 변경하지 않는다.
알림을 삭제·disable하지 않고 최종 보고서의 실제 건수와 내용을 남긴다.
그 외 lint warning/error는 기존 fatal 정책을 유지한다. 따라서 최종 exit 0도 '발견 사항 0건'을 의미하지 않는다.

수정 전 source `4abc4bb94fc843fcc843bfeffcdc43164704d7df`의 기본 GitHub CI run
`37163324747`도 실제 connector 로그에서 같은 shared 닫힌 DB case의 실패를 확인했다.
이 실행은 shared 18 cases 중 1 failure이며 로컬 수정 후 19 cases 결과와 구분한다.
같은 source의 device run `37163324713`은 진행 중이며 실제 XML/로그 확인 전 PASS로 기록하지 않는다.

로컬 `./scripts/device-check.sh`를 adb 설치 후 재실행했다.
source/doctor/adb-version/attached-devices 명령은 exit 0이었지만 authorized device 목록은 비어 있어
최종 exit 78 (`BLOCKED_ENV`)이었다. `docs/evidence/device-local-blocker/resumed/`의 실제 로그를 따른다.
로컬 `/dev/kvm` 부재와 별개로 원격 device workflow의 SDK/KVM 준비 성공을 확인했으며 실제 UI 결과는 대기한다.

## 2026-10-04 — 최종 로컬 전체 검증 PASS

이 절은 `bd1ba033` 실행 당시의 이력이며 아래 로그·보고서·summary는
`docs/evidence/m00-validation/attempt-2-local-pass-remote-failure/`에 분리 보존했다.
현재 `3f04b8ba` 최종 결과와 구분한다.

전체 ci-check는 source `bd1ba0332e292029bd09677bb0db9ddbe29dfd43`에서 위와 같은
`BOOKREADER_EVIDENCE_DIR` 명령으로 exit 0이었다.
`docs/evidence/m00-validation/attempt-2-local-pass-remote-failure/ci/results.txt`는 doctor/version/projects/두 모듈 task discovery와
build-and-tests 모두 `PASS`를 기록한다. APK assemble/test APK compile·lint·Android unit/shared tests·schema 검사가 포함된다.

`docs/evidence/m00-validation/attempt-2-local-pass-remote-failure/reports/`의 실제 XML을 확인했다. Android unit은 10 cases, shared는 19 cases
(Room DB 12/locator 6/book model 1)이며 failure/error/skipped 모두 0이다.
Shared 최종 task는 수정 후 실제 19 cases가 실행된 isolated regression 결과를 up-to-date로 재사용했고
Android unit은 최종 전체 실행에서 다시 수행됐다. 로그의 up-to-date 표기와 실제 XML을 함께 남긴다.
Lint는 error/warning 0, 활성 informational upgrade 안내 20건(3/9/8)이다.
이는 finding 0건이나 의존성 최신 버전 채택을 의미하지 않는다.

Room KSP가 생성한 `shared/schemas/org.bookreader.mobile.database.BookReaderDatabase/1.json`은
version1, books/reading_progress 두 테이블 및 FK를 포함하며 committed schema와 동일하다.
Debug 앱 APK는 17,085,394 bytes, Android test APK는 1,081,831 bytes다.
정확 SHA-256/경로·schema hash는 `docs/evidence/m00-validation/attempt-2-local-pass-remote-failure/final-validation-summary.json`에 있다.
APK와 unit/lint HTML은 generated build 경로이며 Git에는 작은 XML/summary/로그 증거와 schema를 보존한다.

최신 source의 원격 build run `37163680043`, device run `37163680032`는 확인 중이다.
이전 source `4abc4bb`의 device run `37163324713`에서 5 cases PASS가 관찰됐지만
최신 source의 승인 증거로 대체하지 않는다. 최신 5-case 결과 전 M00 DONE은 보류한다.

### 다음 작업 M01의 현재 입력과 범위

M01 상태는 `TODO`이며 구현에 착수하지 않았다. 다음 명시적 M01 작업의 입력은 다음과 같다.

1. 검증 source `3f04b8ba`의 androidApp/shared 경계와 정확 pin 툴체인,
   M00 CI·19 shared/10 Android unit·최종 5 instrumentation 결과 및 generated Room v1 schema.
   최신 기기/원격 CI gate가 통과한 M00를 완료 상태로 전달한다.
2. Readium stable Android Navigator 후보 3.4.0의 실제 release·라이선스·SDK/compiler 요구/API 확인.
   M00 graph에는 Readium이 없으므로 이 후보가 현재 조합에서 검증됐다고 가정하지 않는다.
3. 필요한 `epubAndroid` adapter와 격리 debug/test probe만 추가하고 ADR-0005에 exact pin과 host 형태를 기록한다.
   Readium payload는 common domain에 노출하지 않으며 locator 전체 round-trip/재사용·복원을 검사한다.
4. 재배포 가능한 EPUB2/3·이미지/CSS/목차/locator 및 publication script/외부 URL fixture.
   trusted engine script는 동작하면서 비신뢰 publication script와 외부 요청을 차단할 수 있는지 실제 관찰한다.
   exact restore와 chapter fallback을 구분하고 없는 보안 API를 만들지 않는다.
5. Android strict decoder의 UTF BOM/CP949 확장/EUC-KR/BOM 없는 UTF-16 fixture와 수동 선택 probe.
   실제 Android 실행 결과를 남기며 JVM charset 지원만으로 대상 Android PASS를 주장하지 않는다.

M00의 cold-launch/3탭/상태/테마 case는 Reader/import/이어읽기나 host-driven process-death 증거가 아니다.
그 제품 경로와 지속 writer/종료 검증은 M02–M04에서 별도로 구현한다.

### 동일 source의 원격 결과 — UI PASS / 기본 CI FAIL

Source `bd1ba033`의 실제 device run `37163680032`는 success, 필수 UI/설정/launcher 5 cases PASS다.
반면 기본 CI run `37163680043`은 shared 19 cases 중
`callerCancellationIsNotConvertedToDatabaseError` 1 failure를 기록했다.
로컬에서 통과한 신규 호출자 cancellation 회귀가 원격 스케줄링에서 실패한 환경 차이를 조사한다.
실제 실패를 숨기거나 회귀 test를 약화하지 않고 호출자 cancellation 전달 경로를 수정한다.
기존 `final-validation-summary.json`의 로컬 PASS는 해당 실행의 유효한 결과이며
원격 실패를 대체하지 않는다. 수정 후 새 source에서 전체 CI와 기기 UI를 모두 다시 검증한다.
최종 M00 DONE은 이 회귀 해소 전 보류한다.

호출자 cancellation의 원격 실패는 Room DAO의 synchronous fast path가 사전에 취소된 호출자에게
suspend 없이 값을 반환할 수 있어 catch 경로에 진입하지 않은 데서 발생했다.
두 public repository read 함수의 첫 문장에 `currentCoroutineContext().ensureActive()`를 추가하여
DAO 호출 전에 사전 취소를 전파한다. 기존 catch·닫힌 DB/reopen/data 보존·호출자 취소 assertion은 유지한다.
수정 결과는 새 source의 실제 19-case/전체 CI와 기기 실행으로 확인한다.

### 호출자 entry guard 수정 후 새 source 로컬 PASS

Source `3f04b8ba53d23846a454ee744c5e1fc33bea45e3`에서 전체 ci-check를 같은 명령으로 다시 실행해
exit 0을 확인했다. 이번 shared JVM task는 실제 새로 실행됐고 Android unit도 재실행됐다.
최종 XML은 shared19/Android10 cases, failure/error/skipped 모두 0이다.
Lint는 20 informational Hint, warning/error 0이며 generated schema는 committed v1과 동일하다.
현재 `final-validation-summary.json`/`final-reports/`는 이 source의 새 결과이고
앞선 bd1ba 로컬 PASS·원격 FAIL 이력은 `attempt-2-local-pass-remote-failure/`에 분리 보존한다.
새 Debug APK SHA-256은 `c9188f03cb9f242af9b1a05a1b0ac284091b4d63872114aa420afec8ee36e737`이다.
새 source의 remote build `37164061596`와 device `37164061580` 결과는 확인 중이다.

같은 source `3f04b8ba`의 원격 기본 CI
[37164061596](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061596)도
2026-10-04 00:13:57 UTC에 success로 완료됐다. 로컬과 원격의 전체 실행 성공을 각각 확인했다.
실제 원격 report/log와 artifact metadata는 `docs/evidence/m00-validation/build-37164061596/`에 보존한다.
Device `37164061580`의 최종 필수 XML 검증만 대기한다.

최종 원격 build artifact `11288751988`의 다운로드 digest와 실제 29-case XML을 확인했다.
로컬과 동일하게 Android10/shared19, failure/error/skipped 0 및 lint 20 Hint이고 exported schema도 byte 동일하다.
실제 CI Debug APK는 16,629,841 bytes, `build/deliverables/m00-3f04b8b/androidApp-debug.apk`,
SHA-256 `716739c3471b4a21d689396e30b2f850b0bd393abc7ed2681506ec91a8edee82`다.
[artifact 링크](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061596/artifacts/11288751988)와
`build-37164061596/artifact-inspection.json`에 원격 bytes/hash를 보존했다.
로컬 APK `c9188f03...`와 원격 APK hash를 분리 기록하고 동일 바이트 빌드 재현성은 검증 완료하지 않는다.

## 2026-10-04 — M00 DONE 및 인계

Source `3f04b8ba53d23846a454ee744c5e1fc33bea45e3`의 실제 API36 Google APIs x86_64 device run
[37164061580](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061580)이 성공했다.
Downloaded actual instrumentation XML에서 필수 5 cases 모두 실행됐고 failure/error/skipped가 없음을 확인했다.
기기 fingerprint/API/ABI·원격 run/job/artifact metadata·실제 XML·필수 명령 결과와 발췌/hash는
`docs/evidence/m00-validation/device-37164061580/` 및 `verified-summary.json`에 보존한다.
원본 전체 job log는 Git에서 제외된 `build/device-ci-artifacts/37164061580/job-111323264829.log`에
바이트 그대로 보존하며 이 파일의 SHA-256을 `verified-summary.json`에 기록한다.
원격 전체 로그는 위 GitHub run의 job 화면에서도 확인할 수 있다.
3탭·검색·테마, Loading/Empty/Error와 retry, 실제 SharedPreferences 정상/손상 설정,
서재 cold launch와 Activity recreation의 최소 사용자 경로를 검사했다.

로컬 fullci exit0·Android unit10/shared19·실제 DB/schema·lint 및 같은 source의 기기5cases가 충족되어
TASKS의 M00만 `DONE`과 완료 체크로 갱신했다. 20 informational 버전 안내는 보고서에 남아 있다.
최신 소스의 원격 기본 CI는 [37164061596](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061596)이며
최종 원격 결과도 success이며 로컬 결과와 실제 원격 XML/log를 각각 보존한다.
문서/evidence 후속 commit은 검증한 앱/테스트/build source를 바꾸지 않는다.
첨부 원문/template/reference와 TASKS M01 이후 bytes는 보존했다.
M01은 TODO이며 위 입력을 사용할 다음 별도 작업이다.
Host-driven process-death·Reader 위치 복원/이어읽기·Readium 보안/호환성·Android CP949·실기기 성능·
OEM backup·migration upgrade·native ABI/16KB는 이번 M00 결과로 검증 완료하지 않는다.

## 2026-10-04 — TXT 가져오기·실제 독서 우선 구현 착수

사용자가 TXT 추가와 실제 읽기를 우선했으므로 M01의 인코딩 부분, M02 관리 import와 M03 TXT 스크롤/이어읽기를
진행한다. Readium/EPUB는 보류하며 기존 M00 원격 증거와 계획서/첨부 원문을 유지한다.
Baseline commit은 `c5d04b2301d08bc25c791f91e68a802a4309212b`이고 현재 변경은 미커밋 working tree다.
검증 기록은 `docs/evidence/m02-m03-validation/`에 분리한다. 이 변경을 원격 commit/push하지 않았다.

구현 범위는 Room v1→v2 additive migration과 ImportJob journal/managed copy/recovery/중복·삭제,
Android SAF 및 단일 content URI VIEW/SEND 수신·확인, strict streaming decoder와 수동 인코딩 미리보기,
canonical UTF-16LE 파생 cache/유한 text window/실제 행 측정 스크롤 Reader, 조건부 progress writer와 하단 이어읽기다.
핵심 변경 경로는 shared의 `database/importing/repository`, Android의 `importing/encoding/reader/ui` 및
MainActivity/manifest다. 세부 선택은 ADR-0002/0003/0004에 기록했다.

TXT normalizationVersion 1은 첫 BOM만 제거하고 CRLF/CR을 LF로 바꾸며 공백/NFC를 바꾸지 않는다.
영구 revision은 source SHA-256/명시한 encodingId/normalizationVersion의 안정 SHA-256이다.
cache의 fixed-width UTF-16LE 형식은 계획 §18.3의 UTF-8 cache 시작안과 다른 되돌릴 수 있는 내부 선택이다.
locator 좌표는 UTF-16 Long으로 유지하며 cache 소실은 원본/기록 소실이 아니다.
prefix는 조기 표시할 수 있으나 strict 전체 decode/원본·cache integrity와 복원 성공 전에는 writer를 활성화하지 않는다.

격리 encoding 구현은 Kotlin 2.2.20 cached compiler와 실제 JUnit 4.13.2로 검사했다.
정확한 실행 명령은 `python3 docs/evidence/m02-m03-validation/encoding-isolated/run.py`이며 exit 0/PASS다.
내부 compiler/JUnit의 exact argv와 exit code, source SHA-256은 같은 디렉터리 `results.json`에 기록했다.
`compile.log` exit 0, `junit.log`는 13 cases/failure 0/exit 0이다. UTF BOM/short read/CRLF·surrogate 경계,
CP949 확장 vs EUC-KR, 늦은 malformed/suffix preview, byte limit/cancel/time budget을 검사했다.
이는 encoding-only host 검사이며 Android 실제 Charset 또는 전체 앱 빌드 PASS로 확대하지 않는다.

자체 작성 `fixtures/encoding/` 11 files의 actual byte size/SHA-256은 manifest와 일치해 PASS다.
CP949 fixture는 U+AC02/`81 41`이며 Android `AndroidTxtEncodingTest`에 동일 바이트를 사용했다.
`bookreader-demo-ko-utf8.txt`는 21,544 bytes, CC0-1.0, SHA-256
`d64f9bff6cd4e789631ce5df2f3882a0bcd86db5f616fe4513b4704ef173cc68`이다. 수동 import/여러 화면 읽기용이며 앱에 자동 삽입하지 않는다.

현재 M01/M02/M03/M04는 IN_PROGRESS다. M01 전체 EPUB gate와 M04 전체 SAFE/60초 이동/경쟁/종료·지연 matrix는
이 기반 구현으로 완료하지 않는다. 새 전체 assemble/lint/unit/shared DB/schema, Android provider/intent/Reader·charset,
외부 force-stop/재실행은 실행 결과 대기(NOT_RUN)다. 실기기 5MiB/50MiB p95·메모리/OEM/접근성은 NOT_RUN이다.
테스트 결과가 들어오면 실제 source identity·exact command·exit·XML case count를 별도 항목으로 추가한다.

### 통합 중간 Android unit 실행 PASS — 최종 CI와 구분

`./gradlew --no-daemon --console=plain :androidApp:testDebugUnitTest`는 exit 0이다.
actual XML은 31 cases/failure 0/error 0/skipped 0이며 M00 10, decoder 13, cache/controller 8 cases다.
`docs/evidence/m02-m03-validation/android-unit-attempt-1-summary.json` 및 `android-unit-attempt-1.log`에 기록했다.
이후 reader 소스/테스트 수정이 계속되어 이 31-case 결과를 최종 working tree 검증으로 취급하지 않는다.
최종 full CI와 Android 기기/외부 프로세스 종료 결과를 따로 실행·확인한다.

실제 Room KSP 중간 compile에서 schema v2 export를 생성했다. `initial-schema-export.json`은 v1/v2
identity/hash와 table 목록을 기록한다. 이는 DB upgrade 실행 검증이나 전체 앱 compile 성공을 대신하지 않는다.

`./gradlew --no-daemon --console=plain :shared:jvmTest` 중간 실행도 exit 0이다.
실제 XML은 40 cases/failure 0/error 0/skipped 0이며 locator/model, 기존 DB 보존, import/recovery,
schema migration, progress writer를 포함한다. `shared-tests-attempt-1-summary.json`과 해당 log에 기록했다.
이후 guard 회귀를 추가하므로 이 count는 최종 CI count가 아니다.

### 전체 CI 중간 시도와 고정 source 최종 실행 대기

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-1 ./scripts/ci-check.sh`는
exit 1/FAIL이다. 실제 Android unit 36/shared 43 cases의 failure/error/skipped는 0이지만 lint의
`UsableSpace`, `UseKtx` 2 errors가 전체 검증을 실패시켰다. 20 Hint는 고정 dependency update 안내다.
로그/XML/lint/hash는 `local-ci-attempt-1/summary.json`, `reports/`, console log에 보존했다.
`UseKtx`는 실제 `String.toUri` API로 수정했다. `UsableSpace`는 conservative free-space preflight의 작은 helper에만
`@SuppressLint("UsableSpace")`를 적용했다. 이 검사에는 지금 이용 가능한 free bytes만 사용하고 clearable cache를 포함한
StorageManager allocatable capacity, reclaim/allocation은 사용하지 않는 정책적 이유가 있다. 모든 lint를 끄거나
ignoreFailures로 실패를 숨긴 변경이 아니다. suppression의 범위와 이유는 해당 helper에 주석으로 남겼다.

같은 evidence dir 형식의 `local-ci-attempt-2` 전체 실행은 exit 0이다. Android unit 37/shared 43 cases,
failure/error/skipped 0, lint 20 Hint/error·warning 0, 앱/test APK와 schema v2 검사가 PASS다.
하지만 실행 중 `TxtCanonicalCache.kt`, `TxtReaderControllerTest.kt`가 SHA corruption 회귀 수정으로 변경되었다.
`local-ci-attempt-2/summary.json`에 source drift 2 files를 기록했으므로 최종 source 검증으로 사용하지 않는다.

변경을 고정한 `BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-3 ./scripts/ci-check.sh`
최종 실행을 기다린다. 시작 source hash는 `local-ci-attempt-3-source-hashes.json`에 있으며 실제 완료/변경 없는지를
확인한 뒤 최종 로컬 결과를 기록한다. 원격 ordinary Android 23 cases 및 host 2 stages는 아직 NOT_RUN이다.
host XML은 실제 adb instrumentation 결과/PID/force-stop proof를 검증한 helper 결과이고 ordinary AGP XML과 구분한다.
새 debug APK의 서명 인증서는 과거 local/CI signer가 다른 사실과 함께 검증하며 기존 앱 삭제/데이터 지우기로
서명 충돌을 해결하지 않는다. 최종 artifact hash·signer·source identity는 결과 확인 후 추가한다.

### 고정 source 최종 로컬 CI PASS — 기기/외부 종료 대기

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-3 ./scripts/ci-check.sh`는
최종 exit 0/PASS다. 실제 Android unit 37/shared 43 cases의 failure/error/skipped는 모두 0이다.
lint는 informational Hint 20건만 남았고 warning/error는 0이다. 앱/test APK compile·assemble 및
Room v1 보존/v2 export·실제 migration/DB 회귀를 확인했다. 모든 단계의 exact command/exit/log와
native XML은 `local-ci-attempt-3/results.txt`, 각 log/`reports/`, `summary.json`에 보존했다.

시작/종료 source/config/fixture hash drift는 0이며 `local-ci-attempt-3-source-hashes.json` SHA-256은
`13a7a332f8f01f2f6b4ba1d32c4cbe74e765262f5d8ef98b84c4f266d3365f4d`다.
로컬 debug 앱 APK SHA-256은 `c35e01f01d08b44747c389d40665f799bd1f15ff70bff899f3cee945ed155720`,
test APK는 `8fcb30f2b3e1ec225458c7d8a9f43361f06a40aae6cac2527ed911ea68d37f54`다.
이 APK hash는 로컬 artifact의 identity이며 이후 원격 APK와 동일 바이트라고 주장하지 않는다.
실제 Android ordinary 23 cases/host-driven 2 stages와 CP949 대상 기기 지원은 아직 NOT_RUN이다.
M02/M03을 이 로컬 PASS만으로 DONE으로 올리지 않는다.

검증한 production/test/build source는 `fdd9ac15182af4e4c1a21a814c5d9c2aa3393808`로 commit/push되어
원격 ordinary Android/host 검증을 기다린다. 이 이후 documentation/evidence 정리는 검증한 앱 source를 바꾸지 않는다.
세션 제공 로컬 APK는 `/workspace/bookreader-mobile-0.2.0-debug.apk`이며 위 `c35e01f0...` hash를 사용한다.
로컬 signer는 이전 local M00과 같고 과거 CI signer와 다르다. 기존 CI 설치본에 대한 데이터 유지 업데이트를
보장하지 않으며 앱 삭제/데이터 지우기 대안을 제시하지 않는다. GitHub release는 요청·생성하지 않았다.
`local-ci-attempt-3/final-local-apk.json`의 실제 aapt/apksigner 명령은 각각 exit 0이다. versionName `0.2.0`, versionCode 2,
17,023,395 bytes, signer SHA-256 `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 확인했다.
같은 source의 원격 build `37167947509`, Android device `37167947545` 실행이 진행 중이다. 아직 성공으로 기록하지 않는다.

### 리뷰에서 명시한 취소·flush 한계

provider stall/cancel 검사는 앱이 소유한 pipe/read와 CancellationSignal/stream close 경로를 검증한다.
비협조적인 원격 Binder/provider 호출이 반드시 앱의 시간 한도 안에 중단된다는 주장은 하지 않는다.
또한 Reader REFLOWING 중 close/ON_STOP은 마지막 commit을 보호하지만 reflow 직전 미커밋 이동까지 flush하지 못할 수 있다.
M04의 flush barrier 잔여 사항이며, 현재 host 종료 검증도 마지막 acknowledged commit 보존을 검사한다.

### 동일 source 원격 build PASS — device/host 대기

Source `fdd9ac15182af4e4c1a21a814c5d9c2aa3393808`의 원격 build
[37167947509](https://github.com/LaceyLuv/BookreaderM/actions/runs/37167947509)는 success다.
job `111334760116`, artifact `11290761635`의 실제 native XML·CI log를 다운로드하여 Android unit 37/shared 43,
failure/error/skipped 모두 0을 확인했다. lint는 20 Hint이며 schema 1/2 export는 local과 byte-for-byte 일치한다.
`remote-fdd9ac1-build/workflow.json`, `summary.json`, 각 verified log와 reports/actual CI log에 증거를 기록했다.
원격 APK의 byte hash/인증서를 local과 별도로 검증했으며 원격 원본 artifact는 Git 외부에 보존한다.
actual Android ordinary 23 cases 및 host 2 stages는 아직 결과 대기이며 원격 build 성공으로 대신하지 않는다.

### 기기 검증 lifecycle option 교정 — 앱/APK 변경 없음

AGP 8.11.1 공식 source에서 connected 테스트 후 앱/test APK 유지 option을 확인하여 검증 스크립트에
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`를 적용했다.
근거는 `agp-test-lifecycle-api.json`이며 script-only commit `d8e4ec9`로 기록·게시했다.
이 수정은 다음 host 검사에 필요한 설치 상태를 유지하는 검증 lifecycle 교정이다.
검증한 production/test source 및 제공 로컬 APK를 바꾸지 않는다. 기존 `fdd9ac1` device 실행은 아직 진행 중이며
그 실행의 host 실패를 관찰했다고 기록하지 않는다. 실제 실행 결과는 별도 확인 후 추가한다.

### 최초 실제 Android 실행 FAIL — ICU EUC-KR 차이 수정

이후 원래 source `fdd9ac1`의 device run `37167947545`가 실제 FAIL로 끝났다. 다운로드한 native connected XML은
23 cases/14 failures/0 errors/0 skipped다. 실제 failure/log와 workflow identity는 `remote-fdd9ac1-device/`에 보존했다.
`AndroidTxtEncodingTest.androidCp949ExtensionFixtureIsDistinctFromEucKr`는 Android ICU의 EUC-KR alias가 CP949의
U+AC02=`81 41` 바이트를 받아들여 `EUC_KR must reject CP949 extension bytes` assertion이 실패했다.
host JDK 13-case PASS를 Android 지원 판정으로 확대하지 않은 이유가 실제로 확인되었다.
다른 provider/picker fixture failures는 해당 owner가 별도 조사·수정한다. host 2 stages는 아직 NOT_RUN이다.

`TxtDecoder`의 EUC-KR 경로에서 vendor alias decode 전에 표준 ASCII 또는 양쪽 A1–FE byte pair grammar를
검사한다. prefix/read 사이 lead carry, 완료 EOF의 dangling lead, malformed pair의 source byte offset을 보존한다.
자동 선택 순서, encodingId, normalizationVersion, 원본 관리 바이트를 바꾸지 않았다. 기존 Android 3 assertions를 유지하고
unit `eucKrGrammarCarriesPairedBytesAndRejectsCp949ExtensionRangesAtExactOffsets` 1개를 추가했다.

현재 source의 isolated compile/JUnit은 `encoding-icu-fix-isolated/run.py` exit 0, 14 cases/failure 0다.
처음 새 실행에 기존 isolated output dir를 재사용했으므로 새 14-case proof를 위 새 디렉터리에 분리했다.
이전 `encoding-isolated/`는 immutable Git `fdd9ac1` source를 실제 재실행한 13-case 역사 proof로 명시했다.
새 rerun을 원래 실행 log처럼 제시하지 않으며 최초 전체 native CI 13-case XML과 첫 기기 FAIL artifact는 그대로 보존한다.
fixture 실제 bytes/hash 11개는 변하지 않았다. 전체 38 Android unit/43 shared 및 원래 Android 23 cases/host 2 stages의
수정 source 통합 재검증은 결과 대기다. 테스트를 약화하여 EUC-KR/CP949 overlap을 허용하지 않는다.

### 수정 source `9eb8f78` 최종 로컬 CI PASS — 원격 재검증 대기

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-4 ./scripts/ci-check.sh`는
actual exit 0/PASS다. Android unit 38(encoding 14)/shared 43 cases의 failure/error/skipped는 모두 0이다.
lint는 20 Hint이고 warning/error는 0이며 앱/test APK, Java instrumentation compile 및 schema 검사가 PASS다.
103 source/config/fixture 파일의 drift는 0이다. 실제 log/XML 및 summary는 `local-ci-attempt-4/`에 기록했다.
source fingerprint SHA-256은 `04cf3d59249644f9b3a44ac8dbcfd2d1a1feb76f49edc3f05ba045c6f6081e19`다.

strict EUC-KR byte grammar, provider fixture Java 변환 및 DocumentsUI 선택 경로를 수정한 source는
`9eb8f782d996fea9c0f04006e39fa72d9a3979ff`로 commit/push되어 실제 Android 재검증을 기다린다.
제공 로컬 APK `/workspace/bookreader-mobile-0.2.0-debug.apk`는 새 파일로 교체했고 SHA-256은
`2129f9c94914c1385f4cc5cbc900f51a851e4cffa8c8460a4f92d17bc0a32d10`이다.
test APK는 `f2f5d53b053c92d45ae9d69618b68f2cc8da2286a7a000d28e48aef537447ab1`이다.
최신 `final-local-apk.json`은 이 `9eb8f78` 후보의 size/version/hash/동일 local signer를 기록한다.
과거 `fdd9ac1` APK와 최초 device 실패는 앞선 각 source의 기록으로 남긴다. 새 실제 Android 23 cases/host 2 stages는 아직 NOT_RUN이다.

### 수정 source 첫 device worker 환경 차단

`9eb8f78`의 첫 원격 device worker는 KVM read/write permission 문제로 tests 시작 전에 exit 1이다.
이 결과는 BLOCKED_ENV이며 Android test assertions가 실패한 결과로 취급하지 않는다.
같은 commit을 새 worker에서 다시 실행하며 checks/테스트/환경 사전 조건을 약화하지 않는다.
실제 ordinary 23 cases/host 2 stages는 아직 NOT_RUN이다. 이전 `d8e4ec9`는 script-only 교정이라 앱 source가
옛 decoder/provider fixture와 동일하며 그 실패의 native archive도 별도로 보존한다.

후속 문서 commit에 `fixtures/encoding/README.md` 설명이 포함되면 `fixtures/**` CI trigger가 다시 실행될 수 있다.
이 파일의 prose 변경은 실제 fixture bytes/runtime/test/build source를 바꾸지 않는다. 최종 source/evidence 비교는
앱 source 동등성과 각 실행 commit을 구분하여 기록한다.

### 두 번째 KVM 차단과 workflow synchronization 교정

같은 `9eb8f78` source의 두 번째 device worker도 KVM permission 사전 검사에서 BLOCKED_ENV다.
두 worker 모두 실제 Android tests/host stages를 실행하지 않았다. source regression 실패와 구분한다.
공식 bounded `udevadm settle` 동작을 확인하여 workflow-only commit
`f3d4c38e2bbe2bb50c98d5da4e5dfaaca0376c26`로 queue synchronization을 추가했다.
근거는 `udev-synchronization-api.json`이며 KVM read/write checks를 유지한다. queue synchronization이 원인이라는
판정이나 문제가 해결되었다는 주장은 새 실제 실행 결과를 확인하기 전까지 하지 않는다.
앱/test source 및 로컬 제공 APK는 `9eb8f78`과 동일하다. workflow 변경 후 새 기기 실행을 기다린다.

### 수정 source 원격 build PASS / 새 KVM setup PASS

Source `9eb8f782d996fea9c0f04006e39fa72d9a3979ff`의 원격 build
[37169215834](https://github.com/LaceyLuv/BookreaderM/actions/runs/37169215834)는 success다.
artifact `11290199967`(15,121,863 bytes)의 native XML/CI log를 다운로드하여 Android unit 38(encoding 14)/shared 43,
failure/error/skipped 모두 0을 확인했다. lint 20 Hint, schema 1/2의 local byte 일치도 PASS다.
actual evidence는 `remote-9eb8f78-build/summary.json`, `workflow.json`, native reports와 verified log에 기록한다.

Workflow-only `f3d4c38`의 새 device run `37169506738`는 실제 KVM setup read/write 검사에 PASS하여
Android instrumentation에 진입했다. 이는 environment preflight PASS이며 ordinary 23 cases/host 2 stages의
app correctness PASS가 아니다. 실제 테스트 종료/원본 artifact 검증을 기다린다. 앞선 환경 차단·기기 FAIL은 유지한다.

`d8e4ec9`의 실제 native device artifact도 `remote-d8e4ec9-device/summary.json`에 추가 보존했다.
이 script-only source는 기존 앱/fixture를 유지하며 observed ordinary 23 cases/14 failures/0 errors/0 skipped다.
ordinary gate에서 실패하여 host stages는 NOT_RUN이다. 이 과거 실행을 최신 수정 source의 결과로 취급하지 않는다.

### 실제 Android 인코딩 PASS / 관리 파일 승격·fixture 실패 교정

`f3d4c38` device run `37169506738`의 다운로드한 native XML은 ordinary 23 cases/11 failures/0 errors/0 skipped다.
`AndroidTxtEncodingTest`의 기존 3 assertions는 모두 PASS이며 Android ICU alias 앞의 strict EUC-KR grammar가
CP949 확장 바이트를 거부함을 실제 Android에서 확인했다. native ICU alias 자체가 거부한다고 주장하지 않는다.
실패 stack, focused logcat, workflow identity는 `remote-f3d4c38-device/summary.json`과 원본 reports에 보존했다.
ordinary gate 실패로 host 2 stages는 NOT_RUN이며 fixture screenshot은 수집하지 않았다.

관리 파일 승격의 `Os.link`는 실제 Android SELinux AVC denial 9건으로 실패했다. 공개
`Files.move(source, target)`의 기본 no-options 경로로 교정했다. process-wide managed mutex, private UUID 경로,
NOFOLLOW_LINKS regular source, 기존 target 부재, source/target parent 동일 `st_dev`를 확인하고 양쪽 parent를 fsync한다.
Android 13/16 libcore source의 no-replace guard → rename → EXDEV/EISDIR에 한정된 copy fallback을 확인했다.
이 관리 경로의 조건에서 fallback을 피하며 `REPLACE_EXISTING`이나 target 교체를 허용하는 `ATOMIC_MOVE`는 요청하지 않는다.
공식 source URL/hash/excerpt는 `files-move-api/`에 보존하고 ADR-0002를 실제 경로에 맞췄다.

외부 intent 처리의 불필요한 `setIntent`는 ActivityScenario가 추적하는 launch intent identity를 바꾸어
lifecycle cleanup을 무시하게 했다. 전달된 incoming intent 처리 흐름을 유지하면서 해당 호출을 제거했다.
asset segment fixture는 API 36의 read `openAssetFileDescriptor`가 typed dispatch를 거쳐 2-argument
`ContentProvider.openAssetFile`을 호출한다는 공식 source를 확인했다. fixture의 기존 cancellation 3-argument override만으로는
이 경로를 처리하지 못하므로 2-argument override를 추가하고 공통 helper에 위임했다. `asset-provider-api/`에 근거를 남긴다.
이 교정은 assertion을 약화하거나 실제 provider 테스트를 제거하는 변경이 아니다.

### 교정 source `cfe17f0` 로컬 CI5·최종 fixture compile PASS — 원격 대기

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-5 ./scripts/ci-check.sh`는 actual exit 0/PASS다.
Android unit 38/shared 43 cases의 failure/error/skipped는 모두 0이며 lint는 20 Hint, 양쪽 APK/schema 검사는 PASS다.
실행 시작/종료 source drift는 0이고 fingerprint SHA-256은
`1b66992ce15236e6acc0b52f1465bde9af2f7d18fd94e6c9e25c2bd5eead7949`다. native XML과 log는 `local-ci-attempt-5/`에 보존한다.

그 뒤 `TestAssetProvider.java` 1개만 바꾸었으며 production/Android unit/shared test/config input hash는 CI5와 동일하다.
`./gradlew --no-daemon --console=plain --stacktrace :androidApp:assembleDebugAndroidTest :androidApp:lintDebug`가
actual exit 0/PASS, lint 20 Hint이며 final fingerprint
`94d162eadf2669eb62f376efec97f077f2e0377baf1f3387a786da3bde391040`의 실행 중 drift도 0이다.
`local-asset-fixture-validation/summary.json`은 이 source 동등성과 실제 compile/assemble/lint 결과를 구분한다.
새 승격 성공/no-overwrite 회귀를 포함한 ordinary 필수 identity는 24개이며 host는 별도 2 stages다.

수정 source는 `cfe17f0b9d2efbc39d6817c5e9b1470539587e09`로 commit/push했다. 제공 로컬 APK
`/workspace/bookreader-mobile-0.2.0-debug.apk`의 SHA-256은
`63f4174da6012253a30ce37059e72b5d5c40ce955063cf7e7161dab4eb9ac65a`, 크기는 17,023,931 bytes다.
최종 test APK SHA-256은 `75fa96cba2497b6b76cd9ec4f7ba9f9badaef972d273c94b24649dd32a3d3608`이다.
`final-local-apk.json`에 실제 aapt/apksigner exit 0, versionName 0.2.0/versionCode 2와 이전 local M00과 동일한 signer를 기록했다.
과거 CI signer와 다르므로 기존 CI 설치본의 데이터 유지 업데이트를 보장하거나 데이터 삭제를 제안하지 않는다.
최신 source의 원격 build, actual ordinary Android 24 cases/host 2 stages는 아직 결과 대기다.
M02/M03은 IN_PROGRESS이며 실기기/OEM 성능·접근성, M04 미커밋 이동 flush, EPUB/Readium은 별도 잔여 gate다.

### `cfe17f0` 원격 build PASS / 실제 가져오기 PASS / Reader 관찰 실패

원격 build [37171679446](https://github.com/LaceyLuv/BookreaderM/actions/runs/37171679446)의 실제 native XML은
Android unit 38/shared 43 cases, failure/error/skipped 모두 0이다. lint 20 Hint, schema 1/2 local 일치와 APK 서명을 확인했고
`remote-cfe17f0-build/summary.json` 및 원본 reports에 보존했다.
실제 device [37171679395](https://github.com/LaceyLuv/BookreaderM/actions/runs/37171679395)는 native ordinary
24 cases/1 failure/0 errors/0 skipped다. 가져오기 13 cases(system picker·VIEW·SEND·manual·asset segment·승격 guard 포함)와
encoding 3 cases는 모두 PASS다. 새 Files.move 경로의 실제 성공/no-overwrite를 확인했다.

단독 실패는 `TxtReaderFlowTest.measuredCanonicalLineIsSavedAndContinueRestoresAfterCacheLossAndRecreation`의
fixture `saved(book)` read가 repository DATABASE_UNAVAILABLE로 매핑된 것이다. 원래 내부 exception을 로그에 남기지 않아
Room initialization contention/SQLITE_BUSY는 아직 가설이며 production DB 결함이나 원인 해결을 단정하지 않는다.
`remote-cfe17f0-device/summary.json`에 exact method/stack과 native XML을 보존했다. ordinary gate 실패로 host는 NOT_RUN이고
최종 screenshot pull은 NOT_COLLECTED다.

### test-only DB observer 수명·진단 교정 — 재검증 대기

`3ce33868503bac2dd0ecd3829a7bf1a77d562f90`는 Flow/host fixture와 raw DAO 진단 helper 3 test files만 변경했다.
각 Flow/host stage의 행동 전에 DB observer 하나를 열고 stage 종료까지 유지한다. Error를 Missing으로 바꾸거나 retry하지 않는다.
Error 발생 시 raw DAO 진단을 첨부하고 실패시키며 측정 행·durable commit·복원 assertions와 production writer는 유지한다.
Astra는 이 observer 소유권과 오류 보존을 검토했다. 실제 DB error 원인 조사와 M04 concurrency 검증은 follow-up이다.

`./gradlew --no-daemon --console=plain --stacktrace :androidApp:assembleDebugAndroidTest :androidApp:lintDebug`는
actual exit 0/PASS, lint 20 Hint다. `local-reader-observer-validation/summary.json`은 104-file drift 0과
fingerprint `a0cc88b0119f7b5db7df90c0f46d08e110cb27919fa9b89bf97840c1217136e3`를 기록한다.
production/Android unit/shared/config input hash는 CI5와 같아 실제 38/43 proof를 유지한다.
test APK SHA-256은 `b3c07f6b39187777f686d34693122e6e30b7ce37f168353aa772df4532c2e3c4`이며
제공 앱 APK SHA-256은 이전과 같은 `63f4174da6012253a30ce37059e72b5d5c40ce955063cf7e7161dab4eb9ac65a`다.
새 source의 full remote build 및 actual ordinary 24/host 2는 아직 결과 대기다. M02/M03 IN_PROGRESS를 유지한다.


## 2026-10-09 — TXT 내부 알파 재개 기준과 Room 소유권 교정

HEAD/remote main은 `3ce33868503bac2dd0ecd3829a7bf1a77d562f90`로 확인했고 10월 4일 미커밋 docs/evidence와
`ManagedImportAndroidTest` 진단 변경을 보존했다. 원문 계획/첨부 template은 변경하지 않는다.
`resume-2026-10-09-baseline.json`은 재개 시 환경/실행 상태를 기록한다. 기존 Temurin 17.0.16+8,
SDK API36/build-tools 35.0.0/Gradle wrapper 8.13 및 상속된 proxy TLS 옵션·`/workspace/toolchains/cacerts`를 유지했다.
`./scripts/doctor.sh`는 exit 0/PASS이고 local adb 연결 기기는 0이다. 이 baseline 시점의 새 Gradle 실행은 NOT_RUN이었다.
네트워크 정책은 unrestricted 상태를 추정하지 않았고 키/토큰/인증서 파일을 evidence에 복사하지 않았다.

### 재개 전 최신 실제 원격 결과

원격 build [37172491541](https://github.com/LaceyLuv/BookreaderM/actions/runs/37172491541)는 PASS다.
`remote-3ce3386-build/summary.json`과 native XML은 Android unit 38/shared JVM 43 cases,
failure/error/skipped 모두 0, lint 20 Hint 및 committed schema 1/2 bytes 일치를 기록한다.
actual device [37172491508](https://github.com/LaceyLuv/BookreaderM/actions/runs/37172491508)는 ordinary 24 cases/1 failure/0 errors/0 skips다.
`remote-3ce3386-device/summary.json`과 원본 XML/log는 유일한 실패
`ManagedImportAndroidTest.externalViewReceivesTemporaryGrantAndOpensActualReader`를 보존한다.
import Idle/library Content인 상태에서 첫 reader_ready 이전 readerPhase ERROR다. 실제 내부 exception은 당시 진단에 없다.
이전 `cfe17f0` 실패였던 measured canonical line 저장/cache loss/Continue/recreation과 missing managed copy flow는
둘 다 PASS이며 Android encoding 3도 PASS다. 과거 가져오기 13 PASS와 구분하여 최신 외부 VIEW failure를 기록한다.
ordinary mandatory gate 실패로 host 2 stages는 NOT_RUN, 최종 fixture screenshot pull은 NOT_COLLECTED다.
Room 초기화 contention/SQLITE_BUSY는 미확인 가설이며 원인 해결을 주장하지 않는다.

### 재개 working tree 변경과 잔여 gate

서재·import/recovery/delete·Reader progress access가 synchronized `AndroidDatabaseOwner`의 process-lifetime
Room instance/pool을 공유하고 borrower가 닫지 않도록 소유권을 명시했다. fresh `createAndroidDatabase`는
독립 테스트의 caller-owned 경로로 남는다. repository/Reader는 fixed 단계·기존 오류 코드·최대 4개/64자 클래스 chain의
sanitized snapshot을 제공한다. private 책 본문/URI/path/SQL/예외 메시지·stack trace를 production 진단에 담지 않는다.
Error/Missing·cancellation·조회/복원 후 저장 gate·epoch/sequence 조건부 commit과 실패 보존은 유지한다.
세부 선택과 불확실성은 ADR-0002/0004에 남긴다. 이 변경만으로 최신 외부 VIEW 실패 해결을 선언하지 않는다.

재개 후 full `ci-check`(assemble/lint/unit/shared/schema 및 양쪽 APK), actual ordinary Android 필수 전체,
별도 host force-stop 2 stages와 자체 fixture PNG 4개를 실제 실행하여 결과를 기다린다. 현재 상태는 M02/M03 IN_PROGRESS다.
M04 전체 SAFE/race/60초 주기 저장·REFLOWING 미커밋 flush, physical 성능/OEM/TalkBack/16KB,
EPUB/Readium M01은 미완료다. TXT 내부 알파 `0.2.0`을 세 포맷 최종 v1.0으로 표시하지 않는다.

재개 시점 제공 `/workspace/bookreader-mobile-0.2.0-debug.apk`는 이전 `cfe17f0` 앱 source로,
SHA-256 `63f4174da6012253a30ce37059e72b5d5c40ce955063cf7e7161dab4eb9ac65a`이며 새 미빌드 Room/진단 변경을 포함하지 않는다.
새 빌드 후 실제 version/size/hash/signature를 다시 기록해야 한다. 기존 local signer
`aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 유지하며 CI의 독립 debug signer와 구분한다.
서명이 다른 설치본에 대한 데이터 유지 업데이트를 보장하거나 uninstall/data clear를 안내하지 않는다.

소스 승인·고정 뒤 full CI6 실행을 시작했다. 새 필수 검사는 Android unit 41/shared JVM 45,
ordinary Android 25 및 별도 host 2 stages다. 종료 결과/실제 XML/source drift 확인 전 PASS로 기록하지 않는다.
`AndroidDatabaseOwnerTest`는 직접 생성한 DAO fixture에서 12 concurrent borrower의 동일 owner,
library lease 종료 후 3회 새 Reader 활성화/저장 및 독립 caller-owned observer를 검사한다.
실제 import borrower 수명은 강화한 외부 VIEW provider→import→Reader READY 회귀가 함께 담당한다.
owner 테스트 자체가 실제 import를 수행한다고 해석하지 않는다.


### `2be610a` full CI6 PASS 및 새 로컬 APK

`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-6 ./scripts/ci-check.sh`는 actual exit 0/PASS다.
`local-ci-attempt-6/summary.json`, `results.txt`, log와 native XML은 Android unit 41/shared JVM 45 cases,
failure/error/skipped 모두 0, lint 20 Hint, app/test APK 및 generated Room schema 검사 PASS를 기록한다.
source fingerprint `85556d8e9ae941153761b22791bc34fdc71e11aaee890e5da474df9d8f31e3a0`의 실행 중 drift는 0이다.
같은 소스는 `2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf`로 commit/push했다.

제공 `/workspace/bookreader-mobile-0.2.0-debug.apk`를 새 로컬 빌드로 교체하고 복사본 실제 SHA-256을 확인했다.
SHA-256은 `021ec47de8311435f6becd0eb5b6e677ed22312b6ef3740b86d86b60d10a6e5e`, 17,027,692 bytes다.
`final-local-apk.json`의 실제 aapt/apksigner exit는 0이며 versionName `0.2.0`/versionCode 2와 이전 local M00과 동일한
signer `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 확인했다.
이전 APK는 `/workspace/bookreader-mobile-0.2.0-debug-before-2be610a.apk`, 이전 metadata는
`final-local-apk-before-2be610a.json`에 보존했다. 새 APK는 Room 공유/진단 변경을 포함하지만 actual 기기 gate PASS는 아직 아니다.
새 원격 build, ordinary Android 25/별도 host force-stop 2 stages/자체 fixture PNG 4개 결과를 기다린다.
M02/M03 IN_PROGRESS와 M04/EPUB/실기기/OEM/16KB 잔여 gate를 유지하며 SQLite 오류 원인 확정이나 최종 v1 완료를 주장하지 않는다.


동일 source의 원격 build [37879616525](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616525)는 PASS다.
`remote-2be610a-build/summary.json`과 다운로드한 native reports는 Android unit 41/shared JVM 45,
failure/error/skipped 모두 0, lint 20 Hint 및 schema 1/2 local bytes 일치를 기록한다.
원격 app APK SHA-256 `00941a5d21ba9cd7a86c45cddf18a42bda5b422bbf6c7e94c867439273b8df06`의 apksigner exit는 0이고
CI signer `980adc346f1c476d4a98ef096b0782bbe153871296fb17d7b94f94ff6fee70d5`는 제공 local signer와 다르다.
원격 APK를 제공 local APK와 동일 바이트/서명이라고 주장하지 않는다.
기기 workflow `37879616383`는 success지만 actual ordinary/host proof/PNG 검증과 Astra 승인 전 milestone DONE은 보류한다.


### `2be610a` actual Android/host/fixture 최종 PASS — TXT 내부 알파 승인

실제 device [37879616383](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616383)의 source는
`2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf`다. native ordinary 25 cases의 failure/error/skipped는 모두 0이며,
외부 VIEW·실제 SAF/SEND·Android encoding 3·Room owner·Reader measured anchor/cache loss/Continue/recreation 회귀가 PASS다.
원격 artifact `11593602844`의 native XML은 `remote-2be610a-device/native-connected/`, 요약과 workflow metadata는
`summary.json`/`workflow.json`, 실제 검증 출력은 `android-device-verified.log`/`android-host-verified.log`에 보존한다.

별도 host `prepareDurableReader`/`verifyColdLibraryContinue`는 exact raw instrumentation start/pass/finish 및 2-stage manifest PASS다.
외부 adb가 실제 살아 있는 PID 5624를 force-stop했고 이후 PID absent를 확인한 뒤 cold launcher PID 5671을 확보했다.
마지막 commit UTF-16 offset 11607/sessionEpoch 1/sequence 2를 서재 하단 Continue가 복원했다.
raw stage/PID/committed proof 및 helper XML은 `remote-2be610a-device/host-process-proof/`, 실제 명령/exit 기록은
`remote-2be610a-device/command-evidence/`에 보존한다. 모든 stage/외부 종료/launch 명령 exit는 0이다.
기기 검증 명령은 `./scripts/device-check.sh`, host는 `python3 scripts/reader-process-check.py build/device-evidence/reader-host-process` 계약의
실제 순서로 수행했다. verification instrumentation은 또 다른 target process를 시작할 수 있으며 미커밋 화면 위치 보존은 증명하지 않는다.

`remote-2be610a-device/fixture-screenshots/`의 `m02-library.png`, `m03-reader.png`,
`txt-reader-generated-scroll.png`, `txt-reader-generated-host.png`는 실제 PNG 1080×1920 decode 및 SHA-256 검증 PASS다.
각 hash/bytes는 `summary.json`에 기록하고 root가 4개 화면도 확인했다. 사용자의 개인 책 대신 자체 작성 fixture만 캡처했다.
로컬 CI6 native XML/manifest와 원격 build native XML/schema도 ignore되지 않는 `native-*` 경로에 보존했다.

Astra가 실제 source/필수 identity/host PID 종료 proof/raw stage/PNG를 독립 검토하여 좁은 TXT 내부 알파에 blocker가 없음을 승인했다.
M02/M03을 TXT 내부 알파 범위 DONE으로 변경하고 M04는 입증된 force-stop criterion만 체크하며 IN_PROGRESS를 유지한다.
M01 Readium/EPUB, M04 전체 SAFE/race/A-B rapid reopen/60초 저장·REFLOWING 미커밋 flush,
physical 성능/OEM/TalkBack/16KB 및 TXT 페이지/본문 검색/북마크·Comic은 후속이다.
Room 소유권 변경 후 회귀 PASS와 이전 SQLITE_BUSY 내부 원인 입증은 구분한다. 원래 내부 원인은 여전히 미확인이다.

제공 local debug APK는 같은 검증 source의 빌드이며 hash `021ec47de8311435f6becd0eb5b6e677ed22312b6ef3740b86d86b60d10a6e5e`,
0.2.0/versionCode 2, signer `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`다.
기기 job은 설치 APK hash를 보존하지 않으므로 이 local APK와 actual device 설치본의 byte 동일성을 주장하지 않는다.
CI 독립 debug signer와 구분하며 서명이 다른 기존 설치본을 uninstall/data clear하도록 안내하지 않는다.
최종 제품 경로는 `TXT 추가`→picker→관리 복사본→`읽기`; 외부 VIEW는 import 후 바로 Reader,
SEND는 확인→commit→`읽기`다. 새 프로세스는 서재부터 시작한다. 앱은 TXT 내부 알파이지 최종 세 포맷 v1.0이 아니다.


## 2026-10-11 — M04 REFLOWING 미커밋 flush 보완

`/workspace/bookreader-mobile` 부재를 확인하고 사용자 지정 URL/main을 해당 경로로 clone했다.
시작 HEAD는 `03870c9c612694ca69528d0b5df7e44fa432a447`, `main...origin/main` clean이었다.
`/AGENTS.md`와 `/workspace/AGENTS.md`는 없었으며 저장소 AGENTS, CONTEXT_HANDOFF,
TASKS M04/개정 계획 SAFE matrix/ADR-0004와 controller·Android progress adapter·Room writer·ViewModel을 읽었다.
기존 README와 과거 증거는 유지했다.

이번 한 작업은 `persistStable`의 READY-only gate 때문에 REFLOWING 중 flush/close가
직전 미커밋 사용자 anchor를 저장하지 못하는 결함이다. READY에서 얻은 stableOffset만 REFLOWING에서도
저장하도록 바꾸고 임시 locator/비사용자 callback 차단 및 mutex/session/sequence 검사·취소 소유권은 유지했다.
새 회귀 4개는 미커밋 역방향 위치 flush, 지연 write 중 close 대기, 이동 없는 reflow, 오류 후 retry를 검증한다.
테스트는 저장 실패를 주입한 단위 검사이며 실제 disk-full 결과가 아니다.

변경 파일:
- `androidApp/src/main/kotlin/org/bookreader/mobile/reader/TxtReaderController.kt`
- `androidApp/src/test/kotlin/org/bookreader/mobile/reader/TxtReaderControllerTest.kt`
- `scripts/required-tests.json`
- `docs/adr/0004-progress-writer-session-order.md`, `docs/TASKS.md`, `docs/TESTING.md`, `docs/CONTEXT_HANDOFF.md`, 이 WORKLOG
- `docs/evidence/m04-reflow-validation/`의 raw logs/native XML/source fingerprint/summary

새 환경에 정확 pin의 JDK/SDK를 설치하고 제공 proxy/CA trust를 유지했다.
원래 툴체인·debug signer/APK는 이 clone에 없었다. 도구/CA/서명키는 저장소 evidence에 넣지 않는다.
정확 명령과 exit는 TESTING M04 절에 있으며 full CI 내부 명령/exit는 `ci-check.log` 및 `ci/results.txt`에 있다.
수정 전 tests-only 실행은 4개/3 failures, exit 1로 결함을 재현했다. 이후 full CI는 exit 0,
Android unit 45/shared JVM 45, failure/error/skipped 모두 0, lint 20 Hint, APK/schema PASS다.
검증 입력 fingerprint `3cc61fc407e9a1441b862fba940e178dc849c22b0e240ef222487f35939b24cf`, source drift 0이다.
`git diff --check`는 exit 0이다. 새 local device-check는 authorized device가 없어 exit 78/BLOCKED_ENV다.

M04는 IN_PROGRESS를 유지한다. 다음 선행 조건은 이번 소스에서 실제 Android reflow/ON_STOP 및 host 회귀 실행 환경,
SAFE-01/02/03/04/06/07/09/10/12 matrix의 실제 단계별 증거, A/B 및 같은 책 rapid close/reopen,
60초 연속 이동과 실제 disk-full/commit-latency 측정이다. 신규 Astra 최종 검토는 NOT_RUN이다.
과거 `2be610a` 기기/host PASS를 이번 수정의 결과로 재사용하지 않았다.


최종 stage 이후 `git diff --cached --check`는 exit 2였다. 신규 raw Gradle/adb 로그의 원래 줄 끝 공백 및 EOF 빈 줄만
보고했으며, 원본 실행 증거는 수정하지 않았다.
`git diff --cached --check -- . ':!docs/evidence/m04-reflow-validation'`는 exit 0으로 소스·문서의 공백 검사를 통과했다.


## 2026-10-11 — M04 실제 DB race와 host 준비의 UI 게시 스레드

시작은 clean `main...origin/main`, HEAD `16e4615247be9ef4a4c0534354f2a6b00c9083ab`였다.
상위 AGENTS 부재와 저장소 지침, 최신 TASKS/CONTEXT_HANDOFF/ADR 및 writer/DAO/DB fixture를 재확인했다.
`RoomProgressRaceTest`는 실제 파일 Room/bundled SQLite에서 immutable event 전달을 deferred gate로 지연한다.
A 지연→B, 12회 같은 책 epoch 전환/reopen, forward 뒤 backward sequence, 동시 duplicate sequence,
DELETING/삭제 완료 뒤 event를 검증하며 전체 durable record/책 metadata/다른 책 보존을 확인한다.
DB writer production 로직은 변경하지 않았다. task 5개 단독 실행 exit 0/PASS다.

이전 source의 원격 build `38109705786`은 workflow success였고, device `38109705802`는 ordinary 25 PASS지만
host prepare에서 실패했다. artifact `11691343396`의 native XML을 직접 검증했고 raw host 실패를 보존했다.
stack은 IO import progress가 AppViewModel StateFlow를 갱신하여 Compose test inline continuation이 worker에서 view를 건드린 경로다.
새 unit은 실제 executor와 inline collector로 수정 전 `Progress must be published on Main` 실패(exit 1)를 재현했다.
AppViewModel의 suspend callback은 UI 게시만 Main.immediate로 옮기며 파일 작업 IO와 부모 import 취소/완료 순서를 유지한다.
검증 기준이나 host parser를 완화하지 않았다.

변경 파일은 `shared/src/jvmTest/.../RoomProgressRaceTest.kt`, `androidApp/src/main/.../ui/AppViewModel.kt`,
`androidApp/src/test/.../ui/AppViewModelTest.kt`, `scripts/required-tests.json`,
TASKS/TESTING/CONTEXT_HANDOFF/ADR-0004/WORKLOG, 새 `docs/M04_SAFETY_MATRIX.md`,
`docs/evidence/m04-race-validation/`다. 기존 README/사용자 변경/과거 증거는 보존했다.

정확 명령은 TESTING의 M04 DB 경쟁 절을 따른다. doctor/task discovery는 exit 0,
full CI는 exit 0(Android unit 46/shared JVM 50, failure/error/skipped 0, lint 20 Hint, APK/schema PASS)다.
source fingerprint `2cce4a46f213306e12cc9d54a6ec07f8f1d08991cc5445b7a7ac21acb5da31c1`, drift 0이다.
새 device/host는 push 뒤 결과를 확인한다. M04는 IN_PROGRESS, SAFE 전체/60초/실제 disk-full/실기기 latency/신규 Astra 검토는 미완료다.


### `96c15aa` 원격 검증 확정

코드·회귀·중간 증거를 `96c15aa6d074b0b6d96579e1deb54d6fbc3413ec`로 commit/push했다.
원격 build `38110471084`는 success이며 artifact `11691432639`의 native XML이 Android unit 46/shared JVM 50,
failure/error/skipped 0이고 exported schema는 local과 같다. 원격 기기 `38110471069`도 success다.
artifact `11690673689`의 source SHA는 정확히 `96c15aa`이며 ordinary native XML은 25/0 failures/errors/skips다.

actual host raw 두 stage는 정확한 start/pass/finish identity를 검증했다. host script가 실제 live PID 5640을
force-stop한 뒤 absent, cold launcher PID 5688을 확인했고 offset 11607/epoch 1/sequence 2 commit이
cold 서재 Continue로 복원됐다. stage/adb command exit는 모두 0, device-check wrapper도 exit 0이다.
Activity recreation이나 미커밋 화면 위치 보존으로 해석하지 않는다. verification instrumentation은 별도 target process를 시작할 수 있다.
각 native XML/raw logs/proof/workflow/archive SHA-256은 `docs/evidence/m04-race-validation/remote-96c15aa-{build,device}/`에 보존했다.
exact 실행/다운로드 후 verifier 명령은 TESTING에 있으며 모두 exit 0이다. 이전 `16e4615` host FAIL을 삭제하지 않았다.

로컬·원격 결과 및 M04 잔여 항목을 TASKS/TESTING/CONTEXT_HANDOFF/ADR/SAFE matrix에 확정한다.
이 확정은 문서·증거 변경뿐이며 검증 code input은 source fingerprint와 동일하다.
새 source의 실제 기기 host 회귀는 완료했으나 전체 SAFE/60초 연속 이동/실제 disk-full/실기기 latency와
신규 Astra 최종 검토는 미완료다. 다음 우선 작업은 60초 주기 commit 또는 SAFE-07 실제 disk-full 검증이다.
코드·문서 공백 검사는 evidence raw 로그를 제외하고 exit 0이며, raw Gradle/adb의 원래 공백은 유지한다.


## 2026-10-11 — M04 SAFE-09 60초 연속 이동 검증

시작 HEAD `0a66be5a4887ed19adf0bccb30561d2c41cd5168`, main/origin clean 및 상위 지침 부재를 확인했다.
TASKS/SAFE matrix/인계와 controller sampler, Reader 실제 UI, TestDocumentsProvider, 기기 wrapper를 읽었다.
공식 Compose UI Android 1.9.3 source JAR에서 SemanticsNode.positionOnScreen/size를 확인했고 URL/hash는
`docs/evidence/m04-continuous-validation/compose-api.json`에 남겼다.

controller unit은 실제 cache IO를 마친 뒤 60초 가상 시간의 100ms 이동을 주입한다. sampling 60회/정확 offset/sequence와
그 뒤 250ms settled backward save를 확인한다. 테스트 소유 scope를 cancel/drain한 뒤 controller를 닫는다.
actual Reader case는 자체 생성 reader-continuous 6,000행 TXT를 가져온 뒤 한 pointer를 60초 이상 유지하며
2초마다 방향을 바꾼다. visibleAnchor/flush를 직접 호출하지 않고 실제 측정 anchor와 별도 Room observer를 기록한다.
report에 fixture hash/bytes, device/API/ABI/app version, 입력/anchor/새 sequence 관찰 시각과 최종 위치를 남긴다.
사용자 책/URI나 SQL ack latency로 확대하지 않는다. 구체 한도와 해석은 M04_COMMIT_LATENCY를 따른다.

device-check는 연결 테스트 실패 때도 report 수집을 시도하되 원래 nonzero exit를 유지한다.
새 native identity 2개를 manifest에 등록하고 raw JSON의 PASS/60초/입력 간격/commit 간격을 별도로 확인한다.
기존 실패/과거 증거/README는 보존했다. 생산 앱/DB writer 코드 변경은 없다.

변경 파일은 TxtReaderControllerTest, TxtReaderFlowTest, TestDocumentsProvider, scripts/device-check.sh,
scripts/required-tests.json과 TASKS/TESTING/CONTEXT_HANDOFF/ADR-0004/WORKLOG/M04_COMMIT_LATENCY 및 evidence다.
정확 실행 명령은 TESTING SAFE-09 절과 ci-check.log/ci/results.txt를 따른다. doctor/task discovery,
단독 unit+test APK, bash -n, full CI 모두 exit 0이다. 최종 native unit 47/shared JVM 50, failures/errors/skips 0,
lint 20 Hint 및 APK/schema PASS, source fingerprint d0bf070fa0e2cd318ef3946db01db752574a729be70dfff7a0dc584873d8ac4a,
drift 0이다. actual 기기/host 검증은 push 후 확인한다. SQL latency/실기기 및 M04 잔여 gate는 NOT_RUN이다.

### 실제 연속 이동 FAIL과 Reader 관찰 수정

`78e45f5153d47d7bc3188ebe90905b3988445be2` push 뒤 build `38116198367` native 47/50은 PASS였으나
actual device `38116198398` ordinary 26 중 새 60초 case 1개가 FAIL(exit 1)했다. host는 NOT_RUN이다.
raw JSON은 60,081ms/600 inputs/최대 입력 공백 130ms/anchor 변화 0/commit 관찰 1을 보인다.
원본 native XML/connected log/report/source SHA 및 artifact `11693885911` archive hash를
`docs/evidence/m04-continuous-validation/remote-78e45f5-device/`에 보존했다.

Foundation Android 1.9.3 공식 source JAR에서 LazyListMeasuredItem.applyScrollDelta가 offset을 바꾸며
LazyListMeasureResult가 같은 visibleItemsInfo 객체를 재사용하는 것을 확인했다. URL/hash는 foundation-api.json이다.
Reader의 snapshotFlow가 mutable item 객체를 그대로 반환하여 동일 fragment 내부 이동 emission을 놓친다.
TxtReaderScreen에서 item의 index/offset을 값으로 캡처하도록 수정했다. 새 테스트/한도/SQL writer는 그대로다.

`source /workspace/toolchains/mobile-env.sh` 뒤
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-continuous-validation/fix-ci ./scripts/ci-check.sh`는 exit 0,
unit 47/shared JVM 50, failure/error/skipped 0, lint 20 Hint, APK/schema PASS다.
입력 fingerprint는 `480c6087db7ebd764497bf84534605af5f1880c87a21109777968ab7be493018`, drift 0이다.
`git diff --check -- . ':!docs/evidence'`도 exit 0이다. 실제 Android/host 수정 후 결과는 push 뒤 확인한다.

### `14ce759` 연속 이동 PASS, host 준비 대기 FAIL

수정 source `14ce759131342364b79a52442566e78c747bc6cc`를 push했다. build `38116877737`은 native 47/50 PASS,
기기 `38116877735`는 ordinary native 26/0 failures/errors/skips 및 continuous JSON verifier PASS다.
실제 60,033ms/593 inputs/anchor 224회/commit 60회, 최대 입력 공백 250ms,
최대 commit 관찰 공백 1,102ms, p50 1,000ms/p95 1,100ms(nearest rank, 시작/끝 구간 포함),
pointer-up 후 최종 anchor 관찰 266ms다. 이 측정은 SQL write-ack latency가 아니다.

workflow 전체는 host prepareDurableReader의 77행에서 fixed expected offset commit 대기 10초가 만료되어 FAIL(exit 65)이다.
`remote-14ce759-device/`에 native XML/raw report/host 실패/workflow/artifact hash를 보존했다.
host 코드는 마지막 swipe 뒤 안정화를 기다리지 않고 nonzero anchor를 바로 캡처하고 있었다.
기존 이동 누락 수정 뒤 중간 fling 위치를 expected로 고정할 수 있어, 명시적 compose.waitForIdle로
마지막 fling/layout을 완료한 뒤 expected를 잡고 commit 후 같은 위치/saveError false를 확인하도록 보완한다.
현재 raw 실패에는 중간/최종 offset 수치가 없어 timeout만으로 실제 값 불일치를 확정하지는 않는다.
기존 force-stop/모든 필드 보존 검증과 timeout/필수 test/parser 기준은 유지한다.

host 동기화 수정 뒤 `source /workspace/toolchains/mobile-env.sh` 및
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-continuous-validation/host-ci ./scripts/ci-check.sh`는 exit 0이다.
단위 47/50·failures/errors/skips 0·APK/schema·lint 20 Hint는 PASS, fingerprint
`0670f2c61c8c6522123f3bd0c7261172649eecdafe3679385946e409dfea561c`, drift 0이다.

### `73276fe` 최종 remote build/device/host PASS

검증 source `73276fe27da781dde807f05a85422ae18363642f`의 build `38117532227`은 PASS다.
artifact `11694107392` native Android unit 47/shared JVM 50, failures/errors/skips 0 및 schema local 일치를 확인했다.
기기 `38117532253`도 exit 0/PASS이며 artifact `11694212469`의 native ordinary 26과
actual host 2 stages를 검사했다. native 필수 identity, exact raw stage start/pass/finish,
모든 adb exit 0, live PID 5672→force-stop→absent→cold launcher 5719를 확인했다.
마지막 commit offset 13285/epoch 1/sequence 2가 cold 서재 Continue로 복원됐다.

SAFE-09 최종 raw JSON은 60,001ms/입력 592/anchor 226/새 commit 46, 최대 입력 공백 148ms,
commit 관찰 p50 1,017ms/p95 2,033ms/최대 2,034ms, pointer-up 후 최종 일치 관찰 259ms다.
시작/끝을 포함한 관찰 간격의 nearest-rank이며 SQL write-ack latency나 실기기 SLA 측정이 아니다.
보고서/측정 기기 fingerprint/fixture hash/native XML/raw host/명령 exit/workflow SHA/archive SHA-256을
`docs/evidence/m04-continuous-validation/remote-73276fe-{build,device}/`에 보존했다.

정확 CI/device/native verifier 명령은 TESTING 최상단에 있으며 모두 exit 0이다.
원본 JSON에서 각 입력·commit 공백, 매 10초 구간 이동/commit, 증가 sequence를 다시 계산했다.
fixture 647,999 bytes/SHA256 e094cb17995b2aafccd165a47013ccd3f8a9b91190c2b9209a0635432872b951도
독립 생성 결과와 같다. 준비 안정화 보완 후 같은 host 검증이 통과했으며 `14ce759` 준비 timeout 원본은 유지한다.

최종 문서 작성 때 source-before-host-ci의 106개 입력 hash가 모두 일치해 drift 0이다.
TASKS/TESTING/CONTEXT_HANDOFF/SAFE matrix/측정 보고서/ADR과 evidence summary를 확정한다.
SAFE-09 emulator correctness는 PASS지만 M04는 IN_PROGRESS다. SAFE-07 실제 disk-full,
UI A/B·same-book rapid reopen, 삭제 중 decode/index, reflow/ON_STOP, SQL ack/실기기 수치 및 신규 Astra 검토가 남는다.

## 2026-10-11 — SAFE-07 native SQLite FULL 검증

시작은 clean main/origin/main `685b6d23acfcdb66f2a95ad86c4e3e1809046ee7`이다. 상위/저장소 AGENTS,
CONTEXT_HANDOFF/SAFE matrix와 실제 writer/DAO/schema/Reader 오류 경로를 읽었다.
cloud-environment-runtime 스킬에 따라 환경을 확인했다. network enforced/unrestricted, credentials 없음,
기존 JDK17/SDK 환경을 재사용했다. Room 2.8.3 및 SQLite 2.6.1 공식 source의 useWriterConnection/usePrepared/
execSQL/SQLiteException과 공식 SQLite max_page_count/code 13을 확인했다. URL/hash는 official-api.json이다.

RoomProgressFullTest 3개는 실제 파일 DB와 native engine을 사용한다. 용량 한도와 bounded 부가 할당 trigger로
progress UPDATE/첫 INSERT/activation metadata INSERT 뒤 SQLITE_FULL을 일으킨다. 원래 예외 code 13과
실패 stage, 전체 두 책/진행도/read_order 보존·DB reopen·같은 event retry 성공 및 부가 할당 성공을 검증한다.
예외나 repository 반환값을 주입하지 않는다. OS filesystem ENOSPC/WAL/fsync 부족과는 구분한다.

TxtReaderFlowTest에 자체 생성 두 TXT를 실제 import한 뒤 앱 소유 writer pool의 page 한도를 제한하는 case를 추가했다.
native probe와 오류 UI, 마지막 commit 및 다른 책/원본 hash 보존, 용량 복원 후 추가 입력/직접 flush 없는 자동 retry를
검사한다. finally에서 원래 한도와 fixture schema를 복원하며 빌린 DB는 닫지 않는다.
TestDocumentsProvider/required-tests/device-check도 새 fixture/필수 identity/raw JSON 수집·검증을 반영했다.
production writer/UI/schema는 변경하지 않았다. 기존 README/증거와 사용자 파일을 보존했다.

단독 shared 3개는 exit 0/native XML PASS다. Android APK 첫 compile은 member assertDoesNotExist를 extension으로
잘못 import해 exit 1이었다. import만 제거 후 동일 명령 exit 0; 두 로그를 보존했다. doctor/bash -n도 exit 0이다.
정확 명령은 TESTING 최신 절을 따른다. full CI exit 0, Android unit 47/shared JVM 53, failures/errors/skips 0,
lint 20 Hint, APK/schema PASS다. 107개 입력 fingerprint f1a40c26c304730dd729a9052a129d50292f4ddddf83d6082d4a028f12ae3e50,
drift 0이다. source-before-ci 및 native XML/명령 exit는 docs/evidence/m04-sqlite-full-validation에 보존했다.
actual Android/host는 push 후 검증한다. M04는 IN_PROGRESS이며 OS ENOSPC·실기기 및 신규 Astra는 NOT_RUN이다.

### Android 예외 타입 공식 확인 보완

초기 구현을 `7b9d27a`로 push한 뒤 Android SQLite 2.6.1의 실제 구현을 추가 확인했다.
SQLiteException은 Android에서 android.database.SQLException의 typealias이므로 진단의 simpleName을
JVM의 SQLiteException 문자열로 고정하면 안 된다. native probe가 실제 낸 runtime type과 Reader의
bounded diagnostic type을 비교하도록 테스트를 보완했다. code 13/동작/보존 기준은 유지한다.
공식 source URL/hash를 official-api.json에 추가했다.

`gh run cancel 38126142063`, `gh run cancel 38126142050`은 exit 0이며 오래된 7b9d27a의 두 원격 작업은
cancelled다. 실제 Android PASS/FAIL 증거로 취급하지 않는다. 보완 후 full CI 및 새로운 source로 원격 검증한다.

`source /workspace/toolchains/mobile-env.sh` 뒤
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m04-sqlite-full-validation/platform-ci ./scripts/ci-check.sh`는 exit 0이다.
최종 local native 47/53, failures/errors/skips 0, lint 20 Hint, APK/schema PASS, 입력 fingerprint
`504c4bb345f79b0f7f638bc0298c9d8eb7cadde96c81d0efefca73de7c4e0a00`, drift 0이다.

### `bf7589d` 원격 및 actual Android 확정

최종 source `bf7589dcc018c57fc43101a16198cc3afd12b9cd`를 main에 push했다.
remote build `38126287697`는 PASS이며 artifact `11696743481` native 47/53, failures/errors/skips 0,
세 SQLite FULL 원본 code 13 및 schema local bytes 일치를 확인했다.
actual device `38126287699`도 exit 0/PASS, artifact `11697417819` native ordinary 27/host 2 stages PASS다.

기기 report는 16 pages×4,096 bytes 한도와 growth 81,920 bytes, native probe code 13/SQLException을 보인다.
실제 Reader의 저장 실패 UI가 나타난 동안 마지막 commit offset 4964/epoch 1/sequence 1, 다른 책,
두 관리 파일 hash, metadata/read_order가 유지됐다. 용량 복원 후 추가 입력/직접 flush 없이 pending 6625를
sequence 3으로 자동 저장했고 오류/diagnostic이 사라졌다. 동일 trigger의 할당 1행과 최종 한도/schema 복원도 확인했다.
fixture 두 개의 hash/bytes는 독립 생성한 값과 일치한다. full report/native XML/verifier log를 보존했다.

기존 60초 회귀는 60,017ms/anchor 226/commit 60/최대 관찰 공백 1,067ms PASS다.
raw host 두 stage는 정확한 start/pass/finish와 모든 command exit 0, PID 5794→외부 force-stop→absent→cold 5843,
commit offset 13285/epoch 1/sequence 3의 cold 서재 Continue 복원을 확인했다.
원본 명령/exit/native XML/raw JSON/PID proof/workflow SHA/archive SHA-256을
`docs/evidence/m04-sqlite-full-validation/remote-bf7589d-{build,device}/`에 보존했다.

최종 정확 CI/device/native verifier 명령은 TESTING 최신 절을 따른다. 모두 exit 0이다.
증거 정리 당시 107개 검증 입력의 hash가 일치해 source drift 0이다. 이후 변경은 문서와 증거뿐이다.
TASKS/TESTING/WORKLOG/CONTEXT_HANDOFF/ADR/SAFE matrix/범위 보고서를 확정한다.
M04는 IN_PROGRESS, OS filesystem ENOSPC·WAL/fsync 부족, UI race/reflow 등 잔여 gate와 신규 Astra는 미완료다.

## 2026-10-11 — SAFE-07 실제 Linux filesystem ENOSPC

`071e920` clean main과 상위/저장소 지침·인계 문서를 확인하고 다음 잔여 ENOSPC 검증을 선택했다.
user/mount namespace에서만 새 8MiB tmpfs를 생성한다. helper는 mount source/type/capacity/path를 확인하고
bounded 할당으로 실제 errno 28을 확인한다. working filesystem의 공간은 소진하지 않는다.
production 파일/schema/UI는 변경하지 않았다. 별도 jvmEnospcTest compilation/필수 3개 identity 및
scripts/enospc-check.sh/fill-enospc-fixture.py와 build workflow의 필수 단계를 추가했다.

Room WAL checkpoint TRUNCATE 뒤 실제 production save/activation을 실행해 native code 13/stage를 검사한다.
두 책 전체 기록/metadata/read_order/TXT hash, 첫 저장 Missing, 공간 확보 후 DB 재개방·같은 event retry,
역방향 offset과 duplicate stale 거부를 확인한다. finally/EXIT에서 파일·DB·mountpoint를 정리한다.
TXT fixture 각 2,700 bytes는 자체 생성이다. Android import/Reader ENOSPC 또는 process-death 시험이 아니다.

첫 Gradle DSL compile exit 1과 namespace root cache exit 1은 원본 로그로 남겼다.
receiver/불필요한 non-null assertion과 Gradle home/cache만 수정한 뒤 전용 native 3개 PASS다.
full local CI exit 0, Android unit 47/shared JVM 53, failure/error/skip 0이다.
정확 명령/exit/원본 XML/초기 실패/111개 입력 fingerprint는 TESTING 및
`docs/evidence/m04-enospc-validation/`을 따른다. remote 검증은 push 후 진행한다.
M04 IN_PROGRESS, Android ext4/f2fs/fsync/rollback journal/조회 오류 UI 및 신규 Astra는 NOT_RUN이다.

### 원격 namespace 권한 보완

`a3ee33b` remote build `38127759100`의 일반 CI는 PASS이나 ENOSPC는 uid_map Operation not permitted로
exit 78/BLOCKED_ENV다. workflow 전체는 failure이며 제품 기록 보존 실패나 전용 테스트 PASS가 아니다.
원본 failed-step 로그와 workflow/artifact metadata를 보존했다. Ubuntu 24.04 runner에서만 sudo로
같은 private user/mount namespace 스크립트를 실행하도록 workflow를 보완했다. host sysctl/AppArmor는 변경하지 않는다.
기존 HOME/JDK/SDK/Gradle cache 경로를 유지한다. 새 검증 source로 remote build를 다시 실행한다.
앱/테스트/스크립트 바이트는 동일하며 CI 실행 권한 설정만 변경했다.

### 실제 기기 회귀 실패와 activation 보완

`a3ee33b` ordinary 27개 중 AndroidDatabaseOwnerTest가 REJECTED_STALE로 FAIL했다. 실제 host 단계는 시작하지
않았으므로 NOT_RUN이다. 이력의 PASS를 덮어쓰거나 재실행만으로 실패를 숨기지 않았다. raw XML/stack과
당시 continuous/page-limit report는 `remote-a3ee33b-device`에 보존했다.

실제 코드를 조사해 restored()의 job 검사와 launch 대입 사이에 callback이 겹치면 activation이 중복되는 경로를
발견했다. 새 deterministic 단위 회귀에서 expected 1/actual 2로 수정 전 실패했다. 기기 raw만으로 정확한
스케줄 interleaving을 단정하지는 않는다. restored() 진입을 직렬화하고 LAZY job을 먼저 등록해 재진입/동시
callback이 새 epoch를 추가로 만들지 않게 했다. 기존 owner 테스트/기준은 낮추지 않았다.
수정 후 targeted exit 0, full local CI Android unit 48/shared JVM 53 및 실제 ENOSPC 3개 모두 PASS다.

`7f7c46b` remote build는 일반 47/53 PASS 후 root user namespace의 checkout 접근 실패(exit 1)였다.
관리자 경로에서는 global user identity를 유지한 채 mount namespace만 생성하게 수정했다.
비권한 local 경로는 원래 user+mount namespace다. 전역 sysctl/AppArmor 변경은 없다.
정확 명령/exit/native XML/새 입력 fingerprint는 TESTING 및 `source-activation-fix.json`을 따른다.
새 source를 commit/push하고 remote build/device를 재검증한다. M04는 IN_PROGRESS다.

### `bacd79f` 결과와 page-limit UI 측정 순서 보완

`bacd79f` remote build `38128654804`는 native 48/53/ENOSPC 3 모두 PASS다.
관리자 mount namespace에서 실제 errno 28/가용 공간 0/SQLite13/보존·retry·정리까지 확인했다.
actual device `38128654709`는 기존 DB owner case가 PASS지만 ordinary 27개 중 page-limit UI 1개가
expected measured anchor 53 / controller stableOffset 6625로 FAIL했다. host는 NOT_RUN이다.
raw XML/report/command/metadata/archive hash를 `remote-bacd79f-{build,device}`에 보존했다.

오류 배너는 Reader viewport 높이를 바꿔 비동기 reflow를 시작한다. 기존 test는 배너 표시를 기다리기 전에
화면 anchor를 측정해 임시 scroll-to-item 프레임을 관측할 수 있었다. 배너 표시와 reader_ready 복귀를
먼저 기다린 후 같은 독립 TextLayout 측정을 하도록 순서만 수정했다. expected를 controller 값으로 대체하거나
기록/다른 책/파일/자동 retry assertion을 제거하지 않았다. 정확한 이전 scheduling 자체를 raw만으로 단정하지 않는다.
이번 변경은 Android test뿐이며 production 활성화 수정/단위/ENOSPC 코드는 bacd79f와 동일하다.
`./gradlew --no-daemon --console=plain :androidApp:assembleDebugAndroidTest`는 exit 0이다.
새 source로 실제 기기 회귀를 다시 검증한다.

### `ebee772` 원격 최종 확정

source `ebee7723eed8c7ca487736af52570ae1cfd3d4ba`의 remote build `38129413116` 및 actual device `38129413128` 모두 PASS다.
build artifact `11698451773`는 native 48/53/ENOSPC 3, device artifact `11698847810`는
ordinary 27/host 2 stages이며 failure/error/skip 0이다. sudo 관리자 경로의 private mount namespace에서
실제 errno 28/여유 공간 0/WAL 0 bytes/code 13 세 건 및 records/files/reopen/retry/cleanup exit 0을 확인했다.

이전 실패를 삭제하지 않았다. `a3ee33b` device의 DB owner case도 최종 PASS다.
raw host exact start/pass/finish, 모든 command exit 0, PID 5691→absent→cold 5739,
서재 Continue 복원을 확인했다. 실제 연속 이동 60,067ms/60 commits,
기존 page-limit FULL의 UI/기록/원본 보존 및 자동 retry도 PASS다.

원본 XML/JSON/host proof/command exit/workflow SHA/archive SHA-256 및 서버 digest 일치를
`docs/evidence/m04-enospc-validation/remote-ebee772-{build,device}/`에 보존했다.
111개 검증 입력 fingerprint `112c4be06eba47de32ff750c02af7d909310a3bea3f7d3c33e560a528de669e5`, drift 0이다.
이후 변경은 문서/증거뿐이며 README/사용자 파일은 보존했다. 정확 명령/exit는 TESTING/commands.json을 따른다.
TASKS/TESTING/WORKLOG/CONTEXT_HANDOFF/ADR/SAFE matrix/범위 보고서를 확정했다.
M04 IN_PROGRESS, 다음은 SAFE-01 실제 Reader DB 조회 오류/재시도 UI다. 새 Astra 검토는 NOT_RUN이다.


## 2026-10-17 — SAFE-01 actual Reader 조회 오류 및 retry

clean `bedfdea` main 및 origin 일치를 확인하고 기존 README/사용자 변경을 보존했다.
AGENTS/최신 인계/TASKS/실제 코드를 읽고 다음 우선 작업인 SAFE-01 UI를 선택했다.
`0e2605e`에 TxtReaderFlowTest 2개, 자체 fixture provider 3개 ID, mandatory manifest 29개,
device-check report 수집/검증을 구현하고 main에 commit/push했다. production 변경은 없다.

첫 `199e9b9` 기기 실행은 예외 클래스 이름 assertion으로 29개 중 1 FAIL, host NOT_RUN이었다.
공식 AndroidX 2.6.1 source의 typealias가 SQLException임을 확인해 native probe의 실제 타입과
진단을 비교하도록 1줄, device report checker의 기대 타입 1줄을 보완했다. 실패 원본은 보존했다.
local 48/53은 초기 입력이고 최종 test APK 재컴파일은 exit 0이다.
가역적인 query column rename은 native SQLite code 1을 발생시키며 borrowed DB close를 쓰지 않는다.
malformed locator는 원래 JSON을 보존해 fixture repair/UI retry한다. 전체 progress 9열/book/read_order,
두 관리 파일 hash, 오류 중 retry 2회, 실제 TextLayout 복원과 새 epoch save, finally 복원까지 검사한다.
local CI 48/53 exit 0, local device exit 78/BLOCKED_ENV, remote build 39000328372 48/53/3,
actual device 39000328613 ordinary 29/host 2 stages 모두 PASS다. 정확 명령/exit 및 source fingerprint는
`docs/evidence/m04-read-error-validation/commands.json`, `source.json`, `summary.json`을 따른다.
native XML/raw host proof/기기 report/workflow SHA/artifact digest를 보존하고 별도 검증했다.

검증 입력 112개 fingerprint `c04c2b9d4a5500a1e6fe63d4e3629f8f721d230b22ccb3d7d91a1ed9e84db4b8`, drift 0.
bookmark entity/DAO/UI가 없으므로 해당 gate는 NOT_RUN, 새 Astra 검토도 NOT_RUN이다.
M04 IN_PROGRESS를 유지한다. 다음 우선 작업은 실제 UI A/B 및 같은 책 rapid close/reopen이다.

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

검증 입력 fingerprint `891c14fab251cab7a4e864eb4a838dd195677fcce822a37fe608fcc84372c6ed`, source drift 0.
M05는 `/workspace/bookreader-mobile-m05`의 로컬 격리 worktree에서 구현 중이며 아직 이 M04 source의 기능이 아니다.

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

M05 최신 source fingerprint `e2d3caa89d74e7d946b3523389a362e381b803b86899c71edd25d5e22a35ae89`, drift 0. 정확 명령은 local-ci-reflow-fix/results.txt 및 각 remote workflow/results.txt를 따른다.

## 2026-10-17 M07 far seek와 archive 회귀 후속

- f7483e6 API36 49개 중 46 PASS/3 FAIL. 기존 M07 메뉴 seek와 신규 6회 원거리 왕복 seek는 PASS. 전체/host PASS 아님.
- 남은 실패: EPUB bounded service 미적용, DocumentsUI 항목 탐색 실패, malformed locator 재시도 중 SnapshotStateObserver 스레드 오류. 후자는 IO snapshot callback에서 scrollToItem 재측정이 실행된 원본 stack을 확보했다.
- Reader 인스턴스별 composition key와 Main dispatcher 강제 복귀를 추가했다. Readium 3.1.2 open()의 명시적 onCreatePublication hook으로 bounded service를 설치하고 실제 서비스 타입도 확인한다.
- Comic 100장 fixture를 40×20에서 1080×1600으로 강화하고 실제 allocation ≥1MiB를 요구한다. 이전 tiny fixture PASS는 새로운 이미지 크기 검증으로 승계하지 않는다.
- local test APK/app/module lint/EPUB unit: /workspace/scratch/m01/service-and-ui-dispatch-check.log. 새 원격 결과는 별도 기록 예정.

## 2026-10-17 commit acknowledgement 취소 회귀

- main 7eaa0f5 actual API36 49개 중 48 PASS/1 FAIL. M07 원거리 seek·UI 소유권·bounded EPUB search 5개·1080×1600 Comic 100장 decoder는 PASS다. 전체/host는 PASS 아님. Comic 최대 bitmap 1,728,000 bytes / 예산 13,421,772 bytes, emulator PSS 130,708→153,404 KiB이며 물리 성능 판정 아님.
- TXT 회전 native는 같은 anchor/epoch에 sequence만 다시 증가한 상태를 발견했다. DB commit 후 ack 대기를 취소하는 단위 fixture로 동일 문제를 먼저 FAIL 재현했다(`/workspace/scratch/m07/commit-ack-before.log`).
- writer mutex 안의 save/ack 전달 구간을 NonCancellable로 보유해 settling/reflow 취소가 성공 ack를 버리지 않게 했다. 새로운 event/record를 만들지 않고 ack를 마친 뒤 caller 취소/close가 진행된다. 기존 activation cancel gate는 변경하지 않았다. archive 공통 writer에도 적용하고 반복 입력 회귀를 추가했다.
- `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebugAndroidTest :androidApp:lintDebug` 후속 로그는 commit-ack-final.log. 새 실제 회전/host 결과는 별도 확인한다.
- bcc2081 EPUB 제품 branch remote build 64 Android/71 shared/13 EPUB/3 native ENOSPC PASS, native 51 중 50 PASS/1 검색 assertion FAIL. 검색 SDK 위치는 highlight 텍스트를 반드시 보존하지 않으므로 실제 progression/렌더 위치 확인으로 교정했다. 원본 결과 모두 보존한다.


## 2026-10-17 제품 후속·최종 패키지 준비·사용자 요청에 따른 세션 인계

사용자는 남은 계획 전체 진행 후 이번 세션은 현재 작업까지만 마무리하고 전달서를 만들도록 지시했다.
현재 main은 검증된 EPUB 22526d3 + 문서/실패 증거이며 Comic 후속은 codex/comic-product에서 보존한다.
9dfb884(후속 716ae03은 문서 merge) local `BOOKREADER_EVIDENCE_DIR=build/ci-contract ./scripts/ci-check.sh`
exit 0: Android unit 66/shared JVM 74/EPUB 16, assembleDebug/test APK/lint/schema 5 PASS.
[원본 XML·명령](evidence/m10-local-validation/source-9dfb884/summary.json)을 보존했다.
8a6a778은 실제 SQL migration 실패와 schema validation 실패를 만들고 두 번 재열어 전체 raw schema/row 보존을 검증한다.
9dfb884는 공통 취소형 seek, Comic return, 서재 grid, EPUB publisher style 기본값/opt-out 및 native 회귀를 추가했다.

c1ab039 `./gradlew :androidApp:assembleRelease :androidApp:bundleRelease` exit 0;
`python3 scripts/audit-apk.py ... --unsigned --source-sha c1ab03953876b5111a356f66a3ef05856d39f623 ...` exit 0.
[실제 APK/AAB hash](evidence/m10-package-audit/archive-c1ab039-artifacts.json), [정적 감사](evidence/m10-package-audit/archive-c1ab039-release.json).
서명 상태 NOT_SIGNED, 4 ABI ELF/ZIP 16KB 정렬·manifest PASS이며 최종 9dfb884 release 패키지는 NOT_RUN이다.

새 QA_MATRIX/PERFORMANCE/SECURITY/RELEASE_CANDIDATE_REPORT와 Comic 제품 보고서, ADR 후속을 작성했다.
M01 feasibility 기준은 과거 실제 probe 5/encoding 3 PASS로 DONE, M09/M10 IN_PROGRESS, M11 BLOCKED다.
다른 전체 milestone을 자동 완료하지 않았다. a5a7a31 native 53/1 FAIL 및 새 9dfb884 원격 미확인 결과는
[SESSION_HANDOFF](SESSION_HANDOFF.md)에 기록했다. 물리 기기/신규 독립 리뷰/서명·배포는 NOT_RUN이다.
