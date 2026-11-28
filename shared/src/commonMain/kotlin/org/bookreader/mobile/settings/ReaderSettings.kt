package org.bookreader.mobile.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable enum class TxtReadingMode { SCROLL, PAGES }
@Serializable enum class ComicDirection { LTR, RTL }
@Serializable enum class ComicFit { WIDTH, HEIGHT, CONTAIN }
@Serializable enum class ReaderFont { SANS, SERIF, NANUM_GOTHIC, NANUM_MYEONGJO }
@Serializable enum class ReaderFontWeight(val value: Int) { NORMAL(400), BOLD(700) }
@Serializable enum class PageTransition { NONE, SLIDE }
@Serializable enum class ReaderTheme { LIGHT, SEPIA, DARK, OLED }

@Serializable
data class ReaderWindowOptions(
    val brightness: Float? = null,
    val keepScreenOn: Boolean = false,
    val lockRotation: Boolean = false,
    val fullscreen: Boolean = false,
) {
    init { require(brightness == null || brightness.isFinite() && brightness in .05f..1f) }
}

@Serializable
data class ReaderSettings(
    val readingMode: TxtReadingMode = TxtReadingMode.SCROLL,
    val font: ReaderFont = ReaderFont.SANS,
    val fontSizeSp: Float = 18f,
    val lineHeight: Float = 1.6f,
    val letterSpacingEm: Float = 0f,
    val marginDp: Int = 16,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val window: ReaderWindowOptions = ReaderWindowOptions(),
    val comicDirection: ComicDirection = ComicDirection.LTR,
    val comicFit: ComicFit = ComicFit.CONTAIN,
    val comicGapDp: Int = 8,
    val epubPublisherStyles: Boolean = true,
    val pageTransition: PageTransition = PageTransition.NONE,
    val customFontId: String? = null,
    val fontWeight: ReaderFontWeight = ReaderFontWeight.NORMAL,
    val fontWeightValue: Int? = null,
) {
    init {
        require(customFontId == null || customFontId.matches(Regex("[0-9a-f]{64}")))
        require(fontSizeSp.isFinite() && fontSizeSp in 12f..36f)
        require(lineHeight.isFinite() && lineHeight in 1f..2f)
        require(letterSpacingEm.isFinite() && letterSpacingEm in -0.02f..0.10f)
        require(marginDp in 8..40)
        require(comicGapDp in 0..32)
        require(fontWeightValue == null || fontWeightValue in MIN_FONT_WEIGHT..MAX_FONT_WEIGHT)
    }

    val effectiveFontWeight: Int
        get() = fontWeightValue ?: fontWeight.value

    fun withFontWeight(weight: Int): ReaderSettings {
        val clamped = weight.coerceIn(MIN_FONT_WEIGHT, MAX_FONT_WEIGHT)
        return when (clamped) {
            ReaderFontWeight.NORMAL.value -> copy(fontWeight = ReaderFontWeight.NORMAL, fontWeightValue = null)
            ReaderFontWeight.BOLD.value -> copy(fontWeight = ReaderFontWeight.BOLD, fontWeightValue = null)
            else -> copy(
                fontWeight = if (clamped >= 600) ReaderFontWeight.BOLD else ReaderFontWeight.NORMAL,
                fontWeightValue = clamped,
            )
        }
    }

    companion object {
        const val MIN_FONT_WEIGHT = 100
        const val MAX_FONT_WEIGHT = 900
        const val FONT_WEIGHT_STEP = 50
    }
}

@Serializable
data class BookReaderPreferences(val readingMode: TxtReadingMode? = null,
    val comicDirection: ComicDirection? = null, val comicFit: ComicFit? = null)

/** Unknown versions/fields and invalid values stay errors; decoding never rewrites stored bytes. */
object ReaderSettingsCodec {
    const val VERSION = 1
    private val json = Json { encodeDefaults = true }
    private fun check(version: Int, value: String) { require(version == VERSION && value.length <= 4096) }
    fun readGlobal(version: Int, value: String): ReaderSettings {
        check(version, value)
        return json.decodeFromString<ReaderSettings>(value)
    }
    fun readBook(version: Int, value: String): BookReaderPreferences {
        check(version, value)
        return json.decodeFromString<BookReaderPreferences>(value)
    }
    fun write(value: ReaderSettings): String = json.encodeToString(value)
    fun write(value: BookReaderPreferences): String = json.encodeToString(value)
}
