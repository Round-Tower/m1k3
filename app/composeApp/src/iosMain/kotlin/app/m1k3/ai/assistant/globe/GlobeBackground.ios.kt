package app.m1k3.ai.assistant.globe

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * iOS: Rubin Canvas globe only (MapLibre WebView is Android-specific).
 * MapLibre mode falls back gracefully to Rubin.
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

    // iOS uses Rubin canvas regardless of requested mode
    RubinGlobe(
        modifier = modifier,
        focusLocation = focusLocation,
        alpha = if (dimmed) 0.08f else 0.35f,
        performanceTier = performanceTier
    )
}
