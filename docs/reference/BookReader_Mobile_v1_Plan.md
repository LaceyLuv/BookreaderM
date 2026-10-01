# BookReader Mobile v1 — 제품·기술 계획서

> 상태: **Pre-Implementation / Pro 검증 대기**
>
> 목표: 이 문서를 Pro 또는 시니어 리뷰어가 한 번 검증한 뒤, 추가 기획 없이 바로 구현에 들어갈 수 있는 수준의 v1 기준 문서로 사용한다.
>
> 기준일: 2026-10-01

---

## 0. 한 줄 정의

**BookReader Mobile은 PC BookReader의 포팅판이 아니라, PC판에서 검증된 독서 기능과 실패 사례만 참고해 새로 만드는 모바일 전용 로컬 퍼스트 전자책 리더다.**

핵심 가치는 다음 네 가지다.

1. TXT / EPUB / ZIP을 빠르게 연다.
2. 앱을 닫았다 다시 열어도 정확한 위치에서 이어 읽는다.
3. 모바일 한 손 독서에 최적화한다.
4. 네트워크·계정 없이도 대부분의 기능이 동작한다.

---

# 1. 제품 방향

## 1.1 PC판과의 관계

BookReader Mobile은 기존 BookReader Desktop의 코드나 기술 구조를 의무적으로 재사용하지 않는다.

Desktop은 다음 역할을 한다.

- 이미 검증된 기능 아이디어 참고
- TXT / EPUB / ZIP 처리에서 발견한 실패 사례 참고
- 저장·복원·진행도 관련 버그 사례 참고
- UI/UX 중 모바일에서도 유효한 부분만 참고

즉:

```text
BookReader Desktop
        │
        ├─ 기능 참고
        ├─ UX 참고
        └─ 실패 사례 참고
                ↓
       BookReader Mobile
         신규 코드베이스
```

Desktop과 Mobile은 독립적으로 발전시킨다.

v1에서는 기존 Desktop 데이터 포맷과의 호환을 목표로 하지 않는다.

---

# 2. 모바일판 핵심 사용자 흐름

## 2.1 앱 실행

앱을 실행하면 **마지막 책으로 자동 진입하지 않는다.**

항상 서재가 먼저 열린다.

하단에는 마지막으로 읽었던 책을 표시하는 **이어읽기 바**를 제공한다.

```text
┌─────────────────────────────┐
│ BookReader                  │
│                             │
│ 최근 읽은 책                │
│                             │
│ ┌────┐  책 제목             │
│ │표지│  42%                 │
│ └────┘                      │
│                             │
│ 내 서재                     │
│ □ □ □                       │
│ □ □ □                       │
│                             │
├─────────────────────────────┤
│ ▣ 책 제목            42% 〉 │  ← 이어읽기 바
├─────────────────────────────┤
│  서재      검색      설정   │
└─────────────────────────────┘
```

이어읽기 바를 탭하면 마지막으로 저장된 **논리적 독서 위치**로 즉시 이동한다.

### 이어읽기 바 규칙

- 마지막으로 실제 읽었던 책 1권만 표시한다.
- 작은 표지, 제목, 진행률, 이동 화살표를 표시한다.
- 얇은 진행률 바를 사용할 수 있다.
- 서재 / 전체 검색 / 설정 화면에서는 유지한다.
- 실제 Reader 화면에서는 숨긴다.
- 다른 책을 읽으면 해당 책으로 자동 변경한다.
- 앱 재실행 후에도 유지한다.
- 마지막 책이 삭제되면 그 이전에 읽었던 유효한 책으로 대체한다.
- 대체할 책이 없으면 이어읽기 바를 표시하지 않는다.
- 완독한 책도 마지막 책이면 `완독` 상태로 표시 가능하다.

---

# 3. 하단 네비게이션

v1의 하단 탭은 3개만 둔다.

```text
서재 | 검색 | 설정
```

별도의 Home 탭은 만들지 않는다.

`서재 = 홈`이다.

---

# 4. v1 지원 포맷

## P0

- TXT
- EPUB
- ZIP 이미지 만화

## 이미지 포맷

ZIP 내부에서 최소 다음 이미지를 지원한다.

- JPG / JPEG
- PNG
- WebP

향후 필요하면 AVIF 등을 추가한다.

---

# 5. v1 기능 우선순위

## P0 — 출시 필수

### 공통

- 파일 가져오기
- 서재
- 최근 읽은 책
- 이어읽기 바
- 책 열기
- 진행도 자동 저장
- 정확한 마지막 위치 복원
- 책 삭제
- 제목 검색
- 기본 설정
- 다크 모드
- OLED 완전 검정 모드
- 앱 재시작 복원
- 비정상 종료 이후 최대한 안전한 복원
- Android Back 동작
- 시스템 안전영역 / 노치 대응

### TXT

- UTF-8
- UTF-16
- CP949 / EUC-KR 계열 처리 또는 신뢰 가능한 자동 감지
- 페이지 모드
- 세로 스크롤 모드
- 글자 크기
- 행간
- 좌우 여백
- 글꼴 선택
- 테마
- 책 내 검색
- 북마크
- 진행도 표시
- 설정 변경 후 독서 위치 유지

### EPUB

- EPUB2 / EPUB3의 일반적인 reflowable 문서
- 목차
- 이미지
- 기본 CSS 반영
- 글자 크기
- 테마
- 검색
- 북마크
- 진행도
- 마지막 위치 복원

### ZIP / 만화

- 이미지 자연 정렬
- 한 장 보기
- 세로 연속 보기
- 웹툰형 연속 보기
- 좌→우
- 우→좌
- 핀치 줌
- 더블 탭 줌
- 화면 맞춤
- 너비 맞춤
- 이미지 위치 복원

### 모바일 UX

- 좌/우 화면 탭으로 페이지 이동
- 중앙 탭으로 Reader UI 표시/숨김
- 좌우 스와이프
- 진행도 슬라이더
- 전체화면 독서
- 화면 꺼짐 방지 옵션
- 화면 회전 잠금 옵션
- 앱 내부 밝기 조절
- 다른 앱에서 `BookReader로 열기`

---

## P1 — v1.1 또는 v1 출시 후 우선 추가

- 볼륨키 페이지 이동
- 탭 영역 사용자 설정
- 사용자 글꼴 import
- 책별 Reader 설정 기억
- 독서 시간 통계
- 최근 읽은 책 가로 목록
- 책별 메모 / 주석
- 북마크 메모
- 완독 처리
- 읽지 않음 / 읽는 중 / 완독 필터
- 정렬: 최근 읽음 / 최근 추가 / 제목 / 진행률
- 파일 중복 감지
- 앱 내부 백업 / 복원
- 대용량 파일 인덱스 캐시 최적화

---

## P2 — 추후 검토

- iOS 앱
- PC ↔ Mobile 동기화
- 클라우드 동기화
- 계정 시스템
- TTS
- 고급 독서 통계
- 홈 화면 위젯
- OPDS
- AI 요약
- AI 질의
- EPUB 각주 팝업
- Ruby / 복잡한 세로쓰기
- Fixed Layout EPUB 고급 지원
- 고급 태그 / 컬렉션
- 서점 연동

---

# 6. 모바일에 가져오지 않을 PC 중심 기능

다음은 v1에서 제외한다.

- Drag & Drop
- Hover 기반 UI
- Windows 파일 경로 직접 노출
- 데스크톱 사이드 패널 중심 UI
- 마우스 전용 인터랙션
- 과도한 키보드 단축키
- FastAPI
- Python sidecar
- 기존 PC 백업 포맷 강제 호환
- 기존 PC 설정 파일 강제 호환
- 기존 PC JSON 저장 구조
- Windows 전용 기능

PC 키보드 단축키는 추후 블루투스 키보드 대응 수준으로 추가할 수 있다.

---

# 7. 권장 기술 구조

## 7.1 기본 결정

**Android 우선 + Kotlin Multiplatform + Compose Multiplatform + 로컬 SQLite 계열 DB**

권장 기본값:

```text
Language          Kotlin
UI                Compose Multiplatform
Architecture      Feature-based + shared core
Async             Kotlin Coroutines / Flow
Database          Room KMP 또는 동등한 SQLite abstraction
Android first     Yes
iOS               Later
Backend server    None
Account           None
Network required  None for core reading
```

현재 KMP의 Android/iOS 코어와 Compose Multiplatform의 Android/iOS 대상은 Stable 상태다.

Room / SQLite 계열도 Android 공식 KMP 경로를 사용할 수 있다.

### 공식 참고

- Kotlin Multiplatform platform stability  
  https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- Compose Multiplatform  
  https://kotlinlang.org/docs/multiplatform/compose-multiplatform.html
- Android Kotlin Multiplatform libraries  
  https://developer.android.com/kotlin/multiplatform
- Android Storage Access Framework  
  https://developer.android.com/guide/topics/providers/document-provider.html

---

# 8. 권장 프로젝트 구조

```text
bookreader-mobile/
│
├─ androidApp/
│  ├─ Android entry point
│  ├─ Android file intents
│  ├─ volume key integration
│  ├─ brightness / orientation
│  └─ platform permissions
│
├─ iosApp/                    # 초기에는 비어 있어도 됨
│
├─ shared/
│  ├─ core/
│  │  ├─ model/
│  │  ├─ database/
│  │  ├─ repository/
│  │  ├─ import/
│  │  └─ settings/
│  │
│  ├─ reader/
│  │  ├─ common/
│  │  ├─ txt/
│  │  ├─ epub/
│  │  └─ comic/
│  │
│  ├─ features/
│  │  ├─ library/
│  │  ├─ search/
│  │  ├─ bookmarks/
│  │  ├─ settings/
│  │  └─ statistics/
│  │
│  └─ ui/
│     ├─ theme/
│     ├─ components/
│     └─ navigation/
│
├─ tests/
│
├─ docs/
│
└─ README.md
```

Android 전용 API는 `androidApp` 또는 Android source set에 둔다.

공통 Reader / DB / 도메인 로직에서는 Android API에 직접 의존하지 않는다.

---

# 9. 데이터 모델

기존 PC판의 전체 JSON 덮어쓰기 방식을 사용하지 않는다.

모든 핵심 상태는 DB 트랜잭션 단위로 저장한다.

## 9.1 Book

```text
Book
----
id
format                 TXT | EPUB | COMIC
title
author?
fileUri
fileIdentity
coverRef?
addedAt
lastOpenedAt?
createdAt
updatedAt
status                 UNREAD | READING | COMPLETED
```

`fileIdentity`는 가능하면 파일 이름만이 아니라 크기 / 수정 시각 / fingerprint 등을 조합해 중복 식별과 파일 변경 감지에 활용한다.

---

## 9.2 ReadingProgress

```text
ReadingProgress
---------------
bookId
locatorType
locatorPayload
percentage
updatedAt
```

`locatorPayload`가 핵심이다.

페이지 번호 자체를 영구 위치 기준으로 저장하지 않는다.

---

## 9.3 Bookmark

```text
Bookmark
--------
id
bookId
locatorType
locatorPayload
label?
memo?
createdAt
updatedAt
```

---

## 9.4 ReaderSettings

```text
ReaderSettings
--------------
bookId?                 null이면 global default
readingMode
fontFamily
fontSize
lineHeight
letterSpacing
horizontalMargin
verticalMargin
theme
brightness?
tapZonePreset
swipeEnabled
volumeKeyEnabled
keepScreenOn
rotationLock
comicDirection
comicFitMode
```

---

# 10. 가장 중요한 원칙: 페이지 번호를 저장 위치로 사용하지 않는다

## 이유

다음이 바뀌면 페이지 수가 달라진다.

- 글자 크기
- 폰트
- 행간
- 자간
- 화면 크기
- 화면 회전
- 시스템 글꼴 배율
- 여백
- 기기 변경

따라서:

```text
page = 325
```

만 저장하면 위치 복원이 깨질 수 있다.

페이지 번호는 **표시용 파생 값**이다.

실제 저장 기준은 포맷별 논리적 locator다.

---

# 11. 포맷별 Locator

## 11.1 TXT

권장:

```text
TxtLocator
----------
byteOffset?             원본 파일 기준
charOffset
paragraphIndex?
inParagraphOffset?
contextHash?
```

최소 기준은 `charOffset`.

더 강한 복원력을 원하면 주변 텍스트 일부의 hash 또는 paragraph locator를 함께 저장한다.

파일이 변경되었을 경우:

1. exact offset 확인
2. 주변 context 확인
3. 실패하면 가장 가까운 안전 위치로 fallback
4. 사용자에게 파일 변경 사실을 알릴 수 있음

---

## 11.2 EPUB

가능하면 EPUB 문서 구조 기반 locator 사용.

예:

```text
EpubLocator
-----------
spineItemId
href
chapterIndex
elementPath / CFI-like locator
textOffset
progressWithinChapter
```

단순 `chapter + page`만 저장하지 않는다.

---

## 11.3 Comic / ZIP

```text
ComicLocator
------------
imageIndex
inImageScrollOffset
zoom?                   영구 저장 여부는 추후 판단
```

세로 연속 읽기라면 현재 이미지와 이미지 내부 offset을 저장한다.

---

# 12. 파일 가져오기

## 12.1 Android

다음 경로를 지원한다.

### A. 앱 내부 파일 선택

Android Storage Access Framework 사용.

```text
+ 버튼
  ↓
파일 선택
  ↓
TXT / EPUB / ZIP
  ↓
서재 등록
```

### B. 다른 앱에서 열기

예:

```text
Downloads / 브라우저 / 메신저
           ↓
       공유 / 열기
           ↓
       BookReader
```

앱이 지원 포맷을 수신해 서재 등록 또는 즉시 열기를 제공한다.

---

# 13. 파일 보관 정책

v1 기본안:

**원본 파일을 무조건 복사하지 말고, 플랫폼이 안정적인 지속 접근 권한을 제공하는 경우 URI/reference를 보존한다.**

단:

- 원본이 삭제됨
- 권한이 사라짐
- 외부 저장소 연결 해제

등의 상황을 반드시 처리한다.

향후 설정으로:

```text
파일 관리
○ 원본 위치 사용
○ BookReader 내부로 복사
```

를 추가할 수 있다.

MVP에서는 구현 난도와 안정성을 비교해 하나로 고정해도 된다.

---

# 14. 서재 화면

기본 구조:

```text
BookReader

[ 책 검색 🔍 ]

최근 읽음

┌──────┐ ┌──────┐
│ 표지 │ │ 표지 │
└──────┘ └──────┘

내 책

┌──────┐ ┌──────┐
│ 표지 │ │ 표지 │
└──────┘ └──────┘


━━━━━━━━━━━━━━━━━━━━
▣ 마지막 책          73% 〉
━━━━━━━━━━━━━━━━━━━━
  서재      검색      설정
```

### v1 최소 요소

- 책 표지
- 제목
- 진행률
- 최근 읽은 시점
- 포맷 표시가 필요한 경우 작은 badge
- 파일 추가 FAB 또는 상단 `+`

---

# 15. 전체 검색 탭

하단 `검색`은 **서재 전체 검색**이다.

검색 대상:

- 제목
- 작가
- 필요하면 파일 이름

책 내부 본문 검색과 분리한다.

```text
[ 제목 또는 작가 검색 ]

결과
- 책 A
- 책 B
```

---

# 16. Reader 기본 UI

## 16.1 몰입 상태

평상시에는 본문 외 UI를 최대한 숨긴다.

```text
┌────────────────────────┐
│                        │
│                        │
│        본문             │
│                        │
│                        │
└────────────────────────┘
```

## 16.2 중앙 탭

```text
┌────────────────────────┐
│ ←  책 제목           ⋮ │
├────────────────────────┤
│                        │
│        본문             │
│                        │
├────────────────────────┤
│ █████████░░░░░    64%  │
│                        │
│ 목차  검색  북마크  Aa │
└────────────────────────┘
```

### 하단 Reader action

- 목차
- 본문 검색
- 북마크
- Reader 설정 (`Aa`)

TXT에 목차가 없으면 감추거나 자동 목차 기능을 추후 지원한다.

---

# 17. 터치 조작

기본 탭 영역:

```text
┌────────┬────────────────┬────────┐
│  25%   │      50%       │  25%   │
│ 이전   │   UI 표시/숨김 │  다음   │
└────────┴────────────────┴────────┘
```

추가:

- 좌우 Swipe → 이전 / 다음
- 진행도 슬라이더
- 목차 이동
- 북마크 이동
- 볼륨키 이동(P1 또는 구현 쉬우면 P0)

향후 사용자 preset:

```text
[ 이전 ][ 메뉴 ][ 다음 ]
[ 메뉴 ][ 다음 ][ 다음 ]
[ 이전 ][ 다음 ][ 메뉴 ]
```

---

# 18. TXT Reader

TXT는 BookReader Mobile의 핵심 경쟁력으로 본다.

## 18.1 필수 목표

- 대용량 TXT를 열어도 UI가 장시간 멈추지 않는다.
- 첫 본문이 가능한 빨리 표시된다.
- 전체 파일 pagination이 끝날 때까지 사용자를 기다리게 하지 않는다.
- 긴 단일 문단도 누락 없이 표시한다.
- 한글 줄바꿈/문단을 안정적으로 처리한다.
- Reader 설정 변경 시 같은 문장 근처를 유지한다.
- 검색 결과 이동과 화면 위치가 어긋나지 않는다.

---

## 18.2 TXT 모드 A — 페이지

```text
━━━━━━━━━━━━━━━━━━━━
          본문
━━━━━━━━━━━━━━━━━━━━

365 / 1422
```

전체 책을 모든 페이지 View 객체로 미리 렌더링하지 않는다.

현재 위치 주변의 제한된 범위만 UI에 유지한다.

예:

```text
-2
-1
CURRENT
+1
+2
```

페이지 계산 결과는 캐시할 수 있으나 캐시 key에는 레이아웃에 영향을 주는 요소가 모두 포함되어야 한다.

예:

```text
font
fontSize
lineHeight
letterSpacing
viewportWidth
viewportHeight
margins
orientation
```

---

## 18.3 TXT 모드 B — 세로 스크롤

웹소설 스타일.

핵심 표시는 페이지보다는:

```text
64.8%
```

를 사용한다.

재진입 시 정확한 문장 근처로 복원해야 한다.

---

## 18.4 TXT 성능 전략

대용량 파일은 다음과 같이 단계적으로 처리한다.

```text
파일 열기
  ↓
encoding 판단
  ↓
빠른 초기 chunk 로드
  ↓
첫 화면 표시
  ↓
백그라운드 인덱싱
  ↓
검색 / 진행률 / 페이지 계산용 index 완성
```

사용자가 첫 화면을 보기 전에 전체 파일 분석을 강제하지 않는다.

---

# 19. EPUB Reader

v1은 일반적인 reflowable EPUB에 집중한다.

## P0

- EPUB2
- EPUB3
- Spine
- TOC
- 이미지
- 기본 CSS
- 글자 크기
- 테마
- 본문 검색
- 북마크
- 진행도
- 논리적 위치 복원

## v1 비목표

- 복잡한 fixed layout
- DRM
- 완벽한 CSS 호환
- 모든 SVG edge case
- 전문 출판 수준 세로쓰기
- 복잡한 interactive EPUB

보안상 HTML/EPUB 콘텐츠 렌더링 시 script 또는 위험한 외부 리소스 실행을 기본 허용하지 않는다.

---

# 20. Comic / ZIP Reader

## 20.1 자연 정렬

아래 파일은:

```text
1.jpg
2.jpg
10.jpg
```

문자열 정렬로:

```text
1
10
2
```

가 되면 안 된다.

반드시 natural sort를 적용한다.

---

## 20.2 읽기 모드

```text
○ 한 장 보기
○ 세로 연속
○ 웹툰
```

읽기 방향:

```text
○ 왼쪽 → 오른쪽
○ 오른쪽 → 왼쪽
```

지원 기능:

- pinch zoom
- double tap zoom
- 화면 맞춤
- 너비 맞춤
- 이미지 prefetch
- 현재 이미지 위치 저장

---

# 21. Reader 설정

## 기본

- 글꼴
- 글자 크기
- 행간
- 자간
- 좌우 여백
- 테마
- 페이지 / 스크롤
- 밝기

## 테마

최소:

- Light
- Sepia 또는 Soft
- Dark
- OLED Black (`#000000` 계열)

## 기기 제어

- 화면 꺼짐 방지
- 화면 회전 잠금
- 볼륨키 이동
- Swipe 사용
- Tap zone preset

---

# 22. 전역 설정 vs 책별 설정

기본 구조:

```text
Global Reader Settings
        ↓
새 책 기본값
```

책에서 설정을 바꾸면 선택적으로 book override를 저장한다.

예:

```text
소설 A
- 명조
- 19
- 페이지

소설 B
- 고딕
- 17
- 스크롤

만화 A
- 세로 연속
- RTL
```

P1로 미뤄도 되지만 DB 모델은 처음부터 book override를 지원하도록 만든다.

---

# 23. 상태 저장 원칙

## 절대 원칙

**진행도 업데이트 때문에 전체 서재 데이터를 통째로 다시 저장하지 않는다.**

예:

```text
UPDATE reading_progress
SET locator = ?, percentage = ?, updatedAt = ?
WHERE bookId = ?
```

같은 작은 단위의 transaction으로 저장한다.

### 저장 시점

- 의미 있는 위치 변화
- 일정 debounce 이후
- 앱 background 전환
- Reader 종료
- 앱 process 종료 직전 callback이 가능한 경우

단, 마지막 lifecycle callback만 믿지 않는다.

주기적 debounce save가 기본이다.

---

# 24. 데이터 안전성

기존 Desktop에서 경험한 문제를 재발시키지 않도록 다음 원칙을 적용한다.

- atomic DB transaction
- 전체 JSON overwrite 금지
- 복원 작업은 staging 후 commit
- 백업 파일 validation
- 파일 변경 감지
- book delete 시 관련 progress/bookmark FK cleanup
- import 중 실패하면 incomplete book record를 남기지 않음
- DB migration 테스트
- 앱 강제 종료 중 저장 안전성 테스트

---

# 25. 성능 목표

정확한 수치는 초기 profiling 후 조정할 수 있으나 v1 목표는 다음으로 둔다.

## 앱

- 일반적인 기기에서 앱 시작 후 서재 UI가 빠르게 표시될 것
- DB 초기화 때문에 빈 화면이 오래 유지되지 않을 것

## TXT

- 수 MB급 TXT: 거의 즉시 첫 화면
- 대용량 TXT: 전체 인덱싱보다 첫 화면 표시를 우선
- 스크롤 중 parser가 UI thread를 장시간 block하지 않을 것

## Comic

- 현재 이미지 주변만 prefetch
- ZIP 전체 이미지를 bitmap으로 동시에 decode하지 않음
- OOM 방지

## EPUB

- chapter 단위 lazy loading 우선
- remote resource 때문에 독서 UI가 무한 대기하지 않음

---

# 26. 접근성

v1부터 고려한다.

- 충분한 터치 영역
- TalkBack label
- 시스템 text scaling 충돌 검토
- 고대비
- 화면 reader에서 불필요한 UI announcement 방지
- 애니메이션 감소 설정 존중 가능성 검토

---

# 27. 개인정보 / 네트워크 원칙

v1 기본:

- 로그인 없음
- 광고 없음
- 독서 기록 서버 전송 없음
- analytics는 기본 미포함 또는 명확한 opt-in
- 책 내용 업로드 없음
- 로컬 독서 기능은 offline 완전 동작

향후 AI/Sync 기능을 추가한다면 별도 동의와 명확한 데이터 정책을 둔다.

---

# 28. 백업 / 복원

v1.0에서 제외 가능하지만 DB 구조는 고려한다.

향후 백업 패키지 예:

```text
bookreader-backup/
├ manifest.json
├ database/
├ covers/
└ optional-files/
```

원본 책 파일을 백업에 포함할지 여부는 옵션으로 둔다.

복원은:

```text
검증
↓
staging
↓
기존 데이터 안전 백업
↓
transactional replace / merge
↓
재검증
```

순으로 설계한다.

---

# 29. Desktop 동기화는 v1 비목표

PC와 Mobile의 저장 구조를 억지로 같게 만들지 않는다.

향후 동기화가 필요하면 별도의 공통 Sync 모델을 정의한다.

예:

```text
SyncBookIdentity
SyncLocator
SyncProgress
SyncBookmark
SyncTimestamp
```

동기화 설계는 앱 내부 DB 모델과 분리한다.

---

# 30. 아키텍처 원칙

## 30.1 UI와 Reader core 분리

잘못된 예:

```text
TXT Screen
 └─ Android Context
 └─ 직접 DB
 └─ 직접 파일 parser
 └─ 직접 progress write
```

권장:

```text
TXT Screen
   ↓
Reader ViewModel / Presenter
   ↓
Reader Use Cases
   ↓
TXT Engine
   ↓
Repositories
   ↓
DB / File provider
```

---

## 30.2 포맷별 Reader 분리

공통 `Book`은 최소 정보만 공유한다.

```text
Book
 ├─ TextBook
 ├─ EpubBook
 └─ ComicBook
```

공통:

- title
- cover
- progress
- lastRead
- bookmark API

포맷 특화:

- TXT locator / pagination
- EPUB spine / DOM locator
- Comic image index / scroll offset

하나의 억지 페이지 모델에 세 포맷을 넣지 않는다.

---

# 31. 오류 UX

사용자에게 stack trace를 노출하지 않는다.

예:

### 파일이 사라짐

```text
파일을 찾을 수 없습니다.
원본 파일이 이동되거나 삭제된 것 같습니다.

[파일 다시 연결] [서재에서 제거]
```

### 지원하지 않는 EPUB

```text
이 EPUB의 일부 형식은 아직 지원되지 않습니다.
가능한 내용만 열어보거나 파일을 닫을 수 있습니다.

[열기 시도] [닫기]
```

### 손상 ZIP

```text
압축 파일을 읽을 수 없습니다.
파일이 손상되었거나 지원하지 않는 압축 형식일 수 있습니다.
```

---

# 32. 개발 단계

## Phase 0 — 프로젝트 기반

완료 조건:

- 신규 repository
- Android 앱 실행
- Compose navigation
- DB
- 기본 theme
- test framework
- CI 기본 구성
- architecture skeleton

---

## Phase 1 — TXT MVP

구현:

```text
TXT 선택
↓
서재 등록
↓
책 열기
↓
본문 표시
↓
위치 저장
↓
앱 종료
↓
앱 재실행
↓
이어읽기 바
↓
정확한 위치 복원
```

### Phase 1 DoD

- UTF-8 TXT 정상
- 큰 TXT 기본 테스트
- 마지막 위치가 재실행 후 복원
- 글자 크기 변경 후 같은 문장 근처 유지
- 앱 kill 후 위치 손실 최소화
- 파일 삭제 시 crash 없음

---

## Phase 2 — TXT 완성

- 페이지 모드
- 스크롤 모드
- font
- font size
- line height
- letter spacing
- margin
- themes
- search
- bookmarks
- encoding
- pagination cache
- large TXT indexing

---

## Phase 3 — 모바일 UX

- reader overlay
- tap zones
- swipe
- fullscreen
- brightness
- rotation lock
- keep screen on
- volume keys
- safe area
- back navigation

---

## Phase 4 — EPUB

- import
- parse
- TOC
- rendering
- search
- bookmark
- progress
- position restore
- security hardening

---

## Phase 5 — Comic / ZIP

- natural sort
- image lazy decode
- single page
- vertical
- webtoon
- LTR/RTL
- zoom
- progress restore
- OOM testing

---

## Phase 6 — 품질 / 베타

- DB migration
- backup / restore 여부 결정
- crash recovery
- battery profiling
- memory profiling
- accessibility
- Android 버전 matrix
- Play Store 준비

---

## Phase 7 — iOS

Android에서 domain / reader core가 안정된 뒤 진행한다.

iOS 때문에 Android v1 개발을 지연시키지 않는다.

---

# 33. 테스트 전략

## 33.1 Unit

- natural sort
- encoding detection
- locator serialize/deserialize
- progress calculation
- TXT chunk boundaries
- long paragraph
- file fingerprint
- DB repository
- migration

---

## 33.2 Integration

- import → DB → reader
- reader → progress save → restore
- settings 변경 → locator 유지
- delete → related row cleanup
- file missing
- file changed
- corrupted file

---

## 33.3 UI

- 서재
- 이어읽기
- Reader overlay
- tap zones
- search
- bookmark
- settings
- orientation change
- Android back

---

## 33.4 강제 종료 테스트

매우 중요.

각 포맷에서:

```text
읽기
↓
진행
↓
앱 강제 종료
↓
재실행
↓
이어읽기
```

위치가 허용 범위 내에서 복원되어야 한다.

---

# 34. TXT 회귀 테스트 필수 케이스

기존 Desktop에서 문제 가능성이 있었던 유형을 Mobile 초기부터 테스트한다.

1. 100KB 일반 TXT
2. 5MB TXT
3. 50MB 이상 TXT
4. 매우 긴 단일 문단
5. CRLF
6. LF
7. 혼합 line ending
8. UTF-8 BOM
9. UTF-16 LE/BE
10. CP949 한국어
11. 이모지
12. 특수문자
13. 빈 줄 다수
14. 글자 크기 변경
15. 자간 변경
16. 회전
17. 검색 결과 이동
18. 북마크 위치 복원
19. 앱 kill
20. 원본 파일 변경

---

# 35. Comic 회귀 테스트

1. 1.jpg / 2.jpg / 10.jpg
2. 001 / 002 / 010
3. 한글 파일명
4. 일본어 파일명
5. 대형 이미지
6. 세로로 매우 긴 이미지
7. 1,000장 이상 ZIP
8. corrupted image 1개 포함
9. nested directory
10. RTL
11. pinch zoom 중 페이지 변경
12. app kill 후 복원

---

# 36. EPUB 회귀 테스트

1. EPUB2
2. EPUB3
3. 이미지 포함
4. 내부 CSS
5. 긴 chapter
6. 짧은 chapter 다수
7. TOC 중첩
8. 한글
9. 일본어
10. 외부 링크
11. script 포함 파일
12. 깨진 resource
13. app kill
14. font/theme 변경 후 locator 복원

---

# 37. v1 출시 차단 조건

아래가 하나라도 존재하면 Release Candidate로 올리지 않는다.

## P0 blocker

- 책 내용 유실
- 진행도 반복 유실
- 다른 책 진행도로 덮어씀
- DB corruption
- import 중 기존 서재 손상
- 긴 TXT 일부가 표시되지 않음
- 검색 위치가 완전히 잘못된 위치로 이동
- 페이지 설정 변경 후 독서 위치가 심각하게 점프
- ZIP 대형 이미지에서 반복 OOM
- EPUB 콘텐츠가 임의 script 실행 가능
- 파일 하나의 오류 때문에 앱 전체가 실행 불가
- 앱 재실행 시 서재가 빈 상태로 잘못 보임

---

# 38. 구현 중 절대 피할 것

- 책 전체를 하나의 거대한 UI text node에 넣기
- 전체 TXT 페이지를 앱 시작 시 모두 렌더링
- ZIP 모든 이미지를 동시에 decode
- progress save 시 전체 DB/JSON 재작성
- UI thread에서 대용량 parser 실행
- page number를 canonical locator로 사용
- 포맷 3개를 하나의 pagination 로직에 강제 통합
- Android Context를 shared core 깊숙이 전달
- Reader 내부에서 직접 DB write 남발
- file path 문자열만을 영구 식별자로 사용
- 실패를 성공으로 간주하고 빈 값을 저장
- lifecycle `onStop` 1회에만 진행도 저장 의존

---

# 39. 초기 UI 디자인 원칙

- 독서 중 UI 최소화
- Material 기본값을 그대로 쓴 느낌은 줄이되 과도한 custom UI는 피한다.
- 애니메이션은 짧고 읽기를 방해하지 않는다.
- Bottom Sheet 적극 활용
- 설정 / 목차 / 북마크는 모바일에서 side panel보다 Bottom Sheet 우선
- 검색은 필요하면 full-screen
- 주요 버튼은 한손 범위 고려
- Reader는 콘텐츠가 최우선

---

# 40. 초기 화면 목록

v1에서 필요한 화면:

```text
Splash / Initialization
Library
Global Search
Settings
Import
TXT Reader
EPUB Reader
Comic Reader
Reader Search
TOC
Bookmarks
Reader Settings
Book Details / Actions
Error / Relink
```

목차, 북마크, Reader Settings는 독립 화면이 아니라 Bottom Sheet로 구현 가능하다.

---

# 41. 구현 전 남아 있는 선택 사항

Pro 검증 시 아래를 확정한다.

## A. KMP UI 공유 범위

기본 제안:

**Android v1에서는 Compose Multiplatform shared UI 사용.  
Android-specific 기능만 platform layer로 격리.**

iOS에서 UI 공유를 계속할지 추후 재평가.

## B. Database

기본 제안:

**Room KMP**

조건:

- 현재 target과 migration tooling이 충분한지 확인
- 문제가 있으면 SQLDelight 등 대체 검토

## C. EPUB engine

직접 전부 구현하지 않는다.

검토 기준:

- 라이선스
- EPUB2/3
- Kotlin/KMP 적합성
- HTML sanitization
- locator 지원
- 검색
- TOC
- 유지보수 상태

## D. TXT encoding detector

검토 기준:

- Korean CP949 정확도
- BOM 처리
- false positive
- 라이선스
- KMP 지원성

---

# 42. v1 Definition of Done

다음이 모두 충족되어야 한다.

### Library

- TXT / EPUB / ZIP import 가능
- 앱 재실행 후 서재 유지
- 파일 삭제 / relink 처리
- 마지막 읽은 책 추적
- 이어읽기 바 정상

### Reader

- 각 포맷 읽기 가능
- logical locator 저장
- app restart 후 위치 복원
- progress 표시
- reader overlay
- search
- bookmark
- basic settings

### Stability

- 대용량 TXT에서 내용 누락 없음
- 대형 ZIP에서 OOM 방어
- malformed EPUB 하나가 앱 전체를 죽이지 않음
- DB migration test 존재
- force-kill restore test 통과

### Privacy

- core reading offline
- 사용자 책 내용 임의 업로드 없음
- 계정 필수 아님

---

# 43. 첫 구현 PR 제안

## PR 1 — `bootstrap-mobile-foundation`

범위:

- KMP project
- Android application module
- Compose root
- navigation
- theme
- Room KMP skeleton
- Book / ReadingProgress entities
- repository interface
- Library empty screen
- basic CI
- unit test smoke test

**Reader 구현은 넣지 않는다.**

---

## PR 2 — `txt-import-and-library`

- SAF TXT import
- file metadata
- Book insert
- Library cards
- book delete
- file missing detection

---

## PR 3 — `txt-reader-core`

- streaming / chunk reader
- encoding abstraction
- TxtLocator
- first render
- progress save
- restore

---

## PR 4 — `continue-reading-bar`

- lastOpenedAt
- last read book
- persistent continue bar
- tap to resume
- delete fallback

---

## PR 5 — `txt-reader-mobile-controls`

- overlay
- tap zones
- swipe
- progress slider
- fullscreen
- Android Back

이후 page mode / search / bookmarks를 순차 추가한다.

---

# 44. Pro 검증 요청 프롬프트

아래 내용을 이 문서와 함께 그대로 Pro 리뷰에 사용한다.

```text
당신은 시니어 모바일 앱 아키텍트이자 전자책 리더 엔진 리뷰어다.

첨부한 `BookReader_Mobile_v1_Plan.md`는 새로 만들 모바일 전자책 리더의 구현 전 계획서다.

중요 전제:
- 기존 PC BookReader 코드를 포팅하는 프로젝트가 아니다.
- Desktop에서는 기능 아이디어와 과거 실패 사례만 참고한다.
- Android를 먼저 만든다.
- 핵심 포맷은 TXT, EPUB, ZIP 이미지 만화다.
- local-first / offline-first다.
- 마지막 책 자동 실행이 아니라 서재 하단 `이어읽기 바`를 탭하여 즉시 복귀한다.
- canonical progress는 page number가 아니라 포맷별 logical locator다.
- 기술 기본안은 Kotlin Multiplatform + Compose Multiplatform + SQLite/Room KMP다.

다음을 순서대로 검증하라.

1. 제품 설계에서 빠진 필수 모바일 전자책 UX
2. 모바일에서 불필요하거나 과한 기능
3. KMP/Compose/DB 선택이 현재 시점에 적절한지
4. Android-first 후 iOS 확장에 발목을 잡을 구조가 있는지
5. TXT 대용량 처리 / pagination / search / locator 설계 결함
6. EPUB parser/rendering/locator/security 위험
7. ZIP comic 메모리/OOM/정렬/복원 위험
8. DB transaction / progress save / force-kill 복원 문제
9. Android SAF 및 외부 파일 수명 문제
10. 테스트에서 빠진 P0 회귀 조건
11. 출시를 막을 가능성이 높은 문제를 P0/P1/P2로 분류
12. 실제 구현 전에 반드시 문서에 고쳐야 할 항목

규칙:
- 칭찬 위주 리뷰 금지.
- 가능성만 말하지 말고 구체적인 실패 시나리오를 적어라.
- P0는 재현 조건과 최소 수정 설계를 함께 적어라.
- 불필요한 대규모 재작성은 제안하지 마라.
- 확인이 필요한 최신 라이브러리/API 정보는 공식 문서를 우선 검증하라.
- 최종적으로 `GO`, `GO WITH FIXES`, `NO-GO` 중 하나로 판정하라.
- `GO WITH FIXES` 또는 `NO-GO`라면 구현 시작 전에 반영할 수정 목록을 체크박스로 제공하라.
```

---

# 45. Pro 검증 통과 후 Codex 구현 시작 프롬프트

Pro 리뷰의 필수 수정 사항을 이 문서에 반영한 후 다음 프롬프트로 구현을 시작한다.

```text
`BookReader_Mobile_v1_Plan.md`를 이 프로젝트의 제품 및 아키텍처 기준 문서로 사용하라.

목표는 Android-first BookReader Mobile을 새 코드베이스로 구현하는 것이다.
기존 Desktop BookReader의 기술 구조나 코드를 그대로 복제하지 마라.

작업 원칙:
1. 계획서의 P0와 architecture constraints를 최우선으로 지켜라.
2. canonical reading position에 page number를 사용하지 마라.
3. 전체 JSON overwrite 저장 구조를 만들지 마라.
4. 대용량 파일 parser 또는 image decode로 UI thread를 막지 마라.
5. TXT / EPUB / Comic의 locator와 reader engine을 분리하라.
6. Android-specific API는 platform layer로 격리하라.
7. 각 PR은 테스트 가능한 작은 범위로 유지하라.
8. 구현하면서 계획서와 충돌하는 사항이 생기면 임의로 우회하지 말고 근거와 대안을 기록하라.
9. 기존 기능을 넓히기보다 먼저 안정적인 P0 경로를 완성하라.

첫 작업:
`PR 1 — bootstrap-mobile-foundation`만 구현하라.

PR 1 범위:
- KMP project bootstrap
- Android app module
- Compose root/navigation
- theme
- Room KMP 또는 Pro 검증 후 확정된 DB skeleton
- Book / ReadingProgress model
- repository interfaces
- empty Library screen
- basic CI
- smoke/unit test

하지 말 것:
- EPUB 구현
- ZIP reader 구현
- TXT renderer 구현
- sync
- cloud
- account
- AI
- 과도한 디자인 polish

완료 후 다음을 보고하라.
- 생성/변경 파일
- 아키텍처 설명
- 테스트 결과
- 남은 위험
- PR 2를 시작하기 전에 확인할 항목
```

---

# 46. 구현 직전 최종 체크리스트

Pro 검증을 반영한 뒤 모두 체크하고 시작한다.

- [ ] Android-first 원칙 확정
- [ ] KMP / Compose 사용 확정
- [ ] DB 기술 확정
- [ ] TXT locator 확정
- [ ] EPUB locator 전략 확정
- [ ] Comic locator 확정
- [ ] Android file import 정책 확정
- [ ] 파일을 원본 URI로 읽을지 앱 내부 복사할지 확정
- [ ] progress save debounce 정책 확정
- [ ] 대용량 TXT first-render 전략 확정
- [ ] EPUB engine/library 확정
- [ ] encoding detector 확정
- [ ] force-kill restore test 정의
- [ ] database migration test 정의
- [ ] v1 P0 범위 동결
- [ ] P1/P2 기능이 PR 1~P0 완료 전에 침범하지 않도록 제한
- [ ] Pro 판정이 GO 또는 GO WITH FIXES이고 필수 수정 반영 완료

---

# 47. 최종 제품 원칙

BookReader Mobile의 품질 판단 기준은 기능 개수가 아니다.

다음이 먼저다.

> **파일을 넣는다 → 바로 읽힌다 → 편하게 넘긴다 → 앱을 닫는다 → 다시 열어 정확히 이어 읽는다.**

이 경로가 빠르고 안정적이면 v1은 성공이다.

그 이후에 EPUB 고급 기능, 통계, 동기화, iOS, AI 기능을 추가한다.
