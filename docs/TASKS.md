# BookReader Mobile — Codex 작업표

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
| M01 | epub-and-encoding-feasibility | IN_PROGRESS | strict grammar host 14 + 실제 Android 인코딩 3 cases PASS; Readium/EPUB 보류로 전체 M01 미완료 |
| M02 | managed-import-and-library | DONE | TXT 내부 알파: `2be610a` local/remote CI 41/45 및 Android 25·host 2·fixture PNG PASS, [WORKLOG](WORKLOG.md) |
| M03 | txt-scroll-progress-and-continue-bar | DONE | TXT 내부 알파: measured anchor/cache loss/Continue/recreation·cold launch/host force-stop PASS, [WORKLOG](WORKLOG.md) |
| M04 | durable-progress-and-race-tests | IN_PROGRESS | epoch/sequence 및 force-stop 복원 PASS; 전체 SAFE/race·60초 저장·미커밋 flush 미완료 |
| M05 | txt-pagination-and-reflow | TODO | 없음 |
| M06 | txt-search-bookmarks-and-encoding | TODO | 없음 |
| M07 | mobile-controls-and-accessibility | TODO | 없음 |
| M08 | epub-product-integration | TODO | 없음 |
| M09 | comic-reader-and-memory | TODO | 없음 |
| M10 | hardening-and-release-evidence | TODO | 없음 |
| M11 | release-candidate-audit | TODO | 없음 |

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

**상태:** IN_PROGRESS

**선행 조건:** M00

**2026-10-04 현재 범위:** 사용자 요청으로 TXT 가져오기와 실제 독서를 우선한다. strict streaming decoder,
UTF BOM/UTF-16/CP949/EUC-KR 미리보기·수동 선택과 자체 작성 fixture를 구현했다.
기존 host 13 cases는 PASS였지만 실제 Android Charset 3 cases 중 CP949/EUC-KR 구분이 실패했다.
Android ICU alias의 확장 허용을 앱의 strict EUC-KR byte grammar로 제한했고 경계 회귀 포함 host 14 cases는 PASS다.
원래 Android assertion을 유지한 수정 뒤 실제 API36 Android 인코딩 3 cases는 모두 PASS다.
Readium/EPUB probe·ADR-0005는 보류하며 이 작업 전체를 DONE으로 올리지 않는다.

## 구현 범위
epubAndroid 어댑터와 격리된 debug/test probe를 만든다. Readium 안정 Android Navigator로 EPUB2/3/이미지/목차/Locator 저장·재사용/테마·크기 변경을 확인한다. publication script와 외부 리소스 차단 가능성을 실제로 검증한다. Android strict decoder의 UTF BOM/CP949 확장 문자/EUC-KR/BOM 없는 UTF-16 후보와 수동 선택을 검증한다.

## 제외 범위
완성 EPUB UI, 자체 전체 EPUB 렌더러/CFI 엔진, alpha Compose Navigator 채택, DRM.

## 완료 조건
- [ ] Readium 고정 버전/라이선스/추가 모듈 필요성/Android host 형태가 ADR에 기록되어 있다.
- [ ] 공개 Locator를 페이지 번호로 축약하지 않고 round-trip한다.
- [ ] 정상 이미지/CSS/검색·위치 기능과 비신뢰 script/외부 URL 차단의 양립을 확인한다.
- [ ] SDK가 제공하지 않는 보안 API를 추정하거나 성공으로 기재하지 않는다.
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

## 구현 범위
앱 수준 progress single writer, persisted sessionEpoch/sequence, 1초 sampling+250ms settled save, conditional transaction, flush barrier, 저장 실패 UI를 완성한다. 외부 host-driven 종료 테스트를 만든다.

## 제외 범위
offset 최대값 기반 최신 판정, lifecycle 단독 저장, 복구 실패 시 0% 덮어쓰기.

## 완료 조건
- [ ] SAFE-01/02/03/04/06/07/09/10/12 통과.
- [ ] A/B 책과 같은 책의 빠른 닫기/재열기, 역방향 이동에서 의도한 최신 기록 유지.
- [ ] 60초 연속 스크롤에도 주기 commit이 존재.
- [x] force-stop 뒤 마지막 commit 완료 locator 보존; 미커밋 상태까지 보존된다고 주장하지 않음.
- [ ] 손상 locator/조회 오류는 기록 보존과 재시도 UI로 처리.

## 산출물
ADR-0004, scripts/process-death-test.*, commit-latency report, race test results

## 중단/제약 처리
실제 process kill 없이 ActivityScenario.recreate만 통과한 것은 이 작업 전체 PASS가 아니다.


# M05 — `txt-pagination-and-reflow`

**상태:** TODO  
**선행 조건:** M04

## 구현 범위
같은 실제 text measurement를 사용하는 bounded 페이지 렌더링, 지역 page boundary cache, 타이포 설정/시스템 배율/창 크기 reflow, 페이지·스크롤 상호 전환을 구현한다.

## 제외 범위
평균 글자 폭 pagination, 전체 페이지 View 생성, 페이지 번호 영구 저장.

## 완료 조건
- [ ] 각 page range가 연속 [start,end)이며 긴 문단/빈 줄/이모지의 내용 coverage 유지.
- [ ] 131072/1MiB 단일 문단 끝까지 접근 가능.
- [ ] font/size/line/letter/margin/insets/density/systemFontScale 변경마다 적절한 cache invalidation.
- [ ] 100회 앞뒤 이동과 random jump 후에도 내용 중복/누락/드리프트 없음.
- [ ] 설정 변경 후 기존 anchor가 같은 문장 또는 동일 페이지 범위 안에 존재.

## 산출물
layout-key spec, coverage/property tests, reflow UI screenshots

## 중단/제약 처리
정확한 총 페이지를 아직 모르면 숫자를 만들지 않고 %/계산 중을 표시한다.


# M06 — `txt-search-bookmarks-and-encoding`

**상태:** TODO  
**선행 조건:** M05

## 구현 범위
literal 본문 검색, chunk overlap/결과 token/offset mapping, 북마크, 미완성 index 상태, 수동 인코딩/미리보기/해석 revision 보존, 검색 후 일회성 돌아가기를 완성한다.

## 제외 범위
정규식/형태소/서재 전체 본문 검색, 자동 문장 fuzzy relocation, 사용자 글꼴.

## 완료 조건
- [ ] TXT-05/06/09/10/12와 검색/북마크 integration 통과.
- [ ] fragment 경계 query도 같은 canonical offset으로 이동.
- [ ] 전체 페이지 수 없음/인덱싱 중에도 유효 locator 북마크 저장 가능.
- [ ] 인코딩 변경 시 이전 해석의 기록을 삭제하거나 새 offset으로 오적용하지 않음.
- [ ] 오래된 검색 job 결과가 새 query/다른 책에 나타나지 않음.

## 산출물
encoding UX screenshots, search/bookmark tests, 50MiB TXT report

## 중단/제약 처리
인코딩 모호성을 정확히 해결했다는 근거가 없으면 자동 감지 성공이라고 주장하지 않는다.


# M07 — `mobile-controls-and-accessibility`

**상태:** TODO  
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

**상태:** TODO  
**선행 조건:** M07 및 M01 EPUB gate 해소

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

**상태:** TODO  
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

**상태:** TODO  
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

**상태:** TODO  
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
