package app.m1k3.ai.domain.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ToolCall domain entity
 *
 * TDD: These tests define the contract for ToolCall.
 */
class ToolCallTest {

    @Test
    fun `ToolCall with no arguments`() {
        val call = ToolCall(
            toolId = "open_camera",
            arguments = emptyMap(),
            rawText = """{"tool": "open_camera"}"""
        )

        assertEquals("open_camera", call.toolId)
        assertTrue(call.arguments.isEmpty())
    }

    @Test
    fun `ToolCall with arguments`() {
        val call = ToolCall(
            toolId = "toggle_flashlight",
            arguments = mapOf("enable" to "true"),
            rawText = """{"tool": "toggle_flashlight", "args": {"enable": "true"}}"""
        )

        assertEquals("toggle_flashlight", call.toolId)
        assertEquals("true", call.arguments["enable"])
    }

    @Test
    fun `getArgument returns value when present`() {
        val call = ToolCall(
            toolId = "set_volume",
            arguments = mapOf("level" to "75"),
            rawText = ""
        )

        assertEquals("75", call.getArgument("level"))
    }

    @Test
    fun `getArgument returns null when absent`() {
        val call = ToolCall(
            toolId = "set_volume",
            arguments = mapOf("level" to "75"),
            rawText = ""
        )

        assertNull(call.getArgument("missing"))
    }

    @Test
    fun `getArgumentOrDefault returns value when present`() {
        val call = ToolCall(
            toolId = "open_browser",
            arguments = mapOf("url" to "https://example.com"),
            rawText = ""
        )

        assertEquals("https://example.com", call.getArgumentOrDefault("url", "https://google.com"))
    }

    @Test
    fun `getArgumentOrDefault returns default when absent`() {
        val call = ToolCall(
            toolId = "open_browser",
            arguments = emptyMap(),
            rawText = ""
        )

        assertEquals("https://google.com", call.getArgumentOrDefault("url", "https://google.com"))
    }

    @Test
    fun `ToolCall preserves raw text for debugging`() {
        val rawJson = """{"tool": "get_battery_level", "args": {}}"""
        val call = ToolCall(
            toolId = "get_battery_level",
            arguments = emptyMap(),
            rawText = rawJson
        )

        assertEquals(rawJson, call.rawText)
    }
}
