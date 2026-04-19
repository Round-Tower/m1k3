package app.m1k3.ai.assistant.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.m1k3.ai.assistant.ai.ondevice.AiAvailability
import app.m1k3.ai.assistant.ai.ondevice.AndroidOnDeviceAi
import app.m1k3.ai.assistant.ai.ondevice.OnDeviceAi
import app.m1k3.ai.domain.ai.AiCoreModelPreference
import app.m1k3.ai.domain.ai.GenerationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SettingsViewModel - Manages settings screen UI state
 *
 * **Responsibilities:**
 * - ML Kit status checking
 * - RAG toggle persistence
 * - Test generation execution
 * - Model info retrieval
 *
 * **Usage Example:**
 * ```kotlin
 * val viewModel = rememberSettingsViewModel(onDeviceAi)
 * val state by viewModel.state.collectAsState()
 *
 * LaunchedEffect(Unit) {
 *     viewModel.checkMlKitAvailability()
 * }
 * ```
 */
class SettingsViewModel(
    private val onDeviceAi: OnDeviceAi,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("ma_ai_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /**
     * Check ML Kit GenAI availability.
     */
    fun checkMlKitAvailability() {
        _state.value = _state.value.copy(mlKitStatus = MlKitStatus.Checking)

        scope.launch {
            val availability = onDeviceAi.checkAvailability()
            val modelInfo = onDeviceAi.getModelInfo()

            _state.value =
                _state.value.copy(
                    mlKitStatus = MlKitStatus.Loaded(availability),
                    modelInfo = modelInfo,
                )
        }
    }

    /**
     * Toggle RAG feature.
     */
    fun setRagEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("rag_enabled", enabled).apply()
        _state.value = _state.value.copy(ragEnabled = enabled)
    }

    /**
     * Get current RAG enabled state.
     */
    fun isRagEnabled(): Boolean = prefs.getBoolean("rag_enabled", false)

    /**
     * Run a test generation.
     */
    fun runTestGeneration() {
        if (_state.value.isTestRunning) return

        _state.value =
            _state.value.copy(
                isTestRunning = true,
                testResult = "Initializing...",
            )

        scope.launch {
            try {
                // First, ensure model is downloaded/initialized
                val downloadResult = onDeviceAi.downloadModelIfNeeded()
                downloadResult.fold(
                    onSuccess = {
                        // Model ready, now generate
                        _state.value = _state.value.copy(testResult = "Generating...")
                        val config = GenerationConfig(maxTokens = 64)
                        val result = onDeviceAi.generate("Hello, what is 2+2?", config)
                        result.fold(
                            onSuccess = { response ->
                                _state.value =
                                    _state.value.copy(
                                        testResult = "Success: $response",
                                        isTestRunning = false,
                                    )
                            },
                            onError = { code, message ->
                                _state.value =
                                    _state.value.copy(
                                        testResult = "Generation Error [$code]: $message",
                                        isTestRunning = false,
                                    )
                            },
                        )
                    },
                    onError = { code, message ->
                        _state.value =
                            _state.value.copy(
                                testResult = "Init Error [$code]: $message",
                                isTestRunning = false,
                            )
                    },
                )
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        testResult = "Exception: ${e.message}",
                        isTestRunning = false,
                    )
            }
        }
    }

    /**
     * Clear test result.
     */
    fun clearTestResult() {
        _state.value = _state.value.copy(testResult = null)
    }

    /**
     * Switch AICore model preference.
     *
     * Updates the ML Kit engine to use the selected Gemma 4 variant
     * or fall back to stable Gemini Nano.
     */
    fun switchAiCorePreference(preference: AiCoreModelPreference) {
        if (preference == _state.value.aiCorePreference) return

        _state.value = _state.value.copy(aiCorePreference = preference)
        prefs.edit().putString("aicore_preference", preference.name).apply()

        scope.launch {
            val androidAi = onDeviceAi as? AndroidOnDeviceAi ?: return@launch
            androidAi.switchAiCoreModel(preference)

            // Refresh model info
            val modelInfo = onDeviceAi.getModelInfo()
            _state.value = _state.value.copy(modelInfo = modelInfo)
        }
    }

    /**
     * Initialize state from preferences.
     */
    fun initializeFromPreferences() {
        val savedPreference = prefs.getString("aicore_preference", null)
        val aiCorePreference =
            savedPreference?.let {
                try {
                    AiCoreModelPreference.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            } ?: AiCoreModelPreference.STABLE

        _state.value =
            _state.value.copy(
                ragEnabled = prefs.getBoolean("rag_enabled", false),
                aiCorePreference = aiCorePreference,
            )
    }
}

/**
 * UI state for settings screen.
 */
data class SettingsState(
    val mlKitStatus: MlKitStatus = MlKitStatus.Checking,
    val modelInfo: String = "Loading...",
    val ragEnabled: Boolean = false,
    val isTestRunning: Boolean = false,
    val testResult: String? = null,
    val aiCorePreference: AiCoreModelPreference = AiCoreModelPreference.STABLE,
)

/**
 * ML Kit status for display.
 */
sealed class MlKitStatus {
    data object Checking : MlKitStatus()

    data class Loaded(
        val availability: AiAvailability,
    ) : MlKitStatus()
}

/**
 * Remember a SettingsViewModel scoped to the composition.
 *
 * @param onDeviceAi The OnDeviceAi instance
 * @return SettingsViewModel instance
 */
@Composable
fun rememberSettingsViewModel(onDeviceAi: OnDeviceAi): SettingsViewModel {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return remember(onDeviceAi) {
        SettingsViewModel(
            onDeviceAi = onDeviceAi,
            context = context,
            scope = scope,
        ).also {
            it.initializeFromPreferences()
        }
    }
}

/**
 * Collect settings state as Compose State.
 */
@Composable
fun SettingsViewModel.collectAsState(): androidx.compose.runtime.State<SettingsState> = state.collectAsState()
