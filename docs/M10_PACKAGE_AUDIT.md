# APK 정적 패키지 감사 — 진행 중

`scripts/audit-apk.py`는 정확한 소스 SHA와 APK hash를 기록하고 실제 aapt2/apksigner/zipalign 및 ELF PT_LOAD를 읽는다. 결과는 [원본 JSON](evidence/m10-package-audit/)에 보존한다.

| APK 소스 | 결과 | 확인 범위 |
|---|---|---|
| EPUB 제품 브랜치 bcc2081 | PASS | arm64-v8a/armeabi-v7a/x86/x86_64, native 8개 ELF·ZIP 16KB 정렬, debug 서명 검증, allowBackup=false, cleartext=false |
| Comic 제품 브랜치 207d2a1 | PASS | 같은 정적 항목; APK SHA-256 e45d825f0b2ddda9de396643996c0279ff844a76ec530dfe6a37b6fcb6600d62 |

실제 요청 권한은 ACCESS_NETWORK_STATE다. INTERNET/전체 파일 접근 권한은 없다. 첫 스크립트는 receiver 보호 권한 DUMP를 요청 권한으로 잘못 집계했으며 실패 JSON도 보존했다. 수정 스크립트는 uses-permission 요소만 집계한다.

앱 백업 규칙은 root/file/database/sharedpref/external 및 device 영역을 cloud와 device-transfer에서 제외한다. 이 문서의 OS별 실제 백업/이전 동작 검증은 NOT_RUN이다.

재현 명령(먼저 해당 소스에서 APK를 빌드):

```bash
source /workspace/toolchains/mobile-env.sh
./gradlew :androidApp:assembleDebug
python3 scripts/audit-apk.py androidApp/build/outputs/apk/debug/androidApp-debug.apk \
  --source-sha "$(git rev-parse HEAD)" --output build/apk-audit.json
```

이는 정적 debug 패키지 검사다. physical 16KB 기기의 실행, Android 지원 버전별 UI, 기기 성능/PSS/GPU, 실제 TalkBack, 최종 release 축소/서명 패키지, 전이 dependency 전체 라이선스 감사와 전체 P0 gate는 NOT_RUN이다. 해당 브랜치는 main 제품 기능으로 통합되기 전이며 출시 승인 근거가 아니다.
