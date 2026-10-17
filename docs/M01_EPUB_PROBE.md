# M01 안정 EPUB probe — 2026-10-17

제품 UI 연결 전 Readium 3.1.2의 공개 Fragment Navigator를 검증했다. 현재 도구와의 호환성 및
보안 경계는 [ADR-0005](adr/0005-epub-stable-navigator-and-content-policy.md)에 기록한다. EPUB 제품 지원 완료가 아니다.

main `6b59093` 및 `46da7fa`의 API36 원격 실행에서 EPUB native 3개는 모두 PASS다.
자체 EPUB2/3의 한글·이미지 naturalWidth·중첩 목차·chapter 이동·전체 SDK Locator JSON roundtrip,
글자/테마 변경·Activity 재생성, script/external image의 preflight 거부와 실제 WebView CSP 위반을 확인했다.
전체 device suite는 각각 2/1 FAIL이므로 전체 PASS 또는 host process-death PASS로 올리지 않는다.
`6b59093` 메뉴 seek 및 회전 timeout, `46da7fa` 이전 fling 중 baseline 비교 실패의 원본 XML/log를 보존했다.
`b7a1898` 창 설정 소유권 보완과 `3b48430` 테스트 baseline 보완 후 새 전체 회귀를 실행 중이다.

`46da7fa` 원격 build는 Android unit 60/shared JVM 67/EPUB unit 6/실제 ENOSPC 3 PASS다.
50MiB TXT cache·전체 검색·임의 anchor는 host correctness 증거다. 실기기 성능 수치로 취급하지 않는다.
`f67bb75`는 OPF MIME/확장자 연결을 추가해 HTML의 PNG 위장을 차단하며 EPUB unit은 8개다.
이 변경의 로컬 full CI는 archive journal 보고서에 기록했고 새 native 검증은 별도 필요하다.

[증거 디렉터리](evidence/m01-epub-validation)의 각 summary는 원본 source SHA, workflow URL,
artifact ID/ZIP SHA256과 XML counts를 포함한다. GitHub artifact digest 및 원본 schema와 대조했다.
호환 corpus 확대, 긴 chapter·깨진 resource, cross-book/내부 링크, exact/fallback 및 실제 process-death,
제품 host 보안과 수명 관리는 M01/M08의 남은 gate다. TalkBack/실기기/출시 gate는 미실행이다.
