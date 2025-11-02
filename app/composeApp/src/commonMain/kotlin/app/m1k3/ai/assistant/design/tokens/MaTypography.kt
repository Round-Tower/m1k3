package app.m1k3.ai.assistant.design.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * M1K3 AI Typography System
 *
 * Custom branded typography with Google Fonts:
 * - Inter: Modern geometric sans-serif for branding (displays, headlines, titles)
 * - Roboto: Android default for body text (optimal readability)
 * - JetBrains Mono: Technical monospace for stats/code
 *
 * Material3 type scale optimized for mobile readability and terminal aesthetic.
 * Scale: 7 levels from 12sp (label) to 57sp (display)
 * Line heights: 1.25-1.5x for optimal readability
 */
val MaTypography = Typography(
    // ============================================
    // Display Styles - Large marketing/hero text
    // Uses Inter (brand font) for strong visual identity
    // ============================================

    displayLarge = TextStyle(
        fontFamily = MaFontFamilyBrand,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),

    displayMedium = TextStyle(
        fontFamily = MaFontFamilyBrand,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),

    displaySmall = TextStyle(
        fontFamily = MaFontFamilyBrand,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // ============================================
    // Headline Styles - Section headers
    // Uses Inter for branded section headers
    // ============================================

    headlineLarge = TextStyle(
        fontFamily = MaFontFamilyBrand,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),

    headlineMedium = TextStyle(
        fontFamily = MaFontFamilyBrand,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),

    headlineSmall = TextStyle(
        fontFamily = MaFontFamilyBrand,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // ============================================
    // Title Styles - Component headers, app bars
    // Uses Inter for consistent branding in UI components
    // ============================================

    titleLarge = TextStyle(
        fontFamily = MaFontFamilyBrand,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    titleMedium = TextStyle(
        fontFamily = MaFontFamilyBrand,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),

    titleSmall = TextStyle(
        fontFamily = MaFontFamilyBrand,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ============================================
    // Body Styles - Main content text
    // Uses system Roboto for optimal readability
    // ============================================

    bodyLarge = TextStyle(
        fontFamily = MaFontFamilyBody,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

    bodyMedium = TextStyle(
        fontFamily = MaFontFamilyBody,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),

    bodySmall = TextStyle(
        fontFamily = MaFontFamilyBody,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // ============================================
    // Label Styles - Buttons, chips, badges
    // Uses system Roboto for UI consistency
    // ============================================

    labelLarge = TextStyle(
        fontFamily = MaFontFamilyBody,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    labelMedium = TextStyle(
        fontFamily = MaFontFamilyBody,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),

    labelSmall = TextStyle(
        fontFamily = MaFontFamilyBody,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Typography Usage Guidelines
 *
 * Display: Hero sections, splash screens (displayLarge/Medium/Small)
 * Headline: Section headers, page titles (headlineLarge/Medium/Small)
 * Title: Component headers, dialog titles, app bar (titleLarge/Medium/Small)
 * Body: Paragraph text, list items, chat messages (bodyLarge/Medium/Small)
 * Label: Buttons, chips, metadata, badges (labelLarge/Medium/Small)
 *
 * Example Usage:
 * ```kotlin
 * Text(
 *     text = "M1K3 AI",
 *     style = MaterialTheme.typography.displayLarge
 * )
 * ```
 */
