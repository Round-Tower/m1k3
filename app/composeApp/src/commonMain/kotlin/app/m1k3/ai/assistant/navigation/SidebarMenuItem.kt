package app.m1k3.ai.assistant.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.tokens.*

/**
 * Individual sidebar menu item with selection state animation
 *
 * Features:
 * - Orange highlight when selected
 * - Animated color transitions
 * - Left accent bar indicator
 * - Rounded corners on unselected state
 * - Touch target: 48dp (Material3 minimum)
 */
@Composable
fun SidebarMenuItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaColors.Orange.copy(alpha = 0.15f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = MaDurations.fast, easing = EaseInOutCubic)
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaColors.Orange else MaColors.TextSecondary,
        animationSpec = tween(durationMillis = MaDurations.fast, easing = EaseInOutCubic)
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) MaColors.Orange else MaColors.TextSecondary,
        animationSpec = tween(durationMillis = MaDurations.fast, easing = EaseInOutCubic)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaRadius.sm))
            .clickable(
                enabled = true,
                onClick = onClick,
                indication = ripple(color = MaColors.Orange.copy(alpha = 0.3f)),
                interactionSource = remember { MutableInteractionSource() }
            )
            .background(backgroundColor)
            .semantics {
                selected = isSelected
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(
                    horizontal = MaSpacing.base,
                    vertical = MaSpacing.md
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = label,
                style = MaTypography.bodyMedium,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .background(MaColors.Orange)
                )
            }
        }
    }
}
