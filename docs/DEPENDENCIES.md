# M00 dependency and toolchain record

2026-10-01. 정확 버전은 catalog/build scripts/wrapper에 고정했다.
**조합의 dependency resolve, compilation, Android APK 실행은 BLOCKED_ENV이며 호환성 완료 판정이 아니다.**
현재 실행 환경은 OpenJDK JRE 21.0.12.1, javac/Android SDK/adb 없음이다.

| 항목 | 고정값 | 근거 / 현재 확인 범위 |
|---|---|---|
| Build JDK | Temurin 17.0.16+8, JVM bytecode/toolchain 17 | CI 정확 patch 고정; 로컬 JDK 없음 |
| Gradle | 8.13 | 공식 release metadata와 wrapper checksum 직접 확인 |
| AGP | 8.11.1 | Kotlin 공식 지원 범위 안; 실제 Google Maven resolve 미실행 |
| Kotlin / Compose compiler / serialization plugin | 2.2.20 | 공식 안정 release 직접 확인; Compose compiler를 Kotlin과 정렬 |
| KSP | 2.2.20-2.0.3 (KSP2) | 공식 안정 release 직접 확인 |
| Room runtime/compiler/plugin | 2.8.3 | Room KMP 안정 계열 후보; 고정 artifact/API resolve 확인 필요 |
| SQLite bundled | 2.6.1 | 실제 bundled driver 사용; native APK ABI/16KB 확인 M10 |
| Compose BOM | 2025.10.00 | UI/foundation/material3/tooling/test가 BOM 제약을 공유; resolve 미실행 |
| Activity Compose | 1.11.0 | catalog 정확 버전; resolve 미실행 |
| Lifecycle | 2.9.4 | ViewModel/runtime-compose; resolve 미실행 |
| Core KTX | 1.17.0 | WindowCompat; resolve 미실행 |
| Coroutines | 1.10.2 | core/Android/test 정확 정렬; resolve 미실행 |
| Serialization JSON | 1.9.0 | locator envelope round-trip; resolve 미실행 |
| JUnit | 4.13.2 | Android unit/JVM test engine |
| Android test core / runner / ext-junit | 1.7.0 / 1.7.0 / 1.3.0 | instrumentation 경로; 실행 미실행 |
| min / compile / target SDK | 26 / 36 / 36 | min26 제품 기본값; 의존성 소비 요구 검증 필요 |
| Build tools | 35.0.0 | Android modules/CI 동일 고정; 설치 미실행 |
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

다음 공식 링크는 재검증 진입점이며 이번 환경에서 해당 HTTP 문서의 본문을 직접 읽었다는 주장이 아니다:
[AGP 8.11](https://developer.android.com/build/releases/past-releases/agp-8-11-0-release-notes),
[Room](https://developer.android.com/jetpack/androidx/releases/room),
[Room KMP](https://developer.android.com/kotlin/multiplatform/room),
[SQLite](https://developer.android.com/jetpack/androidx/releases/sqlite),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom/bom-mapping),
[Compose compiler](https://developer.android.com/develop/ui/compose/compiler).

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
이 task들은 현재 작성된 계약이며 로컬 task discovery 성공 주장이 아니다.
확인 뒤 Android assemble/lint/unit/instrumentation APK compile와
공통 locator/JVM 실제 Room DB integration tests를 하나의 실패 전파 build로 실행한다.
Room KSP schema JSON 생성도 필요하다. 실패 무시/skip/destructive migration을 사용하지 않는다.

Google Maven/Maven Central/Gradle Plugin Portal 및 Gradle distribution 접속이 필요하다.
이번 sandbox에서 wrapper는 `SocketException: Operation not permitted`로 exit 1을 반환했다.
JVM에 세션용 HTTP/HTTPS proxy 옵션을 주는 별도 probe도 같은 sandbox socket 차단으로 exit 1이었다.
이 결과는 upstream artifact가 없다는 증거가 아니다.
프록시/TLS 검증/권한을 우회하지 않았으며 추가 network approval을 재요청하지 않았다.

실제 exits/logs는 [build-command-results.json](evidence/build-command-results.json)과
[proxy probe](evidence/gradle-version-proxy.log)에 있다. Doctor/ci-check exit 78 = BLOCKED_ENV.
APK, 테스트 HTML/XML 및 Room schema JSON은 아직 생성되지 않았다.

dependency locking/verification metadata는 실제 resolution 뒤 생성·검토해야 한다.
없는 resolved graph/checksum 목록을 손으로 작성해 검증 완료로 보이지 않게 한다.
