package app.m1k3.ai.assistant.design.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.m1k3.ai.assistant.design.effects.glassmorphicCard
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing

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
