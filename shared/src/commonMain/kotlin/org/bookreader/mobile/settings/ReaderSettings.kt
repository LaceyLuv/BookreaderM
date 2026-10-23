package org.bookreader.mobile.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable enum class TxtReadingMode { SCROLL, PAGES }
@Serializable enum class ComicDirection { LTR, RTL }
@Serializable enum class ComicFit { WIDTH, HEIGHT, CONTAIN }
@Serializable enum class ReaderFont { SANS, SERIF, NANUM_GOTHIC, NANUM_MYEONGJO }
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
) {
    init {
        require(fontSizeSp.isFinite() && fontSizeSp in 12f..36f)
        require(lineHeight.isFinite() && lineHeight in 1f..2f)
        require(letterSpacingEm.isFinite() && letterSpacingEm in -0.02f..0.10f)
        require(marginDp in 8..40)
        require(comicGapDp in 0..32)
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
