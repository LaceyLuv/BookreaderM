# 2026-10-18 재개 검증 결과

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
