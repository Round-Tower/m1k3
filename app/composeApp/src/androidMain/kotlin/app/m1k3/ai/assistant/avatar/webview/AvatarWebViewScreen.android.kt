package app.m1k3.ai.assistant.avatar.webview

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.m1k3.ai.assistant.avatar.AvatarState

/**
 * M1K3 - Android WebView Avatar Implementation
 *
 * Native Android WebView wrapper for THREE.js avatar rendering.
 *
 * **Features**:
 * - Hardware-accelerated WebGL rendering
 * - JavaScript bridge for state synchronization
 * - Auto-injects state changes via evaluateJavascript
 * - Loads web assets from android_asset directory
 *
 * **Performance**:
 * - Hardware acceleration enabled
 * - Mixed content allowed (for GitHub model loading)
 * - DOM storage enabled (for caching)
 * - JavaScript enabled (required for THREE.js)
 *
 * **Asset Loading**:
 * ```
 * composeApp/src/androidMain/assets/web-avatar/
 * ├── index.html
 * ├── index.js  (built from src/)
 * ├── index.css (built from src/styles/)
 * └── models/   (pre-bundled GLB files)
 * ```
 *
 * **State Sync Flow**:
 * 1. Kotlin: `avatarState` updates
 * 2. LaunchedEffect detects change
 * 3. evaluateJavascript(`window.renderer.setState(...)`)
 * 4. THREE.js renderer applies animation/shader
 *
 * MurphySig: https://murphysig.dev
 * Confidence: 0.9 - Android WebView is mature and well-documented
 * Context: Android implementation for Phase 1 WebView integration
 */

/**
 * Android WebView-based Avatar Content
 *
 * @param state Current avatar state (triggers re-sync on change)
 * @param onModelLoaded Callback when user loads custom model
 * @param enableGitHubExplorer Enable GitHub model search
 * @param enableShaders Enable post-processing effects
 * @param modifier Compose modifier
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun AvatarWebViewContent(
    state: AvatarState,
    onModelLoaded: (String) -> Unit,
    enableGitHubExplorer: Boolean,
    enableShaders: Boolean,
    modifier: Modifier
) {
    // Track WebView instance across recompositions
    var webView: WebView? by remember { mutableStateOf(null) }

    // AndroidView wrapper for WebView
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // Enable WebView debugging (IMPORTANT for troubleshooting)
                WebView.setWebContentsDebuggingEnabled(true)

                // Enable WebGL and JavaScript
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    databaseEnabled = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true

                    // Performance optimizations
                    @Suppress("DEPRECATION")
                    setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)

                    // Mixed content (for GitHub model loading)
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                // Add console message handler for debugging
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d(
                            "WebViewConsole",
                            "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                        )
                        return true
                    }
                }

                // Add JavaScript interface
                addJavascriptInterface(
                    AvatarBridge(
                        getCurrentState = { state },
                        onModelLoaded = onModelLoaded
                    ),
                    "AndroidBridge"
                )

                // WebViewClient for page load handling
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Initialize with current state
                        syncStateToWeb(view, state)

                        // Configure features
                        view?.evaluateJavascript(
                            """
                            if (window.renderer) {
                                window.renderer.config = {
                                    enableGitHub: $enableGitHubExplorer,
                                    enableShaders: $enableShaders
                                };
                            }
                            """.trimIndent(),
                            null
                        )
                    }
                }

                // Load WebView avatar (IIFE bundle for file:// compatibility)
                loadUrl("file:///android_asset/web-avatar/index.html")

                webView = this
            }
        },
        modifier = modifier,
        update = { view ->
            // Update reference
            webView = view
        }
    )

    // Sync state changes to WebView
    LaunchedEffect(state) {
        webView?.let { view ->
            syncStateToWeb(view, state)
        }
    }
}

/**
 * Sync Kotlin state to JavaScript
 *
 * Injects avatar state into the THREE.js renderer via evaluateJavascript.
 */
private fun syncStateToWeb(webView: WebView?, state: AvatarState) {
    webView?.evaluateJavascript(
        """
        if (window.renderer && window.renderer.setState) {
            window.renderer.setState({
                emotion: '${state.emotion.name.lowercase()}',
                activity: '${state.activity.name}',
                intensity: ${state.intensity},
                message: ${state.message?.let { "'$it'" } ?: "null"}
            });
        }
        """.trimIndent(),
        null
    )
}

/**
 * JavaScript Bridge for Web → Kotlin communication
 *
 * Exposes methods to JavaScript via `window.AndroidBridge`.
 *
 * **Usage from JavaScript**:
 * ```javascript
 * // Get current state
 * const stateJson = AndroidBridge.getAvatarState();
 * const state = JSON.parse(stateJson);
 *
 * // Notify model loaded
 * AndroidBridge.onModelLoaded('Colobus');
 *
 * // Notify shader changed
 * AndroidBridge.onShaderChanged('pixelation');
 * ```
 */
private class AvatarBridge(
    private val getCurrentState: () -> AvatarState,
    private val onModelLoaded: (String) -> Unit
) {
    /**
     * Get current avatar state as JSON
     *
     * @return JSON string: `{"emotion":"happy","activity":"IDLE",...}`
     */
    @JavascriptInterface
    fun getAvatarState(): String {
        return getCurrentState().toJS()
    }

    /**
     * Called when user loads a custom model from GitHub
     *
     * @param modelName Name of the loaded model
     */
    @JavascriptInterface
    fun onModelLoaded(modelName: String) {
        onModelLoaded.invoke(modelName)
    }

    /**
     * Called when user changes shader effect
     *
     * @param shaderName Name of the shader (pixelation, glitch, etc.)
     */
    @JavascriptInterface
    fun onShaderChanged(shaderName: String) {
        // Future: Save shader preference
        android.util.Log.d("AvatarWebView", "Shader changed: $shaderName")
    }
}

/**
 * Example: Full-screen WebView avatar in a Screen
 *
 * ```kotlin
 * @Composable
 * fun AvatarDemoScreen(
 *     avatarViewModel: AvatarViewModel,
 *     onBackClick: () -> Unit
 * ) {
 *     val state by avatarViewModel.avatarState.collectAsState()
 *
 *     Scaffold(
 *         topBar = {
 *             TopAppBar(
 *                 title = { Text("Web Avatar") },
 *                 navigationIcon = {
 *                     IconButton(onClick = onBackClick) {
 *                         Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
 *                     }
 *                 }
 *             )
 *         }
 *     ) { padding ->
 *         AvatarWebViewContent(
 *             state = state,
 *             onModelLoaded = { modelName ->
 *                 Toast.makeText(context, "Loaded: $modelName", Toast.LENGTH_SHORT).show()
 *             },
 *             modifier = Modifier.fillMaxSize().padding(padding)
 *         )
 *     }
 * }
 * ```
 */
