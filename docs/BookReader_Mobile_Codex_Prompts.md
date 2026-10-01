# BookReader Mobile — Codex 실행 프롬프트

> 이 프롬프트는 실제 구현을 Codex에 배정하기 위한 문서다. 이번 문서 검토에서 앱 코드를 구현했다는 뜻이 아니다.  
> 권장 시작: 패키지의 docs와 AGENTS.template.md를 Codex가 실제 읽을 수 있는 저장소/작업 공간에 제공하고 “1. 첫 실행”을 붙여넣는다.  
> AGENTS.md는 Codex가 인식하는 지침 파일이며, 다른 문서 이름은 이 프로젝트에서 정한 작업 관례다.[S12: 개정 계획 부록 D]

## 1. 첫 실행 — M00만 구현

```text
당신은 이 저장소의 BookReader Mobile 구현 담당자다. 계획만 다시 쓰지 말고 이번 작업의 실제 코드와 테스트를 구현하라.

먼저 현재 저장소의 루트·상위 AGENTS.md·브랜치·git status·기존 프로젝트 구조를 확인하라. 사용자 미커밋 변경과 기존 PC BookReader 코드를 보존하라.

입력 문서:
1. docs/BookReader_Mobile_v1_1_Implementation_Plan.md
2. docs/TASKS.md
3. AGENTS.template.md 또는 이미 존재하는 AGENTS.md

문서가 위 경로에 없으면 실제 제공된 첨부/작업 디렉터리에서 찾아 해당 docs 경로에 보존하라. 실제 내용을 읽지 않은 문서를 읽었다고 가정하지 마라. 원문 BookReader_Mobile_v1_Plan.md는 이력용이며 개정 계획과 충돌하면 개정 계획의 명시적 변경을 따른다.

기본 위치는 독립 모바일 저장소다. 기존 PC 저장소라면 mobile/ 아래에 격리하고, 기존 PC 코드·설정·CI 파일을 변경하지 마라. 모바일 전용 새 CI workflow가 필요하면 범위를 모바일 경로로 제한하고 그 추가를 보고하라. 위 docs 경로는 선택한 모바일 루트를 기준으로 해석하라.

이번 작업은 docs/TASKS.md의 M00 — bootstrap-mobile-foundation만 수행한다.
- Android Jetpack Compose 앱 + KMP core + Room KMP 기반을 실제로 만든다.
- 안정적인 JDK/Gradle/AGP/Kotlin/Compose/KSP/Room/SQLite/SDK 조합을 공식 문서와 실제 dependency resolve/build로 확인하고 정확한 버전을 고정한다.
- 실제 실행 가능한 서재/검색/설정 3탭, Loading/Empty/Error 구분, 최소 테마를 만든다.
- Book/ReadingProgress/locator envelope 및 repository 경계를 구현한다.
- 실제 Room DB 생성·삽입·조회 smoke, schema export, locator 직렬화 테스트를 만든다.
- Gradle wrapper, scripts/doctor.sh, scripts/ci-check.sh, 기본 CI, DEPENDENCIES.md/TESTING.md/WORKLOG.md를 만든다.
- AGENTS.template.md는 기존 지침을 대체하지 말고 관련 규칙을 적절한 범위의 AGENTS.md에 병합한다.

제품의 고정 조건:
- Android 우선, PC 코드를 포팅하지 않는다.
- 새 프로세스의 일반 앱 실행은 서재다. 마지막 책은 하단 이어읽기 바로 진입한다.
- 원본 URI가 아니라 앱 내부 관리 복사본을 사용한다.
- 정식 위치는 version/revision이 있는 포맷별 locator다. page number를 저장 기준으로 쓰지 않는다.
- 전체 서재 JSON overwrite, 조회 실패 후 0% 저장, destructive migration을 금지한다.
- EPUB는 검증된 Readium Android 어댑터 경로를 기본으로 하되 M01에서 호환성·locator·보안을 실제 검증한다.
- iOS UI 공유/계정/동기화/AI/통계로 이번 범위를 넓히지 않는다.

M00에서는 TXT 본문 렌더러, EPUB 제품 기능, Comic Reader를 구현하지 마라.
완성처럼 보이는 빈 구현, TODO 반환값, 가짜 DB/테스트만으로 완료 처리하지 마라.
Gradle task는 현재 plugin이 실제 제공하는 목록을 확인하여 선택하고, ci-check에 Android assemble/lint/unit 및 shared tests를 연결하라.
테스트 삭제·skip·ignoreFailures·실패 무시로 녹색 CI를 만들지 마라.

환경에 SDK/JDK/네트워크/에뮬레이터가 없으면 가능한 작업을 수행하고 필요한 실행 검증을 BLOCKED_ENV/NOT_RUN으로 분리하라. 성공 결과나 실행 로그를 꾸며내지 마라. 기능·보안 요구를 낮추는 우회 대신 재현 가능한 차단 사유와 해결 조건을 남겨라.
불필요한 확인 질문으로 멈추지 말고 문서의 기본값을 사용하되, 되돌리기 어려운 범위·보안·데이터 정책 변경은 임의 수행하지 마라.
사용자 요청 없이 원격 push/merge/스토어 배포를 하지 마라.

마지막에는 다음을 보고하라.
1. 실제 구현한 사용자 경로와 변경 파일.
2. 고정한 의존성/구조 및 원문 대비 선택한 정책.
3. 각 실행 명령·exit code·PASS/FAIL/NOT_RUN/BLOCKED_ENV·로그/산출물 경로.
4. 해결하지 못한 위험과 실제로 하지 않은 검증.
5. TASKS.md의 M00 상태와 다음 M01의 정확한 착수 조건.
```

## 2. 계속하기 — 가장 이른 착수 가능한 작업 하나

작업을 마칠 때마다 이 프롬프트를 다시 사용할 수 있다.
TASKS의 체크 상태만 믿지 않고 실제 구현과 실행 증거를 확인하게 한다.

```text
이 저장소의 BookReader Mobile 구현을 이어서 진행하라. 이전 대화의 기억이 아니라 실제 저장소의 문서·코드·테스트를 기준으로 한다.

상위/현재 AGENTS.md, docs/BookReader_Mobile_v1_1_Implementation_Plan.md, docs/TASKS.md, docs/WORKLOG.md와 최근 변경을 읽어라. git status로 사용자 변경을 보존하라.
TASKS 상태가 코드/검증 증거와 다르면 먼저 바로잡고, 선행 조건을 충족하는 가장 이른 미완료 작업 하나를 선택하라. 일부 작업이 환경/SDK 문제로 막혔으면 독립 작업으로 이동 가능한지 TASKS의 의존성 규칙대로 판단하고 blocker를 유지하라.

작업 ID, 이번 범위, 제외 범위, 필요한 테스트를 간단히 알리고 실제 구현·검증을 수행하라.
안정적인 기존 기능을 지우고 다시 만드는 방식이나 전체 v1을 한 번에 덧붙이는 방식은 피하라.
아키텍처 결정을 바꿔야 하면 ADR에 이유/대안/검증/영향을 기록하되 P0·데이터 보존·보안을 조용히 완화하지 마라.
page number locator, 전체 JSON overwrite, progress 조회 실패 후 기본값 저장, 본문 전체 메모리 로드, 무제한 이미지 decode를 금지한다.
테스트가 가능하면 변경된 기능과 기존 회귀를 실제로 실행한다. 환경 부재는 NOT_RUN/BLOCKED_ENV로 남긴다.
완료 후 TASKS/WORKLOG/TESTING을 갱신하고, 실제 명령 결과·남은 위험·다음 작업을 보고하라.
기본 권한은 현재 작업의 로컬 변경이다. 별도 요청 없이 push/merge/배포하지 마라.
```

## 3. 작업 ID를 직접 지정

예: `<Mxx>`를 `M04`로 바꾸면 저장 경쟁/강제 종료 작업만 배정한다.

```text
docs/TASKS.md의 <Mxx>만 구현하라. <Mxx>를 이번에 지정한 실제 작업 ID로 바꿔 적용하라.
먼저 AGENTS/개정 계획/TASKS/WORKLOG와 현재 코드 상태를 읽고 선행 조건을 검증하라.
범위·제외 항목·승인 테스트·산출물은 해당 작업 계약을 따른다.
계획 재작성만 하지 말고 코드와 테스트를 구현하고 실제 검증을 수행하라.
선행 조건이 충족되지 않으면 우회하지 말고 필요한 최소 선행 작업만 식별하여 보고하라.
완료 보고에는 파일·사용자 경로·실행 명령과 결과·남은 위험·다음 조건을 포함하라.
```

## 4. 읽기 전용 감사

구현 세션 다음에 별도 검토 세션에서 사용한다.
현재 단계가 M03인데 아직 하지 않은 M09 Comic 자체를 M03 코드 결함이라고 잘못 판정하지 않도록 한다.

```text
이번 작업의 변경을 출시 차단점 중심으로 읽기 전용 감사하라. 코드를 수정하지 마라.

AGENTS.md, docs/BookReader_Mobile_v1_1_Implementation_Plan.md, docs/TASKS.md, WORKLOG와 현재 변경 diff를 실제로 읽어라.
현재 단계의 계약을 기준으로, 미구현 후속 단계와 이번 단계의 결함을 구분하라.
파일/행/실패 조건/사용자 영향/최소 수정/필요한 테스트를 제시하라.
특히 다음을 검증하라.
- 관리 복사본/import journal/삭제의 원자성 및 재시작 복구.
- locator 단위/해석 revision/검색·북마크 좌표계.
- progress read 성공 게이트와 sessionEpoch/sequence 순서.
- 단순 debounce starvation, 오래된 reflow 콜백, A/B 책 혼동.
- EPUB publication script/외부 요청/SDK 내부 코드의 신뢰 경계.
- ZIP 경로/해제량, 이미지 decode 메모리, entryKey/normalized anchor.
- 필수 테스트의 실제 실행 여부와 CI 실패 우회.
- 원문 P0와 개정안의 범위 누락/허위 완료.

테스트가 가능하면 읽기 전용 확인에 필요한 범위에서 실행하고, 실행하지 못한 것은 명시하라.
재현된 결함, 코드로 확인한 결함, 가설/검증 필요를 분리하라.
판정은 GO / GO WITH FIXES / NO-GO로 하되, 해당 단계 판정인지 최종 출시 판정인지 명확히 하라.
치명적 결함이 없으면 없다고 말하고 억지로 문제 개수를 채우지 마라.
```

## 5. 가장 위험한 EPUB 검증의 상세 프롬프트

```text
M01의 EPUB 기술 검증을 수행하라. 아직 EPUB 제품 전체를 만들지 마라.

Readium 고정 안정 버전의 공식 문서와 실제 소스를 확인하여 Android Navigator host를 격리된 probe에 통합하라.
EPUB2/3/이미지/CSS/TOC/검색/공개 Locator round-trip/크기·테마 변경/재실행 복원을 검증하라.
출판 콘텐츠의 script/event handler/iframe/SVG 외부 참조/CSS import/외부 URL/redirect/파일·다른 책 리소스 접근을 차단하면서 엔진 내부 기능이 유지되는지 확인하라.
모든 JS를 꺼도 괜찮다거나 SDK 이름만으로 안전하다고 가정하지 마라.
정규식 script 제거로 보안 완료 처리하지 마라.
사용한 API/설정/리소스 변환/allowlist/검증 fixture/관찰 결과를 ADR-0005에 기록하라.
현재 SDK에서 안전하게 구현할 수 없는 요구가 있으면 재현 조건과 최소 대안을 남기고 M08 blocker로 유지하라.
라이브러리의 보안 기본값이나 성공 결과를 추정하지 마라. 환경 부재는 분리 보고하라.
```

## 6. Codex 보고 형식

```text
작업 ID / 범위:
작업 전 상태:
실제 구현한 사용자 경로:
변경 파일:
아키텍처/정책 결정 및 ADR:
실행한 명령:
- 명령 / exit code / 결과 / 로그 경로
생성한 APK·보고서·스크린샷:
실행하지 못한 검증과 이유:
미해결 blocker:
TASKS 상태:
다음 작업과 선행 조건:
```

## 7. 사용 시 주의

이 패키지는 저장소를 생성하거나 GitHub에 코드를 올리지 않는다.
과거 대화/개인 기억이 Codex에 자동 공유된다고 가정하지 않는다.
현재 프로젝트에 이미 AGENTS.md/코드/브랜치 규칙이 있으면 보존한다.
문서가 저장소 밖에만 있을 때는 실제 첨부 경로를 찾아 읽은 뒤 복사해야 한다.
서명키/스토어 계정/사용자 책 파일을 프롬프트나 공개 저장소에 넣지 않는다.
