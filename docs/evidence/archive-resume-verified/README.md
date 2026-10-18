# 재개 검증 원본

`summary-8bced5f.json`은 SHA/필수 case 목록/native XML/PID 증거를 직접 확인한 결과다.
`required-tests-8bced5f.json`은 해당 commit의 원본 필수 목록이다.
최신 성공 matrix 전체와 이전 실패 API의 command/관찰/host proof/XML/logcat을 보존한다.
API26 이전 실패는 검색 결과 이동 후 저장 progression 대기 timeout이며,
API34 이전 실패는 reflow progression 비교, API36 이전 중단은 WebView 호출 중 SIGSEGV다.
이들을 이번 대기 조건 수정으로 해결했다고 해석하지 않는다.

`*.sha256`은 원격 artifact metadata digest 및 실제 ZIP bytes와 대조했다.
ZIP 전체는 `/workspace/scratch/resume-ci/<run>/`에 있다. 저장소에는 원본 보고서와 logcat,
fixture 증거를 보존하고 중복 HTML 및 binary protobuf는 생략했다.
build APK는 ZIP에 보존되며 이 디렉터리에 중복 저장하지 않았다.
원본 파일의 CRLF/공백을 정규화하지 않았다.

재검증 도구 `verify-downloads.py`는 현재 작업 환경의 다운로드 폴더와 저장소를 사용한다.

```bash
python3 docs/evidence/archive-resume-verified/verify-downloads.py \
  8bced5f4ff3136af2dc002d3c457397b7853bfe6 39098185637 39098185673
```

SHA는 `git show`로 필수 case 목록을 선택하며, 관찰된 PID의 일치·종료 후 부재·새 PID를 확인한다.
수집된 모든 host command exit도 0이어야 한다. 출력 summary는 scratch 폴더에 생성된다.
강제 종료 결과는 이미 저장 완료된 위치에 대한 검증이며 미저장 입력의 내구성을 뜻하지 않는다.
