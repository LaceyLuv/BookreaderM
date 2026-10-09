# BookreaderM
북리더 모바일

Android 우선 로컬 전자책 리더다. 현재 **TXT 가져오기·스크롤 독서 내부 알파 0.2.0**을 사용할 수 있다. 기준 문서는
[개정 계획서](docs/BookReader_Mobile_v1_1_Implementation_Plan.md)와 [작업표](docs/TASKS.md)다.
첨부 원문과 `docs/reference/`는 보존된 참고 이력이며 개정된 계약이 우선한다.
`MANIFEST.json`, `README_START_HERE.md`, `START_PROMPT.txt`는 첨부 당시의 원본 패키지 기록이다.
그 안의 코드 미수정·실행 증거 없음 표시는 현재 working tree의 구현 상태를 설명하지 않는다.

Android UI는 Jetpack Compose, 공유 모델·locator·DB·repository는 KMP `shared`에 둔다.
공유 영역에는 Android UI API나 Readium 클래스를 넣지 않는다. DB는 Room KMP와 bundled SQLite다.
일반적인 새 프로세스의 첫 화면은 서재다.

M00 기준 source는 **DONE**이다(2026-10-04). 고정 툴체인의 실제 assemble/lint/Android unit/shared tests와 Room schema 검사가
`PASS`다. Android unit 10 cases와 shared 19 cases는 failure/error/skipped 없이 통과했다.
Lint는 error/warning 없이 고정 버전의 update 안내 20건을 informational로 기록한다.
수정 후 최신 source의 API36 에뮬레이터에서 필수 UI/설정/launcher 5 cases도 실제 통과했다.
10월 1일 환경 차단과 수정 전 실패 로그는 이력으로 보존한다.
현재 증거와 미실행 범위는 [검증 기록](docs/WORKLOG.md), [TESTING](docs/TESTING.md)을 따른다.

구현한 화면 경로는 새 앱 프로세스 → Room 서재 조회 → `Loading / Empty / Content / Error`,
검색 탭의 제목·작가·원본 파일명 부분 문자열 필터, 설정 탭의 시스템/밝게/어둡게 테마다.
오류 화면의 재시도는 실제 DB 연결·조회로 돌아간다. 새 DB에는 예제 책을 자동 삽입하지 않는다.
검색은 성공적으로 조회한 메타데이터 snapshot을 문자 그대로 필터하며 `%`/`_`를 SQL wildcard로 해석하지 않는다.
앱 테마는 SharedPreferences에 저장하고 읽기 실패 시 표시 기본값만 사용한다.
사용자가 테마를 명시적으로 선택하기 전에는 손상된 저장 값을 기본값으로 덮어쓰지 않는다.
검색/설정에서 시스템 Back을 누르면 서재로 돌아간다.

이번 변경은 서재의 `TXT 추가` → Android 파일 선택 → 내부 관리 복사본 → `읽기` → TXT 스크롤 독서와
Reader 밖 하단 이어읽기 바를 연결한다. 가져오기 성공 후 `읽기`를 누르거나 서재의 책에서 `읽기`를 선택한다.
공유(SEND)로 들어온 문서는 가져오기 확인을 거친다. 외부 열기(VIEW)는 가져오기 성공 후 바로 읽기를 연다.
원본 URI의 권한이 사라져도 성공한 관리 복사본을 읽는다.
UTF BOM과 strict UTF-8을 기본으로 검사하며, 모호하면 UTF-16 LE/BE·CP949·EUC-KR 미리보기와 수동 선택을 제공한다.
전체 TXT를 String으로 올리지 않으며 원본은 최대 256MiB다. 서재 검색은 계속 메타데이터 검색이다.

직접 테스트할 책은 [자체 작성 한글 UTF-8 예제](fixtures/encoding/bookreader-demo-ko-utf8.txt)를 사용한다.
여러 화면의 한글·이모지와 CRLF 줄바꿈을 포함하며 CC0-1.0으로 배포한다. 앱에 자동 삽입하지 않는다.

TXT 내부 알파 source는 `2be610af8b7f4030ba4e0fa5b0833bbbf0d84aaf`다. 앱 소유 Room pool을 서재·가져오기·Reader가
공유하고 단계별 sanitized 진단을 제공한다. 로컬 full CI6와 [원격 build](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616525)는
Android unit 41/shared JVM 45 cases, failure/error/skipped 0, lint 20 Hint, APK/schema 검사 PASS다.
[실제 API36 Android 실행](https://github.com/LaceyLuv/BookreaderM/actions/runs/37879616383)은 ordinary 25 cases와
별도 host force-stop/서재/이어읽기 2 stages 모두 PASS이며 자체 fixture PNG 4개도 검증했다. M02/M03은 TXT 범위 `DONE`이다.
최신 외부 VIEW/Room owner 회귀는 PASS지만 이전 SQLite 오류의 내부 원인을 입증한 것은 아니다. 실패 이력은 TESTING/WORKLOG에 보존한다.
M01은 Android 인코딩 부분만 검증했고 Readium/EPUB는 미완료다. M04 전체 SAFE/race·60초 주기 저장·미커밋 flush와
실기기 성능/OEM/TalkBack/16KB 검증은 남아 있다. Activity 재생성과 host force-stop 결과는 구분한다.
EPUB 제품 기능, Comic Reader, TXT 페이지 모드·본문 검색·북마크는 이후 작업이다.
iOS·AI·동기화·계정·통계는 이번 작업 범위 밖이다.

환경과 의존성은 [DEPENDENCIES](docs/DEPENDENCIES.md), 검증 분리는
[TESTING](docs/TESTING.md), 아키텍처 선택은 [ADR-0001](docs/adr/0001-android-first-and-kmp-boundary.md)를 따른다.

정확히 고정한 버전은 Gradle 8.13, AGP 8.11.1, Kotlin/Compose compiler 2.2.20,
KSP 2.2.20-2.0.3, Room 2.8.3, SQLite 2.6.1, Compose BOM 2025.10.00,
JDK 17, minSdk 26/compile·targetSdk 36, build-tools 35.0.0이다.
M00 기준 환경에서 plugin resolve/task discovery/build/test와 API36 기기 테스트가 통과했다. 정확한 증거는
DEPENDENCIES·TESTING·ADR-0006에 별도로 기록한다.

## TXT 예제 직접 읽기

현재 [0.2.0 debug APK](/workspace/bookreader-mobile-0.2.0-debug.apk)는 `2be610a`의 Room 공유/진단 변경을 포함한 로컬 빌드다.
SHA-256은 `021ec47de8311435f6becd0eb5b6e677ed22312b6ef3740b86d86b60d10a6e5e`이다. 같은 source의 실제 Android 25/host 2 stages는 PASS다.
기기 job은 설치 APK hash를 보존하지 않아 이 로컬 APK와 기기 설치본의 byte 동일성은 확인하지 않았다. GitHub release는 만들지 않았다.

Android 8.0(API 26) 이상에서 debug APK를 설치한다. APK가 만들어진 작업 환경에서는 아래 명령을 사용한다.

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb push fixtures/encoding/bookreader-demo-ko-utf8.txt /sdcard/Download/bookreader-demo-ko-utf8.txt
```

1. 앱의 서재에서 `TXT 추가`를 누르고 Downloads의 `bookreader-demo-ko-utf8.txt`를 선택한다.
2. 가져오기 완료 뒤 `읽기`를 누른다. 한글·이모지가 보이는지 확인하고 여러 화면을 스크롤한다.
3. `서재`로 돌아온 뒤 하단 `이어읽기`를 누른다. 저장한 문장 부근으로 돌아오는지 확인한다.
4. 앱을 완전히 닫고 다시 열면 서재가 먼저 열린다. `이어읽기`를 눌러 마지막 commit 완료 위치를 연다.

파일을 일반 다운로드한 기기에서는 파일 관리자에서 APK 설치 후 같은 TXT를 Downloads에 저장해 선택할 수 있다.
텍스트 인코딩 선택이 나오면 예제에는 `utf-8`을 선택한다. 본문 검색·페이지 모드·북마크·책별 인코딩 변경은 아직 없다.

## Debug APK 서명과 기존 데이터

현재 APK는 개발용 debug 서명이며 배포용 release 서명이 아니다. 기존 앱을 유지한 업데이트는 applicationId와
서명 인증서가 모두 같아야 한다. `adb install -r`도 서명 차이를 우회하지 않는다.
이 환경의 기존 local debug 인증서와 과거 CI APK 인증서는 서로 다르다. 정확한 SHA-256은
`docs/evidence/m02-m03-validation/baseline-debug-signers.json`에 기록했다.
새 로컬 APK는 이전 로컬 M00의 debug 인증서를 유지한다. 과거 CI APK 인증서와는 다르므로 그 설치본에 대한
데이터 유지 업데이트를 보장하지 않는다. `INSTALL_FAILED_UPDATE_INCOMPATIBLE`이면 기존 앱을 삭제하거나
데이터를 지우지 말고 같은 서명 키로 만든 APK를 사용해야 한다. 이번 작업은 기존 서명 키를 변경·내보내지 않는다.
새 APK의 버전·크기·hash·서명 검증은 `docs/evidence/m02-m03-validation/final-local-apk.json`에 기록했다.

JDK/SDK 및 dependency 네트워크를 준비한 뒤 모바일 루트에서 실행한다.

```bash
./scripts/doctor.sh
./scripts/ci-check.sh
```

`ci-check`는 실제 task 목록에서 필수 Android/shared task를 찾은 뒤 assemble/lint/unit/shared tests와
Room schema export를 검사한다. 실패나 환경 차단이면 비정상 exit로 종료한다.
기기 UI 및 외부 프로세스 종료 검증은 별도다. 현재 단계의 결과와 미실행 범위는 WORKLOG에 있다.

앱 내부 책·독서 기록은 앱 삭제 또는 데이터 지우기로 손실될 수 있다.
원본 파일은 변경하지 않는 관리 복사본 정책을 유지하며, v1에는 앱 백업/복원 기능이 없다.
