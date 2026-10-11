# M04 연속 이동 중 저장 관찰 보고서

## 측정 범위

SAFE-09 검증은 두 수준으로 구분한다.

- controller 단위: 60,000ms 가상 시간 동안 100ms마다 사용자 anchor를 변경한다. 정확히 60회의 1초 sampling,
  계속 증가하는 sequence와 해당 시점의 anchor를 확인한다. 이후 역방향 이동은 250ms settled save로 확인한다.
  실제 DB·화면·wall-clock 성능 측정은 아니다.
- Android instrumentation: 실제 가져오기/Reader에서 한 pointer를 60초 이상 유지하고 2초마다 이동 방향을 바꾼다.
  `UiAutomation.injectInputEvent`로 실제 touch를 주입하고 Compose 측정을 진행하며 독립 Room observer로 commit을 읽는다.
  controller의 visibleAnchor/flush/close를 직접 호출하지 않는다. elapsedRealtime/uptimeMillis로 실제 시간을 기록한다.

기기 입력은 50ms sleep에 주입·layout·DB 조회 시간이 더해진다. 가상 시간의 100ms 간격과 동일하다고 주장하지 않는다.
actual 입력 최대 공백은 1초 이하, 측정 anchor 변경 120회 이상, 매 10초 구간의 이동/commit 존재,
pointer-up 전 commit 관찰 12회 이상, 시작/끝을 포함한 최대 commit 관찰 간격 5초 이하를 요구한다.
5초는 emulator correctness 검사 한도이며 제품의 실기기 p95 SLA가 아니다.

## 측정치의 의미

`continuous-progress-report.log`는 JSON이며 fixture ID/SHA-256/bytes, device build fingerprint/API/ABI,
app version, 초기 epoch/sequence, 실제 입력 시각, 측정 anchor 변화, 새 commit의 관찰 시각/sequence/offset을 보존한다.
body·사용자 URI·책 ID는 기록하지 않는다. fixture는 TestDocumentsProvider가 자체 생성한
`reader-continuous` 6,000행 UTF-8이며 원문 파일 hash는 실제 import한 Book의 sourceSha256으로 기록한다.

commit 관찰 간격에는 sampling, IO scheduling, 독립 DB 조회의 관찰 주기가 포함된다.
`gesture_end_to_settled_observation_ms`는 pointer-up 이후 최종 측정 anchor와 DB가 일치한다고 관찰한 시간이다.
이는 SQL write 호출→durable acknowledgement latency, 모든 중간 anchor의 저장 지연 또는 전원 차단 내구성 측정이 아니다.
해당 SQL latency, physical-device 5MiB/50MiB p95·메모리·OEM 수치는 **NOT_RUN**이다.

## 결과

`78e45f5` local/remote 단위 47/50은 PASS, actual Android `38116198398`은 FAIL이다.
60,081ms 동안 입력 600회(최대 공백 130ms)를 주입했으나 anchor 변화 0회/commit 관찰 1회였다.
해당 결과는 SAFE-09 통과 근거가 아니며 host는 후속 단계 미실행이다.
Reader의 mutable LazyList item 비교가 연속 이동을 누락하여 index/offset 값 캡처로 수정하고 같은 테스트를 재실행한다.
최신 native XML/raw report/source fingerprint는 `docs/evidence/m04-continuous-validation/`에 남긴다.
APK 컴파일을 실제 60초 입력 통과로 간주하지 않는다.
