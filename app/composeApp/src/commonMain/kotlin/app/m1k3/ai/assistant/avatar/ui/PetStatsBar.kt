package app.m1k3.ai.assistant.avatar.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.avatar.PixelPetState
import app.m1k3.ai.assistant.avatar.EvolutionStage
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.eco.EcoCalculator

/**
 * 間 AI Pet Stats Bar - Compact UI Component
 *
 * Displays health, energy, and happiness bars with eco credit tooltips.
 * Tapping a bar shows the corresponding eco metric (water/energy/CO2).
 *
 * **Design:**
 * - 3 horizontal bars stacked vertically (8dp height each)
 * - Health (green), Energy (yellow), Happiness (pink)
 * - Smooth fill animations
 * - Tap to reveal eco tooltips
 *
 * **Philosophy:**
 * Visual connection between eco consciousness and pet wellbeing.
 * Users see how their AI usage directly nurtures the pixel pet.
 */

@Composable
fun PetStatsBar(
    petState: PixelPetState,
    modifier: Modifier = Modifier,
    showEcoTooltips: Boolean = true,
    compact: Boolean = false
) {
    var selectedStat by remember { mutableStateOf<StatType?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Health Bar (Water)
        StatBarRow(
            label = "Health",
            value = petState.health,
            maxValue = 100f,
            color = MaColors.Success, // Green - theme-aware
            icon = "💧",
            isSelected = selectedStat == StatType.HEALTH,
            onTap = {
                selectedStat = if (selectedStat == StatType.HEALTH) null else StatType.HEALTH
            },
            compact = compact
        )

        // Show eco tooltip for health (water saved)
        if (showEcoTooltips && selectedStat == StatType.HEALTH) {
            EcoTooltip(
                icon = "💧",
                label = "Water Saved",
                value = EcoCalculator.formatWater(petState.lifetimeWaterMl.toInt()),
                description = "Maintains health through hydration"
            )
        }

        // Energy Bar (Energy)
        StatBarRow(
            label = "Energy",
            value = petState.energy,
            maxValue = 100f,
            color = MaColors.Warning, // Yellow - theme-aware
            icon = "⚡",
            isSelected = selectedStat == StatType.ENERGY,
            onTap = {
                selectedStat = if (selectedStat == StatType.ENERGY) null else StatType.ENERGY
            },
            compact = compact
        )

        // Show eco tooltip for energy
        if (showEcoTooltips && selectedStat == StatType.ENERGY) {
            EcoTooltip(
                icon = "⚡",
                label = "Energy Saved",
                value = EcoCalculator.formatEnergy(petState.lifetimeEnergyWh.toInt()),
                description = "Powers pet's activity and vitality"
            )
        }

        // Happiness Bar (CO2)
        StatBarRow(
            label = "Happiness",
            value = petState.happiness,
            maxValue = 100f,
            color = Color(0xFFE91E63), // Pink - semantic stat color (not in MaColors)
            icon = "💕",
            isSelected = selectedStat == StatType.HAPPINESS,
            onTap = {
                selectedStat = if (selectedStat == StatType.HAPPINESS) null else StatType.HAPPINESS
            },
            compact = compact
        )

        // Show eco tooltip for happiness (CO2 prevented)
        if (showEcoTooltips && selectedStat == StatType.HAPPINESS) {
            EcoTooltip(
                icon = "🌱",
                label = "CO₂ Prevented",
                value = EcoCalculator.formatCO2(petState.lifetimeCO2G.toInt()),
                description = "Environmental care brings joy"
            )
        }

        // Evolution stage indicator (if not compact)
        if (!compact && petState.evolutionStage.ordinal > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✨ ${petState.evolutionStage.displayName} Stage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaColors.Orange, // Gold - use theme's accent
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Single stat bar row with label and value
 */
@Composable
private fun StatBarRow(
    label: String,
    value: Float,
    maxValue: Float,
    color: Color,
    icon: String,
    isSelected: Boolean,
    onTap: () -> Unit,
    compact: Boolean
) {
    val fillRatio = (value / maxValue).coerceIn(0f, 1f)

    // Animate fill ratio
    val animatedFill by animateFloatAsState(
        targetValue = fillRatio,
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubic)
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Label row
        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = icon,
                        fontSize = 12.sp
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "${value.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 6.dp else 8.dp)
                .clip(RoundedCornerShape(if (compact) 3.dp else 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant) // Theme-aware background
                .clickable(onClick = onTap)
        ) {
            // Fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFill)
                    .background(color)
            )

            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
            }
        }
    }
}

/**
 * Eco credit tooltip
 */
@Composable
private fun EcoTooltip(
    icon: String,
    label: String,
    value: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface) // Theme-aware background
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 16.sp
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaColors.Success, // Theme-aware eco color
            fontSize = 12.sp
        )
    }
}

/**
 * Stat type enum for selection
 */
private enum class StatType {
    HEALTH,
    ENERGY,
    HAPPINESS
}

/**
 * Compact mini stats bar (for small displays)
 */
@Composable
fun MiniPetStatsBar(
    petState: PixelPetState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Compact vertical bars
        MiniStatBar(
            value = petState.health,
            color = MaColors.Success, // Green - theme-aware
            icon = "💧"
        )
        MiniStatBar(
            value = petState.energy,
            color = MaColors.Warning, // Yellow - theme-aware
            icon = "⚡"
        )
        MiniStatBar(
            value = petState.happiness,
            color = Color(0xFFE91E63), // Pink - semantic stat color
            icon = "💕"
        )
    }
}

/**
 * Single mini stat bar (vertical)
 */
@Composable
private fun MiniStatBar(
    value: Float,
    color: Color,
    icon: String
) {
    val fillRatio = (value / 100f).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Icon
        Text(
            text = icon,
            fontSize = 10.sp
        )

        // Vertical bar
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant) // Theme-aware background
        ) {
            // Fill from bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fillRatio)
                    .align(Alignment.BottomCenter)
                    .background(color)
            )
        }
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * // Full stats bar with eco tooltips
 * PetStatsBar(
 *     petState = petState,
 *     showEcoTooltips = true,
 *     compact = false
 * )
 *
 * // Compact mode (no labels, just bars)
 * PetStatsBar(
 *     petState = petState,
 *     compact = true
 * )
 *
 * // Mini vertical bars (for toolbar/header)
 * MiniPetStatsBar(
 *     petState = petState,
 *     modifier = Modifier.padding(8.dp)
 * )
 * ```
 */

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun PetStatsBarFullPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PetStatsBar(
                petState = PixelPetState(
                    health = 85f,
                    energy = 70f,
                    happiness = 85f,
                    lifetimeWaterMl = 15000,
                    lifetimeEnergyWh = 28000,
                    lifetimeCO2G = 8500,
                    evolutionStage = EvolutionStage.INTERMEDIATE
                ),
                showEcoTooltips = true,
                compact = false
            )
        }
    }
}

@Preview
@Composable
private fun PetStatsBarCompactPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Compact Mode:",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )

            PetStatsBar(
                petState = PixelPetState(
                    health = 70f,
                    energy = 60f,
                    happiness = 75f
                ),
                showEcoTooltips = false,
                compact = true
            )
        }
    }
}

@Preview
@Composable
private fun PetStatsBarLowHealthPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Low Health State:",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )

            PetStatsBar(
                petState = PixelPetState(
                    health = 25f,
                    energy = 15f,
                    happiness = 30f,
                    lifetimeWaterMl = 3000,
                    lifetimeEnergyWh = 5000,
                    lifetimeCO2G = 1500
                ),
                showEcoTooltips = true,
                compact = false
            )
        }
    }
}

@Preview
@Composable
private fun MiniPetStatsBarPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Mini Stats Bar:",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )

            MiniPetStatsBar(
                petState = PixelPetState(
                    health = 90f,
                    energy = 85f,
                    happiness = 95f
                )
            )
        }
    }
}
