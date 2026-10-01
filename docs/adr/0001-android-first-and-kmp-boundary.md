# ADR-0001 — Android 우선과 KMP 경계

- 날짜: 2026-10-01
- 상태: 제품·아키텍처 계약 채택, M00 필수 실행 검증 BLOCKED_ENV
- 기준: 개정 계획서 §7/§8/§30/§43, TASKS M00

## 선택

Android `androidApp`의 Jetpack Compose UI와 KMP `shared` 두 모듈을 기본으로 한다.
공유 모델, 포맷별 version/contentRevision locator, repository 계약과 Room KMP DB는 shared에 둔다.
Android 파일/수명주기/UI API와 플랫폼 DB builder는 플랫폼 경계에 둔다.
동작하지 않는 기능을 위한 빈 모듈이나 DI 프레임워크를 추가하지 않고 constructor injection을 사용한다.
JVM target은 공유 코드와 실제 file-backed Room/SQLite 테스트의 실행 경로이며 PC 제품 앱을 의미하지 않는다.
서재 조회 1회마다 코루틴이 DB handle을 열고 `finally`로 닫는다. ViewModel 취소와 오류도 같은 소유권을 따른다.
앱 chrome의 최소 테마만 플랫폼 SharedPreferences에 저장하며 Reader settings 구현 완료로 확대 해석하지 않는다.

일반적인 새 프로세스는 서재다. 파일 가져오기와 Reader가 없는 M00에서 마지막 책 자동 진입이나
가짜 이어읽기 바를 만들지 않는다. M03의 바를 눌러 Reader에 진입하는 제품 계약은 유지한다.
ReadingProgress 조회 실패는 별도 오류이고 0%/빈 locator를 저장하는 경로를 허용하지 않는다.
서재 전체 JSON overwrite와 destructive migration은 금지한다.

M00의 `ProgressRepository`는 `Found / Missing / Error` 읽기 전용 경계다. 조건부 writer와
복원 성공 게이트는 M03/M04에서 구현한다. DAO 삽입은 실제 DB smoke에서 검증하며 이를
제품의 안전한 진행도 저장 기능 완료로 간주하지 않는다.

EPUB 엔진의 기본은 안정 Readium Android Navigator이며 `epubAndroid` 어댑터는 M01에서 실제 필요와
API를 확인한 뒤 추가한다. Readium 클래스를 common domain에 노출하지 않고 SDK locator payload를 보존한다.
M00는 EPUB 호환성·위치 복원·보안 검증 완료를 의미하지 않는다.

## 대안과 이유

Compose Multiplatform UI 공유는 iOS가 이번 범위 밖이고 Readium host의 Android 수명주기를
검증해야 하므로 채택하지 않는다. PC FastAPI/Python/JSON 저장 포팅도 신규 Android 제품 계약에 맞지 않는다.
Android 전용 domain 대신 KMP core를 선택하여 모델·DB·locator 계약을 독립적으로 검증하지만,
iOS 빌드/호환성이나 세 포맷 pagination의 완전한 공통화를 주장하지 않는다.

## 실패 조건과 변경 영향

Android API가 commonMain에 유입되거나 UI가 DAO를 직접 호출하는 변경은 이 경계를 위반한다.
안정 Room/toolchain의 실제 resolve/build가 실패하면 ADR-0006과 M00 실행 증거에 blocker를 남긴다.
진행도 조회 실패를 empty로 처리하거나 보안 우회를 채택하여 빌드 성공을 만들지 않는다.
플랫폼 경계 변경은 shared의 모델/DB/직렬화 테스트와 Android UI/빌드 검증에 영향을 준다.

## 검증 결과

`BLOCKED_ENV`: Gradle 배포 다운로드 차단 및 JDK compiler·Android SDK·adb 부재로
실제 task 목록/assemble/lint/unit/shared/Room smoke를 확인하지 못했다.
Android XML·권한/백업 제외 선언 정적 검사 `PASS`는 런타임·OEM 동작이나 실제 DB 실행을 증명하지 않는다.
명령·exit code·로그와 다음 gate는 [WORKLOG](../WORKLOG.md)에 있다.
