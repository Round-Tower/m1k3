package app.m1k3.ai.assistant.globe

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * Globe mode — which renderer to use.
 */
enum class GlobeMode {
    /** Rubin-style dot-globe. Pure Compose Canvas. KMP-portable. */
    RUBIN,
    /** MapLibre GL in WebView. Richer cartography. Android-only. */
    MAPLIBRE,
    /** No globe — transparent */
    NONE
}

/**
 * GlobeBackground - Unified globe composable.
 *
 * Wraps both globe renderers behind a common API. The caller
 * chooses the mode; the composable handles the rest.
 *
 * Usage in ChatScreen:
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     GlobeBackground(
 *         mode = GlobeMode.RUBIN,
 *         dimmed = uiState.generationState.isGenerating
 *     )
 *     // ... chat UI on top
 * }
 * ```
 *
 * @param mode Which globe renderer to use
 * @param focusLocation Optional location to orient toward. Null = auto-cycle.
 * @param dimmed Dim the globe during AI generation (0.35 → 0.08)
 * @param performanceTier Rubin dot count (TIER_LOW/MEDIUM/HIGH)
 */
@Composable
expect fun GlobeBackground(
    mode: GlobeMode = GlobeMode.RUBIN,
    focusLocation: GlobeLocation? = null,
    dimmed: Boolean = false,
    performanceTier: Int = TIER_MEDIUM,
    modifier: Modifier = Modifier.fillMaxSize()
)
