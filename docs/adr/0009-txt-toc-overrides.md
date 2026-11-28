# ADR 0009 — TXT 목차의 파생 후보와 사용자 수정

상태: TXT 목차 단위 구현·자동 검증 완료. 2026-10-29. [사용자 계획](../reference/BookReaderM_Product_Improvement_Request_2026-10-24.md).

자동 목차는 기존 canonical UTF-16 위치에서 만드는 파생 결과다. Reader가 준비된 뒤 목차를 열면
Reader 소유 코루틴에서 분석하며 UI를 막지 않는다. 전체 본문을 메모리에 올리거나 원본에 쓰지 않는다.
창16,384단위/행256단위/제목120단위/자동 후보5,000개로 제한하고 취소를 검사한다.
목차를 닫으면 실행 중 스캔을 취소하고, 완성한 결과는 같은 Reader 안에서만 재사용한다.
캐시 reader가 surrogate 경계 때문에 짧게 반환한 실제 길이로 다음 창을 읽는다.

자동 후보를 영구 기록과 섞어 전체 JSON으로 덮어쓰는 방식을 사용하지 않는다.
Room6→7은 `txt_toc_overrides` 하나만 추가한다. 키는 bookId/contentRevision/offset이며
사용자 제목·제외 여부·schemaVersion·낙관적 충돌 검사용 changeId를 저장한다.
자동 제목 변경, 제외 및 복구, 현재 위치에 수동 추가를 지원한다. 현재 revision당 수정은1,000개다.
인코딩 변경 후 다른 revision에 이전 목차를 적용하지 않으며 이전 수정 기록은 보존한다.

읽기는 일관된 DB snapshot을 사용한다. 쓰기는 같은 writer transaction에서 현재 READY TXT 책,
contentRevision/sessionEpoch, 사용자가 읽은 기존 row를 재검사한다. 지원하지 않는 버전/값이나
한도 초과 데이터가 있으면 저장하지 않는다. 낙관적 충돌은 재조회 후 재시도한다.
작업 취소는 DB transaction 내부에서 호출자 Job도 확인하여 rollback한다.
원본·Book 필드·진행도·북마크·설정·서재 분류·epoch/sequence를 변경하지 않는다.
제목은 제어 문자/잘못된 surrogate를 거부하고 새 위치는 canonical reader로 경계를 확인한다.

목차 클릭만 기존 `navigateTo` 경로를 사용한다. 실제 측정 위치가 보인 뒤 기존 저장 절차를 따른다.
목차 분석·편집·제외 자체로 진행도를 저장하거나 Reader 세션을 새로 만들지 않는다.

검증:1–6 스키마의 원본 기록/서재 보존과 migration rollback, 미래 데이터/동시 수정/오래된 세션/
삭제된 책/자원 한도/DB statement 오류, 실제 Reader 편집/복구/이동/재열기 및 외부 force-stop을 구분한다.
결과는 [0.7.0 개발 기록](../READER_0_7.md)을 따른다. 형광펜/메모·백업 테이블을 선행 추가하지 않는다.
