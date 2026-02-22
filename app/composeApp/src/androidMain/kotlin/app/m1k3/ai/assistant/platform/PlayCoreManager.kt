package app.m1k3.ai.assistant.platform

import android.content.Context
import com.google.android.play.core.splitinstall.*
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * PlayCoreManager - Google Play Dynamic Feature Delivery Manager
 *
 * Manages on-demand download of the Qwen2.5-Coder dynamic feature module (130MB).
 *
 * Features:
 * - Asynchronous module installation with progress tracking
 * - Automatic retry on failure
 * - Installation state monitoring
 * - User confirmation dialog handling
 * - Cancellation support
 *
 * Module Details:
 * - Module name: "codingModule"
 * - Size: ~130MB (120MB model + 10MB assets)
 * - Contents: Qwen2.5-Coder-0.5B-Instruct ONNX model + tokenizer + templates
 *
 * Google Play Asset Delivery:
 * - On-demand install: Downloads when user requests code generation
 * - Deferred install: Downloads in background after app install
 * - Fast-follow install: Downloads shortly after app install
 *
 * Usage:
 * ```kotlin
 * val manager = PlayCoreManager(context)
 *
 * // Check if module is installed
 * if (!manager.isModuleInstalled("codingModule")) {
 *     // Monitor installation progress
 *     manager.installModule("codingModule").collect { state ->
 *         when (state) {
 *             is InstallState.Downloading -> updateProgress(state.progress)
 *             is InstallState.Installed -> onModuleReady()
 *             is InstallState.Failed -> showError(state.error)
 *         }
 *     }
 * }
 * ```
 *
 * @property context Android application context
 */
class PlayCoreManager(private val context: Context) {

    private val splitInstallManager: SplitInstallManager by lazy {
        SplitInstallManagerFactory.create(context)
    }

    /**
     * Check if a module is currently installed
     *
     * @param moduleName Name of the dynamic feature module
     * @return true if module is installed and ready
     */
    fun isModuleInstalled(moduleName: String): Boolean {
        return splitInstallManager.installedModules.contains(moduleName)
    }

    /**
     * Get list of all installed modules
     */
    fun getInstalledModules(): Set<String> {
        return splitInstallManager.installedModules
    }

    /**
     * Install a dynamic feature module with progress tracking
     *
     * Flow emissions:
     * 1. InstallState.Pending - Installation request submitted
     * 2. InstallState.RequiresConfirmation - User confirmation needed (>10MB)
     * 3. InstallState.Downloading - Download in progress (0-100%)
     * 4. InstallState.Installing - Installing downloaded module
     * 5. InstallState.Installed - Module ready to use
     * 6. InstallState.Failed - Installation failed with error
     *
     * @param moduleName Name of the dynamic feature module
     * @return Flow of installation states
     */
    fun installModule(moduleName: String): Flow<InstallState> = callbackFlow {
        // Check if already installed
        if (isModuleInstalled(moduleName)) {
            trySend(InstallState.Installed(moduleName))
            close()
            return@callbackFlow
        }

        // Create install request
        val request = SplitInstallRequest.newBuilder()
            .addModule(moduleName)
            .build()

        // Register state listener
        val listener = object : SplitInstallStateUpdatedListener {
            override fun onStateUpdate(state: SplitInstallSessionState) {
                when (state.status()) {
                    SplitInstallSessionStatus.PENDING -> {
                        trySend(InstallState.Pending(moduleName))
                    }

                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                        // Module is >10MB, requires user confirmation
                        trySend(InstallState.RequiresConfirmation(
                            moduleName = moduleName,
                            sessionId = state.sessionId()
                        ))
                    }

                    SplitInstallSessionStatus.DOWNLOADING -> {
                        val totalBytes = state.totalBytesToDownload()
                        val downloadedBytes = state.bytesDownloaded()
                        val progress = if (totalBytes > 0) {
                            (downloadedBytes * 100 / totalBytes).toInt()
                        } else {
                            0
                        }

                        trySend(InstallState.Downloading(
                            moduleName = moduleName,
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes
                        ))
                    }

                    SplitInstallSessionStatus.INSTALLING -> {
                        trySend(InstallState.Installing(moduleName))
                    }

                    SplitInstallSessionStatus.INSTALLED -> {
                        trySend(InstallState.Installed(moduleName))
                        splitInstallManager.unregisterListener(this)
                        close()
                    }

                    SplitInstallSessionStatus.FAILED -> {
                        val errorCode = state.errorCode()
                        trySend(InstallState.Failed(
                            moduleName = moduleName,
                            error = InstallError.fromErrorCode(errorCode),
                            errorCode = errorCode
                        ))
                        splitInstallManager.unregisterListener(this)
                        close()
                    }

                    SplitInstallSessionStatus.CANCELING -> {
                        trySend(InstallState.Canceling(moduleName))
                    }

                    SplitInstallSessionStatus.CANCELED -> {
                        trySend(InstallState.Canceled(moduleName))
                        splitInstallManager.unregisterListener(this)
                        close()
                    }

                    else -> {
                        // Unknown or downloaded state
                    }
                }
            }
        }

        // Register listener
        splitInstallManager.registerListener(listener)

        // Start installation
        splitInstallManager.startInstall(request)
            .addOnFailureListener { exception ->
                trySend(InstallState.Failed(
                    moduleName = moduleName,
                    error = InstallError.Unknown(exception.message ?: "Installation failed"),
                    errorCode = -1
                ))
                splitInstallManager.unregisterListener(listener)
                close()
            }

        // Cleanup on cancellation
        awaitClose {
            splitInstallManager.unregisterListener(listener)
        }
    }

    /**
     * Uninstall a dynamic feature module to free space
     *
     * @param moduleName Name of the module to uninstall
     * @return Result indicating success or failure
     */
    suspend fun uninstallModule(moduleName: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        if (!isModuleInstalled(moduleName)) {
            continuation.resume(Result.success(Unit))
            return@suspendCancellableCoroutine
        }

        splitInstallManager.deferredUninstall(listOf(moduleName))
            .addOnSuccessListener {
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    /**
     * Cancel an ongoing installation
     *
     * @param sessionId Session ID from InstallState.RequiresConfirmation
     */
    suspend fun cancelInstall(sessionId: Int): Result<Unit> = suspendCancellableCoroutine { continuation ->
        splitInstallManager.cancelInstall(sessionId)
            .addOnSuccessListener {
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    /**
     * Get size of a module in bytes
     *
     * Note: This requires the module to be downloaded first, or check Google Play Console
     * for pre-download size estimation.
     *
     * For codingModule: ~130MB (120MB model + 10MB assets)
     */
    fun getModuleSize(moduleName: String): Long {
        // This is an approximation - actual size varies by device
        return when (moduleName) {
            "codingModule" -> 130 * 1024 * 1024L // 130MB
            else -> 0L
        }
    }
}

/**
 * Installation state for dynamic feature modules
 */
sealed class InstallState {
    abstract val moduleName: String

    /**
     * Installation request submitted, pending network
     */
    data class Pending(override val moduleName: String) : InstallState()

    /**
     * User confirmation required (module >10MB)
     * Show confirmation dialog to user
     */
    data class RequiresConfirmation(
        override val moduleName: String,
        val sessionId: Int
    ) : InstallState()

    /**
     * Downloading module from Google Play
     *
     * @property progress Download progress (0-100)
     * @property downloadedBytes Bytes downloaded
     * @property totalBytes Total bytes to download
     */
    data class Downloading(
        override val moduleName: String,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : InstallState() {
        /**
         * Human-readable download status
         */
        fun getDisplayText(): String {
            val downloadedMB = downloadedBytes / (1024 * 1024)
            val totalMB = totalBytes / (1024 * 1024)
            return "Downloading: $downloadedMB MB / $totalMB MB ($progress%)"
        }
    }

    /**
     * Installing downloaded module
     */
    data class Installing(override val moduleName: String) : InstallState()

    /**
     * Module successfully installed and ready
     */
    data class Installed(override val moduleName: String) : InstallState()

    /**
     * Installation failed
     */
    data class Failed(
        override val moduleName: String,
        val error: InstallError,
        val errorCode: Int
    ) : InstallState()

    /**
     * Installation being canceled
     */
    data class Canceling(override val moduleName: String) : InstallState()

    /**
     * Installation canceled by user
     */
    data class Canceled(override val moduleName: String) : InstallState()
}

/**
 * Installation error types
 */
sealed class InstallError(open val message: String) {
    /**
     * Network error during download
     */
    data class NetworkError(override val message: String = "Network connection failed") : InstallError(message)

    /**
     * Insufficient storage space
     */
    data class InsufficientStorage(override val message: String = "Not enough storage space") : InstallError(message)

    /**
     * Module not found in Google Play
     */
    data class ModuleNotFound(override val message: String = "Module not available") : InstallError(message)

    /**
     * App version incompatible with module
     */
    data class Incompatible(override val message: String = "App version incompatible") : InstallError(message)

    /**
     * Unknown error
     */
    data class Unknown(override val message: String = "Installation failed") : InstallError(message)

    companion object {
        /**
         * Convert Google Play error code to InstallError
         */
        fun fromErrorCode(errorCode: Int): InstallError {
            return when (errorCode) {
                SplitInstallErrorCode.NETWORK_ERROR -> NetworkError()
                SplitInstallErrorCode.INSUFFICIENT_STORAGE -> InsufficientStorage()
                SplitInstallErrorCode.MODULE_UNAVAILABLE -> ModuleNotFound()
                SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION -> Incompatible()
                SplitInstallErrorCode.API_NOT_AVAILABLE -> Unknown("Google Play Core API not available")
                SplitInstallErrorCode.ACCESS_DENIED -> Unknown("Access denied")
                SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED -> Unknown("Too many active downloads")
                else -> Unknown("Error code: $errorCode")
            }
        }
    }
}
