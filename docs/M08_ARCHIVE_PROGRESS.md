# Archive 진행도·EPUB 검색 기반 — 2026-10-17

main `6ee9d4f`의 ArchiveProgressController는 EPUB/Comic host가 사용할 저장 수명 관리 코드다.
제품 host에 아직 연결하지 않았다. M08/M09 완료로 처리하지 않는다.

실패/잘못된 포맷·미지원 payload 조회는 기본값 저장 및 세션 activation을 차단한다.
실제 host 복원 callback 이후 한 번만 세션을 만들고, READY의 사용자 이동만 받아들인다.
이벤트는 book/revision/epoch/sequence를 고정하며 250ms 정지 저장과 1초 주기 저장을 함께 사용한다.
reflow·renderer 오류·종료에서도 이전에 수락한 anchor를 flush하고, 과거 generation의 callback은 거부한다.
activation 대기를 취소한 뒤 flush하며 기존 Android ReaderActivationGate에 연결할 예정이다.
전체 SDK JSON과 Comic entryKey/normalized anchor를 유지한다.

`e721cda`의 BoundedEpubSearchService는 공개 Readium SearchService를 사용한다.
SDK 기본 구현이 한 chapter의 모든 결과를 List로 만들고 snippet을 공백까지 확장하는 경로 대신,
KMP cursor·최대 50개 결과 page·고정 160자 전후 snippet·전체 1000개·30초 작업 예산을 적용한다.
문자열 literal/대소문자 구분 검색이며 regex/whole word 옵션을 제공하는 척하지 않는다.
search resource 2MiB 한도는 parser의 16MiB 한도와 별개다. 초과는 명시적 오류이며 chapter를 조용히 건너뛰지 않는다.
읽기·추출·검색은 IO dispatcher에서 수행하고, 취소/닫힘 후 결과를 계속 만들지 않는다.

local `./scripts/ci-check.sh` exit 0: Android 64/shared 71/archive·EPUB·Comic 13, APK/test APK/lint/schema 5 PASS.
[원본 결과](evidence/archive-reader-validation/local-ci/summary.json). 초기 JSON dependency 누락 compile 실패도 보존했다.
기본 progress 4개와 KMP 2개의 회귀를 mandatory manifest에 넣었다.
Android search page/한도 및 Navigator 이동 검증은 native 실행 후 기록한다. UI·프로세스 종료·실기기 검증을 대체하지 않는다.
