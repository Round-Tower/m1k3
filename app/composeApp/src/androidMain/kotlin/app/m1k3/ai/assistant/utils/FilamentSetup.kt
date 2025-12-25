package app.m1k3.ai.assistant.utils

import com.google.android.filament.utils.Utils as FilamentUtils

/**
 * Filament 3D engine initialization utilities.
 *
 * Filament is Google's physically-based rendering engine used for:
 * - 3D avatar rendering
 * - Real-time lighting and materials
 * - High-performance graphics on mobile
 *
 * **IMPORTANT:** Must be called before any Filament Engine.create() calls.
 * This initializes native .so libraries required for 3D rendering.
 *
 * Threading: Must be called on main thread (Android UI thread)
 * Performance: One-time initialization, <10ms on most devices
 *
 * Example usage:
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         // Initialize Filament FIRST, before any 3D components
 *         FilamentSetup.initialize()
 *
 *         // Now safe to create 3D views, engines, etc.
 *         setContent { Avatar3DView(...) }
 *     }
 * }
 * ```
 */
object FilamentSetup {

    /**
     * Initialize Filament native libraries.
     *
     * Safe to call multiple times (idempotent) - subsequent calls are no-ops.
     *
     * @throws RuntimeException if initialization fails (rare, usually missing libs)
     */
    fun initialize() {
        FilamentUtils.init()
    }
}
