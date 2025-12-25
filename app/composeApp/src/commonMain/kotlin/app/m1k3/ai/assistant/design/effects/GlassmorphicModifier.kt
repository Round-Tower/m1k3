package app.m1k3.ai.assistant.design.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius

/**
 * Glassmorphic effect modifier for M1K3 AI
 *
 * Creates liquid glass aesthetic with:
 * - Semi-transparent backgrounds
 * - Subtle borders
 * - Blur effect on Android 12+ (API 31+)
 * - Graceful fallback on older devices (API 27-30)
 *
 * Philosophy: Depth through layering, not heavy shadows
 */

/**
 * Apply glassmorphic styling to any composable
 *
 * On Android 12+ (API 31+): Background blur + transparency
 * On Android 8-11 (API 27-30): Transparency only (no blur)
 *
 * @param backgroundColor Base color (default: 3% white)
 * @param borderColor Border color (default: 6% white)
 * @param borderWidth Border thickness (default: 1dp)
 * @param shape Corner shape (default: 12dp rounded)
 * @param blurRadius Blur radius in pixels (API 31+ only, default: 8f)
 *
 * Usage:
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .size(200.dp)
 *         .glassmorphic()
 * )
 * ```
 */
fun Modifier.glassmorphic(
    backgroundColor: Color = MaColors.BgGlass.copy(alpha = 0.1f),
    borderColor: Color = MaColors.BorderSubtle,
    borderWidth: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(MaRadius.none),
    blurRadius: Float = 8f
): Modifier = composed {
    // Note: Blur effect requires platform-specific implementation
    // For now, using transparency-only approach (works on all API levels)
    // Blur will be added in androidMain implementation if needed

    this
        .clip(shape)
        .background(
            color = backgroundColor,
            shape = shape
        )
        .border(
            width = borderWidth,
            color = borderColor,
            shape = shape
        )
}

/**
 * Glassmorphic card variant - more elevated
 */
fun Modifier.glassmorphicCard(
    shape: Shape = RoundedCornerShape(MaRadius.md)
): Modifier = glassmorphic(
    backgroundColor = Color.Transparent,
    borderColor = MaColors.BorderLight.copy(alpha = 0.1f),
    borderWidth = 2.dp,
    shape = shape
)

/**
 * Glassmorphic surface variant - subtle
 */
fun Modifier.glassmorphicSurface(
    shape: Shape = RoundedCornerShape(MaRadius.md)
): Modifier = glassmorphic(
    backgroundColor = MaColors.BgSecondary,
    borderColor = MaColors.BorderSubtle,
    borderWidth = 1.dp,
    shape = shape
)

/**
 * Glassmorphic overlay variant - for modals/dialogs
 */
fun Modifier.glassmorphicOverlay(
    shape: Shape = RoundedCornerShape(MaRadius.lg)
): Modifier = glassmorphic(
    backgroundColor = MaColors.BgHighElevated,
    borderColor = MaColors.BorderMedium,
    borderWidth = 1.5.dp,
    shape = shape
)

/**
 * Usage Guidelines
 *
 * glassmorphic():          General purpose, 3% white background
 * glassmorphicCard():      Cards, 8% white (more prominent)
 * glassmorphicSurface():   Subtle surfaces, 2% white (barely visible)
 * glassmorphicOverlay():   Modals/dialogs, 12% white (highly elevated)
 *
 * Example:
 * ```kotlin
 * // Standard card
 * Card(
 *     modifier = Modifier.glassmorphicCard()
 * ) {
 *     Text("Content")
 * }
 *
 * // Custom styling
 * Box(
 *     modifier = Modifier.glassmorphic(
 *         backgroundColor = MaColors.Orange.copy(alpha = 0.1f),
 *         borderColor = MaColors.Orange.copy(alpha = 0.3f)
 *     )
 * )
 * ```
 *
 * Platform-Specific Notes:
 * - Blur effect can be added in androidMain using RenderEffect (API 31+)
 * - This commonMain implementation works universally
 * - Transparency + borders create depth even without blur
 */
