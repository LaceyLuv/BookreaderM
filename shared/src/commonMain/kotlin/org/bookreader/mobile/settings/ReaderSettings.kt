package org.bookreader.mobile.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable enum class TxtReadingMode { SCROLL, PAGES }
@Serializable enum class ReaderFont { SANS, SERIF }
@Serializable enum class ReaderTheme { LIGHT, SEPIA, DARK, OLED }

@Serializable
data class ReaderSettings(
    val readingMode: TxtReadingMode = TxtReadingMode.SCROLL,
    val font: ReaderFont = ReaderFont.SANS,
    val fontSizeSp: Float = 18f,
    val lineHeight: Float = 1.6f,
    val letterSpacingEm: Float = 0f,
    val marginDp: Int = 16,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
) {
    init {
        require(fontSizeSp.isFinite() && fontSizeSp in 12f..36f)
        require(lineHeight.isFinite() && lineHeight in 1f..2f)
        require(letterSpacingEm.isFinite() && letterSpacingEm in -0.02f..0.10f)
        require(marginDp in 8..40)
    }
}

@Serializable
data class BookReaderPreferences(val readingMode: TxtReadingMode? = null)

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
