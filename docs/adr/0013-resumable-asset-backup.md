# ADR-0013 — 책 원본·사용자 글꼴 선택적 백업 패키징과 중단 복구(Resumable Commit)

2026-11-27. Phase 4의 두 번째 단위([Issue #3](https://github.com/LaceyLuv/BookreaderM/issues/3))다.
[ADR-0012](0012-portable-reading-records.md)의 `.brmbackup` v1(독서 기록 전용) 하위 호환을 완전히 유지하면서,
사용자가 선택적으로 **관리 중인 책 원본 파일**과 **등록된 사용자 글꼴 파일**을 함께 내보내고,
공간 부족(`ENOSPC`)이나 외부 강제 종료(`force-stop`)로 복원이 중단되더라도 기존 서재·진행도를 보존한 채
중단 지점에서 멱등 재개할 수 있는 파일 커밋 계약을 정의한다.

## 1. `.brmbackup` v1 호환 및 v2 스트리밍 컨테이너 형식

- **v1 유지 (기본값)**: 사용자가 책 원본·사용자 글꼴 포함 옵션을 모두 끈 경우 기존과 바이트 단위로 동일한 v1 포맷(`BRMBKUP\n` + version `1` + 8 MiB 이하 UTF-8 JSON)을 생성한다. 기존 v1 파일의 미리보기와 복원 경로는 변경하지 않는다.
- **v2 순차 프레임 번들**: 책 원본 또는 사용자 글꼴을 하나 이상 포함하기로 선택한 경우 `BRMBKUP\n` + big-endian version `2` 형식을 생성한다.
  ZIP 중앙 디렉터리·압축 비율 공격(zip bomb)·심볼릭 링크·임의 탐색(random-access seek) 의존성을 피하기 위해,
  SAF 단방향 스트림(`InputStream`/`OutputStream`)에서 64 KiB 청크로 순차 송수신할 수 있는 비압축 프레임 형식을 사용한다.

| 순서 | 필드 | 크기 / 규칙 |
|---|---|---|
| 1 | 매직 `BRMBKUP\n` | 8바이트 |
| 2 | 형식 버전 | big-endian 4바이트 (`2`; `<= 0` 또는 `>= 3`은 할당 전 `VERSION` 거부) |
| 3 | 매니페스트 JSON 길이 | big-endian 4바이트 (`1..8 MiB`) |
| 4 | 매니페스트 JSON SHA-256 | 32바이트 |
| 5 | 매니페스트 UTF-8 JSON (`BackupBundleV2`) | `records: ReadingBackup`, `assets: List<BackupAssetEntry>` |
| 6 | 순차 자산 프레임 (`0 until assets.size`) | 각 프레임: `BRMAST\n` (7B) + `mode` (1B, `0x00` 일반 파일만 허용) + `pathLen` (4B) + `pathUtf8` + `byteSize` (8B) + `sha256` (32B) + 원본 바이트 |
| 7 | 파일 끝(EOF) | 마지막 자산 직후 추가 바이트 존재 시 `INTEGRITY` 거부 |

## 2. 보안 및 자원 한도 (Fail-Closed)

적용 전 검증 단계(`VERIFY`)에서 다음 규칙을 하나라도 위반하면 DB·관리 책 파일·사용자 글꼴을 전혀 건드리지 않고 임시 스테이징을 즉시 삭제한다.

- **엔트리·크기 한도**:
  - `MAX_ASSET_ENTRIES = 2,012` (책 최대 2,000권 + 사용자 글꼴 최대 12개).
  - 책 자산 개별 한도: `maxImportBytes(format)` (TXT 256 MiB, EPUB 512 MiB, COMIC 2 GiB).
  - 글꼴 자산 개별 한도: 20 MiB (`FontFilePolicy.MAX_BYTES`), 글꼴 합계 최대 12개·100 MiB (`UserFontStore`와 동일).
  - 번들 전체 자산 합계 한도: 2 GiB (`MAX_BUNDLE_BYTES`).
  - 스트리밍 버퍼: 64 KiB 고정(전체 번들이나 책 원본을 메모리에 일괄 적재하지 않음).
- **경로·타입·무결성 규칙**:
  - 모든 경로는 `books/<64-hex-sha256>.bin` 또는 `fonts/<64-hex-sha256>.bin` 정규 문법만 허용한다. `..`, 선행 `/`, `\`, NUL·제어 문자, 중복 경로, 중복 `(kind, sha256)`은 거부한다.
  - 프레임 `mode` 바이트는 `0x00`(regular file)만 허용한다. 심볼릭 링크 등 비일반 파일 플래그는 즉시 `FORMAT`으로 거부한다.
  - 모든 `BOOK` 자산은 매니페스트 `records.books`의 동일 `(sha256, byteSize, format)` 항목과 1:1 대응해야 한다(미참조 고아 블롭 거부).
  - 매니페스트와 모든 자산 파일은 스트리밍 중 실제 바이트 수와 SHA-256을 계산해 대조하며, 스테이징 완료 전 콘텐츠 검증(TXT 엄격 디코딩, EPUB `SafeEpub`, Comic `ComicManifest`, 글꼴 `FontFilePolicy`+`Typeface`)을 수행한다.
  - 미리보기 완료 시 모든 자산은 앱 전용 스테이징(`filesDir/managed/backup-staging/<session-uuid>/`)에 검증 저장되므로, 미리보기 이후 외부 SAF 제공자가 원본 파일을 변조·삭제해도 검증된 데이터만 커밋된다.

## 3. Room 9 유지 및 내구성 저널 기반 멱등 재개 상태 머신

Room 9(14개 테이블)의 기존 스키마와 1→9 마이그레이션을 변경하지 않고, 기존 `withManagedBookOperations` 프로세스 전역 뮤텍스 안에서 앱 전용 저널(`filesDir/backup-journal/active.json`, 임시 파일 write → `fsync` → atomic rename → 디렉터리 `fsync`)과 기존 `BookDao.restoreVerifiedAvailability(...)`를 결합해 중단 복구를 구현한다.

1. **`STAGED` (미리보기 대기)**:
   - 검증 완료된 파일이 `managed/backup-staging/<session-uuid>/`에만 존재한다. DB와 `managed/books/`, `reader-fonts/`는 무변경이다.
   - 사용자가 취소하거나 복원 전 강제 종료되면 다음 앱 시작의 `recoverOrResume()`가 저널 없는 고아 스테이징을 자동 정리한다.
2. **`PLANNING` (DB 기록 트랜잭션 커밋 전후)**:
   - 새 책의 결정적 UUID(`plannedBookIds: sha256 -> uuid`)를 미리 생성해 `backup-journal/active.json`(`phase = PLANNING`)에 내구성 있게 기록한다.
   - 단일 SQLite `immediateTransaction`(`RoomReadingBackup.restoreWithIds`)으로 새 책(`availability = 'MISSING'`, `managedRelativePath = "books/<uuid>/original"`)과 누락된 독서 기록·책장·설정을 추가한다. 기존 책·진행도·세션 epoch는 덮어쓰지 않는다.
   - SQLite 실패(`ENOSPC`/`SQLITE_FULL`, `CHANGED_LIBRARY`, `LIMIT`) 시 DB는 전체 롤백되며 저널과 스테이징을 정리(또는 공간 부족 안내)하고 기존 데이터를 보존한다.
   - DB 커밋 직후 저널을 `phase = COMMITTING_ASSETS`로 원자 갱신한다. DB 커밋 직후·저널 갱신 직전에 프로세스가 종료되더라도, 재시작 시 `PLANNING` 저널의 `plannedBookIds`와 DB 존재 여부를 대조해 커밋 전이면 롤백·정리, 커밋 후면 `COMMITTING_ASSETS`로 자동 전이한다.
3. **`COMMITTING_ASSETS` (자산별 멱등 승격 및 `READY` 전환)**:
   - **책 자산**: `(sha256, byteSize)`로 DB 책을 조회한다. 이미 `READY`인 기존 책은 기존 관리 복사본을 절대 덮어쓰지 않고 건너뛴다. `MISSING`/`CORRUPT` 책에 대해:
     - 최종 경로(`books/<id>/original`)에 이미 파일이 존재하면(승격 직후·DB 갱신 직전 중단) 실제 SHA-256·크기를 재검증해 `restoreVerifiedAvailability`만 수행한다.
     - 최종 경로에 파일이 없으면 스테이징 파일의 SHA-256·크기를 재검증한 뒤 [ADR-0002](0002-managed-import-journal.md)의 안전한 `Files.move`(NOFOLLOW_LINKS, regular file, 대상 부재 확인, 동일 `st_dev`, `REPLACE_EXISTING`/`ATOMIC_MOVE` 미사용, 부모 디렉터리 `fsync`)로 승격하고 `restoreVerifiedAvailability`로 `READY`로 전환한다.
   - **사용자 글꼴 자산**: `UserFontStore`의 SHA-256 콘텐츠 주소 경로(`reader-fonts/<sha256>/`)에 동일 파일이 이미 있으면 멱등 생략한다. 12개·100 MiB 한도 내이면 `FontFilePolicy`+`Typeface` 재검증 후 임시 디렉터리 `fsync`→원자 이동으로 등록한다.
   - 도중 `ENOSPC`나 `force-stop`이 발생하면 `active.json`(`COMMITTING_ASSETS`)과 남은 스테이징 파일을 유지한다. 다음 앱 시작(`AppViewModel.init`) 또는 설정의 **중단된 복원 이어하기**가 이미 승격된 항목은 건너뛰고 남은 항목만 멱등 완료한다. 사용자가 공간 확보 대신 **임시 파일 정리**를 선택하면 이미 커밋된 책·기록을 건드리지 않고 저널과 남은 스테이징만 제거한다.
4. **`CLEANUP` (완료 정리)**:
   - 모든 자산 승격·확인 후 스테이징 디렉터리와 `active.json`을 삭제한다.

## 4. 개인정보·권한 경계

- `android:allowBackup="false"`, 권한 목록(네트워크 권한 없음), 서명키, 기존 관리 복사본과 Room 9 스키마를 유지한다.
- 외부 SAF URI는 일시 권한으로만 접근하며 영구 저장·로깅하지 않는다.
