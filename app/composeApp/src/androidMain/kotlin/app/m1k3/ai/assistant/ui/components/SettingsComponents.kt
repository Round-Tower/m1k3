package app.m1k3.ai.assistant.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.ai.ondevice.AiAvailability
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaFontFamilyCaption
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.settings.MlKitStatus

/**
 * SettingsSection - A settings section with title, icon, and content.
 *
 * @param title Section title
 * @param icon Section icon
 * @param content Content to display in the section
 */
@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val sectionShape = RoundedCornerShape(MaRadius.md)

    Column(
        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
            modifier = Modifier.padding(bottom = MaSpacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaColors.Orange,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaTypography.titleMedium,
                color = MaColors.textPrimary()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sectionShape)
                .background(MaColors.bgElevated())
                .border(
                    width = 1.dp,
                    color = MaColors.borderSubtle(),
                    shape = sectionShape
                )
                .padding(MaSpacing.sm),
            content = content
        )
    }
}

/**
 * SettingsItem - An individual settings item with icon, title, subtitle, and action.
 *
 * @param title Item title
 * @param subtitle Item subtitle
 * @param icon Item icon
 * @param onClick Click handler
 * @param isDestructive Whether this is a destructive action (red styling)
 */
@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isDestructive) MaColors.Error else MaColors.textSecondary()
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaTypography.bodyLarge,
                    color = if (isDestructive) MaColors.Error else MaColors.textPrimary()
                )
                Text(
                    text = subtitle,
                    style = MaTypography.bodySmall,
                    color = MaColors.textMuted()
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                modifier = Modifier.size(18.dp),
                tint = MaColors.textDisabled()
            )
        }
    }
}

/**
 * SettingsToggleItem - A settings item with a toggle switch.
 *
 * @param title Item title
 * @param subtitle Item subtitle
 * @param icon Item icon
 * @param checked Current toggle state
 * @param onCheckedChange Toggle change handler
 */
@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaColors.Orange
                )
                Text(
                    text = title,
                    style = MaTypography.titleMedium,
                    color = MaColors.textPrimary()
                )
            }
            Spacer(modifier = Modifier.height(MaSpacing.xs))
            Text(
                text = subtitle,
                style = TextStyle(
                    fontFamily = MaFontFamilyCaption,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.25.sp
                ),
                color = MaColors.textMuted()
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = { enabled ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(enabled)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaColors.White,
                checkedTrackColor = MaColors.Orange,
                uncheckedThumbColor = MaColors.textMuted(),
                uncheckedTrackColor = MaColors.bgElevated()
            )
        )
    }
}

/**
 * MlKitStatusSection - Displays ML Kit GenAI status and test controls.
 *
 * @param status Current ML Kit status
 * @param testResult Test generation result
 * @param isTestRunning Whether a test is currently running
 * @param onTestClick Test button click handler
 */
@Composable
fun MlKitStatusSection(
    status: MlKitStatus,
    testResult: String?,
    isTestRunning: Boolean,
    onTestClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    val (statusText, statusColor) = when (status) {
        is MlKitStatus.Checking -> "Checking..." to MaColors.textMuted()
        is MlKitStatus.Loaded -> when (val availability = status.availability) {
            is AiAvailability.Available -> "Available (Gemini Nano)" to MaColors.Success
            is AiAvailability.Downloading -> "Downloading model..." to MaColors.Info
            is AiAvailability.Unavailable -> "Unavailable: ${availability.reason}" to MaColors.Error
            is AiAvailability.Fallback -> "Fallback: ${availability.engineName}" to MaColors.Warning
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = statusColor
            )
            Text(
                text = "Status: $statusText",
                style = MaTypography.bodyMedium,
                color = statusColor
            )
        }

        Text(
            text = "Android ${Build.VERSION.SDK_INT} (requires 34+)",
            style = MaTypography.bodySmall,
            color = MaColors.textMuted()
        )

        // Test generation result
        testResult?.let { result ->
            HorizontalDivider(
                modifier = Modifier.padding(vertical = MaSpacing.sm),
                color = MaColors.borderSubtle()
            )
            Text(
                text = "Test Result:",
                style = MaTypography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaColors.textSecondary()
            )
            Text(
                text = result,
                style = MaTypography.bodySmall,
                color = MaColors.textMuted()
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = MaSpacing.md),
        color = MaColors.borderSubtle()
    )

    // Test Generation Button
    Surface(
        onClick = {
            if (!isTestRunning) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onTestClick()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTestRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaColors.Orange
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaColors.Orange
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
            ) {
                Text(
                    text = if (isTestRunning) "Running test..." else "Test Generation",
                    style = MaTypography.bodyLarge,
                    color = MaColors.textPrimary()
                )
                Text(
                    text = "Send 'Hello, what is 2+2?' to AI",
                    style = MaTypography.bodySmall,
                    color = MaColors.textMuted()
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
private fun SettingsSectionPreview() {
    MaTheme {
        SettingsSection(
            title = "Appearance",
            icon = Icons.Default.Settings,
            content = {
                Text("Theme and display settings")
            }
        )
    }
}

@Preview
@Composable
private fun SettingsItemPreview() {
    MaTheme {
        Column {
            SettingsItem(
                title = "Export Data",
                subtitle = "Download your conversations",
                icon = Icons.Default.Download,
                onClick = {}
            )
            SettingsItem(
                title = "Clear Cache",
                subtitle = "Remove temporary files",
                icon = Icons.Default.Delete,
                onClick = {},
                isDestructive = true
            )
        }
    }
}

@Preview
@Composable
private fun SettingsToggleItemPreview() {
    MaTheme {
        Column {
            SettingsToggleItem(
                title = "Analytics",
                subtitle = "Help improve the app",
                icon = Icons.Default.Analytics,
                checked = true,
                onCheckedChange = {}
            )
            SettingsToggleItem(
                title = "Notifications",
                subtitle = "Receive app updates",
                icon = Icons.Default.Notifications,
                checked = false,
                onCheckedChange = {}
            )
        }
    }
}
