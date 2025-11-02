package app.m1k3.ai.assistant.design.tokens

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import app.m1k3.ai.assistant.R

/**
 * Android Font Implementation
 *
 * Uses Google Fonts for modern, accessible typography:
 * - Inter: Clean geometric sans-serif (brand/headings)
 * - Roboto: Android default (body text)
 * - JetBrains Mono: Technical monospace (stats/code)
 */

// Google Fonts provider
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Inter font for branding (modern, geometric, highly legible)
private val interFontName = GoogleFont("Inter")

private val interFontFamily = FontFamily(
    Font(googleFont = interFontName, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = interFontName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = interFontName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = interFontName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = interFontName, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = interFontName, fontProvider = provider, weight = FontWeight.ExtraBold)
)

// JetBrains Mono for technical content
private val jetbrainsMonoFontName = GoogleFont("JetBrains Mono")

private val jetbrainsMonoFontFamily = FontFamily(
    Font(googleFont = jetbrainsMonoFontName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = jetbrainsMonoFontName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = jetbrainsMonoFontName, fontProvider = provider, weight = FontWeight.Bold)
)

// Actual font family implementations
actual val MaFontFamilyBrand: FontFamily = interFontFamily
actual val MaFontFamilyBody: FontFamily = FontFamily.Default  // Roboto on Android
actual val MaFontFamilyMono: FontFamily = jetbrainsMonoFontFamily
