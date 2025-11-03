package app.m1k3.ai.assistant.design.tokens

import androidx.compose.ui.unit.dp

/**
 * M1K3 AI Border Radius System
 *
 * Consistent corner rounding for components.
 * Creates visual hierarchy and softens the brutalist/terminal aesthetic.
 *
 * Philosophy: Subtle rounding for approachability without sacrificing minimalism
 */
object MaRadius {
    /** 0dp - No rounding (sharp corners for terminal/brutalist elements) */
    val none = 0.dp

    /** 4dp - Minimal rounding (chips, small badges) */
    val xs = 4.dp

    /** 8dp - Small rounding (buttons, input fields) */
    val sm = 8.dp

    /** 12dp - Standard rounding (cards, most components) */
    val md = 12.dp

    /** 16dp - Large rounding (prominent cards, modals) */
    val lg = 16.dp

    /** 20dp - Extra large rounding (chat bubbles, special elements) */
    val xl = 20.dp

    /** 24dp - Maximum rounding (nearly circular for smaller elements) */
    val xxl = 24.dp

    /** 9999dp - Fully circular (avatars, floating action buttons) */
    val full = 9999.dp
}

/**
 * Radius Usage Guidelines
 *
 * none (0dp):  Terminal-style elements, sharp dividers
 * xs (4dp):    Small chips, inline badges
 * sm (8dp):    Buttons, text fields, small cards
 * md (12dp):   Standard cards, dialogs (DEFAULT for most components)
 * lg (16dp):   Prominent cards, bottom sheets
 * xl (20dp):   Chat bubbles, special interactive elements
 * xxl (24dp):  Large interactive elements
 * full (9999dp): Avatars, FABs, fully circular elements
 *
 * Example Usage:
 * ```kotlin
 * Card(
 *     shape = RoundedCornerShape(MaRadius.md)
 * )
 * ```
 */
