# M00 dependency and toolchain record

2026-10-04 갱신. 정확 버전은 catalog/build scripts/wrapper에 고정했다.
공식 release/호환표/API 확인과 실제 resolved graph·compile·runtime 결과를 구분한다.
현재 JDK 17과 SDK 36/build-tools 35.0.0 환경에서 `doctor`, `projects`, 두 모듈 task discovery가 `PASS`다.
실제 runtime graph와 전체 assemble/lint/unit/shared/schema 검사는 `PASS`다.
Android unit 10/shared 19 cases와 최신 source의 API36 instrumentation 5 cases가 통과했다.
첫 shared DB failure와 lint failure는 이력으로 보존했다.
10월 1일 JRE만 있고 SDK/adb가 없던 차단 이력은 WORKLOG와 기존 로그에 보존한다.

| 항목 | 고정값 | 근거 / 현재 확인 범위 |
|---|---|---|
| Build JDK | Temurin 17.0.16+8, JVM bytecode/toolchain 17 | CI 정확 patch 고정; 로컬 JDK 설치 및 doctor PASS |
| Gradle | 8.13 | 공식 release metadata와 wrapper checksum 직접 확인 |
| AGP | 8.11.1 | Kotlin 공식 지원 범위 안; 실제 plugin resolve/projects/task discovery PASS |
| Kotlin / Compose compiler / serialization plugin | 2.2.20 | 공식 안정 release 직접 확인; Compose compiler를 Kotlin과 정렬 |
| KSP | 2.2.20-2.0.3 (KSP2) | 공식 안정 release 직접 확인 |
| Room runtime/compiler/plugin | 2.8.3 | 공식 stable release; 실제 KSP compile/schema export/DB 회귀 PASS |
| SQLite bundled | 2.6.1 | 실제 bundled driver 사용; 실제 graph 2.6.1; native APK ABI/16KB 확인 M10 |
| Compose BOM | 2025.10.00 | 공식 BOM POM과 실제 UI graph 확인; UI 1.9.3 선택 |
| Activity Compose | 1.11.0 | catalog 정확 버전; 실제 graph/build PASS |
| Lifecycle | 2.9.4 | ViewModel/runtime-compose; 실제 graph/build PASS |
| Core KTX | 1.17.0 | WindowCompat; 실제 graph/build PASS |
| Coroutines | 1.10.2 | core/Android/test 정확 정렬; 실제 graph/build PASS |
| Serialization JSON | 1.9.0 | locator envelope round-trip; 실제 graph/build PASS |
| JUnit | 4.13.2 | Android unit/JVM test engine |
| Android test core / runner / ext-junit | 1.7.0 / 1.7.0 / 1.3.0 | test APK compile/실제 API36 5-case 실행 PASS |
| min / compile / target SDK | 26 / 36 / 36 | min26; Room/SQLite 최소23 이상, 실제 compile 성공 |
| Build tools | 35.0.0 | Android modules/CI 동일 고정; 설치 및 doctor PASS |
| Readium | 미추가; 문서 후보 3.4.0 | M01에서 stable Android Navigator 소비 요구/보안/locator 검증 후 확정 |

Room 3.0.1은 계획서의 초기 후보다. M00은 `androidx.room` 2.8.3 KMP API로 구현했고,
계열 선택과 검증되지 않은 부분은 [ADR-0006](adr/0006-dependency-toolchain.md)에 남겼다.
안정 계열 후보라는 이유로 전체 조합을 검증 완료로 취급하지 않는다.
iOS target/Readium/DI 프레임워크/서버 의존성을 추가하지 않았다.

## 직접 읽은 공식 근거

- [Gradle 8.13 공식 배포 release](https://github.com/gradle/gradle-distributions/releases/tag/v8.13.0):
  stable release와 distribution/wrapper SHA256. 로컬 증거: [official-toolchain.json](evidence/official-toolchain.json).
- [Kotlin 2.2.20 release](https://github.com/JetBrains/kotlin/releases/tag/v2.2.20) 및
  [KSP 2.2.20-2.0.3 release](https://github.com/google/ksp/releases/tag/2.2.20-2.0.3):
  `prerelease=false` metadata를 직접 읽었다.
- [Kotlin 공식 Gradle 호환표 고정 commit](https://github.com/JetBrains/kotlin-web-site/blob/3b022f0af1cbab7e9f4b63455a7fae73e6a2bcf8/docs/topics/gradle/gradle-configure-project.md):
  Kotlin 2.2.20–2.2.21에서 Gradle 7.6.3–8.14, AGP 7.3.1–8.11.1을 지원한다고 명시한다.
  [읽은 excerpt](evidence/kotlin-compatibility.md). 이 범위는 실제 앱 build 성공의 대체 증거가 아니다.
- 공식 Gradle source tag `v8.13.0`의 launchers/JAR를 GitHub connector로 가져왔다.
  source 내부 wrapper properties는 RC를 가리키므로 복사하지 않고 stable 8.13 properties를 작성했다.

2026-10-03에 아래 공식 자료를 HTTPS/TLS 검증과 HTTP 200으로 직접 확인했다.
URL·조회 시각·SHA-256은 [official manifest](evidence/m00-validation/official/manifest.json)에,
읽은 부분은 같은 디렉터리의 `*-excerpt.txt`에 있다.

- [AGP 8.11 공식 호환표](https://developer.android.com/build/releases/past-releases/agp-8-11-0-release-notes):
  API 36, Gradle 8.13, build-tools 35.0.0, JDK 17 조합을 명시하고 8.11.1 patch를 확인했다.
- [Room 공식 release notes](https://developer.android.com/jetpack/androidx/releases/room):
  stable 2.8.3, 2.8 계열 minSdk 23과 Room Gradle plugin 최소 AGP 8.4를 확인했다.
- [SQLite 공식 release notes](https://developer.android.com/jetpack/androidx/releases/sqlite):
  stable 2.6.1과 2.6 계열 minSdk 23을 확인했다. 앱 minSdk 26은 이 요구 이상이다.
- [Room KMP 공식 가이드](https://developer.android.com/kotlin/multiplatform/room):
  `ConstructedBy`, generated `RoomDatabaseConstructor`, 플랫폼별 builder와 bundled driver 경계를 확인했다.
  최신 가이드에는 Room3 예제가 있으므로 Room2 pin의 API는 실제 artifact/KSP compile 결과와 대조한다.
  별도로 공식 `room-runtime-jvm`/`room-runtime-android:2.8.3` sources JAR에서
  `RoomDatabase.close()`의 자체 query scope 취소를 확인했다.
  [고정 API 출처/hash](evidence/m00-validation/official/room-2.8.3-api-sources.json).
- [Compose BOM 2025.10.00 공식 POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.10.00/compose-bom-2025.10.00.pom):
  UI/test 등 constraint 목록을 직접 확인했다. BOM 자체와 transitive graph의 실제 선택은 구분한다.
  공식 `ui-test-android:1.9.3` sources JAR의 `SemanticsNodeInteraction.assertDoesNotExist()`
  member를 확인했다. [API excerpt](evidence/m00-validation/official/compose-ui-test-1.9.3-api.txt).

실제 plugin resolve는 `evidence/m00-validation/gradle-resolution-diagnostic.log`에서 확인했다.
AGP 8.11.1/Kotlin 2.2.20/KSP 2.2.20-2.0.3/Room plugin 2.8.3은 pin 변경 없이 resolve됐다.
아래 실제 dependency 보고서 명령은 exit 0이었다.

```bash
./gradlew --no-daemon --console=plain \
  :androidApp:dependencies --configuration debugRuntimeClasspath \
  :shared:dependencies --configuration jvmRuntimeClasspath
```

[evidence/m00-validation/resolved-runtime-graphs.log](evidence/m00-validation/resolved-runtime-graphs.log)는
Room 2.8.3, SQLite 2.6.1, Coroutines 1.10.2, Serialization JSON 1.9.0과
Compose UI 1.9.3의 실제 선택을 기록한다. transitive dependency의 `->` 표시는 graph 결과 그대로 읽는다.
공식 문서 확인만으로 전체 compile·test·device 호환성을 PASS로 올리지 않는다.

## Wrapper authenticity

```text
Gradle distribution: https://services.gradle.org/distributions/gradle-8.13-bin.zip
distributionSha256Sum: 20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
wrapper SHA256: 81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f
wrapper Git blob: 9bbc975c742b298b441bfb90dbc124400a3751b9
wrapper size: 43705 bytes
```

JAR checksum은 공식 release와 일치해 PASS다. `gradle wrapper` task를 로컬 실행해
생성했다는 주장은 하지 않는다. 정식 Gradle 생성 launchers와 JAR를 공식 tag에서 보존했다.

## 설치와 실제 검증

JDK 17.0.16+8을 설치하고 JAVA_HOME/PATH를 지정한다. Android SDK 및 licenses를 준비한다.

```bash
sdkmanager 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
./scripts/doctor.sh
./gradlew --version
./gradlew projects
./gradlew :androidApp:tasks --all
./gradlew :shared:tasks --all
./scripts/ci-check.sh
```

ci-check는 선택한 plugin의 task 목록에서 `assembleDebug`, `lintDebug`,
`testDebugUnitTest`, `assembleDebugAndroidTest`, shared `jvmTest`를 실제 찾아야 다음 단계로 간다.
2026-10-03 실제 task discovery에서 해당 task를 확인했다. 증거는 `evidence/m00-validation/ci/*-tasks.log`다.
확인 뒤 Android assemble/lint/unit/instrumentation APK compile와
공통 locator/JVM 실제 Room DB integration tests를 하나의 실패 전파 build로 실행한다.
Room KSP schema JSON 생성도 필요하다. 실패 무시/skip/destructive migration을 사용하지 않는다.

Google Maven/Maven Central/Gradle Plugin Portal 및 Gradle distribution 접속이 필요하다.
10월 1일의 wrapper socket 차단과 doctor/ci-check exit 78은 당시 환경 결과다.
현재 설치·Gradle/task discovery 증거는 `evidence/m00-validation/`에 분리한다.
KSP 공식 Maven POM과 resolve HTTP 진단은 같은 디렉터리의 `ksp-official-*.pom`,
`ksp-resolution-http.json`에 있다. 초기 artifact 조회 실패를 버전 부재로 판정하지 않는다.
전체 실행 명령과 APK·XML·schema 상태는 [TESTING](TESTING.md), [WORKLOG](WORKLOG.md)에 기록한다.

dependency locking/verification metadata는 실제 resolution 뒤 생성·검토해야 한다.
없는 resolved graph/checksum 목록을 손으로 작성해 검증 완료로 보이지 않게 한다.

최종 로컬 결과는 [final-validation-summary.json](evidence/m00-validation/final-validation-summary.json)에 있다.
Source `3f04b8ba53d23846a454ee744c5e1fc33bea45e3`, 전체 ci-check exit 0,
Android unit 10/shared 19 cases 및 APK/schema bytes·SHA-256을 기록한다.
Lint 20건은 활성화된 informational upgrade 안내이며 나머지 warning/error는 fatal이다.
의존성 lock/checksum verification metadata를 생성했다는 주장은 하지 않는다.

API36 Google APIs x86_64 emulator의 최신 source 5-case 실행도 `PASS`다.
기기 API/ABI/fingerprint와 실제 XML은 `evidence/m00-validation/device-37164061580/`에 보존했다.
