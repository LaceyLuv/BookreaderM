# 다운로드 경로 운영 기준

앞으로는 [APK 배포 방안](APK_DISTRIBUTION.md)에 따라 GitHub Releases 다운로드 페이지를 안내한다.
아래 로컬 경로는 생성물 위치 기록이며 GPT Android 앱의 대용량 미리보기 제한을 해결하는 다운로드 경로가 아니다.

# 설치 시험용 APK — 0.5.0, 2026-10-24

사용자 TTF·OTF 등록과 보통/굵게 선택을 추가했다. [사용 안내](READER_FONTS_0_5.md)를 따른다.
서명·정적 감사, build164개 및 Android API26/34/36 각각 native60+host6 검증 PASS다.
GitHub 첨부는 실패했으므로 GitHub 다운로드는 아직 제공하지 않는다.

- 생성물: `/workspace/artifacts/BookreaderM/4449f1e/installable/BookreaderM-0.5.0-test-signed.apk`
- 소스: `4449f1e6094a5fb9546ac4ed4ea3de04c2f4b351`
- SHA-256: `839d4e85c0bd8197005069e83d76fef99d5462f2845cf7ac48cc978282d2447c`
- 크기: 21,839,729 bytes. applicationId `org.bookreader.mobile`, versionCode 5/versionName 0.5.0, Android 8.0(API26) 이상.
- 기존 개발용 인증서 유지: `605d7f79c6ec788937ecda215447235a81b3aa91437f2b1adea8d7a796ce167f` (SHA-256).
- v2/v3 서명·서명 필수 정적 감사·4 ABI/8 ELF 및 ZIP 16KB 정렬 PASS.
- 서명 전후 모든 ZIP entry 바이트 동일, debug 전용 OTF fixture는 release에 포함되지 않음.

실제 휴대전화 설치는 NOT_RUN이다. 기존 책과 진행도를 보존하기 위해 오류 확인 없이 앱을 삭제하지 않는다.
0.5.0 초안 APK 첨부는 HTTP 400으로 FAIL했고 첨부가 없다. 자동 배포 workflow도 구현 전 계획이다.
0.4.0의 미게시 초안에 이 버전을 섞지 않는다. 이 로컬 경로를 GPT Android 미리보기 제한의 해결책으로 안내하지 않는다.
[패키지 감사 원본](evidence/custom-fonts/package-4449f1e/summary.json)을 보존한다.

임시 ZIP: `/workspace/artifacts/BookreaderM/4449f1e/installable/BookreaderM-0.5.0-test-signed.zip`.
18,384,516 bytes이며 20,000,000 bytes 미만이다. CRC와 압축 해제 후 APK SHA-256 동일성을 확인했다.
ZIP을 풀어 안의 APK를 설치한다. 대화 앱에서의 실제 다운로드는 미확인이며 GitHub Releases 장기 배포를 대체하지 않는다.
[ZIP 검증](evidence/custom-fonts/package-4449f1e/zip-fallback.json)을 보존한다.

아래는 이전 패키지 이력이다.

# 설치용 APK — 0.4.0, 2026-10-23

글꼴·페이지 전환·쪽수 표시·서재 카드 열기를 개선했다. [변경 내용과 페이지 수 기준](READER_FEATURES_0_4.md)을 따른다.
검증 소스가 main에 반영됐으며 API26/34/36 각각 native 58 + host 6, build 161개가 PASS다.

- 파일: `/workspace/artifacts/BookreaderM/d1570aa/installable/BookreaderM-0.4.0-test-signed.apk`
- 소스: `d1570aa92edddb676be3c5bc311d53506f2e9ad9`.
- SHA-256: `cf74e8ea6ede2fa360ddb7d75210b0822b87aa4e512005e78d2a0512582dbdf2`
- 크기: 21,823,345 bytes.
- applicationId `org.bookreader.mobile`, versionCode 4/versionName 0.4.0, Android 8.0(API26) 이상.
- 기존 개발용 인증서 유지: `605d7f79c6ec788937ecda215447235a81b3aa91437f2b1adea8d7a796ce167f` (SHA-256).
- v2/v3 서명·서명 필수 정적 감사·4 ABI/8 ELF 및 ZIP 16KB 정렬 PASS.
- 서명 전 APK의 모든 ZIP entry와 번들 글꼴 바이트를 서명 후에도 대조했다.

기기에 APK를 내려받아 열어 설치한다. 기존 시험용 앱과 같은 서명을 유지했다.
실제 휴대전화 설치는 연결 기기 부재로 NOT_RUN이며 정식 배포 서명은 아니다.
설치 오류가 나더라도 책·진행도 보존을 위해 기존 앱을 임의 삭제하지 않는다.
unsigned APK와 AAB는 직접 설치 파일로 안내하지 않는다.
[패키지 원본](evidence/reader-features/package-d1570aa/summary.json)을 보존한다.

아래는 이전 패키지 이력이다.

# 설치용 APK — 0.3.0, 2026-10-23

독서 화면·터치 동작 개편이 반영된 설치 시험용 APK다.
본문 중앙을 탭하면 도구가 나타나고, 좌우 탭으로 이동한다. 설정과 도구는 남색으로 구분한다.

- 파일: `/workspace/artifacts/BookreaderM/c238325/installable/BookreaderM-0.3.0-test-signed.apk`
- 소스: `c2383259eb1c15794e4c1f6ecfdbb198c6c6f968` (main 반영 완료).
- SHA-256: `5f23b00fe084b27975d283a5892faa87328f124bfe4b34e5e671f4a55baa7cca`
- 크기: 20,065,731 bytes.
- applicationId `org.bookreader.mobile`, versionCode 3/versionName 0.3.0, Android 8.0(API26) 이상.
- 기존 0.2.0의 개발용 서명 인증서 유지: `605d7f79c6ec788937ecda215447235a81b3aa91437f2b1adea8d7a796ce167f` (SHA-256).
- APK v2/v3 서명 검증·서명 필수 정적 감사·4 ABI/8 ELF 및 ZIP 16KB 정렬 PASS.

APK를 기기에 내려받아 열어 설치한다. 기존 시험용 0.2.0과 같은 인증서이므로 업데이트 조건을
유지했지만, 실제 휴대전화 설치는 연결 기기 부재로 NOT_RUN이다. 설치 오류가 나더라도
책·진행도 보존을 위해 기존 앱을 임의 삭제하지 않는다.
unsigned APK와 AAB는 직접 설치용 파일이 아니다. 이 APK는 개발용 서명이며 정식 배포 서명은 아니다.

[디자인과 자동 검증](READER_DESIGN_2026-10-23.md),
[패키지 요약](evidence/reader-design/verified-c238325/package/summary.json),
[서명·정적 감사 원본](evidence/reader-design/verified-c238325/package/audit.json)을 따른다.

## 이전 0.2.0 설치 오류 수정 이력 — 2026-10-22

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
