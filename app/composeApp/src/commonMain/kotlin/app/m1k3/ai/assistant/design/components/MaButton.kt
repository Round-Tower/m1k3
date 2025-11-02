package app.m1k3.ai.assistant.design.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing

/**
 * 間 AI Button Components
 *
 * M1K3 orange branded buttons with variants:
 * - MaButtonPrimary: Filled orange button (primary actions)
 * - MaButtonSecondary: Outlined button (secondary actions)
 * - MaButtonText: Text-only button (tertiary actions)
 */

/**
 * Primary filled button with M1K3 orange
 *
 * Use for primary/call-to-action buttons (e.g., "Chat", "Send", "Confirm")
 */
@Composable
fun MaButtonPrimary(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(MaRadius.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaColors.Orange,
            contentColor = MaColors.White,
            disabledContainerColor = MaColors.OrangeDim,
            disabledContentColor = MaColors.TextDisabled
        ),
        contentPadding = PaddingValues(
            horizontal = MaSpacing.lg,
            vertical = MaSpacing.md
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Secondary outlined button
 *
 * Use for secondary actions (e.g., "Cancel", "Back", "Settings")
 */
@Composable
fun MaButtonSecondary(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(MaRadius.sm),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaColors.TextPrimary,
            disabledContentColor = MaColors.TextDisabled
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (enabled) MaColors.BorderLight else MaColors.BorderSubtle
            )
        ),
        contentPadding = PaddingValues(
            horizontal = MaSpacing.lg,
            vertical = MaSpacing.md
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Text-only button
 *
 * Use for tertiary actions (e.g., "Learn More", "Skip", navigation)
 */
@Composable
fun MaButtonText(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaColors.Orange,
            disabledContentColor = MaColors.TextDisabled
        ),
        contentPadding = PaddingValues(
            horizontal = MaSpacing.base,
            vertical = MaSpacing.sm
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * // Primary action
 * MaButtonPrimary(
 *     onClick = { /* navigate to chat */ },
 *     text = "Start Chat",
 *     modifier = Modifier.fillMaxWidth()
 * )
 *
 * // Secondary action
 * MaButtonSecondary(
 *     onClick = { /* go back */ },
 *     text = "Back"
 * )
 *
 * // Text action
 * MaButtonText(
 *     onClick = { /* show settings */ },
 *     text = "Settings"
 * )
 * ```
 */
