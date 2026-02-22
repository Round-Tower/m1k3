package app.m1k3.ai.assistant.avatar.webview

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.AvatarState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * M1K3 - 3D Avatar WebView Integration
 *
 * Embeds the web-avatar THREE.js system in a native WebView,
 * providing shader effects and dynamic model loading without
 * requiring native Filament shader implementation.
 *
 * **Features**:
 * - WebView wrapper for src/web-avatar/index.html
 * - Bidirectional JS ↔ Kotlin state sync
 * - Shader effects (Pixelation, Glitch, Hologram, Bloom, etc.)
 * - GitHub model explorer integration
 * - Fallback to native 3D when WebView unavailable
 *
 * **Architecture**:
 * ```
 * AvatarViewModel (Kotlin)
 *   ↓ StateFlow<AvatarState>
 * AvatarWebViewContent (expect/actual)
 *   ↓ JavaScript bridge
 * index.html (THREE.js)
 *   ↓ WebGL renderer
 * GPU
 * ```
 *
 * **State Sync**:
 * - Kotlin → Web: `window.renderer.setState(avatarState)`
 * - Web → Kotlin: `AndroidBridge.onModelLoaded(modelName)`
 *
 * **Performance**:
 * - Target: 30+ FPS on mid-range devices
 * - WebGL hardware acceleration enabled
 * - Automatic quality scaling based on device
 *
 * @see [AvatarWebViewContent] Platform-specific WebView implementation
 * @see [AvatarState] Shared state model (emotion, activity, intensity)
 *
 * MurphySig: https://murphysig.dev
 * Confidence: 0.85 - WebView integration is well-tested pattern
 * Context: Phase 1 of KMP Avatar Integration Plan - Quick Win
 */

/**
 * Avatar state data for JavaScript bridge
 *
 * JSON-serializable representation of AvatarState for WebView communication.
 */
@Serializable
data class AvatarStateJS(
    val emotion: String,      // lowercase emotion name (happy, sad, thinking, etc.)
    val activity: String,     // uppercase activity name (IDLE, GENERATING, etc.)
    val intensity: Float,     // 0.0 to 1.0
    val message: String? = null
)

/**
 * Convert AvatarState to JavaScript-compatible JSON
 */
fun AvatarState.toJS(): String {
    val jsState = AvatarStateJS(
        emotion = emotion.name.lowercase(),
        activity = activity.name,
        intensity = intensity,
        message = message
    )
    return Json.encodeToString(jsState)
}

/**
 * WebView-based Avatar Screen
 *
 * Platform-agnostic expect/actual composable for rendering the
 * web avatar in a native WebView.
 *
 * **Implementation Notes**:
 * - Android: Uses `AndroidView` with `WebView`
 * - iOS: Uses `UIKitView` with `WKWebView`
 * - Desktop: Uses JCEF (Java Chromium Embedded Framework) if available
 *
 * @param state Current avatar state (reactive)
 * @param onModelLoaded Callback when user loads new model from GitHub
 * @param enableGitHubExplorer Show GitHub model search UI
 * @param enableShaders Enable post-processing shader effects
 * @param modifier Compose modifier
 */
@Composable
expect fun AvatarWebViewContent(
    state: AvatarState,
    onModelLoaded: (String) -> Unit = {},
    enableGitHubExplorer: Boolean = true,
    enableShaders: Boolean = true,
    modifier: Modifier = Modifier
)

/**
 * Settings Screen Integration - Web Avatar Toggle
 *
 * Example usage in SettingsScreen.kt:
 * ```kotlin
 * var useWebAvatar by remember { mutableStateOf(false) }
 *
 * SettingsSection(title = "Avatar Rendering") {
 *     SwitchRow(
 *         label = "Use Web Avatar (Beta)",
 *         description = "Enable shader effects and GitHub model loading",
 *         checked = useWebAvatar,
 *         onCheckedChange = { useWebAvatar = it }
 *     )
 * }
 * ```
 */

/**
 * Avatar Screen Integration - Conditional Rendering
 *
 * Example usage in AvatarScreen.kt:
 * ```kotlin
 * val useWebAvatar by settingsViewModel.useWebAvatar.collectAsState()
 * val avatarState by avatarViewModel.avatarState.collectAsState()
 *
 * if (useWebAvatar) {
 *     AvatarWebViewContent(
 *         state = avatarState,
 *         onModelLoaded = { modelName ->
 *             // Log or save custom model preference
 *             Log.d("Avatar", "User loaded model: $modelName")
 *         }
 *     )
 * } else {
 *     AvatarViewContent3D(state = avatarState)  // Native Filament rendering
 * }
 * ```
 */

/**
 * JavaScript Bridge API Reference
 *
 * **Kotlin → Web (JavaScript execution)**:
 * ```javascript
 * // Update avatar state
 * window.renderer.setState({
 *   emotion: 'happy',
 *   activity: 'GENERATING',
 *   intensity: 0.8
 * });
 *
 * // Load model by name
 * window.renderer.loadModel('Colobus');
 *
 * // Apply shader effect
 * window.renderer.setShader('pixelation', 0.7);
 * ```
 *
 * **Web → Kotlin (JavaScript interface)**:
 * ```kotlin
 * @JavascriptInterface
 * fun onModelLoaded(modelName: String) {
 *     // Called when user loads model from GitHub explorer
 * }
 *
 * @JavascriptInterface
 * fun onShaderChanged(shaderName: String) {
 *     // Called when user changes shader effect
 * }
 *
 * @JavascriptInterface
 * fun getAvatarState(): String {
 *     // Returns current state as JSON
 * }
 * ```
 */
