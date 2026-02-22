package app.m1k3.ai.assistant.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.domain.status.ChatStatus
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Status Card - Welcome message with system status.
 *
 * Displays a styled card with:
 * - Time-based greeting (Good morning/afternoon/evening)
 * - Engine status indicator
 * - Memory and knowledge counts
 * - Context capacity
 * - Last session eco stats (if available)
 *
 * Design: Glassmorphic card with orange accent border,
 * distinct from regular chat bubbles.
 */
@Composable
fun MaStatusCard(
    greeting: String,
    engineReady: Boolean,
    memoryCount: Long,
    knowledgeCount: Long,
    maxContextTokens: Int,
    deviceTierName: String,
    lastSessionWaterMl: Long? = null,
    lastSessionEnergyWh: Long? = null,
    lastSessionCo2G: Long? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(MaRadius.lg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaSpacing.md, vertical = MaSpacing.base)
            .clip(shape)
            .background(MaColors.bgElevated())
            .border(
                width = 1.dp,
                color = MaColors.OrangeDim,
                shape = shape
            )
            .padding(MaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
    ) {
        // Greeting
        Text(
            text = greeting,
            style = MaTypography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaColors.textPrimary()
        )

        Spacer(modifier = Modifier.height(MaSpacing.xs))

        // Status row: Engine | Memories | Knowledge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Engine status
            StatusChip(
                icon = {
                    Icon(
                        imageVector = if (engineReady) Icons.Filled.CheckCircle else Icons.Filled.Memory,
                        contentDescription = null,
                        tint = if (engineReady) MaColors.Success else MaColors.Warning,
                        modifier = Modifier.size(14.dp)
                    )
                },
                label = if (engineReady) "Ready" else "Loading"
            )

            // Memories count
            StatusChip(
                label = "Memories: ${formatCount(memoryCount)}"
            )

            // Knowledge count
            StatusChip(
                label = "Knowledge: ${formatCount(knowledgeCount)}"
            )
        }

        // Context capacity
        Text(
            text = "Context: ${formatCount(maxContextTokens.toLong())} tokens ($deviceTierName)",
            style = MaTypography.labelSmall,
            color = MaColors.textSecondary()
        )

        // Last session eco stats (if available)
        if (lastSessionWaterMl != null || lastSessionEnergyWh != null || lastSessionCo2G != null) {
            Spacer(modifier = Modifier.height(MaSpacing.xs))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MaRadius.sm))
                    .background(MaColors.SuccessBg)
                    .padding(horizontal = MaSpacing.sm, vertical = MaSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.md)
            ) {
                Text(
                    text = "Last session saved:",
                    style = MaTypography.labelSmall,
                    color = MaColors.Success,
                    fontWeight = FontWeight.Medium
                )

                lastSessionWaterMl?.let {
                    Text(
                        text = "${formatWater(it)} water",
                        style = MaTypography.labelSmall,
                        color = MaColors.textSecondary()
                    )
                }

                lastSessionEnergyWh?.let {
                    Text(
                        text = "${it} Wh",
                        style = MaTypography.labelSmall,
                        color = MaColors.textSecondary()
                    )
                }

                lastSessionCo2G?.let {
                    Text(
                        text = "${it}g CO2",
                        style = MaTypography.labelSmall,
                        color = MaColors.textSecondary()
                    )
                }
            }
        }
    }
}

/**
 * Convenience overload that takes a ChatStatus directly.
 */
@Composable
fun MaStatusCard(
    status: ChatStatus,
    modifier: Modifier = Modifier
) {
    MaStatusCard(
        greeting = status.greeting,
        engineReady = status.engineReady,
        memoryCount = status.memoryCount,
        knowledgeCount = status.knowledgeCount,
        maxContextTokens = status.maxContextTokens,
        deviceTierName = status.deviceTierName,
        lastSessionWaterMl = status.lastSessionWaterMl,
        lastSessionEnergyWh = status.lastSessionEnergyWh,
        lastSessionCo2G = status.lastSessionCo2G,
        modifier = modifier
    )
}

@Composable
private fun StatusChip(
    label: String,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.invoke()
        Text(
            text = label,
            style = MaTypography.labelSmall,
            color = MaColors.textSecondary()
        )
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}

private fun formatWater(ml: Long): String {
    return if (ml >= 1000) {
        "${ml / 1000.0}L"
    } else {
        "${ml}ml"
    }
}

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun MaStatusCardPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.bgPrimary())
                .padding(MaSpacing.base)
        ) {
            MaStatusCard(
                greeting = "Good afternoon!",
                engineReady = true,
                memoryCount = 127,
                knowledgeCount = 1842,
                maxContextTokens = 4096,
                deviceTierName = "Flagship"
            )
        }
    }
}

@Preview
@Composable
private fun MaStatusCardWithEcoPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.bgPrimary())
                .padding(MaSpacing.base)
        ) {
            MaStatusCard(
                greeting = "Good morning!",
                engineReady = true,
                memoryCount = 42,
                knowledgeCount = 500,
                maxContextTokens = 2048,
                deviceTierName = "Mid-range",
                lastSessionWaterMl = 2300,
                lastSessionEnergyWh = 45,
                lastSessionCo2G = 12
            )
        }
    }
}

@Preview
@Composable
private fun MaStatusCardLoadingPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.bgPrimary())
                .padding(MaSpacing.base)
        ) {
            MaStatusCard(
                greeting = "Good evening!",
                engineReady = false,
                memoryCount = 0,
                knowledgeCount = 0,
                maxContextTokens = 1024,
                deviceTierName = "Entry-level"
            )
        }
    }
}
