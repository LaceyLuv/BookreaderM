# BookreaderM

Android에서 오프라인으로 읽는 TXT·EPUB·만화 리더입니다. Android 8.0(API26) 이상을 지원합니다.
책은 앱 안에 복사해 보관하며 원본 파일을 변경하지 않습니다. 앱을 새로 시작하면 서재가 열립니다.

## 현재 기능

- **서재:** 책 가져오기·정보 확인·삭제, 제목/작가/파일명 검색, 하단 이어읽기.
- **TXT:** 페이지·스크롤, 본문 검색, 북마크, 인코딩 미리보기·변경.
- **EPUB:** EPUB2/3 읽기, 목차·검색·북마크, 글자 설정과 위치 복원.
- **만화:** ZIP/CBZ의 정적 JPEG·PNG·WebP, 한 장·연속·웹툰 보기, 방향·맞춤·확대.
- **읽기 설정:** 기본/번들/사용자 TTF·OTF 글꼴, 보통·굵게, 크기·간격·여백·색상,
  페이지 전환, 밝기·화면 켜짐 유지. 중앙 탭으로 독서 도구를 열고 닫습니다.
- **위치 보존:** TXT UTF-16 위치, EPUB locator, 만화 이미지 위치를 저장합니다.
  설정 변경·다시 열기 후 저장된 위치를 복원하며, 쪽수는 표시용입니다.

사용 안내: [독서 화면](docs/READER_DESIGN_2026-10-23.md),
[글꼴·전환·쪽수](docs/READER_FEATURES_0_4.md), [내 글꼴·굵기](docs/READER_FONTS_0_5.md).
표지·책 정보 편집·책장·일괄 가져오기·TXT 목차·형광펜·백업은 [제품 개선 계획](docs/PRODUCT_IMPROVEMENT.md)의 후속 단계입니다.

## 버전과 설치

현재 개발 버전은 **0.5.1(versionCode 6)**입니다. 가져오기 안내, 사용할 수 없는 책의 원인 표시,
빈 화면·검색·기본 UI와 문서를 정리합니다. 구현·실행 검증 상태는 [진행 기록](docs/PRODUCT_IMPROVEMENT.md)을 따릅니다.

이전 검증·패키지 기준은 **0.5.0 / 4449f1e**입니다. 당시 build 164개,
API26/34/36 각각 native 60 + host 6단계 PASS이며 [원본 결과](docs/evidence/custom-fonts/verified-4449f1e/summary.json)를 보존합니다.
이 결과를 새 버전의 기기 검증으로 대신하지 않습니다.

[설치 APK 안내](docs/INSTALLABLE_APK.md)와 [다운로드 운영 방안](docs/APK_DISTRIBUTION.md)을 확인하세요.
GitHub 릴리스 첨부와 공개 배포는 아직 완료되지 않았습니다. unsigned APK/AAB는 직접 설치용이 아닙니다.
기존 앱의 책·진행도를 유지하려면 같은 applicationId와 서명 인증서로 만든 업데이트가 필요합니다.
서명 오류가 나면 기존 앱을 삭제하거나 데이터를 지우지 마세요.

## 데이터와 지원 범위

독서 기록·북마크·설정·등록한 글꼴은 기기에 저장합니다. 개인 책이나 기록을 외부 서버로 자동 전송하지 않습니다.
앱 삭제 또는 데이터 지우기는 책의 앱 내부 복사본과 독서 기록을 삭제할 수 있습니다.
현재 앱 백업·복원 기능은 없으므로 원본 책 파일을 따로 보관하세요.

DRM·암호화·고정 레이아웃 EPUB, RAR/CBR, 애니메이션 이미지는 지원하지 않습니다.
큰 이미지에는 해상도 제한을 적용하며 손상되거나 자원 한도를 넘는 파일은 거부합니다.
실제 휴대전화 성능·TalkBack·전체 사용자 파일 호환성·16KB OS 검증과 정식 출시 승인은 남아 있습니다.

## 개발과 검증

Jetpack Compose Android UI, KMP `shared`의 모델·locator·Room DB,
안정 Readium 3.1.2 기반 `epubAndroid`를 사용합니다. 세 Reader의 저장/복원 계약을 유지합니다.
JDK 17, Gradle 8.13, AGP 8.11.1, Kotlin 2.2.20, SDK36/build-tools35를 고정합니다.
전체 버전과 근거는 [DEPENDENCIES](docs/DEPENDENCIES.md)를 따릅니다.

```bash
# 저장된 클라우드 환경에서는 각 shell에서 먼저 실행
source /workspace/toolchains/mobile-env.sh
./scripts/doctor.sh
./scripts/ci-check.sh
# 개인 독서 데이터가 없는 전용 검사 기기에서만
./scripts/device-check.sh
```

`ci-check`는 APK/test APK 빌드·lint·단위/실제 JVM DB·Room schema를 검사합니다.
`device-check`는 Android UI 및 TXT·EPUB·Comic의 실제 force-stop/재실행을 따로 검증합니다.
테스트 APK 컴파일만으로 기기 검증 PASS를 주장하지 않습니다.

[새 세션 인계](docs/SESSION_HANDOFF.md), [작업표](docs/TASKS.md), [검증 계약](docs/TESTING.md),
[QA](docs/QA_MATRIX.md), [작업 기록](docs/WORKLOG.md)를 참조하세요.
새 제품 개선 지침은 기존 계획의 범위 제한을 명시적으로 확장하지만 데이터 안전 계약은 그대로 유지합니다.

과거 설명과 결과는 [개선 전 README 원문](docs/reference/README_before_product_improvement_2026-10-24.md)에 보존했습니다.
`README_START_HERE.md`, `START_PROMPT.txt`, `MANIFEST.json`은 최초 계획 패키지의 이력입니다.
