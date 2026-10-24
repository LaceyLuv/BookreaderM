# 제품 완성도 개선 검증 원본

[감사와 진행 상태](../../PRODUCT_IMPROVEMENT.md), [0.5.1 보고서](../../UX_POLISH_0_5_1.md)를 따른다.

- `phase1-local`: 새 unit2의 수정 전 FAIL, 첫 ModifierParameter lint FAIL, 수정 후 전체163 PASS.
  `large-layout`은 실제 화면 관찰 뒤 보완한 Android73/APK/test APK/lint PASS다.
- `verified-11ba576`: 첫 후보 build40059003307 166개, device40059003261 API26/34/36 각각 native63+host6 PASS.
  각 ZIP을 GitHub artifact SHA-256과 대조한 뒤 원본 XML·source clean·필수 사례·실제 force-stop/PID 교체를 확인했다.
  이 후보의 큰 글자 카드 폭/다크 navigation을 보완했다. `phase1-library-large-dark.png`는 닫기 직후 dialog가 아직 보이는 중간 캡처다.
- `package-11ba576`: 이전 후보 설치 시험용 패키지. `gradle-default-signature.log`는 기존 Gradle 기본 설정의 v3=false 결과이며
  이후 임시 서명 설정에 v2/v3를 명시해 기존 키로 다시 만들었다. 이 후보를 최종 패키지로 안내하지 않는다.
- `verified-5a8aad2`: 최종 build40060195353 166개, device40060195360 API26/34/36 각각 native63+host6 PASS.
  ZIP digest/CRC, source clean, 전체 필수 XML 및 실제 PID 교체 대조 완료. API26/36 새 캡처에서 보완 결과를 확인했다.
- `package-5a8aad2`: 큰 글자/다크 UI 보완 후 릴리스 APK/AAB와 기존 키를 사용한 서명 APK의 감사 기록.
  기기 검증 상태는 `summary.json`을 따른다. 공개 배포나 실제 휴대전화 설치를 뜻하지 않는다.

테스트 원본의 책 본문·이미지·폰트는 자체 생성/재배포 허용 fixture다. 사용자 책이나 개인 URI, 서명키·토큰은 포함하지 않는다.
ZIP/APK 바이너리는 중복 커밋하지 않고 XML/로그/화면/JSON/체크섬을 보존한다. 원본 ZIP은 작업 환경 CI 디렉터리에 유지한다.
스크린샷의 `../../unknown`은 위험한 표시 이름이 경로로 사용되지 않는지 확인하는 기존 fixture의 의도된 제목이다.

환경 한계: 로컬 `adb` 연결 기기와 KVM 없음. 물리 기기·TalkBack·16KB OS·실제 사용자 서재 성능은 NOT_RUN이며
원격 에뮬레이터 검사나 정적 정렬 검사로 대체하지 않는다.
