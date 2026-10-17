# M05 TXT 페이지와 읽기 설정 — 2026-10-17

검증 source는 `1349ce8ee502686bd217373257e96432a7ff0204`다. 구현 및 아래 핵심 검증은 PASS이며
추가 설정 조합 검증이 남아 전체 M05는 IN_PROGRESS다. [evidence summary](evidence/m05-validation/summary.json)를 따른다.

## 구현

- Compose 1.9.3의 실제 TextMeasurer와 표시 Text에 같은 TextStyle/폭/밀도/시스템 배율을 적용한다.
  canonical UTF-16의 연속 `[start,end)`를 사용하며 페이지 번호를 저장하지 않는다.
- 한 번에 16Ki–64Ki UTF-16 범위만 읽고 현재 본문과 최대 128개 지역 경계만 유지한다.
  긴 문단에 줄바꿈을 삽입하지 않는다. 정확한 전체 페이지 수 대신 실제 canonical 위치의 %를 표시한다.
- layout key는 revision, 엔진 정책 버전, TextStyle(글꼴·크기·행간·자간·locale·line break·hyphenation·font padding),
  실제 resolved font, density/fontScale, viewport, margin, safe insets, locale 및 layout direction을 포함한다.
- 스크롤/페이지 모드 전환과 설정 변경은 기존 anchor를 유지한다. 명시적 페이지 이동만 실제 측정 완료 후
  새 anchor로 저장한다. EOF로 열기나 설정 reflow 자체는 진행도를 새로 쓰지 않는다.
- 전역 타이포/테마와 책별 readingMode override를 Room schema 3의 별도 versioned 테이블에 저장한다.
  override 제거는 global 상속으로 돌아간다. schema 1→3/2→3은 additive migration이다.
  읽지 못한/손상된/미래 버전 설정은 기본값으로 표시하지만 저장을 차단하고 원본을 보존한다.
  저장은 읽었던 원본 rows를 transaction에서 비교하여 동시 수정·삭제 뒤 늦은 요청을 거부한다.

## 발견한 문제

첫 전체 로컬 재검증에서 새 navigation/reflow 단위 검사에 간헐적 10초 timeout이 발생했다.
직전 restoration coroutine이 READY를 게시한 뒤 아직 종료되지 않았으면 다음 reflow callback을 무시했다.
이미 session이 있는 reflow는 동기적으로 완료하도록 분리했고, navigation anchor와 READY를 한 번에 게시한다.
수정 전 FAIL XML/log를 보존했다. 원격 초기 소스 `6552d56`가 통과했어도 로컬 재현을 무시하지 않았다.

## 실제 검증

| 범위 | 결과 |
|---|---|
| local full CI | unit 52/shared JVM 57, 실패·오류·skip 0, APK/test APK/lint/schema PASS |
| remote build 39005182214 | 52/57/실제 ENOSPC 3 PASS; artifact 12090787475 |
| remote API36 device 39005182216 | ordinary 35/외부 host 2 stages PASS; artifact 12090342943 |
| 실제 페이지 측정 | 131072자 문단, 1MiB ASCII 단일 문단, 빈 줄/공백/이모지/결합문자 coverage hash 일치 |
| 이동 | 지역 anchor에서 100회 앞뒤 pair의 동일 경계 재사용; 실제 UI 12회 전진·12회 후진 |
| UI/저장 | scroll→page→font size 변경→scroll→재열기; anchor/진행도 보존과 설정 복원 |
| DB | 설정 round-trip/상속/동시 stale save 거부/손상·future bytes 보존/FK 삭제 및 late save 거부 |

원본 native XML, workflow SHA, ZIP digest, host stage/PID와 PNG decode/hash를 확인했다.
첫 페이지 PNG는 복원 중 overlay가 남은 프레임이므로 안정된 READY 화면 증거로 사용하지 않는다.
글자 크기 변경 뒤 PNG는 같은 anchor의 새 줄바꿈을 보여 준다. 후속 UI에서는 READY semantics와 화면 프레임 동기화를 보완한다.

글꼴·행간·자간·여백별 변경과 실제 OS fontScale/density 조합, 더 많은 임의 지역 이동은 M06과 함께 후속 검증한다.
실기기 성능/접근성 및 신규 Astra 검토는 NOT_RUN이다. 사용자 요청에 따라 M06 검색·북마크·인코딩 구현을 계속한다.
