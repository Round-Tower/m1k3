package app.m1k3.ai.assistant.globe

/**
 * Signed: Kev + claude-sonnet-4-6, 2026-04-06
 * Format: MurphySig v0.1 (https://murphysig.dev/spec)
 *
 * Context: MapLibre GL JS globe via WebView — richer cartography alternative to
 * RubinGlobe. Reuses the WebView infrastructure already in-place for the THREE.js
 * avatar. Chosen over Mapbox (API token + binary size) and CesiumJS (battery killer).
 * Currently uses demotiles.maplibre.org for vector tiles — requires internet.
 * Upgrade path: bundle Protomaps PMTiles for zoom 0-3 (~3MB) for offline-first.
 *
 * Confidence: 0.7 — WebGL in WebView is proven, but GPU contention with Filament
 * avatar is untested when both are on screen simultaneously.
 * Open: Profile GPU usage on Pixel 9a when MapLibre + Avatar3DView coexist.
 * Consider lazy-loading MapLibre only when avatar is hidden.
 */

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * MapLibre Globe background view.
 *
 * Renders the Earth as a beautiful 3D globe via MapLibre GL in a WebView.
 * Dark Ma-styled map with orange coastline accents. Slowly drifts between
 * 20 curated locations, or flies to a real/specified location.
 *
 * GPU note: Runs in a separate WebGL context from Filament (avatar engine).
 * If on the same screen as Avatar3DView, monitor GPU usage on low-end devices.
 *
 * @param modifier Compose modifier
 * @param focusLocation Optional location to fly to. Null = auto-cycle curated spots
 * @param alpha Opacity 0.0–1.0. Dim to 0.15 during AI generation.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapLibreGlobeView(
    modifier: Modifier = Modifier,
    focusLocation: GlobeLocation? = null,
    alpha: Float = 1.0f
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var isLoaded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                loadUrl("about:blank")
                destroy()
            }
            webView = null
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                WebView.setWebContentsDebuggingEnabled(false)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    // WebGL needs hardware acceleration
                    @Suppress("DEPRECATION")
                    setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d("MapLibreGlobe", msg.message())
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoaded = true
                    }
                }

                loadUrl("file:///android_asset/globe/maplibre_globe.html")
                webView = this
            }
        },
        modifier = modifier,
        update = { view -> webView = view }
    )

    // Alpha changes — dim during generation
    LaunchedEffect(alpha, isLoaded) {
        if (isLoaded) {
            webView?.evaluateJavascript(
                "window.GlobeBridge && window.GlobeBridge.setAlpha($alpha);",
                null
            )
        }
    }

    // Fly to focus location when it changes
    LaunchedEffect(focusLocation, isLoaded) {
        if (isLoaded && focusLocation != null) {
            webView?.evaluateJavascript(
                "window.GlobeBridge && window.GlobeBridge.flyToLocation(${focusLocation.lat}, ${focusLocation.lon});",
                null
            )
        }
    }
}
