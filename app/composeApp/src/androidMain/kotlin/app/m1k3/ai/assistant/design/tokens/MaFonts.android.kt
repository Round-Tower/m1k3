package app.m1k3.ai.assistant.design.tokens

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import app.m1k3.ai.assistant.R

/**
 * Android Font Implementation
 *
 * Uses local fonts for branded, accessible typography:
 * - Silkscreen: M1K3 brand font (geometric, distinctive)
 * - Roboto: Android default (body text)
 * - JetBrains Mono: Technical monospace (stats/code)
 * - Doto: Caption font (readable, distinctive)
 */

// Google Fonts provider (still needed for JetBrains Mono)
private val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

// Silkscreen font for M1K3 branding
private val silkscreenFontFamily =
    FontFamily(
        Font(R.font.silkscreen_regular, FontWeight.Normal),
        Font(R.font.silkscreen_bold, FontWeight.Bold),
    )

// JetBrains Mono for technical content
private val jetbrainsMonoFontName = GoogleFont("JetBrains Mono")

private val jetbrainsMonoFontFamily =
    FontFamily(
        Font(googleFont = jetbrainsMonoFontName, fontProvider = provider, weight = FontWeight.Normal),
        Font(googleFont = jetbrainsMonoFontName, fontProvider = provider, weight = FontWeight.Medium),
        Font(googleFont = jetbrainsMonoFontName, fontProvider = provider, weight = FontWeight.Bold),
    )

// Doto font for captions
private val dotoFontFamily =
    FontFamily(
        Font(R.font.doto_regular, FontWeight.Normal),
        Font(R.font.doto_medium, FontWeight.Medium),
        Font(R.font.doto_bold, FontWeight.Bold),
    )

// Actual font family implementations
actual val MaFontFamilyBrand: FontFamily = silkscreenFontFamily
actual val MaFontFamilyBody: FontFamily = FontFamily.Default // Roboto on Android
actual val MaFontFamilyMono: FontFamily = jetbrainsMonoFontFamily
actual val MaFontFamilyCaption: FontFamily = dotoFontFamily
