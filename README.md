# BookreaderM

Android에서 오프라인으로 읽는 TXT·EPUB·만화 리더입니다. Android 8.0(API26) 이상을 지원합니다.
책은 앱 안에 복사해 보관하며 원본 파일을 변경하지 않습니다. 앱을 새로 시작하면 서재가 열립니다.

## 현재 기능

- **서재:** 실제/사용자 표지, 제목·작가 편집, 읽기 상태, 책장, 격자/목록·정렬·필터, 통합 검색, 다중 선택·일괄 가져오기, 하단 이어읽기.
- **TXT:** 페이지·스크롤, 본문 검색, 북마크, 인코딩 미리보기·변경.
- **EPUB:** EPUB2/3 읽기, 목차·검색·북마크, 글자 설정과 위치 복원.
- **만화:** ZIP/CBZ의 정적 JPEG·PNG·WebP, 한 장·연속·웹툰 보기, 방향·맞춤·확대.
- **읽기 설정:** 기본/번들/사용자 TTF·OTF 글꼴, 보통·굵게, 크기·간격·여백·색상,
  페이지 전환, 밝기·화면 켜짐 유지. 중앙 탭으로 독서 도구를 열고 닫습니다.
- **위치 보존:** TXT UTF-16 위치, EPUB locator, 만화 이미지 위치를 저장합니다.
  설정 변경·다시 열기 후 저장된 위치를 복원하며, 쪽수는 표시용입니다.

사용 안내: [독서 화면](docs/READER_DESIGN_2026-10-23.md),
[글꼴·전환·쪽수](docs/READER_FEATURES_0_4.md), [내 글꼴·굵기](docs/READER_FONTS_0_5.md).
TXT 목차·형광펜·백업은 [제품 개선 계획](docs/PRODUCT_IMPROVEMENT.md)의 후속 단계입니다.

## 버전과 설치

현재 검증 버전은 **0.6.0(versionCode 7)**입니다. 서재2.0의 구현·화면·호환성은 [최종 보고서](docs/LIBRARY_0_6.md)를 따릅니다.

**0.6.0 / a22970b**의 build175개와 API26/34/36 각각 native72+host7단계가 PASS입니다.
[검증 원본](docs/evidence/product-quality/verified-a22970b/summary.json)을 보존했습니다.
기존 키로 서명한 시험용 APK를 생성했으며 이전0.5.1 설치 파일도 유지합니다.
후속 `codex/reader-070` 브랜치의 TXT 목차는 자동·수동/수정·제외/이동과 build189/세 API native73+host7 검증을 완료했습니다.
[후속 개발 결과](https://github.com/LaceyLuv/BookreaderM/blob/codex/reader-070/docs/READER_0_7.md). 형광펜·도구 개편·백업은 미완료입니다.

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
