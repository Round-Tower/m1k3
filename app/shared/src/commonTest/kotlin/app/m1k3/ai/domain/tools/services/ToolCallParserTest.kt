package app.m1k3.ai.domain.tools.services

import app.m1k3.ai.domain.tools.ToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for ToolCallParser interface and DefaultToolCallParser implementation
 *
 * TDD: These tests define the contract for parsing tool calls from LLM output.
 */
class ToolCallParserTest {

    private val parser: ToolCallParser = DefaultToolCallParser()

    // ===== JSON Format Tests =====

    @Test
    fun `parses simple JSON tool call`() {
        val output = """{"tool": "get_battery_level"}"""

        val calls = parser.parse(output)

        assertEquals(1, calls.size)
        assertEquals("get_battery_level", calls[0].toolId)
        assertTrue(calls[0].arguments.isEmpty())
    }

    @Test
    fun `parses JSON tool call with args`() {
        val output = """{"tool": "toggle_flashlight", "args": {"enable": "true"}}"""

        val calls = parser.parse(output)

        assertEquals(1, calls.size)
        assertEquals("toggle_flashlight", calls[0].toolId)
        assertEquals("true", calls[0].arguments["enable"])
    }

    @Test
    fun `parses JSON tool call with multiple args`() {
        val output = """{"tool": "open_browser", "args": {"url": "https://example.com", "new_tab": "true"}}"""

        val calls = parser.parse(output)

        assertEquals(1, calls.size)
        assertEquals("open_browser", calls[0].toolId)
        assertEquals("https://example.com", calls[0].arguments["url"])
        assertEquals("true", calls[0].arguments["new_tab"])
    }

    @Test
    fun `parses JSON tool call embedded in text`() {
        val output = """I'll help you with that.
            {"tool": "toggle_flashlight", "args": {"enable": "true"}}
            Let me know if you need anything else."""

        val calls = parser.parse(output)

        assertEquals(1, calls.size)
        assertEquals("toggle_flashlight", calls[0].toolId)
    }

    // ===== XML-style Format Tests =====

    @Test
    fun `parses XML-style tool call`() {
        val output = """<tool_call>{"tool": "get_battery_level"}</tool_call>"""

        val calls = parser.parse(output)

        assertEquals(1, calls.size)
        assertEquals("get_battery_level", calls[0].toolId)
    }

    @Test
    fun `parses XML-style tool call with args`() {
        val output = """<tool_call>{"tool": "set_volume", "args": {"level": "75"}}</tool_call>"""

        val calls = parser.parse(output)

        assertEquals(1, calls.size)
        assertEquals("set_volume", calls[0].toolId)
        assertEquals("75", calls[0].arguments["level"])
    }

    @Test
    fun `parses XML-style tool call embedded in text`() {
        val output = """Sure, I can check your battery level.
            <tool_call>{"tool": "get_battery_level"}</tool_call>
            Here's the result..."""

        val calls = parser.parse(output)

        assertEquals(1, calls.size)
        assertEquals("get_battery_level", calls[0].toolId)
    }

    // ===== Multiple Tool Calls =====

    @Test
    fun `parses multiple tool calls`() {
        val output = """Let me do two things:
            {"tool": "toggle_flashlight", "args": {"enable": "true"}}
            {"tool": "get_battery_level"}"""

        val calls = parser.parse(output)

        assertEquals(2, calls.size)
        assertEquals("toggle_flashlight", calls[0].toolId)
        assertEquals("get_battery_level", calls[1].toolId)
    }

    // ===== hasToolCalls Tests =====

    @Test
    fun `hasToolCalls returns true for JSON`() {
        val output = """{"tool": "get_battery_level"}"""

        assertTrue(parser.hasToolCalls(output))
    }

    @Test
    fun `hasToolCalls returns true for XML-style`() {
        val output = """<tool_call>{"tool": "test"}</tool_call>"""

        assertTrue(parser.hasToolCalls(output))
    }

    @Test
    fun `hasToolCalls returns false for plain text`() {
        val output = "Just a normal response without any tools."

        assertFalse(parser.hasToolCalls(output))
    }

    @Test
    fun `hasToolCalls returns false for malformed JSON`() {
        val output = """{"not_a_tool": "value"}"""

        assertFalse(parser.hasToolCalls(output))
    }

    // ===== extractPlainText Tests =====

    @Test
    fun `extractPlainText removes JSON tool calls`() {
        val output = """Let me check that.
            {"tool": "get_battery_level"}
            Here's the result."""

        val plainText = parser.extractPlainText(output)

        assertTrue(plainText.contains("Let me check"))
        assertTrue(plainText.contains("Here's the result"))
        assertFalse(plainText.contains("get_battery_level"))
    }

    @Test
    fun `extractPlainText removes XML-style tool calls`() {
        val output = """Sure thing!
            <tool_call>{"tool": "test"}</tool_call>
            Done."""

        val plainText = parser.extractPlainText(output)

        assertTrue(plainText.contains("Sure thing"))
        assertTrue(plainText.contains("Done"))
        assertFalse(plainText.contains("tool_call"))
    }

    @Test
    fun `extractPlainText returns full text when no tools`() {
        val output = "Just a normal response."

        val plainText = parser.extractPlainText(output)

        assertEquals("Just a normal response.", plainText.trim())
    }

    // ===== Edge Cases =====

    @Test
    fun `handles empty output`() {
        val calls = parser.parse("")

        assertTrue(calls.isEmpty())
    }

    @Test
    fun `handles output with no tools`() {
        val output = "This is just a regular response with no tool calls."

        val calls = parser.parse(output)

        assertTrue(calls.isEmpty())
    }

    @Test
    fun `handles malformed JSON gracefully`() {
        val output = """{"tool": "broken"""

        val calls = parser.parse(output)

        assertTrue(calls.isEmpty())
    }

    @Test
    fun `preserves raw text in parsed ToolCall`() {
        val output = """{"tool": "get_battery_level"}"""

        val calls = parser.parse(output)

        assertTrue(calls[0].rawText.contains("get_battery_level"))
    }

    @Test
    fun `handles tool call with numeric args as strings`() {
        val output = """{"tool": "set_volume", "args": {"level": 75}}"""

        val calls = parser.parse(output)

        assertEquals(1, calls.size)
        assertEquals("75", calls[0].arguments["level"])
    }

    @Test
    fun `handles tool call with boolean args as strings`() {
        val output = """{"tool": "toggle_flashlight", "args": {"enable": true}}"""

        val calls = parser.parse(output)

        assertEquals(1, calls.size)
        assertEquals("true", calls[0].arguments["enable"])
    }

    // ===== Tokenization Artifact Tests (Small Model Quirks) =====

    @Test
    fun `handles spaced tokenization from Qwen models`() {
        // Real output from Qwen3.5 on device — spaces around JSON chars
        val output = """<tool_call> {" tool ": " get _battery _level ", " args ": {" }} </tool_call>"""

        val calls = parser.parse(output)

        assertEquals(1, calls.size, "Should parse spaced tool call, got: $calls")
        assertEquals("get_battery_level", calls[0].toolId)
    }

    @Test
    fun `handles spaced tool call with args`() {
        val output = """<tool_call> {" tool ": " web _search ", " args ": {" query ": " weather Cork "}} </tool_call>"""

        val calls = parser.parse(output)

        assertEquals(1, calls.size, "Should parse spaced tool call with args")
        assertEquals("web_search", calls[0].toolId)
    }

    @Test
    fun `hasToolCalls detects spaced format`() {
        val output = """< tool_call > {" tool ": " get _battery _level "} </ tool_call >"""

        assertTrue(parser.hasToolCalls(output), "Should detect spaced tool call")
    }
}
