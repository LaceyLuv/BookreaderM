# 제품 완성도 개선 — 감사와 단계별 진행

2026-10-24 사용자 [개발 지침 원문](reference/BookReaderM_Product_Improvement_Request_2026-10-24.md)을 적용한다.
기준은 `790175b`이며 제품/테스트 코드는 검증된 `4449f1e`와 동일하다. 원문은 수정하지 않는다.
기존 엔진·위치 계약·Room 데이터·서명키를 유지하고 단계별로 검증한다. 공개 릴리스는 별도 승인 대상이다.

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
| Phase 1 / 0.5.1 | 진행 중 | 문구·상태·기본 UI·문서, 핵심 엔진/스키마 변경 없음. 회귀 및 새 UI 검증 필요 |
| Phase 2 / 0.6.0 | 대기 | 서재 2.0: metadata/cover, 편집·책장·정렬/필터, batch import·검색 통합. migration 보존 검사 선행 |
| Phase 3 / 0.7.0 | 대기 | 도구/빠른 설정, bounded TXT 목차, version/revision에 묶인 annotation |
| Phase 4 / 0.8.0 | 대기 | 검증 가능한 backup/restore·기기/성능/접근성·전달/자동화. 실기기는 실제 자원 필요 |

각 단계의 PASS는 실제 실행 범위만 의미한다. 구현만 끝난 항목이나 기기 실행 없이 compiled test를
단계 전체 완료로 올리지 않는다. 실패·NOT_RUN·BLOCKED_ENV와 다음 착수 조건을 이 문서 및 TASKS/WORKLOG에 남긴다.
