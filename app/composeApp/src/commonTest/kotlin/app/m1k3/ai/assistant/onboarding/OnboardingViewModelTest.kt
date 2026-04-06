package app.m1k3.ai.assistant.onboarding

import app.m1k3.ai.assistant.platform.PreferenceKeys
import app.m1k3.ai.domain.ai.LlmModel
import app.m1k3.ai.domain.ai.M1K3Tier
import app.m1k3.ai.domain.platform.DeviceTier
import app.m1k3.ai.domain.platform.PreferencesStoreInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD Tests for OnboardingViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ===== Helpers =====

    private fun fakePrefs() = FakePreferences()

    private fun vmFor(
        deviceTier: DeviceTier = DeviceTier.HIGH_END,
        downloadFlow: Flow<OnboardingDownloadState> = flowOf(),
        prefs: PreferencesStoreInterface = fakePrefs()
    ) = OnboardingViewModel(
        getDeviceTier = { deviceTier },
        downloadModel = { downloadFlow },
        prefs = prefs
    )

    // ===== Initial state =====

    @Test
    fun `initial step is Welcome`() {
        assertEquals(OnboardingStep.Welcome, vmFor().uiState.value.step)
    }

    @Test
    fun `initial download state is Idle`() {
        assertIs<OnboardingDownloadState.Idle>(vmFor().uiState.value.downloadState)
    }

    @Test
    fun `initial recommended tier is null`() {
        assertNull(vmFor().uiState.value.recommendedTier)
    }

    // ===== Welcome → YourEngine =====

    @Test
    fun `onWelcomeContinue transitions to YourEngine`() {
        val vm = vmFor()
        vm.onWelcomeContinue()
        assertEquals(OnboardingStep.YourEngine, vm.uiState.value.step)
    }

    @Test
    fun `FLAGSHIP maps to Big M1K3`() {
        val vm = vmFor(deviceTier = DeviceTier.FLAGSHIP)
        vm.onWelcomeContinue()
        assertIs<M1K3Tier.Big>(vm.uiState.value.recommendedTier)
    }

    @Test
    fun `HIGH_END maps to Big M1K3`() {
        val vm = vmFor(deviceTier = DeviceTier.HIGH_END)
        vm.onWelcomeContinue()
        assertIs<M1K3Tier.Big>(vm.uiState.value.recommendedTier)
    }

    @Test
    fun `BUDGET maps to Lil M1K3`() {
        val vm = vmFor(deviceTier = DeviceTier.BUDGET)
        vm.onWelcomeContinue()
        assertIs<M1K3Tier.Lil>(vm.uiState.value.recommendedTier)
    }

    @Test
    fun `LOW_END maps to Mini M1K3`() {
        val vm = vmFor(deviceTier = DeviceTier.LOW_END)
        vm.onWelcomeContinue()
        assertIs<M1K3Tier.Mini>(vm.uiState.value.recommendedTier)
    }

    @Test
    fun `recommended tier is also selected by default`() {
        val vm = vmFor(deviceTier = DeviceTier.BUDGET)
        vm.onWelcomeContinue()
        assertEquals(vm.uiState.value.recommendedTier, vm.uiState.value.selectedTier)
    }

    // ===== Tier selection =====

    @Test
    fun `onTierSelected updates selectedTier`() {
        val vm = vmFor()
        vm.onWelcomeContinue()
        vm.onTierSelected(M1K3Tier.Mini)
        assertIs<M1K3Tier.Mini>(vm.uiState.value.selectedTier)
    }

    @Test
    fun `tier selection does not change recommendedTier`() {
        val vm = vmFor(deviceTier = DeviceTier.HIGH_END)
        vm.onWelcomeContinue()
        val recommended = vm.uiState.value.recommendedTier
        vm.onTierSelected(M1K3Tier.Mini)
        assertEquals(recommended, vm.uiState.value.recommendedTier)
    }

    // ===== Install → Awakening =====

    @Test
    fun `onInstallConfirmed transitions to Awakening`() = runTest {
        val vm = vmFor(
            deviceTier = DeviceTier.BUDGET,
            downloadFlow = flowOf(OnboardingDownloadState.Complete)
        )
        vm.onWelcomeContinue()
        vm.onInstallConfirmed()
        assertEquals(OnboardingStep.Awakening, vm.uiState.value.step)
    }

    // ===== Download flow =====

    @Test
    fun `successful download sets isComplete true`() = runTest {
        val vm = vmFor(
            downloadFlow = flowOf(OnboardingDownloadState.Complete)
        )
        vm.onWelcomeContinue()
        vm.onInstallConfirmed()
        assertTrue(vm.uiState.value.isComplete)
    }

    @Test
    fun `successful download sets downloadState to Complete`() = runTest {
        val vm = vmFor(
            downloadFlow = flowOf(OnboardingDownloadState.Complete)
        )
        vm.onWelcomeContinue()
        vm.onInstallConfirmed()
        assertIs<OnboardingDownloadState.Complete>(vm.uiState.value.downloadState)
    }

    @Test
    fun `failed download sets downloadState to Failed`() = runTest {
        val vm = vmFor(
            downloadFlow = flowOf(OnboardingDownloadState.Failed("Network error"))
        )
        vm.onWelcomeContinue()
        vm.onInstallConfirmed()
        val state = vm.uiState.value.downloadState as OnboardingDownloadState.Failed
        assertEquals("Network error", state.error)
    }

    @Test
    fun `in-progress download updates progressPercent`() = runTest {
        val vm = vmFor(
            downloadFlow = flowOf(
                OnboardingDownloadState.Downloading(50, 310, 620)
            )
        )
        vm.onWelcomeContinue()
        vm.onInstallConfirmed()
        val state = vm.uiState.value.downloadState as OnboardingDownloadState.Downloading
        assertEquals(50, state.progressPercent)
    }

    // ===== Preferences =====

    @Test
    fun `completed download persists ONBOARDING_COMPLETE`() = runTest {
        val prefs = fakePrefs()
        val vm = vmFor(
            prefs = prefs,
            downloadFlow = flowOf(OnboardingDownloadState.Complete)
        )
        vm.onWelcomeContinue()
        vm.onInstallConfirmed()
        assertTrue(prefs.getBoolean(PreferenceKeys.ONBOARDING_COMPLETE, false))
    }

    @Test
    fun `completed download persists Lil tier key for BUDGET device`() = runTest {
        val prefs = fakePrefs()
        val vm = vmFor(
            deviceTier = DeviceTier.BUDGET,
            prefs = prefs,
            downloadFlow = flowOf(OnboardingDownloadState.Complete)
        )
        vm.onWelcomeContinue()
        vm.onInstallConfirmed()
        assertEquals("lil", prefs.getString(PreferenceKeys.SELECTED_M1K3_TIER, null))
    }
}

// ---------------------------------------------------------------------------
// Test double
// ---------------------------------------------------------------------------

private class FakePreferences : PreferencesStoreInterface {
    private val bools = mutableMapOf<String, Boolean>()
    private val strings = mutableMapOf<String, String?>()
    private val ints = mutableMapOf<String, Int>()

    override fun getBoolean(key: String, default: Boolean) = bools[key] ?: default
    override fun setBoolean(key: String, value: Boolean) { bools[key] = value }
    override fun observeBoolean(key: String, default: Boolean) =
        kotlinx.coroutines.flow.flowOf(bools[key] ?: default)

    override fun getString(key: String, default: String?) = strings[key] ?: default
    override fun setString(key: String, value: String?) { strings[key] = value }

    override fun getInt(key: String, default: Int) = ints[key] ?: default
    override fun setInt(key: String, value: Int) { ints[key] = value }

    override fun contains(key: String) = bools.containsKey(key) ||
            strings.containsKey(key) || ints.containsKey(key)
    override fun remove(key: String) { bools.remove(key); strings.remove(key); ints.remove(key) }
    override fun clear() { bools.clear(); strings.clear(); ints.clear() }
}
