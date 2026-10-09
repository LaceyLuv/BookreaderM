# BookReader Mobile 새 컨텍스트 인계 — 2026-10-09

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
| M04 | IN_PROGRESS | epoch/sequence·조건부 commit·sampling 기반과 마지막 commit force-stop 복원만 검증; 아래 잔여 gate 필수 |
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
2. `persistStable`은 READY에서만 저장한다. **REFLOWING 중 close/ON_STOP가 reflow 직전 미커밋 이동을 flush하지 못할 수 있는 잔여 문제**를 해결·검증한다. 임시 0 locator/복원 callback이 기존 위치를 덮어쓰면 안 된다.
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
