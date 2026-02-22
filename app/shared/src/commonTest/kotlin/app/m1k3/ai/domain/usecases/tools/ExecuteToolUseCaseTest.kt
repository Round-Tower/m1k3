package app.m1k3.ai.domain.usecases.tools

import app.m1k3.ai.domain.tools.*
import app.m1k3.ai.domain.tools.services.ToolExecutor
import app.m1k3.ai.domain.tools.services.ToolRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

/**
 * Tests for ExecuteToolUseCase
 *
 * TDD: These tests define the orchestration contract for tool execution.
 */
class ExecuteToolUseCaseTest {

    // ===== Success Cases =====

    @Test
    fun `executes tool successfully`() = runTest {
        val registry = MockToolRegistry()
        registry.registerTool(
            createBatteryTool(),
            MockToolExecutor(
                toolId = "get_battery_level",
                available = true,
                result = ToolResult.Success(
                    toolId = "get_battery_level",
                    output = "Battery is at 75%",
                    data = mapOf("level" to 75),
                    executionTimeMs = 10
                )
            )
        )
        val useCase = ExecuteToolUseCase(registry)

        val result = useCase.execute(
            ToolCall("get_battery_level", emptyMap(), "")
        )

        assertIs<ToolResult.Success>(result)
        assertEquals("Battery is at 75%", result.output)
    }

    @Test
    fun `executes tool with arguments`() = runTest {
        val registry = MockToolRegistry()
        registry.registerTool(
            createFlashlightTool(),
            MockToolExecutor(
                toolId = "toggle_flashlight",
                available = true,
                result = ToolResult.Success(
                    toolId = "toggle_flashlight",
                    output = "Flashlight turned on",
                    executionTimeMs = 5
                )
            )
        )
        val useCase = ExecuteToolUseCase(registry)

        val result = useCase.execute(
            ToolCall("toggle_flashlight", mapOf("enable" to "true"), "")
        )

        assertIs<ToolResult.Success>(result)
        assertEquals("Flashlight turned on", result.output)
    }

    // ===== Failure Cases =====

    @Test
    fun `returns NotFound for unknown tool`() = runTest {
        val registry = MockToolRegistry()
        val useCase = ExecuteToolUseCase(registry)

        val result = useCase.execute(
            ToolCall("unknown_tool", emptyMap(), "")
        )

        assertIs<ToolResult.Failure>(result)
        assertIs<ToolError.NotFound>(result.error)
        assertEquals("unknown_tool", (result.error as ToolError.NotFound).toolId)
    }

    @Test
    fun `returns Unavailable when tool not available`() = runTest {
        val registry = MockToolRegistry()
        registry.registerTool(
            createFlashlightTool(),
            MockToolExecutor(
                toolId = "toggle_flashlight",
                available = false // Not available
            )
        )
        val useCase = ExecuteToolUseCase(registry)

        val result = useCase.execute(
            ToolCall("toggle_flashlight", mapOf("enable" to "true"), "")
        )

        assertIs<ToolResult.Failure>(result)
        assertIs<ToolError.Unavailable>(result.error)
    }

    @Test
    fun `returns InvalidArguments for validation failure`() = runTest {
        val registry = MockToolRegistry()
        registry.registerTool(
            createVolumeTool(),
            MockToolExecutor(
                toolId = "set_volume",
                available = true,
                validationError = "level must be 0-100"
            )
        )
        val useCase = ExecuteToolUseCase(registry)

        val result = useCase.execute(
            ToolCall("set_volume", mapOf("level" to "150"), "")
        )

        assertIs<ToolResult.Failure>(result)
        assertIs<ToolError.InvalidArguments>(result.error)
        assertTrue((result.error as ToolError.InvalidArguments).message.contains("0-100"))
    }

    // ===== Confirmation Cases =====

    @Test
    fun `returns RequiresConfirmation for sensitive tools`() = runTest {
        val registry = MockToolRegistry()
        registry.registerTool(
            createWriteNoteTool(), // Has requiresConfirmation = true
            MockToolExecutor(
                toolId = "write_note",
                available = true
            )
        )
        val useCase = ExecuteToolUseCase(registry)

        val result = useCase.execute(
            ToolCall("write_note", mapOf("content" to "Hello"), "")
        )

        assertIs<ToolResult.RequiresConfirmation>(result)
        assertEquals("write_note", result.toolId)
        assertTrue(result.confirmationPrompt.isNotBlank())
    }

    @Test
    fun `executes with confirmation when flag is set`() = runTest {
        val registry = MockToolRegistry()
        registry.registerTool(
            createWriteNoteTool(),
            MockToolExecutor(
                toolId = "write_note",
                available = true,
                result = ToolResult.Success(
                    toolId = "write_note",
                    output = "Note saved",
                    executionTimeMs = 20
                )
            )
        )
        val useCase = ExecuteToolUseCase(registry)

        val result = useCase.execute(
            ToolCall("write_note", mapOf("content" to "Hello"), ""),
            confirmed = true
        )

        assertIs<ToolResult.Success>(result)
        assertEquals("Note saved", result.output)
    }

    // ===== Execution Time Tracking =====

    @Test
    fun `tracks execution time`() = runTest {
        val registry = MockToolRegistry()
        registry.registerTool(
            createBatteryTool(),
            MockToolExecutor(
                toolId = "get_battery_level",
                available = true,
                result = ToolResult.Success(
                    toolId = "get_battery_level",
                    output = "75%",
                    executionTimeMs = 50
                )
            )
        )
        val useCase = ExecuteToolUseCase(registry)

        val result = useCase.execute(
            ToolCall("get_battery_level", emptyMap(), "")
        )

        assertTrue(result.executionTimeMs >= 0)
    }

    // ===== Helper Functions =====

    private fun createBatteryTool() = Tool(
        id = "get_battery_level",
        name = "Battery Level",
        description = "Gets the current battery level",
        parameters = emptyList(),
        category = ToolCategory.DEVICE_INFO
    )

    private fun createFlashlightTool() = Tool(
        id = "toggle_flashlight",
        name = "Flashlight",
        description = "Toggle device flashlight",
        parameters = listOf(
            ToolParameter(
                name = "enable",
                type = ParameterType.BOOLEAN,
                description = "Turn on or off",
                required = true
            )
        ),
        category = ToolCategory.SYSTEM
    )

    private fun createVolumeTool() = Tool(
        id = "set_volume",
        name = "Set Volume",
        description = "Set device volume",
        parameters = listOf(
            ToolParameter(
                name = "level",
                type = ParameterType.NUMBER,
                description = "Volume level 0-100",
                required = true
            )
        ),
        category = ToolCategory.SYSTEM
    )

    private fun createWriteNoteTool() = Tool(
        id = "write_note",
        name = "Write Note",
        description = "Write content to a note",
        parameters = listOf(
            ToolParameter(
                name = "content",
                type = ParameterType.STRING,
                description = "Content to write",
                required = true
            )
        ),
        category = ToolCategory.FILES,
        requiresConfirmation = true
    )

    // ===== Mock Implementations =====

    private class MockToolRegistry : ToolRegistry {
        private val tools = mutableMapOf<String, Tool>()
        private val executors = mutableMapOf<String, ToolExecutor>()

        override fun getAllTools(): List<Tool> = tools.values.toList()

        override fun getToolsByCategory(category: ToolCategory): List<Tool> =
            tools.values.filter { it.category == category }

        override suspend fun getAvailableTools(): List<Tool> =
            tools.values.filter { executors[it.id]?.isAvailable() == true }

        override suspend fun getRelevantTools(query: String, maxTools: Int): List<Tool> =
            getAvailableTools().take(maxTools)  // Simplified for testing

        override fun findTool(toolId: String): Tool? = tools[toolId]

        override suspend fun isToolAvailable(toolId: String): Boolean =
            executors[toolId]?.isAvailable() == true

        override fun registerTool(tool: Tool, executor: ToolExecutor) {
            tools[tool.id] = tool
            executors[tool.id] = executor
        }

        override fun getExecutor(toolId: String): ToolExecutor? = executors[toolId]
    }

    private class MockToolExecutor(
        override val toolId: String,
        private val available: Boolean = true,
        private val validationError: String? = null,
        private val result: ToolResult? = null
    ) : ToolExecutor {

        override suspend fun isAvailable(): Boolean = available

        override suspend fun execute(call: ToolCall): ToolResult {
            return result ?: ToolResult.Success(
                toolId = toolId,
                output = "Mock executed",
                executionTimeMs = 1
            )
        }

        override fun validateArguments(arguments: Map<String, String>): Result<Unit> {
            return if (validationError != null) {
                Result.failure(IllegalArgumentException(validationError))
            } else {
                Result.success(Unit)
            }
        }
    }
}
