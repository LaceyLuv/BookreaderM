# EPUB/Comic 가져오기 journal 기반 — 2026-10-17

main `4019766`은 포맷별 source 한도와 archive 검증 hook을 기존 journal 복구에 연결한다.
TXT 256MiB/EPUB 512MiB/Comic 2GiB이며 archive revision은 원본 SHA와 policy version이다.
Android 제품 adapter/UI는 아직 archive를 지원하지 않는다. 기본 validator는 UNSUPPORTED_FORMAT을 반환한다.

Room schema 5는 import_jobs.format을 기본 TXT로 추가한다. 실제 export된 v4 DB를 migration하여
기존 book/progress/global settings, 해석할 수 없는 bookmark JSON, 진행 중 TXT journal을 보존했다.
EPUB/Comic 각각 7개 중단 지점에서 source 접근 상실 후 2회 복구를 실행했다.
실패·취소·검증 중 bytes 변경·중복 및 동일 bytes의 포맷 변경 거부도 확인했다.
테스트 파일 adapter는 coordinator의 fault 경계를 검증하며 Readium 보안 검증을 대신하지 않는다.

local source `f0885bf`의 `./scripts/ci-check.sh` exit 0: Android unit 60/shared JVM 71/EPUB unit 8,
APK/test APK/lint/schema 5 검사 PASS. [원본 증거](evidence/archive-import-validation/local-ci/summary.json).
초기 테스트의 잘못된 DAO 메서드 이름으로 인한 compile FAIL도 보존했다.
실제 Android migration/import, EPUB 제품 host, Comic decode/메모리는 후속 검증이다.
