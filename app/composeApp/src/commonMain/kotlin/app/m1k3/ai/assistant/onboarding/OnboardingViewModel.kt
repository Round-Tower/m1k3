package app.m1k3.ai.assistant.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.m1k3.ai.assistant.platform.PreferenceKeys
import app.m1k3.ai.domain.ai.LlmModel
import app.m1k3.ai.domain.ai.M1K3Tier
import app.m1k3.ai.domain.platform.DeviceTier
import app.m1k3.ai.domain.platform.PreferencesStoreInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * OnboardingViewModel — Orchestrates the first-launch experience.
 *
 * @param getDeviceTier Returns the [DeviceTier] for this device
 * @param downloadModel Downloads a model, emitting [OnboardingDownloadState] progress.
 *        The DI layer adapts from [DownloadProgress] (androidMain) to [OnboardingDownloadState]
 *        (commonMain) so this ViewModel stays KMP-safe.
 * @param prefs Persistent key-value store for completion flag
 */
class OnboardingViewModel(
    private val getDeviceTier: () -> DeviceTier,
    private val downloadModel: (LlmModel) -> Flow<OnboardingDownloadState>,
    private val prefs: PreferencesStoreInterface
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    // ===== Step navigation =====

    fun onWelcomeContinue() {
        val recommended = M1K3Tier.forDevice(getDeviceTier())
        _uiState.update {
            it.copy(
                step = OnboardingStep.YourEngine,
                recommendedTier = recommended,
                selectedTier = recommended
            )
        }
    }

    fun onTierSelected(tier: M1K3Tier) {
        _uiState.update { it.copy(selectedTier = tier) }
    }

    fun onInstallConfirmed() {
        val tier = _uiState.value.selectedTier ?: return
        _uiState.update { it.copy(step = OnboardingStep.Awakening) }
        startDownload(tier.model)
    }

    // ===== Download =====

    private fun startDownload(model: LlmModel) {
        _uiState.update { it.copy(downloadState = OnboardingDownloadState.Starting) }

        viewModelScope.launch {
            downloadModel(model).collect { progress ->
                _uiState.update { it.copy(downloadState = progress) }

                if (progress is OnboardingDownloadState.Complete) {
                    prefs.setString(
                        PreferenceKeys.SELECTED_M1K3_TIER,
                        tierToKey(_uiState.value.selectedTier)
                    )
                    prefs.setBoolean(PreferenceKeys.ONBOARDING_COMPLETE, true)
                    _uiState.update { it.copy(isComplete = true) }
                }
            }
        }
    }

    fun retryDownload() {
        val tier = _uiState.value.selectedTier ?: return
        startDownload(tier.model)
    }

    // ===== Helpers =====

    private fun tierToKey(tier: M1K3Tier?): String = when (tier) {
        is M1K3Tier.Mini -> "mini"
        is M1K3Tier.Lil  -> "lil"
        is M1K3Tier.Big  -> "big"
        null             -> "lil"
    }
}
