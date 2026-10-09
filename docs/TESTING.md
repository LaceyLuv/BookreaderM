# 모바일 검증 계약

2026-10-09 갱신. 결과는 `PASS / FAIL / NOT_RUN / BLOCKED_ENV`로 구분한다.
테스트 파일의 존재, 환경 검사 성공, Activity 재생성은 각각 실제 테스트 통과나 프로세스 종료 검증을 대신하지 않는다.

## M02/M03 현재 검증 — 2026-10-09 재개 working tree

TXT 가져오기·실제 스크롤 독서 내부 알파 `0.2.0`의 source는 `2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf`다.
local full CI6와 remote build `37879616525`는 native Android unit 41/shared JVM 45 cases, failure/error/skipped 0,
lint 20 Hint와 APK/schema 검사 PASS다. 실제 API36 device `37879616383`은 native ordinary 25 cases/0 failures·errors·skips와
별도 host 2 stages PASS다. 필수 identity/source/PID 종료 proof/raw host stage를 확인했고 PNG 4개의 hash/1080×1920 decode도 PASS다.
Astra의 독립 evidence 검토로 좁은 TXT 내부 알파 M02/M03 gate를 승인했다. M04 전체 및 EPUB/Readium M01은 미완료다.
이전 `3ce3386` 외부 VIEW Reader ERROR와 `cfe17f0` DATABASE_UNAVAILABLE는 아래 이력으로 보존한다.
Room 공유/단계 진단 변경 후 외부 VIEW와 owner/Reader 회귀가 PASS한 것은 검증 결과이며, 이전 SQLite BUSY 원인 확정은 아니다.

| 수준 | 현재 결과 | 검사 범위 / 증거 |
|---|---|---|
| 역사 source host Kotlin/JUnit | PASS | `fdd9ac1` encoding-only 13 cases; 최초 native CI XML 보존, `encoding-isolated/`는 명시한 역사 source 재실행 |
| Android ICU 차이 수정 host Kotlin/JUnit | PASS | 현재 strict EUC-KR grammar 포함 14 cases failure 0/exit 0; `encoding-icu-fix-isolated/` |
| 자체 작성 fixture manifest | PASS | 11개 actual byte size/SHA-256 일치, CC0-1.0; `fixtures/encoding/manifest.json` |
| Android unit 통합 중간 실행 | PASS | attempt 1 실제 XML 31 cases/failure·error·skipped 0, exit 0; `android-unit-attempt-1-summary.json`; 이후 source 변경으로 최종 CI 재실행 필요 |
| Shared 실제 DB 통합 중간 실행 | PASS | attempt 1 실제 XML 40 cases/failure·error·skipped 0, exit 0; `shared-tests-attempt-1-summary.json`; 추가 회귀 후 최종 CI 재실행 필요 |
| 전체 CI attempt 1 | FAIL | Android unit 36/shared 43 cases는 PASS이나 lint 2 errors로 exit 1; `local-ci-attempt-1/summary.json` |
| 전체 CI attempt 2 | PASS(중간) | Android unit 37/shared 43 cases, failure·error·skipped 0; lint 20 Hint·APK/schema PASS, source drift 2 files로 최종 검증에 사용하지 않음 |
| `fdd9ac1` assemble/lint/Android unit/shared DB/schema | PASS | source drift 0의 attempt 3, exit 0; Android unit 37/shared 43 cases failure·error·skipped 0, lint 20 Hint, 양쪽 APK/schema v2 PASS |
| 동일 source 원격 build | PASS | run `37167947509`, 실제 native XML Android unit 37/shared 43 failure·error·skipped 0, lint 20 Hint, schema 1/2 local bytes 일치 |
| `fdd9ac1` 실제 Android provider/intent/charset/Reader UI | FAIL | run `37167947545`, native connected 23 cases/14 failures/0 errors·skips; `remote-fdd9ac1-device/summary.json` |
| 수정 `9eb8f78` 최종 로컬 CI | PASS | attempt 4 exit 0/source drift 0, Android unit 38/shared 43 failure·error·skipped 0, lint 20 Hint, 양쪽 APK/schema PASS |
| 수정 `9eb8f78` 원격 build | PASS | run `37169215834`, 실제 native 38 Android unit/43 shared failure·error·skipped 0, lint 20 Hint, local byte 동일 schema 1/2 |
| `f3d4c38` 실제 Android 실행 | FAIL | native 23 cases/11 failures/0 errors·skips; encoding 3 모두 PASS, link denial/intent identity/asset fixture 실패 증거 보존 |
| `cfe17f0` 로컬 CI5 | PASS | 38 Android unit/43 shared failure·error·skipped 0, lint 20 Hint, 양쪽 APK/schema PASS, source drift 0 |
| 최종 asset fixture 수정 compile/lint | PASS | instrumentation Java 1 file만 변경, 실제 test APK compile/assemble +lint exit 0; production/unit/shared input hash는 CI5와 동일 |
| `cfe17f0` 원격 build | PASS | run `37171679446`, native 38 Android unit/43 shared failure·error·skipped 0, lint 20 Hint, schema 1/2 local 일치 |
| `cfe17f0` 실제 Android | FAIL | run `37171679395`, native 24 cases/1 failure/0 errors·skips; import 13·encoding 3 PASS, Reader progress read DATABASE_UNAVAILABLE |
| `3ce3386` observer fixture compile/lint | PASS | test-only observer/diagnostics 수정; exit 0/source drift 0, production/unit/shared inputs는 CI5와 동일 |
| 재개 기준 `3ce3386` 원격 build | PASS | run `37172491541`, native Android unit 38/shared 43 failure·error·skipped 0, lint 20 Hint, schema 1/2 일치; `remote-3ce3386-build/` |
| 재개 기준 `3ce3386` 실제 Android | FAIL | run `37172491508`, native 24/1 failure/0 errors·skips, 인코딩 3/두 ReaderFlow PASS, 외부 VIEW Reader ERROR; `remote-3ce3386-device/` |
| 2026-10-09 재개 doctor | PASS | exit 0; 이전 JDK/SDK/proxy TLS 유지, local adb 연결 기기 0; `resume-2026-10-09-baseline.json` |
| `2be610a` full local CI6 | PASS | actual exit 0, native Android unit 41/shared JVM 45 failure·error·skipped 0, lint 20 Hint, 앱/test APK/schema PASS, source drift 0; `local-ci-attempt-6/` |
| `2be610a` 원격 build | PASS | run `37879616525`, native 41 Android unit/45 shared JVM failure·error·skipped 0, lint 20 Hint, schema 1/2 local 일치; `remote-2be610a-build/` |
| `2be610a` actual Android | PASS | run `37879616383`, native ordinary 25 cases failure·error·skipped 0, owner·외부 VIEW·SAF/SEND·encoding·Reader 모두 PASS; `remote-2be610a-device/` |
| `2be610a` host force-stop/restore | PASS | exact raw 2 stages, PID 5624→외부 force-stop→absent→cold PID 5671, commit offset 11607/epoch 1/sequence 2 보존→서재 Continue |
| 자체 fixture PNG 4개 | PASS | `m02-library`, `m03-reader`, `txt-reader-generated-scroll`, `txt-reader-generated-host`; SHA-256 및 PNG 1080×1920 decode 확인 |
| `9eb8f78` device workers 1/2 | BLOCKED_ENV | 두 실행 모두 KVM read/write permission 문제로 tests 시작 전 exit 1; 앱 회귀 FAIL과 구분 |
| host-driven force-stop/relaunch/continue | PASS | `2be610a`의 외부 adb/runner 2 stages; 미커밋 위치 보존/전체 M04 SAFE PASS를 뜻하지 않음 |
| 실기기 5MiB/50MiB p95·메모리·OEM·TalkBack | NOT_RUN | 에뮬레이터 correctness와 별도 gate |
| EPUB/Readium | NOT_RUN | TXT 우선 작업에서 보류; Android 인코딩 결과로 대신하지 않음 |

현재 `/workspace/bookreader-mobile-0.2.0-debug.apk`는 `2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf` source다.
SHA-256 `021ec47de8311435f6becd0eb5b6e677ed22312b6ef3740b86d86b60d10a6e5e`, 17,027,692 bytes이며 versionName `0.2.0`/versionCode 2다.
실제 aapt/apksigner는 exit 0이고 기존 local signer `aa02ee8ea2a0debb252a9b18033f730c85abb5c218fa06440740546f7dda481d`를 유지한다.
`final-local-apk.json`에 metadata를 기록했고 제공 복사본의 SHA-256도 일치한다. 이전 APK는
`/workspace/bookreader-mobile-0.2.0-debug-before-2be610a.apk`에 보존하며 `final-local-apk-before-2be610a.json`이 당시 metadata다.
CI6 source fingerprint는 `85556d8e9ae941153761b22791bc34fdc71e11aaee890e5da474df9d8f31e3a0`이며 실행 중 drift는 0이다.
동일 source의 actual Android/host gate는 PASS다. 기기 job이 설치 APK hash를 보존하지 않으므로 이 local debug APK를
기기에서 설치한 APK와 byte 동일하다고 주장하지 않는다. independently built CI APK는 별도 hash/서명이다.

[최종 device summary](evidence/m02-m03-validation/remote-2be610a-device/summary.json)는 native connected XML/host raw stage와 검증 결과를 기록한다.
원격 artifact `11593602844`의 실제 XML은 [native-connected](evidence/m02-m03-validation/remote-2be610a-device/native-connected/), raw host proof는
[host-process-proof](evidence/m02-m03-validation/remote-2be610a-device/host-process-proof/), 명령 기록은
[command-evidence](evidence/m02-m03-validation/remote-2be610a-device/command-evidence/), PNG는
[fixture-screenshots](evidence/m02-m03-validation/remote-2be610a-device/fixture-screenshots/)에 보존한다.
준비/복원 instrumentation의 exact start/pass/finish가 확인되고,
실제 외부 adb 종료/PID proof와 committed offset 11607/sequence 2/epoch 1 이후 cold library Continue 복원을 증명한다.
verification instrumentation이 또 다른 target process를 시작할 수 있어 launcher PID 지속 생존이나 미커밋 scroll 보존은 보장하지 않는다.
로컬 CI6의 native XML과 manifest는 `local-ci-attempt-6/`의 `native-*`/`native-evidence-manifest.json`,
원격 build의 native XML/schema는 `remote-2be610a-build/native-android-unit/`, `native-shared-jvm/`, `native-schemas/`에 보존한다.

다음은 이전 `cfe17f0`/`3ce3386`의 local 검증 이력이다.
변경 전 local 검증은 `local-ci-attempt-5/summary.json`의 full CI와 `local-asset-fixture-validation/summary.json`의
최종 test fixture compile/lint다. 각각 fingerprint는 `1b66992ce15236e6acc0b52f1465bde9af2f7d18fd94e6c9e25c2bd5eead7949`,
`94d162eadf2669eb62f376efec97f077f2e0377baf1f3387a786da3bde391040`이며 각 실행의 drift는 0이다.
최종 fixture 변경은 production/unit/shared test inputs를 바꾸지 않았음을 hash로 확인했다.
당시 version/signature와 source 관계는 `final-local-apk-before-2be610a.json`에 기록한다. `cfe17f0`의 actual ordinary는 아래 기록한 24/1 FAIL이며
이후 `3ce3386` ordinary 결과는 24/1 FAIL이며 host 2 stages는 NOT_RUN이다.

이후 `3ce33868503bac2dd0ecd3829a7bf1a77d562f90`는 Flow/host 테스트와 진단 helper만 수정하여 앱 APK 바이트는 그대로다.
`local-reader-observer-validation/summary.json`의 compile/assemble/lint는 actual exit 0, lint 20 Hint, 104-file drift 0이다.
fingerprint는 `a0cc88b0119f7b5db7df90c0f46d08e110cb27919fa9b89bf97840c1217136e3`이며 production/unit/shared/config input hash는 CI5와 같다.
test APK SHA-256은 `b3c07f6b39187777f686d34693122e6e30b7ce37f168353aa772df4532c2e3c4`다.
`remote-3ce3386-build/summary.json`의 실제 38/43-case XML은 PASS다.
`remote-3ce3386-device/summary.json`은 ordinary 24/1 FAIL을 기록한다. 외부 VIEW 실패는 import 완료 뒤
Reader ERROR이며 이전 phase-only 진단에서 내부 원인이 빠졌다. 측정 행·durable commit·cache loss·Continue·recreation
테스트가 PASS한 것은 해당 실행의 결과이며 외부 VIEW 실패를 상쇄하지 않는다. host는 NOT_RUN, PNG pull은 NOT_COLLECTED다.

다음은 이전 `cfe17f0` 실행의 진단 이력이다.
`remote-cfe17f0-build/summary.json`은 native 38/43 PASS를, `remote-cfe17f0-device/summary.json`은 native 24/1 FAIL을 보존한다.
실패 method는 `measuredCanonicalLineIsSavedAndContinueRestoresAfterCacheLossAndRecreation`이며 fixture `saved(book)` read가
repository의 DATABASE_UNAVAILABLE로 매핑되었다. 원본에 내부 exception이 없어 Room 초기화 contention/SQLITE_BUSY는
확인되지 않은 가설이다. test observer는 각 Flow/host stage의 행동 전에 DB 하나를 열고 stage 종료까지 유지하도록 바꿨다.
Error→Missing 전환, error retry, assertion 완화는 없고 Error 발생 시 raw DAO 진단을 첨부하고 계속 실패시킨다.
production DB/writer를 고쳤다고 주장하지 않는다. host는 NOT_RUN, screenshot pull은 NOT_COLLECTED다.

다음은 이전 `9eb8f782d996fea9c0f04006e39fa72d9a3979ff` 로컬 명령이다.
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-4 ./scripts/ci-check.sh`다.
`local-ci-attempt-4/summary.json`/native XML은 38 Android unit/43 shared, failure/error/skipped 0을 기록한다.
source fingerprint SHA-256은 `04cf3d59249644f9b3a44ac8dbcfd2d1a1feb76f49edc3f05ba045c6f6081e19`이며 drift 0이다.
당시 제공 APK SHA-256은 `2129f9c94914c1385f4cc5cbc900f51a851e4cffa8c8460a4f92d17bc0a32d10`이며 이후 최신 파일로 교체했다.
같은 source의 원격 build [37169215834](https://github.com/LaceyLuv/BookreaderM/actions/runs/37169215834) native artifact
`11290199967`의 XML/CI log를 다운로드하여 같은 38/43 count와 실패·건너뛰기 0을 확인했다.
`remote-9eb8f78-build/summary.json`/`workflow.json` 및 native reports에 기록했다. schema 1/2는 local과 byte 동일하다.

다음은 수정 전 `fdd9ac1` 로컬/원격 CI 기록이다. 당시 최종 명령은
`BOOKREADER_EVIDENCE_DIR=docs/evidence/m02-m03-validation/local-ci-attempt-3 ./scripts/ci-check.sh`다.
같은 production/test/build source는 commit `fdd9ac15182af4e4c1a21a814c5d9c2aa3393808`로 기록했다.
`local-ci-attempt-3/summary.json`, `results.txt`, 각 log와 native XML `reports/`에 exit/count/hash를 기록했다.
`local-ci-attempt-3-source-hashes.json`의 SHA-256은
`13a7a332f8f01f2f6b4ba1d32c4cbe74e765262f5d8ef98b84c4f266d3365f4d`이며 시작/종료 파일 hash 변경이 없다.
로컬 debug 앱 APK SHA-256은 `c35e01f01d08b44747c389d40665f799bd1f15ff70bff899f3cee945ed155720`이다.
당시 제공 복사본은 이후 수정 APK로 교체되었다. 이전 로컬 M00 debug signer를 유지하되
과거 CI signer와는 다르므로 기존 CI APK 설치본의 데이터 유지 업데이트를 보장하지 않는다.
이 로컬 APK를 미래 원격 APK와 같은 바이트라고 가정하지 않는다.

동일 source `fdd9ac1`의 원격 build [37167947509](https://github.com/LaceyLuv/BookreaderM/actions/runs/37167947509)도 success다.
job `111334760116`/artifact `11290761635`의 실제 native XML을 다운로드하여 위 37/43 count와 실패·건너뛰기 0을 확인했다.
증거는 `remote-fdd9ac1-build/workflow.json`, `summary.json`, `reports/` 및 원본 CI log에 있다.
schema 1/2 export는 local과 byte-for-byte 일치한다. 원격 APK는 별도의 byte hash/서명을 기록하며 local APK와 동일성을 가정하지 않는다.

격리 검사는 cached compiler/JUnit로 Android/공유 앱 소스 전체와 독립적으로 실행한다.
정확한 command/exit/source hash는 `encoding-isolated/results.json`에 기록했다.
이전 디렉터리를 재사용한 14-case 결과는 `encoding-icu-fix-isolated/`에 분리했다. `encoding-isolated/`는 Git에 보존된
원래 `fdd9ac1` source를 실제 다시 실행한 역사 증거이며 최초 실행 결과인 것처럼 재작성하지 않는다.
현재 source 검사는 아래 ICU 수정 디렉터리의 명령을 사용한다.

```bash
python3 docs/evidence/m02-m03-validation/encoding-icu-fix-isolated/run.py
```

전체 build/test는 기존 `ci-check.sh`가 담당한다. 신규 `scripts/required-tests.json`과 XML verifier가 실제 필수
test identity, 실패/error/skipped를 확인한다. empty XML, stale result 또는 missing case를 PASS로 처리하지 않는다.
신규 추가 case는 실행 manifest와 실제 source를 함께 확인한다.

ordinary Android 기기 검사는 AGP/UTP의 native connected XML을 사용한다. host-driven 검사는
`scripts/reader-process-check.py`가 준비 단계 instrumentation → 살아 있는 앱 PID 확인 → 외부 `adb force-stop`
→ 종료 확인 → 앱 재실행/서재/이어읽기 검증 단계 instrumentation을 순서대로 수행한다.
`TEST-reader-host-process.xml`은 helper가 실제 adb instrumentation의 정확한 start/pass/finish와 test identity를
검사한 뒤 만든 결과이며 native AGP XML이 아니다. ordinary connected cases와 2 host stages를 하나의 connected suite로 합치지 않는다.
`3ce3386`의 ordinary는 24개였고 Room owner 회귀를 추가한 재개 source의 필수 ordinary는 25개다.
ordinary connected 실행 뒤 host 검사를 위해 APK를 유지하는 AGP 8.11.1 공식 stable option
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`를 검증 스크립트에 적용했다.
공식 AGP source/API 확인은 `agp-test-lifecycle-api.json`, script-only 교정은 commit `d8e4ec9`에 기록했다.
앱/test source 및 로컬 제공 APK는 바꾸지 않았다. 기존 device 실행이 진행 중인 시점의 API 교정이며,
원본 실행의 host 실패를 관찰했다는 뜻이 아니다.
raw stage 로그, PID·명령·종료 proof를 XML과 함께 보존한다. 준비 instrumentation 종료 뒤 앱 프로세스가 없다면
host는 앱을 명시적으로 실행하여 실제 살아 있는 PID를 확보한 뒤 강제 종료하며 이 경로도 proof에 기록한다.
복원 확인 instrumentation은 launcher가 만든 프로세스와 다른 target 프로세스를 시작할 수 있다.
force-stop 이후 보존 기준은 마지막 DB commit 완료 locator다. 미커밋 화면 위치나 하나의 launcher 프로세스가
verification 끝까지 계속 살아 있었다고 보장하지 않는다.

`9eb8f78`의 두 KVM worker 차단 뒤 공식 `udevadm settle`의 bounded queue synchronization을 workflow에 추가했다.
API 근거는 `udev-synchronization-api.json`, workflow-only commit은 `f3d4c38e2bbe2bb50c98d5da4e5dfaaca0376c26`다.
KVM read/write 검사를 그대로 유지하며 실제 성공 전에는 permission 문제가 해결되었다고 주장하지 않는다.
이 실행의 앱/test/APK source는 `9eb8f78`과 같고 workflow만 다르다.
새 run `37169506738`에서는 실제 KVM setup 검사가 PASS하여 Android instrumentation 실행에 진입했다.
그 뒤 내려받은 native XML은 ordinary 23 cases/11 failures/0 errors/0 skipped다. 인코딩 3 cases는 모두 PASS이고
관리 파일의 SELinux link denial 및 intent/asset fixture 실패를 확인했다. `remote-f3d4c38-device/summary.json`에
원본 실패를 보존했다. 당시 host는 NOT_RUN이었으며 이후 `cfe17f0` ordinary와 최종 `2be610a` host 결과는 위 이력에 별도 기록한다.

```bash
./scripts/device-check.sh
python3 scripts/reader-process-check.py build/host-process-evidence
```

스크린샷은 CI의 자체 작성 fixture 화면만 `BOOKREADER_CAPTURE_FIXTURE_SCREENSHOTS=true`로 캡처한다.
로컬 기본값은 꺼져 있어 사용자의 책 화면을 자동으로 기록하지 않는다. 스크린샷은 실제 기능/종료 검증 결과를 대신하지 않는다.

| 신규 검사 소스 | 책임 |
|---|---|
| `TxtDecoderTest` | BOM·short reads·strict UTF/legacy malformed·CRLF/surrogate 경계·bounded prefix·late error·actual source byte limit·cancel/time budget |
| `AndroidTxtEncodingTest` | Android 실제 CP949 확장/EUC-KR 구분, UTF BOM/canonical 경계, 늦은 incomplete 문자 거부; 필수 3 cases |
| `ImportCoordinatorTest` | 실제 DB+journal과 filesystem port의 checkpoint fault/recovery·중복 progress 보존·권한과 무관한 관리 복사본·삭제 무효화 |
| `MigrationTest` | Room schema 1→2에서 기존 book/progress 유지; destructive fallback 없이 reopen |
| `RoomProgressWriterTest` | 세션/revision/sequence 조건부 저장과 stale write 거부; 실제 DB integration |
| `TxtCanonicalCacheTest` | bounded fragment coverage·5MiB 단일 문단·cache 손실 재구축·취소와 같은 길이 source/cache 변조 거부 |
| `DatabaseFailureDiagnosticTest`/writer/controller 회귀 | 고정 stage/오류 코드와 bounded 클래스 chain, 진단 callback 실패 시 기존 Error 결과 및 기록 보호 |
| `AndroidDatabaseOwnerTest` | 12 concurrent borrowers 동일 instance, library lease close 뒤 3회 fresh Reader/저장과 독립 observer; 직접 DAO fixture를 만들며 import 자체 테스트는 아님 |
| 강화한 외부 VIEW 회귀 | 실제 provider import→Reader READY 뒤 owner 동일성/library 조회로 import/Reader borrower 수명 검증 |
| Android provider/Reader integration | 실제 `TestDocumentsProvider`, 외부 intent, TXT import→scroll→저장→서재→이어읽기; `2be610a` ordinary 25 PASS; 외부 VIEW/SAF/SEND와 두 ReaderFlow 포함 |

수동 확인에는 [자체 작성 UTF-8 예제](../fixtures/encoding/bookreader-demo-ko-utf8.txt)를 기기 Downloads 등 선택 가능한
위치에 복사하고 `TXT 추가`에서 선택한다. 가져오기 완료의 `읽기`를 누르고 여러 화면을 스크롤한 뒤 서재로 돌아와
이어읽기를 연다. 외부 프로세스 종료 검증은 별도 host 명령과 DB commit 관찰 결과를 기록해야 한다.
어떤 검사도 개인 책 본문/URI를 로그로 출력하지 않는다.

아래 M00 섹션은 완료 당시의 증거와 검사 수준을 보존한 이력이다. M00의 case count와 APK identity를
신규 working tree 결과로 취급하지 않는다.

## M00 실행 증거

Source `3f04b8ba53d23846a454ee744c5e1fc33bea45e3`의 전체 `ci-check`는 exit 0 (`PASS`)이다.
실제 task discovery, debug 앱/test APK assemble, lint, Android unit, shared JVM tests와
Room schema export를 확인했다. 실제 XML은 Android unit 10/shared 19 cases,
failure/error/skipped 모두 0이다. Lint XML은 informational `Hint` 20건만 기록하며
`AndroidGradlePluginVersion` 3/`GradleDependency` 9/`NewerVersionAvailable` 8건이다.
고정 pin의 upgrade 안내는 활성화된 상태로 남겼고 다른 warning/error는 fatal이다.

| 증거 | 경로 |
|---|---|
| 전체 명령별 exit/log | `docs/evidence/m00-validation/ci/results.txt`, `ci/*.log`, `ci-check.log` |
| 결과·source SHA·APK/schema hash | `docs/evidence/m00-validation/final-validation-summary.json` |
| Android unit 실행 XML | `docs/evidence/m00-validation/final-reports/androidApp/` |
| shared/실제 Room 실행 XML | `docs/evidence/m00-validation/final-reports/shared/` |
| lint XML/HTML | `docs/evidence/m00-validation/final-reports/lint-results-debug.*` |
| 최초 shared/lint 실패 이력 | `docs/evidence/m00-validation/attempt-1-shared-failure/`, `attempt-1-lint-failure/` |

동일 source `3f04b8ba`의 실제 API36 Google APIs x86_64 instrumentation 5 cases도 `PASS`다.
실행 `37164061580`의 XML은 failure/error/skipped가 없고 필수 5개 case를 모두 포함한다.
기기 identity·필수 명령 결과/발췌·원격 metadata·실제 XML/hash는
`docs/evidence/m00-validation/device-37164061580/` 및 `verified-summary.json`에 있다.
원본 전체 job log는 Git 제외 경로 `build/device-ci-artifacts/37164061580/job-111323264829.log`에
보존하고 SHA-256은 위 summary에 기록한다. 원격 run의 job 화면에서도 전체 로그를 확인할 수 있다.
원격 기본 CI `37164061596`도 success이며 같은 29-case XML과 schema를 확인했다.
10월 1일 환경 차단 및 수정 전 원격 CI 실패는 WORKLOG와 기존 로그에 보존했다.

## 로컬/CI 실행

JDK 17과 Android platform 36/build-tools 35.0.0/platform-tools, Gradle/Maven 저장소 접근이 필요하다.
아래 명령은 모바일 루트에서 실행한다.

```bash
./scripts/doctor.sh
./scripts/ci-check.sh
```

`ci-check.sh`는 `./gradlew --version`, `projects`, `:androidApp:tasks --all`, `:shared:tasks --all`을 실제 실행한다.
이 목록에 `assembleDebug / lintDebug / testDebugUnitTest / assembleDebugAndroidTest`, `jvmTest`가 있는지 확인한 다음
다음 명령을 실행한다. 2026-10-03 실제 plugin 제공 여부를 확인했다.
누락되면 실패하며 임의 no-op task로 대체하지 않는다.

```bash
./gradlew --no-daemon --console=plain --stacktrace \
  :androidApp:assembleDebug :androidApp:lintDebug \
  :androidApp:testDebugUnitTest :androidApp:assembleDebugAndroidTest :shared:jvmTest
```

`shared:jvmTest`는 common tests와 실제 file-backed Room DB smoke의 실행 경로다.
`assembleDebugAndroidTest`는 instrumentation APK 컴파일이며 기기 테스트 실행을 의미하지 않는다.
기기 연결 후 실제 `:androidApp:tasks --all`에서 `connectedDebugAndroidTest`를 확인한 뒤 실행한다.

```bash
./gradlew --no-daemon --console=plain :androidApp:connectedDebugAndroidTest
```

명령 실행 후 확인할 경로는 아래와 같다. 최종 성공 여부는 XML/log/schema 확인 결과를 따른다.

| 산출물 | 생성 경로 |
|---|---|
| Debug 앱 APK | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| 같은 source의 실제 GitHub CI 앱 APK | `build/deliverables/m00-3f04b8b/androidApp-debug.apk` |
| Android unit XML/HTML | `androidApp/build/test-results/testDebugUnitTest/`, `androidApp/build/reports/tests/testDebugUnitTest/` |
| shared XML/HTML | `shared/build/test-results/jvmTest/`, `shared/build/reports/tests/jvmTest/` |
| lint | `androidApp/build/reports/lint-results-debug.html` |
| 기기 UI 결과 | `androidApp/build/outputs/androidTest-results/`, `androidApp/build/reports/androidTests/` |
| Room processor export | `shared/schemas/` |
| 다음 ci-check 명령 증거 | `build/ci-evidence/` |

현재 시도의 원본 실행 로그는 `docs/evidence/`에 있으며 이후 ci-check 로그에 덮어쓰이지 않는다.
GitHub 게시를 위해 기존 환경·원문 보존·정적 검사 로그는 `docs/evidence/verification/`,
기존 ci-check 출력 snapshot은 `docs/evidence/ci-evidence/`에 복사해 commit에 포함한다.
`build/ci-evidence/`는 앞으로 실행할 ci-check의 실제 출력 경로다.
GitHub workflow는 필수 checks 및 instrumentation APK compile을 실행하도록 작성했다.
10월 1일 기본 원격 CI run `36805008404`는 SDK 설정과 debug APK packaging 뒤 Android test Kotlin compile에서 실패했다.
`assertDoesNotExist`의 잘못된 extension import를 공식 Compose API와 대조해 제거했고
수정 후 최신 source의 instrumentation APK compile 및 실제 5-case 실행은 PASS다.
실패 실행과 수정 후 실행을 구분하여 WORKLOG에 기록한다.

실제 연결 기기/에뮬레이터가 있는 환경에서는 아래 wrapper를 실행한다.

```bash
./scripts/device-check.sh
```

이 스크립트는 source SHA·working tree, device fingerprint/API/ABI, 실제 task 목록을 기록하고
`--rerun-tasks :androidApp:connectedDebugAndroidTest`를 실행한다. 이전 generated XML을 지운 뒤
새 XML에서 실패/error/skipped가 없는지와 필수 case 5개가 모두 실행됐는지 확인한다.
`build/device-evidence/`의 로그와 `androidApp/build/outputs/androidTest-results/connected/` XML을 함께 보존한다.
`.github/workflows/mobile-device-tests.yml`은 GitHub Ubuntu 24.04 KVM에서 API 36 Google APIs x86_64
에뮬레이터로 같은 명령을 실행한다. workflow 작성·APK compile은 UI PASS 증거가 아니다.
로컬 adb 부재의 최초 차단은 `docs/evidence/device-local-blocker/results.txt`에 보존했다.
adb 설치 후 재실행도 연결된 authorized device가 없어 exit 78이었다.
현재 증거는 `docs/evidence/device-local-blocker/resumed/results.txt`이며 로컬 `/dev/kvm`도 없다.
SDK emulator37.1.11/command-line tools19.0 및 emulator runner v2.34.0 commit의 공식 근거는
`docs/evidence/device-local-blocker/toolchain-pins.json`에 있다. Synthetic verifier 검사는 UI 실행이 아니다.

환경 없이 실행 가능한 스크립트 오류 전달 검사는 아래 명령이며 현재 exit 0이었다.
실제 성공·명령 실패·환경 차단·로그 쓰기 실패를 구분하는 검사로 앱 build/test를 대신하지 않는다.

```bash
bash -n scripts/doctor.sh scripts/ci-check.sh
sh -n gradlew
python3 docs/evidence/ci-helper-harness.py
```

아래 테스트는 M00에서 실제 통과했으며 각 테스트의 책임은 다음과 같다.

| 소스 | 현재 검사 내용 |
|---|---|
| `shared/src/commonTest/.../LocatorCodecTest.kt` | TXT/EPUB/Comic envelope round-trip, UTF-16 Long offset, 미지원/손상 locator 거부 |
| `shared/src/commonTest/.../BookModelTest.kt` | 내부 관리 상대 경로 모델 계약 |
| `shared/src/jvmTest/.../RoomDatabaseSmokeTest.kt` | 실제 파일 DB 생성·삽입·조회·재개방, FK, 중복 삽입 시 기존 progress 보존, 손상/미지원/revision 불일치 기록 보존, 닫힌 DB 오류 구분 |
| `androidApp/src/test/.../AppViewModelTest.kt` | Loading/Empty/Error/retry, open 실패, 취소 시 close, 탭/테마 실패 처리 |
| `androidApp/src/test/.../LibrarySearchTest.kt` | 메타데이터 부분 문자열과 `%`/`_` literal 검색 |
| `androidApp/src/androidTest/.../BookReaderScreenTest.kt` | 3탭/검색/테마/각 상태·재시도 Compose UI |
| `androidApp/src/androidTest/.../AndroidThemeStoreTest.kt` | 실제 SharedPreferences 저장·조회와 손상 설정 보존 |
| `androidApp/src/androidTest/.../MainActivityLaunchTest.kt` | 서재 첫 화면, Activity recreation 중 탭 유지; process-death 검사는 아님 |

schema는 Room processor의 실제 export가 성공해야 증거가 된다.
`exportSchema=true` 설정만으로 schema 생성 완료를 기록하지 않는다.
다음 버전 migration 검증은 M10 범위다.

## 검사 수준

| 수준 | 검증 책임 | 다른 검증을 대신하지 않는 범위 |
|---|---|---|
| doctor | JDK/SDK/wrapper/기기 환경의 사전 검사 | dependency resolve, build, test 결과 |
| common tests | 버전·revision이 있는 포맷별 locator 직렬화와 오류 거부 | Readium 실제 복원, TXT renderer |
| 실제 DB integration | Room/bundled SQLite open, insert, query, FK, 데이터 보존 | SAF/provider, migration upgrade, native 기기 호환 |
| Android unit | UI 상태와 navigation/설정 로직 | 기기 UI, Android text measurement |
| Android instrumentation | Activity/Compose 경로와 실제 기기 DB | host가 제어하는 프로세스 종료, 실기기 성능 |
| host-driven process | 외부 adb/runner의 종료·재실행 | 미커밋 상태 보존, 전원 차단 |
| 실기기 | TalkBack/배율/OEM/성능/ABI·16KB | 소스 리뷰만으로 판정 불가 |

## M00 완료 당시 이후 작업의 검사 — 이력

M00는 Reader가 없으므로 진행도 저장 경쟁, 마지막 커밋 위치 복원, 이어읽기 및 process-death는 `NOT_RUN`이다.
M03/M04에서 실제 import→독서→저장→외부 종료→서재→이어읽기 경로와 함께 구현·실행한다.
M01의 EPUB Locator round-trip/위치 복원/publication script 및 외부 리소스 차단, CP949 확장 문자 검사는 별도다.
M10의 migration upgrade, 최종 APK native ABI/16KB, cloud/D2D 백업 동작 및 실기기 수치 성능도 M00 통과로 대체하지 않는다.

사용자 책 본문·개인 URI·서명키를 로그나 CI artifact에 넣지 않는다.
테스트는 자체 생성 메타데이터·locator를 사용하고 삭제/skip/ignoreFailures로 실패를 숨기지 않는다.

M00 완료 당시 M01은 `TODO`였다. 현재는 Android 인코딩 부분 PASS/Readium 미완료로 `IN_PROGRESS`다.
당시 다음 입력은 검증된 M00 source·정확 pin·Room v1 schema와 위 실행 증거였다.
Readium stable 후보 3.4.0의 소비 요구/API/라이선스를 새로 확인한 뒤 필요한 Android adapter와
격리된 debug/test probe를 만든다. locator round-trip/복원, trusted engine와 비신뢰 publication
script·외부 URL 차단, Android strict encoding/CP949 fixture를 각각 실제 실행한다.
M00 PASS를 Readium 호환성이나 Reader/import/process-death PASS로 확대하지 않는다.

같은 source의 원격 기본 CI `37164061596`도 success이며 다운로드한 실제 보고서는
`docs/evidence/m00-validation/build-37164061596/reports/`에 있다.
원격 Android unit10/shared19 cases의 failure/error/skipped는 0이고 lint는 20 Hint다.
실제 CI APK는 [GitHub artifact](https://github.com/LaceyLuv/BookreaderM/actions/runs/37164061596/artifacts/11288751988)와
위 `build/deliverables/`에 있으며 크기는 16,629,841 bytes, SHA-256은
`716739c3471b4a21d689396e30b2f850b0bd393abc7ed2681506ec91a8edee82`다.
로컬 APK와 CI APK의 hash는 각각 기록하며 동일 바이트 재현성을 주장하지 않는다.
