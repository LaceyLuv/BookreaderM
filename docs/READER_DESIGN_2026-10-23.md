# 독서 화면과 터치 동작 개편

상태: 구현·자동 검증·main 반영 완료. 검증 소스는 `c2383259eb1c15794e4c1f6ecfdbb198c6c6f968`이다.
설치 시험용 0.3.0 APK도 생성했다. 정식 출시 판정은 별도다.

TXT·EPUB·Comic의 본문은 처음부터 화면 전체를 사용한다. 가운데를 탭하면 도구창을 열고,
다시 탭하거나 ‘숨기기’를 누르면 닫는다. 도구창 표시와 시스템 전체화면 옵션을 분리했다.
상단에는 서재 이동·책 제목, 하단에는 이전/다음·진행도·설정·검색/목차·북마크·위치 이동을 둔다.
TalkBack의 터치 탐색 중에는 명시적 버튼을 유지하며, 불러오기/오류 중에도 서재로 나갈 수 있다.

본문은 사용자가 선택한 밝게/세피아/어둡게/검정 색상을 유지한다.
도구와 설정은 남색 표면, 민트 강조, 테두리와 그림자로 구별한다.
설정은 읽기 방식·글자/간격·본문 색상·기본 동작 구획으로 나누고,
본문 색상과 글자 크기를 확인할 수 있는 미리보기를 제공한다.
만화 설정에는 읽기 방식·넘기는 방향·이미지 맞춤을 구분했다.

## 터치 규칙

| 형식 | 좌우 탭 | 가운데 탭 | 추가 동작 |
| --- | --- | --- | --- |
| TXT 페이지 | 이전/다음 페이지 | 도구 열기/닫기 | 가로 스와이프, 길게 눌러 선택 |
| TXT 스크롤 | 화면 높이의 약 90% 이동 | 도구 열기/닫기 | 세로 스크롤 |
| EPUB | 출판물 읽기 방향에 따른 이전/다음 | 도구 열기/닫기 | SDK 링크/선택/스크롤 보존 |
| Comic 한 장 | LTR/RTL 설정에 따른 이전/다음 | 도구 열기/닫기 | 두 번 탭/핀치 확대, 확대 중 끌기 |
| Comic 연속 | 위/아래로 화면 높이의 약 90% 이동 | 도구 열기/닫기 | 세로 스크롤, 두 번 탭 또는 도구의 ‘확대’ |

TXT의 기존 두 번 탭 위치 메뉴와 취소 가능한 위치 슬라이더도 유지한다.
TXT 화면의 가장자리 탭을 허용하되 가장자리에서 시작한 스와이프는 시스템 뒤로가기에 남긴다.
만화 확대 중에는 좌우 단일 탭으로 다른 장으로 넘어가지 않는다.

## 구현 판단

도구창이 본문 위에 겹쳐 표시되므로 도구창을 열고 닫아도 본문 viewport가 변하지 않는다.
이 동작은 TXT의 UTF-16 위치, EPUB의 전체 locator, Comic의 entry/정규화 좌표를 그대로 유지한다.
페이지 번호를 저장 좌표로 사용하지 않으며, 복원 완료 전 저장 금지 규칙도 유지한다.
큰 시스템 글자에서는 패널 안을 스크롤해 도구를 찾을 수 있고 가운데 터치 영역을 남긴다.

EPUB은 Readium 3.1.2의 공식 InputListener를 사용한다. SDK가 링크·선택·출판물의
이벤트 처리를 마친 뒤 전달한 탭에만 반응한다. WebView 위에 터치 차단층을 추가하지 않는다.
TXT/Comic의 오래 유지되는 제스처 처리는 최신 callback을 참조한다.

검색·북마크 dialog의 키보드가 나타나고 사라져도 뒤쪽 본문 크기를 유지하도록
Reader의 시스템 여백에서 IME를 제외한다. 독서 중 Activity의 soft input만 ADJUST_NOTHING으로
설정하고 종료 시 복원한다. TXT는 재배치 중 입력한 검색어를 READY 이후 실행한다.
Android 8/9의 시스템 탐색 바도 남색으로 표시해 흰 버튼의 대비를 확보한다.

## 검증과 산출물

기존 native 54개와 host 6개를 모두 실행했다.
새 native 3개는 반복 좌우/중앙/가장자리 탭, 길게 누르기/취소/다중 터치/시스템 경계,
실제 EPUB 좌우 이동 및 도구창 전환 중 viewport/위치 보존을 검증한다.
TXT/Comic 제품 회귀에도 실제 중앙/좌우 탭과 도구창 전환 검증을 추가했다.
자체 생성 책만 사용한 본문·도구·설정 스크린샷을 CI에서 수집한다.

EPUB의 전체 저장 locator와 같은 레이아웃에서의 progression 허용 오차 `.03`을 유지한다.
글자 크기가 달라진 레이아웃은 독립 DOM 문단 위치와 장 끝 clamp로 예상 progression을 계산해
같은 `.03`으로 검사한다. 검색한 문단이 **전체 표시**되는지도 확인한다.
[실제 재배치 관측](evidence/reader-design/verified-c238325/reflow-geometry.json)을 보존했다.

| 검증 | 결과 | 원격 실행 |
| --- | --- | --- |
| Android/shared/EPUB 단위·실제 ENOSPC | 66 + 74 + 16 + 3 = 159 PASS | [39946827381](https://github.com/LaceyLuv/BookreaderM/actions/runs/39946827381) |
| API26 / Android 8 | native 57 + host 6 PASS | [39946827437](https://github.com/LaceyLuv/BookreaderM/actions/runs/39946827437) |
| API34 / Android 14 | native 57 + host 6 PASS | 같은 device 실행 |
| API36 / Android 16 | native 57 + host 6 PASS | 같은 device 실행 |
| release APK/AAB 생성, 서명 APK 정적 감사 | PASS, exit 0 | [패키지 원본](evidence/reader-design/verified-c238325/package/) |

필수 사례 누락·실패·오류·skip은 0이다. host 검증은 TXT·EPUB·Comic 각각 2개이며,
실제 force-stop 후 PID 소멸과 새 프로세스의 다른 PID를 확인했다.
CI ZIP SHA-256은 GitHub artifact digest와 일치하며 source SHA와 원본 XML도 대조했다.
[최종 결과와 원본](evidence/reader-design/verified-c238325/summary.json),
[첫 실패](evidence/reader-design/ci-2b24f7d/summary.json),
[두 번째 실패](evidence/reader-design/ci-9f99b2c/summary.json)를 함께 보존한다.

자체 생성 fixture의 최종 화면을 직접 확인했다:
[독서 본문](evidence/reader-design/verified-c238325/preview/reading.png),
[도구 표시](evidence/reader-design/verified-c238325/preview/controls.png),
[설정](evidence/reader-design/verified-c238325/preview/settings.png),
[Android 8 탐색 바](evidence/reader-design/verified-c238325/preview/legacy-navigation.png).

[설치 안내](INSTALLABLE_APK.md)의 **BookreaderM-0.3.0-test-signed.apk**를 사용한다.
기존 개발용 인증서를 유지했으며 v2/v3 서명, 4 ABI/8 ELF 및 ZIP 16KB 정렬 검사를 통과했다.

로컬 환경에는 KVM과 연결 기기가 없어 native 검증은 원격 API26/34/36을 사용한다.
실기기 설치·성능·TalkBack 수동 평가·16KB OS·정식 배포는 이 작업의 PASS에 포함하지 않는다.
