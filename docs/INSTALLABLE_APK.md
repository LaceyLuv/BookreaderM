# 설치용 APK 수정 — 2026-10-22

앞서 제공한 release-unsigned APK는 서명이 없어 직접 설치할 수 없었다.
실제 `apksigner verify --verbose`가 exit 1, `DOES NOT VERIFY / Missing META-INF/MANIFEST.MF`를 반환했다.
AAB도 Android 파일 관리자에서 직접 설치하는 파일이 아니다.

검증 소스 `bb0f53787d2e26a0fcc321af31cf033d935eaed6`의 기존 release APK를 변경 없이
보존된 Android 개발용 키로 서명해 **BookreaderM-0.2.0-test-signed.apk**를 생성했다.
새 키를 생성하거나 기존 키를 교체하지 않았다. keystore는 저장소나 배포 파일에 넣지 않는다.
앱 내용은 이전 APK의 모든 ZIP entry 바이트와 대조해 동일함을 확인했다.

보존 파일: `/workspace/artifacts/BookreaderM/bb0f537/installable/BookreaderM-0.2.0-test-signed.apk`

SHA-256: `cfeb567ff16aa1f89e0f97727268e970317a159e2f4ba8483c9b54c97d457fd7`

- APK Signature Scheme v2/v3 검증 PASS, `apksigner verify --verbose --print-certs` exit 0.
- 서명 필수 모드의 `scripts/audit-apk.py` exit 0 PASS. 4 ABI, native ELF 8개 및 ZIP 16KB 정렬 PASS.
- applicationId `org.bookreader.mobile`, versionCode 2/versionName 0.2.0, minSdk 26(Android 8.0 이상).
- 실제 기기 설치는 NOT_RUN: `adb devices -l`에 연결 기기가 없다.

[검증 원본](evidence/installable-apk-bb0f537/summary.json)을 따른다.
이 파일은 **개발용 키로 서명한 설치 시험용 APK**다. 정식 배포 서명·출시 승인 완료를 뜻하지 않는다.
향후 같은 앱을 업데이트하려면 같은 서명 키를 유지해야 한다.
기존 설치본과 서명이 다르면 업데이트가 거부될 수 있으므로, 오류 확인 없이 앱을 삭제해 사용자 책·진행도를 잃게 하지 않는다.
사용자 기기의 정확한 오류와 Android 버전은 아직 확인되지 않았으므로 서명 외 원인을 모두 해결했다고 주장하지 않는다.
