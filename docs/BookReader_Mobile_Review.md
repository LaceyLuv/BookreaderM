# BookReader Mobile 원문 검토 보고서

> 기준일: 2026-10-01  
> 대상: `BookReader_Mobile_v1_Plan.md`  
> 판정: **GO WITH FIXES**  
> 의미: 제품 방향을 폐기할 필요는 없지만, 원문 그대로는 개발자가 중요한 결정을 추측해야 한다. 개정 계획과 작업별 검증 조건을 적용하여 구현을 시작한다.  
> 원문의 실제 코드가 조사된 것은 아니다. 아래는 **설계상 빈틈과 그로 인해 발생할 수 있는 실패 시나리오**이지 이미 재현한 모바일 앱 결함 목록이 아니다.

## 1. 원문에서 유지할 것

독립적인 모바일 코드베이스, Android 우선, TXT/EPUB/ZIP, 계정 없는 로컬 독서,
서재 먼저 진입하고 이어읽기 바를 탭하는 UX, 페이지 번호가 아닌 논리 locator,
전체 JSON overwrite 금지, 대용량 처리와 강제 종료 회귀를 핵심으로 삼은 방향은 유지한다.

원문 구조 0–47장을 개정 계획에도 유지했다.
이번에 바꾼 정책은 파일 맨 앞 변경표에 표시했으며,
미측정 목표와 공식 문서 확인 사실, 실제 구현 검증이 필요한 항목을 구분했다.

## 2. 반드시 보완할 설계

### R01 — P0: 원본 URI를 주 저장소로 쓰는 기본값

**원문 근거:** §12–13, L554–614. 가능한 경우 지속 접근 URI를 사용하고 내부 복사는 선택 사항이다.

**실패 조건:** 메신저의 임시 URI로 책 등록 → 앱/전달 앱 종료 또는 원본 삭제 → 이어읽기 실패.
클라우드 provider가 오프라인이거나 seek를 지원하지 않는 경우도 원본을 로컬 파일처럼 다룰 수 없다.
지속 접근 권한이 있다고 원본 수명이나 오프라인 바이트 가용성까지 보장되는 것은 아니다.[S07]

**최소 수정:** v1은 private durable storage의 관리 복사본을 사용한다.
원본 URI는 출처로만 보관한다. import 성공 전까지 새 책을 완성 상태로 표시하지 않는다.
공간 중복/복사 시간을 UX에 명시하고, 삭제는 관리 복사본과 앱 기록만 대상으로 한다.

**승인 테스트:** 임시 URI/원본 삭제 뒤에도 완료된 관리 복사본은 읽힘.
공간 부족/복사 중 kill에도 기존 서재 손상 없음.

### R02 — P0: TXT charOffset의 좌표계가 정의되지 않음

**원문 근거:** §11.1, L490–514. `byteOffset?`, `charOffset`, 문단 offset이 함께 제시되지만 단위/정규화/변환 관계가 미정이다.

**실패 조건:** 저장은 원본 기준, 검색은 CRLF 정규화 뒤 기준, 화면은 UTF-16 기준으로 처리하면
한국어·이모지·줄바꿈 수에 따라 같은 offset이 다른 문장을 가리킨다.
문단을 렌더 fragment로 나눈 뒤 fragment index를 원문 문단 index처럼 쓰면 PC판과 같은 종류의 위치 혼동이 생길 수 있다.

**최소 수정:** BOM 제거+CRLF/CR→LF 외에는 원문을 변형하지 않는다.
그 결과의 UTF-16 코드 단위 offset을 canonical로 고정한다.
인코딩/정규화 버전에 대한 revision을 저장하고 검색/북마크/페이지가 같은 좌표계를 사용한다.
인코딩을 바꾼 기록은 별도 revision으로 보존한다. 자동 감지가 모호하면 미리보기/수동 선택을 제공한다.

**승인 테스트:** chunk 크기를 바꾸어도 canonical 결과와 검색 offset 동일.
CP949 확장 글자, UTF-16 surrogate, emoji/CRLF 경계, 인코딩 재선택 테스트.

### R03 — P0: transaction+debounce만으로는 저장 순서가 안전하지 않음

**원문 근거:** §23, L996–1022. transaction, 의미 있는 변화, debounce/lifecycle save를 권장한다.

**실패 조건:** 같은 책을 다시 연 새 세션의 위치보다 이전 세션의 늦은 write가 나중에 도착할 수 있다.
계속 스크롤하면 단순 debounce는 저장을 계속 미룰 수 있다.
기존 progress 조회가 실패했는데 기본 화면 위치가 저장되면 기존 기록을 덮어쓴다.

**최소 수정:** 조회 성공+복원 완료 전 저장 금지.
이벤트마다 bookId/revision/sessionEpoch/sequence를 캡처하고 조건부 저장한다.
single writer, 1초 sampling과 정지 후 250ms 저장을 병행한다.
뒤로 이동도 최신 의도이므로 최대 offset 비교로 해결하지 않는다.

**승인 테스트:** stale 세션 거부, A/B 책 전환, 조회 실패 후 무변경, 60초 연속 스크롤 주기 commit,
삭제 중 save, disk-full 오류 표시.

### R04 — P0: DB와 파일의 원자성이 구분되지 않음

**원문 근거:** §24, L1026–1038. 실패 시 incomplete book record를 남기지 않도록 요구하지만 절차가 없다.

**실패 조건:** 파일 복사와 Book insert 사이에서 kill → 없는 파일을 가진 서재 항목 또는 고아 파일.
rename과 DB commit 사이의 중단은 DB transaction 하나로 해결되지 않는다.

**최소 수정:** ImportJob journal, staging, 검증, final 경로 이동, Book+COMMITTED transaction.
중단된 단계는 다음 시작에서 멱등 recovery. 삭제도 DELETING 상태로 재개 가능하게 한다.
원문 파일/기존 책을 잘못 정리하지 않도록 참조 확인 후 고아 판단.

**승인 테스트:** 각 단계에 fault injection, 재실행 2회 이상에서도 동일한 결과,
중복 import와 완료 직전 취소, 파일 핸들이 살아 있는 삭제.

### R05 — P0: EPUB 엔진·보안 정책이 뒤늦게 확정됨

**원문 근거:** §19, L846–874; §41.C, L1607–1620; EPUB가 Phase 4, L1331–1341.

**실패 조건:** UI/도메인을 KMP 공통 엔진 전제로 만든 뒤 Android Fragment SDK를 넣으려다 구조를 다시 바꿈.
“JS를 끈다”는 조치가 엔진 내부 locator/pagination 기능과 충돌하거나,
스크립트 태그만 지우고 외부 CSS/이미지/iframe/event handler 경로를 놓칠 수 있다.

**외부 확인:** Readium은 EPUB2/3와 Locator/검색/기존 Android Navigator를 제공하며,
신규 Compose 기반 Navigator는 alpha로 안내된다.[S04][S05][S06]
Readium 아키텍처에는 엔진 자체 JS/CSS 주입 가능성이 있다.[S13]
이 사실은 특정 릴리스의 publication script 차단이 이미 안전하다는 증거는 아니다.

**최소 수정:** M00 다음 M01에서 Readium adapter, 실제 책 열기, locator round-trip,
글꼴 변경 복원, 비신뢰 콘텐츠 차단을 작은 probe로 검증한다.
엔진 trusted code와 publication code를 구분하고 구현 경로를 ADR로 고정한다.
막힌 보안 기준을 완화해서 “EPUB 완료”라고 하지 않는다.

**승인 테스트:** script/event handler/SVG/iframe/외부 리소스/redirect/다른 책 접근을 차단하면서
정상 EPUB의 이미지/검색/위치 복원이 동작하는지 확인.

### R06 — P0: 압축·이미지의 구체적인 자원 방어가 없음

**원문 근거:** §20, L878–926; §25, L1057–1066; ZIP 테스트 L1471–1484.

**실패 조건:** 작은 ZIP이 거대한 데이터를 해제하거나 경로 순회로 다른 파일을 덮어씀.
현재 이미지 한 장만 읽어도 긴 웹툰의 full bitmap decode가 메모리 한도를 초과할 수 있다.

**최소 수정:** 엔트리 경로/중복/크기/개수/실제 해제량을 검증한다.
원본 크기, 비압축 총량, 파싱 리소스, 이미지 decode/cache 한도를 따로 둔다.
정렬은 locale-independent, locator는 entryKey+정규화 좌표로 고정한다.
이미지 크기 먼저 조회, 다운샘플, 제한된 decode, region 미지원 fallback을 적용한다.[S08][S09]

**승인 테스트:** ZipSlip/중복 엔트리/압축 폭탄/손상 이미지/긴 PNG·WebP/빠른 넘김,
회전·RTL·fit 변경 뒤 같은 이미지 위치 복원.

### R07 — P1: UI까지 무조건 공유하면 Android 목표가 뒤로 밀릴 수 있음

**원문 근거:** §7–8, L279–366; §41.A, L1587–1594.

**최소 수정:** Android UI는 Jetpack Compose, KMP는 도메인/모델/DB/공유 가능한 텍스트 로직으로 한정한다.
플랫폼 text measurement/SAF/Readium host는 adapter에 둔다.
이는 KMP가 불안정해서 버린다는 뜻이 아니다. KMP Android/iOS 안정성은 확인된다.[S01]
iOS UI 공유/실기기 증명은 iOS 작업에서 판단한다.

**검증:** shared common 코드에 Android Context/Fragment/Compose Android 측정 API가 새어들지 않음.
M00부터 과도한 모듈이나 빈 iosApp을 만들지 않음.

### R08 — P1: P0/P1·화면·개발 단계의 불일치

**원문 근거:** P1 목록 L219–234, 서재 화면 L618–643, 설정 L930–992,
볼륨키 L739 및 L1325, 전체 DoD L1646–1655.

**문제:** 최근 읽음 캐러셀은 P1인데 기본 화면에 들어가고, 볼륨키는 P1이면서 구현 단계 필수처럼 보인다.
웹툰/연속 보기의 차이가 없다. “각 포맷 검색”은 만화 OCR까지 확장될 수 있다.
책별 설정은 P1이지만 Comic 방향/모드를 기억하지 않으면 재진입 UX가 흔들린다.

**최소 수정:** 기능표를 동결한다.
최근 캐러셀/볼륨키/통계/모든 책별 타이포는 P1.
책별 모드/만화 방향, byte-identical 중복 방지, 수동 인코딩만 P0로 추가.
웹툰은 연속 보기 프리셋. 본문 검색은 TXT/EPUB. 최종 v1 포맷 3개는 유지한다.

### R09 — P1(출시 전 필수): 백업/삭제/개인정보 계약 누락

**원문 근거:** 개인정보 L1083–1094; 백업 L1098–1128.

**문제:** 앱 백업 기능을 미뤘다고 시스템 자동 백업도 꺼지는 것은 아니다.
Android는 기본 설정에 따라 DB/내부 파일을 자동 백업할 수 있다.[S10]
내부 복사본을 채택하면 앱 삭제/데이터 지우기의 손실도 분명히 알려야 한다.

**최소 수정:** v1 백업 UI는 P1, 자동 클라우드 백업 제외 정책/OS별 extraction rule/데이터 손실 안내는 P0.
OEM D2D와 cloud backup의 차이를 확인한다. 스토어·최종 manifest 감사에 포함한다.

### R10 — P1: 성능과 복원 정확도의 합격선이 정성적임

**원문 근거:** §25, L1042–1066; force-kill L1422–1440; Phase 1 DoD L1287–1294.

**최소 수정:** first-render와 import 시간을 분리하고,
지원 한도/fixture/기기/빌드/반복 횟수/p95/PSS/commit 지연을 정의한다.
같은 TXT revision은 저장 anchor가 같은 문장/페이지에 있어야 한다.
EPUB exact locator와 chapter fallback을 구분한다.
Comic은 이미지 identity+normalized anchor로 비교한다.

제안 수치는 보장/실측이 아니라 초기 테스트 목표로 표기했다.
force-stop은 host가 제어한다. Activity 재생성 테스트와 구분한다.

### R11 — P1: 앱 실행·읽기 판정·탐색 취소가 모호함

**원문 근거:** §2, L58–100; §17, L722–746; Book의 lastOpenedAt L376–392.

**최소 수정:** cold launch=서재, 살아 있는 Reader 복귀=유지, 외부 명시적 열기=예외를 정한다.
lastOpenedAt와 lastReadAt/readOrder를 분리한다.
진행률 drag는 preview→release commit. 검색/목차 점프에는 일회성 돌아가기를 제공한다.
overlay/회전/글꼴 reflow의 중간 위치를 영구 저장하지 않는다.
큰 화면의 OS 정책 때문에 회전 잠금이 항상 작동하지 않을 수 있음을 반영한다.[S15]

### R12 — P1: Codex 작업 계약과 실제 빌드 검증의 경계가 부족함

**원문 근거:** §43–46, L1673–1862.

**문제:** “Room KMP 또는 대체안”, “기본 CI”, “smoke test”만으로는
툴체인/작업 범위/환경 부재/완료 판정이 일관되지 않는다.

**최소 수정:** M00–M11, 작업별 입력/출력/금지 범위/선행 조건/승인 테스트,
AGENTS 템플릿, 시작/계속/회귀 감사 프롬프트를 제공한다.
`PASS/FAIL/NOT_RUN/BLOCKED_ENV`를 구분하고 CI에서 테스트 우회로 녹색을 만들지 못하게 한다.
최종 APK native library의 16KB/ABI 검사도 포함한다.[S14]
고정 버전/실행 명령을 저장소에 남기게 한다. OpenAI 공식 자료도 AGENTS와 단계별 맥락 기록을 활용하는 방식을 제시한다.[S12]

## 3. 과도하게 추가하지 않을 것

통계, 계정, AI, 동기화, iOS UI, 서재 전체 본문 검색, 완독 관리,
고급 annotation, 자동 문장 매칭 재배치까지 첫 구현에 넣지 않는다.
기능 확장보다 파일 import→TXT 읽기→진행도→이어읽기 경로를 먼저 만든다.
다만 최종 v1에서 EPUB/Comic을 조용히 빼는 방식으로 일정 문제를 해결하지 않는다.

## 4. 이번 전달물의 상태

- 원문은 수정하지 않고 reference 폴더에 보관했다.
- 개정 계획은 원문 0–47장과 부록으로 자기완결형 구현 계약을 작성했다.
- 작업표/프롬프트/AGENTS는 즉시 구현 작업을 배정하기 위한 문서다.
- 실제 repository 코드/브랜치/PR은 변경하지 않았다.
- 실제 Android build, 실기기, Readium 보안 probe, 성능 테스트를 이번 검토에서 실행하지 않았다.
- 그러므로 판정은 **계획의 조건부 착수 승인**이며 앱 출시 승인이나 완전 검증 선언이 아니다.

## 5. 근거

원문 인용 위치는 원본 `docs/reference/BookReader_Mobile_v1_Plan.md`의 절 번호와 제공된 줄 번호를 사용했다.
외부 근거 [S01]–[S16]은 개정 계획 부록 D에 정리되어 있다.
