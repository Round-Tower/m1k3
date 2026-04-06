package app.m1k3.ai.assistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.eco.LifetimeStats

/**
 * EcoStatsHero - Large hero section with animated key metrics.
 *
 * Shows the three main eco metrics (water, energy, CO2) as large
 * numbers with count-up animation and glassmorphic card styling.
 *
 * Philosophy: Make impact feel tangible. Big numbers, bold colors.
 */
@Composable
fun EcoStatsHero(
    stats: LifetimeStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.md)
    ) {
        // Title
        Text(
            text = "Your Impact",
            style = MaTypography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaColors.textPrimary(),
            modifier = Modifier.padding(bottom = MaSpacing.xs)
        )

        // Three hero metric cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm)
        ) {
            HeroMetricCard(
                value = stats.totalWaterMl,
                unit = "ml",
                label = "Water Saved",
                color = Color(0xFF4FC3F7),
                modifier = Modifier.weight(1f)
            )
            HeroMetricCard(
                value = stats.totalEnergyWh,
                unit = "Wh",
                label = "Energy Saved",
                color = MaColors.Orange,
                modifier = Modifier.weight(1f)
            )
            HeroMetricCard(
                value = stats.totalCo2G,
                unit = "g",
                label = "CO2 Prevented",
                color = Color(0xFF66BB6A),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * HeroMetricCard - Single large metric with count-up animation.
 */
@Composable
private fun HeroMetricCard(
    value: Long,
    unit: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedValue = remember { Animatable(0f) }

    LaunchedEffect(value) {
        animatedValue.animateTo(
            targetValue = value.toFloat(),
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        )
    }

    val cardShape = RoundedCornerShape(MaRadius.lg)

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(color.copy(alpha = 0.08f), cardShape)
            .padding(MaSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
    ) {
        // Big number
        Text(
            text = formatHeroValue(animatedValue.value, unit),
            style = MaTypography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )

        // Unit/label
        Text(
            text = label,
            style = MaTypography.labelSmall,
            color = MaColors.textMuted(),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * SavingsRing - Circular progress ring showing savings percentage.
 *
 * A visual way to show how much the user saves vs cloud AI.
 * The ring fills based on the savings percentage.
 */
@Composable
fun SavingsRing(
    savingsPercent: Double,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedSweep = remember { Animatable(0f) }

    LaunchedEffect(savingsPercent) {
        animatedSweep.animateTo(
            targetValue = (savingsPercent / 100.0 * 360.0).toFloat(),
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(80.dp)
                    .aspectRatio(1f)
            ) {
                val strokeWidth = 8.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val topLeft = Offset(
                    (size.width - radius * 2) / 2,
                    (size.height - radius * 2) / 2
                )

                // Background track
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = animatedSweep.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Center percentage
            Text(
                text = "${savingsPercent.toInt()}%",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        Text(
            text = label,
            style = MaTypography.labelSmall,
            color = MaColors.textMuted(),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * SavingsRingsRow - Three circular rings showing water/energy/CO2 savings.
 */
@Composable
fun SavingsRingsRow(
    waterPercent: Double,
    energyPercent: Double,
    co2Percent: Double,
    modifier: Modifier = Modifier
) {
    MaCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md)
        ) {
            Text(
                text = "Savings vs Cloud AI",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.textPrimary()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SavingsRing(
                    savingsPercent = waterPercent,
                    label = "Water",
                    color = Color(0xFF4FC3F7)
                )
                SavingsRing(
                    savingsPercent = energyPercent,
                    label = "Energy",
                    color = MaColors.Orange
                )
                SavingsRing(
                    savingsPercent = co2Percent,
                    label = "CO2",
                    color = Color(0xFF66BB6A)
                )
            }
        }
    }
}

/**
 * Format a hero value with appropriate units.
 */
private fun formatHeroValue(value: Float, unit: String): String {
    return when {
        value >= 1000f -> String.format("%.1f%s", value / 1000f, if (unit == "ml") "L" else "k$unit")
        else -> "${value.toInt()}$unit"
    }
}
