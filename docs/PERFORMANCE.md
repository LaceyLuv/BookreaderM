# 성능 측정 — 2026-10-17

## 2026-10-22 현재: 환경 연결 복구, native 후속 검증

환경 연결과 Git/CI 다운로드를 실제 확인했다. `49d1a04` build 66/74/16 + ENOSPC 3 PASS.
Native는 API26/34 각각 54개 중 2 FAIL, API36 1 FAIL이며 host는 NOT_RUN이다.
probe의 기본 CSS 경로와 picker의 화면 밖 파일 탐색을 보완했다. API34 TXT 측정 재진입은 원인 미확정이다.
[현재 변경·실패 원본](NATIVE_UI_SYNCHRONIZATION.md)을 따른다. main 통합은 전체 native/host 통과 후 진행한다.
아래 절은 이전 검증 시점의 기록이다.


## 2026-10-18 최신: probe 설정 완료 신호와 picker 조회 교정

`9a96ac5` build는 66/74/16 + ENOSPC 3 PASS, 제품 EPUB/Comic 회귀는 세 API 모두 PASS다.
전체 suite는 API26 54개 중 2 FAIL, API34/36 각각 1 FAIL이며 host는 NOT_RUN이다.
추가했던 probe의 새 페이지 콜백 대기가 설정 변경 완료 신호로 부적합해 실제 textZoom/visual callback으로 교정했다.
API26 picker는 fixture 폴더 진입 후 파일 label 탐색 실패를 보완했다. 기존 전체 assertion과 54 + 6 검증 목록은 유지한다.
[변경과 원본](NATIVE_UI_SYNCHRONIZATION.md)을 따른다. main 통합은 전체 결과 확인 후 진행한다.

## 2026-10-18 최신: Native UI 비동기 경계 보완

`dc06d3e` build 원본은 66/74/16 + ENOSPC 3 PASS다. device는 API26 54개 중 2 FAIL,
API34/36 각각 54개 중 1 FAIL이며 세 API host는 NOT_RUN이다. API26의 제품 EPUB flow는 동일 viewport에서 PASS다.
새 실패는 Comic semantics 스레드 접근, SDK probe 전환, Readium 지연 위치 알림, 시스템 picker 탐색이다.
기존 assertion·54개 manifest·6개 host를 유지한 후속 변경과 원본은 [Native UI 동기화](NATIVE_UI_SYNCHRONIZATION.md)에 정리했다.
main 통합은 아직 하지 않았다. 아래 내용은 이전 시점 이력이다.

## 2026-10-18 현재 검증 상태

제품 수정 `98d7f0f`는 EPUB 검색 요청을 화면 교체 동안 보존한다.
원본 확인 결과 build는 Android 66/shared 74/EPUB 16/ENOSPC 3 PASS,
API36은 native 54 + TXT/EPUB/Comic host 6 PASS다. API26/34는 각각 native 1 FAIL이며 host는 NOT_RUN이다.
문서 조회 시점을 보완한 `1622bb6`의 build 및 API34/36 각각 native 54 + host 6은 PASS다. API26은 키보드 전후 viewport가 달라 .03 progression 비교가 실패했고, 동일 viewport에서 비교하도록 후속 테스트를 보완 중이다.
과거 artifact 접근 차단은 이번 원본 다운로드에서 해소됐으며 ZIP digest를 확인했다.
[현재 분석·원본·후속 CI](EPUB_REFLOW_NAVIGATION.md)를 우선한다. 아래 절은 이전 시점 이력이다.
main 통합과 출시 승인은 미완료다.

## 2026-10-18 검증 갱신

`8ca750e` unsigned APK/AAB 생성 및 APK 정적 감사는 PASS다.
[정확 source/hash·감사 원본](evidence/archive-resume-verified/release-8ca750e/)을 보존한다.
최신 device matrix는 API26/34 FAIL, API36 SUCCESS(metadata)로 main 통합 전이다.
물리 release 성능·PSS/GPU·실제 16KB OS·TalkBack·전체 보안 corpus·최종 서명/출시 검증은 여전히 미완료다.
[최신 검증 보고서](RESUME_VALIDATION_2026-10-18.md)를 우선하며 아래 자료는 이전 소스의 이력이다.

기준 물리 Android 기기는 이 환경에 연결돼 있지 않다. 계획 §25의 제품 성능 판정은 BLOCKED_ENV다.
JVM/에뮬레이터 측정을 실기기 release 성능으로 바꾸어 적지 않는다.

| 지표 | 합격 목표 | 현재 판정 |
|---|---|---|
| cold launch → 조작 가능한 서재 | 30회 p95 ≤ 2초 | NOT_RUN |
| 5MiB TXT 새 index → 첫 본문 | p95 ≤ 1.5초 | NOT_RUN |
| 50MiB TXT 새 index → 첫 본문 | p95 ≤ 3초 | NOT_RUN |
| 50MiB TXT 기존 index 앞/중간/끝 복원 | 각각 p95 ≤ 1.5초 | NOT_RUN |
| 준비된 인접 페이지 → 안정 화면 | p95 ≤ 100ms | NOT_RUN |
| 연속 이동 → durable commit | p95 ≤ 1.5초 | 실기기 NOT_RUN; API36 TXT report 별도 |
| 50MiB TXT peak PSS | baseline +96MiB 이하 | NOT_RUN |
| Comic decoded bitmap | min(64MiB, heap 20%) | API36 native fixture PASS; 실기기 NOT_RUN |

자동 실행한 50MiB TXT의 실제 길이는 52,428,800 bytes다. bounded cache/search unit은
3,200개 window와 window당 최대 16,384 UTF-16 단위를 확인한다. 호스트 실행 시간은 위 UI latency가 아니다.
7eaa0f5 Comic 100장 fixture는 각 1080×1600이며 최대 bitmap 1,728,000 bytes,
해당 emulator 예산 13,421,772 bytes, PSS 130,708 → 153,404 KiB였다.
[원본](evidence/m09-comic-validation/native-7eaa0f5/summary.json)을 따르며 peak GPU 측정으로 부르지 않는다.

## 물리 측정 절차

1. 전용 기기의 model/fingerprint/API/ABI, `getconf PAGE_SIZE`, RAM, heap, 화면/주사율,
   배터리·온도·충전 상태, WebView 버전을 기록한다. 빌드 SHA/APK SHA256와 fixture manifest SHA256을 함께 보존한다.
2. 실제 설치할 release 또는 benchmark APK를 사용한다. unsigned APK는 설치 불가하므로
   별도 테스트용 서명 후 그 APK의 새 hash와 서명 종류를 기록한다. 배포 서명과 혼동하지 않는다.
3. 자체 생성 fixture만 가져온다. cold는 `adb shell am force-stop org.bookreader.mobile` 후 시작하고,
   `adb shell am start -W -n org.bookreader.mobile/.MainActivity`의 시작 시간과 실제 서재 READY 시점을 별도로 기록한다.
   `am start -W` TotalTime 하나만으로 조작 가능 시간의 PASS를 정하지 않는다.
4. 5/50MiB TXT index 유무, 앞/중간/끝 canonical offset을 통제한다. 진행도/원본을 삭제해 cold 조건을 만들지 않는다.
   앱 전용 파생 cache만 삭제한 별도 실험과 기존 cache 실험을 구분한다.
5. Perfetto/Android Studio profiler 또는 테스트용 event trace로 입력·첫 안정 frame·DB acknowledgement를 연결한다.
   각 조건 30회 이상 원자료를 남기고 median/p95/최댓값을 산출한다. 실패·timeout도 표본에서 숨기지 않는다.
6. `adb shell dumpsys meminfo org.bookreader.mobile`를 idle baseline 및 지속 독서·검색·설정 변경/Comic 빠른 100장 이동 중 수집한다.
   Java/native/graphics/PSS 및 bitmap 예약을 분리하고 OOM·GC·trim-memory 이벤트를 함께 남긴다.

출시 보고서에는 실제 측정 기기와 표본 파일을 연결한다. 숫자가 없거나 기기가 다른 경우 해당 gate는 통과하지 않는다.
