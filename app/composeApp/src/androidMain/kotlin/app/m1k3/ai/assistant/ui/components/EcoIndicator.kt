package app.m1k3.ai.assistant.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.chat.SessionEcoStats
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackType
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackController
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * 間 AI - Eco Indicator Component
 *
 * Real-time environmental impact display for chat sessions.
 * Shows water, energy, and CO2 savings from local AI inference.
 *
 * Philosophy: Transparency in environmental impact - every token matters.
 */

/**
 * Compact eco indicator for inline chat display
 *
 * @param stats Current session eco statistics
 * @param onClick Optional click handler (e.g., navigate to detailed stats)
 * @param modifier Modifier for the indicator
 * @param variant Display variant (compact, expanded)
 */
@Composable
fun EcoIndicator(
    stats: SessionEcoStats,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    variant: EcoIndicatorVariant = EcoIndicatorVariant.COMPACT
) {
    val haptics = rememberHapticFeedback()

    // Animate value changes
    val animatedWater by animateIntAsState(
        targetValue = stats.waterMl.toInt(),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "water_animation"
    )
    val animatedEnergy by animateIntAsState(
        targetValue = stats.energyWh.toInt(),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "energy_animation"
    )
    val animatedCO2 by animateIntAsState(
        targetValue = stats.co2G.toInt(),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "co2_animation"
    )

    // Pulse animation when values update
    var pulseKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(stats.messageCount) {
        if (stats.messageCount > 0) {
            pulseKey++
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pulseKey > 0) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pulse_animation"
    )

    when (variant) {
        EcoIndicatorVariant.COMPACT -> CompactEcoIndicator(
            waterMl = animatedWater.toLong(),
            energyWh = animatedEnergy.toLong(),
            co2G = animatedCO2.toLong(),
            onClick = onClick,
            scale = scale,
            haptics = haptics,
            modifier = modifier
        )
        EcoIndicatorVariant.EXPANDED -> ExpandedEcoIndicator(
            stats = stats.copy(
                waterMl = animatedWater.toLong(),
                energyWh = animatedEnergy.toLong(),
                co2G = animatedCO2.toLong()
            ),
            onClick = onClick,
            scale = scale,
            haptics = haptics,
            modifier = modifier
        )
    }
}

/**
 * Compact variant - single line with icons
 */
@Composable
private fun CompactEcoIndicator(
    waterMl: Long,
    energyWh: Long,
    co2G: Long,
    onClick: (() -> Unit)?,
    scale: Float,
    haptics: HapticFeedbackController,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = MaColors.BgElevated,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = MaSpacing.md, vertical = MaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Water
            EcoMetric(
                emoji = "💧",
                value = formatWaterCompact(waterMl),
                color = Color(0xFF4FC3F7) // Light blue
            )

            // Energy
            EcoMetric(
                emoji = "⚡",
                value = formatEnergyCompact(energyWh),
                color = Color(0xFFFFA726) // Orange
            )

            // CO2
            EcoMetric(
                emoji = "🌱",
                value = formatCO2Compact(co2G),
                color = Color(0xFF66BB6A) // Green
            )

            // Info icon (if clickable)
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "View details",
                    tint = MaColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Expanded variant - detailed card with labels
 */
@Composable
private fun ExpandedEcoIndicator(
    stats: SessionEcoStats,
    onClick: (() -> Unit)?,
    scale: Float,
    haptics: HapticFeedbackController,
    modifier: Modifier = Modifier
) {
    MaCard(
        modifier = modifier.scale(scale),
        onClick = if (onClick != null) {
            {
                haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                onClick()
            }
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌿 Environmental Savings",
                    style = MaTypography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaColors.TextPrimary
                )

                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "View details",
                        tint = MaColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaColors.BorderLight,
                thickness = 1.dp
            )

            // Metrics
            EcoMetricRow(
                emoji = "💧",
                label = "Water Saved",
                value = stats.formatWater(),
                color = Color(0xFF4FC3F7)
            )

            EcoMetricRow(
                emoji = "⚡",
                label = "Energy Saved",
                value = stats.formatEnergy(),
                color = Color(0xFFFFA726)
            )

            EcoMetricRow(
                emoji = "🌱",
                label = "CO2 Prevented",
                value = stats.formatCO2(),
                color = Color(0xFF66BB6A)
            )

            // Footer stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${stats.messageCount} messages",
                    style = MaTypography.bodySmall,
                    color = MaColors.TextMuted
                )

                Text(
                    text = "${stats.totalTokens} tokens",
                    style = MaTypography.bodySmall,
                    color = MaColors.TextMuted
                )
            }
        }
    }
}

/**
 * Single eco metric (compact)
 */
@Composable
private fun EcoMetric(
    emoji: String,
    value: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            style = MaTypography.bodySmall
        )
        Text(
            text = value,
            style = MaTypography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * Eco metric row (expanded)
 */
@Composable
private fun EcoMetricRow(
    emoji: String,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    style = MaTypography.bodyMedium
                )
            }

            Text(
                text = label,
                style = MaTypography.bodyMedium,
                color = MaColors.TextSecondary
            )
        }

        Text(
            text = value,
            style = MaTypography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Compact formatting helpers
 */
private fun formatWaterCompact(waterMl: Long): String {
    return when {
        waterMl >= 1000 -> "${waterMl / 1000}L"
        waterMl == 0L -> "0ml"
        else -> "${waterMl}ml"
    }
}

private fun formatEnergyCompact(energyWh: Long): String {
    return when {
        energyWh >= 1000 -> "${energyWh / 1000}kWh"
        energyWh == 0L -> "0Wh"
        else -> "${energyWh}Wh"
    }
}

private fun formatCO2Compact(co2G: Long): String {
    return when {
        co2G >= 1000 -> "${co2G / 1000}kg"
        co2G == 0L -> "0g"
        else -> "${co2G}g"
    }
}

/**
 * Display variant options
 */
enum class EcoIndicatorVariant {
    /** Compact single-line display */
    COMPACT,

    /** Expanded card with detailed labels */
    EXPANDED
}

/**
 * Preview helpers (for Android Studio preview)
 */
@Composable
fun EcoIndicatorPreview() {
    val sampleStats = SessionEcoStats(
        totalTokens = 1250,
        waterMl = 2500,
        energyWh = 1800,
        co2G = 1200,
        messageCount = 5
    )

    Column(
        modifier = Modifier
            .background(MaColors.BgPrimary)
            .padding(MaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.md)
    ) {
        Text("Compact Variant:", color = MaColors.TextPrimary)
        EcoIndicator(
            stats = sampleStats,
            variant = EcoIndicatorVariant.COMPACT,
            onClick = { println("Clicked!") }
        )

        Text("Expanded Variant:", color = MaColors.TextPrimary)
        EcoIndicator(
            stats = sampleStats,
            variant = EcoIndicatorVariant.EXPANDED,
            onClick = { println("Clicked!") }
        )
    }
}
