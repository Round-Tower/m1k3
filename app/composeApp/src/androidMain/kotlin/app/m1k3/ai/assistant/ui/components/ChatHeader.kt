package app.m1k3.ai.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.avatar.AvatarState
import app.m1k3.ai.assistant.avatar.AvatarView
import app.m1k3.ai.assistant.design.tokens.*

/**
 * Chat header with toolbar, status, and 3D avatar.
 *
 * Features:
 * - Liquid glass blur effect with gradient overlay
 * - Engine initialization status indicator
 * - 3D avatar with emotion/activity feedback
 * - Clear conversation button
 * - Minimal, clean design following Ma design system
 *
 * @param engineInitialized Whether the AI engine is ready
 * @param avatarState Current avatar emotional/activity state
 * @param onClearClick Callback when clear button is pressed
 * @param modifier Optional modifier for customization
 */
@Composable
fun ChatHeader(
    engineInitialized: Boolean,
    avatarState: AvatarState,
    onClearClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Gradient overlay for liquid glass effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaColors.BgPrimary.copy(alpha = 0.95f),
                            MaColors.BgPrimary.copy(alpha = 0.85f),
                            MaColors.BgPrimary.copy(alpha = 0.0f)
                        )
                    )
                )
        )

        // Toolbar content
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaColors.BgPrimary.copy(alpha = 0.75f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaSpacing.md, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left side: Title and status
                Column {
                    Text(
                        "M1K3",
                        style = MaTypography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaColors.TextPrimary,
                    )
                    Text(
                        if (engineInitialized) "Ready" else "Loading...",
                        style =
                            TextStyle(
                                fontFamily = MaFontFamilyCaption,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.25.sp,
                            ),
                        color = if (engineInitialized) MaColors.Orange else MaColors.TextSecondary,
                    )
                }

                // Right side: Clear button and Avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clear conversation button
                    Box(
                        modifier = Modifier
                            .testTag("clear_button")
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaColors.BgSecondary)
                            .clickable(onClick = onClearClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Clear conversation",
                            tint = MaColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3D Avatar with activity/emotion feedback
                    AvatarView(
                        state = avatarState,
                        use3D = true,
                        showInfo = true,
                        modifier = Modifier
                            .testTag("avatar")
                            .size(100.dp)
                    )
                }
            }
        }
    }
}
