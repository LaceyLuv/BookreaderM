## 2026-10-30 요청한 설치 시험용 APK — 0.7.0-dev

TXT 목차를 포함한 검증 소스05ffd50을 기존 키로 서명했다. 코드8/Room7이며 0.7.0 전체 기능 완료는 아니다.
사용자 요청에 따라 임시 ZIP 전달을 준비했다. 장기 GitHub Releases 경로는 아직 미완료이고 기존 HTTP400 업로드를 재시도하지 않았다.

- APK: `/workspace/artifacts/BookreaderM/05ffd50/installable/BookreaderM-0.7.0-dev-test-signed.apk` (21,982,367 bytes).
- SHA256: `b58061b17b41335c17ac7d662be1d325092ab19db49e3284570eabd1e44454bc`.
- ZIP: `/workspace/artifacts/BookreaderM/05ffd50/installable/BookreaderM-0.7.0-dev-test-signed.zip` (18,521,140 bytes,20MB 미만).
- ZIP SHA256: `dae06ad079607fc1fa4f89b0d6fc6ac1e8048a3f47dc8bc6a697b26574f565d2`.
- 기존 인증서/v2/v3/16KB 정적 감사, 서명 전후612 entry 동일성, ZIP CRC·추출 APK 동일성 PASS.
- 제품 source `05ffd50ab8213ec9563a92149b6ed1930c510c3b`: build189/API26·34·36 native73+host7 PASS.

[패키지 증거](evidence/product-quality/package-05ffd50/summary.json). 실제 휴대전화 설치와 대화 다운로드 성공은 미확인이다.
기존0.5.1/0.6.0 APK 및 서명키를 보존했다. 앱을 삭제하거나 데이터를 지우지 않고 같은 키 업데이트로 설치한다.
공개 릴리스를 게시하지 않았다. 다음 개발은 도구/빠른 설정·형광펜/메모이며 인계의 기존 작업 순서를 유지한다.

# 다운로드 경로 운영 기준

앞으로는 [APK 배포 방안](APK_DISTRIBUTION.md)에 따라 GitHub Releases 다운로드 페이지를 안내한다.
아래 로컬 경로는 생성물 위치 기록이며 GPT Android 앱의 대용량 미리보기 제한을 해결하는 다운로드 경로가 아니다.

# 설치 시험용 APK — 0.6.0, 2026-10-29

[서재2.0 변경·실제 화면·검증 한계](LIBRARY_0_6.md). build175/세 API native72+host7 PASS.

- 소스 `a22970b1e40eaf06c47acaa683b9d754eb66f2b4`, applicationId `org.bookreader.mobile`, versionCode7/versionName0.6.0.
- APK `/workspace/artifacts/BookreaderM/a22970b/installable/BookreaderM-0.6.0-test-signed.apk` (21,949,599 bytes).
- APK SHA256 `d531d80a7a7ac7bba7c4ebf76045f43f8722de432cec09aa8ed94c597ed13674`.
- 기존 인증서 SHA256 `605d7f79c6ec788937ecda215447235a81b3aa91437f2b1adea8d7a796ce167f`; v2/v3·4 ABI·16KB 정적 정렬 PASS.
- ZIP `/workspace/artifacts/BookreaderM/a22970b/installable/BookreaderM-0.6.0-test-signed.zip` (18,481,910 bytes).
- ZIP SHA256 `3827a112f8faff1bef3a6e95cce619258e5782f2d9dd40894db2404678a8edc0`; CRC·추출 APK 동일성 PASS.

[패키지 원본](evidence/product-quality/package-a22970b/summary.json). 기존 키·0.5.1 APK 보존 확인.
로컬 경로는 생성물 위치이며 공개 다운로드 링크가 아니다. 실제 휴대전화 업데이트·대화 다운로드·공개 릴리스는 미완료다.
이전 업로드 HTTP400 경로를 재시도하지 않았다. 업데이트 오류 시 앱/책/진행도를 임의 삭제하지 않는다.

아래는 이전 패키지 이력이다.

# 설치 시험용 APK — 0.5.1, 2026-10-24

가져오기/상태 안내·서재 UI를 정리했다. [변경과 실제 화면](UX_POLISH_0_5_1.md)을 따른다.
build166, API26/34/36 각각 native63+host6 및 기존 키 서명/정적 감사 PASS다.

- 소스: `5a8aad2ebb35359740a2fe18bec0c3731d6e9072`, applicationId `org.bookreader.mobile`, versionCode6/versionName0.5.1.
- 파일: `/workspace/artifacts/BookreaderM/5a8aad2/installable/BookreaderM-0.5.1-test-signed.apk` (21,818,527 bytes).
- SHA256: `44a8f8b84e3c7aeb86b77f537bd20ef8bac4d0ae7e35b037dd8cab1e78bf80af`.
- 기존 인증서 SHA256: `605d7f79c6ec788937ecda215447235a81b3aa91437f2b1adea8d7a796ce167f`.
- v2/v3, 4 ABI/16KB ZIP·ELF 정렬, 서명 전후612 ZIP entry 동일성 PASS.
- 같은 폴더의 ZIP은18,362,053 bytes, SHA256 `681d0fb66cc2c80803cdc79d2b81cb7a618036746ef71c099cc135375f9c28c0`.
  ZIP CRC와 추출 APK 동일성 PASS다. 실제 대화 다운로드는 미확인이다.

[패키지 원본](evidence/product-quality/package-5a8aad2/summary.json)을 보존한다.
이 경로는 생성물 기록이며 공개 GitHub 다운로드 링크가 아니다. 실제 휴대전화 설치는 NOT_RUN이다.
이전 0.5.0 APK와 서명키를 보존했다. 업데이트 오류가 나도 기존 앱/책/진행도를 임의 삭제하지 않는다.
공개 릴리스·업로드·배포 자동화는 이번 단계에서 완료하지 않았다.

아래는 이전 패키지 이력이다.

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
