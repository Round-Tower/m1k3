package app.m1k3.ai.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.ai.ondevice.OnDeviceAi
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.domain.ai.AiCoreModelPreference
import app.m1k3.ai.assistant.settings.collectAsState
import app.m1k3.ai.assistant.settings.rememberSettingsViewModel
import app.m1k3.ai.assistant.ui.components.*
import org.koin.compose.koinInject

/**
 * SettingsScreen - User preferences and configuration interface
 *
 * **Features:**
 * - Privacy dashboard (0 bytes transmitted)
 * - Model settings (Gemma 3 270M configuration)
 * - ML Kit GenAI status and testing
 * - Knowledge & RAG settings
 * - Data management (export, import, clear)
 * - App information
 *
 * **Architecture:**
 * - Uses SettingsViewModel for state management
 * - Delegates to extracted components (SettingsSection, SettingsItem)
 * - Minimal UI logic - ViewModel handles business logic
 *
 * Philosophy: Privacy-first settings with full transparency and user control.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current

    // Inject OnDeviceAi for ML Kit status checking
    val onDeviceAi: OnDeviceAi = koinInject()

    // SettingsViewModel - Single source of truth for settings state
    val viewModel = rememberSettingsViewModel(onDeviceAi)
    val state by viewModel.collectAsState()

    // Check ML Kit availability on launch
    LaunchedEffect(Unit) {
        viewModel.checkMlKitAvailability()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaColors.bgPrimary())
            .padding(MaSpacing.base),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
    ) {
        // Privacy Section
        item {
            PrivacySection(
                onPrivacyDashboardClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Navigate to privacy dashboard
                },
                onEncryptionClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Show encryption details
                }
            )
        }

        // Model Settings
        item {
            ModelSection(
                modelInfo = state.modelInfo,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Show model details
                }
            )
        }

        // ML Kit GenAI Section
        item {
            SettingsSection(
                title = "ML Kit GenAI",
                icon = Icons.Default.AutoAwesome
            ) {
                MlKitStatusSection(
                    status = state.mlKitStatus,
                    testResult = state.testResult,
                    isTestRunning = state.isTestRunning,
                    onTestClick = { viewModel.runTestGeneration() }
                )
            }
        }

        // AICore Model Selection
        item {
            AiCoreSection(
                currentPreference = state.aiCorePreference,
                onPreferenceChange = { viewModel.switchAiCorePreference(it) }
            )
        }

        // Knowledge & RAG Section
        item {
            KnowledgeSection(
                ragEnabled = state.ragEnabled,
                onRagEnabledChange = { viewModel.setRagEnabled(it) },
                onKnowledgeBaseClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Show knowledge base browser
                },
                onIntentClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Show intent classifier details
                }
            )
        }

        // Data Management
        item {
            DataSection(
                onExportClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Export functionality
                },
                onImportClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Import functionality
                },
                onClearClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Clear data with confirmation
                }
            )
        }

        // About Section
        item {
            AboutSection(
                onVersionClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Show version details
                },
                onLicensesClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Show licenses
                },
                onPrivacyPolicyClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // TODO: Show privacy policy
                }
            )
        }

        // Bottom padding
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * PrivacySection - Privacy settings section.
 */
@Composable
private fun PrivacySection(
    onPrivacyDashboardClick: () -> Unit,
    onEncryptionClick: () -> Unit
) {
    SettingsSection(
        title = "Privacy",
        icon = Icons.Default.Lock
    ) {
        SettingsItem(
            title = "Privacy Dashboard",
            subtitle = "0 bytes transmitted - 100% local",
            icon = Icons.Default.Security,
            onClick = onPrivacyDashboardClick
        )

        SettingsItem(
            title = "Data Encryption",
            subtitle = "AES-256 - SQLCipher",
            icon = Icons.Default.Shield,
            onClick = onEncryptionClick
        )
    }
}

/**
 * ModelSection - AI model settings section.
 */
@Composable
private fun ModelSection(
    modelInfo: String,
    onClick: () -> Unit
) {
    SettingsSection(
        title = "AI Model",
        icon = Icons.Default.Memory
    ) {
        SettingsItem(
            title = "Current Model",
            subtitle = modelInfo,
            icon = Icons.Default.ModelTraining,
            onClick = onClick
        )
    }
}

/**
 * KnowledgeSection - Knowledge & RAG settings section.
 */
@Composable
private fun KnowledgeSection(
    ragEnabled: Boolean,
    onRagEnabledChange: (Boolean) -> Unit,
    onKnowledgeBaseClick: () -> Unit,
    onIntentClick: () -> Unit
) {
    SettingsSection(
        title = "Knowledge & RAG",
        icon = Icons.Default.MenuBook
    ) {
        SettingsToggleItem(
            title = "RAG (Retrieval-Augmented Generation)",
            subtitle = "Enhance responses with 1,401 expert documents across 24 categories",
            icon = Icons.Default.AutoAwesome,
            checked = ragEnabled,
            onCheckedChange = onRagEnabledChange
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingsItem(
            title = "Knowledge Base",
            subtitle = "1,401 documents - 24 categories",
            icon = Icons.Default.Book,
            onClick = onKnowledgeBaseClick
        )

        SettingsItem(
            title = "Intent Classification",
            subtitle = "20 query types - Adaptive retrieval",
            icon = Icons.Default.Category,
            onClick = onIntentClick
        )
    }
}

/**
 * DataSection - Data management section.
 */
@Composable
private fun DataSection(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    SettingsSection(
        title = "Data",
        icon = Icons.Default.Storage
    ) {
        SettingsItem(
            title = "Export Conversations",
            subtitle = "Backup to JSON",
            icon = Icons.Default.Upload,
            onClick = onExportClick
        )

        SettingsItem(
            title = "Import Conversations",
            subtitle = "Restore from backup",
            icon = Icons.Default.Download,
            onClick = onImportClick
        )

        SettingsItem(
            title = "Clear All Data",
            subtitle = "Reset app to defaults",
            icon = Icons.Default.DeleteForever,
            onClick = onClearClick,
            isDestructive = true
        )
    }
}

/**
 * AboutSection - App information section.
 */
@Composable
private fun AboutSection(
    onVersionClick: () -> Unit,
    onLicensesClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit
) {
    SettingsSection(
        title = "About",
        icon = Icons.Default.Info
    ) {
        SettingsItem(
            title = "Version",
            subtitle = "0.1.0 (Phase 2 Complete)",
            icon = Icons.Default.AppShortcut,
            onClick = onVersionClick
        )

        SettingsItem(
            title = "Open Source Licenses",
            subtitle = "Apache 2.0 - MIT",
            icon = Icons.Default.Code,
            onClick = onLicensesClick
        )

        SettingsItem(
            title = "Privacy Policy",
            subtitle = "No data collection",
            icon = Icons.Default.PrivacyTip,
            onClick = onPrivacyPolicyClick
        )
    }
}

/**
 * AiCoreSection - AICore model preference selector.
 *
 * Allows switching between Gemini Nano (stable) and Gemma 4
 * variants (AICore Developer Preview).
 */
@Composable
private fun AiCoreSection(
    currentPreference: AiCoreModelPreference,
    onPreferenceChange: (AiCoreModelPreference) -> Unit
) {
    SettingsSection(
        title = "AICore Model",
        icon = Icons.Default.AutoAwesome
    ) {
        AiCoreModelPreference.entries.forEach { preference ->
            val isSelected = preference == currentPreference

            SettingsItem(
                title = preference.displayName,
                subtitle = when (preference) {
                    AiCoreModelPreference.STABLE -> "Production model — stable, optimized"
                    AiCoreModelPreference.PREVIEW_SPEED -> "Gemma 4 E2B — 3x faster (Preview)"
                    AiCoreModelPreference.PREVIEW_FULL -> "Gemma 4 E4B — highest quality (Preview)"
                },
                icon = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                onClick = { onPreferenceChange(preference) }
            )

            if (preference != AiCoreModelPreference.entries.last()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MaSpacing.base),
                    color = MaColors.borderSubtle()
                )
            }
        }
    }
}

/**
 * Preview for Settings Screen
 */
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaTheme {
        SettingsScreen()
    }
}
