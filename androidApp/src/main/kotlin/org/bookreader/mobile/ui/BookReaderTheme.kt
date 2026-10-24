package org.bookreader.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The library and reader controls share the same ink/mint palette. Book paper stays independent.
internal val BookReaderDarkColors = darkColorScheme(
    primary = Color(0xFFA7E0D3), onPrimary = Color(0xFF103C34),
    primaryContainer = Color(0xFF294E48), onPrimaryContainer = Color(0xFFD5F5EC),
    secondaryContainer = Color(0xFF35485C), onSecondaryContainer = Color(0xFFE0EBF8),
    background = Color(0xFF101720), onBackground = Color(0xFFF0F3F7),
    surface = Color(0xFF202D3B), onSurface = Color(0xFFF0F3F7),
    surfaceVariant = Color(0xFF2B3B4C), onSurfaceVariant = Color(0xFFBDCBD8),
    surfaceContainerHigh = Color(0xFF243343), surfaceContainerHighest = Color(0xFF2B3B4C),
    outline = Color(0xFF8294A7), outlineVariant = Color(0xFF485B6E),
)

private val BookReaderLightColors = lightColorScheme(
    primary = Color(0xFF226756), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EEE5), onPrimaryContainer = Color(0xFF163D33),
    secondary = Color(0xFF455C70), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EAF0), onSecondaryContainer = Color(0xFF243343),
    background = Color(0xFFF7F8F6), onBackground = Color(0xFF202D3B),
    surface = Color(0xFFFCFDFB), onSurface = Color(0xFF202D3B),
    surfaceVariant = Color(0xFFE9EEEB), onSurfaceVariant = Color(0xFF4C6059),
    surfaceContainerLow = Color(0xFFF0F3EF), surfaceContainer = Color(0xFFEBF0EB),
    surfaceContainerHigh = Color(0xFFE6ECE7), surfaceContainerHighest = Color(0xFFE1E8E2),
    outline = Color(0xFF6D8077), outlineVariant = Color(0xFFC2CEC6),
)

@Composable
fun BookReaderTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) BookReaderDarkColors else BookReaderLightColors, content = content)
}
