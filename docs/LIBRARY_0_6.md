# 서재 2.0 / 0.6.0 개발 기록

## 2026-10-29 Phase2 첫 기기 결과와 후속 수정

후보 `f35d6857358501f87c3c15339b57f71bb6e7cc8c`: build40871727052 원본 XML/필수 사례/digest PASS175.
Device40871727016은 native71개 중 API26 8 FAIL, API34/36 각각3 FAIL, host는 NOT_RUN.
[실패 원본과 기기별 목록](evidence/product-quality/failed-f35d685/summary.json)을 보존했다.

- batch 취소의 IO 정리 후 취소된 Main으로 복귀하면서 마지막 UI 갱신이 실행되지 않았다. UI 게시까지 NonCancellable 경계에 포함했다.
- 표지 picker의 OPENABLE 누락으로 테스트 provider Activity가 실패했다. 실제 picker 요청을 OPENABLE/단일 content URI 검증으로 통일했다.
- TXT 인코딩 변경 직후 재열기 timeout을 확인했다. 늦은 artwork 결과가 Book 전체를 바꾸던 경합 가능성을 제거했다.
  현재 revision/timestamp를 확인해 표지 관련 필드만 병합하고, 인코딩 성공 시 commit된 Book을 즉시 UI에 게시한다. 기존 위치 assertion은 유지했다.
- API26 Window의 CalledFromWrongThreadException을 확인했다. catalog/진행률을 IO 안에서 따로 게시하지 않고
  책 목록과 함께 Main에서 단일 snapshot으로 게시한다. 시스템 바 효과도 명시적으로 Main에서 실행한다.
- 같은 표지 재선택, Reader/설정 왕복 스크롤 유지, 화면 밖 EPUB 메타데이터 검색을 보완했다. native 필수 사례는72개다.

후속 `./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin :androidApp:lintDebug --console=plain` exit0.
로그 `phase2-coherent-library-state.log`, `phase2-native-fixes*.log`, `phase2-offscreen-metadata.log`.
새 소스의 API26/34/36 전체 회귀와 기존host6/새host1은 아직 대기다. 첫 실패를 이전 PASS로 덮어쓰지 않는다.
원본 캡처에서 책장/목록과 일괄 결과 UI를 확인했다. 1,000권 두 보기의 끝/처음/검색 탐색과24개 표지 캐시 제한은
세 API에서 PASS지만 전체 suite는 FAIL이다. 물리 FPS/성능이나 사용자 데이터 검증으로 확대하지 않는다.


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
화면에 보이는 카드와 서재 화면의 코루틴이 작업을 소유하며 publication/decoder를 직렬화하고 취소·닫기를 수행한다.
화면 밖 EPUB도 순차 추출하여 작가/제목 검색에 포함한다. Reader에 진입하면 이 작업을 취소한다.
캐시에서 퇴출한 bitmap을 강제로 recycle하지 않아 이미 표시 중인 화면이 깨지지 않게 한다.

## 주요 구현

- `shared/.../library/LibraryCatalog.kt`, `RoomLibraryManager.kt`, `database/LibraryDao.kt`: 편집 우선권·원자적 분류·서재 설정.
- `shared/.../database/Migrations.kt`, schema6: 명시적 migration과 실패 시 원본 보존.
- `androidApp/.../library/AndroidLibraryArtwork.kt`, `BookCoverStore.kt`, `epubAndroid/.../library/BoundedCoverImage.kt`: 표지 추출·보관·디코드.
- `androidApp/.../ui/LibraryComponents.kt`, `BookReaderApp.kt`, `AppViewModel.kt`: 편집/책장/선택/필터/검색/일괄 처리 화면.
- `importing/IncomingDocument.kt`, `MainActivity.kt`, `ui/AndroidBookManagement.kt`: picker 다중 grant와 중단 marker.

## 후속 동작 보완

같은 사용자 이미지를 다시 선택해도 프리셋을 확실히 바꾸도록 선택 이벤트를 갱신했다.
정렬/필터/정보 편집 뒤에는 기존 목록을 유지한 채 새 결과를 반영한다.
서재 스크롤 위치는 설정 탭 및 Reader 왕복 동안 보존하며 cold start 화면 계약은 그대로다.
관련 unit/compile/lint exit0: `phase2-scroll-state.log`, 표지 재선택 UI assertion 컴파일 `phase2-cover-reselect-test.log`.
화면 밖 EPUB를 검색할 수 있도록 서재 소유 순차 추출과 UI 사례를 추가했다. `phase2-offscreen-metadata.log` exit0.
후속 소스의 실제 native72개 검증은 아직 대기다.

## 검증 상태

- PASS: 기존 도구 재사용 환경 doctor, shared JVM/실제 SQLite83개, Android unit73개, Android 테스트 코드 컴파일·lint.
  `/workspace/work/bookreaderm-product-quality/phase2-catalog-tests-fixed.log`, exit0.
- PASS: `BOOKREADER_EVIDENCE_DIR=build/phase2-local ./scripts/ci-check.sh` exit0. Android73/shared83/EPUB16, 전체172개 및 APK/test APK/lint/schema6. [원본과 중간 실패](evidence/product-quality/phase2-local/summary.json).
- PASS: 후보f35d685 원격 build40871727052 원본 artifact SHA-256/CRC/XML/필수 사례 대조. Android73/shared83/EPUB16/실제 ENOSPC3, 총175개.
- NOT_RUN: 새 실제 Android71개와 기존 TXT/EPUB/Comic host6단계, 추가 active-batch force-stop 절차.
  `scripts/required-tests.json`에 필수 사례를 추가했다. 새 batch 준비 단계는 실제 host가 중간에 종료하며 JUnit PASS로 세지 않는다.
- 실제 기기 성능/16KB OS/TalkBack/설치 업데이트는 로컬 기기 부재로 미실행이다.
  새 1,000권 서재 탐색과 표지24개 캐시 측정도 실제 실행 전이며 물리 성능으로 확대하지 않는다.

중간 실패(서로 다른 책 fixture가 같은 SHA 사용, nullable cross-module smart cast, Context singleton lint,
테스트에 없는 Espresso API 참조)는 로그를 보존하고 각각 fixture/API/소유권을 수정했다. 테스트 assertion을 완화하지 않았다.
기기 실행 결과와 원본 화면 확인 후 이 문서의 결과를 갱신한다. Phase3·4와 공개 배포는 미완료다.
