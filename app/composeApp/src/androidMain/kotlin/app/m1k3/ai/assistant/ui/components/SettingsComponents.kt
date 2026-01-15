package app.m1k3.ai.assistant.ui.components

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.ai.ondevice.AiAvailability
import app.m1k3.ai.assistant.design.tokens.MaFontFamilyCaption
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
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(8.dp),
                content = content
            )
        }
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
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    fontFamily = MaFontFamilyCaption,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.25.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = { enabled ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(enabled)
            }
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
        is MlKitStatus.Checking -> "Checking..." to MaterialTheme.colorScheme.onSurfaceVariant
        is MlKitStatus.Loaded -> when (val availability = status.availability) {
            is AiAvailability.Available -> "Available (Gemini Nano)" to MaterialTheme.colorScheme.primary
            is AiAvailability.Downloading -> "Downloading model..." to MaterialTheme.colorScheme.tertiary
            is AiAvailability.Unavailable -> "Unavailable: ${availability.reason}" to MaterialTheme.colorScheme.error
            is AiAvailability.Fallback -> "Fallback: ${availability.engineName}" to MaterialTheme.colorScheme.secondary
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = statusColor
            )
            Text(
                text = "Status: $statusText",
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
        }

        Text(
            text = "Android ${Build.VERSION.SDK_INT} (requires 34+)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Test generation result
        testResult?.let { result ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Test Result:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

    // Test Generation Button
    Surface(
        onClick = {
            if (!isTestRunning) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onTestClick()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTestRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isTestRunning) "Running test..." else "Test Generation",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Send 'Hello, what is 2+2?' to AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
