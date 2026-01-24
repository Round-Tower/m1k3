package app.m1k3.ai.assistant.chat

import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.mocks.MockBaseLlmEngine
import app.m1k3.ai.assistant.mocks.MockDeviceInfoProvider
import app.m1k3.ai.assistant.mocks.TestPreferencesStore
import app.m1k3.ai.assistant.platform.PreferenceKeys
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.ToolCategory
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolExecutor
import app.m1k3.ai.domain.tools.services.ToolRegistry
import app.m1k3.ai.domain.usecases.chat.LlmOutputProcessor
import app.m1k3.ai.domain.usecases.chat.ProcessedOutput
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ChatScreenViewModel tool calling preference behavior.
 *
 * Verifies that:
 * - Tools can be enabled/disabled via preferences
 * - Tool calling respects the TOOLS_ENABLED preference
 * - Legacy path is used when tools are disabled
 */
class ChatScreenViewModelToolsTest {

    private val testScope = TestScope()

    // ===== isToolCallingEnabled Tests =====

    @Test
    fun `isToolCallingEnabled returns false when TOOLS_ENABLED preference is false`() {
        val prefs = TestPreferencesStore()
        prefs.setBoolean(PreferenceKeys.TOOLS_ENABLED, false)

        val viewModel = createViewModel(
            preferences = prefs,
            toolRegistry = FakeToolRegistry(),
            processLlmOutput = FakeLlmOutputProcessor()
        )

        assertFalse(viewModel.isToolCallingEnabled)
    }

    @Test
    fun `isToolCallingEnabled returns true when TOOLS_ENABLED is true and registry provided`() {
        val prefs = TestPreferencesStore()
        prefs.setBoolean(PreferenceKeys.TOOLS_ENABLED, true)

        val viewModel = createViewModel(
            preferences = prefs,
            toolRegistry = FakeToolRegistry(),
            processLlmOutput = FakeLlmOutputProcessor()
        )

        assertTrue(viewModel.isToolCallingEnabled)
    }

    @Test
    fun `isToolCallingEnabled returns true by default when registry provided`() {
        // Default should be true (opt-out model)
        val prefs = TestPreferencesStore()

        val viewModel = createViewModel(
            preferences = prefs,
            toolRegistry = FakeToolRegistry(),
            processLlmOutput = FakeLlmOutputProcessor()
        )

        assertTrue(viewModel.isToolCallingEnabled)
    }

    @Test
    fun `isToolCallingEnabled returns false when toolRegistry is null`() {
        val prefs = TestPreferencesStore()
        prefs.setBoolean(PreferenceKeys.TOOLS_ENABLED, true)

        val viewModel = createViewModel(
            preferences = prefs,
            toolRegistry = null,
            processLlmOutput = FakeLlmOutputProcessor()
        )

        assertFalse(viewModel.isToolCallingEnabled)
    }

    @Test
    fun `isToolCallingEnabled returns false when processLlmOutput is null`() {
        val prefs = TestPreferencesStore()
        prefs.setBoolean(PreferenceKeys.TOOLS_ENABLED, true)

        val viewModel = createViewModel(
            preferences = prefs,
            toolRegistry = FakeToolRegistry(),
            processLlmOutput = null
        )

        assertFalse(viewModel.isToolCallingEnabled)
    }

    // ===== Helper Methods =====

    private fun createViewModel(
        preferences: TestPreferencesStore,
        toolRegistry: ToolRegistry? = null,
        processLlmOutput: LlmOutputProcessor? = null
    ): ChatScreenViewModel {
        val database = TestDatabaseFactory.createInMemoryDatabase()
        return ChatScreenViewModel(
            aiEngine = MockBaseLlmEngine(),
            conversationRepo = ConversationRepository(database),
            ecoMetricsRepo = EcoMetricsRepository(database),
            database = database,
            deviceInfo = MockDeviceInfoProvider.midRange(),
            preferences = preferences,
            scope = testScope,
            projectId = "test_project",
            memoryManager = null,
            ragManager = null,
            toolRegistry = toolRegistry,
            processLlmOutput = processLlmOutput
        )
    }

    // ===== Test Doubles =====

    private class FakeToolRegistry : ToolRegistry {
        override fun getAllTools(): List<Tool> = emptyList()
        override fun getToolsByCategory(category: ToolCategory): List<Tool> = emptyList()
        override suspend fun getAvailableTools(): List<Tool> = emptyList()
        override fun findTool(toolId: String): Tool? = null
        override suspend fun isToolAvailable(toolId: String): Boolean = false
        override fun registerTool(tool: Tool, executor: ToolExecutor) {}
        override fun getExecutor(toolId: String): ToolExecutor? = null
    }

    private class FakeLlmOutputProcessor : LlmOutputProcessor {
        override suspend fun execute(llmOutput: String, confirmedToolIds: Set<String>): ProcessedOutput {
            return ProcessedOutput.TextOnly(llmOutput)
        }

        override fun hasToolCalls(llmOutput: String): Boolean = false

        override fun extractPlainText(llmOutput: String): String = llmOutput
    }
}
