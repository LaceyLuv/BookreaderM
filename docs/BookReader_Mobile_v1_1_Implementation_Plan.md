# BookReader Mobile v1.1 — 구현 기준 계획서

> 문서 버전: 1.1 / 기준일: 2026-10-01  
> **v1.1은 계획서의 개정 번호다. 앱의 출시 목표는 여전히 Android v1.0이다.**  
> 검토 판정: **GO WITH FIXES — 이 개정안 기준으로 단계별 구현 착수 가능**  
> 검증 범위: 원문 계획서 검토와 공식 기술 문서 조사. 저장소 코드, Android 빌드, 실기기, 성능 및 보안 테스트는 이번 문서 작업에서 실행하지 않았다.  
> 원문: `docs/reference/BookReader_Mobile_v1_Plan.md`  
> 이 문서는 아래 변경표에 명시한 수정·추가를 포함한 **권장 실행안**이다. 원문에서 이미 합의된 사항과 이번 리뷰의 제안을 구분한다.

## 읽는 순서와 문서 권한

`AGENTS.md`의 작업 규칙 → 이 문서의 제품·기술 계약 → `docs/TASKS.md`의 현재 작업 → 해당 ADR → 코드/테스트 순서로 확인한다.
기존 저장소의 상위 AGENTS 규칙을 덮어쓰지 않는다. 이 문서를 채택하면 원문은 참고 이력으로만 사용한다.
외부 공식 문서는 API와 호환성을 확인하는 근거이며, 이 문서에 정한 제품 범위를 자동으로 확대하는 근거가 아니다.
수치·예산·지원 한도 중 `[설계 목표]`는 이번에 제안한 초기 기준이지 실측 성능이나 플랫폼의 보장값이 아니다.

## 개정 핵심표

| 영역 | 원문 | 이 개정안의 명시적 수정/추가 |
|---|---|---|
| 유지 | Android 우선, 신규 코드베이스, 로컬 퍼스트, TXT/EPUB/ZIP | 그대로 유지 |
| 유지 | 마지막 책 자동 진입 금지, 하단 이어읽기 바 | 콜드 런치/웜 복귀/외부 열기별 동작만 구체화 |
| UI 공유 | Compose Multiplatform shared UI 기본 | **Android v1 UI는 Jetpack Compose, KMP는 도메인·모델·DB·공유 가능한 텍스트 로직에 한정** |
| 파일 보관 | 가능한 경우 원본 URI 지속 접근 | **v1은 앱 내부 관리 복사본을 사용**, 원본 URI는 출처 메타데이터 |
| 진행도 | 논리 locator + debounce | 해석 버전, 조회 성공 게이트, 세션 세대/순번, 주기 저장 + 정지 후 저장 |
| EPUB | 라이브러리 검토 예정 | Readium Kotlin의 안정 경로 + Android 어댑터를 기본 선택, 초기에 통합/보안 검증 |
| TXT | charOffset | 정규화 텍스트의 UTF-16 코드 단위 offset으로 정의 |
| 범위 | 몇몇 P0/P1가 화면/단계에서 충돌 | 필수/후속/내부 알파/최종 v1을 분리 |
| 추가 P0 | 중복 감지·책별 설정이 P1 | 정확히 같은 파일 중복 방지, 책별 **모드/방향**만 P0로 승격 |
| 추가 P0 | 인코딩 자동 감지 중심 | 수동 인코딩 선택·미리보기, 읽기 실패 복구, 탐색 점프의 일회성 돌아가기 |
| 추가 P0 | 백업은 나중 | 앱 자동 백업 제외 정책, 삭제 시 데이터 손실 안내 |
| 구현 순서 | EPUB 검증이 Phase 4 | 부트스트랩 다음에 EPUB·인코딩 기술 검증을 선행 |
| 작업 관리 | 첫 PR 프롬프트만 있음 | 작업 ID, 선행 조건, 테스트, 산출물, 계속하기/감사 프롬프트 추가 |

---

# 0. 한 줄 정의

**PC판을 포팅하지 않고, Android에서 파일을 안전하게 가져와 빠르게 읽고 정확히 이어 읽는 로컬 퍼스트 전자책 리더.**

최종 Android v1.0의 포맷 범위는 TXT, 일반 reflowable EPUB2/3, ZIP/CBZ 이미지 만화다.
TXT만 완성된 빌드는 **내부 알파**이지 세 포맷을 지원하는 v1.0 출시 완료가 아니다.

# 1. 제품 방향

## 1.1 PC판과의 관계

Desktop의 기능 아이디어와 실패 사례만 참고한다. FastAPI, Python sidecar, 전체 JSON 저장, Windows 경로, PC 백업 형식을 복제하지 않는다.
기존 PC 저장소에 작업하더라도 PC 코드/설정/CI를 무단 변경하지 않는다. 기본 배치는 신규 모바일 저장소다.

## 1.2 성공 기준

- 파일 가져오기 실패가 기존 서재/진행도를 손상시키지 않는다.
- 같은 원문 해석에서 화면 설정을 바꾸어도 저장한 문장을 찾을 수 있다.
- 앱 프로세스가 종료되어도 마지막 **커밋 완료** 위치가 보존된다.
- 손상된 책 한 권 때문에 앱 전체가 실행되지 않는 일이 없다.
- 네트워크 없이 관리 복사본의 독서·검색·북마크가 동작한다.

# 2. 모바일판 핵심 사용자 흐름

## 2.1 실행 경로별 규칙

| 진입 경로 | 동작 |
|---|---|
| 런처에서 새 프로세스로 시작 | 항상 서재 → 이어읽기 바. 저장된 Reader 화면으로 자동 진입하지 않음 |
| 백그라운드에서 살아 있는 Reader로 복귀 | 기존 Reader를 유지. 홈으로 튕기지 않음 |
| 회전·창 크기 변경에 따른 Activity 재생성 | 같은 Reader와 locator 유지 |
| 프로세스 종료 뒤 task 상태 복원 | 서재로 시작. 오래된 navigation back stack으로 Reader 자동 복원 금지 |
| 외부 `ACTION_VIEW`로 책 열기 | 사용자의 명시적 열기 요청이므로 안전한 import 후 해당 책 진입 |
| 외부 공유 `ACTION_SEND` | import 확인 → 추가 완료 → `읽기` 버튼. 기존 Reader를 임의 교체하지 않음 |

외부 파일을 다운로드해야 하는 문서 제공자까지 네트워크 없는 import를 보장하지는 않는다.
오프라인 보장은 **성공적으로 가져온 관리 복사본**에 적용한다.

## 2.2 이어읽기 바

서재/검색/설정의 하단 탭 바로 위에 1개만 표시하고 Reader에서는 감춘다.
표지, 제목, 진행률 또는 `계산 중`, 이동 버튼을 제공한다.

`lastOpenedAt`는 열기 시도 기록이고, `lastReadAt`는 **본문 표시와 위치 복원이 성공한 읽기 세션**의 기록이다.
이어읽기는 `READY && lastReadAt != null`인 책을 최근 실제 읽기 순서로 선택한다.
단순 탭·import·실패한 열기·표지 미리보기로 마지막 책을 바꾸지 않는다.
같은 시각의 동률을 피하려고 DB 트랜잭션에서 증가시키는 `readOrder`를 함께 쓴다.
삭제/접근 불가 책은 건너뛰고 이전 유효 책으로 대체한다. 후보가 없으면 바를 숨긴다.
앱 시작에 모든 파일을 해시하지 않는다. 실제 열기 시 검증 실패한 책만 제외한다.

# 3. 하단 네비게이션

`서재 | 검색 | 설정` 3개를 유지한다. 별도 Home 없음.
검색 탭은 제목/작가/원본 파일명 검색이며, 책 안의 본문 검색과 구분한다.
기본 Back 흐름은 `열린 팝업/시트 → 검색/목차 → Reader → 서재 → 시스템 Back`이다.
단순 Reader overlay는 Back으로 숨길 수 있으나 끝없는 Back 단계가 생기지 않게 한다.

# 4. v1 지원 포맷

| 포맷 | v1 필수 범위 | 명시적 제외 |
|---|---|---|
| TXT | UTF-8, UTF-16 LE/BE, CP949, EUC-KR; 페이지/스크롤 | PDF처럼 해석, 임의 인코딩 자동 보장 |
| EPUB | DRM 없는 EPUB2/3 reflowable; 목차/이미지/CSS/검색/북마크 | DRM, interactive, 전문 세로쓰기, 고급 fixed layout |
| ZIP/CBZ | 정적 JPEG/PNG/WebP; 하위 폴더; 자연 정렬 | RAR/CBR/7z, 암호화 ZIP, 재귀 압축 해제 |
| 이미지 | EXIF 방향 보정 후 표시 좌표를 기준으로 복원 | 애니메이션은 정지 대표 프레임만 또는 명확한 미지원 안내 |

CBZ는 ZIP 이미지 만화의 확장자 별칭이며 별도 엔진을 만들지 않는다.
확장자/MIME은 후보 판정에만 사용한다. 실제 파일 구조가 맞는지 확인한다.
지원하지 않는 EPUB 프로필은 원본을 보관한 채 이유를 안내하고 Reader 진입을 차단한다.
검증되지 않은 위험한 콘텐츠에 `무조건 열기` 버튼을 제공하지 않는다.

# 5. v1 기능 우선순위

## 5.1 범위 동결

**P0: 최종 v1.0 필수**
- 서재, 단일/외부 파일 가져오기, 삭제, 제목/작가 검색, 이어읽기.
- 세 포맷의 읽기, locator 기반 진행도, 북마크, 앱 재실행 복원.
- TXT/EPUB 본문 검색. **이미지 만화 OCR 본문 검색은 제외**.
- TXT 페이지/스크롤, 글자 크기·행간·자간·여백·기본 글꼴·테마.
- EPUB 목차, 이미지, 기본 출판 CSS와 사용자 크기/테마 설정.
- 만화 한 장/연속, LTR/RTL, 확대/축소, 화면/너비 맞춤.
- 모바일 overlay, 탭/스와이프, 진행도 이동, 안전영역, Back.
- 화면 꺼짐 방지, 창 단위 밝기, 지원되는 화면에서의 회전 잠금.
- 인코딩 수동 선택, 정확한 파일 중복 방지, import 취소/실패 복구.
- 책별 읽기 모드 및 만화 방향 기억, 보안/접근성 최소 기준, 데이터 저장 실패 안내, 탐색 점프의 일회성 돌아가기.

**P1: v1.0 이후**
볼륨키, 사용자 탭 영역, 사용자 글꼴 import, 모든 타이포그래피의 책별 override,
최근 읽음 가로 캐러셀, 통계, 메모/주석/북마크 메모, 수동 완독 및 상태 필터,
다양한 정렬, 유사 파일 중복 감지, 앱 백업/복원, 다중 파일 일괄 import 고도화.

**P2: 유지**
iOS, 동기화, 계정, TTS, AI, OPDS, 위젯, 고급 EPUB, 서점 연동.

## 5.2 원문 충돌 해소

- 서재 그림의 최근 읽음 캐러셀은 P1. P0은 그리드 + 이어읽기 바로 충분하다.
- 볼륨키를 “쉬우면 P0”로 올리지 않는다.
- 웹툰은 별도 세 번째 렌더러가 아니라 **연속 보기 + 너비 맞춤 + 간격 0** 프리셋이다.
- 기본 정렬은 최근 추가 내림차순. 정렬 UI는 P1.
- 수동 완독은 P1. P0에서는 진행률과 끝 도달 표시를 제공하되 진행률 100%만으로 완독 DB 상태를 자동 확정하지 않는다.
- 이미지 만화에도 북마크를 제공하되 본문 검색/글자 설정은 숨긴다.

# 6. 모바일에 가져오지 않을 PC 중심 기능

원문 제외 목록을 유지한다: Drag & Drop, Hover, Windows 경로/기능,
데스크톱 사이드 패널, 마우스 전용 조작, 과도한 단축키, PC 저장/백업 포맷 강제 호환.
v1에서 시스템 파일 전체 접근 권한이나 시스템 설정 변경 권한을 편의를 위해 요구하지 않는다.

# 7. 권장 기술 구조

## 7.1 이번 리뷰의 실행 기본안

| 영역 | 결정 |
|---|---|
| 플랫폼 | Android 우선, `minSdk 26` [설계 선택] |
| UI | **Android Jetpack Compose** |
| 공유 영역 | Kotlin Multiplatform: 모델/locator/도메인/DB와 공유 가능한 텍스트 처리 |
| 비동기 | Coroutines / Flow; 구조화된 취소와 작업 소유권 |
| DB | Room KMP + bundled SQLite, 스키마 export 및 migration test |
| TXT | 자체 bounded text engine; Android decoder/파일 API/텍스트 측정은 어댑터 |
| EPUB | Readium Kotlin **기존 Android Navigator 경로**, 독립 어댑터 |
| 만화 | 별도 ZIP/index/이미지 표시 엔진; 제한된 decode/cache |
| 서버/계정 | 없음 |
| 의존성 주입 | 처음에는 명시적 constructor injection. DI 프레임워크는 필요 증명 전 추가하지 않음 |

**변경 이유:** 원문 KMP 선택 자체가 잘못된 것은 아니다. Android/iOS의 KMP 안정성은 공식 문서로 확인된다.[S01]
하지만 Readium의 기본 시각 Navigator는 Android Fragment이며 새 Compose 기반 경로는 alpha로 안내된다.[S04][S05]
따라서 UI 전부의 iOS 공유를 v1 완료 조건으로 삼지 않는다. iOS 때 실제 재사용 범위를 평가한다.
Android UI/텍스트 측정 API를 `commonMain`에 숨겨 넣는 대신 플랫폼 경계를 명시한다.

## 7.2 라이브러리 버전 계약

2026-10-01 확인 기준:
- Readium 공식 3.4.0 문서와 Room 3.0.1 안정 릴리스를 **초기 검증 후보**로 삼는다.[S03][S04]
- 확인한 Room KMP 설정 예시에는 `3.1.0-alpha01`이 들어 있다. 공식 예시라는 이유로 alpha를 그대로 채택하지 않는다.[S02]
- Kotlin/AGP/Gradle/JDK/Compose BOM/KSP/Room/SQLite/Readium 전체 조합은 **아직 빌드 검증하지 않았다**.
- M00에서 호환되는 안정 조합을 실제 해석·빌드하고 `libs.versions.toml`, wrapper, `docs/DEPENDENCIES.md`에 정확히 고정한다.
- Readium 3.4.0 문서의 SDK/컴파일러 요구와, Maven 소비자 앱에 필요한 실제 요구를 구분하여 확인한다. SDK 버전을 관성적으로 35/36에 고정하지 않는다.
- `latest`, `+`, SNAPSHOT, preview/alpha를 제품 의존성에 기본 사용하지 않는다.
- 호환성 문제로 변경할 때는 ADR과 전체 회귀 결과가 필요하다. 임의의 구버전 API 혼합 금지.
- native SQLite 등 포함된 모든 `.so`의 ABI/16KB page size 지원을 최종 APK/AAB로 검증한다.[S14]

# 8. 권장 프로젝트 구조

초기 Gradle 모듈 수를 제한하고, 기능 구분은 우선 패키지로 한다.

```text
bookreader-mobile/
  androidApp/                    # Android UI, navigation, lifecycle, imports, controls
    .../features/{library,search,settings,reader}
    .../platform/{files,encoding,layout,device}
  shared/                        # KMP library
    commonMain/.../{model,locator,database,repository,txt,settings}
    androidMain/.../            # platform database builder, necessary adapters
    commonTest/...
  epubAndroid/                   # M01에서 추가; Readium, Android host, security adapter
  docs/
    BookReader_Mobile_v1_1_Implementation_Plan.md
    TASKS.md
    DEPENDENCIES.md
    TESTING.md
    WORKLOG.md
    adr/
    reference/
  scripts/{doctor.sh,ci-check.sh,...}
  .github/workflows/
  AGENTS.md
  README.md
```

`iosApp`, 통계 모듈, 클라우드 모듈, 사용하지 않는 expect/actual 쌍을 선제 생성하지 않는다.
초기에 Android target만 빌드해도 된다. “KMP 프로젝트”라는 이름으로 iOS 빌드 검증까지 완료했다고 보고하지 않는다.

# 9. 데이터 모델

## 9.1 영구 데이터와 파생 데이터

| 데이터 | 영구 / 재생성 | 원칙 |
|---|---|---|
| 관리 원본 파일 | 영구 | 앱 private durable storage, cacheDir 금지 |
| 진행도/북마크/책 메타데이터 | 영구 | Room transaction, destructive migration 금지 |
| 인코딩 선택/정규화 버전 | 영구 | locator 해석을 재현할 수 있어야 함 |
| canonical TXT, sparse index, pagination | 재생성 | 원본+해석 계약으로 재생성; 불완전 캐시를 완성으로 사용 금지 |
| 썸네일/압축 해제 이미지 | 재생성 | 용량 한도와 LRU; 캐시 삭제로 책 기록이 사라지지 않음 |

## 9.2 최소 테이블

```text
Book
  id: UUID string primary key
  format: TXT | EPUB | COMIC
  title, author?, originalDisplayName
  managedRelativePath          # private root 아래 상대 경로
  sourceUri?                   # 출처일 뿐 재독서의 필수 의존성이 아님
  sourceSha256, sourceByteSize
  currentRevision?             # 첫 해석 전 NULL; Ready에서는 반드시 유효한 해석 식별자
  encodingId?, normalizationVersion?
  availability: READY | MISSING | CORRUPT | UNSUPPORTED | DELETING
  coverKey?
  addedAt, lastOpenedAt?, lastReadAt?, readOrder?
  activeSessionEpoch: Long
  createdAt, updatedAt

ReadingProgress
  (bookId, contentRevision) primary key
  locatorType, locatorVersion, locatorJson
  percent?                     # NULL = 계산 불가, 0과 다름
  writerSessionEpoch, writerSequence
  updatedAt

Bookmark
  id primary key
  bookId FK -> Book ON DELETE CASCADE
  contentRevision, locatorType, locatorVersion, locatorJson
  label?, createdAt, updatedAt

ReaderGlobalSettings
  id = "global" primary key
  schemaVersion, settingsJson

BookReaderPreferences
  bookId primary key + FK
  schemaVersion, preferencesJson   # P0: readingMode, comicDirection, fitMode

ImportJob
  id primary key
  sourceUri?, stagingPath, finalRelativePath?, proposedBookId?
  state, bytesCopied, expectedBytes?, sourceSha256?, failureCode?
  createdAt, updatedAt

AppMetadata
  key primary key
  value                          # readOrder counter, internal schema flags
```

`ReadingProgress`에도 Book 외래키/cascade를 적용한다.
진행도 locator JSON 한 필드 저장은 허용한다. **전체 서재/모든 북마크 JSON을 매번 덮어쓰는 것**이 금지다.
원문의 nullable `bookId`로 전역 설정을 구분하는 대신 전역/책별 테이블을 분리해 유일성을 보장한다.
`sourceSha256 + sourceByteSize`의 동일 바이트 중복을 관리한다. 이름이 같다는 이유로 합치지 않는다.
재import 시 기존 READY book을 재사용하되 MISSING 파일의 **동일 해시** 재연결은 기록을 유지한다.

## 9.3 해석 버전

TXT의 `currentRevision`은 원본 해시 + 명시한 encodingId + normalizationVersion의 안정 해시다.
폰트/화면/페이지 수/캐시 형식 변경은 내용 해석 버전을 바꾸지 않는다.
EPUB/Comic은 원본 해시와 필요한 내용 정책 버전을 바탕으로 정의한다.

TXT 인코딩을 바꾸어 텍스트가 달라지면 새 revision을 쓴다.
기존 revision의 진행도/북마크는 보존한다. 새 해석으로 기존 offset을 억지 적용하지 않는다.
새 revision에 기록이 없으면 처음부터 시작한다고 알리고, 이전 인코딩을 다시 선택하면 그 기록을 복원한다.
고급 문장 기반 자동 재배치는 P1로 남긴다.

# 10. 가장 중요한 원칙: 페이지 번호를 저장 위치로 사용하지 않는다

페이지 번호, LazyList item index, Compose 노드 index, WebView 스크롤 픽셀은 정식 locator가 아니다.
화면에 표시하는 페이지 수·%는 파생 정보이며 없다고 저장/북마크를 막지 않는다.
`percentage`만 저장하고 locator를 나중에 복원하는 구조도 금지한다.

공통 envelope:

```json
{
  "type": "txt",
  "version": 1,
  "contentRevision": "<stable-revision>",
  "payload": {
    "utf16Offset": 123456,
    "affinity": "leading",
    "contextHash": "<optional>"
  }
}
```

지원하지 않는 새 locatorVersion을 읽으면 기존 레코드를 보존한 채 복구 안내한다.
역직렬화 실패를 0%/빈 북마크/새 책으로 바꾸어 저장하지 않는다.

# 11. 포맷별 Locator

## 11.1 TXT

- 원본을 선택한 인코딩으로 decode한다.
- 파일 처음의 BOM만 제거한다.
- CRLF/CR을 LF로 바꾼다. chunk 경계의 CR/LF도 동일하게 처리한다.
- 공백 trim/연속 빈 줄 축약/Unicode NFC 변환을 하지 않는다.
- 이 텍스트의 **0-based UTF-16 코드 단위 offset(Long)**을 canonical 위치로 사용한다.
- 원본 byte offset은 진단/인덱스 정보이며 UI 검색 결과의 기준과 섞지 않는다.
- offset은 범위와 surrogate 경계를 검사한다. 렌더링 시작점은 가능하면 grapheme/행 시작에 맞춘다.
- 화면 폭 변경 복원은 같은 anchor가 보이는 위치를 목표로 한다. 같은 픽셀/같은 페이지 번호는 요구하지 않는다.
- 검색 결과, 북마크, 페이지 경계, 스크롤 위치 모두 이 좌표계를 사용한다.

`contextHash`는 검증 보조다. 해시만으로 다른 원문의 문장을 찾아 이동할 수 있다고 가정하지 않는다.
순수 픽셀 offset은 보조값일 뿐이며 viewport가 달라지면 버린다.

## 11.2 EPUB

Readium의 공개 Locator를 adapter에서 serialize한다.[S06]
`href/type/locations/text` 등 제공되는 정보를 보존하고 `chapter+page`로 축약하지 않는다.
앱 envelope에 SDK 식별자와 locator schemaVersion을 함께 둔다.
앱 도메인은 Readium 클래스에 직접 의존하지 않고 opaque/typed payload 경계로 분리한다.

복원 순서: 동일 원본의 SDK locator → 지원되는 text/fragment anchor → resource progression → chapter 시작.
하위 단계로 내려간 경우 `정확한 위치를 찾지 못해 가까운 위치로 이동했습니다` 안내를 남긴다.
보안 변환이 DOM/텍스트 구조를 바꾸면 그 결과에 대한 locator 안정성을 반드시 검증한다.

## 11.3 Comic

```text
ComicLocatorV1
  entryKey                    # 검증·정규화한 ZIP 내부 경로; stable unique key
  sortVersion
  imageIndexHint?             # 빠른 탐색용, 정식 식별자는 아님
  normalizedX: 0..1
  normalizedY: 0..1           # EXIF 적용 표시 이미지에서 viewport anchor의 비율
```

스크롤에서 “현재 이미지”는 Reader viewport의 위쪽 20% 지점을 포함하는 이미지로 정한다.
이미지 사이 간격이면 다음 이미지를 선택하고 y=0으로 둔다.
재진입/회전 시 같은 이미지의 같은 비율 위치가 anchor 부근에 오게 한다.
v1은 영구 zoom scale 저장을 하지 않고 기본 fit으로 복귀한다. anchor 내용 위치는 유지한다.
RTL은 탐색 방향만 바꾼다. 파일 identity와 안정 정렬 목록을 뒤집어 다른 imageIndex로 저장하지 않는다.

# 12. 파일 가져오기

Android SAF의 `ACTION_OPEN_DOCUMENT`와 외부 `ACTION_VIEW`/`ACTION_SEND`를 지원한다.[S07]
권한이 필요한 URI는 부여된 범위 내에서만 읽고, 영구 권한을 얻을 수 있다는 가정은 하지 않는다.
사용자가 선택하지 않은 저장소 전체를 스캔하지 않는다. `MANAGE_EXTERNAL_STORAGE`를 사용하지 않는다.

## import 상태 머신

```text
NEW -> COPYING -> VALIDATING -> FINALIZING -> COMMITTED
                        \-> FAILED
NEW/COPYING/VALIDATING -> CANCELLED
```

1. 작업 UUID와 staging 경로를 journal에 만든다.
2. 크기가 알려지면 여유 공간/포맷 제한을 사전 검사한다.
3. private staging으로 bounded stream 복사하며 SHA-256과 실제 바이트 수를 계산한다.
4. 포맷/구조/보안 한도를 검증한다. 이름/URI를 경로로 직접 연결하지 않는다.
5. 동일 해시 READY book이 있으면 기존 책을 반환하고 staging을 정리한다.
6. 같은 파일시스템의 고유 최종 경로로 완성된 파일을 옮긴다.
7. Book 생성과 ImportJob COMMITTED를 하나의 DB 트랜잭션으로 기록한다.
8. 이후 썸네일/텍스트 인덱싱은 독립된 재생성 작업으로 수행한다.

DB 트랜잭션과 파일 rename은 하나의 원자적 작업이 아니다.
`FINALIZING` journal에 대상 id/path/hash를 기록해 두고, 시작 시 멱등 recovery를 수행한다.
rename 후 DB commit 전에 죽으면 hash/구조를 재검증하고 완료하거나 고아 파일을 정리한다.
DB가 참조하는 영구 파일을 “고아”로 오판해 삭제하지 않는다.

복사 중 취소/디스크 부족/프로세스 종료/URI 권한 소멸을 오류 유형으로 구분한다.
크기 미상 파일은 무한 진행 막대가 아니라 복사한 용량과 취소 버튼을 제공한다.
외부 공유의 임시 권한에 의존하는 미완료 작업은 재선택이 필요할 수 있다고 알린다.
v1은 중단된 다운로드를 자동으로 계속하거나 foreground service로 장시간 유지하지 않는다.

# 13. 파일 보관 정책

**v1 기본값: 항상 앱 내부 관리 복사본.** 원본을 이동/삭제/수정하지 않는다.
파일명은 UUID로 생성하며 파일 시스템 경로를 외부 입력에서 만들지 않는다.
앱 내부에서 책을 삭제하면 관리 복사본과 관련 기록만 삭제하고 원본은 남는다.

트레이드오프는 저장 공간 중복과 최초 복사 시간이다.
그 대신 URI 수명, 원본 수정, 비seekable provider, 외장 매체 분리와 독서 세션을 분리한다.
외부 파일 참조 모드는 P1 이후에 별도 제품 기능으로 검토한다.

파일 삭제 절차는 `DELETING 표시 + active session 무효화 → Reader/작업 종료 → 파일 정리 → DB cascade`다.
중간에 죽으면 다음 시작에서 정리를 재개한다. 진행도 저장 이벤트가 삭제한 책을 다시 만들면 안 된다.
캐시 정리 기능은 영구 원본/DB/진행도/북마크를 건드리지 않는다.

# 14. 서재 화면

P0 구성은 상단 제목/추가 버튼, 책 그리드, 이어읽기 바, 하단 3탭이다.
표지 없음은 제목/포맷 placeholder로 정상 처리한다.

`Initializing / Loading / Empty / Content / RecoverableError`를 분리한다.
DB 조회 실패를 “책이 없습니다”로 표시하지 않는다.
import 중 항목은 작업 UI에 표시하고, 성공한 READY book과 혼동하지 않게 한다.
책 메뉴는 `읽기 / 책 정보 / 삭제`, TXT에는 `인코딩`을 제공한다.
삭제 확인문에는 원본 파일은 삭제하지 않으며 진행도/북마크는 지워진다고 명시한다.

# 15. 전체 검색 탭

서재 메타데이터만 검색한다. 제목/작가/원본 파일명의 부분 문자열 검색을 기본으로 한다.
SQL parameter binding을 사용하고 `%`/`_` 등 wildcard는 문자로 검색하려면 escape한다.
검색 debounce 기본 200ms [설계 목표], 최신 query token의 결과만 표시한다.
초성 검색·형태소 분석·서재 전체 본문 FTS는 v1 비목표다.

# 16. Reader 기본 UI

```text
ReaderState
  Opening -> LoadingProgress -> Preparing -> Restoring -> Ready
                                               \-> Error
Ready <-> Reflowing
```

본문을 잠깐 표시했다고 Ready로 보지 않는다. 기존 진행도 조회와 복원 완료가 필요하다.
`Ready` 전의 기본 위치/0% 콜백은 저장하지 않는다.
`Reflowing` 때는 기존 anchor를 유지하고 중간 임시 위치로 영구 진행도를 덮어쓰지 않는다.

상단: 뒤로, 제목, 책 메뉴.
하단: 진행률/슬라이더, 목차(EPUB만), 본문 검색(TXT/EPUB), 북마크, Reader 설정.
overlay는 본문 위에 겹쳐 보여 기본 viewport 높이를 바꾸지 않는 쪽을 우선한다.
시스템 바/키보드/시트로 실제 viewport가 바뀌면 locator 기반 reflow로 처리한다.

# 17. 터치 조작

TXT/EPUB 페이지 기본: 좌 25% 이전 / 중앙 50% UI / 우 25% 다음.
만화 RTL에서는 좌/우 이동 의미를 반대로 한다.
스크롤 모드의 측면 탭은 한 화면에서 10% 겹침을 남긴 전/후 이동으로 정의한다 [설계 선택].
텍스트 링크·선택·팝업·핀치·드래그와 단순 페이지 탭이 동시에 실행되면 안 된다.

우선순위: 열린 modal/시트 → 접근성 조작 → 핀치/확대 패닝 → 텍스트 선택/링크 → 스크롤/페이지 드래그 → 단순 탭.
pinch scale > 1일 때 좌우 드래그는 패닝이 우선이다.
double tap 판정 전에 첫 tap으로 페이지를 넘기지 않는다.
시스템 Back 제스처 가장자리를 가로채지 않는다.

탐색 슬라이더는 **drag 중 preview, 손을 뗄 때 commit**한다.
취소하면 기존 위치로 돌아가며 preview를 영구 저장하지 않는다.
전체 길이가 확정되지 않으면 슬라이더를 비활성화하고 `위치 계산 중`을 표시한다.
연속 페이지 이동 중 최종 target의 중간 결과만 렌더링/저장하지 않도록 요청 generation을 둔다.

# 18. TXT Reader

## 18.1 첫 화면과 인덱싱

성능 측정의 두 구간을 분리한다.
A. 외부 URI 선택 → 복사/검증 완료. 제공자 속도에 영향을 받으므로 별도 측정.
B. 관리 복사본 열기 → 첫 본문 표시. 핵심 Reader 성능 지표.

원본 복사가 완료된 뒤 TXT 정규화/인덱싱을 시작한다.
전체 pagination이나 전체 본문 분석을 첫 렌더의 선행 조건으로 삼지 않는다.
초기 prefix를 만들면 첫 화면을 표시하고, 남은 canonical 변환·sparse index를 계속 작성한다.

```text
managed original
   -> encoding decision / preview
   -> streaming decode + newline normalization
   -> committed canonical prefix
       -> initial reader window
   -> background canonical/index build
   -> complete marker + atomic cache publication
```

인덱스 상태는 `NONE / BUILDING / READY / FAILED`로 분리한다.
부분 결과에는 commit한 최대 offset을 표시한다. 완성되지 않은 cache를 READY로 열지 않는다.
프로세스 종료 후 미완성 인덱스는 재구축 가능하다. 영구 progress/bookmark는 그대로 유지한다.
인덱스가 사라진 상태에서 책 뒷부분 복원은 추가 스캔이 필요할 수 있다. 진행/취소 UI를 보여 주며 “즉시”라고 보장하지 않는다.

## 18.2 인코딩

자동 결정 기본 순서: UTF BOM → strict UTF-8 검사/진행 → 실패 시 CP949/EUC-KR 후보와 미리보기.
BOM 없는 UTF-16은 확실하지 않으면 수동 선택을 요구한다.
CP949 디코더는 Android 플랫폼 지원 여부를 `Charset.isSupported`와 fixture로 검증한다.[S11]
CP949 전용 확장 글자를 포함하는 테스트로 EUC-KR과 구분한다.
외부 detector를 기본 도입하지 않는다. 자동 판단이 모호하면 사용자 선택이 정확성의 일부다.

자동 판단 중 늦게 오류를 발견하면 잘못된 문자로 조용히 치환하지 않고 인코딩 선택 흐름으로 전환한다.
현재 revision의 기록을 보존하고, 새 인코딩 선택 시 §9.3에 따른 별도 해석 기록을 사용한다.
전체 파일을 `String`/`readText()`로 올려 검사하지 않는다.
엄격 decode 오류 처리, 미완성 멀티바이트 및 surrogate carry, chunk 경계의 CR 처리를 테스트한다.

## 18.3 chunk / sparse index

[설계 시작값] I/O buffer 64KiB, 텍스트 window는 수 개의 최대 16Ki UTF-16 단위 fragment.
긴 문단도 강제 줄바꿈을 삽입하지 않고 grapheme-safe fragment로 다룬다.
입력 문단이 1MiB 이상이어도 어떤 단계도 전체 문단 렌더를 요구하지 않는다.
Sparse index는 일정 canonical offset 간격으로 UTF-16 offset ↔ canonical UTF-8 byte 위치를 연결한다.
chunk/index 버전은 cache key에 포함한다. 캐시 경계를 사용자 문단 경계로 오인하지 않는다.

## 18.4 페이지 모드

실제 표시와 같은 글꼴/측정기로 line break와 페이지 경계를 계산한다.
“글자 수 × 평균 폭” 추정으로 페이지를 자르지 않는다.
현재 및 앞뒤 소수 페이지/경계만 보유한다. 전체 View 생성 금지.
레이아웃의 연속 구간은 `[start, end)`로 표현한다. `page[i].end == page[i+1].start`를 보장한다.
공백/빈 줄도 원문 좌표에 포함하며 중복 표시/누락을 검사한다.

큰 임의 위치 이동은 그 anchor에서 지역 페이지 구간을 다시 잡을 수 있다.
이 경우 전체 `현재/총 페이지`는 표시하지 않고 % 또는 `페이지 계산 중`을 사용한다.
전체 페이지 수가 없어도 검색·북마크·앞뒤 이동은 가능해야 한다.
되돌아가기/다시 앞으로 가기에서 같은 지역 페이지 경계를 재사용하고 반복 이동 드리프트를 검사한다.

layout key:
`contentRevision + textEngineVersion + fontIdentity/fontVersion + fontSize + systemFontScale + density
+ lineHeight + letterSpacing + viewportPx + insets + margins + paragraphPolicy + locale
+ lineBreak/hyphenation/fontPadding policy`.
폰트 비동기 로딩 후 재측정도 key 변화로 처리한다.

## 18.5 스크롤 모드

LazyList의 item index를 저장하지 않는다.
visible text layout의 첫 의미 있는 행을 canonical offset으로 변환하여 저장한다.
fragment 경계가 바뀌어도 복원 대상은 같은 원문 문자다.
시스템 font scale을 임의로 1로 고정하지 않는다.
글꼴/크기/행간/자간/여백 변경은 기존 anchor 캡처 → reflow → anchor 복원 → Ready 순서다.

## 18.6 책 내 검색

v1 기본은 정규화 본문의 **literal substring** 검색이며 정규식/형태소/Unicode 정규화 검색은 제외한다.
쿼리 최대 256 UTF-16 단위 [설계 한도]. 너무 긴 쿼리는 안내한다.
검색 fragment 사이에 최소 `queryLength-1` 문자를 겹쳐 읽고 결과 offset으로 중복 제거한다.
기본은 한글 정확 일치와 ASCII 대소문자 무시 옵션. 길이가 바뀌는 임의 Unicode case folding으로 offset을 깨뜨리지 않는다.
결과는 50개씩, UI에는 최대 1,000개까지 누적 표시하고 더 찾기 상태를 명시한다.
인덱싱 중이면 검색 범위/진행 중임을 표시하거나 완료를 기다리게 하되, 아직 안 읽은 구간을 “결과 없음”으로 확정하지 않는다.
검색 job에는 query generation을 둔다. 오래된 결과가 새 검색/다른 책에 표시되지 않게 한다.
결과 이동 후 원래 위치로 돌아오는 **일회성 돌아가기 버튼**을 P0로 제공한다.


# 19. EPUB Reader

## 19.1 엔진 기본안과 조기 검증

Readium Kotlin의 EPUB parser/Publication/기존 Navigator를 사용한다.[S04][S05]
직접 EPUB CFI·CSS pagination·목차 파서를 모두 새로 만들지 않는다.
단, SDK 채택만으로 앱의 보안 정책과 locator 복원이 검증된 것은 아니다.

M01에서 다음 작은 실제 검증을 끝내고 나서 EPUB 본기능을 확장한다.
- EPUB2/3 fixture 열기, 이미지/CSS, TOC.
- SDK Locator round-trip과 앱 프로세스 재시작 뒤 복원.
- 글자 크기/테마 변경 뒤 anchor 보존.
- 앱 UI와 Fragment lifecycle, Back, 회전의 충돌 여부.
- 오프라인 리소스 로딩과 외부 요청 차단.
- publication script 차단과 SDK 내부 동작의 양립.

라이브러리 API 이름을 기억에 의존해 추정 구현하지 않는다. 고정 버전의 문서/소스/샘플로 확인한다.
M01의 보안·복원 검증이 막혀도 독립적인 TXT/서재 개발은 가능하다.
그러나 EPUB 포함 v1을 출시 완료로 표시할 수 없다. SDK 한계는 별도 blocker/ADR로 기록한다.

## 19.2 보안 계약

신뢰 범위는 **앱에 번들한 검증된 엔진 코드**와 **사용자가 가져온 비신뢰 출판 콘텐츠**로 나눈다.
Readium 계열은 기능 구현을 위해 JS/CSS를 주입할 수 있으므로, 모든 JavaScript를 끄는 한 줄 설정이 엔진 기능까지 보존한다고 가정하지 않는다.[S13]

v1 요구:
- 책 안의 script, event handler, `javascript:`/위험한 URL, iframe/object/embed의 실행 경로 차단.
- 외부 이미지/폰트/CSS import/fetch/redirect 및 임의 WebView 탐색 차단.
- XML 외부 entity/DTD 해석 차단, ZIP 경로·실제 리소스 크기 제한.
- SVG의 스크립트/외부 참조도 동일 정책 적용.
- 파일 시스템 임의 접근, 다른 책 리소스 접근, cookie/bridge/디버그 기능 노출 차단.
- 엔진과 앱 사이 메시지는 출처/스키마/세션/허용 동작을 검증하고 임의 명령 실행 기능을 제공하지 않음.
- 외부 웹 링크는 자동 실행하지 않고 사용자가 확인했을 때만 외부 브라우저로 전달.
- 지원하지 않는 보안 변환이 필요한 파일은 fail-closed로 거부. `열기 시도`로 보안 검사를 우회하지 않음.

SDK가 사용하는 내부 리소스 라우트는 필요한 출판 리소스만 제한적으로 허용한다.
로컬 origin/내부 서버가 필요하면 앱 수명과 묶고 외부 인터페이스에 바인딩하지 않는다.
`INTERNET` 권한이 필요한 SDK라면 필요 이유를 문서화하고, 콘텐츠의 외부 요청은 별도로 차단/검증한다.
“오프라인 앱”이므로 SDK의 내부 리소스 처리 방식과 무관하게 INTERNET 권한을 무조건 없애면 된다고 가정하지 않는다.

정규식으로 HTML script 문자열만 삭제하는 자체 sanitizer를 보안 해법으로 삼지 않는다.
SDK 설정/리소스 변환/allowlist/CSP 등 실제 선택 경로를 ADR에 적고 공격 fixture로 검증한다.
CSP와 sanitizer의 필요성/가능성은 선택 SDK 경로에 따라 검증한다. 설정 이름이나 적용 성공을 꾸며내지 않는다.

## 19.3 기능 동작

출판사 CSS 존중을 기본으로 하되 사용자 글자 크기/테마를 일관되게 적용한다.
각주 팝업은 P2지만 일반 내부 각주 링크 이동과 돌아오기는 지원한다.
지원 불가/손상 resource는 위치와 원인을 기록하고 무한 로딩하지 않는다.
Readium 검색 결과도 동일 공개 Locator를 통해 이동한다.
DRM/고급 fixed layout은 지원되는 것처럼 import 후 빈 화면을 보여 주지 않는다.

# 20. Comic / ZIP Reader

## 20.1 ZIP 검증과 안정 정렬

ZIP 엔트리 이름은 외부 입력이다. canonical path가 추출 root 안인지 확인한다.[S08]
절대 경로, `..`, 역슬래시 우회, NUL, 중복 정규화 경로, 심볼릭 링크와 디렉터리/파일 충돌을 차단한다.
원본 엔트리명을 디스크 파일명으로 바로 사용하지 않고 해시/고유 key로 매핑한다.

natural sort version 1:
- 전체 정규화 경로를 `/` 세그먼트로 비교한다.
- 세그먼트 안의 숫자 구간은 자릿수와 값으로 비교한다. 큰 숫자를 Int로 변환해 overflow시키지 않는다.
- 숫자 값이 같으면 원래 길이/문자열을 tie-breaker로 써 `1`, `01`, `001` 순서를 결정한다.
- locale 의존 정렬 금지. 최종 tie-breaker는 원본 이름의 안정 순서다.
- `__MACOSX`, `.DS_Store`, 숨김 메타 파일을 제외하며 비이미지는 페이지로 만들지 않는다.
- 정렬 결과 manifest를 source hash + sortVersion에 묶어 저장한다.

UTF-8/ZIP Unicode 이름 규칙을 우선 적용한다.
표시 이름 해석이 모호한 레거시 ZIP은 경고하며, 표시 이름 인코딩 추정으로 stable entry identity를 바꾸지 않는다.
한국어/일본어/중첩 폴더/선행 0/큰 숫자 이름 fixture를 포함한다.

## 20.2 모드

`한 장 보기`, `연속 보기` 2개 엔진 모드다.
`웹툰`은 연속 보기의 너비 맞춤/간격 0 프리셋이다.
LTR/RTL은 한 장 모드의 다음/이전 의미를 바꾸며 세로 스크롤 자체는 항상 위→아래다.
핏/방향/모드 변경 때 같은 entryKey와 비율 anchor를 유지한다.

## 20.3 메모리

뷰포트 크기를 기준으로 다운샘플링하고 먼저 이미지 크기를 읽어 예산을 계산한다.[S09]
ARGB 4 bytes/pixel 계산은 기초 추정일 뿐 디코더 임시 버퍼/GPU/네이티브 메모리를 모두 포함하지 않는다.
[초기 예산] decoded bitmap cache는 `min(64MiB, 앱 heap budget의 20%)`, decode concurrency 1.
현재 이미지 우선, 다음/이전 저해상도 prefetch를 제한한다. 빠른 이동 때 오래된 decode를 취소한다.
OOM을 catch한 뒤 같은 크기로 무한 재시도하지 않는다.

매우 긴 웹툰은 지원 포맷/디코더에서 region/tile decode 경로를 검증한다.
지원되지 않는 경우 안전한 다운샘플 버전으로 열고 해상도 제한을 알리거나 해당 이미지에 복구 가능한 오류를 표시한다.
PNG/WebP까지 모든 기기에서 효율적인 region decode가 된다고 미리 보장하지 않는다.
ZIP 전체 이미지를 풀어 bitmap으로 올리거나 단일 수만 px bitmap을 무조건 full decode하지 않는다.

손상 이미지 1개는 오류 페이지 + 건너뛰기로 처리한다. 다른 이미지의 entryKey와 진행도를 보존한다.
현재 페이지를 건너뛰었다고 원본을 자동 수정하거나 manifest에서 삭제하지 않는다.

# 21. Reader 설정

공통 기본 테마는 Light / Soft(Sepia) / Dark / OLED Black.
본문 UI와 앱 chrome의 테마를 구분하되 첫 설정 화면에서 지나치게 복잡하게 만들지 않는다.
TXT 글꼴은 기기 제공 기본 고딕/명조 등 실제 존재하는 선택지만 표시한다.
특정 한글 글꼴이 모든 기기에서 동일하다고 가정하지 않는다. 번들 글꼴 추가 시 라이선스/용량 검토가 필요하다.

[초기 UI 범위] 글자 크기 12–36sp, 행간 배수 1.0–2.0, 자간 -0.02–0.10em, 좌우 여백 8–40dp.
범위를 넘는 시스템 확대에서도 조작 UI와 본문이 접근 가능해야 한다. 범위는 사용자 테스트 후 ADR로 조정 가능하다.
설정 복원 실패는 안전한 표시 기본값을 사용하되, 손상 원본 설정/진행도를 덮어쓰지 않는다.

밝기는 Reader window에만 적용하고 나갈 때 `system default`로 복원한다.[S16]
시스템 밝기/자동 밝기 설정을 전역 변경하지 않는다.
화면 꺼짐 방지도 활성 Reader에서만 적용한다.
회전 잠금은 OS/창 환경에서 허용되는 범위의 요청이다. 큰 화면에서 항상 적용된다고 약속하지 않는다.[S15]

# 22. 전역 설정 vs 책별 설정

전역 기본값은 모든 책의 기본 타이포그래피/테마다.
P0 책별 override는 `readingMode`, Comic의 `direction/fitMode`만 저장한다.
필드가 없는 값은 global을 상속하고 `기본값으로 되돌리기`는 해당 override만 지운다.
향후 모든 글꼴/행간을 책별로 저장할 수 있도록 스키마는 versioned preferences로 둔다.
P1 UI를 미리 노출하지 않는다. 인코딩은 표시 설정이 아니라 §9.3의 **내용 해석 설정**이다.

# 23. 상태 저장 원칙

## 23.1 저장 허용 게이트

progress 조회 `Loading / Ready(existing or none) / Error`를 구분한다.
**성공한 조회에서 행이 없음**만 새 독서 기록으로 인정한다.
조회 실패나 손상 locator를 `Ready(empty)`로 바꾸지 않는다.
복원 성공/사용자 명시적 새 시작 전까지 쓰기를 차단한다.
오류 시 읽기를 제한적으로 허용할 수 있으나 진행도/북마크를 저장하지 않는 상태임을 분명히 알리고 재시도한다.

## 23.2 세션과 쓰기 순서

- Reader 진입마다 Book의 `activeSessionEpoch`를 DB transaction에서 증가시킨다.
- 각 이벤트는 `bookId/contentRevision/sessionEpoch/sequence/locator`를 캡처한다.
- 화면의 현재 bookId를 나중에 다시 읽어서 지연 이벤트에 붙이지 않는다.
- 앱 수준 single writer가 순서대로 처리하며, 마지막 성공 저장 순번을 기록한다.
- DB 쓰기 시 book이 READY이고 currentRevision/activeSessionEpoch가 일치하는지 검증한다.
- 동일 세션에서는 더 큰 sequence만 받는다. 새 세션의 첫 저장은 이전 세션 기록보다 우선한다.
- 이전 세션의 늦은 이벤트, 삭제 중인 책, 오래된 revision, 이전 reflow의 이벤트는 무시한다.
- 쓰기 실패는 성공으로 취급하지 않고 재시도 가능한 오류로 보존한다.

locator offset/percentage가 더 크다는 이유로 “최신”이라고 판단하지 않는다. 뒤로 읽기 이동도 유효하다.
upsert가 없는 Book을 생성하거나 다른 책의 행을 건드리지 않도록 FK/조건 검사를 적용한다.

## 23.3 저장 타이밍

[설계 목표]
- 계속 스크롤/페이지 이동 중에는 최신 안정 locator를 **최대 1초 간격**으로 sampling 저장한다.
- 이동이 멈추면 **250ms 뒤** 최종 locator를 저장한다.
- 페이지 이동 완료/북마크 추가/검색 확정 점프는 즉시 또는 동일 writer의 빠른 큐로 처리한다.
- Reader 종료, 책 전환, background 전환은 가능한 범위에서 flush barrier를 요청한다.
- 무한 debounce 때문에 계속 읽는 동안 저장되지 않는 구현은 금지한다.
- lifecycle callback만 믿지 않는다.

“매번 마지막 1초가 무조건 보존된다”는 플랫폼 보장은 아니다.
목표는 정상 작동 환경에서 **durable commit 지연 p95 1.5초 이하**다.
임의 프로세스 종료 시 보장 기준은 마지막 commit 완료 레코드이며, 전원 차단/디스크 오류 보장은 별도다.
북마크 버튼의 성공 표시는 DB commit 뒤에만 제공한다.

# 24. 데이터 안전성

import/삭제/캐시 작업마다 취소와 복구 계약을 둔다.
DB migration 실패 시 destructive fallback을 하지 않고 복구 화면으로 진입한다.
읽기 전용 진단/사용자 명시적 초기화 외에 앱이 자동으로 DB를 지워 “정상 실행”시키지 않는다.

Room transaction은 단일 row 저장뿐 아니라 북마크/설정의 관련 변경을 일관되게 묶는 데 쓴다.
장시간 파일 복사/압축 해제/텍스트 파싱을 DB transaction 안에서 수행하지 않는다.
외래키, 유일성, migration schema export를 CI에서 검증한다.
WAL 활성 DB를 백업할 때 메인 파일 하나만 복사하면 된다는 가정은 하지 않는다.
백업 구현은 P1이며, 실제 구현 시 일관된 snapshot 절차를 별도 검증한다.

예기치 않은 포맷 오류가 앱 startup에서 재현돼 crash loop가 되지 않게 한다.
앱 시작에는 최소 서재 정보만 조회하고 책 파서는 사용자 열기 이후에 실행한다.
오류 로그에는 bookId/jobId/errorCode만 기본 포함하고 책 본문/전체 URI/개인 파일명을 업로드하지 않는다.

# 25. 성능 목표

다음은 **미측정 초기 목표**다. M00에서 기준 Android 실기기/OS/메모리/주사율을 기록하고,
측정이 불가능하면 수치를 PASS로 채우지 않는다.

| 지표 | 초기 합격 목표 | 측정 조건 |
|---|---|---|
| cold launch → 서재 조작 가능 | p95 2초 이하 | release 또는 benchmark build, 30회, 초기 import 제외 |
| 관리 5MiB TXT → 첫 본문 | p95 1.5초 이하 | 새 index, 앞부분 열기, 인코딩 확정 |
| 관리 50MiB TXT → 첫 본문 | p95 3초 이하 | 같은 조건, 전체 분석 선행 금지 |
| 완성 index가 있는 50MiB TXT 위치 복원 | p95 1.5초 이하 | 앞/중간/끝 위치별 측정 |
| 페이지 넘김 → 안정 화면 | p95 100ms 이하 | 이미 준비된 인접 페이지; cold jump 별도 |
| 진행도 durable commit 지연 | p95 1.5초 이하 | 연속 스크롤, 정상 저장장치 |
| 50MiB TXT 메모리 증가 | baseline 대비 PSS +96MiB 이하 | 지속 독서/검색/설정 변경 뒤 peak 측정 |
| Comic 이미지 캐시 | §20.3 예산 준수 | 앱 전체 PSS/GPU는 별도 계측 |

각 결과에 빌드 SHA, fixture SHA, 기기/OS, warm/cold, 중앙값/p95를 기록한다.
에뮬레이터 성능을 실기기 성능으로 표현하지 않는다.
threshold를 못 맞췄을 때 테스트를 삭제/skip하거나 수치를 몰래 느슨하게 하지 않는다.
원인 분석과 UX 영향, 새 목표의 근거를 ADR로 남긴다. 내용 누락/데이터 손상은 성능과 맞바꾸지 않는다.

# 26. 접근성

최소 48dp 조작 영역, 텍스트/아이콘 의미 label, 고대비, 상태 변경의 과도한 announcement 억제.
TalkBack 사용 시 탭 영역만으로 이전/다음/메뉴를 조작하도록 강요하지 않는다.
실제 접근성 노출 버튼과 본문 읽기 순서를 제공한다.
시스템 글자 배율 1.0/1.3/2.0, display scale, RTL UI 환경에서 핵심 동작을 확인한다.
시스템 애니메이션 축소 설정을 존중한다.
서재/오류/설정/Reader 접근성은 알파 후반까지 미루지 않고 각 UI 작업의 DoD에 포함한다.

# 27. 개인정보 / 네트워크 원칙

로그인/광고/책 내용 업로드/독서 기록 서버 전송/analytics는 v1에 넣지 않는다.
원격 crash SDK도 기본 추가하지 않는다. 필요한 진단은 사용자가 선택한 로컬 내보내기로 한정한다.

**앱 코드에 업로드가 없는 것과 OS 자동 백업이 없는 것은 다르다.**
Android Auto Backup에는 기본적으로 DB/내부 파일이 포함될 수 있다.[S10]
v1은 앱이 관리하는 책/진행도/북마크의 자동 클라우드 백업을 제외하는 정책을 적용한다.
`allowBackup`, Android 11 이하 규칙, Android 12+ data extraction rules, cloud/D2D 항목을 함께 검토한다.
OEM별 D2D 동작 차이가 있으므로 `allowBackup=false` 한 줄만으로 모든 이전이 차단됐다고 주장하지 않는다.[S10]
M10에서 실제 manifest/리소스 규칙과 지원 환경의 백업·복원 동작을 검증한다.

사용자 안내:
`책은 앱 내부에 복사됩니다. 원본은 변경하지 않습니다.
앱을 삭제하거나 앱 데이터를 지우면 내부 복사본과 독서 기록이 삭제될 수 있습니다.
v1에는 앱 백업/복원 기능이 없습니다.`

# 28. 백업 / 복원

앱 내부 export/import backup UI는 **P1로 확정**한다. Phase 6에서 다시 범위를 흔들지 않는다.
v1에서 자동 백업 제외와 데이터 손실 안내는 P0다.

향후 백업은 schemaVersion, manifest, 해시, 총 크기/엔트리 제한, 원본 포함 옵션을 갖춘다.
생성 제한과 복원 제한을 일치시킨다.
검증 → staging → 현재 작업 정지/배타 구간 → 안전 snapshot → commit → 재검증 순서로 설계한다.
동기화와 백업, DB 파일 복사, 원본 파일 export를 같은 기능으로 취급하지 않는다.

# 29. Desktop 동기화는 v1 비목표

PC와 모바일 데이터 포맷을 지금 통일하지 않는다.
원본 내용 해시/버전 있는 locator는 향후 식별의 기초가 될 수 있지만 기기간 동기화 구현을 의미하지 않는다.
계정/충돌 해결/서버 timestamp/암호화/선택 동의는 향후 별도 계획이다.

# 30. 아키텍처 원칙

```text
Screen
  -> ViewModel/Presenter
  -> UseCase + ReaderSession
  -> Format Engine / Platform Ports
  -> Repositories / ManagedFileStore / Room
```

공유 인터페이스의 최소 책임:
- `ManagedFileStore`: 안전한 복사/열기/삭제/존재 확인; 임의 공개 경로 접근 금지.
- `BookRepository`: 서재/상태/메타데이터, format별 engine 선택.
- `ProgressRepository`: 조회 상태, 조건부 저장, durable acknowledgement.
- `BookmarkRepository`: revision-aware CRUD.
- `ReaderSession`: lifecycle, locator, seek 요청, restore/reflow 상태.
- `TextLayoutPort`: 포맷 텍스트 측정/표시와 같은 레이아웃 계약.
- `EpubEngineAdapter`: SDK lifecycle/locator/preferences/security를 캡슐화.
- `ComicEngine`: manifest/entry 식별/이미지 로드/anchor 변환.

공통 추상화는 `open/seek/currentLocator/close` 같은 계약에 한정한다.
세 포맷의 pagination/locator/검색을 하나의 거대한 base class로 강제 통합하지 않는다.
UI가 DAO를 직접 호출하거나 스크롤 콜백마다 독립 코루틴을 무한 생성하지 않는다.

# 31. 오류 UX

| 오류 | 사용자 동작 | 데이터 처리 |
|---|---|---|
| import 공간 부족 | 공간 확보/취소/다시 시도 | staging 정리, 기존 서재 유지 |
| 임시 URI 권한 만료 | 파일 다시 선택 | 작업 실패 기록, 기존 책 영향 없음 |
| 관리 복사본 없음 | 동일 파일 재연결/서재에서 삭제 | 동일 해시만 기존 기록에 재연결 |
| 인코딩 오류 | 미리보기/수동 선택 | 이전 revision 기록 보존 |
| 손상/지원 불가 EPUB | 닫기/삭제/다른 파일 가져오기 | 보안 우회 버튼 없음 |
| 이미지 한 장 손상 | 건너뛰기/재시도 | 다른 entry 위치 유지 |
| progress 조회 실패 | 재시도/읽기 취소 | 빈 상태 저장 금지 |
| progress 저장 실패 | 재시도, “기록 저장 안 됨” 표시 | 마지막 durable 기록 보존 |
| DB migration 실패 | 복구 안내/사용자 명시적 초기화 | 자동 DB 삭제 금지 |

지원 한도 초과와 파일 손상을 구분해서 알린다.
에러 화면에 stack trace/내부 전체 경로를 표시하지 않는다.
모든 긴 작업에는 최소한 진행 상태/취소 또는 안전한 뒤로 가기를 제공한다.

# 32. 개발 단계

원문 Phase의 목표는 유지하되 실제 작업 단위를 아래로 재배치한다.
작업별 상세 계약은 `docs/TASKS.md`가 담당한다.

| 작업 | 내용 | 다음으로 넘어가는 조건 |
|---|---|---|
| M00 | 프로젝트/툴체인/실행 앱/Room smoke/CI | 실제 assemble·단위 테스트·lint, 환경 보고 |
| M01 | EPUB adapter·보안·locator 및 인코딩 기술 검증 | 증거 기반 ADR, 위험을 별도 blocker로 등록 |
| M02 | 파일 관리/import journal/서재/삭제/메타데이터 검색 | 실패 복구/중복/부분 등록 방지 |
| M03 | TXT canonical/스크롤/진행도/이어읽기 | 첫 end-to-end 내부 알파 경로 |
| M04 | 세션 쓰기 순서/재시작/강제 종료 | 오래된 이벤트/조회 오류/삭제 race 테스트 |
| M05 | TXT 페이지·타이포그래피·reflow | 내용 coverage와 locator invariants |
| M06 | 본문 검색·북마크·큰 TXT/인코딩 회귀 | 경계 검색/부분 index/수동 인코딩 |
| M07 | 모바일 제어·Back·접근성·기기 설정 | 제스처/확대/창 복원 충돌 없음 |
| M08 | EPUB 제품 통합 | M01의 EPUB blocker 해소, 기능·보안 회귀 |
| M09 | ZIP/Comic 제품 통합 | 정렬/메모리/zoom/anchor/손상 방어 |
| M10 | migration·privacy·16KB·실기기/보안/성능 | 최종 v1 범위 전체 증거 확보 |
| M11 | release candidate 감사·배포 산출물 | blocker 0, 검증표/알려진 제한/패키징 |

M01의 일부 결과가 막혀도 M02–M07의 독립 범위는 진행할 수 있다.
M08은 EPUB 관련 M01 기준을 통과해야 한다. M11은 미해결 P0를 우회할 수 없다.
한 세션/PR에서 전체 v1을 무작정 만들지 않는다. 작은 실행 가능 묶음과 검증 결과를 남긴다.

# 33. 테스트 전략

## 33.1 레벨

- common/JVM unit: locator/정규화/정렬/순번/검색 offset/property tests.
- DB integration: 실제 Room/SQLite의 FK/transaction/recovery/migration.
- Android instrumentation: 텍스트 측정, SAF/intent, Fragment/Compose, UI/Back/접근성.
- host-driven process tests: 외부 adb/runner가 앱 종료·재시작을 제어.
- 실기기: 읽기 품질, 메모리/온도/배터리, OEM 동작, 16KB 환경.
- 보안: 의도적으로 조작한 ZIP/EPUB fixture, 외부 네트워크 시도 관찰, 앱 파일 canary 검사.

JVM 테스트만으로 Android 렌더/폰트/수명주기 검증을 대체하지 않는다.
UI screenshot은 필요 시 로컬/CI artifact로 남기되 사용자의 책 본문을 공개 CI에 올리지 않는다.
fixture는 자체 생성/재배포 허용 자료만 사용하고 seed/hash/license를 기록한다.

## 33.2 테스트 데이터와 자동화

`test-fixtures/manifest.json`에 id/format/encoding/bytes/hash/예상 결과/라이선스를 둔다.
큰 TXT/이미지/ZIP은 생성 스크립트로 만들고 거대한 바이너리를 Git에 계속 누적하지 않는다.
실제 사용자의 책/DRM 파일/로그인 토큰을 CI 테스트 자료로 넣지 않는다.
보안 fixture는 네트워크 없는 테스트 환경에서 local canary/테스트 서버만 사용한다.

# 34. TXT 회귀 테스트 필수 케이스

원문의 20개 유형을 유지하고 다음을 필수로 추가한다.

| ID | 조건 | 기대 결과 |
|---|---|---|
| TXT-01 | 100KiB/5MiB/50MiB 원문 | 전체 논리 내용 coverage 일치 |
| TXT-02 | 개행 없는 1MiB 문단, 131072 경계 통과 | 끝까지 접근/검색/북마크 가능 |
| TXT-03 | CRLF가 buffer 경계에서 분할, 혼합 CR/LF | chunk 크기와 무관한 동일 canonical 결과 |
| TXT-04 | UTF-8 multibyte/UTF-16 surrogate 분할, emoji ZWJ | 손실/중복/잘못된 위치 없음 |
| TXT-05 | CP949 확장 글자, EUC-KR, BOM 없는 UTF-16 | 오판 시 수동 복구, 조용한 치환 금지 |
| TXT-06 | 인코딩 변경 뒤 재진입 | revision별 기록 보존, 잘못된 offset 재사용 금지 |
| TXT-07 | font/size/line/letter/margin/system scale 변경 | 저장 anchor가 동일 문장/현재 페이지에 존재 |
| TXT-08 | 100회 다음/이전, jump 뒤 재이동 | 중복/누락/페이지 경계 drift 없음 |
| TXT-09 | query가 fragment 경계를 가로지름 | 정확한 원문 offset으로 이동, 중복 결과 없음 |
| TXT-10 | index 미완성/삭제/손상 뒤 북마크 이동 | 기록 유지, 상태 표시 후 재구축 |
| TXT-11 | 연속 스크롤 중 강제 종료 | 마지막 durable locator 보존 |
| TXT-12 | 빈 파일/빈 줄만/매우 긴 공백/잘못된 byte | 명확한 처리, crash/무한 로딩 없음 |

# 35. Comic 회귀 테스트

원문 12개 유형에 다음을 추가한다.
- 동일 basename이 다른 폴더에 존재; `1/01/001`; 매우 큰 숫자; sorting locale 변경.
- 전체 ZIP index 순서를 바꾸어도 entryKey로 같은 이미지 찾기.
- EXIF 회전된 JPEG, 작은 이미지, 1080×30000 수준의 긴 이미지 [시험 fixture].
- 빠른 100장 이동, pinch 중 page gesture, 화면 회전/fit 변경 뒤 normalized anchor.
- PNG/WebP의 decode 제한 경로, region 미지원 fallback.
- zip path traversal, 중복 경로, 큰 선언값/실제 해제 크기 불일치, 암호화.
- 손상 이미지 건너뛴 후 되돌아가기와 다음 재실행.
- 삭제 중 진행 중인 decode/파일 핸들이 영구 파일 재생성을 하지 않음.

# 36. EPUB 회귀 테스트

원문 14개 유형을 유지한다.
추가: manifest 상대 경로/URL encoding, 내부 링크/각주 왕복, nested TOC,
폰트 로딩 후 reflow, 매우 긴 chapter, 문장 검색 후 돌아오기,
SDK upgrade 전후 locator fixture, 네트워크 불가/타임아웃 상태,
script/event handler/iframe/SVG external reference/CSS import/redirect,
XXE/zip traversal/압축 폭탄, 다른 책 리소스 요청,
엔진 trusted script는 동작하지만 publication script는 실행되지 않는 검증.

복원이 chapter 시작 fallback으로만 성공한 결과를 exact restore PASS로 표기하지 않는다.

# 37. v1 출시 차단 조건

원문의 내용 유실/진행도 손실/다른 책 덮어쓰기/DB 손상/긴 TXT 누락/심각한 위치 점프/
ZIP OOM/임의 script/파일 하나로 앱 전체 실패/서재 빈 상태 오표시를 그대로 blocker로 유지한다.

추가 blocker:
- import journal recovery가 기존 영구 파일을 삭제.
- progress 조회 실패 뒤 0%/빈 북마크 저장.
- stale session이 새 위치를 덮어씀.
- 최종 패키지에서 Android native library 호환 문제로 시작 불가.
- 책 리소스가 사용자 확인 없이 외부 요청을 발생시킴.
- 보안/실기기 테스트를 실행하지 않았는데 PASS로 보고.
- M01 실패를 숨긴 채 EPUB 지원 완료로 표시.
- 최종 v1으로 부르면서 원문의 P0 포맷/필수 기능 누락.

미측정 성능은 “성능 목표 충족 여부 미확인”이다.
검증 환경이 없다는 사실을 결함으로 단정하지는 않지만, 필요한 출시 증거가 없는 상태로 출시 판정을 내리지 않는다.

# 38. 구현 중 절대 피할 것

원문 금지사항을 유지한다.
추가로 다음을 금지한다:
- `readText()`/전체 ByteArray/전체 bitmap을 대용량 기본 경로에 사용.
- 단순 debounce 하나로 진행도 안정성 보장 주장.
- 예외를 삼키고 empty list/0%/기본 설정을 저장하는 처리.
- 보안 옵션이 어려워 JS/네트워크를 전부 허용하고 나중에 고치기.
- tests skip/ignoreFailures/`|| true`로 녹색 CI 만들기.
- 지원하지 않는 API 이름/라이브러리 버전/테스트 결과를 추정하여 작성.
- 원본 계획서와 이 개정안의 상충 규칙을 동시에 구현.
- iOS 대응을 명목으로 첫 Android 독서 경로를 미루기.
- 별도 사용자 요청 없이 remote push/merge/스토어 배포/서명키 생성·업로드.

# 39. 초기 UI 디자인 원칙

콘텐츠 우선, 한손 조작, 최소 overlay, 짧은 애니메이션, Bottom Sheet 유지.
초기부터 픽셀 단위 커스텀 디자인 시스템을 새로 만드는 대신 Compose/Material의 접근성 있는 구성요소를 사용한다.
서재와 Reader가 같은 기능의 중복 버튼으로 가득 차지 않게 한다.
텍스트 엔진/저장 안정성이 확보된 뒤 시각적 polish를 수행한다.

# 40. 초기 화면 목록

원문 화면 목록에 `Encoding Preview`, `Import Progress`, `Storage/Recovery Error`를 명시적으로 추가한다.
`Error/Relink`는 관리 복사본 손상/실수 삭제에 대한 **동일 내용 재연결**로 의미를 변경한다.
해시가 다른 파일을 같은 책으로 무조건 덮어쓰지 않고 새 책으로 추가한다.

목차/북마크/Reader Settings는 Bottom Sheet 가능.
Reader 본문 검색과 인코딩 미리보기는 좁은 화면에서 full-screen을 허용한다.
백업/통계/볼륨키 설정처럼 미구현 P1 버튼을 disabled 상태로 잔뜩 노출하지 않는다.

# 41. 구현 전 남아 있는 선택 사항

**제품 수준의 기본값은 이 문서에서 정했다. 남은 것은 구현 증거가 필요한 기술 검증이다.**

| 결정 | 기본값 | 증거 담당 |
|---|---|---|
| UI 공유 | Android Compose; KMP core만 | M00 architecture check |
| DB | Room KMP + bundled SQLite | M00 실제 빌드, M10 migration/16KB |
| EPUB engine | 안정 Readium Android Navigator | M01 import/restore/security probe |
| TXT encoding | 플랫폼 strict decoder + 수동 선택 | M01 fixture 검증 |
| 파일 보관 | 내부 관리 복사본 | M02 journal recovery |
| 진행도 | revision + session/sequence + sampling | M03/M04 DB·process tests |
| 최종 minSdk | 26 초기값 | M00 의존성/제품 매트릭스 |
| target/compile/JDK 등 | 안정 호환 조합 실측 고정 | M00 `DEPENDENCIES.md` |

M00/M01에서 대안이 필요하면 영향 범위가 최소인 ADR을 작성한다.
P0/보안/데이터 보존 기준을 완화하는 대안은 구현 편의를 이유로 자동 채택하지 않는다.
작업 가능한 독립 범위는 계속하되 실제 blocker를 문서에 남긴다.

# 42. v1 Definition of Done

서재/import/세 Reader/검색(TXT·EPUB)/북마크/기본 설정/이어읽기/삭제/복구가 연결된 실제 사용자 경로.
모든 locator/인코딩/세션 회귀와 필수 보안 테스트 통과.
동일 contentRevision에서 exact restore와 허용 fallback 결과를 구분.
Android 지원 매트릭스, 강제 종료, 시스템 배율, Back, OEM/창 크기, native compatibility 검증.
실기기 성능·메모리 기록과 알려진 제한을 문서화.
최종 manifest/권한/자동 백업/라이선스/서명·패키징 검토.
로그인/책 업로드 없이 관리 복사본을 완전 오프라인 독서 가능.

README, TESTING, DEPENDENCIES, WORKLOG, TASKS 상태가 실제 코드와 일치해야 한다.
빈 인터페이스/placeholder 화면/미실행 테스트 파일 존재만으로 기능 완료를 체크하지 않는다.

# 43. 첫 구현 PR 제안

**M00 — `bootstrap-mobile-foundation`**

- 현재 저장소/상위 지침/미커밋 변경을 확인하고 변경 범위를 고정.
- Kotlin/Android/KMP/Room의 안정 툴체인 고정.
- 실제 설치 가능한 Android 앱, 서재/검색/설정 탭, Empty/Loading/Error 구분.
- shared locator envelope/model, Room DB open/insert/query smoke와 schema export.
- 핵심 UI navigation/테마의 최소 구현.
- Gradle wrapper, doctor, ci-check, 기본 CI와 테스트 report.
- API/버전/실행 명령/환경 제한 기록.

TXT renderer/EPUB 전체/Comic/동기화/AI는 이 PR에 넣지 않는다.
M01 EPUB probe용 접점만 문서화한다.
실제 Android SDK나 네트워크가 없어서 build를 못 했으면 `BLOCKED_ENV`로 보고하고,
코드/테스트 작성 부분과 실행 검증 부분을 나누어 완료 현황을 남긴다.

# 44. Pro 검증 요청 프롬프트

후속 감사는 `docs/BookReader_Mobile_Codex_Prompts.md`의 “읽기 전용 감사”를 사용한다.
이 계획을 다시 일반적인 아이디어 목록으로 되돌리지 말고, 현재 작업의 구현 증거를 검증한다.
판정은 계획/코드 리뷰/자동 테스트/실기기/보안/출시를 구분한다.

# 45. Codex 구현 시작 프롬프트

`docs/BookReader_Mobile_Codex_Prompts.md`의 “첫 실행”을 사용한다.
첫 작업은 M00 하나이며, 완료 결과와 다음 M01의 준비 조건을 보고한다.
파일이 첨부됐지만 저장소에 없으면 제공받은 실제 내용을 docs에 보존한다.
없는 문서를 읽었다고 가정하거나 과거 대화가 Codex에 전달됐다고 가정하지 않는다.

# 46. 구현 직전 최종 체크리스트

## 이번 개정안에서 기본 결정 완료
- [x] Android 우선, PC 포팅/동기화 비목표.
- [x] Android Compose + KMP core + Room 계열 DB 경계.
- [x] 내부 관리 복사본과 journal 기반 import.
- [x] TXT/EPUB/Comic locator 계약과 version/revision.
- [x] 저장 허용 게이트/세션/주기 저장 원칙.
- [x] EPUB 기본 SDK 경로와 보안 fail-closed 기준.
- [x] P0/P1 구분 및 최종 세 포맷 출시 범위.
- [x] 인코딩 수동 선택, 메모리 한도, 오류 UX.
- [x] 테스트/작업 단위/산출물 계약.

## 아직 실행되지 않은 검증
- [ ] M00 안정 라이브러리 조합의 실제 dependency resolve/build.
- [ ] M01 Readium host/locator/보안 경로 검증.
- [ ] M01 대상 Android의 CP949 fixture.
- [ ] 각 작업의 unit/integration/instrumentation.
- [ ] 강제 종료/디스크 부족/재생성/16KB/실기기 검증.
- [ ] 수치 성능 목표 측정.
- [ ] 최종 출시 감사.

# 47. 최종 제품 원칙

**파일을 넣는다 → 빠르게 읽힌다 → 편하게 넘긴다 → 앱을 닫는다 → 서재의 이어읽기 바를 눌러 정확히 이어 읽는다.**

정확함은 “페이지 번호가 같음”이 아니라 같은 내용 해석의 같은 문장을 다시 찾는 것이다.
안전함은 “에러를 숨김”이 아니라 실패해도 기존 책과 독서 기록을 보존하는 것이다.
개발 완료는 문서/화면 개수가 아니라 검증 가능한 사용자 경로와 테스트 증거로 판단한다.

---

# 부록 A. 비신뢰 입력의 초기 자원 한도

모든 수치는 **제품의 초기 설계 한도**이며 표준의 최대값/실측 가능값이 아니다.
작은 값을 넘어도 즉시 앱 전체를 중단하지 않고 해당 파일/리소스의 오류로 처리한다.

| 대상 | 초기 한도 / 정책 |
|---|---|
| 단일 TXT 원본 | 256MiB |
| 단일 EPUB 원본 | 512MiB |
| 단일 Comic ZIP 원본 | 2GiB |
| ZIP 엔트리 수 | 20,000개, 중첩 압축 재귀 금지 |
| ZIP 논리적 총 비압축 크기 | 4GiB; 선언값 검증과 실제 스트리밍 해제량 제한을 함께 적용 |
| 이미지 엔트리 | 압축 해제된 파일 바이트 64MiB; decode 메모리는 별도 더 작은 예산 |
| EPUB XML/HTML/CSS 단일 파싱 리소스 | 16MiB, 실패 시 명확한 지원 한도 오류 |
| 최대 압축 경로 길이/깊이 | 1,024자 / 32단계 |
| TXT query 길이 | 256 UTF-16 단위 |
| 검색 UI 결과 | 50개 단위, 동시 보유 최대 1,000개 |
| 이미지 decode | 동시 1개 기본, bytes/pixels/메모리 사전 검사 |

ZIP central directory의 size/CRC를 맹신하지 않는다.
actual stream에 각 리소스와 작업 전체의 bytes/time/cancellation budget을 적용한다.
같은 리소스를 다시 읽는 정상 동작과 “논리 총크기” 합산을 구분하여 잘못된 누적 초과를 만들지 않는다.
압축률 하나만으로 정상 텍스트를 거부하지 않는다.
이미지 dimensions 계산은 Long으로 overflow를 검사하고 디코더 전에 안전한 크기 결정을 한다.

# 부록 B. 데이터 보존 승인 테스트

| ID | 재현 조건 | 합격 기준 |
|---|---|---|
| SAFE-01 | progress read 예외 후 기본 화면 콜백 | 기존 progress/bookmark 변경 없음 |
| SAFE-02 | A 책 저장 지연 중 B 책 열기 | A/B 레코드 분리, B에 A locator 저장 없음 |
| SAFE-03 | 같은 책 이전 세션 write가 새 세션 뒤 도착 | epoch로 오래된 write 거부 |
| SAFE-04 | 새 페이지→뒤로 이동의 save 순서 경쟁 | sequence 기준 최신 실제 의도 보존, 최대 offset 비교 금지 |
| SAFE-05 | import copy/rename/DB commit 각 지점 종료 | 기존 서재 유지, 재실행 recovery 멱등 |
| SAFE-06 | 읽는 책 삭제 중 save/decode/index 실행 | 책 재생성/다른 파일 삭제 없음 |
| SAFE-07 | disk-full로 progress write 실패 | 성공 UI 금지, 마지막 durable 기록 유지 |
| SAFE-08 | index/cover 전체 삭제 후 재실행 | 영구 원본/locator 유지, 재생성 가능 |
| SAFE-09 | 60초 연속 스크롤 | 주기적 commit 존재, debounce starvation 없음 |
| SAFE-10 | host가 force-stop 후 다시 실행 | 서재 먼저, 이어읽기로 last committed anchor |
| SAFE-11 | migration 예외 | 빈 DB로 자동 초기화하지 않음 |
| SAFE-12 | settings reflow 중 임시 0 locator | 복원 전/중 위치로 기존 기록 덮어쓰기 없음 |

force-stop은 테스트 대상 프로세스 자신이 아닌 host/외부 runner가 수행한다.
Activity recreation만 수행한 테스트를 process-death 테스트라고 부르지 않는다.
force-stop 뒤 미커밋 in-memory 상태는 보존 보장 대상이 아니다.
전원 차단/저장장치 손상은 별도 장애 모델이며 이번 강제 종료 테스트와 구분한다.

# 부록 C. ADR와 검증 기록 규칙

필수 ADR:
`0001-android-first-and-kmp-boundary`,
`0002-managed-file-copy-and-import-journal`,
`0003-locator-normalization-and-revision`,
`0004-progress-writer-ordering`,
`0005-epub-engine-and-security`,
`0006-dependency-toolchain`,
`0007-backup-and-privacy`.

각 ADR은 상태, 선택, 대안, 이유, 실패 조건, 검증 결과, 변경 시 영향 범위를 기록한다.
확정 정책과 아직 검증 안 된 구현 가정을 한 칸에 섞지 않는다.
실행 결과는 `PASS / FAIL / NOT_RUN / BLOCKED_ENV` 중 하나로 표시한다.
문서상 요구사항을 정했다는 이유만으로 테스트 결과를 PASS로 만들지 않는다.

# 부록 D. 공식 근거

아래는 2026-10-01에 확인한 자료다. [Sxx]는 외부 사실의 근거이며,
본 문서의 작업 분해·성능 수치·크기 제한·기본 UX는 이번 리뷰의 설계 제안이다.
동적으로 바뀌는 문서는 실제 구현 때 고정 버전과 대조한다.

- **[S01] Kotlin Multiplatform platform stability** — 플랫폼 안정성; 앱/엔진 전체 재사용 보장은 아님.  
  `https://kotlinlang.org/docs/multiplatform/supported-platforms.html`
- **[S02] Room KMP setup** — KMP 구성과 플랫폼 차이; 확인 당시 예시에 alpha 포함.  
  `https://developer.android.com/kotlin/multiplatform/room`
- **[S03] Room 3 release notes** — 3.0.1 안정 릴리스 확인; 조합의 실제 빌드는 미검증.  
  `https://developer.android.com/jetpack/androidx/releases/room3`
- **[S04] Readium Kotlin 3.4.0** — EPUB 지원/배포 모듈/요구 환경.  
  `https://readium.org/kotlin-toolkit/3.4.0/`
- **[S05] Readium 3.4.0 Navigator guide** — Android Fragment Navigator와 alpha Compose 경로 구분.  
  `https://readium.org/kotlin-toolkit/3.4.0/guides/navigator/navigator/`
- **[S06] Readium Locator model** — href/type/locations/text 등 locator 구조.  
  `https://readium.org/architecture/models/locators/`
- **[S07] Android Storage Access Framework** — 문서 선택/URI 접근/지속 권한의 범위.  
  `https://developer.android.com/training/data-storage/shared/documents-files`
- **[S08] Android Zip Path Traversal** — 엔트리 경로 검증 필요.  
  `https://developer.android.com/privacy-and-security/risks/zip-path-traversal`
- **[S09] Android Loading Large Bitmaps Efficiently** — 크기 조회 및 다운샘플 decode.  
  `https://developer.android.com/topic/performance/graphics/load-bitmap`
- **[S10] Android Auto Backup** — 기본 백업 대상, cloud/D2D, 버전별 규칙.  
  `https://developer.android.com/identity/data/autobackup`
- **[S11] Android Charset API** — 지원 검사/strict decoder 사용 근거; CP949 대상 기기 검증은 별도.  
  `https://developer.android.com/reference/java/nio/charset/Charset`
- **[S12] OpenAI: Iterating development workflows with Codex** — AGENTS.md와 단계별 계획/검증 기록의 활용.  
  `https://developers.openai.com/cookbook/examples/codex/iterating-development-workflows-with-codex`
- **[S13] Readium Navigator architecture** — 엔진 기능용 JS/CSS 주입의 구조적 가능성; 현재 SDK의 보안 기본값 증명은 아님.  
  `https://readium.org/technical/r2-navigator-architecture/`
- **[S14] Android 16KB page sizes** — native 라이브러리를 포함한 최종 패키지의 호환 검증.  
  `https://developer.android.com/guide/practices/page-sizes`
- **[S15] Android orientation and resizability** — 대형 화면의 회전/resize 정책.  
  `https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability`
- **[S16] Android WindowManager.LayoutParams** — 창 단위 밝기와 시스템 기본값 복원.  
  `https://developer.android.com/reference/android/view/WindowManager.LayoutParams`
