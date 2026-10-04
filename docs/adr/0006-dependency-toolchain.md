# ADR-0006 — Android/KMP toolchain and Room candidate

상태: **2026-10-04 M00 고정 조합 build/test 및 API36 기기 실행 PASS**

날짜: 2026-10-01 / 갱신: 2026-10-04 / 작업: M00

## 선택

Gradle 8.13, AGP 8.11.1, Kotlin 2.2.20, 동일 Kotlin Compose compiler,
KSP 2.2.20-2.0.3, Room 2.8.3, SQLite bundled 2.6.1,
Compose BOM 2025.10.00, JDK/JVM 17, SDK min26/compile36/target36,
build tools35.0.0을 정확히 고정한다. 전체 matrix는 [DEPENDENCIES](../DEPENDENCIES.md).

Android UI는 Jetpack Compose다. shared는 Android KMP library와 JVM host test target이며
실제 Room/SQLite의 insert/query/FK/reopen smoke를 로컬/CI에서 실행하기 위한 JVM target이다.
JVM target을 desktop 제품 앱으로 취급하지 않는다. iOS target은 없다.

Room 3.0.1은 원문 개정 계획의 초기 검증 후보이며 의무 버전이 아니다.
이번 코드는 androidx.room 2.x KMP의 ConstructedBy/RoomDatabaseConstructor 및 bundled driver
경계로 작성한다. 이 변경은 모델/DB 보존 정책을 낮추지 않으며 destructive migration을 추가하지 않는다.
Room/SQLite version별 official API와 실제 KSP generated source는 resolve가 가능해지면 반드시 검증한다.

## 이유와 대안

Kotlin 공식 호환표가 선택한 Gradle/AGP를 지원 범위에 포함한다.
Gradle/Kotlin/KSP는 공식 stable release metadata를 읽었다.
Compose compiler를 Kotlin 버전과 맞추고 Compose UI를 BOM으로 정렬한다.
AGP9/새 Android KMP plugin으로 동시에 이동하거나 Room3 API를 섞는 대안은
M00 bootstrap의 변경 폭을 늘리므로 이번에는 채택하지 않았다.
어느 대안도 runtime 검증 없이 호환성 성공이라고 취급하지 않는다.

Readium은 M01에서 Android Navigator adapter에 고정한다. M00에 미사용 엔진 의존성을 넣지 않는다.
개정 계획의 Readium3.4.0 소비자 SDK/컴파일러 요구를 이번 SDK36 선택으로 이미 만족했다고 주장하지 않는다.

## 증거와 실패 조건

- PASS: authentic Gradle wrapper/JAR release checksum 일치, Kotlin 공식 compatibility range 확인,
  shell syntax/TOML parse, ci helper가 command failure와 evidence log write failure를 실제 전파함.
- PASS(2026-10-03): JDK/SDK 환경 및 실제 Gradle version/projects/Android/shared task discovery.
- PASS(2026-10-04, source `3f04b8ba`): 실제 graph·Kotlin/KSP compile·Room schema export·assemble/lint/unit/shared DB tests.
  Android unit 10/shared 19 cases, failure/error/skipped 0; lint의 활성 informational upgrade 안내 20건은 보고서에 유지한다.
- PASS: Android instrumentation APK compile 및 같은 source의 실제 API36 emulator 필수 UI/설정/launcher 5 cases.
- BLOCKED_ENV(2026-10-01 이력): JRE만 존재하고 JDK/SDK/device가 없었으며 wrapper socket이 차단됨.
  당시 Wrapper discovery exit1과 doctor/ci-check exit78 로그는 덮어쓰지 않는다.
- 공식 문서·artifact·실제 실행의 각 확인 범위는 [DEPENDENCIES](../DEPENDENCIES.md)에 구분한다.

M00의 ci-check·실제 DB/locator·schema 및 Android UI 필수 gate가 통과했다.
Room schema를 손으로 쓰거나 fake DB/skip/ignoreFailures로 차단을 우회하지 않았다.
Reader/import/process-death·Readium·실기기 성능 및 최종 ABI/16KB 검증은 후속 범위다.

## 변경 영향

버전 변경 시 catalog/wrapper/CI/JDK/SDK 문서가 같이 바뀌어야 한다.
Android assemble/lint/unit, shared JVM locator/Room smoke, schema export를 다시 실행한다.
Room major/schema 변경 시 명시적인 migration 및 데이터 보존 검증이 선행해야 한다.
최종 native ABI/16KB 검증은 M10의 실제 APK/AAB gate다.
