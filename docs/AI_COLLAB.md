# BookReader Mobile 협업 절차

[AGENTS.md](../AGENTS.md)는 저장소 공통 규칙의 단일 원본이다. 구현과 검증에는
[작업표](TASKS.md)와 [검증 계약](TESTING.md)을 함께 적용한다. GitHub Issue는 요구사항과
범위를, PR은 변경과 리뷰를, Actions는 실제 CI 실행 결과를 기록한다.

## 역할과 책임

R은 실행, A는 최종 책임, C는 협의, I는 결과 공유를 뜻한다.

| 작업 | 사용자 (PO) | Gemini (Tech Lead) | Codex (구현) |
| --- | --- | --- | --- |
| 제품 요구사항과 우선순위 결정 | A/R | C | I |
| 기술 설계, 작업 분할, 구현 Issue 작성 | C | A/R | C |
| 구현, 회귀 검증, PR 작성과 리뷰 수정 | I | C | A/R |
| PR 독립 리뷰 및 수정 확인 | I | A/R | C |
| 최종 수락과 `main` 수동 병합, 배포 승인 | A/R | C | I |

## 브랜치 소유권

- Codex는 `codex/<작업명>`, Gemini는 별도 UI/UX 구현 등을 맡은 경우 `gemini/<작업명>`을 편집한다.
  작업마다 Issue에 기록한 기준 브랜치와 전체 SHA에서 새 브랜치와 별도 워크트리를 만든다.
- 소유자 한 명이 작업 브랜치에 커밋하고 push한다. Gemini는 Codex PR의 리뷰 의견을 PR에 남긴다.
  다른 PR에 의존하는 작업은 선행 PR과 선행 조건을 Issue에 적고, 그 작업이 반영된 SHA에서 시작한다.
- `main`은 사용자가 PR에서 수동 병합한다. `main` 직접 push, force push와 자동 병합은 금지한다.

## Issue에서 병합까지

1. **Gemini → 사용자.** [구현 Issue 양식](../.github/ISSUE_TEMPLATE/implementation.yml)에 목표,
   기준 브랜치와 전체 SHA, 허용 범위, 금지 사항, 수락 기준, 필수 검증과 선행 조건을 적는다.
   사용자가 범위와 우선순위를 확정한다.
2. **사용자 → Codex.** Codex Cloud에서 `LaceyLuv/BookreaderM`과 Issue에 명시된 기준 브랜치를
   선택한다. 작업 입력에 Issue URL, 기준 SHA, 작업 브랜치명과 완료 조건을 넣고 실행한다.
   GitHub Issue 작성과 Codex Cloud 작업 실행은 별도 단계로 진행한다.
3. **Codex → Gemini.** 현재 워크트리와 기존 인계를 확인하고 진행 중인 작업을 먼저 정리한다.
   Issue의 SHA와 범위를 대조한 뒤 구현하고, 해당 검증과 회귀를 실제 실행한다. 작업 브랜치를 push하고
   [PR 양식](../.github/PULL_REQUEST_TEMPLATE.md)으로 `main` 대상 PR을 열어 Issue를 연결한다.
   `Closes #번호`는 해당 PR이 수락 기준을 모두 해결할 때 사용한다.
4. **Gemini → Codex.** 사용자가 선택한 Antigravity 세션에 Issue와 PR 링크를 전달한다.
   Gemini는 PR의 기준 SHA와 리뷰 대상 HEAD를 확인하고 diff, 수락 기준, 검증 증거를 독립 검토한다.
   변경이 필요한 경우 파일·줄, 영향과 필요한 검증을 PR 리뷰에 남긴다.
5. **Codex → Gemini.** 지적 사항을 같은 작업 브랜치에서 수정하고, 수정 커밋과 재실행 결과를 PR에
   연결한다. Gemini는 갱신된 HEAD에서 수정과 영향 범위를 확인하고 재검토 결과를 기록한다.
6. **Gemini → 사용자.** 사용자가 최신 HEAD의 Gemini 리뷰, 해결된 지적, 수락 기준, 필수 검증과
   미해결 blocker를 확인하고 승인한 뒤 수동 병합한다. 적용되는 GitHub 필수 검사는 PR의 Checks에서
   확인한다. 배포는 사용자 승인에 따라 별도로 진행한다.

## 충돌 해결

- 브랜치 소유자는 원격 기준 브랜치를 fetch하고 PR의 충돌 파일, 기준 SHA와 변경 내용을 확인한다.
  범위 안에서 양쪽 변경을 보존해 해결하고, 기술 판단이 필요한 충돌은 Gemini에게 검토를 요청한다.
- 범위나 우선순위 변경이 필요하면 Issue에 충돌 원인, 관련 PR과 선택지를 남겨 사용자 결정까지
  해당 변경을 보류한다. 기준 SHA를 바꾸기로 합의한 경우 Issue와 PR에 이전·새 SHA와 이유를 남긴다.
- 해결 후 변경 파일과 수락 기준을 다시 대조하고 검증을 재실행한다. 해결 커밋과 증거를 PR에 올려
  Gemini가 새 HEAD를 확인할 수 있게 한다.

## 작업 인계와 오류 추적

- 중단하거나 넘길 때 Issue/PR 링크, 작업 브랜치와 HEAD, 미커밋 변경 여부, 변경 파일, 정확한 명령과
  종료 코드, 결과별 증거, blocker, 다음 담당자와 재개 조건을 남긴다. 기존
  [작업표](TASKS.md)와 [작업 기록](WORKLOG.md)에 근거를 남기고, 세션은
  [세션 인계](SESSION_HANDOFF.md)에서 이어간다.
- 검증 실패는 해당 PR에 재현 SHA, 환경, 명령과 종료 코드, 기대·실제 결과, 로그 링크를 기록한다.
  별도 수정이 필요한 결함은 Issue를 열어 원 PR과 연결하고 담당자·선행 조건을 적는다. 재실행 결과와
  수정 커밋을 추가해 최초 실패의 증거를 보존한다. 증거를 공유할 때는 AGENTS.md의 개인정보 규칙을 적용한다.
- 각 검증 결과는 `PASS`, `FAIL`, `NOT_RUN`, `BLOCKED_ENV`로 기록한다. 권한, 기기 또는 도구가
  필요한 경우 그 조건과 확인 담당자를 적는다. 실행하지 않은 검증에는 종료 코드 대신 `—`를 적는다.

## 첫 실행과 권한 확인

- 사용자는 Google 계정으로 Antigravity에 로그인하고 Remote Agents에서 사용할 Gemini 모델을 선택한다.
  GitHub 계정을 연결해 `LaceyLuv/BookreaderM`의 Issue 작성, PR 및 Checks 읽기, 리뷰 제출 권한을 확인한다.
  Gemini에 별도 구현을 맡길 경우 해당 `gemini/*` 브랜치 push와 PR 작성 권한도 확인한다.
- 저장소 설정의 `Branches` 및 `Rulesets`에서 `main`의 보호와 필수 CI를 확인한다.
  문서와 템플릿은 수동 인계에 사용하고, GitHub 설정의 실제 적용 상태는 저장소에서 확인한다.
- 첫 시험은 문서 한 곳만 수정하는 Issue로 시작한다. Issue의 기준 SHA가 PR과 일치하는지, 템플릿에
  정확한 검증 명령·결과가 있는지, 최신 HEAD에 Gemini 리뷰와 수정 확인이 남는지 대조한다.
  기록을 확인한 사용자가 병합 여부를 결정한다.
