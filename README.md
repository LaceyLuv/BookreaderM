# BookreaderM
북리더 모바일

Android 우선 로컬 전자책 리더의 **M00 기반 구현**이다. 기준 문서는
[개정 계획서](docs/BookReader_Mobile_v1_1_Implementation_Plan.md)와 [작업표](docs/TASKS.md)다.
첨부 원문과 `docs/reference/`는 보존된 참고 이력이며 개정된 계약이 우선한다.
`MANIFEST.json`, `README_START_HERE.md`, `START_PROMPT.txt`는 첨부 당시의 원본 패키지 기록이다.
그 안의 코드 미수정·실행 증거 없음 표시는 현재 working tree의 구현 상태를 설명하지 않는다.

Android UI는 Jetpack Compose, 공유 모델·locator·DB·repository는 KMP `shared`에 둔다.
공유 영역에는 Android UI API나 Readium 클래스를 넣지 않는다. DB는 Room KMP와 bundled SQLite다.
일반적인 새 프로세스의 첫 화면은 서재다.

M00는 **DONE**이다(2026-10-04). 고정 툴체인의 실제 assemble/lint/Android unit/shared tests와 Room schema 검사가
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

M00에는 TXT 본문 렌더러, EPUB 제품 기능, Comic Reader, 파일 가져오기 및 이어읽기 경로가 없다.
내부 관리 복사본 import는 M02, TXT 독서와 하단 이어읽기 바 연결은 M03의 범위다.
EPUB는 M01에서 안정 Readium Android adapter의 호환성·복원·보안을 검증한 뒤 통합한다.
iOS·AI·동기화·계정·통계는 이번 작업 범위 밖이다.

환경과 의존성은 [DEPENDENCIES](docs/DEPENDENCIES.md), 검증 분리는
[TESTING](docs/TESTING.md), 아키텍처 선택은 [ADR-0001](docs/adr/0001-android-first-and-kmp-boundary.md)를 따른다.

정확히 고정한 버전은 Gradle 8.13, AGP 8.11.1, Kotlin/Compose compiler 2.2.20,
KSP 2.2.20-2.0.3, Room 2.8.3, SQLite 2.6.1, Compose BOM 2025.10.00,
JDK 17, minSdk 26/compile·targetSdk 36, build-tools 35.0.0이다.
현재 환경에서 plugin resolve/task discovery/build/test와 API36 기기 테스트가 통과했다. 정확한 증거는
DEPENDENCIES·TESTING·ADR-0006에 별도로 기록한다.

JDK/SDK 및 dependency 네트워크를 준비한 뒤 모바일 루트에서 실행한다.

```bash
./scripts/doctor.sh
./scripts/ci-check.sh
```

`ci-check`는 실제 task 목록에서 필수 Android/shared task를 찾은 뒤 assemble/lint/unit/shared tests와
Room schema export를 검사한다. 실패나 환경 차단이면 비정상 exit로 종료한다.
기기 UI 검증은 별도이며 M00 완료/M01 착수 조건은 WORKLOG에 있다.

앱 내부 책·독서 기록은 앱 삭제 또는 데이터 지우기로 손실될 수 있다.
원본 파일은 변경하지 않는 관리 복사본 정책을 유지하며, v1에는 앱 백업/복원 기능이 없다.
