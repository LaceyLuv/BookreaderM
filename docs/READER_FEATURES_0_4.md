# 독서 편의 개선 — 0.4.0

상태: 구현·자동 검증·main 반영 완료. 검증 제품/테스트 소스는 `d1570aa92edddb676be3c5bc311d53506f2e9ad9`이다.
개발용 서명 0.4.0 APK를 생성했다. 실제 휴대전화 설치 및 정식 출시 판정은 별도다.

## 사용자 동작

- TXT·EPUB에 나눔고딕·나눔명조를 추가했다. 폰트 파일을 앱에 포함하므로 오프라인에서도 선택한다.
- 페이지 전환은 ‘효과 없음’(기본값)/‘밀어서 넘기기’를 선택한다. TXT·Comic 화면에는 같은 전환을 사용하고 EPUB은 공식 SDK의 animated 이동을 사용한다.
- TXT는 같은 레이아웃에서 새 본문을 준비하는 동안 직전 본문을 유지하고 일반 페이지 넘김마다 복원 spinner를 띄우지 않는다. Comic 한 장 보기도 새 이미지 decode가 끝날 때까지 직전 bitmap을 유지한다.
- 본문 하단과 중앙 탭으로 여는 하단 독서 현황에 현재/전체 쪽수를 표시한다. 사용자가 명확히 한 상태 영역은 Android 시스템 상태바가 아니다. 상단에는 별도 쪽수 표시를 넣지 않는다.
- 서재의 책 카드 본문을 눌러도 책이 열린다. 기존 ‘읽기’와 ‘책 정보’ 버튼은 각자의 동작을 수행한다.

## 페이지 수와 위치 보존

TXT는 현재 글꼴·줄간격·여백·viewport로 측정한 페이지 경계를 별도 worker에서 계산한다.
스크롤 보기의 쪽수도 같은 화면 크기로 환산한 경계를 사용한다. 계산 중에는 계산 중이라고 표시한다.
본문/레이아웃 전체를 메모리에 올리지 않고 65,536 UTF-16 단위 이하 창과 최대 250,000개의 경계만 유지한다.
글꼴·화면 크기가 달라지면 취소 후 다시 계산하며, 한도/계산 오류 시 쪽수 계산 불가를 표시하고 독서는 유지한다.
EPUB 전체는 Readium publication position 기준이므로 ‘EPUB 위치 기준’이라고 명시한다.
한 화면을 넘겨도 이 기준 위치는 그대로일 수 있다. Comic은 압축파일의 검증된 이미지 순서/장수다.

쪽수는 표시용이며 영구 locator로 저장하지 않는다. 기존 TXT UTF-16/EPUB 전체 locator/Comic entry 좌표,
복원 완료 전 저장 금지와 설정 변경 시 위치 보존 계약을 유지한다.
TXT는 실제 TextLayout 측정과 전환 완료를 모두 기다리고, Comic도 전환이 끝난 프레임 이후에만 새 위치를 확정한다.
키보드·도구 표시 때문에 본문 높이가 달라지지 않도록 하단 쪽수 영역은 항상 같은 공간을 사용한다.

## 글꼴 출처와 호환성

Google Fonts `e7db6ca603fd4862010383ff17ffd6cc4ffac1c0`의 변경하지 않은 NanumGothic-Regular.ttf,
NanumMyeongjo-Regular.ttf를 사용한다. 파일별 URL/hash는 [sources.json](../androidApp/src/main/assets/reader-fonts/sources.json),
SIL Open Font License 원문은 같은 폴더와 앱의 오프라인 제3자 고지에 포함했다.
EPUB은 Readium 3.1.2의 `servedAssets`/`addFontFamilyDeclaration` 공식 API로 font face를 제공한다.
CSP의 font-src에는 번들 글꼴 두 파일의 정확한 내부 URL만 추가한다. script/connect/image 정책은 그대로다.
EPUB에서 글꼴을 직접 선택하면 출판사 스타일 우선을 해제해 선택 결과가 본문에 반영되게 한다.
설정 JSON의 기존 값은 보존하며 새 전환 필드가 없는 0.3.0 설정도 읽는다.

## 검증 범위

기존 포맷별 회귀에 두 신규 글꼴/효과 선택/설정 유지/하단 쪽수/카드 및 정보 버튼 검증을 추가했다.
새 단위 2개는 페이지 경계 매핑·잘못된 경계·취소를 검증한다.
새 native 1개는 양방향 slide와 효과 없음의 실제 렌더 프레임에서 빈 배경 노출을 검사한다.

| 검증 | 최종 결과 | 실행 |
| --- | --- | --- |
| Android/shared/EPUB 단위·실제 ENOSPC | 68 + 74 + 16 + 3 = 161 PASS | [39964576203](https://github.com/LaceyLuv/BookreaderM/actions/runs/39964576203) |
| API26 / Android 8 | native 58 + host 6 PASS | [39964576436](https://github.com/LaceyLuv/BookreaderM/actions/runs/39964576436) |
| API34 / Android 14 | native 58 + host 6 PASS | 같은 device 실행 |
| API36 / Android 16 | native 58 + host 6 PASS | 같은 device 실행 |
| release APK/AAB 생성, 개발용 서명 APK 감사 | PASS, exit 0 | [패키지 원본](evidence/reader-features/package-d1570aa/) |

필수 사례 누락·실패·오류·skip은 0이다. host 검증은 TXT·EPUB·Comic 각각 2개이며
실제 force-stop 후 PID 소멸과 새로운 프로세스의 다른 PID를 확인했다.
CI source SHA/clean 상태·XML·필수 manifest를 대조했고 다운로드 ZIP hash는 GitHub artifact digest와 일치한다.
[최종 결과와 원본](evidence/reader-features/verified-d1570aa/summary.json),
[중간 실패 및 수정 이력](evidence/reader-features/README.md)을 보존한다.

최종 생성 fixture 캡처를 직접 확인했다:
[나눔명조와 현재/전체 쪽수](evidence/reader-features/verified-d1570aa/preview/nanum-and-pages.png),
[글꼴 선택](evidence/reader-features/verified-d1570aa/preview/fonts.png),
[전환 효과 선택](evidence/reader-features/verified-d1570aa/preview/transitions.png),
[만화의 하단 쪽수](evidence/reader-features/verified-d1570aa/preview/comic-pages.png).
본문 하단과 도구의 쪽수 표시가 겹치지 않는 것도 확인했다.

[설치 안내](INSTALLABLE_APK.md)의 `BookreaderM-0.4.0-test-signed.apk`를 사용한다.
SHA-256: `cf74e8ea6ede2fa360ddb7d75210b0822b87aa4e512005e78d2a0512582dbdf2`.
기존 인증서를 유지했고 v2/v3 서명·4 ABI/8 ELF 및 ZIP 16KB 정렬을 통과했다.
자동 화면 검사는 실기기에서의 프레임 시간/성능 평가를 대신하지 않는다.
실기기 설치·성능·TalkBack 수동 평가·16KB OS·정식 출시 승인은 NOT_RUN이다.
