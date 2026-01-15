package app.m1k3.ai.assistant.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.design.effects.glassmorphicCard
import app.m1k3.ai.assistant.design.preview.PreviewFixtures
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * M1K3 AI Glassmorphic Card Component
 *
 * Elevated card with liquid glass aesthetic.
 * Semi-transparent background with subtle borders.
 */

/**
 * Standard glassmorphic card
 *
 * @param modifier Modifier for the card
 * @param onClick Optional click handler (makes card interactive)
 * @param shape Corner shape (default: 12dp rounded)
 * @param content Card content (Column scope for vertical layout)
 */
@Composable
fun MaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(MaRadius.none),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.glassmorphicCard(shape),
        onClick = onClick ?: {},
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent  // Glassmorphic modifier handles background
        ),
        content = content
    )
}

/**
 * Usage Example:
 * ```kotlin
 * MaCard(
 *     modifier = Modifier.fillMaxWidth(),
 *     onClick = { /* handle click */ }
 * ) {
 *     Text("Card Content", modifier = Modifier.padding(MaSpacing.base))
 * }
 * ```
 */

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun MaCardBasicPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.bgPrimary())
                .padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
        ) {
            MaCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MaSpacing.base),
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
                ) {
                    Text(
                        "Basic Card",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "This is a simple glassmorphic card with content.",
                        style = MaTypography.bodyMedium,
                        color = MaColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MaCardClickablePreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.bgPrimary())
                .padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
        ) {
            MaCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = PreviewFixtures.noOpOnClick
            ) {
                Column(
                    modifier = Modifier.padding(MaSpacing.base),
                    verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
                ) {
                    Text(
                        "Clickable Card",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Tap to interact with this card.",
                        style = MaTypography.bodyMedium,
                        color = MaColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MaCardWithIconPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.bgPrimary())
                .padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
        ) {
            MaCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(MaSpacing.base),
                    horizontalArrangement = Arrangement.spacedBy(MaSpacing.base)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
                    ) {
                        Text(
                            "Card with Layout",
                            style = MaTypography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Multi-element card content.",
                            style = MaTypography.bodySmall,
                            color = MaColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MaCardMultiplePreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.bgPrimary())
                .padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
        ) {
            Text(
                "Card Collection:",
                style = MaTypography.labelSmall,
                color = MaColors.TextSecondary,
                modifier = Modifier.padding(bottom = MaSpacing.sm)
            )

            MaCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Card 1",
                    modifier = Modifier.padding(MaSpacing.base),
                    style = MaTypography.bodyMedium
                )
            }

            MaCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Card 2",
                    modifier = Modifier.padding(MaSpacing.base),
                    style = MaTypography.bodyMedium
                )
            }

            MaCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Card 3",
                    modifier = Modifier.padding(MaSpacing.base),
                    style = MaTypography.bodyMedium
                )
            }
        }
    }
}
