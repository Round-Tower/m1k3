package app.m1k3.ai.assistant.chat.usecase

import app.m1k3.ai.assistant.config.GenerationConstants
import app.m1k3.ai.assistant.mocks.MockDeviceInfoProvider
import app.m1k3.ai.assistant.mocks.TestPreferencesStore
import app.m1k3.ai.assistant.platform.PreferenceKeys
import app.m1k3.ai.domain.chat.EnrichedContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ContextRetrievalUseCase.
 *
 * Tests context retrieval behavior with various configurations.
 * Integration with RAGManager/MemoryManager tested separately.
 */
class ContextRetrievalUseCaseTest {

    // ===== EnrichedContext Tests =====

    @Test
    fun `EnrichedContext hasContext returns true when hasRagContext is true`() {
        val context = EnrichedContext(
            context = "Some context",
            intentCategory = "SCIENCE",
            ragInfo = null,
            ragSources = null,
            ragConfidence = null,
            hasRagContext = true,
            hasMemoryContext = false
        )
        assertTrue(context.hasContext)
    }

    @Test
    fun `EnrichedContext hasContext returns true when hasMemoryContext is true`() {
        val context = EnrichedContext(
            context = "Some context",
            intentCategory = "GENERAL",
            ragInfo = null,
            ragSources = null,
            ragConfidence = null,
            hasRagContext = false,
            hasMemoryContext = true
        )
        assertTrue(context.hasContext)
    }

    @Test
    fun `EnrichedContext hasContext returns false when no context retrieved`() {
        val context = EnrichedContext(
            context = "",
            intentCategory = "GENERAL",
            ragInfo = null,
            ragSources = null,
            ragConfidence = null,
            hasRagContext = false,
            hasMemoryContext = false
        )
        assertFalse(context.hasContext)
    }

    @Test
    fun `EnrichedContext isEmpty returns true for empty context`() {
        val context = EnrichedContext(
            context = "",
            intentCategory = "GENERAL",
            ragInfo = null,
            ragSources = null,
            ragConfidence = null,
            hasRagContext = false,
            hasMemoryContext = false
        )
        assertTrue(context.isEmpty)
    }

    @Test
    fun `EnrichedContext isEmpty returns false for non-empty context`() {
        val context = EnrichedContext(
            context = "Some content",
            intentCategory = "SCIENCE",
            ragInfo = null,
            ragSources = null,
            ragConfidence = null,
            hasRagContext = true,
            hasMemoryContext = false
        )
        assertFalse(context.isEmpty)
    }

    // ===== Use Case Preference Tests =====

    @Test
    fun `isRagEnabled returns true by default`() {
        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.midRange(),
            preferences = TestPreferencesStore()
        )
        assertTrue(useCase.isRagEnabled())
    }

    @Test
    fun `isRagEnabled returns false when disabled in preferences`() {
        val prefs = TestPreferencesStore()
        prefs.setBoolean(PreferenceKeys.RAG_ENABLED, false)

        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.midRange(),
            preferences = prefs
        )
        assertFalse(useCase.isRagEnabled())
    }

    @Test
    fun `isRagEnabled returns true when enabled in preferences`() {
        val prefs = TestPreferencesStore()
        prefs.setBoolean(PreferenceKeys.RAG_ENABLED, true)

        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.midRange(),
            preferences = prefs
        )
        assertTrue(useCase.isRagEnabled())
    }

    // ===== Device-Adaptive Tests =====

    @Test
    fun `getMemoryTopK returns FLAGSHIP value for flagship device`() {
        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.flagship(),
            preferences = TestPreferencesStore()
        )
        assertEquals(GenerationConstants.MemoryTopK.FLAGSHIP, useCase.getMemoryTopK())
    }

    @Test
    fun `getMemoryTopK returns HIGH_END value for high-end device`() {
        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.highEnd(),
            preferences = TestPreferencesStore()
        )
        assertEquals(GenerationConstants.MemoryTopK.HIGH_END, useCase.getMemoryTopK())
    }

    @Test
    fun `getMemoryTopK returns MID_RANGE value for mid-range device`() {
        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.midRange(),
            preferences = TestPreferencesStore()
        )
        assertEquals(GenerationConstants.MemoryTopK.MID_RANGE, useCase.getMemoryTopK())
    }

    @Test
    fun `getMemoryTopK returns BUDGET value for budget device`() {
        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.budget(),
            preferences = TestPreferencesStore()
        )
        assertEquals(GenerationConstants.MemoryTopK.BUDGET, useCase.getMemoryTopK())
    }

    // ===== Context Retrieval Tests (without managers) =====

    @Test
    fun `retrieveContext returns empty context when no managers available`() = runTest {
        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.midRange(),
            preferences = TestPreferencesStore(),
            ragEnricher = null,
            memoryManager = null
        )

        val result = useCase.retrieveContext("What is photosynthesis?")

        assertTrue(result.isEmpty)
        // Intent is now classified even when RAG is not available
        assertEquals("Science Facts", result.intentCategory)
        assertFalse(result.hasRagContext)
        assertFalse(result.hasMemoryContext)
        assertNull(result.ragInfo)
        assertNull(result.ragSources)
        assertNull(result.ragConfidence)
    }

    @Test
    fun `retrieveContext returns empty context when RAG disabled`() = runTest {
        val prefs = TestPreferencesStore()
        prefs.setBoolean(PreferenceKeys.RAG_ENABLED, false)

        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.midRange(),
            preferences = prefs,
            ragEnricher = null,
            memoryManager = null
        )

        val result = useCase.retrieveContext("Test query")

        assertTrue(result.isEmpty)
        assertFalse(result.hasRagContext)
    }

    @Test
    fun `retrieveContext classifies conversational intent correctly`() = runTest {
        val useCase = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.midRange(),
            preferences = TestPreferencesStore()
        )

        val result = useCase.retrieveContext("Hello")

        // "Hello" is classified as CONVERSATIONAL, not GENERAL
        assertEquals("Casual Conversation", result.intentCategory)
    }

    // ===== EnrichedContext Field Validation Tests =====

    @Test
    fun `EnrichedContext stores all provided values`() {
        val context = EnrichedContext(
            context = "Test context",
            intentCategory = "CODE_DEBUG",
            ragInfo = "✅ CODE_DEBUG (80%) • 2 facts",
            ragSources = "source1; source2",
            ragConfidence = 0.8,
            hasRagContext = true,
            hasMemoryContext = true
        )

        assertEquals("Test context", context.context)
        assertEquals("CODE_DEBUG", context.intentCategory)
        assertEquals("✅ CODE_DEBUG (80%) • 2 facts", context.ragInfo)
        assertEquals("source1; source2", context.ragSources)
        assertEquals(0.8, context.ragConfidence)
        assertTrue(context.hasRagContext)
        assertTrue(context.hasMemoryContext)
        assertTrue(context.hasContext)
        assertFalse(context.isEmpty)
    }

    @Test
    fun `EnrichedContext handles null optional values`() {
        val context = EnrichedContext(
            context = "",
            intentCategory = "GENERAL",
            ragInfo = null,
            ragSources = null,
            ragConfidence = null,
            hasRagContext = false,
            hasMemoryContext = false
        )

        assertNull(context.ragInfo)
        assertNull(context.ragSources)
        assertNull(context.ragConfidence)
    }

    // ===== Memory TopK Scaling Tests =====

    @Test
    fun `getMemoryTopK scales correctly across device tiers`() {
        val flagship = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.flagship(),
            preferences = TestPreferencesStore()
        ).getMemoryTopK()

        val highEnd = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.highEnd(),
            preferences = TestPreferencesStore()
        ).getMemoryTopK()

        val midRange = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.midRange(),
            preferences = TestPreferencesStore()
        ).getMemoryTopK()

        val budget = ContextRetrievalUseCase(
            deviceInfo = MockDeviceInfoProvider.budget(),
            preferences = TestPreferencesStore()
        ).getMemoryTopK()

        assertTrue(flagship > highEnd, "Flagship should have higher topK than high-end")
        assertTrue(highEnd > midRange, "High-end should have higher topK than mid-range")
        assertTrue(midRange > budget, "Mid-range should have higher topK than budget")
    }
}
