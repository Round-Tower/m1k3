package app.m1k3.ai.assistant.design.tokens

import androidx.compose.ui.unit.dp

/**
 * 間 AI Spacing System
 *
 * 8-point grid system (base unit: 4dp)
 * Ensures consistent spacing throughout the app and simplifies layout decisions.
 *
 * Philosophy: "Negative space (間) is as important as content"
 * - Use generous spacing for breathing room
 * - Consistent rhythm creates calm, focused UI
 * - Less clutter = better comprehension
 */
object MaSpacing {
    /** 4dp - Minimal gap (between inline elements, icon padding) */
    val xs = 4.dp

    /** 8dp - Small gap (list item internal spacing, chip padding) */
    val sm = 8.dp

    /** 12dp - Medium-small gap (card internal padding, between related items) */
    val md = 12.dp

    /** 16dp - Standard gap (default padding, between sections) */
    val base = 16.dp

    /** 24dp - Large gap (section headers, between major groups) */
    val lg = 24.dp

    /** 32dp - Extra large gap (screen margins, major section breaks) */
    val xl = 32.dp

    /** 48dp - Huge gap (hero sections, dramatic separation) */
    val xxl = 48.dp

    /** 64dp - Maximum gap (splash screens, full-bleed spacing) */
    val xxxl = 64.dp
}

/**
 * Spacing Usage Guidelines
 *
 * xs (4dp):  Icon padding, inline badges, minimal separation
 * sm (8dp):  List item padding, chip internal spacing
 * md (12dp): Card padding, button padding, moderate separation
 * base (16dp): Standard padding for most components, screen edges
 * lg (24dp): Section header spacing, between major UI groups
 * xl (32dp): Screen top/bottom margins, between major sections
 * xxl (48dp): Hero sections, dramatic spacing
 * xxxl (64dp): Splash screens, maximum breathing room
 *
 * Example Usage:
 * ```kotlin
 * Column(
 *     modifier = Modifier.padding(MaSpacing.base),
 *     verticalArrangement = Arrangement.spacedBy(MaSpacing.md)
 * )
 * ```
 */
