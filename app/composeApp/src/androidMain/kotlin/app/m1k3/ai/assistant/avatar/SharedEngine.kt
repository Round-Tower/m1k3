package app.m1k3.ai.assistant.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.sceneview.rememberEngine
import com.google.android.filament.Engine

/**
 * 間 AI Shared Engine Provider
 *
 * CompositionLocal for sharing a single Filament engine across all 3D avatar views.
 * Prevents SIGSEGV crashes from concurrent engine destruction.
 *
 * **Usage:**
 * ```kotlin
 * // In MainActivity:
 * ProvideSharedEngine {
 *     MaTheme {
 *         // Your app content
 *     }
 * }
 *
 * // In Avatar3DView:
 * val engine = LocalSharedEngine.current
 * ```
 */

/**
 * CompositionLocal holding the shared Filament engine
 *
 * Must be provided at the app root using ProvideSharedEngine.
 * Throws error if accessed without being provided.
 */
val LocalSharedEngine = staticCompositionLocalOf<Engine?> {
    null
}

/**
 * Provider composable that creates and shares a single engine
 *
 * Creates the engine ONCE using rememberEngine() and provides it to all descendants.
 * Should be placed at the root of your composition tree (e.g., in MainActivity).
 *
 * @param content Child composables that will have access to the shared engine
 */
@Composable
fun ProvideSharedEngine(content: @Composable () -> Unit) {
    // Create engine ONCE for the entire app
    val sharedEngine = rememberEngine()

    println("🔧 [SharedEngine] Created single shared engine for entire app")

    // Provide to all descendants
    CompositionLocalProvider(LocalSharedEngine provides sharedEngine) {
        content()
    }
}
