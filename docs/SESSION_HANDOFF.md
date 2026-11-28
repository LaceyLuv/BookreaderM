## 2026-11-28 최신 — `0.8.1` (code 13) 집중 모드·페이지 좌우 스와이프·페이지 넘김 속도 개선 병합 및 서명 APK 검증 완료

- **현재 상태**: [PR #13](https://github.com/LaceyLuv/BookreaderM/pull/13)(`4e312ca5` → `main` 병합 `75dbebeb`, `0.8.1`/versionCode 13/Room 9 14개 테이블 유지)이 `BookReader Mobile Checks` **229/229 PASS**(`46107229772`, 아티팩트 `15248506638`) 및 `BookReader Mobile Device Tests` API 26·34·36 각각 **connected 83/83 + host 8/8 PASS**(`46107229837`, Push `46107225513`도 3/3 PASS)를 모두 통과해 `main`에 병합 완료됐다. 열린 Issue·PR은 0건이다.
- **최신 서명 Release APK (`46108752293`, 아티팩트 `15248324741`):**
  - 바로 내려받기(로그인된 브라우저): `https://github.com/LaceyLuv/BookreaderM/actions/runs/46108752293/artifacts/15248324741`
  - 파일: `BookreaderM-0.8.1-code13-signed.apk` (`22,386,009` bytes, `sha256:44ffe0ec36b6e97a5c101ce5662eedbce2e537e032925708a605c1be7ca0e4e6`)
  - 소스: `main` `75dbebebbae35f826f79ecd7dc361a40eda25c68` (Room 9 14개 테이블·`ReaderSettingsCodec` v1 유지)
  - 영구 정식 서명 인증서 SHA-256: `c86301d1b3556040d5d1edda2e5755514387ff7fea4a1954785fa1c178d62537` (`0.8.0`과 동일 키 — 기존 `0.8.0` 위에 삭제 없이 덮어쓰기 업데이트 가능)
  - `scripts/audit-apk.py` 서명 필수 모드·v2/v3 서명·16KB ELF/ZIP 정렬·권한·비디버그·재다운로드 해시 대조 모두 **PASS** ([설치 안내](INSTALLABLE_APK.md)). 공개 GitHub Release 미게시.
- **미실행 / 다음 작업**: 실제 스마트폰 업데이트 설치 및 실기기 집중 모드·스와이프·페이지 넘김 체감 확인, 실기기 성능·TalkBack·16KB 실제 OS·공개 출시 승인은 `NOT_RUN`(`BLOCKED` 유지).

## 2026-11-28 이전 — `main` 통합 완료 + `0.8.0` (code 12) 서명 APK 생성·재다운로드 검증 완료

- **현재 상태**: PR #7(`b9015f81`, Issue #2), PR #9(`ee112d1a`, Issue #3), PR #10(`f07323fb`, `0.8.0`/code 12 + 선택 서명), PR #11(`9d17ea96`, 감사 필터 보정)이 모두 `main`에 병합 완료됐다. 열린 Issue·PR은 0건이다.
- **최신 서명 Release APK (`46098603675`, 아티팩트 `15244029886`):**
  - 파일: `BookreaderM-0.8.0-code12-signed.apk` (`22,369,021` bytes, `sha256:da04e525a2a3b9a28166f1f29d26e57fce2c7ce9e03094876e34ffd61d2ae99d`)
  - 소스: `main` `9d17ea96a0544c5889a150ee97bfb6599044c725` (Room 9 14개 테이블 유지)
  - 신규 영구 정식 서명 인증서 SHA-256: `c86301d1b3556040d5d1edda2e5755514387ff7fea4a1954785fa1c178d62537` (GitHub Repository Secrets `BOOKREADER_RELEASE_*` 암호화 보관)
  - `scripts/audit-apk.py` 서명 필수 모드·v2/v3 서명·16KB 정렬·권한·비디버그·업로드 후 재다운로드 SHA-256 대조 모두 **PASS** ([설치 안내](INSTALLABLE_APK.md)). 공개 GitHub Release는 게시하지 않았다.
- **미실행 / 다음 작업**: 실제 스마트폰 설치 및 대용량 책/글꼴 백업·복원 확인, 실기기 성능·TalkBack·16KB 실제 OS·공개 출시 승인은 `NOT_RUN`(`BLOCKED` 유지).

## 2026-11-28 Phase 4-2 (Issue #3) 인계 — `a835cf9b` 원본 XML·ENOSPC·PID 대조 완료

- **현재 상태**: PR #7 검증 기준(`0ce19a0a`) 위의 `gemini/phase4-2-asset-backup` 브랜치에서 [Issue #3](https://github.com/LaceyLuv/BookreaderM/issues/3)(Phase 4-2: 책 원본·사용자 글꼴 선택 백업, v1 읽기 호환, `PLANNING → COMMITTING_ASSETS → CLEANUP` 중단·ENOSPC 재개 커밋)을 구현하고 전체 원격 빌드·실제 8 MiB tmpfs ENOSPC·API 26/34/36 기기 및 외부 `force-stop` 검증을 완료했다([ADR 0013](adr/0013-resumable-asset-backup.md)).
- **검증된 코드 SHA**: `a835cf9b8c1bdabb2a06cb55d8f455360dcc46da` (`0.8.0-dev`/versionCode 11/Room 9, 14 테이블 유지).
  - 원격 `BookReader Mobile Checks` Push `46085838946` (`15238429512`, 1차 시도 **PASS**): 원본 XML **229/229 PASS** (`androidApp` 86 / `shared` 121 / `epubAndroid` 17 / 실제 8 MiB tmpfs `enospc` 5 + compile·lint·Room 9, `ENOSPC_BACKUP_PASS` 2건 + `ENOSPC_PASS` 3건 확인).
  - 원격 `BookReader Mobile Device Tests` Push `46085838933` (1차 시도 3/3 **PASS**): API 26(`15239536935`)·API 34(`15239079959`)·API 36(`15239831575`) 각각 원본 XML connected **80/80** + host **8/8** (TXT/EPUB/Comic/Batch/Backup Asset 5개 외부 `force-stop` `absent`→PID 교체 직접 확인).
  - 보존된 중간 실패(`c364a81b` 가시성 컴파일 오류, `6fdf1848` ENOSPC `Unit` 반환 오류) 및 상세 SHA-256·PID 수치는 [작업 기록](WORKLOG.md)에 있다.
- **보존 및 미실행 범위**: Room 9 14개 테이블 정의, 기존 v1 `.brmbackup` 기본값·호환성, 사용자 책·진행도·서명키·`05ffd50` 산출물을 보존했다. 로컬 JDK 17 미설치(`doctor.sh` exit 78)는 `BLOCKED_ENV`, 실기기 대용량 백업 성능·TalkBack·16KB OS·서명/출시 게이트는 `NOT_RUN`이다.
- **다음 작업**:
  1. 사용자가 [PR #7](https://github.com/LaceyLuv/BookreaderM/pull/7)(`Closes #2`)을 `main`에 수동 병합하고 작업 브랜치를 정리한다.
  2. 이어서 Phase 4-2 PR(`gemini/phase4-2-asset-backup`, `Closes #3`)의 대상 브랜치가 `main`인지 확인한 뒤 수동 병합한다.
  3. 다음 후속은 실기기/성능/TalkBack/16KB OS·서명 APK 재생성 또는 사용자 우선순위에 따른 후속 Issue 착수다.

## 2026-11-27 PR #7 `main` 통합 인계 — `e8aa3c49` 원본 XML·PID 대조 완료

- **현재 상태**: `codex/backup-080`(`e60d931f`)의 UI 통합([PR #5](https://github.com/LaceyLuv/BookreaderM/pull/5))·Issue #2 해결([PR #6](https://github.com/LaceyLuv/BookreaderM/pull/6))과 `main`(`cbe4e405`, [PR #4](https://github.com/LaceyLuv/BookreaderM/pull/4))을 통합한 [PR #7](https://github.com/LaceyLuv/BookreaderM/pull/7)(`gemini/integrate-080-to-main`, `Closes #2`)에서, `d3612875` API 36 실패(`139880170365`, `CustomFontSettings` IO 복귀 `CalledFromWrongThreadException`)까지 수정해 전체 원격 CI 통과 및 원본 XML·PID 직접 대조를 완료했다.
- **검증된 코드 SHA**: `e8aa3c497785d7959845ee06ae0530016dddcb96` (`0.8.0-dev`/versionCode 11/Room 9, 14 테이블).
  - 원격 `BookReader Mobile Checks`(원본 XML 222/222: `androidApp` 84 / `shared` 118 / `epubAndroid` 17 / `enospc` 3 + compile·lint·Room 9): PR `46005087945` (`15201536527`) 1차 시도 **PASS** · Push `46005073120` (1차 시도 KSP `BLOCKED_ENV` 보존 후 재실행 `140527309238` / `15201921818`) **PASS**.
  - 원격 `BookReader Mobile Device Tests`(API 26·34·36, 원본 XML connected 79/79 + host 7/7, 4개 항목 `absent`→PID 교체 직접 확인): PR `46005087973` 1차 시도 3/3 **PASS**(`15200828210`·`15200998757`·`15201068772`) · Push `46005073162` 3/3 **PASS**(API 34 `15202592009`·API 36 `15202138802` 1차 시도 PASS, API 26 1차 시도 KSP `BLOCKED_ENV` `15200606943` 보존 후 재실행 `15203126737` PASS).
  - 상세 SHA-256, PID 교체 수치, 핵심 소요 시간 및 보존된 중간 실패(`82b6ab07`, `fe6c493c`, `d3612875`) 원본 기록은 [작업 기록](WORKLOG.md)에 있다.
- **보존 및 미실행 범위**: Room 9 14개 테이블 정의, 사용자 책·진행도·서명키·`05ffd50` 기준 산출물을 변경하지 않았다.
  본 VM의 Actions 아티팩트 다운로드·원본 대조는 정상 동작(`PASS`)하며, 로컬 JDK 17 미설치(`doctor.sh` exit 78)는 `BLOCKED_ENV`,
  실기기 성능·TalkBack·16KB OS·서명/출시 게이트는 `NOT_RUN`이다.
- **다음 작업**:
  1. 사용자가 [PR #7](https://github.com/LaceyLuv/BookreaderM/pull/7)을 `main`에 수동 병합(`Closes #2`).
  2. `main` 반영 완료 후 확인한 전체 `main` SHA를 기준으로 [Issue #3](https://github.com/LaceyLuv/BookreaderM/issues/3)(Phase 4-2: 책·글꼴 원본 선택 백업 + v1 호환 + 중단/ENOSPC 재개 커밋)을 착수한다.

## 2026-11-21 독서 기록 백업·복원 구현과 자동 검증 완료

`/workspace/BookreaderM-reader-070`의 `codex/backup-080`에서 진행한다.
제품·테스트 소스 `4777199e2a72d5766f14b251b4d6a511cae205ba`, 0.8.0-dev/versionCode 11/Room 9.
매 shell에서 `source /workspace/toolchains/mobile-env.sh`을 적용한다. doctor exit 0.

**설정 → 데이터 보관 → 독서 기록 백업·복원**에서 파일을 저장하고 미리 본 뒤 적용할 수 있다.
동일 원본의 현재 진행도와 기존 기록을 우선한다. 새 책은 같은 원본을 다시 추가하면 읽을 수 있다.
[사용 방법](../README.md#데이터와-지원-범위), [파일 형식과 병합 설계](adr/0012-portable-reading-records.md).

원격 빌드 44850131358의 219개와 로컬 빌드 216개, 테스트 APK, lint, unsigned APK/AAB 빌드가 통과했다.
기기 실행 44850131565에서 API 26·34·36 각각 UI 78개와 강제 종료 후 복원 7단계가 통과했다.
같은 소스의 필수 XML, ZIP 해시·CRC, 강제 종료 후 PID 부재와 교체, 백업 화면을 대조했다.
[최종 검증 원본](evidence/product-quality/verified-4777199/summary.json),
[미리보기와 복원 결과 화면](evidence/product-quality/verified-4777199/visual-review.json).

API 34 첫 시도는 백업 검사와 TXT 위치 대조를 통과한 뒤 기존 TXT 전체 쪽수 대기에서 시간 초과했다.
같은 소스의 두 번째 시도에서 재현되지 않았고 원인은 미확정이다.
[첫 실패 원본](evidence/product-quality/failed-4777199-api34/summary.json).

API 26의 독서 기록 복원 테스트와 실제 시스템 파일 선택기의 빈 서재 저장·복원은 로컬에서도 통과했다.
[로컬 명령과 결과](evidence/product-quality/phase4-reading-backup-local/summary.json),
[실제 파일 선택기](evidence/product-quality/phase4-reading-backup-local/system-picker-api26/summary.json).

Room 9의 14개 테이블 정의, 사용자 책과 진행도, 서명키와 기존 05ffd50 APK·ZIP을 보존했다.
최신 설치 파일은 이전에 전달한 05ffd50이다. 다음 단위는 책·글꼴 원본을 선택적으로 함께 담고
중단된 파일 복원을 재개하는 기능이다. 실기기·TalkBack·성능·공개 배포 검증이 남아 있다.
[보존 대조](evidence/product-quality/phase4-reading-backup-local/preservation.json).

실행 상태와 검증 도우미는 `/workspace/work/bookreaderm-product-quality/`의
`phase4-reading-backup/current.json`, `ci/`, `verify-reading-backup.py`에 있다.

## 2026-11-18 EPUB 형광펜·메모 구현과 자동 검증 완료

`/workspace/BookreaderM-reader-070`의 `codex/reader-070`에서 검증했다.
제품·테스트 소스 `3188545e5a4bf04f859f3a7659eca777b50c7a8d`, 0.7.0-dev/versionCode 10/Room 9.
EPUB 본문을 길게 누른 뒤 **형광펜 · 메모**를 선택하면 네 가지 색과 메모를 저장할 수 있다.
형광펜 목록은 수정·삭제·본문 이동을 제공한다. [설계와 선택 제약](adr/0011-exact-epub-annotations.md).

원격 빌드 44243688890의 210개와 기기 실행 44243688898의 API 26·34·36 각각
UI 77개와 외부 강제 종료 검증 7단계가 통과했다. 실패·오류·skip은 0이다.
원본 ZIP SHA-256·CRC, 동일 소스, 필수 XML, 실제 강제 종료 후 PID 교체를 대조했다.
반복되는 두 번째 단어의 표시 좌표를 저장·Activity 재생성·글자 크기 변경·재열기·본문 이동
각 단계에서 확인했다. 기존 EPUB 검색 대상 p179가 화면에 실제 표시되는지도 확인했다.
[검증 원본](evidence/product-quality/verified-3188545/summary.json), [화면 검토](evidence/product-quality/verified-3188545/visual-review.json).

API 26 메모 입력 중 메모리 부족을 조사해 Android 8.x에서 Readium 화면 잘림 여백 콜백을
끄고 Compose가 적용한 시스템 여백의 중복 전달을 막았다. 터치 테스트는 목차에서 첫 장으로
이동하고 선택할 단어가 보이는 것을 확인한 뒤 실제 길게 누르기를 수행한다.
[메모리 부족·추적 원본](evidence/product-quality/failed-c141a99/summary.json),
[화면 밖 터치 진단](evidence/product-quality/api26-diagnostic-977b092/summary.json).

`source /workspace/toolchains/mobile-env.sh` 적용 후
`./gradlew :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug --console=plain` exit 0,
`./gradlew :androidApp:assembleRelease :androidApp:bundleRelease --console=plain` exit 0.
`python3 /workspace/work/bookreaderm-product-quality/verify-phase3.py 3188545e5a4bf04f859f3a7659eca777b50c7a8d 44243688890 44243688898` exit 0.
[로컬 빌드 원본](evidence/product-quality/phase3-epub-api26-local/toc-test-summary.json).

첫 수정 793e16be의 API 26은 메모 위치에서 대기한 뒤 45분 제한에 도달했다.
같은 소스의 API 34 TXT 전체 쪽수 시간 초과는 최종 실행에서 재현되지 않았고 원인은 미확정이다.
ea63114의 API 34는 고정 KSP 의존성을 받지 못해 테스트 시작 전 종료했다.
[첫 수정 원본](evidence/product-quality/failed-793e16b/summary.json),
[후속 원본](evidence/product-quality/failed-ea63114/summary.json).
API 26 최신 EPUB 별도 진단도 13개 전부 통과했다.
[진단 원본](evidence/product-quality/api26-diagnostic-3188545/summary.json).

기존 13개 테이블 정의, 관리 원본·진행도, 서명키·이전 05ffd50 APK/ZIP을 보존했다.
[보존 대조](evidence/product-quality/verified-3188545/preservation.json).
실제 휴대전화·TalkBack·성능·공개 배포는 미실행이다. 마지막 전달 APK는 05ffd50이다.
다음 단위는 Phase 4 백업·복원 설계와 데이터 보존 검증이다.

## 2026-11-18 현재 — Android 8 EPUB 여백 전달 보완

최신 제품·테스트 `ea631148a1199dba8ec1a4f5f7a0d66a47d39d55`.
API 26에서 화면 잘림 전용 콜백을 끈 첫 수정의 전체 실행은 아직 진행 중이다.
793e16be의 API 36은 원본 대조까지 UI 77개, host 7단계 PASS.
API 34는 EPUB 2개 PASS, 기존 TXT 나눔 글꼴/여백 변경 후 전체 쪽수 대기 1 FAIL.
[첫 수정 실패 원본](evidence/product-quality/failed-793e16b/summary.json).

후속 수정은 이미 시스템 여백을 적용한 Compose 화면에서 Android 8.x의 Readium 하위 뷰로
같은 여백을 다시 전달하지 않는다. [검증 명령과 실행 ID](evidence/product-quality/phase3-epub-api26-local/host-insets-summary.json).
테스트 APK 컴파일/lint exit 0. 전체 build 44242666959, device 44242666827 및
API 26 EPUB 진단 44242666916 진행 중이다. 진단은 기존 13개 EPUB 검사와 스레드·좌표 로그를 수집한다.

## 2026-11-18 진행 중 — EPUB 메모 입력 중 Android 8.x 정지 수정

작업 위치 `/workspace/BookreaderM-reader-070`, `codex/reader-070`, 0.7.0-dev/code10/Room9.
저장된 mobile-env.sh 적용 후 doctor exit 0. 개발 브랜치와 원격 main을 확인했고 기존
서명키 파일 정보와 05ffd50 APK·ZIP SHA-256이 일치했다.

API 26 전체 실행 42367624851과 42368968011은 처음 8개 완료 뒤 새 형광펜 테스트 중
앱 메모리 부족으로 종료했다. c141a99의 스레드 기록에서 실제 메뉴 선택 후 메모 입력 대기와
Readium 여백 콜백의 메모리 부족 스택을 확인했다.
[65a57bc 원본](evidence/product-quality/failed-65a57bc/summary.json),
[c141a99 원본](evidence/product-quality/failed-c141a99/summary.json).
c141a99 API 34·36은 각각 native 77개와 host 7단계가 통과했고 본문 강조 화면도 확인했다.
[부분 검증](evidence/product-quality/failed-c141a99/api34-36-verified.json).

별도 API 26 EPUB 진단 42369244168은 13개 중 12개 통과. 선택 목표가 화면 밖인 순간
터치 입력이 거부된 1개가 실패했다. 이전 검색·재생성 검사는 그 실행에서 통과했다.
[진단 원본](evidence/product-quality/api26-diagnostic-977b092/summary.json).

793e16be는 화면 잘림 보정이 필요 없는 Android 8.x에서 Readium 여백 콜백을 비활성화하고
테스트가 실제로 보이는 단어를 길게 누르게 한다. [설계](adr/0011-exact-epub-annotations.md).
로컬 테스트 APK compile·lint 및 릴리스 APK·AAB 빌드 exit 0.
[명령과 로그](evidence/product-quality/phase3-epub-api26-local/summary.json).
원격 build 44239853310 및 API 26·34·36 device 44239853158 실행 중.
전체 증거 대조 후 현 상태를 확정한다. 실기기·TalkBack·성능·공개 배포는 미실행이다.

매 shell에서 `source /workspace/toolchains/mobile-env.sh`을 적용한다.
진행 상태와 검증 도우미는 `/workspace/work/bookreaderm-product-quality/`의
`phase3-epub-annotations/current.json`, `ci/`, `verify-phase3.py`에 있다.

## 2026-11-07 진행 중 — API34/36 EPUB 형광펜·메모 검증 PASS, API26 정지 조사

작업 `/workspace/BookreaderM-reader-070`, `codex/reader-070`, code10/Room9.
제품/테스트 소스 `65a57bcff8388bdbacfe2ef7b3467cac887027ea`의 build42367624823 PASS210,
device42367624851 API34/36 각각 native77+host7 PASS. artifact digest/CRC/source clean/필수 XML,
외부 force-stop PID 부재·교체, 정확한 SDK 사각형5단계와 EPUB p179 가시성을 대조했다.
[부분 matrix 원본과 검증](evidence/product-quality/partial-65a57bc/summary.json).
실제 선택 메뉴/메모 목록 화면 확인. 저장 직후 본문 캡처에는 닫히기 전 목록 창이 남아 있어 한계를 기록했다.
[원본 시각 검토](evidence/product-quality/partial-65a57bc/visual-review.json).

최신 소스 `c141a99fe52126d9706a1d6eb135f98c2e403cac`는 캡처 전 dialog 부재를 검사하고,
멈춘 EPUB fixture의 main/instrumentation 스레드를60초 간격 최대3회 logcat에 남긴다.
검사의 결과/제한시간을 바꾸지 않고 개인정보/본문을 기록하지 않는다.
compile/lint exit0. 최초 internal/public 가시성 컴파일 오류도 보존했다.
[명령·결과](evidence/product-quality/phase3-epub-menu-local/diagnostics-summary.json).
build42368967920/device42368968011 실행 중. 전체 세 API PASS는 아직 아니다.
진단workflow만 추가한977b092의 별도run42369244168은 API26 EPUB 세 클래스만 실행한다.
8분 command 제한 후 원래 exit code와 raw logcat/메모리/fixture를 수집하며 전체검증으로 대체하지 않는다.
공식 pinned emulator-runner는 script의 각 줄을 별도sh로 실행하므로 RUNNER_TEMP에 준비한 bash스크립트를 한 번 호출한다.

API26 이전 ec788cd/a46551a는 기존 EPUB 검색·재생성 검사18에서 진행이 멈춘 뒤 앱512MB OOM,
45분 job 제한으로 CANCELLED다. XML이 완성되지 않아 partial runner/logcat만 보존했다.
단순한 환경 문제나 해결된 현상으로 처리하지 않는다. ec788cd API34에는 기존 TXT 글자배율 검사에서도
SnapshotStateObserver 다중 스레드 접근1 FAIL이 추가됐다(합계3 FAIL). 이후 실행의 해당검사는 PASS지만 원인 미확정이다.
[ec788cd 원본](evidence/product-quality/failed-ec788cd/summary.json), [a46551a 원본](evidence/product-quality/failed-a46551a/summary.json).
3e10382 API26도 기존검사18에서 멈춰45분 CANCELLED였다. partial17개 중 새메뉴1 FAIL, 모호함/본문변경 거부 PASS.
[원본](evidence/product-quality/failed-3e10382/summary.json). 65a57bc API26은 결과 수집 대기다. 다음: API26 원본과 c141a99 thread trace 확인→실패 수정→전체 matrix/화면/문서 확정.

기존13개 DB 테이블·책·진행도·키·05ffd50 APK/main을 보존한다. 기존 계약31개 중30개는 바이트 동일하며,
EpubContentPolicy만 Android Xalan 이모지 변환 결함을 수정했다. [보존 대조](evidence/product-quality/phase3-epub-menu-local/preservation.json).
실기기/TalkBack/성능/공개 배포는 NOT_RUN, Phase4 미착수. 매 shell `source /workspace/toolchains/mobile-env.sh`.
현재/이전 실행ID와 helper는 `/workspace/work/bookreaderm-product-quality/phase3-epub-annotations/current.json`, `ci/`다.
별도 소프트웨어 AVD는 정상 종료 상태이며 데이터는 남겼다. 자세한 설계·실패 수정 이력은 아래와 [ADR0011](adr/0011-exact-epub-annotations.md)을 따른다.

## 2026-11-07 진행 중 — EPUB 형광펜·메모, Unicode 변환 수정 검증

작업 `/workspace/BookreaderM-reader-070`, `codex/reader-070`. 최신 제품/테스트는 `65a57bcff8388bdbacfe2ef7b3467cac887027ea`, code10/Room9.
[ADR0011](adr/0011-exact-epub-annotations.md): 공개 SDK 선택+정확한 DOM 범위/문맥 검증,4색/메모/목록 CRUD/이동.
기존13개 DB 테이블/책 원본/진행도/서명키/main/기존05ffd50 APK는 보존한다.

첫 소스a259e0b 로컬205/릴리스/원격 build208 PASS. 세 API native77 중 새2개 FAIL, 기존75 PASS,
host는 NOT_RUN. [전체 실패 원본](evidence/product-quality/failed-a259e0b/summary.json).
실패 지점은 선택 전 생성 EPUB 문단/텍스트 노드 부재. ec788cd는 SDK의 빈 문맥 필드 생략 호환성 수정(shared113 PASS).
a46551a는 생성 fixture 관리복사본 일치와 실제 DOM 진단/준비 대기를 추가했다. 그 결과는 진행 중.

별도 API26 소프트웨어 AVD에서 Android Xalan이 이모지를 invalid surrogate numeric references로
내보내는 기존 EpubContentPolicy 결함을 재현했다. UTF-16 문자 출력을 엄격한 UTF-8 Writer로 받고
XML encoding 선언을 생략하는 방식으로 수정했다. 출력16MiB를 쓰기 전에 제한한다.
이 수정만 기존 EPUB 처리 코드를 의도적으로 변경했다. 이후 EPUB unit17/compile/lint PASS.
첫 단위 재파싱 검사는 엔진 CSP를 publication-input policy에 잘못 전달해1 FAIL했다. 보안규칙은 유지하고
독립 XML parser로 엔진 출력 roundtrip을 검사하도록 고쳤다. 실패 원본도 보존했다.

현재 실행/누적 실행ID는 `/workspace/work/bookreaderm-product-quality/phase3-epub-annotations/current.json`.
소프트웨어 AVD `bookreader_epub_api26_probe`/emulator-5580, own path `phase3-epub-annotations/isolated-avds`,
실행 exec session74068(정상 종료함). `/dev/kvm` 없어 TCG이다. 기존 AVD/기기/앱 데이터는 건드리지 않는다.
설치 전 빌드 산출물을 동시에 읽어 첫 로컬 install의 digest 검사 실패가 있었으므로, 이후 설치는
`phase3-epub-annotations/local-probe-3e10382/{app,test}.apk`의 고정 복사본만 사용한다(서명 검사PASS).
첫 설치 흐름은 종료했고 고정 APK 복사본 설치는 성공했다. 로컬 cold1+warm2 계측은 READY40초 timeout이다.
실제 캡처에 Android System UI 응답 없음이 있어 BLOCKED_ENV로 별도 보존했다. 해당 AVD는 adb emu kill로
정상 종료했고 데이터는 삭제하지 않았다. 실제 앱의 EpubContentPolicy를 API26 Dalvik에서 직접 호출한 Unicode/CSP 검사는 PASS다.

현재3e10382 원격 build42366128291 원본 digest/CRC/XML PASS210(Android77/shared113/EPUB17/ENOSPC3).
device42366128301 API34/36 native77 중1 FAIL: 실제 선택은 정확하나 메뉴 클릭 시간 초과. 모호함/본문 변경 거부는 PASS. API26 진행 중.
65a57bc는 선택 전에 UiAutomation 연결/별도 창 조회 준비, 실제 보이는 메뉴 클릭과 원본 캡처/구조 진단을 추가했다.
로컬 compile/lint exit0. build42367624823/device42367624851 진행 중.
이전ec788cd/a46551a 원격 build209 PASS, API34/36 새2개 FAIL. ec788cd API34에는 기존 TXT 글자배율 검사에서도
SnapshotStateObserver 다중 스레드 접근1 FAIL이 추가되어 합계3 FAIL이다. 이후a46551a/3e10382의 해당검사는 PASS이며 원인해결로 단정하지 않는다.
ec788cd API26은45분 제한 CANCELLED, logcat17개 완료 중2 FAIL, 18번째 기존managedEpubTocSearchBookmarksAndRecreationPreserveFullLocator에서 멈춤.
완료 XML은 없고 이후앱512MB OOM 로그가 있다. 원인은 아직 미확정이며 환경 문제로 단정하지 않는다.
원본/digest/CRC와partial counts는failed-ec788cd/summary.json에 보존했다.
a46551a API26도같은기존검사18에서 OOM(WindowInsetsCompat.toWindowInsetsCompat)후45분 CANCELLED다.
XML없이 runner test-results.log에추가실패가있고failed-a46551a에보존했다. 3e10382 API26은아직실행중.

다음: 원격 실제 DOM·선택·그림 좌표 검사→실패 수정→전체 matrix 원본/화면/force-stop 대조→문서/증거 commit.
`ci/`, `retain-artifact.py`, `verify-phase3.py`, `retain-verified-phase3.py` helper 사용.
매 shell `source /workspace/toolchains/mobile-env.sh`. 실기기/TalkBack/성능/공개 배포는 NOT_RUN, Phase4 미착수.

## 2026-11-06 최신 — TXT 형광펜·메모 자동 검증 완료

`/workspace/BookreaderM-reader-070`, `codex/reader-070`, 제품/테스트 소스 `208f74caf788bf9a72d1640d4e30926ae0a29716`.
0.7.0-dev/code9/Room8. [결정·범위](adr/0010-exact-txt-annotations.md).
TXT의 **형광펜 → 현재 구간에서 문장 선택**에서 실제 선택 좌표로 저장한다. 네 가지 색상, 선택적 메모,
목록에서 수정/삭제/정확한 본문 이동을 제공한다. 같은 문장을 검색해 위치를 추정하지 않는다.
현재 위치부터 최대4,096 UTF-16 단위의 읽기 전용 선택 창이며, 본문의 기본 복사 선택은 유지한다.
최대500개/내용 버전, 메모2,000 UTF-16 단위다. EPUB 형광펜은 다음 단위이며 Phase3 전체 완료는 아니다.

기존12개 테이블 정의를 그대로 두고 txt_annotations만 추가했다. 실제1–7→8 migration/rollback과
raw 책·진행도·북마크·설정·목차 보존, stale session/CAS/미래 값/한도/SQL 오류를 검사했다.
표시·저장 전에 원문의 정확한 구간을 대조하며, 불일치한 기록을 다른 문장에 붙이지 않는다.
SQLite TEXT 길이와 BLOB 바이트 길이를 함께 검사해 NUL 뒤의 과대 기록도 읽기 전에 거부한다.
기존 진행도/locator/캐시/페이지/Readium 계약28개 파일과 서명키/이전 설치 APK/main을 보존했다.

build42210038416 PASS200(Android77/shared104/EPUB16/실제 ENOSPC3),
device42210038417 API26/34/36 각각 native75+host7 PASS. 실패·오류·skip0.
[필수 XML·원본·대조](evidence/product-quality/verified-208f74c/summary.json): artifact SHA/CRC/source clean,
외부 force-stop 뒤 PID 부재·교체, EPUB p179 실제 가시성까지 확인했다.
`python3 /workspace/work/bookreaderm-product-quality/verify-phase3.py 208f74caf788bf9a72d1640d4e30926ae0a29716 42210038416 42210038417` exit0.
실제 길게 누르기와 두 번째 반복 문장 선택, TextLayoutResult의 정확한 형광펜 범위,
메모 수정·리플로·페이지 전환·재열기·삭제·외부 강제 종료 복원을 검사했다.

로컬 전체197 및 APK/test APK/lint/schema8 PASS. 마지막 화면 보완의 compile/lint/릴리스 APK·AAB exit0.
[로컬 원본](evidence/product-quality/phase3-annotations-bound-local/summary.json),
[최초 잘못된 surrogate fixture 실패와 수정](evidence/product-quality/phase3-annotations-local/summary.json),
[초기 전체 기기 결과](evidence/product-quality/verified-ae4453d/summary.json),
[바이트 한도 보완본 전체 결과](evidence/product-quality/verified-242ae6b/summary.json)를 보존한다.
초기 긴 설정 라벨/가장자리 캡처는 [원본 시각 검토](evidence/product-quality/annotations-visual-ae4453d/review.json)에 남겼다.
세 API의 실제 본문·메모 목록·도구 화면을 확인했다. 선택한 단어 전체의 분홍 표시와 짧아진 설정 라벨이 잘리지 않는다.
[원본 시각 검토·한계](evidence/product-quality/verified-208f74c/visual-review.json). 도구 캡처의 쪽수 계산 중 표시는 최종 쪽수 정확성 증거로 사용하지 않는다.
이전 Phase2 측정 재진입과19a8235 API26 도구 캡처의 원인 미확정 기록도 유지한다.

실기기 설치·TalkBack·성능·공개 배포는 NOT_RUN. 이번에는 새 설치 파일을 전달하지 않았다.
다음 단위는 Readium 공개 선택 locator/Decoration에 기반한 EPUB 형광펜·메모다. Phase4 백업·복원은 미착수다.
매 shell `source /workspace/toolchains/mobile-env.sh`. 실행 상태/helper는 `phase3-annotations/current.json`, `ci/`다.


## 2026-11-06 최신 — 읽기 도구·빠른 설정 자동 검증 완료

`/workspace/BookreaderM-reader-070`, `codex/reader-070`, 제품/테스트 소스 `19a82358746063d97826f04d992c16a30026319b`.
[구현·전후 화면·한계](READER_0_7.md). 0.7.0-dev/code8/Room7이며 0.7.0 전체 완료는 아니다.
TXT·EPUB·Comic 도구를 아이콘/이름으로 통일하고 하단 쪽수에 위치 이동을 모았다. 기존 설정 draft/저장을 재사용한
빠른 설정과 추가 설정 구분, 책별/전체 범위 표시, 상속 복원 시 다른 책별 옵션 보존을 구현했다.

build41194371543 PASS189(Android75/shared95/EPUB16/실제 ENOSPC3),
device41194371545 API26/34/36 각각 native74+host7 PASS, 실패·오류·skip0.
[원본636개와 대조 결과](evidence/product-quality/verified-19a8235/summary.json): artifact SHA/CRC, source clean,
필수 XML, 실제 force-stop 뒤 PID 부재·교체, EPUB p179 가시성 PASS.
`python3 /workspace/work/bookreaderm-product-quality/verify-phase3.py 19a82358746063d97826f04d992c16a30026319b 41194371543 41194371545` exit0.
로컬 `BOOKREADER_EVIDENCE_DIR=build/phase3-controls-local ./scripts/ci-check.sh` exit0(186),
`./gradlew :androidApp:assembleRelease :androidApp:bundleRelease --console=plain` exit0.
[로컬 원본](evidence/product-quality/phase3-controls-local/summary.json). 재개 후 doctor와 키/기존 APK 보존을 재확인했다.

세 API의 빠른 설정과 정상 도구 화면을 확인했다. API26의 설정을 닫은 직후 `phase3-reader-tools.png`에는
검색/북마크 라벨 배치 이상이 있어 안정 화면 증거로 쓰지 않는다. 같은 실행 `txt-controls.png`/`epub-controls.png`는 정상이다.
[시각 검토와 한계](evidence/product-quality/verified-19a8235/visual-review.json). 원인은 미확정이며 캡처 원본을 보존한다.
실기기·TalkBack·성능·공개 배포는 NOT_RUN이다. 이전 Phase2 측정 재진입의 원인 미확정 기록도 유지한다.

DB7/진행도/원본/키/main/기존05ffd50 설치 APK를 유지했다. 새 설치 파일은 전달하지 않았다.
다음 단위: 정확한 TXT·EPUB 선택 범위 검증 후 형광펜/메모. Phase4는 미착수다.
매 shell `source /workspace/toolchains/mobile-env.sh`. 실행/증거 helper는 `phase3-controls/current.json`, `phase3-current.json`, `ci/`다.

이하 이전 상태는 이력이다.

## 2026-10-30 요청한 설치 시험용 APK — 0.7.0-dev

TXT 목차를 포함한 검증 소스05ffd50을 기존 키로 서명했다. 코드8/Room7이며 0.7.0 전체 기능 완료는 아니다.
사용자 요청에 따라 임시 ZIP 전달을 준비했다. 장기 GitHub Releases 경로는 아직 미완료이고 기존 HTTP400 업로드를 재시도하지 않았다.

- APK: `/workspace/artifacts/BookreaderM/05ffd50/installable/BookreaderM-0.7.0-dev-test-signed.apk` (21,982,367 bytes).
- SHA256: `b58061b17b41335c17ac7d662be1d325092ab19db49e3284570eabd1e44454bc`.
- ZIP: `/workspace/artifacts/BookreaderM/05ffd50/installable/BookreaderM-0.7.0-dev-test-signed.zip` (18,521,140 bytes,20MB 미만).
- ZIP SHA256: `dae06ad079607fc1fa4f89b0d6fc6ac1e8048a3f47dc8bc6a697b26574f565d2`.
- 기존 인증서/v2/v3/16KB 정적 감사, 서명 전후612 entry 동일성, ZIP CRC·추출 APK 동일성 PASS.
- 제품 source `05ffd50ab8213ec9563a92149b6ed1930c510c3b`: build189/API26·34·36 native73+host7 PASS.

[패키지 증거](evidence/product-quality/package-05ffd50/summary.json). 실제 휴대전화 설치와 대화 다운로드 성공은 미확인이다.
기존0.5.1/0.6.0 APK 및 서명키를 보존했다. 앱을 삭제하거나 데이터를 지우지 않고 같은 키 업데이트로 설치한다.
공개 릴리스를 게시하지 않았다. 다음 개발은 도구/빠른 설정·형광펜/메모이며 인계의 기존 작업 순서를 유지한다.

명령: `source /workspace/toolchains/mobile-env.sh; ./gradlew -I /workspace/work/bookreaderm-product-quality/test-signing.gradle :androidApp:assembleRelease --console=plain` exit0.
`python3 /workspace/work/bookreaderm-product-quality/finalize-package-05ffd50.py` exit0.
검증 source와 현재 제품 소스 차이 없음. 코드 변경 없이 패키지만 생성해 전체 테스트는 반복하지 않았다.

## 2026-10-29 최신: TXT 목차 단위 구현·자동 검증 완료

작업 위치 `/workspace/BookreaderM-reader-070`, 브랜치 `codex/reader-070`, source `05ffd50ab8213ec9563a92149b6ed1930c510c3b`.
0.7.0-dev/code8/Room7. [구현·화면·남은 범위](READER_0_7.md).
build40892825363 PASS189(Android75/shared95/EPUB16/실제 ENOSPC3),
device40892825193 API26/34/36 각각 native73+host7 PASS, 실패·오류·skip0.
[원본 대조](evidence/product-quality/verified-05ffd50/summary.json): digest/CRC/XML/source clean/필수 사례,
외부 force-stop 뒤 PID 부재·교체와 EPUB 실제 p179 확인, `verify-phase3.py` exit0.
새 TXT 목차의 수정/제외/복구/수동 추가/정확한 이동/재열기와 host 강제 종료 복원을 검사했다.
로컬 CI186 및 릴리스 APK/AAB 컴파일 PASS. 키와 기존0.5.1/0.6.0 APK/main은 보존했다.
API36 실제 목차 목록 캡처 확인. API26/34 이미지에는 닫히는 추가 창이 남아 최종 목록의 시각 증거로 사용하지 않는다.

다음 작업은 도구 UI/빠른 설정, 정확한 TXT·EPUB 선택 범위에 기반한 형광펜·메모다.
현재 책별 override는 읽기 방식/만화 방향/맞춤이며, 글자/색상/밝기는 전체 기본값이다. 기존 저장·복원 경로를 재사용한다.
형광펜/메모·Phase4 백업/복원·실기기 성능/접근성·공개 배포는 미완료다. 0.7.0 전체 완료가 아니다.
이 버전의 설치 배포는 하지 않았다. 0.6.0의 기존 키 설치 파일은 `codex/library-060`과 INSTALLABLE_APK에 보존한다.
매 shell `source /workspace/toolchains/mobile-env.sh`. 현재 소스/증거를 reset/clean하거나 앱 데이터를 삭제하지 않는다.
실행 ID/helper는 `/workspace/work/bookreaderm-product-quality/phase3-current.json`, `ci/`, `verify-phase3.py`다.

아래는 당시 상태를 보존한 이력이다. 다음 세션은 이 최신 절을 먼저 따른다.

## 최신 이어갈 위치 — 2026-10-29

Phase2 최종은 `codex/library-060`에 보존하고, 현재 개발은 `/workspace/BookreaderM-reader-070`의 `codex/reader-070`에서 진행한다.
제품/테스트 소스 `05ffd50ab8213ec9563a92149b6ed1930c510c3b`를 원격에 push했다.
TXT 자동/수동 목차·수정/제외/복구·이동과 Room7 별도 저장, 실제 UI/host 검사를 구현했다.
로컬186/릴리스 APK·AAB 컴파일 PASS, build40892825363 원본 대조 PASS189(실제 ENOSPC3 포함).
device40892825193 API26/34/36은 실행 중이다. 아직 PASS로 기록하지 않는다.
전체 결과 대조/원본 화면 확인/실패 수정이 다음 작업이며, 형광펜·도구 개편·백업은 미완료다.
원본 ZIP/실행 상태 helper는 `/workspace/work/bookreaderm-product-quality/phase3-current.json`, `ci/`에 있다.
05ffd50 제품 소스를 변경하지 않은 문서 작업만 대기 중이며 0.6.0 설치 후보/키/main은 보존했다.

## 2026-10-29 현재 Phase3: TXT 목차 구현·로컬186 PASS, 기기 검증 대기

작업 위치 `/workspace/BookreaderM-reader-070`, 브랜치 `codex/reader-070`, `0.7.0-dev`/code8/Room7.
[구현·남은 범위](READER_0_7.md), [ADR0009](adr/0009-txt-toc-overrides.md), [로컬 원본](evidence/product-quality/phase3-toc-local/summary.json).
자동 감지/현재 회차/이동·제목 수정·제외/복구·현재 위치 수동 추가와 revision별 저장을 연결했다.
`source /workspace/toolchains/mobile-env.sh; BOOKREADER_EVIDENCE_DIR=build/phase3-toc-local ./scripts/ci-check.sh` exit0.
Android unit75/shared95/EPUB16, APK/test APK/lint/schema7 PASS. 최초95개 중 migration fixture1 FAIL도 원본 보존.
새 실제 UI1개를 포함한 native73+host7/API26/34/36과 ENOSPC는 원격 실행 대기다.
기존 TXT host 검사는 목차 추가 후 실제 force-stop 복원과 원본 진행도 불변도 검사하도록 확장했다.
자동 검사 실패·미실행 범위를 기기 PASS로 올리지 않는다. 형광펜/메모·독서 도구/빠른 설정 및 Phase4는 미완료다.
0.6.0 확정 source a22970b·같은 키 패키지는 `/workspace/BookreaderM`의 `codex/library-060`에 보존한다.
main·키·기존 설치 파일·사용자 책과 독서 기록을 삭제/초기화하지 않는다.
다음: 원격 전체 matrix와 실제 목차 화면 확인 → 실패 수정/증거 → 다음 Phase3 기능.

아래는 이전 단위의 이력이다.

## 2026-10-29 Phase3: TXT 목차 분석기 단위 PASS

`codex/reader-070`, `/workspace/BookreaderM-reader-070`. [개발 상태](READER_0_7.md).
6개 추가 포함 shared89 PASS, [원본](evidence/product-quality/phase3-toc-scanner-local/summary.json).
필수 사례 목록에6개를 등록했다. DB/UI는 아직 연결 전, 다음 단위에서 구현한다.
0.6.0 확정 결과는 아래와 별도 `codex/library-060`에 보존한다.

# 세션 인계

## 2026-10-29 현재 상태 — 0.6.0 자동 검증 완료

`codex/library-060`, 제품/테스트 소스 `a22970b1e40eaf06c47acaa683b9d754eb66f2b4`. [결과·소스·실제 화면](LIBRARY_0_6.md).
build40885971844 PASS175, device40885971811 API26/34/36 각각 native72+host7 PASS.
[원본 대조](evidence/product-quality/verified-a22970b/summary.json): artifact SHA/CRC/XML/source clean/PID 교체 확인, exit0.
[같은 키 패키지](evidence/product-quality/package-a22970b/summary.json): 서명/v2/v3/정적16KB/ZIP 동일성 PASS.
기존 서명키·0.5.1 APK·main을 보존했다. 실제 휴대전화 설치·공개 배포는 NOT_RUN이다.
API26의 저장 후 캡처는 이전 편집 창 프레임이며 시각 증거로 채택하지 않는다. API34/36 실제 서재는 확인했다.
abee6a1의 API36 Compose 측정 재진입은 후속 실행에서 재발하지 않았으나 원인은 미확정이다.

Phase3 작업 위치는 `/workspace/BookreaderM-reader-070`, 브랜치 `codex/reader-070`이다.
UTF-16·메모리 한도를 지키는 TXT 목차 분석기6개 포함 JVM89 PASS, DB/UI 연결은 진행 전이다.
다음은 목차의 revision별 수정/제외 저장과 실제 Reader 이동 검증이다. Phase3/4 전체 완료가 아니다.
각 shell은 `source /workspace/toolchains/mobile-env.sh`; 기존 도구를 사용하며 reset/clean·키 교체·앱 데이터 삭제를 하지 않는다.

아래는 당시 상태를 보존한 개발 이력이다. 최신 상태는 이 절과 위 최종 보고서를 따른다.

# 2026-10-29 Phase2 두 번째 기기 실패 수정 후보

현재 제품 fe29dea는 build40882879989 PASS175, device40882880009 세 API native72+host7 PASS다.
[원본](evidence/product-quality/verified-fe29dea/summary.json)의 SHA/CRC/XML/source clean/PID 교체를 확인했다.
마지막 변경은 테스트 캡처 동기화뿐이다. API26 서재 캡처가 닫히는 편집 창의 이전 프레임을 담아
창 소멸 검사 + 공용 렌더링 대기로 보완했다. compile/lint exit0(`phase2-capture-frame.log`).
다음: 후속 캡처/검사 확인 → 같은 키 fe29dea 패키지 승격 또는 같은 제품 소스 패키지 → 최종0.6.0 문서 정리.

최신: d93a268은 build40879397947 PASS175 및 device40879397948 세 API native72+host7 PASS,
[원본 대조 완료](evidence/product-quality/verified-d93a268/summary.json).
525587e는 build40879962358 PASS175, device40879962440 API26/36 PASS79/API, API34 동일 표지 재선택1/72 FAIL·host NOT_RUN.
[원본](evidence/product-quality/failed-525587e/summary.json). 빠른 동일 key 재선택이 StateFlow에서 합쳐지는 경합을
성공 선택 번호로 구분하도록 수정했다. compile/unit73/lint exit0(`phase2-cover-selection-version.log`).
다음 소스 전체 matrix와 최종 패키지/보고서를 완료한다. 525587e APK는 검증 실패 후보로 보존했다.

후속 d93a268의 build40879397947/device40879397948 실행 중에 실제 캡처에서 작은 생성 표지의
큰 글자 잘림을 확인했다. 장식 표지만 폭에 맞게 조정하고 접근 가능한 카드 본문 배율은 보존했다.
compile/unit73/lint exit0(`phase2-decorative-cover-size.log`). 최종 소스 matrix와 캡처 확인이 필요하다.

후속 로컬 `./gradlew :androidApp:compileDebugAndroidTestKotlin :androidApp:testDebugUnitTest :androidApp:lintDebug --console=plain` exit0. Android unit73/테스트 APK 컴파일/lint PASS. [로그](evidence/product-quality/phase2-followup-local/phase2-reopen-jump-fixes-final.log). 실제 기기 검증과 구분한다.

`codex/library-060`의 abee6a1: build40875430022 PASS175, device40875430229 FAIL.
API26 72개 중2 FAIL / API34 72개 중3 FAIL / API36 Compose 측정 예외로3개 중1 FAIL,69 NOT_RUN.
모든 host NOT_RUN. [원본](evidence/product-quality/failed-abee6a1/summary.json), [분석과 후속 수정](LIBRARY_0_6.md).
TXT 카드 버튼 실제 스크롤/실패 진단, 검색 작가 selector, EPUB replacement 후 검색 위치 callback,
강제 종료된 표지 임시 파일 정리와 host 검사를 보완했다. 다음 소스의 전체 native72+host7 검증이 필요하다.
API36 측정 재진입은 원인 미확정이며 재실행 성공만으로 해결이라 적지 않는다.
abee6a1 APK는 검증 실패 후보, 기존0.5.1과 키는 보존했다. Phase3/4는 미착수다.

## 2026-10-29 Phase2 첫 기기 결과와 후속 수정

후보 `f35d6857358501f87c3c15339b57f71bb6e7cc8c`: build40871727052 원본 XML/필수 사례/digest PASS175.
Device40871727016은 native71개 중 API26 8 FAIL, API34/36 각각3 FAIL, host는 NOT_RUN.
[실패 원본과 기기별 목록](evidence/product-quality/failed-f35d685/summary.json)을 보존했다.

- batch 취소의 IO 정리 후 취소된 Main으로 복귀하면서 마지막 UI 갱신이 실행되지 않았다. UI 게시까지 NonCancellable 경계에 포함했다.
- 표지 picker의 OPENABLE 누락으로 테스트 provider Activity가 실패했다. 실제 picker 요청을 OPENABLE/단일 content URI 검증으로 통일했다.
- TXT 인코딩 변경 직후 재열기 timeout을 확인했다. 늦은 artwork 결과가 Book 전체를 바꾸던 경합 가능성을 제거했다.
  현재 revision/timestamp를 확인해 표지 관련 필드만 병합하고, 인코딩 성공 시 commit된 Book을 즉시 UI에 게시한다. 기존 위치 assertion은 유지했다.
- API26 Window의 CalledFromWrongThreadException을 확인했다. catalog/진행률을 IO 안에서 따로 게시하지 않고
  책 목록과 함께 Main에서 단일 snapshot으로 게시한다. 시스템 바 효과도 명시적으로 Main에서 실행한다.
- 같은 표지 재선택, Reader/설정 왕복 스크롤 유지, 화면 밖 EPUB 메타데이터 검색을 보완했다. native 필수 사례는72개다.

후속 `./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug --console=plain` exit0.
로그 `phase2-coherent-library-state.log`, `phase2-native-fixes*.log`, `phase2-offscreen-metadata.log`.
새 소스의 API26/34/36 전체 회귀와 기존host6/새host1은 아직 대기다. 첫 실패를 이전 PASS로 덮어쓰지 않는다.
원본 캡처에서 책장/목록과 일괄 결과 UI를 확인했다. 1,000권 두 보기의 끝/처음/검색 탐색과24개 표지 캐시 제한은
세 API에서 PASS지만 전체 suite는 FAIL이다. 물리 FPS/성능이나 사용자 데이터 검증으로 확대하지 않는다.

# 2026-10-29 재개: Phase2 검증 진행 중

## 2026-10-29 진행 중: 서재 2.0 / 0.6.0

`codex/library-060`에서 [Phase2 구현과 검증 기록](LIBRARY_0_6.md)을 이어 간다. [ADR0008](adr/0008-library-catalog-and-covers.md).
메타데이터·실제/생성/사용자 표지·정보 편집·읽기 상태·책장·정렬/필터·검색 통합·다중 선택/분류·일괄 가져오기를 연결했다.
Room6은 별도 테이블을 추가하고 기존 책/진행도/북마크/설정과 키를 보존한다. main/0.5.1 설치 파일은 유지한다.

`source /workspace/toolchains/mobile-env.sh` 후 doctor PASS. `./gradlew :androidApp:compileDebugAndroidTestKotlin
:androidApp:lintDebug :androidApp:testDebugUnitTest :shared:jvmTest --console=plain` exit0, Android unit73/shared83 PASS.
원본 `/workspace/work/bookreaderm-product-quality/phase2-catalog-tests-fixed.log`.
이후 추가한 active-batch host 준비/검증은 전체 빌드와 실제 API26/34/36 실행을 기다린다.
필수 사례는 native71 + 기존host6 + 새host검증1이며, 강제 종료할 준비 단계를 PASS로 세지 않는다.
`BOOKREADER_EVIDENCE_DIR=build/phase2-local ./scripts/ci-check.sh` exit0, 전체172개/APK/test APK/lint/schema6 PASS. [원본](evidence/product-quality/phase2-local/summary.json). 실제 기기/UI/강제 종료 결과는 아직 NOT_RUN.
다음: 로컬 검사 완료 → 원격 matrix → 실패 수정/원본 증거와 화면 확인 → 동일 키 패키지/문서 갱신.

# 2026-10-24 진행 중: Phase 2 서재 2.0

Phase1을 아래 상태로 완료·보존한 뒤 `codex/library-060` 브랜치에서 순차 개발을 계속하고 있다.
현재 0.6.0/code7, Room6의 별도 서재 테이블·명시적5→6 migration, 사용자 편집 우선권·상태·책장·정렬/필터 저장,
용량 제한 표지 저장/EPUB·Comic 추출과 서재 UI 연결을 구현 중이다. 아직 Phase2 전체 완료나 기기 PASS가 아니다.
[ADR0008](adr/0008-library-catalog-and-covers.md). JVM82개 PASS; 중간 다중 책 fixture 해시 중복2 FAIL은 fixture의 서로 다른 SHA를 사용해 수정했다.
Android 첫 cover 컴파일은 cross-module nullable property smart cast1 FAIL 후 로컬 val로 수정했다.
중간 로그 `/workspace/work/bookreaderm-product-quality/phase2-*.log`, fixture 실패 원본 `phase2-storage-before/`.
다음 작업: 일괄 가져오기/중단 안내, 표지·서재 실제 Android 검사, 새 필수 사례 등록 및 전체 API26/34/36 회귀.
기존 키와0.5.1 설치 파일은 유지한다. 현재 작업을 reset/clean하지 않는다. `source /workspace/toolchains/mobile-env.sh`를 계속 사용한다.

# 2026-10-24 제품 완성도 개선 — Phase 1 완료

사용자의 [개발 지침](reference/BookReaderM_Product_Improvement_Request_2026-10-24.md)을 읽고
[전체 감사](PRODUCT_IMPROVEMENT.md) 후 v0.5.1/code6 UX 정리를 구현·자동 검증했다. 서브에이전트는 사용하지 않았다.
작업 위치 `/workspace/BookreaderM`, 브랜치 `codex/product-quality-051`. 제품/테스트 source `5a8aad2ebb35359740a2fe18bec0c3731d6e9072`.
이후 커밋은 문서/증거다. main790175b와 이전 0.5.0을 보존한다. [최종 보고서](UX_POLISH_0_5_1.md).

build40060195353 PASS166, device40060195360 API26/34/36 각각 native63+host6 PASS.
원본 digest/CRC/XML·필수 사례/source clean/실제 force-stop PID 교체와 화면을 확인했다.
[검증](evidence/product-quality/verified-5a8aad2/summary.json), [중간 실패/후보](evidence/product-quality/README.md).
실제 화면에서 발견한 큰 글자 카드 폭·다크 navigation을 보완하고 전체 회귀를 다시 통과했다.

최종 APK `/workspace/artifacts/BookreaderM/5a8aad2/installable/BookreaderM-0.5.1-test-signed.apk`.
SHA256 `44a8f8b84e3c7aeb86b77f537bd20ef8bac4d0ae7e35b037dd8cab1e78bf80af`.
기존 인증서/v2/v3/4 ABI/16KB 정렬 PASS, ZIP18,362,053 bytes의 CRC/추출 APK 동일성 PASS.
실기기 설치·대화 다운로드·공개 배포는 미완료다. 예전 업로드 HTTP400 경로를 반복하지 않는다.

기존 서명키·책·진행도·북마크·글꼴·설정·DB schema5를 유지했다. 실제 사용자 데이터는 CI에 올리지 않았다.
각 shell에서 `source /workspace/toolchains/mobile-env.sh`를 적용하며 도구를 재설치하거나 reset/clean·앱 삭제를 하지 않는다.
로컬 연결 기기/KVM은 없다. 다음은 [Phase2 서재2.0 착수 순서](PRODUCT_IMPROVEMENT.md#phase-2-착수-순서와-완료-기준)에 따라
메타데이터·표지/사용자 편집 우선권과 명시적 migration/보존 테스트부터 진행한다. Phase2–4 기능 구현은 미완료다.

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
