package org.bookreader.mobile.ui

import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.model.BookFormat

internal val BookFormat.displayName: String get() = when (this) {
    BookFormat.TXT -> "TXT"
    BookFormat.EPUB -> "EPUB"
    BookFormat.COMIC -> "만화"
}

internal val BookAvailability.displayName: String get() = when (this) {
    BookAvailability.READY -> "읽기 가능"
    BookAvailability.MISSING -> "파일 확인 필요"
    BookAvailability.CORRUPT -> "파일 읽기 오류"
    BookAvailability.UNSUPPORTED -> "지원하지 않는 책"
    BookAvailability.DELETING -> "삭제 중"
}

internal fun unavailableBookMessage(availability: BookAvailability): String? = when (availability) {
    BookAvailability.READY -> null
    BookAvailability.MISSING -> "앱에 보관한 파일을 찾을 수 없습니다. 책 추가에서 같은 원본 파일을 다시 선택해 주세요. 기존 독서 기록은 유지됩니다."
    BookAvailability.CORRUPT -> "보관한 파일을 읽을 수 없습니다. 원본 파일의 상태를 확인해 주세요. 기존 책과 독서 기록은 유지됩니다."
    BookAvailability.UNSUPPORTED -> "현재 지원하지 않는 책입니다. 설정에서 지원 형식을 확인해 주세요. 기존 독서 기록은 유지됩니다."
    BookAvailability.DELETING -> "책을 삭제 중입니다. 작업이 끝날 때까지 기다려 주세요."
}
