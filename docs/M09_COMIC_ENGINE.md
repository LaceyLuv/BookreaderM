# Comic 입력·decode 기반 — 2026-10-17

main `30853c6`은 제품 화면 전 단계의 manifest 및 Android image decoder다.
서재 가져오기/한 장·연속 보기/웹툰·zoom·진행도 연결을 완료한 상태가 아니다.
기존 `epubAndroid`의 ZIP 경계를 재사용하며 아직 앱에는 debug dependency로 연결된다.

- 전체 path segment를 locale 없이 자연 정렬한다. 숫자를 Int로 변환하지 않고 자릿수·값·원래 길이 순으로 비교한다.
- 원래 entryKey를 유지하며 ZIP 순서가 바뀌어도 동일 key를 찾는다. 숨김 경로·메타 파일·비이미지는 페이지에서 제외한다.
- 파일/디렉터리 prefix 충돌도 거부한다. archive entry를 파일명으로 쓰지 않고 opaque 임시 파일로 16KiB씩 전달한다.
- JPEG/PNG/WebP만 플랫폼 decode에 전달하고 PNG/WebP animation은 사전에 거부한다. 손상 한 장은 복구 가능한 오류다.
- EXIF는 공식 AndroidX 1.4.2를 고정했다. 기본 android.media.ExifInterface의 lint 보안 오류를 보존하고 교체했다.
- decode 동시성 1, bitmap 예산 min(64MiB, heap/5), 회전의 일시적인 두 bitmap을 고려한 reservation을 사용한다.
- 최대 decode 치수 4096 및 viewport/sample/예산으로 먼저 축소한다. 긴 이미지의 region/tile 지원을 주장하지 않는다.
- 반환 bitmap의 lease는 host가 화면에서 제거한 뒤 close한다. 취소로 반환이 유실되는 경계에서도 해제한다.
- native decode 자체의 강제 선점은 지원하지 않는다. 입력·출력 한도와 전후 취소 검사로 작업을 제한하며 OOM을 동일 크기로 재시도하지 않는다.

local manifest/ZIP/EPUB unit 11 및 module lint PASS. source `1ee3a9e`의 test APK compile/app·module lint도 PASS다.
[공식 ExifInterface 소스](https://dl.google.com/dl/android/maven2/androidx/exifinterface/exifinterface/1.4.2/exifinterface-1.4.2-sources.jar)의
String constructor, TAG_ORIENTATION/getAttributeInt 및 회전 상수를 확인했다. Apache-2.0 dependency의 최종 license 목록은 M10 후속이다.

자체 fixture는 PNG 1080×30000, WebP 1080×16000, EXIF JPEG, 100장 및 손상/animated 이미지를 포함한다.
WebP 한 변 제한 16383 때문에 30000px WebP fixture라고 기록하지 않는다. source/hash는 `fixtures/comic-manifest.json`이다.
실제 Android decoder 2개는 mandatory manifest에 추가했으며 결과는 아직 NOT_RUN이다.
PSS는 native 실행 때 측정하고 bitmap budget과 별도로 보고한다. 실기기 성능·화면/GPU·gesture는 미검증이다.
