package app.m1k3.ai.domain.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Tests for ToolResult sealed class hierarchy
 *
 * TDD: These tests define the contract for ToolResult and ToolError.
 */
class ToolResultTest {

    // ===== ToolResult.Success Tests =====

    @Test
    fun `Success result contains output`() {
        val result = ToolResult.Success(
            toolId = "get_battery_level",
            output = "Battery is at 75%",
            executionTimeMs = 15
        )

        assertEquals("get_battery_level", result.toolId)
        assertEquals("Battery is at 75%", result.output)
        assertEquals(15, result.executionTimeMs)
    }

    @Test
    fun `Success result can contain structured data`() {
        val result = ToolResult.Success(
            toolId = "get_battery_level",
            output = "Battery is at 75%",
            data = mapOf("level" to 75, "charging" to true),
            executionTimeMs = 15
        )

        assertEquals(75, result.data?.get("level"))
        assertEquals(true, result.data?.get("charging"))
    }

    @Test
    fun `Success result isSuccess returns true`() {
        val result: ToolResult = ToolResult.Success(
            toolId = "test",
            output = "done",
            executionTimeMs = 10
        )

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
    }

    // ===== ToolResult.Failure Tests =====

    @Test
    fun `Failure result with NotFound error`() {
        val result = ToolResult.Failure(
            toolId = "unknown_tool",
            error = ToolError.NotFound("unknown_tool"),
            executionTimeMs = 5
        )

        assertEquals("unknown_tool", result.toolId)
        assertIs<ToolError.NotFound>(result.error)
        assertEquals("unknown_tool", (result.error as ToolError.NotFound).toolId)
    }

    @Test
    fun `Failure result with InvalidArguments error`() {
        val result = ToolResult.Failure(
            toolId = "set_volume",
            error = ToolError.InvalidArguments("level must be 0-100"),
            executionTimeMs = 3
        )

        assertIs<ToolError.InvalidArguments>(result.error)
        assertEquals("level must be 0-100", (result.error as ToolError.InvalidArguments).message)
    }

    @Test
    fun `Failure result with PermissionDenied error`() {
        val result = ToolResult.Failure(
            toolId = "write_note",
            error = ToolError.PermissionDenied("WRITE_EXTERNAL_STORAGE"),
            executionTimeMs = 2
        )

        assertIs<ToolError.PermissionDenied>(result.error)
        assertEquals("WRITE_EXTERNAL_STORAGE", (result.error as ToolError.PermissionDenied).permission)
    }

    @Test
    fun `Failure result with ExecutionFailed error`() {
        val exception = RuntimeException("Camera unavailable")
        val result = ToolResult.Failure(
            toolId = "open_camera",
            error = ToolError.ExecutionFailed(exception),
            executionTimeMs = 100
        )

        assertIs<ToolError.ExecutionFailed>(result.error)
        assertEquals("Camera unavailable", (result.error as ToolError.ExecutionFailed).cause.message)
    }

    @Test
    fun `Failure result with Unavailable error`() {
        val result = ToolResult.Failure(
            toolId = "toggle_flashlight",
            error = ToolError.Unavailable("Device has no flashlight"),
            executionTimeMs = 1
        )

        assertIs<ToolError.Unavailable>(result.error)
        assertEquals("Device has no flashlight", (result.error as ToolError.Unavailable).reason)
    }

    @Test
    fun `Failure result isFailure returns true`() {
        val result: ToolResult = ToolResult.Failure(
            toolId = "test",
            error = ToolError.NotFound("test"),
            executionTimeMs = 5
        )

        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    // ===== ToolResult.RequiresConfirmation Tests =====

    @Test
    fun `RequiresConfirmation result contains pending call`() {
        val pendingCall = ToolCall(
            toolId = "write_note",
            arguments = mapOf("content" to "Hello world"),
            rawText = ""
        )

        val result = ToolResult.RequiresConfirmation(
            toolId = "write_note",
            confirmationPrompt = "Allow M1K3 to write a note?",
            pendingCall = pendingCall
        )

        assertEquals("write_note", result.toolId)
        assertEquals("Allow M1K3 to write a note?", result.confirmationPrompt)
        assertEquals("Hello world", result.pendingCall.arguments["content"])
    }

    @Test
    fun `RequiresConfirmation is neither success nor failure`() {
        val result: ToolResult = ToolResult.RequiresConfirmation(
            toolId = "write_note",
            confirmationPrompt = "Allow?",
            pendingCall = ToolCall("write_note", emptyMap(), "")
        )

        assertFalse(result.isSuccess)
        assertFalse(result.isFailure)
        assertTrue(result.requiresConfirmation)
    }

    // ===== ToolError Display Tests =====

    @Test
    fun `ToolError displayMessage is human readable`() {
        assertEquals(
            "Tool 'unknown' not found",
            ToolError.NotFound("unknown").displayMessage
        )
        assertEquals(
            "Invalid arguments: level must be positive",
            ToolError.InvalidArguments("level must be positive").displayMessage
        )
        assertEquals(
            "Permission denied: CAMERA",
            ToolError.PermissionDenied("CAMERA").displayMessage
        )
        assertEquals(
            "Tool unavailable: No flashlight on device",
            ToolError.Unavailable("No flashlight on device").displayMessage
        )
    }

    @Test
    fun `ToolError ExecutionFailed displayMessage includes cause`() {
        val error = ToolError.ExecutionFailed(RuntimeException("Something broke"))
        assertTrue(error.displayMessage.contains("Something broke"))
    }
}
