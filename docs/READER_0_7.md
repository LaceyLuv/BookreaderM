# 독서 경험 강화 / 0.7.0 개발 기록

## 2026-10-29 현재: TXT 목차 연결, 기기 검증 전

`0.7.0-dev`/code8, Room7. [ADR0009](adr/0009-txt-toc-overrides.md).
목차 버튼에서 자동 후보와 현재 회차를 보고 이동하며, 제목 수정·제외/복구·현재 위치 수동 추가를 구현했다.
새 Reader 코루틴은 스캔·읽기·편집을 소유하고, 닫기/재시도/Reader 종료에서 취소한다.
수정 기록은 contentRevision별 별도 테이블과 sessionEpoch/기존 row 재검사로 보호한다.
기존 source/진행도/북마크/설정/서재 기록을 덮어쓰지 않는다. UI를 열거나 편집하는 동작으로 위치를 저장하지 않는다.

주요 소스: `TxtToc.kt`, `RoomTxtTocRepository.kt`, `TxtTocDao.kt`, `Migrations.kt`,
`TxtReaderToc.kt`, `ReaderTocDialog.kt`, `TxtReaderController.kt`, `TxtReaderScreen.kt`, `AndroidStores.kt`.
실제 UI 검사는 새 `TxtReaderFlowTest`에서 편집/제외/복구/수동 추가/정확한 이동/재열기/원본 해시를 확인한다.
기존 TXT host flow에 목차의 실제 외부 force-stop 후 복원과 원본 진행도 불변 검사를 추가했다.
새 UI 캡처는 `phase3-txt-toc.png`; 아직 기기 실행 전이며 완성 화면처럼 합성하지 않는다.
[자체 생성 fixture 출처·해시](../fixtures/txt-toc-manifest.json).

로컬 shared95/Android unit75, APK 테스트 소스 컴파일/lint PASS. 정확한 명령은
`./gradlew :androidApp:compileDebugAndroidTestKotlin :androidApp:testDebugUnitTest :androidApp:lintDebug :shared:jvmTest --console=plain` exit0.
[원본](evidence/product-quality/phase3-toc-local/). 최초 migration95개 중1 FAIL은 fixture가 서재 설정 키를
`global` 대신 `library`로 넣은 오류였다. 실제 계약에 맞춰 수정했고 실패 XML도 보존했다.
필수 사례의 JVM 이름은 검사기가 정규화하는 `[jvm]` 없는 이름으로 등록했다.
전체 로컬 CI exit0, Android75/shared95/EPUB16=186개와 APK/test APK/lint/schema7 PASS.
API26/34/36 native73+host7 및 ENOSPC는 원격 검증 대기다.

형광펜/메모, 독서 도구 재설계·빠른 설정과 Phase4는 미완료다. 검증된0.6.0 APK/브랜치는 별도로 보존한다.

아래는 첫 단위의 이력이다.

2026-10-29, `codex/reader-070`, 작업 위치 `/workspace/BookreaderM-reader-070`.
Phase2 제품 fe29dea의 build175/세 API native72+host7을 확인한 뒤 별도 worktree에서 착수했다.
0.6.0 최종 캡처/문서 작업은 `/workspace/BookreaderM`의 `codex/library-060`에서 보존한다.

## 첫 단위: TXT 자동 목차 분석기

`shared/.../toc/TxtTocScanner.kt`는 canonical UTF-16 범위를16,384단위씩 읽으며
256단위를 넘는 행을 버리고 제목120단위/후보5,000개로 제한한다. 취소를 검사하고
reader가 surrogate 경계 때문에 짧게 돌려준 실제 길이만큼만 전진한다.
제N화·N화·제N장·N회·Chapter N·프롤로그·에필로그와 대괄호 제목을 감지한다.
일반 숫자/본문 중 언급/접미사가 붙은 문장/지나치게 긴 행은 후보로 올리지 않는다.
보수적 자동 후보이므로 수동 추가·수정·제외 UI와 revision별 저장을 다음 단위에서 연결한다.

JVM6개가 한글/영문/emoji/창 경계/CRLF/중복 제목의 서로 다른 정확한 offset,
1백만 글자 긴 줄·후보 제한·취소·잘못된 reader 응답을 검사한다.
첫 `./gradlew :shared:jvmTest --console=plain` exit0, shared89개 PASS.
로그 `/workspace/work/bookreaderm-product-quality/phase3-toc-scanner.log`.
현재 UI와 DB는 연결 전이며 사용자가 쓸 수 있는 목차 기능 완료로 보고하지 않는다.
스키마/버전/기존 Reader와 서명키를 아직 변경하지 않았다.

## 다음 단위

1. book/revision/version에 묶인 수정·제외·수동 목차 저장과 migration/롤백 검증.
2. Reader 소유 비동기 스캔/취소, 목차 목록·현재 회차·정확한 위치 이동.
3. 도구/빠른 설정과 정확한 선택 범위를 사용하는 TXT·EPUB 형광펜/메모.

고정 Compose1.9.3/Readium3.1.2 공개 API를 확인했다. SelectionContainer의 좌표 callback은 internal이므로 사용하지 않는다.
BasicTextField의 공개 TextFieldValue selection과 기존 측정/제스처 계약을 실제 Android에서 비교해야 한다.
EPUB는 공개 currentSelection/applyDecorations와 완전 SDK Locator를 사용한다.
원본 source jar SHA/API 메모는 작업 폴더 `phase3-api/`, `phase3-notes.md`에 보존했다.
