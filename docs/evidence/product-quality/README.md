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

## Phase 2 / 0.6.0

[최종 보고서](../../LIBRARY_0_6.md), [개발 이력](../../LIBRARY_0_6_DEVELOPMENT.md).

- `failed-f35d685`: 최초 build175 PASS, native API26 8/71 FAIL·API34/36 각3/71 FAIL, host NOT_RUN.
- `failed-abee6a1`: build175 PASS, API26 2/72·API34 3/72 FAIL, API36 1/3 FAIL 뒤69 NOT_RUN. host NOT_RUN.
- `verified-d93a268`: build175/세 API native72+host7 PASS. 이후 작은 생성 표지 글자 크기 보완.
- `failed-525587e`: build175/API26·36 PASS, API34 같은 표지 재선택1/72 FAIL, host34 NOT_RUN.
- `verified-fe29dea`: 반복 선택 성공 번호 보완 뒤 build175/세 API native72+host7 PASS.
- `verified-a22970b`: 최종 캡처 보완 후 build40885971844 PASS175/device40885971811 세 API native72+host7 PASS.
  fe29dea와 제품 소스는 동일하다. 원본 SHA/CRC/XML/source clean/외부 force-stop PID 교체 확인.
  API26 `phase2-library.png`는 이전 편집 창 프레임으로 시각 증거에 사용하지 않는다. API34/36 서재 확인.
  API26 책장 캡처는 필터 메뉴가 열린 모습이다. 원본을 편집하거나 삭제하지 않았다.
- `package-a22970b`: 최종 같은 키 설치 시험용 APK/ZIP 감사. 실기기 설치·공개 배포 NOT_RUN.

중간 API36 Compose 측정 재진입의 원인은 미확정이다. 후속 PASS만으로 원인 해결이라 부르지 않는다.

## Phase3 TXT 목차 단위

- `phase3-toc-scanner-local`: 최초 scanner6개 포함 shared89 PASS.
- `phase3-toc-local`: 최초 migration fixture 키 오류1 FAIL과 수정 후 로컬186 PASS, 릴리스 컴파일/기존 계약 불변 확인.
- `verified-05ffd50`: build40892825363 PASS189, device40892825193 API26/34/36 각각 native73+host7 PASS.
  SHA/CRC/XML/source clean/필수 사례/실제 PID 교체를 대조했다. 새 목차 저장·이동·재열기·force-stop 복원 포함.
  API36 실제 목차 목록 확인. API26/34 `phase3-txt-toc.png`는 닫히는 추가 창 프레임이므로 최종 목록의 시각 증거로 사용하지 않는다.

형광펜·도구 개편·백업·실기기·0.7.0 전체 완료를 뜻하지 않는다. [범위와 실제 화면](../../READER_0_7.md).
