package org.bookreader.mobile.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.bookreader.mobile.settings.ReaderSettings
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

@Composable
fun ReaderSettings.composeFamily(): FontFamily {
    val context = LocalContext.current
    val fallback = font.composeFamily()
    val imported = remember(customFontId, context) {
        UserFontStore(context).find(customFontId)?.let { userFont ->
            userFont to FontFilePolicy.variableWeightRange(userFont.file)
        }
    }
    val weight = effectiveFontWeight
    val variableWeight = imported?.second?.let { weight.coerceIn(it.first, it.last) }
    val matchedWeight = if (variableWeight != null) weight else null
    return remember(imported, matchedWeight, variableWeight, fallback) {
        imported?.let { (userFont, range) ->
            runCatching {
                if (range != null && matchedWeight != null && variableWeight != null) {
                    FontFamily(
                        Font(
                            file = userFont.file,
                            weight = FontWeight(matchedWeight),
                            variationSettings = FontVariation.Settings(FontVariation.weight(variableWeight)),
                        ),
                    )
                } else {
                    FontFamily(Font(userFont.file))
                }
            }.getOrNull()
        } ?: fallback
    }
}
