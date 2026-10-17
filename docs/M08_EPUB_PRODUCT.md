# M08 EPUB 제품 연결 — 진행 중

main `22526d3`에서 앱의 `책 추가`/외부 VIEW/SEND → journal 관리 복사본 → 안정 Readium Fragment Reader를 연결했다. 전체 Locator JSON과 contentRevision을 유지하며, 이전 기록 조회·실제 host 복원 전에는 새 진행도를 쓰지 않는다.

제품 경로는 목차, bounded literal 검색(50개/page·최대 1000), 북마크, 이전 위치로 돌아오기, 페이지/스크롤·테마·글자/행간/자간/여백·창 설정, Back/서재/이어읽기를 포함한다. Activity 재생성은 같은 ViewModel owner를 유지하고, 일반 새 프로세스는 서재다. owner 취소·닫기에 publication/search cursor를 해제하고 복원 대기에 30초 한도를 둔다.

Readium은 글자·여백을 배율로 받으므로 EPUB 설정에는 배율을 표시한다. Readium이 지원하지 않는 음수 자간은 EPUB에서 0으로 표시하고 기존 TXT 설정 bytes는 보존한다. 전역 설정을 강제로 다시 쓰지 않는다. 세부 공개 API·버전은 ADR-0005를 따른다.

| 근거 소스 | 실제 결과 |
|---|---|
| bcc2081 remote build | Android unit 64/shared JVM 71/EPUB unit 13/native ENOSPC 3 PASS |
| bcc2081 native | 51개 중 50 PASS/1 FAIL. SDK가 검색 highlight를 canonical currentLocator에 그대로 남길 것이라는 test expectation 실패 |
| eb73378 native | ordinary 51 + 기존 TXT 외부 host 2 PASS. 실제 Navigator progression/재생성/글자 설정 후 복원 비교 |
| 22526d3 native | ordinary 52 + 기존 TXT 외부 host 2 PASS. 음수 TXT 자간도 EPUB 열기 성공·raw 설정 보존 |

원본 source/artifact digest/XML/PID proof는 [evidence](evidence/m01-epub-validation/)에 보존한다. host 2단계는 **TXT**의 외부 force-stop이며 EPUB 자체 force-stop의 증거로 바꾸지 않는다. EPUB/Comic 각각의 별도 host flow는 후속 브랜치에서 실행 중이다.

암호화/DRM/고정 레이아웃/active script 및 외부 자동 리소스는 지원하지 않는다. EPUB2/3 자체 fixture의 정상 이미지/CSS/TOC·검색·CSP enforcement/위장 markup 거부는 실제 WebView에서 검증했다. 개인 도서/URI는 CI 자료로 사용하지 않는다.

추가 corpus, malformed archive 위치/리소스 UI, 독립 SAFE review, 실제 TalkBack·성능과 지원 OS 전체 회귀는 후속 gate다. M08 전체/출시 승인 판정은 아니다.

## 후속 exact restore 검증

강화한 964f8cc 실제 문단 viewport 검증에서 글자 크기 변경 후 위치 이탈을 발견했다.
a5a7a31은 SDK precise text/first-visible-element locator를 보존하지만 후속 native는
progression 근접 assertion 1 FAIL(53 중 52 PASS)이다. 제품 exact restore 완료를 주장하지 않는다.
9dfb884는 출판사 스타일 기본 유지/명시적 opt-out과 취소 가능한 seek를 보완했고 최신 native 결과는 확인 중이다.
자세한 실패 수치·재개 절차는 [전달서](SESSION_HANDOFF.md)를 따른다.
