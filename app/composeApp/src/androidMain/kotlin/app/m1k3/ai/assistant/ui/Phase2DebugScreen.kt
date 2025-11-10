package app.m1k3.ai.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.demo.DemoDataStats
import app.m1k3.ai.assistant.demo.Phase2DebugScreen
import app.m1k3.ai.assistant.demo.Phase2TestResults
import app.m1k3.ai.assistant.design.components.MaButtonPrimary
import app.m1k3.ai.assistant.design.components.MaButtonSecondary
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackType
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaFontFamilyCaption
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 2 Debug Screen - Comprehensive Testing UI
 *
 * Visual testing interface for Phase 2 features:
 * - Conversation History & Management
 * - Search Functionality
 * - Export (JSON/Markdown)
 * - Eco Metrics Tracking
 * - View Models (HistoryViewModel, EcoStatsViewModel)
 *
 * Enables rapid testing and validation without manual navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase2DebugScreenUI(
    database: MaDatabase,
    onBackClick: () -> Unit = {},
) {
    val haptics = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    val debugScreen = remember { Phase2DebugScreen(database) }

    var testResults by remember { mutableStateOf<Phase2TestResults?>(null) }
    var demoStats by remember { mutableStateOf<DemoDataStats?>(null) }
    var isRunningTests by remember { mutableStateOf(false) }
    var isCreatingDemo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🧪 Phase 2 Debug Lab",
                            style = MaTypography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary,
                        )
                        Text(
                            "Chat History & Eco Credentials",
                            style =
                                TextStyle(
                                    fontFamily = MaFontFamilyCaption,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    letterSpacing = 0.25.sp,
                                ),
                            color = MaColors.TextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaColors.TextPrimary,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaColors.BgPrimary,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(MaSpacing.md)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md),
        ) {
            // Quick Actions Card
            MaCard {
                Column(
                    modifier = Modifier.padding(MaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                ) {
                    Text(
                        "Quick Actions",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaColors.TextPrimary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                    ) {
                        MaButtonPrimary(
                            text = if (isRunningTests) "Running..." else "Run All Tests",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SUCCESS)
                                scope.launch {
                                    isRunningTests = true
                                    testResults =
                                        withContext(Dispatchers.Default) {
                                            debugScreen.runAllTests()
                                        }
                                    isRunningTests = false
                                }
                            },
                            enabled = !isRunningTests,
                            modifier = Modifier.weight(1f),
                        )

                        MaButtonSecondary(
                            text = if (isCreatingDemo) "Creating..." else "Create Demo Data",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SUCCESS)
                                scope.launch {
                                    isCreatingDemo = true
                                    demoStats =
                                        withContext(Dispatchers.Default) {
                                            debugScreen.createDemoData()
                                        }
                                    isCreatingDemo = false
                                }
                            },
                            enabled = !isCreatingDemo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Test Results Card
            testResults?.let { results ->
                MaCard {
                    Column(
                        modifier = Modifier.padding(MaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Test Results",
                                style = MaTypography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaColors.TextPrimary,
                            )

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color =
                                    if (results.successRate == 100.0) {
                                        MaColors.Success
                                    } else {
                                        MaColors.Warning
                                    },
                            ) {
                                Text(
                                    "${results.passedCount}/${results.totalCount} (${String.format("%.1f", results.successRate)}%)",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaTypography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaColors.White,
                                )
                            }
                        }

                        HorizontalDivider(color = MaColors.BorderLight, modifier = Modifier.padding(vertical = MaSpacing.sm))

                        results.tests.forEach { test ->
                            TestResultRow(test.name, test.passed, test.details)
                        }
                    }
                }
            }

            // Demo Data Stats Card
            demoStats?.let { stats ->
                MaCard {
                    Column(
                        modifier = Modifier.padding(MaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                    ) {
                        Text(
                            "Demo Data Created",
                            style = MaTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary,
                        )

                        HorizontalDivider(color = MaColors.BorderLight, modifier = Modifier.padding(vertical = MaSpacing.sm))

                        StatRow("Conversations", "${stats.conversationsCreated}")
                        StatRow("Messages", "${stats.messagesCreated}")
                        StatRow("Tokens Processed", "${stats.tokensProcessed}")

                        Spacer(modifier = Modifier.height(MaSpacing.sm))

                        Text(
                            "Environmental Savings",
                            style = MaTypography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary,
                        )

                        StatRow("💧 Water Saved", "${stats.waterSaved} ml")
                        StatRow("⚡ Energy Saved", "${stats.energySaved} Wh")
                        StatRow("🌱 CO2 Prevented", "${stats.co2Saved} g")
                    }
                }
            }

            // Feature Overview Card
            MaCard {
                Column(
                    modifier = Modifier.padding(MaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                ) {
                    Text(
                        "Phase 2 Features",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaColors.TextPrimary,
                    )

                    HorizontalDivider(color = MaColors.BorderLight, modifier = Modifier.padding(vertical = MaSpacing.sm))

                    FeatureRow("💬", "Conversation Management", "Create, update, delete conversations")
                    FeatureRow("🔍", "Search", "Keyword + semantic search (future)")
                    FeatureRow("📤", "Export", "JSON & Markdown export")
                    FeatureRow("🌿", "Eco Metrics", "Track environmental savings")
                    FeatureRow("🎯", "View Models", "HistoryViewModel, EcoStatsViewModel")
                }
            }
        }
    }
}

@Composable
private fun TestResultRow(
    name: String,
    passed: Boolean,
    details: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
    ) {
        Text(
            text = if (passed) "✅" else "❌",
            style = MaTypography.bodyMedium,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaColors.TextPrimary,
            )
            Text(
                text = details,
                style = MaTypography.bodySmall,
                color = MaColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaTypography.bodyMedium,
            color = MaColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaTypography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaColors.TextPrimary,
        )
    }
}

@Composable
private fun FeatureRow(
    icon: String,
    name: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
    ) {
        Text(
            text = icon,
            style = MaTypography.bodyLarge,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaColors.TextPrimary,
            )
            Text(
                text = description,
                style = MaTypography.bodySmall,
                color = MaColors.TextSecondary,
            )
        }
    }
}
