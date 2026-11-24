package org.bookreader.mobile.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.bookreader.mobile.model.BookFormat

/**
 * Presentation-only color tokens aligned with ReaderChrome.kt (#101720 / #202D3B / #A7E0D3)
 * so the library, search, and settings tabs share one visual system with the reader tools.
 */
internal val BookReaderLightColorScheme = lightColorScheme(
    primary = Color(0xFF16685B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F3EE),
    onPrimaryContainer = Color(0xFF0E3E36),
    secondary = Color(0xFF334B61),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4ECF4),
    onSecondaryContainer = Color(0xFF1B2E40),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF141E28),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141E28),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF4C5E70),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFF1F5F8),
    surfaceContainerHigh = Color(0xFFE8EEF3),
    surfaceContainerHighest = Color(0xFFDFE6EE),
    outline = Color(0xFF7D8E9E),
    outlineVariant = Color(0xFFDCE3EB),
    error = Color(0xFFB42336),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDF0F2),
    onErrorContainer = Color(0xFF7A1422),
)

internal val BookReaderDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA7E0D3),
    onPrimary = Color(0xFF103C34),
    primaryContainer = Color(0xFF294E48),
    onPrimaryContainer = Color(0xFFD5F5EC),
    secondary = Color(0xFFB3C7DC),
    onSecondary = Color(0xFF1C3042),
    secondaryContainer = Color(0xFF35485C),
    onSecondaryContainer = Color(0xFFE0EBF8),
    background = Color(0xFF101720),
    onBackground = Color(0xFFF0F3F7),
    surface = Color(0xFF1B2734),
    onSurface = Color(0xFFF0F3F7),
    surfaceVariant = Color(0xFF243343),
    onSurfaceVariant = Color(0xFFBDCBD8),
    surfaceContainerLowest = Color(0xFF0D131B),
    surfaceContainerLow = Color(0xFF16202B),
    surfaceContainer = Color(0xFF202D3B),
    surfaceContainerHigh = Color(0xFF263546),
    surfaceContainerHighest = Color(0xFF2B3B4C),
    outline = Color(0xFF8294A7),
    outlineVariant = Color(0xFF354759),
    error = Color(0xFFF7A3AE),
    onError = Color(0xFF56121C),
    errorContainer = Color(0xFF3A1E24),
    onErrorContainer = Color(0xFFFCD9DE),
)

internal data class BookSpineColors(
    val band: Color,
    val spine: Color,
    val label: Color,
)

internal fun bookSpineColors(format: BookFormat, dark: Boolean): BookSpineColors = when (format) {
    BookFormat.TXT -> if (dark) {
        BookSpineColors(
            band = Color(0xFF213447),
            spine = Color(0xFF7FB3D5),
            label = Color(0xFFC6DEEE),
        )
    } else {
        BookSpineColors(
            band = Color(0xFFE9F0F6),
            spine = Color(0xFF365775),
            label = Color(0xFF243E57),
        )
    }
    BookFormat.EPUB -> if (dark) {
        BookSpineColors(
            band = Color(0xFF1D3936),
            spine = Color(0xFFA7E0D3),
            label = Color(0xFFD0F2E9),
        )
    } else {
        BookSpineColors(
            band = Color(0xFFE4F2EE),
            spine = Color(0xFF176B5B),
            label = Color(0xFF134E43),
        )
    }
    BookFormat.COMIC -> if (dark) {
        BookSpineColors(
            band = Color(0xFF3B2E26),
            spine = Color(0xFFE0B089),
            label = Color(0xFFF0D5BC),
        )
    } else {
        BookSpineColors(
            band = Color(0xFFF5ECE4),
            spine = Color(0xFF8E5634),
            label = Color(0xFF663A1F),
        )
    }
}
