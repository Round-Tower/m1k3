package app.m1k3.ai.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.avatar.AvatarState
import app.m1k3.ai.assistant.avatar.AvatarEmotion
import app.m1k3.ai.assistant.avatar.AvatarView
import app.m1k3.ai.assistant.design.preview.PreviewFixtures
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.*

/**
 * Unified persistent toolbar for all screens.
 *
 * Features:
 * - Hamburger menu icon for drawer toggle
 * - Liquid glass blur effect with gradient overlay
 * - Engine initialization status indicator
 * - 3D avatar with emotion/activity feedback (100dp)
 * - Screen name display
 * - Consistent styling across all screens
 *
 * @param screenName Name of the current screen to display
 * @param engineInitialized Whether the AI engine is ready
 * @param avatarState Current avatar emotional/activity state
 * @param onMenuClick Callback when hamburger menu is clicked
 * @param modifier Optional modifier for customization
 */
@Composable
fun UnifiedToolbar(
    screenName: String,
    engineInitialized: Boolean,
    avatarState: AvatarState,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 42.dp, start = 16.dp, end = 16.dp)
    ) {
        // Gradient overlay for liquid glass effect (theme-aware)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(top = 42.dp, start = 16.dp, end = 16.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.0f)
                        )
                    )
                )
        )

        // Toolbar content
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaSpacing.md, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left side: Menu button + Title and status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Hamburger menu button
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaColors.textPrimary()
                        )
                    }

                    // Title and status
                    Column {
                        Text(
                            screenName,
                            style = MaTypography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.textPrimary(),
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
                            color = if (engineInitialized) MaColors.Orange else MaColors.textSecondary(),
                        )
                    }
                }

                // Right side: Avatar
                AvatarView(
                    state = avatarState,
                    use3D = true,
                    showInfo = true,
                    modifier = Modifier
                        .testTag("avatar_unified")
                        .size(100.dp)
                        .padding(start = 16.dp)
                )
            }
        }
    }
}

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun UnifiedToolbarChatReadyPreview() {
    MaTheme {
        UnifiedToolbar(
            screenName = "Chat",
            engineInitialized = true,
            avatarState = AvatarState(emotion = AvatarEmotion.HAPPY),
            onMenuClick = {}
        )
    }
}

@Preview
@Composable
private fun UnifiedToolbarHistoryLoadingPreview() {
    MaTheme {
        UnifiedToolbar(
            screenName = "History",
            engineInitialized = false,
            avatarState = AvatarState(emotion = AvatarEmotion.THINKING)
        )
    }
}

@Preview
@Composable
private fun UnifiedToolbarEcoGeneratingPreview() {
    MaTheme {
        UnifiedToolbar(
            screenName = "Environmental Impact",
            engineInitialized = true,
            avatarState = AvatarState(emotion = AvatarEmotion.EXCITED)
        )
    }
}

@Preview
@Composable
private fun UnifiedToolbarSettingsPreview() {
    MaTheme {
        UnifiedToolbar(
            screenName = "Settings",
            engineInitialized = true,
            avatarState = AvatarState(emotion = AvatarEmotion.NEUTRAL)
        )
    }
}
