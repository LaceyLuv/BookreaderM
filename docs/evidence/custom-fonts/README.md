# 사용자 글꼴·굵기 검증

## 최종 판정

`verified-4449f1e/`는 build40050209007/device40052571291의 원본과 최종 요약이다.
source4449f1e6094a5fb9546ac4ed4ea3de04c2f4b351, build164개, API26/34/36 각각 native60+host6 PASS.
필수 사례를 생략하지 않았고 timeout/허용오차를 늘리지 않았다.
첫 동일 소스 실행의 쪽수 timeout은 새 실행에서 재현되지 않았으며 이를 원인 수정으로 주장하지 않는다.
`product-equivalence-058-444.json`은 직전 전체 PASS 소스와 현재 APK의 실행 코드/자원이 같음을 기록한다.
`upload-attempt/`는 미게시 초안 생성 및 HTTP400 첨부 실패 기록이다. 다운로드 가능 링크가 아니다.

## 소스와 증거

- `local/`: 로컬 명령 로그와 단위 XML. 구현 중 실행에는 소스 보완이 있었으므로 정확한 최종 소스 판정은 원격 CI를 따른다.
- `verified-058d2b8/`: source 058d2b8bebfdc08c7d028554c87609d51cc4d463, build 40049858896/device 40049858987.
  Android unit 71/shared JVM 74/EPUB 16/실제 ENOSPC 3, API26/34/36 각각 native 60 + host 6 모두 PASS다.
  원본 XML, mandatory manifest, 소스/clean 상태, artifact SHA-256, host force-stop 후 PID 교체를 확인했다.
- `package-4449f1e/`: 현재 0.5.0 APK의 서명·정적 감사·메타데이터. 제품 코드는 058d2b8과 같다.
- `package-f9243cf/`: 선택기 보완 전 중간 생성물이며 최종 배포 후보가 아니다.
- `preview/`: 4449f1e API36 실제 TXT/EPUB 사용자 글꼴·굵게 캡처와 해석 범위.

## 실패 이력

- `ci-b7cc57c/`: build 40048566037 PASS164; device 40048566127은 API34/36 신규 2 FAIL,
  API26은 7 FAIL. 엄격한 테스트 선택기가 CATEGORY_OPENABLE 없는 요청을 거부했고,
  TXT는 스크롤 관성 중 transient anchor를 관찰했다. 선택기 crash 뒤 cleanup·후속 사례 실패도 보존했다.
- `ci-f9243cf/`: build 40048802230 PASS164; device 40048802322는 API34 2/API36 3/API26 8 FAIL.
  선택기 보완과 실제 파일 font descriptor 비교 전 결과다. API36의 기존 TXT helper는 dialog 종료 후 READY를
  설정 저장 완료로 오인할 수 있어 4449f1e에서 settingsLoaded 교체와 settingsSaving 종료까지 기다리도록 고쳤다.
  API26에는 기존 Comic 테스트의 Compose SnapshotStateObserver 스레드 경합도 있다.
  이 Comic 문제에 임의 제품 변경을 하지 않았으며 058d2b8 전체 API26 실행에서는 재현되지 않았다.
- `ci-4449f1e-attempt1/`: 최신 build 40050209007 PASS164; device 40050208939의 API36 PASS66,
  API34 native 60 중 기존 TXT 쪽수 표시 대기 1 FAIL(30초), 신규 글꼴 2건 PASS, host NOT_RUN.
  API26 최종 job conclusion은 `cancelled`이며 원본을 함께 보존했다. 동일 제품 코드의 직전 전체 실행은 PASS였으나 이번 timeout을 숨기지 않는다.
  30초 기준·필수 사례·위치 동일성 검사를 유지한 새 전체 matrix40052571291은 PASS다.

실기기 설치·실기기 성능·공개 릴리스 게시 결과는 이 자동 검사에 포함하지 않는다.

## OTF fixture

제3자 글꼴을 변형하지 않고 사각형 윤곽선으로 자체 제작했다. 생성 도구는 fontTools 4.61.1이다.
필요할 때 별도 도구 환경에 `fonttools==4.61.1`을 설치해 `python scripts/generate-font-fixture.py`로 재생성한다.
앱/CI 빌드의 새 의존성이 아니다. 원본/hash는 `fixtures/font-manifest.json`과 생성 스크립트에 있다.
release APK에서 debug fixture 제외를 확인했다.

첫4449f1e API26 job은 native 60개 중 15개 완료까지 보고한 뒤 진행 보고가 멈췄고,
45분 job 제한에서 취소됐다. 완성된 native XML이 없으며 host는 NOT_RUN이다.
job 원본은 `ci-4449f1e-attempt1/api26-job.log`에 있다. 이 중단의 근본 원인을 특정하지 않았고,
새 실행의 PASS를 중단 원인 수정으로 표현하지 않는다.
