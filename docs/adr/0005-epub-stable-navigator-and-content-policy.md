# ADR-0005 — EPUB 안정 Navigator 및 콘텐츠 경계

상태: 검증 중, 2026-10-17. M01 probe이며 EPUB 제품 지원 또는 출시 승인 기록이 아니다.

## 버전 결정

공식 최신 안정 Readium 3.4.0의 Maven AAR은 minCompileSdk 37이며 POM은 Kotlin 2.4.20,
core-ktx 1.19.0을 요구한다. 현재 검증 도구 compileSdk 36/Kotlin 2.2.20과 다르다.
공식 3.3.0/3.2.0 POM도 Kotlin 2.3.20/core 1.18.0이다. 현재 단계에서는 도구 전체를 함께 바꾸지 않고
안정 Readium **3.1.2**를 정확히 고정하여 API와 보안 가능성을 검증한다. 3.1.2를 최신 버전이라고 부르지 않는다.

`readium-shared`, `readium-streamer`, `readium-navigator`만 사용한다. PDF/DRM/OPDS/오디오 adapter는 추가하지 않는다.
Kotlin 2.1.21/core 1.16.0/Fragment 1.8.7 기반 공식 POM을 현재 앱 도구에서 실제 빌드했다.
별도 `epubAndroid` Android library는 common domain에 SDK 클래스를 노출하지 않는다.
앱 연결은 `debugImplementation`과 exported=false인 fixture 전용 Activity뿐이다.
BSD-3-Clause 원문을 별도 보존하고 AAR assets에도 포함하며 transitive license 감사는 M10 후속이다.

## 공식 API 확인

- [3.1.2 PublicationOpener](https://github.com/readium/kotlin-toolkit/blob/3.1.2/readium/streamer/src/main/java/org/readium/r2/streamer/PublicationOpener.kt)
- [3.1.2 EpubParser](https://github.com/readium/kotlin-toolkit/blob/3.1.2/readium/streamer/src/main/java/org/readium/r2/streamer/parser/epub/EpubParser.kt)
- [3.1.2 EpubNavigatorFactory](https://github.com/readium/kotlin-toolkit/blob/3.1.2/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorFactory.kt)
- [3.1.2 WebViewServer](https://github.com/readium/kotlin-toolkit/blob/3.1.2/readium/navigator/src/main/java/org/readium/r2/navigator/epub/WebViewServer.kt)
- [3.1.2 HtmlInjector](https://github.com/readium/kotlin-toolkit/blob/3.1.2/readium/navigator/src/main/java/org/readium/r2/navigator/epub/HtmlInjector.kt)

기존 Fragment Navigator를 사용한다. alpha Compose Navigator는 사용하지 않는다.
공개 Locator의 `fromJSON`/`toJSON`, `currentLocator`, `go`, `submitPreferences`를 실제 compile한다.
페이지 수로 Locator를 축약하지 않는다. Activity 재생성 검증은 host process-death와 구분한다.

## 보안 경계

`BoundedZip`은 central/local record를 제한된 크기로 읽고 경로·중복 NFC·암호화·특수 파일·압축 방식·범위 중첩을 검증한다.
파일시스템에 archive entry를 추출하지 않는다. 실제 DEFLATE stream의 bytes/CRC/끝 위치를 검사한다.
source 512MiB, entry 20,000, logical total 4GiB, resource 64MiB, directory 64MiB,
path 1024자/32단계, markup 16MiB, XML depth 64/nodes 100,000/attributes 128, resource read 30초 한도다.
ZIP64/split/encrypted/nested archive와 special file은 명시적으로 지원하지 않는다.

Publication은 해당 한 개 ZIP의 virtual Container만 받는다. container rootfile은 OPF로 제한하고,
manifest MIME과 확장자의 대응을 강제한다. 검증한 manifest resource와 metadata만 제공하여
HTML을 PNG 확장자로 숨기는 검사 우회를 거부한다. 전체 사전 검증은 60초/cancellation 예산을 갖는다. absolute URL/query/없는 key는 읽지 못한다.
Readium parser에 HttpClient/외부 파일 retriever를 제공하지 않는다. EPUB script/이벤트 핸들러/active element,
DTD/entity, 위험 URL 및 다른 책 경로는 처리 전에 거부한다. 정규식으로 script를 지우는 sanitizer가 아니다.
정상 XHTML에는 DOM으로 CSP를 head 첫 위치에 넣고, 이후 SDK가 주입한 bundle engine script만 허용한다.
출판 CSS의 외부 import/image/font는 CSP로 차단한다. 제한된 하위 경로 간 상대 참조는 허용한다.

3.1.2 SDK는 외부 WebView 요청에 null을 반환하므로 SDK 자체가 차단한다고 주장하지 않는다.
CSP와 최종 manifest 권한, 실제 WebView attack fixture가 별도 gate다. SDK 전용 엔진 JavaScript를 일괄 비활성화하지 않는다.
최종 product host의 link 확인 UI/종료·취소/정확한 restore/fallback/보안 회귀는 M08에서 다시 검증해야 한다.

## 결과·제약

기본 SDK AAR/Locator compile, bounded ZIP 및 XML/CSP host unit 8개를 실행했다.
실제 Android SAX 호환성, WebView engine/image/CSS/TOC/위치 복원 및 CSP violation event 검증은 준비 중이다.
기기 실행 전 M01 또는 M08 완료로 올리지 않는다. Android host process-death, 더 넓은 정상 EPUB corpus,
고급 SVG·font obfuscation, physical performance, SDK version 업그레이드 검증은 아직 완료되지 않았다.
DTD·암호화/font obfuscation·고급 interactive 콘텐츠는 지원 불가로 거부한다.
