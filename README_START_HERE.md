# BookReader Mobile — Codex 인계 패키지

**계획서 개정 + 구현 작업 계약 패키지. Android 앱/소스코드가 이미 구현된 패키지가 아니다.**

기준일 2026-10-01. 앱 목표는 Android v1.0, 계획서 개정 번호는 v1.1이다.
검토 판정은 **GO WITH FIXES: 개정안과 단계별 검증을 기준으로 구현 착수 가능**이다.
실제 Android 빌드/Readium 보안 probe/성능 테스트는 아직 실행하지 않았다.

## 포함 파일

| 파일 | 용도 |
|---|---|
| `docs/BookReader_Mobile_Review.md` | 원문 근거, 12개 검토 항목, 재현 가능한 위험과 최소 수정 |
| `docs/BookReader_Mobile_v1_1_Implementation_Plan.md` | 원문 0–47장 구조를 유지한 자기완결형 개정 기준 |
| `docs/TASKS.md` | M00–M11 범위/선행 조건/승인 테스트/산출물 |
| `docs/BookReader_Mobile_Codex_Prompts.md` | 첫 실행, 계속하기, 작업 지정, 읽기 전용 감사, EPUB probe |
| `START_PROMPT.txt` | 첫 실행 프롬프트만 바로 복사 |
| `AGENTS.template.md` | 기존 AGENTS에 병합할 개발 규칙 |
| `docs/reference/BookReader_Mobile_v1_Plan.md` | 변경하지 않은 원문 |
| `MANIFEST.json` | 패키지 파일의 SHA-256과 검증 상태 |

## 바로 시작하기

기본 권장 위치는 새 모바일 저장소다. 패키지의 docs와 AGENTS.template.md를 해당 저장소 또는 Codex가 실제 읽을 수 있는 작업 공간에 제공한다.
`START_PROMPT.txt`를 첫 요청으로 사용한다. Codex는 M00 하나를 실제 구현하고, 실행한 검사와 미실행 검사를 구분하여 보고한다.
다음 요청은 프롬프트 문서의 “계속하기”를 사용한다.

현재 PC BookReader 저장소를 사용할 경우 모바일 코드는 `mobile/` 아래에 격리한다.
PC 코드/설정/기존 workflow를 덮어쓰지 않는다. 모바일 전용 새 workflow는 범위를 모바일에 제한한다.
`AGENTS.template.md`를 기존 AGENTS.md 위에 무조건 덮어쓰지 않는다.
개정 계획이 채택되면 원문은 reference로만 둔다.

## 첫 두 작업이 중요한 이유

M00은 실제 Android 실행/빌드/DB 기반과 CI다.
M01은 Readium의 host/Locator/보안 및 CP949 decoder가 선택 환경에서 동작하는지 확인하는 작은 기술 검증이다.
이 검증을 EPUB 개발 마지막에 미루지 않는다.
M03–M07이 TXT 내부 알파이고, M08/M09까지 합쳐야 원문의 세 포맷 v1에 도달한다.

## 검증 상태를 읽는 법

DONE은 필요한 구현과 승인 검증이 끝난 작업에만 사용한다.
PASS/FAIL은 실제 실행 결과이고, 환경이 없으면 NOT_RUN/BLOCKED_ENV다.
개정 계획에 적은 시간/메모리/크기 한도는 초기 설계 기준이며 측정 결과가 아니다.
기술 문서 확인과 전체 의존성 조합의 실제 빌드는 구분한다.

## 원문 및 외부 근거

원문은 reference 폴더에 그대로 보존했다.
공식 근거는 개정 계획 부록 D의 [S01]–[S16]에 있다.
개정안의 내부 복사 정책, KMP 공유 범위 축소, 저장 순번/주기, 수치 기준은 이번 리뷰의 제안이다.
패키지를 제공하면서 GitHub 저장소/브랜치/PR 또는 앱 코드를 변경하지 않았다.
