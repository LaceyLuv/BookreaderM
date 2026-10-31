# 독서 경험 강화 / 0.7.0 개발 기록

## 2026-10-31 진행 중: 도구 모음·빠른 읽기 설정

TXT·EPUB·Comic 도구에 직접 만든 벡터 아이콘과 짧은 이름을 적용했다. 하단 쪽수 영역의 위치 이동 버튼은
기존 미리보기/취소 슬라이더를 연다. 상·하단 여백과 설명을 줄이고 overlay/본문 viewport는 유지한다.
기존 `ReaderSettingsDialog`의 한 draft와 저장 callback을 재사용한다. 글자 크기·행간·기본 글꼴·굵기·여백·색상·
읽기 방식·전환·밝기는 바로 표시하고, 파일 글꼴 관리·자간·화면 고정/전체화면·전체 기본 읽기 방식은 추가 설정으로 분리했다.
책별 읽기 방식과 전체 기본값의 범위를 명시한다. 상속 복원은 `readingMode`만 비워 다른 책별 값을 보존한다.
EPUB에서 행간/자간을 직접 바꾸면 기존 글꼴/굵기 선택과 같이 출판사 스타일을 해제해 변경값을 반영한다.

주요 파일: `ReaderToolButton.kt`, `ReaderChrome.kt`, `ReaderSettingsDialog.kt`, `CustomFontSettings.kt`,
세 포맷 `*ReaderScreen.kt`, `res/drawable/ic_reader_*.xml`.
DB·설정 codec·저장소·locator·진행도 writer·Readium host·TXT 본문 측정/캐시는 수정하지 않았다. 0.7.0-dev/code8/Room7 유지.

새 실제 UI 검사는 설정 취소의 raw row/viewport/진행도 불변, 빠른 항목 적용 후 정확한 canonical anchor,
상속 복원 시 만화 방향/맞춤 보존, Activity 재생성 및 서재 재열기 후 설정/진행도를 확인한다.
기존 큰 글자 1.0/1.3/2.0·사용자 글꼴·EPUB p179/host 교체·TOC·Comic 회귀는 유지하고 추가 설정을 여는 단계만 맞췄다.
로컬 CI exit0, Android75/shared95/EPUB16=186 및 APK/test APK/lint/schema7 PASS.
[원본](evidence/product-quality/phase3-controls-local/summary.json). API26/34/36 native74+host7 원격 실행은 대기다. 아직 기기 PASS나 도구 단위 완료가 아니다.
실제 캡처는 결과 수신 후 확인한다. 원본 로그는 `/workspace/work/bookreaderm-product-quality/phase3-controls/`.

기존 키와 배포된05ffd50 APK를 보존했다. 형광펜/메모·Phase4와 실기기 성능/접근성·공개 배포는 남아 있다.
아래 TXT 목차 결과와 예전 설치 파일은 이 단위의 검증 결과를 대신하지 않는다.

## 2026-10-29 현재: TXT 목차 단위 자동 검증 완료

`0.7.0-dev`/code8, Room7. [ADR0009](adr/0009-txt-toc-overrides.md).
목차 버튼에서 자동 후보와 현재 회차를 보고 이동하며, 제목 수정·제외/복구·현재 위치 수동 추가를 구현했다.
새 Reader 코루틴은 스캔·읽기·편집을 소유하고, 닫기/재시도/Reader 종료에서 취소한다.
수정 기록은 contentRevision별 별도 테이블과 sessionEpoch/기존 row 재검사로 보호한다.
기존 source/진행도/북마크/설정/서재 기록을 덮어쓰지 않는다. UI를 열거나 편집하는 동작으로 위치를 저장하지 않는다.

주요 소스: `TxtToc.kt`, `RoomTxtTocRepository.kt`, `TxtTocDao.kt`, `Migrations.kt`,
`TxtReaderToc.kt`, `ReaderTocDialog.kt`, `TxtReaderController.kt`, `TxtReaderScreen.kt`, `AndroidStores.kt`.
실제 UI 검사는 새 `TxtReaderFlowTest`에서 편집/제외/복구/수동 추가/정확한 이동/재열기/원본 해시를 확인한다.
기존 TXT host flow에 목차의 실제 외부 force-stop 후 복원과 원본 진행도 불변 검사를 추가했다.
새 실제 UI 캡처는 아래 API36 원본이다. API26/34에는 닫히는 추가 창의 프레임이 남아
최종 목록의 시각 증거로 사용하지 않는다. 그 원본도 삭제/편집하지 않았다.
[자체 생성 fixture 출처·해시](../fixtures/txt-toc-manifest.json).

로컬 shared95/Android unit75, APK 테스트 소스 컴파일/lint PASS. 정확한 명령은
`./gradlew :androidApp:compileDebugAndroidTestKotlin :androidApp:testDebugUnitTest :androidApp:lintDebug :shared:jvmTest --console=plain` exit0.
[원본](evidence/product-quality/phase3-toc-local/). 최초 migration95개 중1 FAIL은 fixture가 서재 설정 키를
`global` 대신 `library`로 넣은 오류였다. 실제 계약에 맞춰 수정했고 실패 XML도 보존했다.
필수 사례의 JVM 이름은 검사기가 정규화하는 `[jvm]` 없는 이름으로 등록했다.
전체 로컬 CI exit0, Android75/shared95/EPUB16=186개와 APK/test APK/lint/schema7 PASS.
제품/테스트 source `05ffd50ab8213ec9563a92149b6ed1930c510c3b`.
[build40892825363](https://github.com/LaceyLuv/BookreaderM/actions/runs/40892825363) PASS189(실제 ENOSPC3 포함),
[device40892825193](https://github.com/LaceyLuv/BookreaderM/actions/runs/40892825193) API26/34/36 각각 native73+host7 PASS.
[원본 요약](evidence/product-quality/verified-05ffd50/summary.json)의 artifact SHA/CRC, 필수 XML, source clean,
실제 force-stop/PID 부재·교체를 대조했다. 기존 EPUB p179와 TXT/Comic 위치 회귀도 유지했다.
`python3 /workspace/work/bookreaderm-product-quality/verify-phase3.py 05ffd50ab8213ec9563a92149b6ed1930c510c3b 40892825363 40892825193` exit0.
`./gradlew :androidApp:assembleRelease :androidApp:bundleRelease --console=plain` exit0. 설치 배포는 하지 않았다.

이전 TXT 도구는 검색·북마크·위치 이동이었으며, 이제 회차 목록에서 현재 항목을 보고 직접 편집/이동한다.
![실제 API36 TXT 목차](evidence/product-quality/verified-05ffd50/40892825193/mobile-device-evidence-api-36/build/device-evidence/fixture-screenshots/phase3-txt-toc.png)

Room6→7은 목차 수정 테이블만 추가하며 기존11개 테이블 정의는 동일하다.
[기존 locator/progress/cache/page/font/Readium 소스 불변](evidence/product-quality/phase3-toc-local/preserved-contracts.json).
기존1–6 스키마 migration/롤백, 미래 값·오래된 세션·동시 수정·삭제된 책·한도/DB 오류를 검사했다.
원본·독서 기록·북마크·설정·서재 분류를 수정하지 않는다. 사용자 책/URI/키를 CI에 올리지 않았다.

남은 한계: 자동 감지는 보수적인 후보이며 모든 소설의 제목 형식을 인식하지 않는다.
자동 후보5,000/수정1,000/제목120 UTF-16 단위 한도가 있다. 실기기 성능·TalkBack·전체 사용자 파일은 NOT_RUN이다.
중간 Phase2의 API36 Compose 측정 재진입 원인은 여전히 미확정이며 이번 전체 실행에서는 재발하지 않았다.
다음은 기존 overlay와 저장 경로를 재사용한 도구/빠른 설정, 정확한 선택 구간을 저장하는 형광펜/메모다.

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
