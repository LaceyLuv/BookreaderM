# 제품 완성도 개선 — 감사와 단계별 진행

2026-10-24 사용자 [개발 지침 원문](reference/BookReaderM_Product_Improvement_Request_2026-10-24.md)을 적용한다.
기준은 `790175b`이며 제품/테스트 코드는 검증된 `4449f1e`와 동일하다. 원문은 수정하지 않는다.
기존 엔진·위치 계약·Room 데이터·서명키를 유지하고 단계별로 검증한다. 공개 릴리스는 별도 승인 대상이다.

## 현재 단계

[0.6.0 최종 보고서](LIBRARY_0_6.md): 서재2.0 구현과 build175/세 API native72+host7 자동 검증 PASS.
Phase3은 별도 브랜치에서 TXT 목차의 DB/UI를 연결했고 로컬186 PASS다. [진행·미검증 범위](READER_0_7.md).
아래 감사 표는 **2026-10-24 구현 전 기준**이며 현재 단계 표와 구분한다.

## 구현 전 감사

AGENTS/README/TASKS/SESSION_HANDOFF/READER_DESIGN_2026-10-23/READER_FEATURES_0_4/
READER_FONTS_0_5/QA_MATRIX/TESTING과 Android/shared/epubAndroid 소스·테스트·workflow를 대조했다.
오래된 계획의 미완료 표시는 현재 소스 구현 여부와 구분했다.

| 기능 | 현재 상태 | 코드·근거와 후속 |
|---|---|---|
| TXT·EPUB·Comic 가져오기, 읽기, 검색·북마크·진행도 | 구현 완료 및 자동 검증 완료 | ImportCoordinator, 세 ReaderController, RoomProgressWriter; 4449f1e native60+host6/API. 실기기 전체 보장은 아님 |
| 페이지/스크롤·색상·글꼴·굵기·간격·여백·전환·밝기 | 구현 완료 및 자동 검증 완료 | ReaderSettings, ReaderSettingsDialog, ReaderWindowEffects, UserFontStore. 중복 구현하지 않음 |
| 서재 시작·Reader 밖 하단 이어읽기 | 구현되었으나 UX 개선 필요 | AppViewModel/BookReaderApp. 제목·진행률만 있는 텍스트 바, 표지·위치 설명 없음 |
| 서재 그리드·메타데이터 검색·테마 | 구현되었으나 UX 개선 필요 | BookReaderApp/LibraryBody, filterLibraryBooks. 그리드는 이미 존재. 검색 탭은 동일 목록 필터 |
| 책 제목·작가·coverKey | 부분 구현 | Book/BookEntity에 필드 존재; ImportCoordinator는 파일명에서 제목만 설정. metadata/cover 추출·편집 경로 없음 |
| 실제 표지·사용자 표지·캐시 | 미구현 | SafeEpub/ComicManifest는 안전 검증·읽기를 지원하나 서재 표지 저장·디코드 경로 없음 |
| 정렬·읽기 상태 필터·책장·컬렉션·다중 선택 | 미구현 | BookDao.listBooks는 추가일순. 사용자 상태/책장 테이블 없음 |
| 일괄 가져오기 | 미구현 | DocumentIntents는 명시적으로 단일 URI만 허용. 단일 import journal은 재사용 가능 |
| 책 정보 편집 | 미구현 | 현재 dialog는 파일 정보·인코딩·삭제만 제공 |
| 독서 도구 | 구현되었으나 UX 개선 필요 | ReaderChrome overlay로 viewport 보존. 큰 텍스트 버튼 패널. Phase 3에서 개선 |
| EPUB 목차 | 구현 완료 및 자동 검증 완료 | EpubReaderScreen/controller의 Readium TOC/locator 경로 유지 |
| TXT 목차·형광펜·메모 | 미구현 | 현재 BookmarkEntity는 위치 북마크이며 선택 구간/메모와 다름 |
| 백업·복원 | 미구현 | 앱/OS 백업 정책은 배제 상태. 버전 포맷·병합·복구 설계 필요 |
| TTS·사전·통계 | 미구현 | 후순위 선택 기능; 핵심 단계에 섞지 않음 |
| 실기기 성능·대규모 서재·TalkBack·16KB OS | 실제 검증 부족 | 로컬 기기/KVM 없음. 자동 검사 통과로 대체하지 않음 |
| 설치·배포 | 부분 구현 | 기존 키로 서명한 0.5.0 APK 있음. 업로드 HTTP400, 미게시 초안 첨부0. 자동 배포 미구현 |

### 확인한 Phase 1 문제

- ImportStatus 성공 문구와 공유 제목이 TXT로 고정되어 EPUB/Comic에서도 잘못 표시된다.
- import 검증 단계가 모두 “텍스트 확인 중”이며 크기/오류 문구가 Comic 지원을 누락한다.
- AppViewModel.openBook은 unavailable/작업 중/엔진 부재를 안내 없이 반환한다.
- Empty/search-empty는 다음 행동이 없고, 긴 import 문구와 버튼이 한 Row에서 겹칠 수 있다.
- 탭 아이콘은 폰트 문자이고 앱 기본 색상과 Reader 남색·민트가 서로 다르다.
- README는 0.2.0/2026-10-22 설명과 이미 구현된 기능의 “이후 작업” 설명이 섞여 있다.
- 파일명 제목·책 정보 편집·검색 탭 역할은 Phase 2에서 함께 해결한다. 파일명 자체가 잘못된 제목이라고 일괄 추정하지 않는다.

## 단계와 검증

| 단계 | 상태 | 결과/선행 조건 |
|---|---|---|
| 감사 | 완료 | 위 코드 기준 분류. 기존 로컬 161개 PASS, 새 변경 결과와 구분 |
| Phase 1 / 0.5.1 | 구현·자동 검증 완료 | [결과·실제 화면](UX_POLISH_0_5_1.md). 5a8aad2 build166, native63+host6/API26/34/36 PASS. 실기기 미실행 |
| Phase 2 / 0.6.0 | 구현·자동 검증 완료 | a22970b build175, native72+host7/API26/34/36 PASS. [결과](LIBRARY_0_6.md), 실기기 미실행 |
| Phase 3 / 0.7.0 | 진행 중 | TXT 목차 자동/수동·revision별 편집 저장/이동 구현, 로컬186 PASS. 기기 검증 대기, 도구·annotation은 후속 |
| Phase 4 / 0.8.0 | 대기 | 검증 가능한 backup/restore·기기/성능/접근성·전달/자동화. 실기기는 실제 자원 필요 |

각 단계의 PASS는 실제 실행 범위만 의미한다. 구현만 끝난 항목이나 기기 실행 없이 compiled test를
단계 전체 완료로 올리지 않는다. 실패·NOT_RUN·BLOCKED_ENV와 다음 착수 조건을 이 문서 및 TASKS/WORKLOG에 남긴다.

## Phase 1 구현 결과

- 가져오기 완료/공유 확인에 실제 포맷을 표시하고, 읽기와 닫기를 분리한다. 진행 중 크기는 읽기 쉬운 단위로 표시한다.
- 사용할 수 없는 책을 누르면 원인과 다음 행동을 안내한다. 기존 책·진행도를 갱신하거나 Reader를 열지 않는다.
- 빈 서재의 책 추가, 검색 결과 없음의 검색 초기화, 오류 재시도와 안내 닫기를 제공한다.
- 민트·남색 팔레트, 벡터 아이콘, 제목/진행률을 분리한 이어읽기, 책 정보와 카드의 가독성을 정리했다.
- 글자 배율에 따라 카드 폭을 조절하고 다크 모드 navigation 색상을 통일한다. Reader 색상값은 그대로다.
- README에서 현재 기능과 후속 기능을 구분했다. 실제 표지, 정보 편집, 책장, 통합 검색은 아직 Phase 2 작업이다.

주요 소스는 `ui/BookReaderApp.kt`, `ui/AppViewModel.kt`, `ui/BookReaderTheme.kt`,
`ui/LibraryUi.kt`, `ui/LibraryPresentation.kt`, `reader/ReaderChrome.kt`와 drawable이다.
Room schema5, 관리 원본, locator/version/revision, 저장 epoch/sequence, 북마크와 설정 저장 형식을 변경하지 않았다.

## Phase 2 착수 순서와 완료 기준

1. **메타데이터·표지 저장 계약:** 기존 title/author/coverKey를 재사용하고 사용자가 편집한 값의 우선권을 저장한다.
   SafeEpub/Readium의 검증된 publication metadata와 ComicManifest를 통해 입력을 읽는다.
   표지 생성/디코드의 작업 소유권·취소·픽셀/바이트 한도·캐시 폐기를 정의한다. 자동 추출 실패는 독서를 막지 않는다.
2. **정보 편집·읽기 상태·책장:** 필요한 Room 명시적 migration을 먼저 추가하고 이전 스키마의 책·진행도·북마크·설정 보존을 검사한다.
   제목/작가/표지/읽기 상태/책장을 편집한 뒤 재시작해도 유지되며 자동 추출이 덮어쓰지 않아야 한다.
3. **서재 화면:** 실제/대체 표지, 상태/진행률, 정렬/필터, 그리드/리스트, 검색 통합, 길게 누르기/다중 선택을 연결한다.
   시작은 서재, 이어읽기는 Reader 밖 하단이라는 기존 계약을 유지한다.
4. **일괄 가져오기:** 단일 파일의 검증·journal·중복 처리를 재사용하는 순차 큐를 제공한다.
   중간 취소/부분 실패/중복/프로세스 종료가 이미 완료한 책이나 독서 기록을 손상시키지 않아야 한다.
5. **0.6.0 검증:** 각 단위의 DB/순수 로직 검사 뒤 실제 API26/34/36 UI·재시작·강제 종료 회귀를 실행한다.
   생성한 대규모 fixture 서재로 스크롤/캐시를 측정하고 실제 측정 범위만 보고한다. 실기기 결과를 추정하지 않는다.

이 목록의 구현과 자동 검증 결과는 [0.6.0 보고서](LIBRARY_0_6.md)를 따른다. 실기기 검증은 남아 있다.
