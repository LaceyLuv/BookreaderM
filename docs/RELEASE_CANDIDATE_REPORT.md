# 출시 후보 감사 — 진행 중, 2026-10-17

**판정: 출시 보류(BLOCKED).** 현재 버전은 내부 검증용이며 v1.0 완료/스토어 배포 승인 상태가 아니다.
사용자의 남은 계획 진행 요청에 따라 세 포맷 제품 구현과 자동 검증·패키지 준비를 진행했다.
서명키 업로드, 스토어 제출, 원격 배포는 수행하지 않았다.

## 검증 단위

- main 22526d3: EPUB 제품 통합, API36 ordinary 52/TXT host 2 PASS. 이후 문서 3940a35.
- Comic 후속 9dfb884: 세 포맷 제품 경로, 정확 EPUB anchor 보완, 목록 상태 캡처, markup/positions 한도,
  archive host 6단계 및 Android 26/34/36 검증. 원격 결과 확인 중.
- local 관련 검사: Android unit 66/shared 74/EPUB 16, APK/test APK compile 및 app/module lint PASS.
- 207d2a1/361b3f9/964f8cc native 전체 실패를 보존한다. 개별 Comic 통과와 전체 suite 통과를 구분한다.

## 패키징

14a79be에서 `:androidApp:assembleRelease :androidApp:bundleRelease`가 실제 성공했다.
c1ab039의 unsigned APK/AAB도 실제 빌드했고 [hash/서명](evidence/m10-package-audit/archive-c1ab039-artifacts.json)·[정적 감사](evidence/m10-package-audit/archive-c1ab039-release.json)를 보존했다. 이후 제품 보완 소스의 최종 패키지는 재생성 예정이다.
Gradle의 `signReleaseBundle` task 명칭만으로 서명됐다고 판단하지 않는다.
실제 apksigner/jarsigner 결과와 APK/AAB SHA256, source SHA, dependency graph를 함께 연결해야 한다.
debug 기기 테스트가 unsigned release APK의 실행 검증은 아니다. release minify/R8 사용·성능을 검증했다고 주장하지 않는다.

## 남은 출시 blocker

| 항목 | 이유 / 다음 완료 증거 |
|---|---|
| 전체 최신 native/외부 host | 강화한 exact EPUB 문단 검증 및 TXT/EPUB/Comic host 6단계의 source별 결과 확인 필요 |
| 지원 Android 매트릭스 | API26/34/36 실행 중; 나머지 OS/OEM/ABI는 대표 범위와 별도 미검증을 표시 |
| 실기기 성능/PSS/GPU | [성능 목표](PERFORMANCE.md)의 release/benchmark 30회 원자료 필요; 로컬 기기 없음 |
| 실제 16KB 실행 | APK ELF/ZIP 정렬 PASS와 구분하여 16KB OS 기기에서 세 포맷 실행 필요 |
| 실제 TalkBack/접근성 | 버튼 존재·font scale 테스트 외 읽기 순서/탐색/gesture의 물리 검증 필요 |
| backup/OEM D2D | manifest/XML 정적 제외 외 실제 OS 백업·복원 동작 확인 필요 |
| 전체 SAFE/공격 corpus | [QA](QA_MATRIX.md)의 포맷별 미실행 조합과 [보안](SECURITY.md)의 제품 host 범위 보완 필요 |
| 제품 계약 감사 | 9dfb884가 책 그리드·세 포맷 seek/메뉴·publisher style 기본값을 보완; 최신 native 결과 필요 |
| 독립 코드 리뷰 | 이번 source에 대한 새 독립 리뷰 NOT_RUN; 과거 알파 승인을 전용하지 않음 |
| 최종 배포 서명/설치/업데이트 | 사용자 승인 및 실제 서명 환경 필요; unsigned 준비와 배포를 구분 |

## 알려진 제한

암호화 없는 reflowable EPUB2/3, TXT, 정적 JPEG/PNG/WebP ZIP/CBZ만 지원한다.
DRM/FXL/RAR/CBR/animation/recursive archive는 지원하지 않는다. EPUB 전체 markup·검색 한도를 초과하면
명시적 오류이며 내용을 조용히 잘라내지 않는다. 긴 Comic은 다운샘플로 글자가 작아질 수 있다.
일반 cold start는 서재이며 Continue를 눌러 마지막 durable 위치로 이동한다.
앱 삭제/데이터 지우기는 내부 책과 기록을 지울 수 있고 앱 백업/동기화/복원 기능은 없다.

각 blocker를 실제 증거 없이 DONE으로 올리지 않는다. 최신 결과를 받으면 이 문서의 소스·패키지·판정을 갱신한다.

## 세션 종료 시 최신 발견

사용자가 2026-10-17 현재 작업까지만 정리하고 다음 세션으로 넘기도록 지시했다. 새 구현은 중단한다.
`a5a7a31` device run 39016613293은 ordinary 53개 중 52 PASS/1 FAIL이며 host 6단계는 NOT_RUN이다.
실패는 EPUB 제품 test의 `assertRenderedNear`: expected progression 0.9264910536779324,
actual 0.9565038378966562, tolerance 0.03이다. 정확 문단 표시 assertion보다 먼저 이 비교가 실패했다.
precise text locator의 복원과 progression 값 차이의 관계를 추가로 확인해야 하며 허용 오차만 넓혀 PASS를 만들지 않는다.
`9dfb884`의 최신 local full CI는 66/74/16 및 schema/lint/build PASS다.
원격 build 39017137521 및 API26/34/36 device 39017137527은 결과 확인이 필요하다.
[전달서](SESSION_HANDOFF.md)를 먼저 읽고 재개한다.
