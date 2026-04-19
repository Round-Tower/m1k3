package app.m1k3.ai.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.eco.EcoComparison
import app.m1k3.ai.assistant.eco.LifetimeStats
import app.m1k3.ai.assistant.eco.ProjectStats
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * LifetimeStatsCard - Displays lifetime environmental savings.
 *
 * @param stats Lifetime statistics to display
 */
@Composable
fun LifetimeStatsCard(stats: LifetimeStats) {
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
                    color = MaColors.textPrimary(),
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
                emoji = "W",
                label = "Water Saved",
                value = formatWater(stats.totalWaterMl.toDouble()),
                progress = calculateProgress(stats.totalWaterMl.toDouble(), 10000.0),
                color = Color(0xFF4FC3F7),
            )

            AnimatedMetricRow(
                emoji = "E",
                label = "Energy Saved",
                value = formatEnergy(stats.totalEnergyWh.toDouble()),
                progress = calculateProgress(stats.totalEnergyWh.toDouble(), 5000.0),
                color = Color(0xFFFFA726),
            )

            AnimatedMetricRow(
                emoji = "C",
                label = "CO2 Prevented",
                value = formatCO2(stats.totalCo2G.toDouble()),
                progress = calculateProgress(stats.totalCo2G.toDouble(), 3000.0),
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
                EcoStatChip(label = "Queries", value = "${stats.totalQueries}")
                EcoStatChip(label = "Chat inference", value = "on-device")
            }
        }
    }
}

/**
 * CloudComparisonCard - Compares local vs cloud AI environmental impact.
 *
 * @param comparison Comparison data between local and cloud
 */
@Composable
fun CloudComparisonCard(comparison: EcoComparison) {
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
                color = MaColors.textPrimary(),
            )

            Text(
                text = "Environmental impact comparison with cloud-based AI services",
                style = MaTypography.bodySmall,
                color = MaColors.textSecondary(),
            )

            HorizontalDivider(color = MaColors.BorderLight)

            // Comparison metrics
            ComparisonRow(
                emoji = "W",
                label = "Water",
                localValue = formatWater(comparison.localWaterMl),
                cloudValue = formatWater(comparison.cloudWaterMl),
                savingsPercent = comparison.waterSavingsPercent,
                color = Color(0xFF4FC3F7),
            )

            ComparisonRow(
                emoji = "E",
                label = "Energy",
                localValue = formatEnergy(comparison.localEnergyWh),
                cloudValue = formatEnergy(comparison.cloudEnergyWh),
                savingsPercent = comparison.energySavingsPercent,
                color = Color(0xFFFFA726),
            )

            ComparisonRow(
                emoji = "C",
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
 * ProjectMetricsCard - Displays project-specific eco metrics.
 *
 * @param projectId ID of the project
 * @param metrics Project statistics
 */
@Composable
fun ProjectMetricsCard(
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
                color = MaColors.textPrimary(),
            )

            HorizontalDivider(color = MaColors.BorderLight)

            SimpleMetricRow("Water", formatWater(metrics.waterMl.toDouble()))
            SimpleMetricRow("Energy", formatEnergy(metrics.energyWh.toDouble()))
            SimpleMetricRow("CO2", formatCO2(metrics.co2G.toDouble()))
            SimpleMetricRow("Tokens", "${metrics.tokens}")
        }
    }
}

/**
 * EmptyStatsCard - Empty state for when no stats are available.
 */
@Composable
fun EmptyStatsCard() {
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
                text = "No environmental data yet",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaColors.textPrimary(),
            )

            Text(
                text = "Start chatting to see your environmental impact",
                style = MaTypography.bodyMedium,
                color = MaColors.textSecondary(),
            )
        }
    }
}

/**
 * PrivacyStatementCard - Privacy statement about local processing.
 */
@Composable
fun PrivacyStatementCard() {
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
                    text = "On-Device Chat Inference",
                    style = MaTypography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaColors.textPrimary(),
                )
            }

            Text(
                text =
                    "Every chat response runs on your phone. Environmental savings " +
                        "calculated from avoiding cloud data centre round-trips for those tokens.",
                style = MaTypography.bodySmall,
                color = MaColors.textSecondary(),
            )
        }
    }
}

/**
 * EcoErrorCard - Error display for eco stats screen.
 *
 * @param message Error message
 * @param onDismiss Callback when dismissed
 */
@Composable
fun EcoErrorCard(
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
                text = message,
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
 * AnimatedMetricRow - Metric row with animated progress bar.
 */
@Composable
fun AnimatedMetricRow(
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
                    color = MaColors.textSecondary(),
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
 * AnimatedProgressBar - Progress bar with animation.
 */
@Composable
fun AnimatedProgressBar(
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
 * ComparisonRow - Comparison row for local vs cloud metrics.
 */
@Composable
fun ComparisonRow(
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
                color = MaColors.textSecondary(),
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
                    color = MaColors.textMuted(),
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
                    color = MaColors.textMuted(),
                )
                Text(
                    text = cloudValue,
                    style = MaTypography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaColors.textMuted(),
                )
            }
        }
    }
}

/**
 * SimpleMetricRow - Simple metric row without progress bar.
 */
@Composable
fun SimpleMetricRow(
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
            color = MaColors.textSecondary(),
        )
        Text(
            text = value,
            style = MaTypography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaColors.textPrimary(),
        )
    }
}

/**
 * EcoStatChip - Small stat badge.
 */
@Composable
fun EcoStatChip(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaTypography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaColors.textPrimary(),
        )
        Text(
            text = label,
            style = MaTypography.labelSmall,
            color = MaColors.textMuted(),
        )
    }
}

// Formatting helpers
fun formatWater(waterMl: Double): String =
    when {
        waterMl >= 1000.0 -> String.format("%.2f L", waterMl / 1000.0)
        else -> "${waterMl.toInt()} ml"
    }

fun formatEnergy(energyWh: Double): String =
    when {
        energyWh >= 1000.0 -> String.format("%.2f kWh", energyWh / 1000.0)
        else -> "${energyWh.toInt()} Wh"
    }

fun formatCO2(co2G: Double): String =
    when {
        co2G >= 1000.0 -> String.format("%.2f kg", co2G / 1000.0)
        else -> "${co2G.toInt()} g"
    }

fun calculateProgress(
    value: Double,
    maxValue: Double,
): Float = (value / maxValue).coerceIn(0.0, 1.0).toFloat()

/**
 * CloudBytesAvoidedHero — headline stat. Chat inference never left the
 * device, so this many bytes of cloud-LLM round-trip never happened.
 * Estimate, not audit-grade (see EcoCalculator.cloudBytesAvoided).
 */
@Composable
fun CloudBytesAvoidedHero(
    bytesAvoided: Long,
    modifier: Modifier = Modifier,
) {
    MaCard(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.xs),
        ) {
            Text(
                text = "Your device is the cloud",
                style = MaTypography.labelMedium,
                color = MaColors.textSecondary(),
            )
            Text(
                text = formatBytes(bytesAvoided),
                style = MaTypography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaColors.Orange,
            )
            Text(
                text = "of cloud API traffic avoided — every token answered on-device",
                style = MaTypography.bodySmall,
                color = MaColors.textMuted(),
            )
        }
    }
}

/**
 * NetworkUsageCard — honest accounting of real network bytes.
 * Downloads + web searches. Small numbers are the point: chat itself
 * stays on-device, so the bar stays low.
 */
@Composable
fun NetworkUsageCard(
    bytesSent: Long,
    bytesReceived: Long,
    modifier: Modifier = Modifier,
) {
    MaCard(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
        ) {
            Text(
                text = "Network Usage",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.textPrimary(),
            )
            Text(
                text = "Real bytes from model downloads and web searches. User-initiated only — no background network.",
                style = MaTypography.bodySmall,
                color = MaColors.textMuted(),
            )

            HorizontalDivider(color = MaColors.BorderLight)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.md),
            ) {
                NetworkBytesStat(
                    label = "Sent",
                    value = formatBytes(bytesSent),
                    modifier = Modifier.weight(1f),
                )
                NetworkBytesStat(
                    label = "Received",
                    value = formatBytes(bytesReceived),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Small vertical label/value pair, inline inside the NetworkUsageCard row. */
@Composable
private fun NetworkBytesStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaTypography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaColors.textPrimary(),
        )
        Text(
            text = label,
            style = MaTypography.labelSmall,
            color = MaColors.textMuted(),
        )
    }
}

/** Format a byte count for display (KB / MB / GB). Used by eco UI. */
fun formatBytes(bytes: Long): String {
    val safe = if (bytes < 0L) 0L else bytes
    return when {
        safe >= 1_000_000_000L -> String.format("%.2f GB", safe / 1_000_000_000.0)
        safe >= 1_000_000L -> String.format("%.1f MB", safe / 1_000_000.0)
        safe >= 1_000L -> String.format("%.1f KB", safe / 1_000.0)
        else -> "$safe B"
    }
}

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun PrivacyStatementCardPreview() {
    MaTheme {
        PrivacyStatementCard()
    }
}
