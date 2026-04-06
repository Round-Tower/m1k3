package app.m1k3.ai.assistant.onboarding

import app.m1k3.ai.domain.ai.M1K3Tier

/**
 * OnboardingUiState — State machine for the first-launch experience.
 *
 * Three steps:
 * 1. [OnboardingStep.Welcome] — "Your local intelligence machine."
 * 2. [OnboardingStep.YourEngine] — Hardware-matched tier reveal.
 * 3. [OnboardingStep.Awakening] — Model download + ethos while M1K3 wakes up.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    /** Recommended tier for this device (set on entry to YourEngine step) */
    val recommendedTier: M1K3Tier? = null,
    /** Tier the user actually chose (defaults to recommendedTier) */
    val selectedTier: M1K3Tier? = null,
    val downloadState: OnboardingDownloadState = OnboardingDownloadState.Idle,
    /** True once onboarding is complete and app can navigate to Chat */
    val isComplete: Boolean = false
)

enum class OnboardingStep {
    Welcome,
    YourEngine,
    Awakening
}

sealed class OnboardingDownloadState {
    data object Idle : OnboardingDownloadState()
    data object Starting : OnboardingDownloadState()
    data class Downloading(
        val progressPercent: Int,
        val downloadedMb: Int,
        val totalMb: Int
    ) : OnboardingDownloadState()
    data object Complete : OnboardingDownloadState()
    data class Failed(val error: String) : OnboardingDownloadState()
}
