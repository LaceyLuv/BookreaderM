# 로컬 검증 이력

- reader-features-ci.log: UI 테스트의 nullable Book 인자 compile 오류, exit 1.
- reader-features-final-local.log: 기존 closeCancelsPendingActivationBeforeWaitingForProgressFlush의 TimeoutCancellationException(3초), exit 1.
- after-close-fix-TxtReaderControllerTest.xml: 종료/복원 경합 수정 후 18개 PASS. 실패 XML은 재실행에서 갱신되어 남아 있지 않으며 이 파일은 후속 성공 원본이다.

실패를 skip/timeout 완화로 넘기지 않았다. 종료 claim과 restored 진입을 같은 monitor로 보호하고 종료 후 재활성화를 차단했다.
