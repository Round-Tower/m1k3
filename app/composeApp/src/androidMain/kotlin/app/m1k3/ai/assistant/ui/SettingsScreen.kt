package app.m1k3.ai.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 間 AI - Settings Screen
 *
 * User preferences and configuration interface.
 *
 * **Features:**
 * - Privacy dashboard (0 bytes transmitted)
 * - Model settings (SmolLM2-360M configuration)
 * - App information (version, build, licenses)
 * - Data management (export, import, clear)
 *
 * **Philosophy:**
 * Privacy-first settings with full transparency and user control.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Privacy Section
        item {
            SettingsSection(
                title = "Privacy",
                icon = Icons.Default.Lock
            ) {
                SettingsItem(
                    title = "Privacy Dashboard",
                    subtitle = "0 bytes transmitted • 100% local",
                    icon = Icons.Default.Security,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Navigate to privacy dashboard
                    }
                )

                SettingsItem(
                    title = "Data Encryption",
                    subtitle = "AES-256 • SQLCipher",
                    icon = Icons.Default.Shield,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show encryption details
                    }
                )
            }
        }

        // Model Settings
        item {
            SettingsSection(
                title = "AI Model",
                icon = Icons.Default.Memory
            ) {
                SettingsItem(
                    title = "SmolLM2-360M",
                    subtitle = "180MB • 4-bit quantized",
                    icon = Icons.Default.ModelTraining,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show model details
                    }
                )

                SettingsItem(
                    title = "Context Window",
                    subtitle = "24K tokens • Long conversations",
                    icon = Icons.Default.ViewWeek,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show context settings
                    }
                )
            }
        }

        // Data Management
        item {
            SettingsSection(
                title = "Data",
                icon = Icons.Default.Storage
            ) {
                SettingsItem(
                    title = "Export Conversations",
                    subtitle = "Backup to JSON",
                    icon = Icons.Default.Upload,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Export functionality
                    }
                )

                SettingsItem(
                    title = "Import Conversations",
                    subtitle = "Restore from backup",
                    icon = Icons.Default.Download,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Import functionality
                    }
                )

                SettingsItem(
                    title = "Clear All Data",
                    subtitle = "Reset app to defaults",
                    icon = Icons.Default.DeleteForever,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Clear data with confirmation
                    },
                    isDestructive = true
                )
            }
        }

        // About Section
        item {
            SettingsSection(
                title = "About",
                icon = Icons.Default.Info
            ) {
                SettingsItem(
                    title = "Version",
                    subtitle = "0.1.0 (Phase 2 Complete)",
                    icon = Icons.Default.AppShortcut,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show version details
                    }
                )

                SettingsItem(
                    title = "Open Source Licenses",
                    subtitle = "Apache 2.0 • MIT",
                    icon = Icons.Default.Code,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show licenses
                    }
                )

                SettingsItem(
                    title = "Privacy Policy",
                    subtitle = "No data collection",
                    icon = Icons.Default.PrivacyTip,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show privacy policy
                    }
                )
            }
        }

        // Bottom padding
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Settings section with title and icon
 */
@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                content = content
            )
        }
    }
}

/**
 * Individual settings item
 */
@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
