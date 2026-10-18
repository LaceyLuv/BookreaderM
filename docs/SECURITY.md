# 입력·데이터 보호 검토 — 2026-10-17

## 2026-10-18 검증 갱신

`8ca750e` unsigned APK/AAB 생성 및 APK 정적 감사는 PASS다.
[정확 source/hash·감사 원본](evidence/archive-resume-verified/release-8ca750e/)을 보존한다.
최신 device matrix는 API26/34 FAIL, API36 SUCCESS(metadata)로 main 통합 전이다.
물리 release 성능·PSS/GPU·실제 16KB OS·TalkBack·전체 보안 corpus·최종 서명/출시 검증은 여전히 미완료다.
[최신 검증 보고서](RESUME_VALIDATION_2026-10-18.md)를 우선하며 아래 자료는 이전 소스의 이력이다.

이 문서는 구현 경계와 실행한 증거를 기록한다. 독립 보안 감사나 침투 테스트 완료 판정이 아니다.
실행 결과와 미실행 조합은 [QA 매트릭스](QA_MATRIX.md)를 따른다.

## 영구 데이터와 개인정보

책은 SAF/VIEW/SEND 입력에서 앱 전용 staging으로 복사하고, 실제 bytes/hash·포맷 검증 후 journal을 통해 승격한다.
책의 원본 URI는 독서 저장소가 아니다. 성공한 관리 복사본·버전 있는 locator·DB는 파생 cache와 분리한다.
삭제는 DELETING/session 무효화 → 작업 종료 → 파일 정리 → cascade 순서이며 지연된 쓰기가 책을 재생성하지 않는다.
Room migration은 additive이고 destructive fallback을 호출하지 않는다. 읽기 오류를 빈 서재나 새 0%로 대체하지 않는다.

실제 APK 감사에서 요청 권한은 ACCESS_NETWORK_STATE이며 INTERNET/광범위 저장소 권한이 없다.
앱에는 로그인·analytics·원격 crash 업로드·책/기록 전송 기능이 없다. 외부 HTTP(S) 링크는 확인 후 외부 브라우저로 넘긴다.
그 뒤 브라우저의 네트워크 접근은 별도 앱 동작이다. 링크 문자열은 최대 8,192자이며 실행 가능한 브라우저가 없으면 오류를 표시한다.
공개 CI는 자체 생성 fixture만 사용한다. 개인 책·URI·인증 토큰·서명키를 증거에 넣지 않는다.

`allowBackup=false`, Android ≤11 full-backup 규칙과 Android ≥12 cloud/D2D extraction 규칙은
root/file/database/sharedpref/external 및 device 영역을 제외한다. 실제 OEM D2D 동작은 NOT_RUN이다.
설정 및 삭제 확인에 내부 복사본/기록 손실, 원본 미변경, 백업 기능 부재를 안내한다.

## 비신뢰 입력 한도

| 입력 | 강제 경계 |
|---|---|
| TXT | 실제 원본 256MiB, strict decoding·정규 UTF-16·bounded fragment/search; 잘못된 바이트를 replacement로 숨기지 않음 |
| EPUB | 원본 512MiB, markup 하나 16MiB/전체 32MiB, 보호 변환 후 합계도 32MiB, spine 2,048개 및 반복 참조 bytes 합계 제한 |
| ZIP/CBZ | 원본 2GiB, entry 20,000개, 총 논리 크기 4GiB, 단일 entry 64MiB, central directory 64MiB |
| 공통 ZIP | CRC/실제 inflate 크기/중첩 범위/경로/중복 NFC·파일-디렉터리 충돌 검증; ZIP64/split/encrypted/symlink 거부 |
| XML | DTD/외부 entity 금지, depth 64/nodes 100,000/attributes 128 |
| EPUB 검색 | query 256 UTF-16, 한 chapter 2MiB, 50개/page·총 1,000개, 실제 작업 30초; 한도 도달을 오류로 표시 |
| Comic | static JPEG/PNG/WebP, decode 동시성 1, decoded bitmap min(64MiB, heap/5), 치수/sample 사전 검사 |

deadline은 bounded chunk 사이의 취소 검사다. 플랫폼 native decoder를 임의 시점에 강제 선점한다고 주장하지 않는다.
긴 이미지는 전체 내용을 안전한 다운샘플로 표시하고 해상도 제한을 알린다. 표시 중 bitmap lease 해제와 decode 취소 소유권을 구분한다.

## Readium 경계

공식 안정 3.1.2 Fragment API를 사용하며 alpha renderer나 자체 CFI 엔진을 만들지 않는다.
publication마다 private ZIP allowlist Container를 만들어 다른 책/파일/HTTP resource를 제공하지 않는다.
OPF MIME과 확장자를 함께 검증하여 HTML을 이미지로 위장하는 우회를 차단한다.
XML 구조 검증·active element/event 거부·CSP로 책 script 및 외부 자동 resource를 제한한다.
Readium 엔진의 자체 script는 구분해 허용한다. native probe는 실제 WebView CSP 위반과 정상 local resource 렌더링을 확인했다.
SDK가 query/fragment를 제거해 같은 local resource를 재요청하는 정상 fallback은 허용되지만 foreign path는 제공되지 않는다.
DRM/FXL/interactive 콘텐츠는 지원 불가로 거부하고 원본/진행도를 유지한다.

정확한 Locator는 SDK JSON을 보존한다. page progression만으로 글자 크기 변경 후 exact restore를 주장하지 않는다.
강화된 제품 테스트에서 발견한 964f8cc 실패와 a5a7a31 보완을 별도 추적한다.
새 독립 리뷰, 전체 공격 corpus의 제품 WebView 재검증, OEM 백업 동작 및 물리 저장장치 장애는 출시 잔여 gate다.
