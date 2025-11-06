package app.m1k3.ai.assistant.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackType
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaFontFamilyCaption
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.eco.LifetimeStats
import app.m1k3.ai.assistant.eco.ProjectStats
import app.m1k3.ai.assistant.history.*
import kotlinx.coroutines.launch

/**
 * 間 AI - Environmental Statistics Screen
 *
 * Comprehensive environmental impact dashboard:
 * - Lifetime savings across all projects
 * - Project-specific metrics
 * - Local vs Cloud comparison
 * - Visual charts and progress indicators
 *
 * Philosophy: Transparency breeds accountability - show real impact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoStatsScreen(
    database: MaDatabase,
    projectId: String? = null,
    onBackClick: () -> Unit = {},
) {
    val haptics = rememberHapticFeedback()
    val scope = rememberCoroutineScope()

    // Initialize ViewModel
    val viewModel =
        remember {
            val repository = EcoMetricsRepository(database)
            EcoStatsViewModel(
                repository = repository,
                scope = scope,
            )
        }

    val state by viewModel.state.collectAsState()

    // Load stats on first composition
    LaunchedEffect(projectId) {
        viewModel.loadLifetimeStats()
        projectId?.let { viewModel.loadProjectStats(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🌿 Environmental Impact",
                            style = MaTypography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary,
                        )
                        Text(
                            "100% local AI inference",
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
                actions = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                        scope.launch {
                            viewModel.loadLifetimeStats()
                            projectId?.let { viewModel.loadProjectStats(it) }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (state.isLoading) {
                // Loading indicator
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaColors.Orange)
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(MaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.md),
                ) {
                    // Error display
                    state.error?.let { error ->
                        ErrorCard(
                            message = error,
                            onDismiss = { viewModel.clearError() },
                        )
                    }

                    // Lifetime stats
                    state.lifetimeStats?.let { stats ->
                        LifetimeStatsCard(stats = stats)
                    }

                    // Cloud comparison
                    state.cloudComparison?.let { comparison ->
                        CloudComparisonCard(comparison = comparison)
                    }

                    // Project-specific stats
                    if (projectId != null) {
                        state.projectMetrics?.let { metrics ->
                            ProjectMetricsCard(
                                projectId = projectId,
                                metrics = metrics,
                            )
                        }
                    }

                    // Empty state
                    if (state.lifetimeStats == null && !state.isLoading) {
                        EmptyStatsCard()
                    }

                    // Privacy statement
                    PrivacyStatementCard()
                }
            }
        }
    }
}

/**
 * Lifetime statistics card
 */
@Composable
private fun LifetimeStatsCard(stats: LifetimeStats) {
    MaCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Lifetime Savings",
                    style = MaTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaColors.TextPrimary,
                )

                Surface(
                    shape = CircleShape,
                    color = MaColors.Orange.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "${stats.totalTokens} tokens",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaTypography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaColors.Orange,
                    )
                }
            }

            HorizontalDivider(color = MaColors.BorderLight)

            // Metrics with animated progress bars
            AnimatedMetricRow(
                emoji = "💧",
                label = "Water Saved",
                value = formatWater(stats.totalWaterMl.toDouble()),
                progress = calculateProgress(stats.totalWaterMl.toDouble(), 10000.0), // 10L max
                color = Color(0xFF4FC3F7),
            )

            AnimatedMetricRow(
                emoji = "⚡",
                label = "Energy Saved",
                value = formatEnergy(stats.totalEnergyWh.toDouble()),
                progress = calculateProgress(stats.totalEnergyWh.toDouble(), 5000.0), // 5kWh max
                color = Color(0xFFFFA726),
            )

            AnimatedMetricRow(
                emoji = "🌱",
                label = "CO2 Prevented",
                value = formatCO2(stats.totalCo2G.toDouble()),
                progress = calculateProgress(stats.totalCo2G.toDouble(), 3000.0), // 3kg max
                color = Color(0xFF66BB6A),
            )

            // Footer stats
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = MaSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatChip(label = "Queries", value = "${stats.totalQueries}")
                StatChip(label = "0 bytes sent", value = "100% local")
            }
        }
    }
}

/**
 * Cloud comparison card
 */
@Composable
private fun CloudComparisonCard(comparison: EcoComparison) {
    MaCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md),
        ) {
            // Header
            Text(
                text = "Local vs Cloud AI",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary,
            )

            Text(
                text = "Environmental impact comparison with cloud-based AI services",
                style = MaTypography.bodySmall,
                color = MaColors.TextSecondary,
            )

            HorizontalDivider(color = MaColors.BorderLight)

            // Comparison metrics
            ComparisonRow(
                emoji = "💧",
                label = "Water",
                localValue = formatWater(comparison.localWaterMl),
                cloudValue = formatWater(comparison.cloudWaterMl),
                savingsPercent = comparison.waterSavingsPercent,
                color = Color(0xFF4FC3F7),
            )

            ComparisonRow(
                emoji = "⚡",
                label = "Energy",
                localValue = formatEnergy(comparison.localEnergyWh),
                cloudValue = formatEnergy(comparison.cloudEnergyWh),
                savingsPercent = comparison.energySavingsPercent,
                color = Color(0xFFFFA726),
            )

            ComparisonRow(
                emoji = "🌱",
                label = "CO2",
                localValue = formatCO2(comparison.localCo2G),
                cloudValue = formatCO2(comparison.cloudCo2G),
                savingsPercent = comparison.co2SavingsPercent,
                color = Color(0xFF66BB6A),
            )
        }
    }
}

/**
 * Project-specific metrics card
 */
@Composable
private fun ProjectMetricsCard(
    projectId: String,
    metrics: ProjectStats,
) {
    MaCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
        ) {
            Text(
                text = "Project: $projectId",
                style = MaTypography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary,
            )

            HorizontalDivider(color = MaColors.BorderLight)

            SimpleMetricRow("💧 Water", formatWater(metrics.waterMl.toDouble()))
            SimpleMetricRow("⚡ Energy", formatEnergy(metrics.energyWh.toDouble()))
            SimpleMetricRow("🌱 CO2", formatCO2(metrics.co2G.toDouble()))
            SimpleMetricRow("🔢 Tokens", "${metrics.tokens}")
        }
    }
}

/**
 * Empty state card
 */
@Composable
private fun EmptyStatsCard() {
    MaCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md),
        ) {
            Text(
                text = "🌱",
                style = MaTypography.displaySmall,
            )

            Text(
                text = "No environmental data yet",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaColors.TextPrimary,
            )

            Text(
                text = "Start chatting to see your environmental impact",
                style = MaTypography.bodyMedium,
                color = MaColors.TextSecondary,
            )
        }
    }
}

/**
 * Privacy statement card
 */
@Composable
private fun PrivacyStatementCard() {
    MaCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🔒",
                    style = MaTypography.titleMedium,
                )
                Text(
                    text = "100% Local Processing",
                    style = MaTypography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaColors.TextPrimary,
                )
            }

            Text(
                text =
                    "All AI inference happens on your device. Zero data transmission. " +
                        "Environmental savings calculated from avoiding cloud data center usage.",
                style = MaTypography.bodySmall,
                color = MaColors.TextSecondary,
            )
        }
    }
}

/**
 * Animated metric row with progress bar
 */
@Composable
private fun AnimatedMetricRow(
    emoji: String,
    label: String,
    value: String,
    progress: Float,
    color: Color,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = emoji, style = MaTypography.bodyMedium)
                Text(
                    text = label,
                    style = MaTypography.bodyMedium,
                    color = MaColors.TextSecondary,
                )
            }

            Text(
                text = value,
                style = MaTypography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }

        // Animated progress bar
        AnimatedProgressBar(
            progress = progress,
            color = color,
        )
    }
}

/**
 * Animated progress bar
 */
@Composable
private fun AnimatedProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "progress_animation",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaColors.BgElevated),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(CircleShape)
                    .background(color),
        )
    }
}

/**
 * Comparison row (local vs cloud)
 */
@Composable
private fun ComparisonRow(
    emoji: String,
    label: String,
    localValue: String,
    cloudValue: String,
    savingsPercent: Double,
    color: Color,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$emoji $label",
                style = MaTypography.bodyMedium,
                color = MaColors.TextSecondary,
            )

            Surface(
                shape = CircleShape,
                color = MaColors.Success.copy(alpha = 0.2f),
            ) {
                Text(
                    text = "${savingsPercent.toInt()}% saved",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaTypography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaColors.Success,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Local",
                    style = MaTypography.labelSmall,
                    color = MaColors.TextMuted,
                )
                Text(
                    text = localValue,
                    style = MaTypography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = color,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Cloud",
                    style = MaTypography.labelSmall,
                    color = MaColors.TextMuted,
                )
                Text(
                    text = cloudValue,
                    style = MaTypography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaColors.TextMuted,
                )
            }
        }
    }
}

/**
 * Simple metric row (no progress bar)
 */
@Composable
private fun SimpleMetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaTypography.bodySmall,
            color = MaColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaTypography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaColors.TextPrimary,
        )
    }
}

/**
 * Stat chip (small badge)
 */
@Composable
private fun StatChip(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaTypography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaColors.TextPrimary,
        )
        Text(
            text = label,
            style = MaTypography.labelSmall,
            color = MaColors.TextMuted,
        )
    }
}

/**
 * Error card
 */
@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
) {
    MaCard {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚠️ $message",
                modifier = Modifier.weight(1f),
                style = MaTypography.bodyMedium,
                color = MaColors.Error,
            )

            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaColors.Error)
            }
        }
    }
}

/**
 * Formatting helpers
 */
private fun formatWater(waterMl: Double): String =
    when {
        waterMl >= 1000.0 -> String.format("%.2f L", waterMl / 1000.0)
        else -> "${waterMl.toInt()} ml"
    }

private fun formatEnergy(energyWh: Double): String =
    when {
        energyWh >= 1000.0 -> String.format("%.2f kWh", energyWh / 1000.0)
        else -> "${energyWh.toInt()} Wh"
    }

private fun formatCO2(co2G: Double): String =
    when {
        co2G >= 1000.0 -> String.format("%.2f kg", co2G / 1000.0)
        else -> "${co2G.toInt()} g"
    }

private fun calculateProgress(
    value: Double,
    maxValue: Double,
): Float = (value / maxValue).coerceIn(0.0, 1.0).toFloat()
