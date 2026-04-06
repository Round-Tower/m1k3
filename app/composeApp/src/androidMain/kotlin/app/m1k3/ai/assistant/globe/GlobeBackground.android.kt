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
 * Android implementation of GlobeBackground.
 *
 * Rubin mode: pure Compose Canvas, no extra deps.
 * MapLibre mode: WebView with MapLibre GL JS.
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

    val targetAlpha = if (dimmed) 0.08f else 0.35f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(1200),
        label = "globe_alpha"
    )

    Box(modifier = modifier.alpha(alpha)) {
        when (mode) {
            GlobeMode.RUBIN -> RubinGlobe(
                modifier = Modifier.fillMaxSize(),
                focusLocation = focusLocation,
                alpha = 1f,           // alpha already applied by Box
                performanceTier = performanceTier
            )
            GlobeMode.MAPLIBRE -> MapLibreGlobeView(
                modifier = Modifier.fillMaxSize(),
                focusLocation = focusLocation,
                alpha = 1f
            )
            GlobeMode.NONE -> Unit
        }
    }
}
