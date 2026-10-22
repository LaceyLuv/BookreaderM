# 개발 재개 결과 — 2026-10-22

요청된 CI 확인 → 오류 수정 → 전체 검증 → main 통합 → unsigned 패키지 재생성을 완료했다.
검증 및 release 빌드 소스는 `bb0f53787d2e26a0fcc321af31cf033d935eaed6`다. 원격 main의 `d775783`에서 이 커밋으로
fast-forward한 뒤 문서와 검증 원본만 추가했다. 제품 코드·테스트·의존성은 검증 소스와 동일하다.

## 변경과 검증

EPUB 검색 요청을 화면 교체 중에도 보존하고, 현재 화면의 실제 표시가 확인된 뒤에 진행도를 저장한다.
검색·Activity 재생성·글자 크기 변경·재열기에서 실제 `p179` 표시와 기존 progression 오차 `.03`을 모두 유지했다.
검색 직후 화면 교체 회귀도 통과했다. Comic 화면·그리드·취소형 seek/return 후속을 main에 함께 반영했다.
테스트는 실제 SDK CSS 적용, 동일 viewport, 완료된 위치 알림을 관찰한다.
API26 DocumentsUI에서는 화면 밖 행을 스크롤하고, 정확히 찾은 파일의 표시 좌표에 실제 터치를 보낸다.
원래 파일 선택·권한 grant·본문·진행도 assertion을 삭제하거나 완화하지 않았다.

[빌드 CI](https://github.com/LaceyLuv/BookreaderM/actions/runs/39757163283)와
[기기 CI](https://github.com/LaceyLuv/BookreaderM/actions/runs/39757163184)가 모두 SUCCESS다.
ZIP SHA-256, source.log, clean source 상태, API 번호, 필수 case identity 및 XML을 대조했다.

| 검사 | 결과 |
| --- | --- |
| Android unit / shared JVM / EPUB unit | 66 / 74 / 16 PASS |
| 실제 파일시스템 ENOSPC | 3 PASS |
| API26 native / TXT·EPUB·Comic host | 54 / 6 PASS |
| API34 native / TXT·EPUB·Comic host | 54 / 6 PASS |
| API36 native / TXT·EPUB·Comic host | 54 / 6 PASS |

실패·오류·skip은 0이다. Host는 포맷별 2단계이며 force-stop 후 PID 부재와 cold launch의 새 PID를 확인했다.
Activity 재생성을 process death로 계산하지 않았다. 세 API의 검색·재생성·글자 크기·재열기 모두 `p179`가 보인다.
글자 크기는 실제 DOM에서 16px → 16.8896px로 증가했고 허용 오차는 `.03` 그대로다.
[기계 검증 요약·원본](evidence/epub-reflow-navigation/verified-bb0f537/summary.json),
[main 반영 기록](evidence/epub-reflow-navigation/verified-bb0f537/integration.json)을 보존한다.
main push가 자동으로 재실행하는 동일 SHA의 CI는 위 검증 run과 별개다.

## 최종 패키지

main의 검증 소스에서 아래 명령을 실제 실행했고 exit 0이다.

```bash
source /workspace/toolchains/mobile-env.sh
./gradlew --no-daemon --console=plain --rerun-tasks :androidApp:assembleRelease :androidApp:bundleRelease
python3 scripts/audit-apk.py /workspace/artifacts/BookreaderM/bb0f537/androidApp-release-unsigned.apk --source-sha bb0f53787d2e26a0fcc321af31cf033d935eaed6 --output /workspace/artifacts/BookreaderM/bb0f537/apk-audit.json --unsigned
jarsigner -verify /workspace/artifacts/BookreaderM/bb0f537/androidApp-release.aab
```

| 파일 | SHA-256 |
| --- | --- |
| androidApp-release-unsigned.apk | `c23ce04c4d03688c7bccca1ecf9c05e1d74b65e1ccbc929ff8d660dd5b6ba7fb` |
| androidApp-release.aab | `604e67cced0bce450282565b2ca196a3aff25bcace9e6a063ccb6131d3d0dfda` |

보존 위치는 `/workspace/artifacts/BookreaderM/bb0f537/`다.
[빌드 로그·패키지 목록·감사](evidence/epub-reflow-navigation/release-bb0f537/)를 보존했다.
APK 정적 감사 PASS: 4 ABI, native ELF 8개와 ZIP의 16KB 정렬,
allowBackup=false, cleartext=false, 권한은 ACCESS_NETWORK_STATE만 있다.
APK/AAB 모두 **NOT_SIGNED**다. AAB 검사의 exit 0과 `jar is unsigned`를 서명 성공으로 해석하지 않는다.
release 기기 설치·실행 및 업데이트 검증은 **NOT_RUN**이다.

## 환경과 남은 범위

클라우드 상태 connected, 실제 셸·Git read/push·GitHub API·CI ZIP 다운로드 성공을 확인했다.
고정 JDK17.0.16+8/SDK36/build-tools35/Gradle8.13 doctor와 후속 test APK compile/lint도 PASS다.
기존 install_script/start_skill 및 네트워크 초안은 보존했다. 새 게시나 새 작업에서의 복원 검증을 주장하지 않는다.
로컬은 KVM이 없어 native 실행을 대체하지 않았고 실제 GitHub 에뮬레이터 증거를 사용했다.

`49d1a04` API34의 TXT Compose 측정 재진입 오류는 `277469d`와 최종 `bb0f537`에서 재발하지 않았다.
원인 확정이나 수정 완료로 주장하지 않는다. 과거 실패는 [분석과 원본](NATIVE_UI_SYNCHRONIZATION.md)에 남아 있다.
재발하면 기존 로그와 새 스레드·측정 타이밍을 대조해야 한다.

물리 release 성능(30회 p95), PSS/GPU·메모리 압박·빠른 100장 UI 이동, 실제 전원 장애,
TalkBack, 16KB OS 실행, OEM backup/D2D, 전체 포맷별 공격 corpus, 새 독립 리뷰,
최종 서명·설치/업데이트·배포 승인은 미완료다. M04–M10의 전체 잔여 gate 및 M11 차단 상태를
이번 자동 검사 통과로 해제하지 않는다. 스토어 제출·배포·서명키 업로드는 수행하지 않았다.
