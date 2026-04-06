package app.m1k3.ai.assistant.globe

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Android implementation of GlobeBackground.
 *
 * Alpha is passed directly into each renderer's draw calls rather than
 * wrapping in a Box with Modifier.alpha(). This avoids an extra RenderNode
 * layer and keeps the composable tree flat.
 *
 * For Rubin: alpha flows into Canvas drawCircle calls directly.
 * For MapLibre: alpha controls WebView opacity via JS bridge.
 */
@Composable
actual fun GlobeBackground(
    mode: GlobeMode,
    focusLocation: GlobeLocation?,
    dimmed: Boolean,
    performanceTier: Int,
    modifier: Modifier
) {
    if (mode == GlobeMode.NONE) return

    val alpha by animateFloatAsState(
        targetValue = if (dimmed) 0.08f else 0.35f,
        animationSpec = tween(1200),
        label = "globe_alpha"
    )

    when (mode) {
        GlobeMode.RUBIN -> RubinGlobe(
            modifier = modifier,
            focusLocation = focusLocation,
            alpha = alpha,
            performanceTier = performanceTier
        )
        GlobeMode.MAPLIBRE -> MapLibreGlobeView(
            modifier = modifier,
            focusLocation = focusLocation,
            alpha = alpha
        )
        GlobeMode.NONE -> Unit
    }
}
