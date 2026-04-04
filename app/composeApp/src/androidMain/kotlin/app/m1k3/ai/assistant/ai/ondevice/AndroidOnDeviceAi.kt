package app.m1k3.ai.assistant.ai.ondevice

import app.m1k3.ai.domain.ai.AiCoreModelPreference
import app.m1k3.ai.domain.ai.GenerationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * AndroidOnDeviceAi - Main Android implementation of OnDeviceAi
 *
 * This implementation provides a unified interface for on-device AI on Android by:
 * 1. Checking if ML Kit GenAI (Gemini Nano) is available on the device
 * 2. Using ML Kit GenAI for capable devices (Tensor G3+, SD 8 Gen 3+)
 * 3. Falling back to LlamaCppFallbackEngine for older devices
 *
 * ## Device Requirements (ML Kit GenAI)
 * - **Android 14+** with Google Play AI Core
 * - **Tensor G3+** (Pixel 8 and newer)
 * - **Snapdragon 8 Gen 3+** (Samsung S24 and newer)
 * - **Dimensity 9300+** (select MediaTek devices)
 *
 * ## Fallback (LlamaCpp)
 * - All Android 8.0+ devices (API 27+)
 * - SmolLM2-135M Q4_K_M GGUF model
 * - Slightly lower quality but universal compatibility
 *
 * ## Thread Safety
 * Uses AtomicReference<EngineState> to ensure atomic reads of both engine and
 * initialization status. The Mutex is only used during initialization to prevent
 * concurrent initialization attempts.
 *
 * ## Usage
 *
 * ```kotlin
 * // Create with real dependencies
 * val androidAi = AndroidOnDeviceAi(
 *     mlKitChecker = DefaultMlKitAvailabilityChecker(),
 *     mlKitEngine = RealMlKitGenAiEngine(context),  // when available
 *     fallbackEngine = LlamaCppFallbackEngine(LlamaCppEngine(context))
 * )
 *
 * // Check availability
 * when (val availability = androidAi.checkAvailability()) {
 *     is AiAvailability.Available -> println("Using ML Kit GenAI!")
 *     is AiAvailability.Fallback -> println("Using ${availability.engineName}")
 *     is AiAvailability.Unavailable -> println("No AI available: ${availability.reason}")
 *     is AiAvailability.Downloading -> println("Model downloading...")
 * }
 *
 * // Initialize and generate
 * androidAi.downloadModelIfNeeded()
 * val result = androidAi.generate("Hello!", GenerationConfig())
 * result.onSuccess { println(it) }
 * ```
 *
 * @param mlKitChecker Checker for ML Kit GenAI device compatibility
 * @param mlKitEngine ML Kit GenAI engine (Gemini Nano)
 * @param fallbackEngine LlamaCpp fallback for unsupported devices
 */
class AndroidOnDeviceAi(
    private val mlKitChecker: MlKitAvailabilityChecker,
    private var mlKitEngine: MlKitGenAiEngine,
    private val fallbackEngine: OnDeviceAi,
    private val mlKitEngineFactory: ((AiCoreModelPreference) -> MlKitGenAiEngine)? = null
) : OnDeviceAi {

    /**
     * Enum representing which engine is currently active.
     */
    private enum class ActiveEngine {
        NONE,
        ML_KIT,
        FALLBACK
    }

    /**
     * Immutable state class combining engine and initialization status.
     * Using a data class with AtomicReference ensures atomic reads of both values.
     */
    private data class EngineState(
        val engine: ActiveEngine = ActiveEngine.NONE,
        val initialized: Boolean = false
    )

    // Thread-safe state using AtomicReference for lock-free reads
    private val engineState = AtomicReference(EngineState())

    // Mutex only for initialization to prevent concurrent init attempts
    private val initMutex = Mutex()

    /**
     * Check AI availability.
     *
     * First checks if ML Kit GenAI is available. If not, falls back to LlamaCpp.
     * This is a lightweight check that does not require synchronization.
     *
     * Note: This method does not hold the mutex since it only performs read-only
     * checks on external services. It may return different results if called
     * during or after initialization.
     */
    override suspend fun checkAvailability(): AiAvailability {
        return try {
            if (mlKitChecker.isGenAiAvailable()) {
                val mlKitAvailability = mlKitEngine.checkAvailability()
                if (mlKitAvailability is AiAvailability.Available ||
                    mlKitAvailability is AiAvailability.Downloading) {
                    mlKitAvailability
                } else {
                    // ML Kit check passed but engine not ready, use fallback
                    fallbackEngine.checkAvailability()
                }
            } else {
                // Device doesn't support ML Kit GenAI, use fallback
                fallbackEngine.checkAvailability()
            }
        } catch (e: Exception) {
            // Any error checking ML Kit, use fallback
            fallbackEngine.checkAvailability()
        }
    }

    /**
     * Download/initialize the model.
     *
     * Determines which engine to use and initializes it.
     *
     * Thread-safe: Uses Mutex with double-checked locking for initialization.
     * The AtomicReference ensures atomic state updates visible to all threads.
     */
    override suspend fun downloadModelIfNeeded(): AiResult<Unit> {
        // Fast path: already initialized (lock-free read)
        if (engineState.get().initialized) {
            return AiResult.Success(Unit)
        }

        // Slow path: need to determine engine and initialize
        return initMutex.withLock {
            // Double-check after acquiring lock (atomic read)
            if (engineState.get().initialized) {
                return@withLock AiResult.Success(Unit)
            }

            try {
                val useMlKit = mlKitChecker.isGenAiAvailable()

                if (useMlKit) {
                    val result = mlKitEngine.downloadModelIfNeeded()
                    if (result.isSuccess) {
                        // Atomic update of both engine and initialized flag
                        engineState.set(EngineState(ActiveEngine.ML_KIT, true))
                        return@withLock AiResult.Success(Unit)
                    }
                    // ML Kit download failed, try fallback
                }

                // Use fallback
                val fallbackResult = fallbackEngine.downloadModelIfNeeded()
                if (fallbackResult.isSuccess) {
                    // Atomic update of both engine and initialized flag
                    engineState.set(EngineState(ActiveEngine.FALLBACK, true))
                }
                fallbackResult
            } catch (e: Exception) {
                // Error checking ML Kit, try fallback directly
                val fallbackResult = fallbackEngine.downloadModelIfNeeded()
                if (fallbackResult.isSuccess) {
                    engineState.set(EngineState(ActiveEngine.FALLBACK, true))
                }
                fallbackResult
            }
        }
    }

    /**
     * Generate text from prompt.
     *
     * Delegates to the active engine (ML Kit or fallback).
     * Uses atomic read of state to ensure consistent engine/initialized values.
     */
    override suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String> {
        // Atomic read of both engine and initialized status
        val state = engineState.get()

        if (!state.initialized) {
            return AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "Engine not initialized. Call downloadModelIfNeeded() first."
            )
        }

        return when (state.engine) {
            ActiveEngine.ML_KIT -> mlKitEngine.generate(prompt, config)
            ActiveEngine.FALLBACK -> fallbackEngine.generate(prompt, config)
            ActiveEngine.NONE -> AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "No engine available"
            )
        }
    }

    /**
     * Generate text with streaming tokens.
     *
     * Delegates to the active engine.
     * Uses atomic read of state to ensure consistent engine/initialized values.
     */
    override fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>> {
        // Atomic read of both engine and initialized status
        val state = engineState.get()

        if (!state.initialized) {
            return kotlinx.coroutines.flow.flowOf(
                AiResult.Error(
                    AiErrorCode.UNAVAILABLE,
                    "Engine not initialized. Call downloadModelIfNeeded() first."
                )
            )
        }

        return when (state.engine) {
            ActiveEngine.ML_KIT -> mlKitEngine.generateStream(prompt, config)
            ActiveEngine.FALLBACK -> fallbackEngine.generateStream(prompt, config)
            ActiveEngine.NONE -> kotlinx.coroutines.flow.flowOf(
                AiResult.Error(AiErrorCode.UNAVAILABLE, "No engine available")
            )
        }
    }

    /**
     * Summarize text.
     *
     * Delegates to the active engine.
     * Uses atomic read of state to ensure consistent engine/initialized values.
     */
    override suspend fun summarize(text: String, style: SummaryStyle): AiResult<String> {
        // Atomic read of both engine and initialized status
        val state = engineState.get()

        if (!state.initialized) {
            return AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "Engine not initialized. Call downloadModelIfNeeded() first."
            )
        }

        return when (state.engine) {
            ActiveEngine.ML_KIT -> mlKitEngine.summarize(text, style)
            ActiveEngine.FALLBACK -> fallbackEngine.summarize(text, style)
            ActiveEngine.NONE -> AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "No engine available"
            )
        }
    }

    /**
     * Get model information.
     *
     * Returns info about the active engine.
     * Uses atomic read for thread-safe state access.
     */
    override suspend fun getModelInfo(): String {
        val state = engineState.get()
        return when (state.engine) {
            ActiveEngine.ML_KIT -> mlKitEngine.getModelInfo()
            ActiveEngine.FALLBACK -> fallbackEngine.getModelInfo()
            ActiveEngine.NONE -> "AndroidOnDeviceAi (not initialized)"
        }
    }

    /**
     * Switch the AICore model preference (e.g., STABLE → PREVIEW_SPEED).
     *
     * Releases the current ML Kit engine and creates a new one with the
     * requested preference. Re-initializes if ML Kit was the active engine.
     *
     * @param preference The desired AICore model preference
     */
    suspend fun switchAiCoreModel(preference: AiCoreModelPreference) {
        val factory = mlKitEngineFactory ?: return

        initMutex.withLock {
            val currentState = engineState.get()

            // Release current ML Kit engine
            if (currentState.engine == ActiveEngine.ML_KIT) {
                mlKitEngine.release()
            }

            // Create new engine with updated preference
            mlKitEngine = factory(preference)

            // Re-initialize if ML Kit was active
            if (currentState.engine == ActiveEngine.ML_KIT) {
                val result = mlKitEngine.downloadModelIfNeeded()
                if (result.isSuccess) {
                    engineState.set(EngineState(ActiveEngine.ML_KIT, true))
                }
            }
        }
    }

    /**
     * Release resources.
     *
     * Releases the active engine and resets state atomically.
     *
     * Thread-safe: Uses compareAndSet loop to ensure only one thread releases the engine,
     * even if release() is called concurrently from multiple threads.
     *
     * If release() is called concurrently with downloadModelIfNeeded(), the
     * mutex in downloadModelIfNeeded() will see the reset state and re-initialize.
     */
    override fun release() {
        // Loop until we successfully transition to NONE or find it already NONE
        while (true) {
            val currentState = engineState.get()

            // Already released by another thread
            if (currentState.engine == ActiveEngine.NONE && !currentState.initialized) {
                return
            }

            // Try to atomically claim the release
            if (engineState.compareAndSet(currentState, EngineState())) {
                // We won the race - release the engine (only one thread reaches here)
                when (currentState.engine) {
                    ActiveEngine.ML_KIT -> mlKitEngine.release()
                    ActiveEngine.FALLBACK -> fallbackEngine.release()
                    ActiveEngine.NONE -> { /* Nothing to release */ }
                }
                return
            }
            // CAS failed, another thread modified state - retry with updated state
        }
    }
}
