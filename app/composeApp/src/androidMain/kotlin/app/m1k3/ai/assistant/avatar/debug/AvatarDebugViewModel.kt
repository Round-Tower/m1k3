package app.m1k3.ai.assistant.avatar.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.m1k3.ai.assistant.avatar.AvatarActivity
import app.m1k3.ai.assistant.avatar.AvatarEmotion
import app.m1k3.ai.assistant.avatar.AvatarState
import app.m1k3.ai.assistant.avatar.ModelConfig
import app.m1k3.ai.assistant.avatar.ModelRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AvatarDebugViewModel - Manages avatar debug screen UI state
 *
 * **Responsibilities:**
 * - Emotion and activity state
 * - 2D/3D mode toggle
 * - Model selection
 * - Camera controls state
 * - Intensity control
 *
 * **Usage Example:**
 * ```kotlin
 * val viewModel = rememberAvatarDebugViewModel()
 * val state by viewModel.collectAsState()
 *
 * AvatarView(state = viewModel.avatarState)
 * ```
 */
class AvatarDebugViewModel {
    private val _state = MutableStateFlow(AvatarDebugState())
    val state: StateFlow<AvatarDebugState> = _state.asStateFlow()

    /**
     * Get the current AvatarState for rendering.
     */
    fun getAvatarState(): AvatarState {
        val s = _state.value
        return AvatarState(
            emotion = s.currentEmotion,
            activity = s.currentActivity,
            intensity = s.intensity,
            message = if (s.use3D) s.selectedModel.name else "2D Canvas Robot"
        )
    }

    fun setEmotion(emotion: AvatarEmotion) {
        _state.value = _state.value.copy(currentEmotion = emotion)
    }

    fun setActivity(activity: AvatarActivity) {
        _state.value = _state.value.copy(currentActivity = activity)
    }

    fun setIntensity(intensity: Float) {
        _state.value = _state.value.copy(intensity = intensity)
    }

    fun setUse3D(use3D: Boolean) {
        _state.value = _state.value.copy(use3D = use3D)
    }

    fun setSelectedModel(model: ModelConfig) {
        _state.value = _state.value.copy(selectedModel = model)
    }

    fun setEnableInteraction(enabled: Boolean) {
        _state.value = _state.value.copy(enableInteraction = enabled)
    }

    fun toggleShowAdvanced() {
        _state.value = _state.value.copy(showAdvanced = !_state.value.showAdvanced)
    }

    fun toggleShowModelInfo() {
        _state.value = _state.value.copy(showModelInfo = !_state.value.showModelInfo)
    }

    /**
     * Apply happy path preset.
     */
    fun applyHappyPath() {
        _state.value = _state.value.copy(
            currentEmotion = AvatarEmotion.HAPPY,
            currentActivity = AvatarActivity.SPEAKING,
            intensity = 0.8f
        )
    }

    /**
     * Apply thinking preset.
     */
    fun applyThinking() {
        _state.value = _state.value.copy(
            currentEmotion = AvatarEmotion.THINKING,
            currentActivity = AvatarActivity.THINKING,
            intensity = 0.6f
        )
    }

    /**
     * Apply generating preset.
     */
    fun applyGenerating() {
        _state.value = _state.value.copy(
            currentEmotion = AvatarEmotion.EXCITED,
            currentActivity = AvatarActivity.GENERATING,
            intensity = 0.9f
        )
    }

    /**
     * Apply error preset.
     */
    fun applyError() {
        _state.value = _state.value.copy(
            currentEmotion = AvatarEmotion.ANGRY,
            currentActivity = AvatarActivity.ERROR,
            intensity = 1.0f
        )
    }
}

/**
 * UI state for avatar debug screen.
 */
data class AvatarDebugState(
    val currentEmotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    val currentActivity: AvatarActivity = AvatarActivity.IDLE,
    val intensity: Float = 0.5f,
    val use3D: Boolean = true,
    val showAdvanced: Boolean = false,
    val selectedModel: ModelConfig = ModelRegistry.getDefault(),
    val enableInteraction: Boolean = true,
    val showModelInfo: Boolean = false
)

/**
 * Remember an AvatarDebugViewModel scoped to the composition.
 */
@Composable
fun rememberAvatarDebugViewModel(): AvatarDebugViewModel {
    return remember { AvatarDebugViewModel() }
}

/**
 * Collect avatar debug state as Compose State.
 */
@Composable
fun AvatarDebugViewModel.collectAsState(): State<AvatarDebugState> {
    return state.collectAsState()
}
