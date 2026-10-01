# ADR-0006 — Android/KMP toolchain and Room candidate

상태: **구현 선택 기록; 전체 조합 승인 BLOCKED_ENV**  
날짜: 2026-10-01 / 작업: M00

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
- BLOCKED_ENV: JRE만 존재하고 JDK/SDK/device가 없으며 sandbox network socket이 차단된다.
  Wrapper version/projects/module task discovery 명령이 각각 exit1; doctor/ci-check exit78.
- NOT_RUN: resolved dependency graph, Kotlin/KSP compilation, generated DB schema,
  Android APK assemble/lint/unit/shared real DB test와 instrumentation.
- 공식 Room/SQLite/Compose/AGP artifact/version API 대조가 완료되지 않은 부분을
  [DEPENDENCIES](../DEPENDENCIES.md)에서 별도로 명시한다.

M00를 DONE으로 올리려면 JDK/SDK/허용된 저장소 접속 후 ci-check 전체 성공과 실제 DB/locator
report가 필요하다. Room schema를 손으로 쓰거나 fake DB/skip/ignoreFailures로 차단을 우회하지 않는다.

## 변경 영향

버전 변경 시 catalog/wrapper/CI/JDK/SDK 문서가 같이 바뀌어야 한다.
Android assemble/lint/unit, shared JVM locator/Room smoke, schema export를 다시 실행한다.
Room major/schema 변경 시 명시적인 migration 및 데이터 보존 검증이 선행해야 한다.
최종 native ABI/16KB 검증은 M10의 실제 APK/AAB gate다.
