package app.m1k3.ai.assistant.avatar.webview

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import app.m1k3.ai.assistant.avatar.AvatarState
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.WebKit.*
import platform.darwin.NSObject

/**
 * M1K3 - iOS WKWebView Avatar Implementation
 *
 * Native iOS WKWebView wrapper for THREE.js avatar rendering.
 *
 * **Features**:
 * - Hardware-accelerated WebGL rendering via Metal
 * - WKScriptMessageHandler for Web → Kotlin communication
 * - evaluateJavaScript for Kotlin → Web state sync
 * - Loads web assets from app bundle resources
 *
 * **Performance**:
 * - Metal-backed WebGL on iOS (better than Android)
 * - Inline media playback allowed
 * - JavaScript enabled (required for THREE.js)
 *
 * **Asset Loading**:
 * ```
 * iosApp/iosApp/Resources/web-avatar/
 * ├── index.html
 * ├── index.js
 * ├── index.css
 * └── models/
 * ```
 *
 * **State Sync Flow**:
 * 1. Kotlin: `avatarState` updates
 * 2. LaunchedEffect detects change
 * 3. evaluateJavaScript(`window.renderer.setState(...)`)
 * 4. THREE.js renderer applies animation/shader
 *
 * MurphySig: https://murphysig.dev
 * Confidence: 0.85 - WKWebView is mature but KMP iOS interop has quirks
 * Context: iOS implementation for Phase 1 WebView integration
 */

/**
 * iOS WKWebView-based Avatar Content
 *
 * @param state Current avatar state (triggers re-sync on change)
 * @param onModelLoaded Callback when user loads custom model
 * @param enableGitHubExplorer Enable GitHub model search
 * @param enableShaders Enable post-processing effects
 * @param modifier Compose modifier
 */
@Composable
actual fun AvatarWebViewContent(
    state: AvatarState,
    onModelLoaded: (String) -> Unit,
    enableGitHubExplorer: Boolean,
    enableShaders: Boolean,
    modifier: Modifier
) {
    // Track WebView instance
    var webView: WKWebView? by remember { mutableStateOf(null) }

    UIKitView(
        factory = {
            // Configure WKWebView
            val config = WKWebViewConfiguration().apply {
                preferences.apply {
                    javaScriptEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                }
                allowsInlineMediaPlayback = true
            }

            // Create message handler
            val messageHandler = AvatarMessageHandler(
                getCurrentState = { state },
                onModelLoaded = onModelLoaded
            )

            // Add script message handlers
            config.userContentController.apply {
                addScriptMessageHandler(messageHandler, name = "avatarBridge")
            }

            // Create WebView
            val wkWebView = WKWebView(frame = platform.CoreGraphics.CGRectZero, configuration = config).apply {
                navigationDelegate = AvatarNavigationDelegate(
                    onPageLoaded = { view ->
                        // Initialize with current state
                        syncStateToWeb(view, state)

                        // Configure features
                        view.evaluateJavaScript(
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
                )

                // Load local HTML from bundle
                val bundle = NSBundle.mainBundle
                val htmlURL = bundle.URLForResource("web-avatar/index", withExtension = "html")

                htmlURL?.let { url ->
                    val request = NSURLRequest.requestWithURL(url)
                    loadRequest(request)
                }
            }

            webView = wkWebView
            wkWebView
        },
        modifier = modifier,
        update = { view ->
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
 * Sync Kotlin state to JavaScript (iOS)
 */
private fun syncStateToWeb(webView: WKWebView, state: AvatarState) {
    webView.evaluateJavaScript(
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
 * WKScriptMessageHandler for Web → Kotlin communication
 *
 * **Usage from JavaScript**:
 * ```javascript
 * // Get current state
 * window.webkit.messageHandlers.avatarBridge.postMessage({
 *     type: 'getState'
 * });
 *
 * // Notify model loaded
 * window.webkit.messageHandlers.avatarBridge.postMessage({
 *     type: 'modelLoaded',
 *     modelName: 'Colobus'
 * });
 * ```
 */
private class AvatarMessageHandler(
    private val getCurrentState: () -> AvatarState,
    private val onModelLoaded: (String) -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol {

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        val message = didReceiveScriptMessage.body as? Map<*, *> ?: return
        val type = message["type"] as? String ?: return

        when (type) {
            "getState" -> {
                // Return state as JSON (future: use callback)
                val stateJson = getCurrentState().toJS()
                println("Avatar state: $stateJson")
            }

            "modelLoaded" -> {
                val modelName = message["modelName"] as? String ?: return
                onModelLoaded(modelName)
            }

            "shaderChanged" -> {
                val shaderName = message["shaderName"] as? String ?: return
                println("Shader changed: $shaderName")
            }
        }
    }
}

/**
 * WKNavigationDelegate for page load tracking
 */
private class AvatarNavigationDelegate(
    private val onPageLoaded: (WKWebView) -> Unit
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(
        webView: WKWebView,
        didFinishNavigation: WKNavigation?
    ) {
        onPageLoaded(webView)
    }
}

/**
 * iOS-specific notes:
 *
 * **Bundle Resources**:
 * - Add web-avatar folder to Xcode project
 * - Mark as "Create folder references" (blue folder icon)
 * - Ensure files are in "Copy Bundle Resources" build phase
 *
 * **WKWebView vs UIWebView**:
 * - WKWebView is required (UIWebView deprecated)
 * - WKWebView runs in separate process (better performance + security)
 * - Metal-backed WebGL rendering (faster than Android in many cases)
 *
 * **Message Passing**:
 * - iOS uses `window.webkit.messageHandlers.avatarBridge.postMessage()`
 * - Android uses `window.AndroidBridge.methodName()`
 * - Web code should detect platform and use appropriate API
 *
 * **Debug Console**:
 * ```swift
 * // In Xcode, enable Web Inspector:
 * Safari > Develop > [Device] > [App] > index.html
 * ```
 */
