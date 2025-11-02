package app.m1k3.ai.assistant.design.tokens

import androidx.compose.ui.graphics.Color

/**
 * 間 AI Color Palette
 *
 * AMOLED black optimized design system with M1K3 branding.
 * Pure black (#000000) saves 15-20% battery on AMOLED displays.
 *
 * Design Philosophy:
 * - Pure AMOLED black for maximum battery efficiency
 * - M1K3 orange (#E25303) as signature brand accent
 * - Transparent white layers for glassmorphic depth
 * - Wabi-sabi aesthetic: minimal, intentional color use
 */
object MaColors {
    // ============================================
    // Foundation Colors
    // ============================================

    /** Pure AMOLED black - Maximum battery savings on OLED displays */
    val Black = Color(0xFF000000)

    /** Pure white - Used sparingly for maximum contrast */
    val White = Color(0xFFFFFFFF)

    // ============================================
    // M1K3 Brand Colors
    // ============================================

    /** M1K3 signature orange - Primary brand color */
    val Orange = Color(0xFFE25303)

    /** Dimmed orange (50% alpha) - For subtle accents */
    val OrangeDim = Color(0x80E25303)

    /** Faint orange (20% alpha) - For very subtle tints */
    val OrangeFaint = Color(0x33E25303)

    // ============================================
    // Background Layers (Transparent White on Black)
    // Creates depth through subtle transparency
    // ============================================

    /** Primary background - Pure AMOLED black */
    val BgPrimary = Black

    /** Secondary background - 2% white transparency */
    val BgSecondary = Color(0x05FFFFFF)  // rgba(255,255,255,0.02)

    /** Tertiary background - 4% white transparency */
    val BgTertiary = Color(0x0AFFFFFF)   // rgba(255,255,255,0.04)

    /** Elevated surface - 8% white transparency */
    val BgElevated = Color(0x14FFFFFF)   // rgba(255,255,255,0.08)

    /** Glassmorphic surface - 3% white transparency (for blur effects) */
    val BgGlass = Color(0x08FFFFFF)      // rgba(255,255,255,0.03)

    /** Highly elevated surface - 12% white transparency */
    val BgHighElevated = Color(0x1FFFFFFF)  // rgba(255,255,255,0.12)

    // ============================================
    // Text Hierarchy
    // ============================================

    /** Primary text - 98% white (nearly pure but softer) */
    val TextPrimary = Color(0xFAFFFFFF)    // rgba(255,255,255,0.98)

    /** Secondary text - 75% white (for less important content) */
    val TextSecondary = Color(0xBFFFFFFF)  // rgba(255,255,255,0.75)

    /** Muted text - 45% white (for hints, placeholders) */
    val TextMuted = Color(0x73FFFFFF)      // rgba(255,255,255,0.45)

    /** Disabled text - 30% white (for inactive elements) */
    val TextDisabled = Color(0x4DFFFFFF)   // rgba(255,255,255,0.30)

    // ============================================
    // Border Colors
    // ============================================

    /** Subtle border - 6% white (barely visible separation) */
    val BorderSubtle = Color(0x0FFFFFFF)   // rgba(255,255,255,0.06)

    /** Light border - 10% white (standard dividers) */
    val BorderLight = Color(0x1AFFFFFF)    // rgba(255,255,255,0.10)

    /** Medium border - 15% white (emphasized dividers) */
    val BorderMedium = Color(0x26FFFFFF)   // rgba(255,255,255,0.15)

    /** Strong border - 25% white (strong visual separation) */
    val BorderStrong = Color(0x40FFFFFF)   // rgba(255,255,255,0.25)

    // ============================================
    // Status Colors
    // ============================================

    /** Success state - Material Green 500 */
    val Success = Color(0xFF4CAF50)

    /** Success background - 15% alpha */
    val SuccessBg = Color(0x264CAF50)

    /** Error state - Material Red 500 */
    val Error = Color(0xFFF44336)

    /** Error background - 15% alpha */
    val ErrorBg = Color(0x26F44336)

    /** Warning state - Material Orange 400 */
    val Warning = Color(0xFFFFA726)

    /** Warning background - 15% alpha */
    val WarningBg = Color(0x26FFA726)

    /** Info state - Material Light Blue 400 */
    val Info = Color(0xFF29B6F6)

    /** Info background - 15% alpha */
    val InfoBg = Color(0x2629B6F6)

    // ============================================
    // Interactive States
    // ============================================

    /** Hover overlay - 8% white */
    val HoverOverlay = Color(0x14FFFFFF)

    /** Pressed overlay - 16% white */
    val PressedOverlay = Color(0x29FFFFFF)

    /** Focus ring - M1K3 orange */
    val FocusRing = Orange

    /** Selection background - 12% orange */
    val SelectionBg = Color(0x1FE25303)

    // ============================================
    // Scrim & Overlays
    // ============================================

    /** Light scrim - 40% black (for overlays) */
    val ScrimLight = Color(0x66000000)

    /** Medium scrim - 60% black (for modals) */
    val ScrimMedium = Color(0x99000000)

    /** Dark scrim - 80% black (for full overlays) */
    val ScrimDark = Color(0xCC000000)
}
