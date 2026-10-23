package org.bookreader.mobile.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.bookreader.mobile.settings.ReaderFont

val ReaderFont.label: String get() = when (this) {
    ReaderFont.SANS -> "기본 고딕"
    ReaderFont.SERIF -> "기본 명조"
    ReaderFont.NANUM_GOTHIC -> "나눔고딕"
    ReaderFont.NANUM_MYEONGJO -> "나눔명조"
}

val ReaderFont.assetPath: String? get() = when (this) {
    ReaderFont.NANUM_GOTHIC -> "reader-fonts/NanumGothic-Regular.ttf"
    ReaderFont.NANUM_MYEONGJO -> "reader-fonts/NanumMyeongjo-Regular.ttf"
    else -> null
}

@Composable
fun ReaderFont.composeFamily(): FontFamily {
    val assets = LocalContext.current.assets
    return remember(this, assets) {
        assetPath?.let { FontFamily(Font(it, assets)) }
            ?: if (this == ReaderFont.SERIF) FontFamily.Serif else FontFamily.SansSerif
    }
}

fun ReaderFont.epubFamily(): org.readium.r2.navigator.preferences.FontFamily = when (this) {
    ReaderFont.SANS -> org.readium.r2.navigator.preferences.FontFamily.SANS_SERIF
    ReaderFont.SERIF -> org.readium.r2.navigator.preferences.FontFamily.SERIF
    else -> org.readium.r2.navigator.preferences.FontFamily(name)
}
