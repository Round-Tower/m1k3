package app.m1k3.ai.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.AvatarGalleryState
import app.m1k3.ai.assistant.avatar.ModelConfig
import app.m1k3.ai.assistant.avatar.ModelRegistry
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.domain.platform.PreferenceKeys
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.design.theme.MaTheme

/**
 * Avatar Gallery Screen - Full-screen avatar selection.
 *
 * Beautiful grid showing all available 3D avatar models.
 * Each card shows the model name, category, and a visual preview area.
 * Tap to select with haptic feedback.
 *
 * Philosophy: Your avatar is your identity. Make the choice feel special.
 */
@Composable
fun AvatarGalleryScreen(
    currentAvatarId: String = ModelRegistry.DEFAULT_MODEL_ID,
    onAvatarSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    var selectedId by remember { mutableStateOf(currentAvatarId) }
    val models = remember { ModelRegistry.allModels }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val categories = remember(models) {
        models.map { it.category }.distinct().sorted()
    }
    val filteredModels = remember(models, selectedCategory) {
        if (selectedCategory == null) models
        else models.filter { it.category == selectedCategory }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaColors.bgPrimary())
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaSpacing.md, vertical = MaSpacing.base),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Choose Your Avatar",
                style = MaTypography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.textPrimary()
            )
            Text(
                text = "${filteredModels.size} companions",
                style = MaTypography.bodyMedium,
                color = MaColors.textMuted()
            )
        }

        // Category filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MaSpacing.sm)
        ) {
            // "All" chip
            item {
                CategoryChip(
                    label = "All",
                    isSelected = selectedCategory == null,
                    onClick = { selectedCategory = null }
                )
            }
            items(categories) { category ->
                CategoryChip(
                    label = category.replaceFirstChar { it.uppercase() },
                    isSelected = selectedCategory == category,
                    onClick = { selectedCategory = category }
                )
            }
        }

        // Grid of avatar cards
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(MaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md),
            modifier = Modifier.fillMaxSize()
        ) {
            lazyGridItems(filteredModels, key = { it.id }) { model ->
                AvatarCard(
                    model = model,
                    isSelected = model.id == selectedId,
                    onClick = {
                        haptics.success()
                        selectedId = model.id
                        onAvatarSelected(model.id)
                    }
                )
            }
        }
    }
}

/**
 * Pill-shaped category filter chip.
 *
 * Selected chips use MaColors.Orange fill; unselected use a subtle border.
 */
@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaColors.Orange else MaColors.bgElevated(),
        animationSpec = tween(200),
        label = "chipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaColors.Orange else MaColors.BorderSubtle,
        animationSpec = tween(200),
        label = "chipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaColors.bgPrimary() else MaColors.textPrimary(),
        animationSpec = tween(200),
        label = "chipText"
    )
    val chipShape = RoundedCornerShape(50)

    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(bgColor, chipShape)
            .border(width = 1.dp, color = borderColor, shape = chipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = MaSpacing.md, vertical = MaSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaTypography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * Individual avatar card in the gallery.
 *
 * Shows a large preview area with the model info.
 * Selected card glows orange with a bouncy scale animation.
 */
@Composable
private fun AvatarCard(
    model: ModelConfig,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaColors.Orange else MaColors.BorderSubtle,
        animationSpec = tween(250),
        label = "borderColor"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaColors.Orange.copy(alpha = 0.08f)
        } else {
            MaColors.bgElevated()
        },
        animationSpec = tween(250),
        label = "bgColor"
    )

    val cardShape = RoundedCornerShape(MaRadius.lg)

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(cardShape)
            .background(bgColor, cardShape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = cardShape
            )
            .clickable(onClick = onClick)
            .padding(MaSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preview area — placeholder for 3D render
        // In production this would host Avatar3DView
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(MaRadius.md))
                .background(
                    if (isSelected) MaColors.Orange.copy(alpha = 0.12f)
                    else MaColors.bgSecondary()
                ),
            contentAlignment = Alignment.Center
        ) {
            // Category emoji as visual hint
            Text(
                text = categoryEmoji(model.category),
                style = MaTypography.displayLarge,
                textAlign = TextAlign.Center
            )
        }

        // Model name
        Text(
            text = model.name,
            style = MaTypography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaColors.Orange else MaColors.textPrimary(),
            modifier = Modifier.padding(top = MaSpacing.sm),
            textAlign = TextAlign.Center
        )

        // Category tag
        Text(
            text = model.category.replaceFirstChar { it.uppercase() },
            style = MaTypography.labelSmall,
            color = MaColors.textMuted(),
            textAlign = TextAlign.Center
        )

        // Selected indicator
        if (isSelected) {
            Text(
                text = "Selected",
                style = MaTypography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaColors.Orange,
                modifier = Modifier.padding(top = MaSpacing.xs)
            )
        }
    }
}

/**
 * Get an emoji for the model category as a visual placeholder.
 */
private fun categoryEmoji(category: String): String = when (category.lowercase()) {
    "mammal" -> "🐾"
    "bird" -> "🐦"
    "reptile" -> "🦎"
    "fish" -> "🐟"
    "cephalopod" -> "🦑"
    "dinosaur" -> "🦕"
    "static" -> "🎭"
    else -> "🌟"
}

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun AvatarGalleryPreview() {
    MaTheme {
        AvatarGalleryScreen(
            currentAvatarId = "colobus"
        )
    }
}
