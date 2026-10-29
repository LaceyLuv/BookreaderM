# 서재 2.0 / 0.6.0 개발 기록

2026-10-29, `codex/library-060`. [사용자 계획](reference/BookReaderM_Product_Improvement_Request_2026-10-24.md)의 Phase 2다.
0.5.1 검증 소스와 기존 설치 파일을 보존하며 main에는 통합하지 않았다. **현재 실제 Android 검증 전이며 완료판이 아니다.**

## 사용자 동작

- 제목·작가·표지를 보여 주는 카드와 목록 보기를 제공한다. EPUB 메타데이터/표지와 Comic 첫 유효 이미지를 추출하고,
  TXT 및 표지 추출 실패 시 제목·작가를 넣은 생성 표지를 표시한다. 생성 표지는 6개 스타일을 선택할 수 있다.
- 책 정보의 `정보 편집`에서 제목·작가·표지·읽기 상태·책장을 저장한다. 선택한 이미지의 작은 사본을 앱 안에 보관하며,
  저장한 수동 값은 늦게 끝난 자동 추출이 덮어쓰지 않는다. 완독 지정은 실제 Reader 위치를 옮기지 않는다.
- 최근 읽기/추가 날짜/제목/작가순 정렬, 읽기 상태와 책장 필터, 목록/격자 보기 설정을 저장한다.
  검색은 서재 상단에서 열며 제목·작가·원래 파일명을 찾는다. 하단 메뉴는 서재와 설정이다.
- 책장을 생성·이름 변경·삭제할 수 있다. 책을 길게 누르면 다중 선택하며 최대200권을 책장에 넣거나 뺀다.
  책장 삭제는 분류만 해제하고 책이나 독서 기록을 삭제하지 않는다.
- 책 추가에서 최대64개 파일을 선택한다. 순서대로 기존 importer의 검증·중복 판정을 거치고 파일별 결과를 표시한다.
  개별 형식 오류 뒤에는 계속 진행하며 저장 공간/DB 오류 또는 취소 시 남은 큐를 멈춘다.
  이미 완료된 책을 유지한다. 강제 종료 후 journal 복구를 수행하고 완료된 책 보존 및 남은 파일 재선택을 안내한다.
  저장된 인코딩 선택 대기 파일은 기존 복구 경로로 계속 처리할 수 있다.

## 보존과 자원 제한

[ADR0008](adr/0008-library-catalog-and-covers.md). Room5→6 migration은 기존 Book/진행도/북마크/설정 행을
재작성하지 않고 별도 서재 테이블을 추가한다. metadata update는 title/author/coverKey/updatedAt만 변경한다.
locator/revision/session epoch/sequence와 원본 관리 파일은 유지한다. 알 수 없는 서재 값은 임의 초기화하지 않는다.

자동 표지는 cacheDir128MiB, 선택한 표지는 filesDir128MiB, 표시 bitmap 캐시는8MiB다.
JPEG/PNG/WebP 이미지와 EPUB 표지 입력은8MiB 이하, 결과JPEG는384×576px/768KiB 이하이다.
Comic은 기존 엔트리64MiB 제한, bitmap8MiB budget과 첫8개 후보 제한을 사용한다.
화면에 보이는 카드의 코루틴이 작업을 소유하며 publication/decoder를 직렬화하고 취소·닫기를 수행한다.
캐시에서 퇴출한 bitmap을 강제로 recycle하지 않아 이미 표시 중인 화면이 깨지지 않게 한다.

## 주요 구현

- `shared/.../library/LibraryCatalog.kt`, `RoomLibraryManager.kt`, `database/LibraryDao.kt`: 편집 우선권·원자적 분류·서재 설정.
- `shared/.../database/Migrations.kt`, schema6: 명시적 migration과 실패 시 원본 보존.
- `androidApp/.../library/AndroidLibraryArtwork.kt`, `BookCoverStore.kt`, `epubAndroid/.../library/BoundedCoverImage.kt`: 표지 추출·보관·디코드.
- `androidApp/.../ui/LibraryComponents.kt`, `BookReaderApp.kt`, `AppViewModel.kt`: 편집/책장/선택/필터/검색/일괄 처리 화면.
- `importing/IncomingDocument.kt`, `MainActivity.kt`, `ui/AndroidBookManagement.kt`: picker 다중 grant와 중단 marker.

## 검증 상태

- PASS: 기존 도구 재사용 환경 doctor, shared JVM/실제 SQLite83개, Android unit73개, Android 테스트 코드 컴파일·lint.
  `/workspace/work/bookreaderm-product-quality/phase2-catalog-tests-fixed.log`, exit0.
- PASS: `BOOKREADER_EVIDENCE_DIR=build/phase2-local ./scripts/ci-check.sh` exit0. Android73/shared83/EPUB16, 전체172개 및 APK/test APK/lint/schema6. [원본과 중간 실패](evidence/product-quality/phase2-local/summary.json).
- NOT_RUN: 새 실제 Android71개와 기존 TXT/EPUB/Comic host6단계, 추가 active-batch force-stop 절차.
  `scripts/required-tests.json`에 필수 사례를 추가했다. 새 batch 준비 단계는 실제 host가 중간에 종료하며 JUnit PASS로 세지 않는다.
- 실제 기기 성능/16KB OS/TalkBack/설치 업데이트는 로컬 기기 부재로 미실행이다.
  새 1,000권 서재 탐색과 표지24개 캐시 측정도 실제 실행 전이며 물리 성능으로 확대하지 않는다.

중간 실패(서로 다른 책 fixture가 같은 SHA 사용, nullable cross-module smart cast, Context singleton lint,
테스트에 없는 Espresso API 참조)는 로그를 보존하고 각각 fixture/API/소유권을 수정했다. 테스트 assertion을 완화하지 않았다.
기기 실행 결과와 원본 화면 확인 후 이 문서의 결과를 갱신한다. Phase3·4와 공개 배포는 미완료다.
