# 2026-10-24 제품 완성도 개선 — Phase 1 후보

사용자의 새 [개발 지침](reference/BookReaderM_Product_Improvement_Request_2026-10-24.md)을 읽고
[전체 감사](PRODUCT_IMPROVEMENT.md) 뒤 v0.5.1/code6 UX 정리를 구현했다. 서브에이전트는 사용하지 않았다.
작업 위치 `/workspace/BookreaderM`, 브랜치 `codex/product-quality-051`. main은790175b를 유지한다.
로컬 unit163/APK/test APK/lint/schema5 PASS, native63+host6/API는 새 후보 CI 확인이 필요하다.
기존 0.5.0 PASS를 새 후보의 기기 검증으로 사용하지 않는다. [로컬 명령·중간 실패](evidence/product-quality/phase1-local/commands.json).

기존 서명키·책·진행도·북마크·글꼴·설정·DB schema5를 유지했다. 공개 릴리스는 별도 승인 대상이다.
다음은 후보의 실제 API26/34/36 회귀·스크린샷 확인 후 Phase2 서재2.0이다.
각 shell에서 `source /workspace/toolchains/mobile-env.sh`를 적용하고 reset/clean·앱 삭제를 하지 않는다.

아래는 이전 0.5.0 완료 시점의 인계이며 당시 패키지·검증 근거를 보존한다.

# 2026-10-24 사용자 글꼴·굵기 작업 완료

사용자 요청의 배포 방안 Markdown, TTF·OTF 등록/선택/삭제, 보통/굵게를 구현했다. 서브에이전트 없이 진행했다.
저장소 `/workspace/BookreaderM`, 검증 source `4449f1e6094a5fb9546ac4ed4ea3de04c2f4b351`. 후속 커밋은 문서/증거다.
[사용 안내](READER_FONTS_0_5.md), [배포 방안](APK_DISTRIBUTION.md), [설치 안내](INSTALLABLE_APK.md)를 현재 기준으로 따른다.

Build40050209007 PASS164, device40052571291 API26/34/36 각각 native60+host6 PASS.
원본 XML/필수 사례/source clean/digest 및 host 실제 force-stop 후 PID 교체를 확인했다.
직전 제품 동일058d2b8도 전체 PASS다. 첫4449f1e 실행40050208939의 API34 쪽수30초 timeout과 API26 `cancelled`를 별도 보존했다.
테스트 timeout이나 위치 허용오차를 완화하지 않았고 재실행 통과를 원인 수정으로 부르지 않는다.
`docs/evidence/custom-fonts/`에 성공·실패·패키지·실제 화면 원본이 있다.

APK `/workspace/artifacts/BookreaderM/4449f1e/installable/BookreaderM-0.5.0-test-signed.apk`.
SHA256 `839d4e85c0bd8197005069e83d76fef99d5462f2845cf7ac48cc978282d2447c`.
같은 폴더의 ZIP은18,384,516 bytes, SHA256 `5482ccd3ff215b02c2e1ef21c7887ccb8181582d84e92117114a9ead0125fdae`.
기존 인증서/v2/v3/16KB 정렬/ZIP CRC/추출APK동일성 PASS. 실기기 설치와 대화 ZIP 다운로드는 미확인이다.
ZIP은 임시 전달 수단이며 20MB 초과 APK 미리보기 링크를 해결책으로 반복 안내하지 않는다.

업로드 루트 GET200과 달리 실제 APK 및 작은 체크섬 POST는 HTTP400 invalid request로 실패했다.
응답의 재전송 중단 안내를 따랐다. 0.5.0 draft ID422455175는 미게시·첨부0개이며 제목/본문에 첨부 대기를 표시했다.
0.4.0 draft ID422430819와 혼합하지 않는다. 공개 릴리스와 CI 배포 자동화는 미완료다.
후속 업로드 전 지원 경로·요청 계약·자격증명을 확인한다. `/workspace/scratch/prepare-font-apk-draft.py`를 그대로 재실행하지 않는다.

다음 세션은 git status/log와 AGENTS.md를 읽고 기존 JDK/SDK(`source /workspace/toolchains/mobile-env.sh`)를 사용한다.
책·진행도·원본·기존 서명키를 보존하며 요청 없는 reset/clean·강제 push·앱 삭제를 하지 않는다.
물리 기기 성능/전체 사용자 글꼴 corpus/정식 배포 서명·공개 출시는 자동 완료로 간주하지 않는다.

아래 내용은 이전 세션 이력이다.


# 2026-10-24 후속: APK 다운로드 미해결

## 2026-10-24 APK 다운로드 경로 — BLOCKED_ENV

사용자는 Android 설치가 아니라 대화의 APK 링크에서 파일 크기 제한이 발생했다고 확인했다.
기존 0.4.0 APK는 21,823,345 bytes이며 해시가 기존 검증값과 일치한다.
같은 폴더의 `BookreaderM-0.4.0-test-signed.zip`은 18,364,084 bytes다.
Python zipfile ZIP_DEFLATED level 9로 생성했고 CRC 및 압축 해제 후 APK SHA-256 동일성을 확인했다(exit 0).
앱 소스/서명키/기존 APK를 변경하지 않아 제품 테스트를 반복하지 않았다. 대화에서 ZIP 다운로드는 미확인이다.

GitHub 다운로드 초안 ID 422430819를 생성했으나 uploads.github.com HTTP CONNECT 403으로 첨부는 실패했다.
초안은 미게시·첨부 0개이며 제목/본문에 업로드 대기를 표시했다. 공개 출시하지 않았다.
환경 설정 초안에 기존 custom allowlist를 보존하며 uploads.github.com만 추가했고 saved/requires_publish=true를 확인했다.
설정의 사용자 저장·게시 후 실제 접속/자격증명을 다시 확인하고 기존 초안에 APK를 업로드한다.
다운로드 바이트의 SHA-256 검증 후에만 GitHub 링크를 설치 경로로 안내한다.
[기록](evidence/apk-download-2026-10-24/summary.json), 로컬 helper `/workspace/scratch/apk-download/create-draft.py`.
helper는 이미 만든 초안을 재생성하지 말고 저장된 release.json의 기존 ID로 이어간다.
실기기 설치 NOT_RUN.

# 후속 작업 전달서 — 0.4.0 완료, 2026-10-23

사용자 요청의 글꼴·깜빡임 보완·전환 선택·쪽수·서재 카드 작업을 완료했다. 처음부터 반복하지 않는다.
‘상태바’는 중앙을 탭하면 나타나는 하단 독서 현황을 뜻한다는 사용자 답변을 반영했다.
서브에이전트는 사용하지 않았다.

작업 위치 `/workspace/BookreaderM`, 브랜치 `main`.
검증 제품/테스트 소스 `d1570aa92edddb676be3c5bc311d53506f2e9ad9`; 후속 commit은 문서/증거다.
Build 39964576203의 161개, device 39964576436의 API26/34/36 각각 native 58 + host 6이 모두 PASS다.
소스/원본 XML/필수 사례/artifact digest 및 실제 force-stop 후 PID 교체를 대조했다.
[동작과 최종 결과](READER_FEATURES_0_4.md), [설치 안내](INSTALLABLE_APK.md)를 현재 기준으로 따른다.

설치 파일 `/workspace/artifacts/BookreaderM/d1570aa/installable/BookreaderM-0.4.0-test-signed.apk`.
SHA-256 `cf74e8ea6ede2fa360ddb7d75210b0822b87aa4e512005e78d2a0512582dbdf2`.
기존 개발용 인증서 유지, v2/v3 서명·정적 감사 PASS. 실기기 설치는 NOT_RUN이다.
중간 후보 bd6b456/0aeba65의 APK 대신 이 최종 파일을 안내한다. unsigned APK/AAB는 직접 설치용이 아니다.

다음 세션은 git 상태/최근 log/AGENTS.md와 위 최종 문서를 먼저 확인한다.
`source /workspace/toolchains/mobile-env.sh`로 기존 도구를 사용한다.
기존 서명키/앱/책/진행도/캐시를 보존하며 요청 없는 reset/clean·강제 push·앱 삭제를 하지 않는다.
남은 출시 조건은 실기기 성능/TalkBack/16KB OS/OEM backup/전체 corpus/독립 리뷰/정식 서명·출시 승인이다.
아래는 이전 전달서 이력이며 현재 소스·패키지·검증 상태는 위 기록을 우선한다.

# 후속 작업 전달서 — 2026-10-23

독서 화면 디자인 요청은 구현·검증·main 반영까지 완료했다. 다음 세션에서 같은 작업을 처음부터 반복하지 않는다.
사용자가 요청한 대로 이번 작업에 서브에이전트는 사용하지 않았다.

작업 위치 `/workspace/BookreaderM`, 브랜치 `main`.
검증 제품/테스트 소스 **`c2383259eb1c15794e4c1f6ecfdbb198c6c6f968`**, 이후 commit은 문서/증거다.
[디자인 결과·스크린샷·CI](READER_DESIGN_2026-10-23.md)와 [설치 APK 0.3.0](INSTALLABLE_APK.md)를 현재 기준으로 따른다.

Build 39946827381의 159개, device 39946827437의 API26/34/36 각각 native 57 + host 6이 모두 PASS다.
원본 XML/필수 사례/소스 SHA/ZIP digest와 실제 프로세스 PID 교체를 대조했다.
이전 실패 원본도 유지했다. 검증 결과는 `docs/evidence/reader-design/verified-c238325/`에 있다.

설치 파일은 `/workspace/artifacts/BookreaderM/c238325/installable/BookreaderM-0.3.0-test-signed.apk`.
SHA-256 `5f23b00fe084b27975d283a5892faa87328f124bfe4b34e5e671f4a55baa7cca`.
기존 개발용 인증서 유지, v2/v3 서명/정적 감사 PASS. 실제 기기 설치는 NOT_RUN이다.
서명키를 저장소에 넣지 않으며 unsigned APK/AAB를 직접 설치 파일로 안내하지 않는다.

다음 세션은 `git status --short`, `git log -3 --oneline`, `AGENTS.md`, 위 결과 문서를 확인한다.
`source /workspace/toolchains/mobile-env.sh`로 기존 JDK/SDK를 사용한다. 연결/온라인 CI 다운로드는 복구된 상태다.
요청 없는 reset/clean·강제 push·기존 앱 삭제를 하지 않는다.
남은 출시 조건은 실기기 성능/TalkBack/16KB OS/OEM backup/전체 corpus/독립 리뷰/정식 서명·출시 승인이다.
실제 자원과 선행 조건을 확보해 후속 요청에 맞게 진행하며 자동 완료로 간주하지 않는다.

아래는 이전 전달서 이력이다. 이전 소스/패키지/검증 대기 상태보다 위 최종 기록을 우선한다.

# 후속 작업 전달서 — 2026-10-22

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


작업 위치는 `/workspace/BookreaderM`, 현재 브랜치는 `main`이다.
제품/테스트 기준 SHA는 `bb0f53787d2e26a0fcc321af31cf033d935eaed6`; 이후 commit은 문서/증거만 포함한다.
사용자가 승인한 재개 순서(CI → 수정 → 전체 검증 → main 통합 → unsigned 패키지 → 문서)를 완료했다.
[최종 보고서](RESUME_COMPLETION_2026-10-22.md)에 정확한 CI run, 명령, hash 및 원본 경로가 있다.

다음 세션은 `git status --short`, `git log -3 --oneline`, `AGENTS.md`와 최종 보고서를 확인한다.
`source /workspace/toolchains/mobile-env.sh` 후 필요한 검증만 실행한다. 검증된 파일·캐시와 사용자 변경을 보존한다.
기존 체크아웃을 사용하고 요청 없는 worktree 생성·reset/clean·강제 push를 하지 않는다.

다음 개발은 TASKS의 잔여 gate에서 환경과 선행 조건을 충족하는 항목을 고른다.
실기기·서명·배포 등 제공되지 않은 자원이나 별도 승인을 자동 완료하지 않는다.
TXT 측정 재진입이 재발하면 원인을 조사하며 skip/예외 무시/허용 오차 완화로 우회하지 않는다.

<details>
<summary>이전 전달서 이력 — 아래 대기·미통합 상태는 당시 기록</summary>

# 다음 세션 작업 전달서 — 2026-10-17

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

## 2026-10-18 현재 검증 상태

제품 수정 `98d7f0f`는 EPUB 검색 요청을 화면 교체 동안 보존한다.
원본 확인 결과 build는 Android 66/shared 74/EPUB 16/ENOSPC 3 PASS,
API36은 native 54 + TXT/EPUB/Comic host 6 PASS다. API26/34는 각각 native 1 FAIL이며 host는 NOT_RUN이다.
문서 조회 시점을 보완한 `1622bb6`의 build 및 API34/36 각각 native 54 + host 6은 PASS다. API26은 키보드 전후 viewport가 달라 .03 progression 비교가 실패했고, 동일 viewport에서 비교하도록 후속 테스트를 보완 중이다.
과거 artifact 접근 차단은 이번 원본 다운로드에서 해소됐으며 ZIP digest를 확인했다.
[현재 분석·원본·후속 CI](EPUB_REFLOW_NAVIGATION.md)를 우선한다. 아래 절은 이전 시점 이력이다.
main 통합과 출시 승인은 미완료다.

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

## 이번 세션의 종료 지시

사용자 최신 지시: **“지금 하고있는 작업까지만하고 다음 세션에 전달하기 위한 작업전달서를 만들어줘”**.
이에 새 기능 작업을 멈추고 현재 로컬 검사·실패 증거·브랜치·CI 정보를 보존했다.
기존 목표는 M04 이후 남은 계획 진행이지만, 이번 세션에서 추가 구현을 계속하지 않는다.
다음 세션에서 사용자가 재개하면 아래 순서로 이어간다. 전체 계획/출시는 아직 완료가 아니다.

## 저장소와 작업 위치

- URL: https://github.com/LaceyLuv/BookreaderM.git
- 기본 폴더/브랜치: `/workspace/bookreader-mobile`, `main`.
- 실제 후속 제품 작업: `/workspace/bookreader-mobile-comic-product`, `codex/comic-product`.
- 마지막 제품 코드: **9dfb884b40b50163e001612d55ffb961898e93ad**.
- 그 뒤 **716ae03**은 main 문서/기존 실패 증거 merge이며 제품 코드 변경이 없다. 전달서/증거 commit이 뒤따른다.
- main의 제품 코드 기준은 **22526d3**(EPUB 통합). main 3940a35/017d0fa 및 후속은 문서/증거다.
  **Comic·precise EPUB 복원 후속·서재 그리드·archive seek는 아직 main 제품 코드에 통합하지 않았다.**
- 기존 폴더/사용자 변경/이전 evidence/worktree를 보존했다. 삭제·reset/clean·force push를 하지 않는다.

시작할 때 `/AGENTS.md`, `/workspace/AGENTS.md`, 저장소 `AGENTS.md`, `docs/CONTEXT_HANDOFF.md`, 이 문서를 읽고
각 폴더의 `git status --short`, `git log -3 --oneline`, `git worktree list`를 확인한다.
이번 세션에서 상위 두 AGENTS.md는 없었고 저장소 지침을 적용했다.
새 sub-agent는 사용하지 않았다. 과거 문서의 모델 검토 이력을 이번 소스의 독립 검토로 간주하지 않는다.

기존 worktree(모두 보존):

| 폴더 suffix | 브랜치 / 코드 기준 |
|---|---|
| `-reader` | codex/archive-reader, 22526d3 |
| `-comic` | codex/comic-engine, 1ee3a9e |
| `-archive` | codex/archive-import, f0885bf |
| `-epub` | codex/m01-epub, e65ec89 |
| `-large` | codex/txt-large-validation, 695f29f |
| `-m07` | codex/m07-controls, 8022939 |
| `-m06` | codex/m06-tools, de6f66e |
| `-m05` | codex/m05-pages, 0767810 |

## 검증된 상태

1. main 22526d3: API36 ordinary **52 PASS + TXT 외부 host 2단계 PASS**. 이전 14c8c42는 49+2 PASS.
   TXT 저장 acknowledgement가 취소되어 같은 위치를 다시 쓰던 문제를 수정했고 실제 회전/STOP 회귀가 통과했다.
   이 host 2개를 EPUB/Comic 외부 종료 검증으로 바꾸어 부르지 않는다.
2. 후속 9dfb884 local full CI: **Android unit 66 / shared JVM 74 / EPUB unit 16**, 실패/오류/skip 0.
   APK/test APK compile, app/module lint, Room schema 5 PASS. 명령 exit 0.
   `docs/evidence/m10-local-validation/source-9dfb884/`에 XML·필수 목록·전체 로그·summary가 있다(제품 브랜치).
3. 8a6a778의 새 실제 DB 테스트 2개: migration SQL 실패, schema validation 실패 뒤 두 번 재열어
   user_version/전체 sqlite_master/책·진행도·북마크·설정·journal raw row가 그대로임을 검증했다.
4. c1ab039 unsigned release APK/AAB 빌드 PASS. APK 정적 감사 PASS(4 ABI, native 8개 ELF/ZIP 16KB 정렬,
   allowBackup=false, cleartext=false, 요청 권한 ACCESS_NETWORK_STATE만 확인).
   **NOT_SIGNED이며 release 기기 실행/배포 승인 PASS가 아니다. 9dfb884 최종 release 패키지도 아직 아니다.**
5. 964f8cc의 Comic 제품 test는 PASS였지만 같은 전체 suite는 EPUB 1 FAIL이었다.
   긴 PNG/WebP·EXIF·100장 decoder의 API36 실제 결과는 이전 7eaa0f5에서 PASS.
   100장 decoder 순회를 빠른 100장 UI 탐색이나 물리 PSS/GPU PASS로 확대하지 않는다.

## 최우선 미해결: EPUB precise 복원 후속

- 964f8cc run **39015606692**: ordinary 53 중 52 PASS/1 FAIL.
  `managedEpubTocSearchBookmarksAndRecreationPreserveFullLocator`의 글자 크기 변경 뒤
  `assertParagraphVisible("two", "p179")`가 false였다. 기존 progression 근접만으로 실제 문단 보존이 보장되지 않았다.
- a5a7a31은 page callback의 progression-only Locator 대신 검색 결과의 text/fragment를 보존하고,
  일반 이동은 공식 `firstVisibleElementLocator()`의 CSS selector/text를 결합한다.
  captureJob은 host/generation/READY를 확인하고 교체·종료 때 취소한다. 4,096자 초과 block highlight는
  exact CSS selector를 유지한 채 생략한다. 사용자 검색 위치·return·bookmark도 precise locator를 사용한다.
- a5a7a31 run **39016613293**: ordinary **53 중 52 PASS/1 FAIL**, host **NOT_RUN**.
  실패는 같은 제품 test의 `assertRenderedNear`다:
  **expected progression 0.9264910536779324, actual 0.9565038378966562, tolerance 0.03**.
  이번에는 다음 실제 문단 assertion에 도달하기 전에 숫자 비교가 실패했다.
  저장된 text anchor와 viewport 문단, callback 순서를 먼저 관찰해야 한다.
  **허용 오차만 넓히거나 assertion을 삭제해서 녹색으로 만들지 않는다.**
  reflow 후 progression이 바뀌어도 precise 문단이 보존되는 정상 상황인지, 여전히 실제 복원 오류인지 미확정이다.
- 증거: main/제품 브랜치의 `docs/evidence/m01-epub-validation/remote-a5a7a31-device-failed/`.
  로컬 원본 `/workspace/scratch/m01/device-precise/`, ZIP 및 workflow/artifacts JSON도 보존했다.
- 9dfb884는 publisherStyles 기본 true/명시적 opt-out, archive seek/return, 그리드를 추가했고
  EPUB/Comic 제품 test에 slider 취소·접근성 SetProgress·return 검증을 붙였다. 최신 native 결과가 우선이다.

## 원격 CI — 세션 종료 시 다시 조회할 항목

| run ID | 소스 / 범위 | 마지막 확인 |
|---|---|---|
| **39017137521** | 9dfb884 build | workflow SUCCESS, artifact 미회수/미검증 |
| **39017137527** | 9dfb884 API26/34/36 device | IN_PROGRESS |
| **39016647039** | c1ab039 API26/34/36 device | 전체 FAIL; API26 job 117350023245 SUCCESS, API34 117350023052/API36 117350023250 FAIL; artifact 미회수 |
| 39016613335 | a5a7a31 build | PASS, artifact 미회수 |
| 39016613293 | a5a7a31 device | FAIL, 위 원본 회수/검증 완료 |
| 39016454188 | 3a629a4 build | PASS, artifact 미회수 |
| 39016454202 | 3a629a4 device | FAIL, artifact 미회수/실패 원인 미확인 |

마지막 [CI 상태 스냅샷](evidence/session-handoff-2026-10-17/ci-status.json)을 보존했다. 새로 완료된 job의 SUCCESS는 metadata이며 아직 artifact/host proof를 직접 검증하지 않았다.
원격 작업은 취소하지 않았다. 최신 결과를 먼저 조회하고 모든 실패를 보존한다.
matrix artifact 이름은 **mobile-device-evidence-api-26/34/36**이며 이전에는 mobile-device-evidence 하나였다.
전체 workflow FAIL이어도 각 API job을 따로 판정한다. ZIP digest, source.log, source별 required-tests.json,
XML case identity/count/failure/error/skip, 실제 host PID 종료/새 PID를 확인한다.
ordinary 기준은 현재 53개, host는 TXT 2 + EPUB 2 + Comic 2 = **총 6단계**다.

CI 확인에는 GitHub connector를 사용했다. 환경 `GH_TOKEN`은 만료되어 `gh api/run`이 401이었지만
`git push origin <branch>`는 성공한다. 토큰을 출력/복사하지 말고 인증 실패 때문에 새 비밀키를 파일에 남기지 않는다.
connector: `github_fetch`로 `/actions/runs/<id>` 및 `/jobs`, `github_fetch_workflow_run_artifacts`,
`github_download_workflow_artifact`. 다운로드의 signed URL은 출력하지 않고 ZIP으로 저장한다.

회수 helper `/workspace/scratch/m01/retain.py`는 다음 형태다:

```bash
python3 /workspace/scratch/m01/retain.py device <prefix>
python3 /workspace/scratch/m01/retain.py build <prefix>
```

`/workspace/scratch/m01/<prefix>.zip`, 추출 폴더, `-workflow.json`, `-artifacts.json`을 입력으로 쓴다.
제품 브랜치가 아니라 **main evidence 폴더에 출력**하므로 두 worktree 상태를 확인한다.
TXT/EPUB/Comic host 모두 검증하도록 확장했다. matrix는 선택 artifact 하나와 해당 job conclusion,
`variant: "api26"`처럼 구분한 metadata를 준비해야 한다. 원래 전체 workflow/job 결과도 보존한다.

## 이번 변경의 위치

- `androidApp/.../epub/EpubReaderController.kt`: SDK ownership, bounded search/settings, precise anchor, seek.
- `EpubHostFragment.kt`, `EpubReaderScreen.kt`: stable Fragment host, 실제 표시/복원, 메뉴/TOC/bookmark/외부 링크 확인.
- `androidApp/.../comic/ComicReaderController.kt`, `ComicReaderScreen.kt`: 한 장/연속/웹툰, RTL/fit/zoom,
  실제 render 성공 후 저장, entryKey anchor, 취소형 seek 및 일회성 return.
- `ReaderMenuDialog.kt`: 기존 TXT slider의 Drag Stop/Cancel 처리를 `ReaderPositionSlider`로 공유.
- `TxtReaderScreen.kt`/ReaderTools/EPUB/Comic 화면: delegated State를 LazyListScope 안에서 다시 읽지 않고
  composition의 한 상태를 캡처한다(361b3f9의 TXT 목록 측정 FAIL 후 보완). 새 whole-suite 결과 필요.
- `shared/.../ReaderSettings.kt`: 책별 Comic mode/direction/fit, EPUB publisher style 기본 true.
- `epubAndroid/.../EpubResourceBudget.kt`: markup 전체/보호 출력 32MiB, spine 2,048개,
  반복된 spine resource bytes까지 합산하여 Readium eager positions 작업량을 제한.
- `EpubContentPolicy.kt`: 외부 링크 참조 8,192자; missing browser 오류를 제품 화면에 표시.
- `ui/BookReaderApp.kt`: 서재 adaptive grid, 세 포맷 dispatch, 오프라인 notices/지원 형식/데이터 손실 안내.
- `.github/workflows/mobile-device-tests.yml`: API26/34/36, fail-fast=false, 포맷별 host 증거.

## 빌드·검사 명령 및 증거

```bash
cd /workspace/bookreader-mobile-comic-product
git status --short
source /workspace/toolchains/mobile-env.sh
BOOKREADER_EVIDENCE_DIR=build/ci-next ./scripts/ci-check.sh
git diff --check -- . ':!docs/evidence'
```

JDK17.0.16+8/SDK36/build-tools35/Gradle8.13/Kotlin2.2.20/AGP8.11.1/Readium3.1.2.
기존 proxy/truststore env를 보존한다. 모든 의존성 버전은 docs/DEPENDENCIES.md를 따른다.
로컬 `adb devices -l`은 연결 기기 0이다. device-check는 기기 없으면 exit 78/BLOCKED_ENV다.
실행하지 않은 native/물리 검사를 PASS로 기록하지 않는다.

최신 로컬 전체 로그: `/workspace/scratch/m01/product-contract-final-ci.log` (exit 0).
관련 로그: product-contract-check.log, product-contract-native-compile.log, migration-failure-check.log,
reader-snapshot-budget-check.log, epub-precise-anchor-check.log.

c1ab039 release 보존본:

- `/workspace/scratch/m01/release-c1ab039/androidApp-release-unsigned.apk`
  SHA256 `ca21d59cfc0a3db2247d0f378c29aa8d58985bdb8c8d125e960099a51eb0125b`
- `/workspace/scratch/m01/release-c1ab039/androidApp-release.aab`
  SHA256 `f21942da47d7c61831315898e0d94e7d8ad19fef3eff55048997c3fc56e3d631`
- `docs/evidence/m10-package-audit/archive-c1ab039-{release,artifacts}.json`.
  AAB `jarsigner -verify`는 exit 0이지만 **jar is unsigned**다. exit code만으로 signed PASS라 하지 않는다.

## 재개 순서

1. 최신 9dfb884 build/세 API device 결과와 artifact를 확인한다. 이전 FAIL은 유지한다.
2. EPUB 실제 문단/SDK precise locator/숫자 progression 관계를 관측해 위 미해결 실패를 수정한다.
   API26/34의 별도 오류가 있으면 실제 로그에 근거해 고친다. assertion 완화·skip으로 우회하지 않는다.
3. 전체 ordinary 및 세 포맷 host 6단계가 통과한 제품 소스를 main과 통합한다.
   main의 최신 문서/evidence와 충돌하면 모두 보존한다. 강제 push나 원격 PR merge는 하지 않는다.
4. 최종 소스에서 unsigned release APK/AAB를 재생성하고 정확 hash/정적 감사를 남긴다.
   최종 debug native 실행과 release 실행을 구분한다.
5. 전달서·TASKS/WORKLOG/README 및 QA/성능/보안/출시 보고서의 미확인 항목을 실제 결과로 갱신한다.

## 남은 전체 gate

제품 브랜치의 `QA_MATRIX.md`, `PERFORMANCE.md`, `SECURITY.md`, `RELEASE_CANDIDATE_REPORT.md`,
`M09_COMIC_PRODUCT.md`가 현재 감사 문서다. M01 feasibility DONE, M04–M10 전체는 아직 잔여 검증,
M11 BLOCKED다. 기존 M02/M03의 TXT 내부 알파 완료 범위를 세 포맷 전체 출시로 확대하지 않는다.

물리 release 성능(30회 p95), 실제 fsync/전원 장애, PSS/GPU/메모리 압박·빠른 100장 UI 탐색,
실제 TalkBack/전체 OS·OEM/16KB OS 실행, OEM backup/D2D, 전체 포맷별 SAFE/공격 corpus,
새 독립 리뷰, 최종 서명·설치/업데이트 검증은 아직 미완료다.
배포/스토어 제출/서명키 업로드는 수행하지 않았다. 물리 환경이나 별도 승인이 필요한 항목을
자동 완료하지 말고 구체적인 blocker로 유지한다.

</details>
