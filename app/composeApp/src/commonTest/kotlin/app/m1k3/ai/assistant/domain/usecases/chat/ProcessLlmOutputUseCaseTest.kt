package app.m1k3.ai.assistant.domain.usecases.chat

import app.m1k3.ai.assistant.domain.tools.Tool
import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolCategory
import app.m1k3.ai.assistant.domain.tools.ToolError
import app.m1k3.ai.assistant.domain.tools.ToolResult
import app.m1k3.ai.assistant.domain.tools.services.ToolCallParser
import app.m1k3.ai.assistant.domain.tools.services.ToolExecutor
import app.m1k3.ai.assistant.domain.tools.services.ToolRegistry
import app.m1k3.ai.assistant.domain.usecases.tools.ExecuteToolUseCase
import app.m1k3.ai.assistant.domain.usecases.tools.ParseToolCallUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProcessLlmOutputUseCaseTest {

    // ===== Test Fixtures =====

    private val batteryTool = Tool(
        id = "get_battery_level",
        name = "Get Battery",
        description = "Gets battery level",
        parameters = emptyList(),
        category = ToolCategory.DEVICE_INFO
    )

    private val flashlightTool = Tool(
        id = "toggle_flashlight",
        name = "Flashlight",
        description = "Toggle flashlight",
        parameters = emptyList(),
        category = ToolCategory.SYSTEM
    )

    // ===== Mock Implementations =====

    private class MockToolCallParser(
        private val toolCalls: List<ToolCall> = emptyList(),
        private val plainText: String = ""
    ) : ToolCallParser {
        var lastInput: String? = null

        override fun parse(output: String): List<ToolCall> {
            lastInput = output
            return toolCalls
        }

        override fun hasToolCalls(output: String): Boolean = toolCalls.isNotEmpty()
        override fun extractPlainText(output: String): String = plainText
    }

    private class MockToolRegistry(
        private val tools: Map<String, Tool> = emptyMap(),
        private val executors: Map<String, ToolExecutor> = emptyMap()
    ) : ToolRegistry {
        override fun getAllTools(): List<Tool> = tools.values.toList()
        override fun findTool(toolId: String): Tool? = tools[toolId]
        override fun getExecutor(toolId: String): ToolExecutor? = executors[toolId]
        override suspend fun getAvailableTools(): List<Tool> = tools.values.toList()
        override fun getToolsByCategory(category: ToolCategory): List<Tool> =
            tools.values.filter { it.category == category }
        override suspend fun isToolAvailable(toolId: String): Boolean =
            executors[toolId]?.isAvailable() ?: false
        override fun registerTool(tool: Tool, executor: ToolExecutor) {
            // No-op for mock
        }
    }

    private class MockToolExecutor(
        override val toolId: String,
        private val result: ToolResult? = null,
        private val available: Boolean = true
    ) : ToolExecutor {
        var executedCalls = mutableListOf<ToolCall>()

        override suspend fun execute(call: ToolCall): ToolResult {
            executedCalls.add(call)
            return result ?: ToolResult.Success(
                toolId = toolId,
                output = "Executed $toolId",
                executionTimeMs = 10
            )
        }

        override suspend fun isAvailable(): Boolean = available
        override fun validateArguments(arguments: Map<String, String>): Result<Unit> = Result.success(Unit)
    }

    // ===== Helper to build UseCase =====

    private fun buildUseCase(
        parser: ToolCallParser,
        registry: ToolRegistry
    ): ProcessLlmOutputUseCase {
        val parseUseCase = ParseToolCallUseCase(parser)
        val executeUseCase = ExecuteToolUseCase(registry)
        return ProcessLlmOutputUseCase(parseUseCase, executeUseCase)
    }

    // ===== Tests: Text Only Output =====

    @Test
    fun `returns TextOnly when no tool calls in output`() = runTest {
        val parser = MockToolCallParser(
            toolCalls = emptyList(),
            plainText = "Hello! How can I help you today?"
        )
        val registry = MockToolRegistry()
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("Hello! How can I help you today?")

        assertIs<ProcessedOutput.TextOnly>(result)
        assertEquals("Hello! How can I help you today?", result.text)
    }

    @Test
    fun `returns TextOnly with empty string for empty output`() = runTest {
        val parser = MockToolCallParser(toolCalls = emptyList(), plainText = "")
        val registry = MockToolRegistry()
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("")

        assertIs<ProcessedOutput.TextOnly>(result)
        assertEquals("", result.text)
    }

    // ===== Tests: Single Tool Execution =====

    @Test
    fun `executes single tool call and returns WithTools`() = runTest {
        val toolCall = ToolCall("get_battery_level", emptyMap(), "")
        val parser = MockToolCallParser(
            toolCalls = listOf(toolCall),
            plainText = "Let me check your battery."
        )
        val executor = MockToolExecutor(
            toolId = "get_battery_level",
            result = ToolResult.Success(
                toolId = "get_battery_level",
                output = "Battery is at 75%",
                executionTimeMs = 5
            )
        )
        val registry = MockToolRegistry(
            tools = mapOf("get_battery_level" to batteryTool),
            executors = mapOf("get_battery_level" to executor)
        )
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("""Let me check your battery. {"tool": "get_battery_level"}""")

        assertIs<ProcessedOutput.WithTools>(result)
        assertEquals("Let me check your battery.", result.plainText)
        assertEquals(1, result.toolResults.size)
        assertIs<ToolResult.Success>(result.toolResults[0])
        assertEquals("Battery is at 75%", (result.toolResults[0] as ToolResult.Success).output)
    }

    @Test
    fun `includes tool call in result even when tool not found`() = runTest {
        val toolCall = ToolCall("unknown_tool", emptyMap(), "")
        val parser = MockToolCallParser(
            toolCalls = listOf(toolCall),
            plainText = "Trying unknown tool."
        )
        val registry = MockToolRegistry() // Empty registry
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("""{"tool": "unknown_tool"}""")

        assertIs<ProcessedOutput.WithTools>(result)
        assertEquals(1, result.toolResults.size)
        assertIs<ToolResult.Failure>(result.toolResults[0])
        val failure = result.toolResults[0] as ToolResult.Failure
        assertIs<ToolError.NotFound>(failure.error)
    }

    // ===== Tests: Multiple Tool Execution =====

    @Test
    fun `executes multiple tool calls in order`() = runTest {
        val batteryCall = ToolCall("get_battery_level", emptyMap(), "")
        val flashlightCall = ToolCall("toggle_flashlight", mapOf("enable" to "true"), "")
        val parser = MockToolCallParser(
            toolCalls = listOf(batteryCall, flashlightCall),
            plainText = "Checking battery and turning on flashlight."
        )
        val batteryExecutor = MockToolExecutor(
            toolId = "get_battery_level",
            result = ToolResult.Success("get_battery_level", "75%", null, 5)
        )
        val flashlightExecutor = MockToolExecutor(
            toolId = "toggle_flashlight",
            result = ToolResult.Success("toggle_flashlight", "Flashlight on", null, 5)
        )
        val registry = MockToolRegistry(
            tools = mapOf(
                "get_battery_level" to batteryTool,
                "toggle_flashlight" to flashlightTool
            ),
            executors = mapOf(
                "get_battery_level" to batteryExecutor,
                "toggle_flashlight" to flashlightExecutor
            )
        )
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("multi-tool output")

        assertIs<ProcessedOutput.WithTools>(result)
        assertEquals(2, result.toolResults.size)
        assertEquals("get_battery_level", result.toolResults[0].toolId)
        assertEquals("toggle_flashlight", result.toolResults[1].toolId)
    }

    @Test
    fun `continues executing remaining tools when one fails`() = runTest {
        val unknownCall = ToolCall("unknown", emptyMap(), "")
        val batteryCall = ToolCall("get_battery_level", emptyMap(), "")
        val parser = MockToolCallParser(
            toolCalls = listOf(unknownCall, batteryCall),
            plainText = ""
        )
        val batteryExecutor = MockToolExecutor(
            toolId = "get_battery_level",
            result = ToolResult.Success("get_battery_level", "75%", null, 5)
        )
        val registry = MockToolRegistry(
            tools = mapOf("get_battery_level" to batteryTool),
            executors = mapOf("get_battery_level" to batteryExecutor)
        )
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("mixed results")

        assertIs<ProcessedOutput.WithTools>(result)
        assertEquals(2, result.toolResults.size)
        assertIs<ToolResult.Failure>(result.toolResults[0]) // unknown failed
        assertIs<ToolResult.Success>(result.toolResults[1]) // battery succeeded
    }

    // ===== Tests: Confirmation Required =====

    @Test
    fun `returns RequiresConfirmation when tool needs confirmation`() = runTest {
        val writeCall = ToolCall("write_note", mapOf("content" to "Hello"), "")
        val parser = MockToolCallParser(
            toolCalls = listOf(writeCall),
            plainText = "I'll create that note for you."
        )
        val writeTool = Tool(
            id = "write_note",
            name = "Write Note",
            description = "Writes a note",
            parameters = emptyList(),
            category = ToolCategory.FILES,
            requiresConfirmation = true
        )
        val writeExecutor = MockToolExecutor(toolId = "write_note")
        val registry = MockToolRegistry(
            tools = mapOf("write_note" to writeTool),
            executors = mapOf("write_note" to writeExecutor)
        )
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("""{"tool": "write_note", "args": {"content": "Hello"}}""")

        assertIs<ProcessedOutput.WithTools>(result)
        assertEquals(1, result.toolResults.size)
        assertIs<ToolResult.RequiresConfirmation>(result.toolResults[0])
    }

    // ===== Tests: Edge Cases =====

    @Test
    fun `handles whitespace-only plain text`() = runTest {
        val parser = MockToolCallParser(toolCalls = emptyList(), plainText = "   \n\t  ")
        val registry = MockToolRegistry()
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("   \n\t  ")

        assertIs<ProcessedOutput.TextOnly>(result)
        assertEquals("   \n\t  ", result.text)
    }

    @Test
    fun `preserves plain text alongside tool results`() = runTest {
        val toolCall = ToolCall("get_battery_level", emptyMap(), "")
        val parser = MockToolCallParser(
            toolCalls = listOf(toolCall),
            plainText = "Here's your battery level:"
        )
        val executor = MockToolExecutor(
            toolId = "get_battery_level",
            result = ToolResult.Success("get_battery_level", "75%", null, 5)
        )
        val registry = MockToolRegistry(
            tools = mapOf("get_battery_level" to batteryTool),
            executors = mapOf("get_battery_level" to executor)
        )
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("output with text and tool")

        assertIs<ProcessedOutput.WithTools>(result)
        assertEquals("Here's your battery level:", result.plainText)
        assertTrue(result.toolResults.isNotEmpty())
    }

    // ===== Tests: Result Formatting =====

    @Test
    fun `hasToolResults returns true when tools were executed`() = runTest {
        val toolCall = ToolCall("get_battery_level", emptyMap(), "")
        val parser = MockToolCallParser(toolCalls = listOf(toolCall), plainText = "")
        val executor = MockToolExecutor(toolId = "get_battery_level")
        val registry = MockToolRegistry(
            tools = mapOf("get_battery_level" to batteryTool),
            executors = mapOf("get_battery_level" to executor)
        )
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("tool output")

        assertTrue(result.hasToolResults)
    }

    @Test
    fun `hasToolResults returns false for text only`() = runTest {
        val parser = MockToolCallParser(toolCalls = emptyList(), plainText = "Just text")
        val registry = MockToolRegistry()
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("Just text")

        assertTrue(!result.hasToolResults)
    }

    @Test
    fun `allToolsSucceeded returns true when all tools succeed`() = runTest {
        val call1 = ToolCall("get_battery_level", emptyMap(), "")
        val call2 = ToolCall("toggle_flashlight", emptyMap(), "")
        val parser = MockToolCallParser(toolCalls = listOf(call1, call2), plainText = "")
        val exec1 = MockToolExecutor(
            toolId = "get_battery_level",
            result = ToolResult.Success("get_battery_level", "75%", null, 5)
        )
        val exec2 = MockToolExecutor(
            toolId = "toggle_flashlight",
            result = ToolResult.Success("toggle_flashlight", "On", null, 5)
        )
        val registry = MockToolRegistry(
            tools = mapOf("get_battery_level" to batteryTool, "toggle_flashlight" to flashlightTool),
            executors = mapOf("get_battery_level" to exec1, "toggle_flashlight" to exec2)
        )
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("multi success")

        assertIs<ProcessedOutput.WithTools>(result)
        assertTrue(result.allToolsSucceeded)
    }

    @Test
    fun `allToolsSucceeded returns false when any tool fails`() = runTest {
        val call1 = ToolCall("get_battery_level", emptyMap(), "")
        val call2 = ToolCall("unknown", emptyMap(), "")
        val parser = MockToolCallParser(toolCalls = listOf(call1, call2), plainText = "")
        val exec1 = MockToolExecutor(
            toolId = "get_battery_level",
            result = ToolResult.Success("get_battery_level", "75%", null, 5)
        )
        val registry = MockToolRegistry(
            tools = mapOf("get_battery_level" to batteryTool),
            executors = mapOf("get_battery_level" to exec1)
        )
        val useCase = buildUseCase(parser, registry)

        val result = useCase.execute("mixed results")

        assertIs<ProcessedOutput.WithTools>(result)
        assertTrue(!result.allToolsSucceeded)
    }
}
