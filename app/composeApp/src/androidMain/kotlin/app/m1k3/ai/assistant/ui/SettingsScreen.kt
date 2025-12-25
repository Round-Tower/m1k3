package app.m1k3.ai.assistant.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.ai.GenerationConfig
import app.m1k3.ai.assistant.ai.ondevice.AiAvailability
import app.m1k3.ai.assistant.ai.ondevice.AiResult
import app.m1k3.ai.assistant.ai.ondevice.OnDeviceAi
import app.m1k3.ai.assistant.design.tokens.MaFontFamilyCaption
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 間 AI - Settings Screen
 *
 * User preferences and configuration interface.
 *
 * **Features:**
 * - Privacy dashboard (0 bytes transmitted)
 * - Model settings (Gemma 3 270M configuration)
 * - App information (version, build, licenses)
 * - Data management (export, import, clear)
 *
 * **Philosophy:**
 * Privacy-first settings with full transparency and user control.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ma_ai_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // Inject OnDeviceAi for ML Kit status checking
    val onDeviceAi: OnDeviceAi = koinInject()

    // RAG toggle state
    var ragEnabled by remember { mutableStateOf(prefs.getBoolean("rag_enabled", true)) }

    // ML Kit GenAI status
    var mlKitStatus by remember { mutableStateOf<MlKitStatusState>(MlKitStatusState.Checking) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestRunning by remember { mutableStateOf(false) }

    // Model info state
    var modelInfo by remember { mutableStateOf("Loading...") }

    // Check ML Kit availability on launch
    LaunchedEffect(Unit) {
        mlKitStatus = MlKitStatusState.Checking
        val availability = onDeviceAi.checkAvailability()
        mlKitStatus = MlKitStatusState.Loaded(availability)
        modelInfo = onDeviceAi.getModelInfo()
    }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Privacy Section
        item {
            SettingsSection(
                title = "Privacy",
                icon = Icons.Default.Lock,
            ) {
                SettingsItem(
                    title = "Privacy Dashboard",
                    subtitle = "0 bytes transmitted • 100% local",
                    icon = Icons.Default.Security,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Navigate to privacy dashboard
                    },
                )

                SettingsItem(
                    title = "Data Encryption",
                    subtitle = "AES-256 • SQLCipher",
                    icon = Icons.Default.Shield,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show encryption details
                    },
                )
            }
        }

        // Model Settings (Dynamic - reads from OnDeviceAi)
        item {
            SettingsSection(
                title = "AI Model",
                icon = Icons.Default.Memory,
            ) {
                SettingsItem(
                    title = "Current Model",
                    subtitle = modelInfo,
                    icon = Icons.Default.ModelTraining,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show model details
                    },
                )
            }
        }

        // ML Kit GenAI Section (Gemini Nano)
        item {
            SettingsSection(
                title = "ML Kit GenAI",
                icon = Icons.Default.AutoAwesome,
            ) {
                // Status display
                val (statusText, statusColor) = when (val status = mlKitStatus) {
                    is MlKitStatusState.Checking -> "Checking..." to MaterialTheme.colorScheme.onSurfaceVariant
                    is MlKitStatusState.Loaded -> when (status.availability) {
                        is AiAvailability.Available -> "Available (Gemini Nano)" to MaterialTheme.colorScheme.primary
                        is AiAvailability.Downloading -> "Downloading model..." to MaterialTheme.colorScheme.tertiary
                        is AiAvailability.Unavailable -> "Unavailable: ${status.availability.reason}" to MaterialTheme.colorScheme.error
                        is AiAvailability.Fallback -> "Fallback: ${status.availability.engineName}" to MaterialTheme.colorScheme.secondary
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = statusColor,
                        )
                        Text(
                            text = "Status: $statusText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor,
                        )
                    }

                    Text(
                        text = "Android ${Build.VERSION.SDK_INT} (requires 34+)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Test generation result
                    testResult?.let { result ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Test Result:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Test Generation Button
                Surface(
                    onClick = {
                        if (!isTestRunning) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            isTestRunning = true
                            testResult = null
                            scope.launch {
                                try {
                                    // First, ensure model is downloaded/initialized
                                    testResult = "Initializing..."
                                    val downloadResult = onDeviceAi.downloadModelIfNeeded()
                                    downloadResult.fold(
                                        onSuccess = {
                                            // Model ready, now generate
                                            testResult = "Generating..."
                                            val config = GenerationConfig(maxTokens = 64)
                                            val result = onDeviceAi.generate("Hello, what is 2+2?", config)
                                            result.fold(
                                                onSuccess = { response ->
                                                    testResult = "Success: $response"
                                                },
                                                onError = { code, message ->
                                                    testResult = "Generation Error [$code]: $message"
                                                }
                                            )
                                        },
                                        onError = { code, message ->
                                            testResult = "Init Error [$code]: $message"
                                        }
                                    )
                                } catch (e: Exception) {
                                    testResult = "Exception: ${e.message}"
                                } finally {
                                    isTestRunning = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isTestRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = if (isTestRunning) "Running test..." else "Test Generation",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Send 'Hello, what is 2+2?' to AI",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

            }
        }

        // Knowledge & RAG Section
        item {
            SettingsSection(
                title = "Knowledge & RAG",
                icon = Icons.Default.MenuBook,
            ) {
                // RAG Toggle
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "RAG (Retrieval-Augmented Generation)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enhance responses with 1,401 expert documents across 24 categories",
                            style =
                                TextStyle(
                                    fontFamily = MaFontFamilyCaption,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    letterSpacing = 0.25.sp,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = ragEnabled,
                        onCheckedChange = { enabled ->
                            ragEnabled = enabled
                            prefs.edit().putBoolean("rag_enabled", enabled).apply()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            println("⚙️ [Settings] RAG ${if (enabled) "enabled" else "disabled"}")
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsItem(
                    title = "Knowledge Base",
                    subtitle = "1,401 documents • 24 categories",
                    icon = Icons.Default.Book,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show knowledge base browser
                    },
                )

                SettingsItem(
                    title = "Intent Classification",
                    subtitle = "20 query types • Adaptive retrieval",
                    icon = Icons.Default.Category,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show intent classifier details
                    },
                )
            }
        }

        // Data Management
        item {
            SettingsSection(
                title = "Data",
                icon = Icons.Default.Storage,
            ) {
                SettingsItem(
                    title = "Export Conversations",
                    subtitle = "Backup to JSON",
                    icon = Icons.Default.Upload,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Export functionality
                    },
                )

                SettingsItem(
                    title = "Import Conversations",
                    subtitle = "Restore from backup",
                    icon = Icons.Default.Download,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Import functionality
                    },
                )

                SettingsItem(
                    title = "Clear All Data",
                    subtitle = "Reset app to defaults",
                    icon = Icons.Default.DeleteForever,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Clear data with confirmation
                    },
                    isDestructive = true,
                )
            }
        }

        // About Section
        item {
            SettingsSection(
                title = "About",
                icon = Icons.Default.Info,
            ) {
                SettingsItem(
                    title = "Version",
                    subtitle = "0.1.0 (Phase 2 Complete)",
                    icon = Icons.Default.AppShortcut,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show version details
                    },
                )

                SettingsItem(
                    title = "Open Source Licenses",
                    subtitle = "Apache 2.0 • MIT",
                    icon = Icons.Default.Code,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show licenses
                    },
                )

                SettingsItem(
                    title = "Privacy Policy",
                    subtitle = "No data collection",
                    icon = Icons.Default.PrivacyTip,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // TODO: Show privacy policy
                    },
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                content = content,
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
    isDestructive: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * ML Kit status state for UI
 */
private sealed class MlKitStatusState {
    data object Checking : MlKitStatusState()
    data class Loaded(val availability: AiAvailability) : MlKitStatusState()
}

/**
 * Preview for Settings Screen
 */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen()
    }
}
