# ADR 0007 — 사용자 글꼴과 굵기

2026-10-24. 사용자는 TTF·OTF 글꼴 등록과 글자 굵기 선택을 요청했다.

## 결정

Android 문서 선택기로 받은 임시 content URI를 읽어 `filesDir/reader-fonts`에 독립 복사한다.
외부 URI나 표시 이름을 저장 경로로 사용하지 않는다. SHA-256 디렉터리 안에 글꼴과 길이를 제한한 표시 이름을 둔다.
동일 파일의 재등록은 같은 ID를 반환한다. 스트림당 20MiB, 등록 12개/합계 100MiB, 읽기 60초를 상한으로 삼는다.
파일 크기·SFNT TTF/OTF 테이블 경계/중복/겹침·필수 테이블과 Android Typeface 로드를 확인한 뒤
staging 디렉터리를 같은 파일시스템에서 이동해 반영한다. 취소/실패 시 staging을 정리한다.
현재 적용 중인 글꼴은 UI에서 삭제할 수 없으며 다른 글꼴을 적용한 후 삭제한다.

ReaderSettings JSON에 nullable `customFontId`와 기본 NORMAL의 `fontWeight`를 추가한다.
기존 JSON을 읽을 수 있고, 사용자 글꼴이 없으면 기존 내장 글꼴을 표시한다. 파일 경로는 설정에 넣지 않는다.
굵기는 NORMAL(400)/BOLD(700) 두 선택이다. 정적 글꼴의 굵게 표시는 플랫폼 합성 결과일 수 있고,
등록 파일 자체가 굵은 글꼴이면 보통은 그 파일의 기본 모양을 뜻한다.

TXT의 실제 TextStyle·측정·쪽수 계산에 같은 fontFamily/fontWeight를 전달한다.
리플로우는 기존 UTF-16 locator를 복원하고 완료 전 진행도를 저장하지 않는다.
EPUB은 Readium 3.1.2 공식 `addFontFamilyDeclaration`과 `EpubPreferences.fontWeight`를 사용한다.
Readium의 기존 가상 publication 리소스 경로에 현재 선택된 ID의 글꼴 하나만 공급한다.
`_bookreader_fonts/<sha256>.ttf`는 앱 전용 경로로 예약하며, 이 접두어를 가진 출판물은 충돌을 피하려고 거부한다.
리소스 요청 시 정확한 ID/길이/hash를 확인한다. `.ttf` URL은 가상 식별자이며 내용은 등록된 TTF/OTF 원본이다.
외부 font URL, file://, data: 허용을 추가하지 않고 기존 CSP와 SDK 자원 로딩을 유지한다.
새 설정을 적용하면 기존 host 교체/전체 locator 복원 경로를 사용한다.

## 대안과 결과

외부 URI 직접 참조는 원본 이동/권한 만료로 깨지므로 앱 소유 복사본을 사용한다.
Base64 글꼴을 모든 XHTML에 삽입하면 장별 메모리가 커지므로 내부 리소스 경로를 사용한다.
폰트 서버/광범위 저장소 권한/새 의존성은 필요하지 않다. 사용자 글꼴은 기존 전체 파일 백업 제외 정책을 따른다.
글꼴 모음(TTC), WOFF/WOFF2, 압축파일 자동 추출은 이번 범위에 포함하지 않는다.

검증은 파일 선택기의 별도 UID 권한 전달, 잘못된 파일 거부, 본문 실제 fontFamily/weight,
EPUB WebView의 실제 font-face loaded 상태, 설정 재개/독서 위치 보존을 포함한다.
실행 결과는 TASKS/WORKLOG에 기록하며 테스트 코드 추가만으로 PASS를 주장하지 않는다.
