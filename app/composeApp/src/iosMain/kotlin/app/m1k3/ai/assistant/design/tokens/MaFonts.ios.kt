package app.m1k3.ai.assistant.design.tokens

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * iOS Font Implementation
 *
 * Custom fonts loaded from resources:
 * - Silkscreen: M1K3 brand font (Display, Headline, Title)
 * - Doto: Caption font (Secondary text)
 * - SF Pro: System font (Body text for readability)
 * - SF Mono: System monospace (Stats/code)
 */

// Silkscreen brand font
private val silkscreenFontFamily = FontFamily(
    Font(resource = "silkscreen_regular.ttf", weight = FontWeight.Normal),
    Font(resource = "silkscreen_bold.ttf", weight = FontWeight.Bold)
)

// Doto caption font
private val dotoFontFamily = FontFamily(
    Font(resource = "doto_regular.ttf", weight = FontWeight.Normal),
    Font(resource = "doto_medium.ttf", weight = FontWeight.Medium),
    Font(resource = "doto_bold.ttf", weight = FontWeight.Bold)
)

// Actual font family implementations
actual val MaFontFamilyBrand: FontFamily = silkscreenFontFamily
actual val MaFontFamilyBody: FontFamily = FontFamily.Default // SF Pro for readability
actual val MaFontFamilyMono: FontFamily = FontFamily.Monospace // SF Mono
actual val MaFontFamilyCaption: FontFamily = dotoFontFamily
