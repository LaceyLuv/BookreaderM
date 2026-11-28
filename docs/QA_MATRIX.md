# Android 검증 매트릭스 — 2026-10-17

## 2026-11-28 최신 — `0.8.2` (code 14) 글자 굵기 세분화 조절(100~900 · 50 단위)

- [작업 기록](WORKLOG.md), [최신 인계](SESSION_HANDOFF.md), [설치 안내](INSTALLABLE_APK.md)를 우선한다.
- `0.8.2`(versionCode 14, Room 9, 14 테이블 유지) 기준 검증 코드 `faa4023b1a47abb88b01c1a6b04b9f7418b883a1` → `main` 병합 `3cf5558ff3914833c7e23cd14624b6b7a81a2195`([PR #15](https://github.com/LaceyLuv/BookreaderM/pull/15)):
  - 원격 빌드·단위·실제 ENOSPC(`BookReader Mobile Checks` PR `46139684473`, `15261037677` · Push `46139680555`): 원본 XML **231/0/0/0 PASS**(`androidApp` 87, `shared` 122, `epubAndroid` 17, 실제 8 MiB tmpfs `enospc` 5 + `assembleDebug`·`lintDebug` `warningsAsErrors=true`·`assembleDebugAndroidTest`·Room 9).
  - 원격 기기 매트릭스(`BookReader Mobile Device Tests` PR `46139684489` · Push `46139680540`, 1차 시도 3/3 PASS): API 26(`15261846632`)·API 34(`15260904114`)·API 36(`15261594286`) 각각 원본 XML **connected 85/0/0/0 + host 8/0/0/0 PASS**(5개 외부 `force-stop` `absent`→PID 교체 확인, 실패·오류·건너뜀 0).
  - 신규 필수 검증 4건(`ReaderSettingsRepositoryTest` +1, `FontFilePolicyTest` +1, `TxtReaderFlowTest` +1, `EpubReaderFlowTest` +1)을 `scripts/required-tests.json`에 등록했다(`shared-jvm` 122, `android-unit` 87, `android-device` 85).
  - 서명 Release APK(`46141382458`, 아티팩트 `15262215920`, `BookreaderM-0.8.2-code14-signed.apk`, `sha256:bacd2ad3b3fe516097da3e80362eb833daa0fd259fc7d3b45fcdd428e444f830`)의 v2/v3·동일 인증서(`c86301d1...`)·16KB ELF/ZIP·권한 정적 감사 및 재다운로드 대조 PASS.
- 실제 스마트폰 업데이트 설치·실기기 가변 글꼴 체감/성능·TalkBack·16KB 실제 OS·OEM 백업·전체 corpus·독립 리뷰·공개 출시 승인은 여전히 `NOT_RUN`/`BLOCKED`다. 아래 절은 이전 시점 이력이다.

## 2026-11-28 이전 — `0.8.1` (code 13) 집중 모드·페이지 좌우 스와이프·페이지 넘김 속도 개선

- [작업 기록](WORKLOG.md), [최신 인계](SESSION_HANDOFF.md), [설치 안내](INSTALLABLE_APK.md)를 우선한다.
- `0.8.1`(versionCode 13, Room 9, 14 테이블 유지) 기준 검증 코드 `4e312ca59a47d1082383f8ae8efa575d46630686` → `main` 병합 `75dbebebbae35f826f79ecd7dc361a40eda25c68`([PR #13](https://github.com/LaceyLuv/BookreaderM/pull/13)):
  - 원격 빌드·단위·실제 ENOSPC(`BookReader Mobile Checks` PR `46107229772`, `15248506638` · Push `46107225533`): 원본 XML **229/0/0/0 PASS**(`androidApp` 86, `shared` 121, `epubAndroid` 17, 실제 8 MiB tmpfs `enospc` 5 + `assembleDebug`·`lintDebug` `warningsAsErrors=true`·`assembleDebugAndroidTest`·Room 9).
  - 원격 기기 매트릭스(`BookReader Mobile Device Tests` PR `46107229837` · Push `46107225513`, 3/3 PASS): API 26(`15248212952`)·API 34(`15248586278`)·API 36(`15248786346`) 각각 원본 XML **connected 83/0/0/0 + host 8/0/0/0 PASS**(5개 외부 `force-stop` `absent` 확인, 실패·오류·건너뜀 0).
  - 신규 필수 기기 검증 3건(`ReaderGestureSurfaceTest.horizontalSwipesTurnPagesInBothDirectionsEvenAtRelaxedSpeedAndNaturalAngle`, `TxtPageEngineTest.prefetchedPagesHitCacheWithoutExtraReadsAndPreserveExactBoundaries`, `TxtReaderFlowTest.focusModeToolbarToggleAndHorizontalPageSwipesPreserveProgress`)을 `scripts/required-tests.json`에 등록했다(`android-device` 80 → 83).
  - 서명 Release APK(`46108752293`, 아티팩트 `15248324741`, `BookreaderM-0.8.1-code13-signed.apk`, `sha256:44ffe0ec36b6e97a5c101ce5662eedbce2e537e032925708a605c1be7ca0e4e6`)의 v2/v3·동일 인증서(`c86301d1...`)·16KB ELF/ZIP·권한 정적 감사 및 재다운로드 대조 PASS.
- 실제 스마트폰 업데이트 설치·실기기 체감 프레임/지연·TalkBack·16KB 실제 OS·OEM 백업·전체 corpus·독립 리뷰·공개 출시 승인은 여전히 `NOT_RUN`/`BLOCKED`다. 아래 절은 이전 시점 이력이다.

## 2026-11-28 이전 — Phase 4-2 (Issue #3) 책·글꼴 원본 선택 백업 + 중단/ENOSPC 재개 커밋

- [작업 기록](WORKLOG.md), [최신 인계](SESSION_HANDOFF.md), [ADR 0013](adr/0013-resumable-asset-backup.md)을 우선한다.
- `0.8.0-dev`(versionCode 11, Room 9, 14 테이블 유지) 기준 검증 코드 `a835cf9b8c1bdabb2a06cb55d8f455360dcc46da`:
  - 원격 빌드·단위·실제 ENOSPC(`BookReader Mobile Checks` Push `46085838946`, `15238429512`): 원본 XML **229/229 PASS** (`androidApp` 86, `shared` 121, `epubAndroid` 17, 실제 8 MiB tmpfs `enospc` 5 + compile·lint·Room 9; `ENOSPC_BACKUP_PASS` 2건 + `ENOSPC_PASS` 3건 확인).
  - 원격 기기 매트릭스(`BookReader Mobile Device Tests` Push `46085838933`, 1차 시도 3/3 PASS): API 26(`15239536935`)·API 34(`15239079959`)·API 36(`15239831575`) 각각 원본 XML **connected 80/80 + host 8/8 PASS** (TXT/EPUB/Comic/Batch/Backup Asset 5개 외부 `force-stop` `absent`→PID 교체 직접 확인, 실패·오류·건너뜀 0).
  - 신규 필수 검증 9건(`BackupAssetCoordinatorTest` 3, `BackupAssetEnospcTest` 2, `ReadingBackupFileTest` v2 2, `ReadingBackupFlowTest` v2 E2E 1, `BackupAssetHostProcessTest` 외부 `force-stop` 1)을 `scripts/required-tests.json`에 등록했다.
- 실기기 대용량 백업 성능·TalkBack·16KB 실제 OS·OEM 백업·전체 corpus·독립 보안/성능 리뷰·서명/공개 출시 게이트는 여전히 `NOT_RUN`/`BLOCKED`다. 아래 절은 이전 시점 이력이다.

## 2026-11-27 최신 — 0.8.0-dev(Room 9) + UI 통합(PR #5) + Issue #2(PR #6) + Compose 스레드 고정(PR #7)

- [작업 기록](WORKLOG.md)과 [최신 인계](SESSION_HANDOFF.md)를 우선한다.
- `0.8.0-dev`(versionCode 11, Room 9, 14 테이블) 기준 검증 코드 `e8aa3c497785d7959845ee06ae0530016dddcb96`:
  - 원격 빌드·단위·ENOSPC(`BookReader Mobile Checks`): 원본 XML **222/222 PASS** (`androidApp` 84, `shared` 118, `epubAndroid` 17, `enospc` 3 + compile·lint·Room 9) — PR `46005087945`(`15201536527`) 1차 시도 PASS · Push `46005073120`(KSP `BLOCKED_ENV` 후 재실행 `15201921818`) PASS.
  - 원격 기기 매트릭스(`BookReader Mobile Device Tests`): API 26·34·36 각각 원본 XML **connected 79/79 + host 7/7 PASS**(4개 항목 `absent`→PID 교체 직접 확인, 실패·오류·건너뜀 0) — PR `46005087973` 1차 시도 3/3 PASS · Push `46005073162` 3/3 PASS(API 26 1차 시도 KSP `BLOCKED_ENV` 보존 후 재실행 PASS).
  - 기존 미확정이던 API 34 TXT 전체 쪽수 30초 타임아웃(Issue #2), Compose 1.9.3 `SnapshotStateObserver` 테스트 스레드 경합, `TxtReaderToc` 저장 직후 `ready` 경합, `CustomFontSettings` 등 IO/Default 복귀 `CalledFromWrongThreadException`의 근본 원인을 모두 규명·수정하고 회귀 테스트 4건을 추가했다.
- 실기기 성능·TalkBack·16KB 실제 OS·OEM 백업·전체 corpus·독립 보안/성능 리뷰·서명/공개 출시 게이트는 여전히 `NOT_RUN`/`BLOCKED`다. 아래 절은 이전 시점 이력이다.

## 2026-10-24 제품 완성도 개선

[새 단계별 감사](PRODUCT_IMPROVEMENT.md)와 [최신 인계](SESSION_HANDOFF.md)를 우선한다.
0.5.0/4449f1e는 build164, API26/34/36 native60+host6 PASS다.
0.5.1/5a8aad2는 build166 및 API26/34/36 각각 native63+host6 PASS다. [원본·실제 화면·범위](UX_POLISH_0_5_1.md)를 따른다.
실기기·전체 corpus·TalkBack·16KB OS·공개 출시 gate는 유지한다. 아래 표와 건수는 각 시점의 이력이다.

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

전체 v1 출시 판정은 아직 BLOCKED다. 아래 PASS는 명시한 소스·계층·fixture에 한정한다.
최신 통합 소스/기기 결과는 [인계](CONTEXT_HANDOFF.md)와 [출시 감사](RELEASE_CANDIDATE_REPORT.md)에 연결한다.

| 범위 | 실행 근거 | 상태 / 남은 검증 |
|---|---|---|
| TXT 가져오기·정규 UTF-16·페이지/스크롤·검색·북마크·인코딩 | main 22526d3 API36 ordinary 52, [원본 XML](evidence/m01-epub-validation/remote-22526d3-device/summary.json) | PASS; 물리 성능 별도 |
| TXT 실제 회전/STOP·A/B 경쟁·재열기·늦은 DB acknowledgement | 14c8c42 API36 49, 회귀 unit 66 | PASS; 저장장치 전원 차단 보장 아님 |
| EPUB 제품 가져오기·TOC·검색·북마크·원본 보존 | 22526d3 제품 3개 포함 | PASS 범위; 강화한 글자 크기 변경 후 문단 표시 검증은 964f8cc FAIL |
| EPUB 정확 표시 위치 | a5a7a31 SDK 텍스트/첫 표시 요소 locator 보완 | 원격 후속 검증 중; progression 근접만으로 exact PASS라 하지 않음 |
| Comic 제품 모드·RTL·pinch/pan·손상 건너뛰기·재열기 | 964f8cc Comic 제품 test PASS, 같은 전체 suite는 EPUB 1 FAIL | 개별 PASS; 전체 통합 gate 미완료 |
| Comic 긴 PNG/WebP·100장 decode | 7eaa0f5 API36 2개; [PSS/bitmap 원본](evidence/m09-comic-validation/native-7eaa0f5/summary.json) | emulator PASS; 실제 기기 PSS/GPU/빠른 UI 이동 별도 |
| 외부 host 강제 종료 | 22526d3 TXT 준비/검증 2단계 및 PID 증거 | TXT PASS; EPUB/Comic 각각 2단계 신규 실행 중 |
| Android 최소/중간/현재 | API26 / API34 / API36 CI 매트릭스 c1ab039 | 실행 중; 모든 OS/OEM/ABI 검증으로 확대 해석하지 않음 |
| 접근성 | TXT 1.0/1.3/2.0 및 density 회귀, 실제 버튼/semantics | API36 기존 PASS; 361b3f9 목록 측정 FAIL 보존, 후속 수정 검증 중; 실기기 TalkBack NOT_RUN |
| 패키지 | 실제 APK manifest, signature, zipalign, ELF | [정적 감사](M10_PACKAGE_AUDIT.md); 실제 16KB OS 실행 NOT_RUN |

## SAFE 계약

| ID | 구현/자동 검증 | 미실행 범위 |
|---|---|---|
| SAFE-01 | 조회 Error와 Missing 분리; 실제 TXT progress/locator 오류; raw bookmark/설정 보존; archive owner unit | 모든 archive 제품 UI 오류 주입 조합 |
| SAFE-02/03/04 | book/revision/epoch/sequence 및 A/B·역방향 경쟁 실제 DB/native | 저장장치 물리 장애 |
| SAFE-05 | 실제 파일 journal checkpoint·취소·mutated copy·원본 URI 없이 recovery 2회 | 포맷별 OS가 import 단계마다 죽이는 전 조합 |
| SAFE-06 | 삭제 중 decoding/native, 늦은 저장/override 재생성 금지 DB | 모든 OEM 프로세스 정책 |
| SAFE-07 | 실제 SQLite FULL, host errno 28/available bytes 0, Android 쓰기 오류 | 물리 fsync/전원 차단 |
| SAFE-08 | TXT cache 손실 후 원본 재생성·같은 canonical 위치 native | Comic 파생 manifest 전체 삭제 후 host 재시작 추가 조합 |
| SAFE-09 | TXT 60초 실제 연속 스크롤 report, archive 주기 저장 unit | EPUB/Comic 각각 60초 UI 장기 계측 |
| SAFE-10 | 위 포맷별 host evidence 참조 | 미커밋 메모리 위치 보존은 계약 밖 |
| SAFE-11 | 실제 schema 1/2/3/4 → 5 migration, destructive fallback 없음 | 8a6a778 SQL 실행/스키마 검증 실패 후 2회 재열기·전체 raw schema/row 불변 JVM PASS; 물리 장애 별도 |
| SAFE-12 | TXT 임시 0/reflow 비저장, archive owner unit | 강화된 EPUB 실제 문단 후속 결과 확인 |

## 재현

전용 fixture 기기에서 `source /workspace/toolchains/mobile-env.sh`, `./scripts/ci-check.sh`,
`./scripts/device-check.sh`를 실행한다. `ANDROID_SERIAL`은 검사 대상 기기만 지정한다.
기기 검사는 APK 설치와 자체 fixture 데이터를 사용하므로 개인 독서 데이터가 있는 기기를 피한다.
각 실패의 XML/logcat과 이전 성공 결과를 모두 보존한다. 단독 재실행 성공이 전체 suite 실패를 지우지 않는다.

기기 없는 로컬 환경은 `adb devices -l`과 device-check의 exit 78/BLOCKED_ENV를 기록한다.
호스트 검사는 `python3 scripts/reader-process-check.py <evidence-dir> txt|epub|comic`이며,
전체 device-check가 세 포맷 모두 실행하고 필수 case identity와 PID 변경을 검증한다.

## 세션 종료 시 최신 발견

사용자가 2026-10-17 현재 작업까지만 정리하고 다음 세션으로 넘기도록 지시했다. 새 구현은 중단한다.
`a5a7a31` device run 39016613293은 ordinary 53개 중 52 PASS/1 FAIL이며 host 6단계는 NOT_RUN이다.
실패는 EPUB 제품 test의 `assertRenderedNear`: expected progression 0.9264910536779324,
actual 0.9565038378966562, tolerance 0.03이다. 정확 문단 표시 assertion보다 먼저 이 비교가 실패했다.
precise text locator의 복원과 progression 값 차이의 관계를 추가로 확인해야 하며 허용 오차만 넓혀 PASS를 만들지 않는다.
`9dfb884`의 최신 local full CI는 66/74/16 및 schema/lint/build PASS다.
원격 build 39017137521은 workflow SUCCESS지만 artifact 미회수이며 API26/34/36 device 39017137527은 아직 실행 중이다. 이전 c1ab039 matrix는 API26 job SUCCESS/API34·36 FAIL이며 원본 회수가 필요하다.
[전달서](SESSION_HANDOFF.md)를 먼저 읽고 재개한다.
