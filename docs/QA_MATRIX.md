# Android 검증 매트릭스 — 2026-10-17

전체 v1 출시 판정은 아직 BLOCKED다. 아래 PASS는 명시한 소스·계층·fixture에 한정한다.
최신 통합 소스/기기 결과는 [인계](CONTEXT_HANDOFF.md)와 [출시 감사](RELEASE_CANDIDATE_REPORT.md)에 연결한다.

| 범위 | 실행 근거 | 상태 / 남은 검증 |
|---|---|---|
| TXT 가져오기·정규 UTF-16·페이지/스크롤·검색·북마크·인코딩 | main 22526d3 API36 ordinary 52, [원본 XML](evidence/m01-epub-validation/remote-22526d3-device/summary.json) | PASS; 물리 성능 별도 |
| TXT 실제 회전/STOP·A/B 경쟁·재열기·늦은 DB acknowledgement | 14c8c42 API36 49, 회귀 unit 66 | PASS; 저장장치 전원 차단 보장 아님 |
| EPUB 제품 가져오기·TOC·검색·북마크·원본 보존 | 22526d3 제품 3개 포함 | PASS 범위; 강화한 글자 크기 변경 후 문단 표시 검증은 964f8cc FAIL |
| EPUB 정확 표시 위치 | a5a7a31 SDK 텍스트/첫 표시 요소 locator 보완 | 원격 후속 검증 중; progression 근접만으로 exact PASS라 하지 않음 |
| Comic 제품 모드·RTL·pinch/pan·손상 건너뛰기·재열기 | 964f8cc Comic 제품 test PASS, 같은 전체 suite는 EPUB 1 FAIL | 개별 PASS; 전체 통합 gate 미완료 |
| Comic 긴 PNG/WebP·100장 decode | 7eaa0f5 API36 2개; [PSS/bitmap 원본](evidence/m09-comic-validation/native-7eaa0f5/summary.json) | emulator PASS; 실제 기기 PSS/GPU/빠른 UI 이동 별도 |
| 외부 host 강제 종료 | 22526d3 TXT 준비/검증 2단계 및 PID 증거 | TXT PASS; EPUB/Comic 각각 2단계 신규 실행 중 |
| Android 최소/중간/현재 | API26 / API34 / API36 CI 매트릭스 c1ab039 | 실행 중; 모든 OS/OEM/ABI 검증으로 확대 해석하지 않음 |
| 접근성 | TXT 1.0/1.3/2.0 및 density 회귀, 실제 버튼/semantics | API36 기존 PASS; 361b3f9 목록 측정 FAIL 보존, 후속 수정 검증 중; 실기기 TalkBack NOT_RUN |
| 패키지 | 실제 APK manifest, signature, zipalign, ELF | [정적 감사](M10_PACKAGE_AUDIT.md); 실제 16KB OS 실행 NOT_RUN |

## SAFE 계약

| ID | 구현/자동 검증 | 미실행 범위 |
|---|---|---|
| SAFE-01 | 조회 Error와 Missing 분리; 실제 TXT progress/locator 오류; raw bookmark/설정 보존; archive owner unit | 모든 archive 제품 UI 오류 주입 조합 |
| SAFE-02/03/04 | book/revision/epoch/sequence 및 A/B·역방향 경쟁 실제 DB/native | 저장장치 물리 장애 |
| SAFE-05 | 실제 파일 journal checkpoint·취소·mutated copy·원본 URI 없이 recovery 2회 | 포맷별 OS가 import 단계마다 죽이는 전 조합 |
| SAFE-06 | 삭제 중 decoding/native, 늦은 저장/override 재생성 금지 DB | 모든 OEM 프로세스 정책 |
| SAFE-07 | 실제 SQLite FULL, host errno 28/available bytes 0, Android 쓰기 오류 | 물리 fsync/전원 차단 |
| SAFE-08 | TXT cache 손실 후 원본 재생성·같은 canonical 위치 native | Comic 파생 manifest 전체 삭제 후 host 재시작 추가 조합 |
| SAFE-09 | TXT 60초 실제 연속 스크롤 report, archive 주기 저장 unit | EPUB/Comic 각각 60초 UI 장기 계측 |
| SAFE-10 | 위 포맷별 host evidence 참조 | 미커밋 메모리 위치 보존은 계약 밖 |
| SAFE-11 | 실제 schema 1/2/3/4 → 5 migration, destructive fallback 없음 | 8a6a778 SQL 실행/스키마 검증 실패 후 2회 재열기·전체 raw schema/row 불변 JVM PASS; 물리 장애 별도 |
| SAFE-12 | TXT 임시 0/reflow 비저장, archive owner unit | 강화된 EPUB 실제 문단 후속 결과 확인 |

## 재현

전용 fixture 기기에서 `source /workspace/toolchains/mobile-env.sh`, `./scripts/ci-check.sh`,
`./scripts/device-check.sh`를 실행한다. `ANDROID_SERIAL`은 검사 대상 기기만 지정한다.
기기 검사는 APK 설치와 자체 fixture 데이터를 사용하므로 개인 독서 데이터가 있는 기기를 피한다.
각 실패의 XML/logcat과 이전 성공 결과를 모두 보존한다. 단독 재실행 성공이 전체 suite 실패를 지우지 않는다.

기기 없는 로컬 환경은 `adb devices -l`과 device-check의 exit 78/BLOCKED_ENV를 기록한다.
호스트 검사는 `python3 scripts/reader-process-check.py <evidence-dir> txt|epub|comic`이며,
전체 device-check가 세 포맷 모두 실행하고 필수 case identity와 PID 변경을 검증한다.

## 세션 종료 시 최신 발견

사용자가 2026-10-17 현재 작업까지만 정리하고 다음 세션으로 넘기도록 지시했다. 새 구현은 중단한다.
`a5a7a31` device run 39016613293은 ordinary 53개 중 52 PASS/1 FAIL이며 host 6단계는 NOT_RUN이다.
실패는 EPUB 제품 test의 `assertRenderedNear`: expected progression 0.9264910536779324,
actual 0.9565038378966562, tolerance 0.03이다. 정확 문단 표시 assertion보다 먼저 이 비교가 실패했다.
precise text locator의 복원과 progression 값 차이의 관계를 추가로 확인해야 하며 허용 오차만 넓혀 PASS를 만들지 않는다.
`9dfb884`의 최신 local full CI는 66/74/16 및 schema/lint/build PASS다.
원격 build 39017137521 및 API26/34/36 device 39017137527은 결과 확인이 필요하다.
[전달서](SESSION_HANDOFF.md)를 먼저 읽고 재개한다.
