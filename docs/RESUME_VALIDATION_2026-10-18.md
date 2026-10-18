# 2026-10-18 재개 검증 결과

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

### 이번 실행 명령과 판정

```bash
source /workspace/toolchains/mobile-env.sh
BOOKREADER_EVIDENCE_DIR=build/resume-final-ci ./scripts/ci-check.sh
./scripts/enospc-check.sh
./gradlew --no-daemon --console=plain :androidApp:assembleRelease :androidApp:bundleRelease
python3 scripts/audit-apk.py androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk \
  --source-sha 8ca750efe07f12c5f7f0d79980868919ba9f88b7 --unsigned \
  --output /workspace/scratch/resume-ci/release-audit-8ca750e.json
jarsigner -verify androidApp/build/outputs/bundle/release/androidApp-release.aab
```

위 명령은 모두 exit 0이다. jarsigner 출력은 `jar is unsigned`이며 서명 PASS가 아니다.
APK SHA256 `c821bdbb4db77a9c9f5ce6af04eabd961ce34f8e07048bde3e06678a4198c0a9`,
AAB SHA256 `b89428e7cbe2ea8fdfd99f551528f00edf77898e3d1239c3909bc5e8f2620554`다.
APK 감사는 4 ABI, native 8개 ELF/ZIP 16KB 정렬, backup/cleartext 비활성 및 요청 권한을 검사했다.
이는 실제 16KB OS 실행이나 release 기기 검증을 대신하지 않는다.

최신 device [39100038799](https://github.com/LaceyLuv/BookreaderM/actions/runs/39100038799)는 완료/FAIL이다.
API26 check annotation은 일반적인 `/usr/bin/sh` exit 1만 제공하므로 assertion 원인을 추정하지 않는다.
다음 작업은 두 새 호스트가 적용된 환경에서
`python3 /workspace/scratch/resume-ci/fetch.py 39100038796 39100038799 --download`를 실행해
ZIP digest/source/필수 case/관찰 JSON/실패 XML을 확인하는 것이다.
확인된 원인만 수정하고 세 API ordinary·host 검증이 통과한 뒤 main 통합을 진행한다.
현재 main `d775783`은 제품 브랜치의 조상이지만 실패가 남아 fast-forward를 실행하지 않았다.

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

사용자가 후속 개발 재개(CI 확인 → 오류 수정 → 검증)를 승인했다.
현재 경로는 `/workspace/BookreaderM`, 브랜치는 `codex/comic-product`다.
제품 코드 기준은 `9dfb884`, 관찰 코드를 포함한 검증 소스는
`8bced5f4ff3136af2dc002d3c457397b7853bfe6`이다.

## 확인된 결과

- **PASS**: 로컬 baseline Android unit 66/shared JVM 74/EPUB unit 16, APK/test APK/lint/schema 5.
  `BOOKREADER_EVIDENCE_DIR=build/resume-baseline-ci ./scripts/ci-check.sh`, exit 0.
- **PASS**: 실제 tmpfs ENOSPC 3개 및 정리, `./scripts/enospc-check.sh`, exit 0.
- **PASS**: 관찰 코드의 test APK compile/lint, exit 0. 기존 progression .03/문단 표시/DB 불변 검증은 유지했다.
- **PASS (GitHub metadata)**: [build 39098185637](https://github.com/LaceyLuv/BookreaderM/actions/runs/39098185637).
- **PASS (GitHub metadata)**: [device 39098185673](https://github.com/LaceyLuv/BookreaderM/actions/runs/39098185673), API26/34/36 모든 job success.
  각 job의 instrumentation 및 evidence upload step도 success다. 상세 native XML/host proof/관찰 JSON을 직접 검증한 결과와 구분한다.
- **BLOCKED_ENV**: 원격 artifact ZIP 다운로드. API metadata는 읽히지만 Azure redirect 연결이 프록시 HTTP 403으로 실패한다.
  새 device artifact는 `productionresultssa11.blob.core.windows.net`, build는 `productionresultssa12.blob.core.windows.net`다.
  과거 build의 `productionresultssa4.blob.core.windows.net` 및 `api.github.com`과 함께 환경 설정 초안에 저장했다.
  runtime custom allowed_hosts는 비어 있고 state는 unknown이었다. 초안 저장이 실행 중 정책 적용을 뜻하지 않는다.
- **BLOCKED_ENV**: KVM 없는 로컬 API26 소프트웨어 에뮬레이터 실행. 부팅은 완료했으나 APK install commit의
  `ShellCommandUnresponsiveException`으로 Gradle exit 1, native XML tests=0이다. 앱 assertion FAIL이나 PASS가 아니다.
  API36 소프트웨어 부팅도 완료되지 않아 앱 검증에 사용하지 않았다. 두 에뮬레이터는 종료했다.

로컬 선택 실행 명령:

```bash
source /workspace/toolchains/mobile-env.sh
ANDROID_SERIAL=emulator-5556 ./gradlew --no-daemon --console=plain \
  :androidApp:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=org.bookreader.mobile.epub.EpubReaderFlowTest#managedEpubTocSearchBookmarksAndRecreationPreserveFullLocator' \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

## 미해결 및 다음 절차

이번 변경은 관찰 수집이다. 제품 복원 오류를 수정했다고 주장하지 않는다.
이전 `9dfb884` API26 실패와 `a5a7a31` EPUB 실패 원인은 아직 확인되지 않았다.
새 관찰은 실행 타이밍에도 영향을 줄 수 있으므로 이번 성공만으로 간헐적 문제가 해소됐다고 판단하지 않는다.
코드 점검에서는 설정 저장이 비동기인데 테스트의 `epub_ready` 대기가 이전 READY를 볼 가능성이 있다.
이는 미확정 가설이며 설정 적용/host 교체/viewport 관찰을 대조한 뒤 별도 수정한다.

1. 환경 설정에서 저장된 네트워크 초안을 검토·저장·게시하고 새 runtime에 적용됐는지 확인한다.
2. `/workspace/scratch/resume-ci/fetch.py 39098185637 39098185673 --download`로 ZIP을 회수한다.
   metadata digest와 ZIP SHA256, source.log의 정확 SHA, required-tests 목록과 native XML identity/count/failure/error/skip을 확인한다.
3. ordinary 예상 53개와 TXT/EPUB/Comic host 예상 총 6단계의 PID 종료/새 PID 증거를 각 API별로 검증한다.
   현재 원본 미검증 상태이므로 이 수치를 실제 통과 건수로 기록하지 않는다.
4. `epub-restore-observations.log`의 search/recreation/font-size/reopen Locator/viewport/p179를 비교하고,
   이전 실패 artifact도 회수해 실제 복원 문제 또는 테스트 대기 문제를 판별한다.
5. 확인된 문제만 수정·회귀 검증한다. main 통합은 상세 증거 확인 뒤 별도로 진행한다.

로컬 원본은 [evidence](evidence/archive-resume-validation/), 최신 상태는
[resume-status-8bced5f.json](evidence/archive-resume-validation/resume-status-8bced5f.json)에 있다.
이전 실패 증거 및 원래 `work` 브랜치는 보존했다. main 변경/merge/출시는 하지 않았다.
