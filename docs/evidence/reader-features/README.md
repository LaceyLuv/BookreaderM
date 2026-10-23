# 0.4.0 검증 원본

- `local-failures/`: nullable UI callback compile 오류와 종료/복원 경합 timeout 로그. 수정 후 성공 XML은 파일명으로 구분한다.
- `ci-bd6b456/`: 첫 구현. Build 161 PASS, API26/34/36 각각 native 58 중 EPUB font 1 FAIL, host NOT_RUN.
- `ci-0aeba65/`: 전환 후 위치 확정 보완. Build 161 PASS, 각 API native 58 중 같은 EPUB font 1 FAIL, host NOT_RUN.
- `package-0aeba65/`: 중간 소스 패키지 감사. 최종 설치 안내에서 사용하지 않는다.
- `package-d1570aa/`: 최신 구현 소스 d1570aa의 서명 APK, 원본 entry 동일성, 인증서 유지 및 16KB 정적 정렬 PASS. 최종 native/host도 같은 소스에서 모두 PASS다.

EPUB 실패는 logcat에 기록된 두 번들 font URL의 CSP 거부다. e883cb7에서 두 내부 파일 URL만 font-src에 추가했다.
e883cb7 device 39963715270은 API26/36 각각 native 58 + host 6 PASS, API34는 KSP plugin 조회 실패로 native/host NOT_RUN이다. `ci-e883cb7/`에 원본을 보존했다.
최신 d1570aa에서는 하단 쪽수와 도구 패널 겹침도 보완했다. 전체 build 39964576203은 161 PASS, device 39964576436은 API26/34/36 각각 native 58 + host 6 PASS다. `verified-d1570aa/`에 원본/필수 manifest/요약/최종 화면을 보존했다.
실기기 설치/성능/TalkBack/정식 출시 승인은 이 결과에 포함하지 않는다.
