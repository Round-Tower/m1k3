package app.m1k3.ai.assistant.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.m1k3.ai.assistant.utils.Logger
import com.google.android.filament.Engine
import java.util.concurrent.atomic.AtomicInteger

private val logger = Logger.withTag("SharedEngine")

/**
 * 間 AI Shared Engine Provider - Reference-Counted Edition
 *
 * Manual engine lifecycle manager with reference counting.
 * Prevents SIGSEGV crashes from premature engine destruction.
 *
 * **Problem:**
 * - rememberEngine() uses DisposableEffect to destroy engine on composition exit
 * - Multi-screen navigation causes "Engine destroyed × 2" and SIGSEGV
 * - SharedEngine pattern alone doesn't prevent disposal
 *
 * **Solution:**
 * - Manual engine creation (not rememberEngine())
 * - Reference counting for active views
 * - Explicit cleanup only when app is destroyed
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
 * val engine = LocalSharedEngine.current!!
 * AcquireEngine()  // Increment ref count
 * ```
 */

/**
 * Reference-counted Filament engine manager
 *
 * Thread-safe singleton that manages engine lifecycle across all 3D views.
 * Tracks active view count and prevents premature destruction.
 */
object FilamentEngineManager {
    @Volatile
    private var engine: Engine? = null
    private val refCount = AtomicInteger(0)
    private val lock = Any()

    /**
     * Acquire engine instance (creates on first call)
     *
     * Increments reference count. Safe to call from multiple views/screens.
     *
     * @return Shared Filament engine instance
     */
    fun acquire(): Engine {
        synchronized(lock) {
            if (engine == null) {
                engine = Engine.create()
                logger.d { "[FilamentEngine] Created new engine instance" }
            }
            val count = refCount.incrementAndGet()
            logger.d { "[FilamentEngine] Acquired (refCount=$count)" }
            return engine!!
        }
    }

    /**
     * Release engine reference (decrements count)
     *
     * Should be called in DisposableEffect cleanup when view leaves composition.
     * Engine is NOT destroyed until app shutdown (prevents multi-screen crashes).
     */
    fun release() {
        synchronized(lock) {
            val count = refCount.decrementAndGet()
            logger.d { "[FilamentEngine] Released (refCount=$count)" }

            // NOTE: We do NOT destroy the engine here to prevent multi-screen crashes
            // Engine will be destroyed explicitly in MainActivity.onDestroy()
            // This is intentional to work around SceneView disposal issues
        }
    }

    /**
     * Force destroy engine (call from MainActivity.onDestroy only)
     *
     * Explicitly destroys the engine when app is shutting down.
     * Do NOT call during navigation - will cause SIGSEGV!
     */
    fun forceDestroy() {
        synchronized(lock) {
            engine?.let {
                logger.i { "[FilamentEngine] Force destroying engine (refCount=${refCount.get()})" }
                it.destroy()
                engine = null
                refCount.set(0)
            }
        }
    }

    /**
     * Get current reference count (for debugging)
     */
    fun getRefCount(): Int = refCount.get()

    /**
     * Check if engine is initialized
     */
    fun isInitialized(): Boolean = engine != null
}

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
 * Provider composable that creates and shares a reference-counted engine
 *
 * Creates the engine ONCE using manual lifecycle (not rememberEngine()).
 * Should be placed at the root of your composition tree (e.g., in MainActivity).
 *
 * Engine is explicitly destroyed in MainActivity.onDestroy() - NOT here!
 *
 * @param content Child composables that will have access to the shared engine
 */
@Composable
fun ProvideSharedEngine(content: @Composable () -> Unit) {
    // Acquire engine ONCE for the entire app (manual lifecycle)
    val sharedEngine = remember {
        FilamentEngineManager.acquire()
    }

    // DO NOT use DisposableEffect here - engine must survive screen navigation
    // Engine will be destroyed explicitly in MainActivity.onDestroy()

    logger.d { "[SharedEngine] Providing shared engine to composition tree" }

    // Provide to all descendants
    CompositionLocalProvider(LocalSharedEngine provides sharedEngine) {
        content()
    }
}

/**
 * Acquire engine reference for a 3D view
 *
 * Call this in your Avatar3DView composable to increment the reference count.
 * Automatically releases when the composable leaves the composition.
 *
 * **Usage:**
 * ```kotlin
 * @Composable
 * fun Avatar3DView() {
 *     val engine = LocalSharedEngine.current!!
 *     AcquireEngine()  // Increment ref count, auto-release on dispose
 *
 *     Scene(
 *         engine = engine,
 *         // ...
 *     )
 * }
 * ```
 */
@Composable
fun AcquireEngine() {
    DisposableEffect(Unit) {
        FilamentEngineManager.acquire()
        onDispose {
            FilamentEngineManager.release()
        }
    }
}
