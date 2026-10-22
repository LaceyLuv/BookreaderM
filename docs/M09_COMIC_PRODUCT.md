# Comic 제품 경로 — 2026-10-17

`codex/comic-product`는 ZIP/CBZ를 private 관리 복사본으로 가져와 한 장/연속 보기와 웹툰 프리셋,
LTR/RTL·fit·pinch/double tap/pan·손상 이미지 건너뛰기·북마크·이어읽기로 연결한다.
main 통합을 완료했다. 최종 `bb0f537`은 API26/34/36 각각 native 54 + TXT/EPUB/Comic host 6 PASS다. [엔진 보고서](M09_COMIC_ENGINE.md)의 초기 미구현 문구는 과거 범위다.

원본 source hash와 sortVersion 1에 묶인 entryKey가 영구 위치다. imageIndexHint는 동일성 기준이 아니다.
설정 변경/재생성에서 entryKey와 normalized anchor를 유지하며, 깨진 이미지로 이동한 사실만으로
마지막 성공 위치를 덮어쓰지 않는다. 새 이미지의 실제 표시가 성공한 뒤에만 jump를 저장한다.
9dfb884는 취소 가능한 위치 슬라이더와 jump 전 위치로 한 번 돌아가는 메뉴를 추가했다.

연속 보기의 각 이미지는 viewport보다 큰 비영 높이 placeholder를 갖고 실제 decode 후 높이/비율 위치를 복원한다.
긴 이미지 높이는 layout 한도 안에서 전체 이미지가 보이도록 줄인다. 내용 끝부분을 잘라서 한도를 맞추지 않는다.
확대 버튼은 같은 bitmap을 재사용하는 별도 확대 화면을 열고 pinch/pan이 연속 스크롤과 충돌하지 않게 한다.
확대 배율은 영구 저장하지 않는다. RTL은 한 장 이동 방향을 바꾸며 세로 스크롤 방향은 그대로다.

decode concurrency 1, bitmap 예산 min(64MiB, heap/5), 최대 decode 치수 4096,
회전 중 임시 bitmap 예약과 cancel/lease 해제를 적용한다. 원본 ZIP entry를 디스크 파일명으로 사용하지 않는다.
region/tile 구현을 주장하지 않으며 긴 PNG/WebP는 안전한 다운샘플과 해상도 안내로 처리한다.

## 실제 증거와 제한

7eaa0f5의 Android decoder 2개는 각 1080×1600인 100장과 PNG 1080×30000,
WebP 1080×16000, EXIF·손상·animated 거부·취소 임시 파일 정리를 확인했다.
[메모리 원본](evidence/m09-comic-validation/native-7eaa0f5/summary.json)은 bitmap/PSS를 구분한다.
964f8cc의 Comic 제품 test는 RTL swipe, pinch/pan, broken 03.png 비저장/04.png skip,
웹툰·확대·북마크·실제 Activity 재생성/재열기를 통과했다. 같은 suite는 EPUB 문단 검사 1 FAIL이므로 전체 PASS가 아니다.
207d2a1의 이동 중 baseline 실패와 361b3f9의 TXT 목록 측정 실패를 원본 보존했다.

제품 host 외부 종료 2단계와 최신 seek/return 회귀는 최종 세 API에서 PASS다. [최종 결과와 원본](RESUME_COMPLETION_2026-10-22.md)을 따른다.
빠른 100장 **UI** 이동과 emulator의 100장 **decoder** 순회는 서로 다른 검사다.
실기기 PSS/GPU/메모리 압박/OOM·TalkBack·전체 OS/기기 gesture 조합은 아직 미완료다.
