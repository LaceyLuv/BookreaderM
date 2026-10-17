# M07 모바일 제어 — 진행 중

source `a18327131ebaba71de70b33194828805ef3babb2`를 main에 반영했다.
local 전체 검증 및 remote build의 Android unit 59/shared JVM 67, remote ENOSPC 3은 PASS다.
Android 기기 검증 run 39007531744는 실행 중이다. native PASS로 기재하지 않는다.

## 구현

- 실제 버튼이 있는 독서 메뉴를 modal로 표시한다. 메뉴 자체는 본문 viewport를 바꾸지 않는다.
- 25/50/25% 탭 영역, 페이지 swipe, scroll 90% 이동을 제공한다. OS 가장자리 24dp를 피하고
  child 소비/다중 터치/스크롤/long press 선택을 우선한다. 첫 탭의 이동은 double-tap 결정까지 기다린다.
- Text selection을 허용하며 touch exploration이 활성화되면 단순 탭 영역 대신 실제 메뉴 버튼을 제공한다.
- slider preview는 별도 상태다. 실제 release/접근성 SetProgress만 canonical 위치로 이동한다.
  Cancel/닫기/Back은 preview를 버린다. cache 길이를 모르는 동안 slider를 비활성화한다.
- 최신 seek 요청만 적용하며 경계는 bounded fragment에서 BreakIterator로 계산한다.
  페이지 번호를 저장하지 않고 실제 표시 측정 완료 후에만 canonical anchor를 commit한다.
- 창 밝기/keepScreenOn/현재 방향 유지 요청/전체화면을 Reader 창에 적용하고 dispose 때 초기 값을 복원한다.
  설정 JSON의 additive window field는 이전 설정의 기본값과 호환된다. 알 수 없는 raw 설정 보존 정책은 유지한다.
- 큰 글자 배율에서 header/footer와 설정 버튼을 줄바꿈하고 설정/메뉴를 스크롤할 수 있게 했다.

## API 확인 및 검증

Material3 1.4.0 Slider와 Foundation 1.9.3 Draggable 공식 source를 확인했다.
Foundation `onDragStopped`는 Cancel에도 호출하므로 이 콜백 자체를 저장 승인으로 취급하지 않는다.
DragInteraction.Stop/Cancel과 preview version을 구분한다. 실제 MotionEvent cancel과 accessibility SetProgress
검증을 필수 Android test에 추가했다. window API는 compileSdk36으로 실제 compile했다.

원본 lint FAIL은 반환값이 있는 Composable 함수의 대문자 이름이었다. 규칙을 끄지 않고 함수명을 수정했다.
[local evidence](evidence/m07-validation/local-ci/summary.json),
[remote build evidence](evidence/m07-validation/remote-a183271-build/summary.json)를 보존했다.
최초 lint 실패도 `initial-failures`에 남겼다. local worktree와 main의 runtime/test/script 입력 hash가 같다.

## 남은 gate

실제 Android menu/cancel/window/fontScale 테스트 결과, 실제 TalkBack 읽기 순서, 모든 edge/selection/pinch 충돌,
노치·IME·Back gesture·창 resize 매트릭스, 실기기 제한 및 최종 독립 검토는 완료되지 않았다.
현재 M07은 IN_PROGRESS이며 M11 출시 gate를 통과한 상태가 아니다.
