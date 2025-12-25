package app.m1k3.ai.assistant.embedding

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Embedding Model Manager - Two-Tier Embedding Strategy
 *
 * Manages switching between built-in and dynamic embedding models:
 *
 * Tier 1 (Built-in): MiniLM-L6-v2
 * - 384 dimensions
 * - 80MB (included in APK)
 * - Fast, excellent quality
 * - Default for all users
 *
 * Tier 2 (Dynamic): Embedding Gemma 300M
 * - 512 dimensions
 * - 180MB (Play Store on-demand delivery)
 * - Slower, higher quality
 * - Optional upgrade for power users
 *
 * Features:
 * - Seamless model switching
 * - Download progress tracking
 * - Automatic fallback to MiniLM if Gemma unavailable
 * - Persistent model preference
 */
class EmbeddingModelManager(private val context: Context) {

    companion object {
        private const val TAG = "EmbeddingModelManager"
        private const val GEMMA_MODULE_NAME = "gemmaEmbedding"
        private const val PREFS_NAME = "embedding_preferences"
        private const val PREF_SELECTED_MODEL = "selected_model"

        const val MODEL_MINILM = "minilm"
        const val MODEL_GEMMA = "gemma"
    }

    private val splitInstallManager: SplitInstallManager =
        SplitInstallManagerFactory.create(context)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get currently selected model preference
     */
    fun getSelectedModel(): String {
        return prefs.getString(PREF_SELECTED_MODEL, MODEL_MINILM) ?: MODEL_MINILM
    }

    /**
     * Set model preference
     */
    fun setSelectedModel(model: String) {
        prefs.edit().putString(PREF_SELECTED_MODEL, model).apply()
        Log.d(TAG, "Selected model: $model")
    }

    /**
     * Check if Gemma module is installed
     */
    fun isGemmaInstalled(): Boolean {
        val installed = splitInstallManager.installedModules.contains(GEMMA_MODULE_NAME)
        Log.d(TAG, "Gemma module installed: $installed")
        return installed
    }

    /**
     * Get appropriate embedding engine based on selection and availability
     */
    fun getEmbeddingEngine(): EmbeddingEngine {
        val selectedModel = getSelectedModel()

        return when {
            selectedModel == MODEL_GEMMA && isGemmaInstalled() -> {
                Log.d(TAG, "Using Gemma 300M embedding engine")
                // Dynamically load Gemma engine from module
                try {
                    val clazz = Class.forName("app.m1k3.ai.assistant.gemma.GemmaEmbeddingEngine")
                    val constructor = clazz.getConstructor(Context::class.java, Int::class.java)
                    constructor.newInstance(context, 512) as EmbeddingEngine
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load Gemma engine, falling back to MiniLM", e)
                    MiniLmEmbeddingEngine(context)
                }
            }
            else -> {
                Log.d(TAG, "Using MiniLM-L6 embedding engine (default)")
                MiniLmEmbeddingEngine(context)
            }
        }
    }

    /**
     * Install Gemma module via Play Store dynamic delivery
     *
     * @return Flow emitting download progress (0.0 to 1.0) and completion status
     */
    fun installGemmaModule(): Flow<InstallProgress> = callbackFlow {
        Log.d(TAG, "Starting Gemma module installation...")

        // Check if already installed
        if (isGemmaInstalled()) {
            trySend(InstallProgress.Completed)
            close()
            return@callbackFlow
        }

        val listener = SplitInstallStateUpdatedListener { state ->
            if (state.moduleNames().contains(GEMMA_MODULE_NAME)) {
                when (state.status()) {
                    SplitInstallSessionStatus.DOWNLOADING -> {
                        val progress = if (state.totalBytesToDownload() > 0) {
                            state.bytesDownloaded().toFloat() / state.totalBytesToDownload()
                        } else {
                            0f
                        }
                        trySend(InstallProgress.Downloading(progress))
                        Log.d(TAG, "Downloading: ${(progress * 100).toInt()}%")
                    }
                    SplitInstallSessionStatus.INSTALLING -> {
                        trySend(InstallProgress.Installing)
                        Log.d(TAG, "Installing module...")
                    }
                    SplitInstallSessionStatus.INSTALLED -> {
                        trySend(InstallProgress.Completed)
                        Log.d(TAG, "Module installed successfully")
                        close()
                    }
                    SplitInstallSessionStatus.FAILED -> {
                        val error = Exception("Installation failed: error code ${state.errorCode()}")
                        trySend(InstallProgress.Failed(error))
                        Log.e(TAG, "Installation failed", error)
                        close(error)
                    }
                    SplitInstallSessionStatus.CANCELED -> {
                        val error = Exception("Installation canceled by user")
                        trySend(InstallProgress.Failed(error))
                        Log.d(TAG, "Installation canceled")
                        close(error)
                    }
                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                        // This would require showing a Play Store dialog
                        // For now, we'll just log it
                        Log.w(TAG, "User confirmation required")
                    }
                    else -> {
                        Log.d(TAG, "Status: ${state.status()}")
                    }
                }
            }
        }

        splitInstallManager.registerListener(listener)

        // Create install request
        val request = SplitInstallRequest.newBuilder()
            .addModule(GEMMA_MODULE_NAME)
            .build()

        // Start installation
        splitInstallManager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                Log.d(TAG, "Installation started: session $sessionId")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start installation", exception)
                trySend(InstallProgress.Failed(exception))
                close(exception)
            }

        awaitClose {
            splitInstallManager.unregisterListener(listener)
        }
    }

    /**
     * Uninstall Gemma module to reclaim storage
     */
    fun uninstallGemmaModule(onComplete: (Result<Unit>) -> Unit) {
        if (!isGemmaInstalled()) {
            onComplete(Result.success(Unit))
            return
        }

        Log.d(TAG, "Uninstalling Gemma module...")

        splitInstallManager.deferredUninstall(listOf(GEMMA_MODULE_NAME))
            .addOnSuccessListener {
                Log.d(TAG, "Gemma module uninstalled")
                // Switch back to MiniLM
                setSelectedModel(MODEL_MINILM)
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to uninstall Gemma module", exception)
                onComplete(Result.failure(exception))
            }
    }

    /**
     * Get model information
     */
    fun getModelInfo(model: String): ModelInfo {
        return when (model) {
            MODEL_MINILM -> ModelInfo(
                id = MODEL_MINILM,
                name = "MiniLM-L6-v2",
                description = "Fast, excellent quality (default)",
                dimensions = 384,
                size = "80MB",
                isBuiltIn = true,
                isInstalled = true,
                quality = ModelQuality.EXCELLENT,
                speed = ModelSpeed.FAST
            )
            MODEL_GEMMA -> ModelInfo(
                id = MODEL_GEMMA,
                name = "Embedding Gemma 300M",
                description = "Slower, higher quality (optional)",
                dimensions = 512,
                size = "180MB",
                isBuiltIn = false,
                isInstalled = isGemmaInstalled(),
                quality = ModelQuality.SUPERIOR,
                speed = ModelSpeed.MEDIUM
            )
            else -> throw IllegalArgumentException("Unknown model: $model")
        }
    }

    /**
     * Get all available models
     */
    fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            getModelInfo(MODEL_MINILM),
            getModelInfo(MODEL_GEMMA)
        )
    }
}

/**
 * Installation progress states
 */
sealed class InstallProgress {
    data class Downloading(val progress: Float) : InstallProgress()
    data object Installing : InstallProgress()
    data object Completed : InstallProgress()
    data class Failed(val error: Throwable) : InstallProgress()
}

/**
 * Model information
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val dimensions: Int,
    val size: String,
    val isBuiltIn: Boolean,
    val isInstalled: Boolean,
    val quality: ModelQuality,
    val speed: ModelSpeed
)

enum class ModelQuality {
    GOOD, EXCELLENT, SUPERIOR
}

enum class ModelSpeed {
    FAST, MEDIUM, SLOW
}
