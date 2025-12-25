package app.m1k3.ai.assistant.ai.ondevice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * TDD Tests for AiResult sealed class.
 *
 * 🔴 RED Phase: These tests are written FIRST before implementation.
 * They should fail initially because AiResult doesn't exist yet.
 */
class AiResultTest {

    // === Success State Tests ===

    @Test
    fun `Success should wrap value`() {
        val result = AiResult.Success("hello")
        assertEquals("hello", result.data)
    }

    @Test
    fun `Success should be an AiResult subtype`() {
        val result: AiResult<String> = AiResult.Success("test")
        assertIs<AiResult<String>>(result)
    }

    @Test
    fun `Success with different values should not be equal`() {
        val result1 = AiResult.Success("hello")
        val result2 = AiResult.Success("world")
        assertIs<AiResult.Success<String>>(result1)
        assertIs<AiResult.Success<String>>(result2)
        assertEquals("hello", result1.data)
        assertEquals("world", result2.data)
    }

    @Test
    fun `Success with same value should be equal`() {
        val result1 = AiResult.Success("hello")
        val result2 = AiResult.Success("hello")
        assertEquals(result1, result2)
    }

    // === Error State Tests ===

    @Test
    fun `Error should contain code and message`() {
        val result = AiResult.Error(AiErrorCode.UNAVAILABLE, "not ready")
        assertEquals(AiErrorCode.UNAVAILABLE, result.code)
        assertEquals("not ready", result.message)
    }

    @Test
    fun `Error should be an AiResult subtype`() {
        val result: AiResult<String> = AiResult.Error(AiErrorCode.UNKNOWN, "error")
        assertIs<AiResult<String>>(result)
    }

    @Test
    fun `Error with different codes should not be equal`() {
        val result1 = AiResult.Error(AiErrorCode.UNAVAILABLE, "error")
        val result2 = AiResult.Error(AiErrorCode.BUSY, "error")
        assertIs<AiResult.Error>(result1)
        assertIs<AiResult.Error>(result2)
    }

    // === map() Function Tests ===

    @Test
    fun `map should transform Success value`() {
        val result: AiResult<String> = AiResult.Success("hello")
        val mapped = result.map { it.uppercase() }
        assertIs<AiResult.Success<String>>(mapped)
        assertEquals("HELLO", mapped.data)
    }

    @Test
    fun `map should propagate Error unchanged`() {
        val result: AiResult<String> = AiResult.Error(AiErrorCode.UNAVAILABLE, "not ready")
        val mapped = result.map { it.uppercase() }
        assertIs<AiResult.Error>(mapped)
        assertEquals(AiErrorCode.UNAVAILABLE, mapped.code)
        assertEquals("not ready", mapped.message)
    }

    @Test
    fun `map should allow type transformation`() {
        val result: AiResult<String> = AiResult.Success("42")
        val mapped: AiResult<Int> = result.map { it.toInt() }
        assertIs<AiResult.Success<Int>>(mapped)
        assertEquals(42, mapped.data)
    }

    @Test
    fun `chained map should work correctly`() {
        val result: AiResult<String> = AiResult.Success("hello")
        val mapped = result
            .map { it.uppercase() }
            .map { it.length }
        assertIs<AiResult.Success<Int>>(mapped)
        assertEquals(5, mapped.data)
    }

    // === getOrNull() Function Tests ===

    @Test
    fun `getOrNull should return value for Success`() {
        val result = AiResult.Success("test")
        assertEquals("test", result.getOrNull())
    }

    @Test
    fun `getOrNull should return null for Error`() {
        val result: AiResult<String> = AiResult.Error(AiErrorCode.UNKNOWN, "error")
        assertNull(result.getOrNull())
    }

    // === getOrThrow() Function Tests ===

    @Test
    fun `getOrThrow should return value for Success`() {
        val result = AiResult.Success("test")
        assertEquals("test", result.getOrThrow())
    }

    @Test
    fun `getOrThrow should throw AiException for Error`() {
        val result: AiResult<String> = AiResult.Error(AiErrorCode.UNAVAILABLE, "not ready")
        val exception = assertFailsWith<AiException> {
            result.getOrThrow()
        }
        assertEquals(AiErrorCode.UNAVAILABLE, exception.code)
        assertEquals("not ready", exception.message)
    }

    // === getOrDefault() Function Tests ===

    @Test
    fun `getOrDefault should return value for Success`() {
        val result = AiResult.Success("test")
        assertEquals("test", result.getOrDefault("default"))
    }

    @Test
    fun `getOrDefault should return default for Error`() {
        val result: AiResult<String> = AiResult.Error(AiErrorCode.UNKNOWN, "error")
        assertEquals("default", result.getOrDefault("default"))
    }

    // === fold() Function Tests ===

    @Test
    fun `fold should apply onSuccess for Success`() {
        val result = AiResult.Success("hello")
        val folded = result.fold(
            onSuccess = { "Success: $it" },
            onError = { code, msg -> "Error: $code - $msg" }
        )
        assertEquals("Success: hello", folded)
    }

    @Test
    fun `fold should apply onError for Error`() {
        val result: AiResult<String> = AiResult.Error(AiErrorCode.BUSY, "system busy")
        val folded = result.fold(
            onSuccess = { "Success: $it" },
            onError = { code, msg -> "Error: $code - $msg" }
        )
        assertEquals("Error: BUSY - system busy", folded)
    }

    // === onSuccess() / onError() Extension Tests ===

    @Test
    fun `onSuccess should invoke action for Success`() {
        var captured: String? = null
        val result = AiResult.Success("test")
        result.onSuccess { captured = it }
        assertEquals("test", captured)
    }

    @Test
    fun `onSuccess should not invoke action for Error`() {
        var captured: String? = null
        val result: AiResult<String> = AiResult.Error(AiErrorCode.UNKNOWN, "error")
        result.onSuccess { captured = it }
        assertNull(captured)
    }

    @Test
    fun `onError should invoke action for Error`() {
        var capturedCode: AiErrorCode? = null
        var capturedMsg: String? = null
        val result: AiResult<String> = AiResult.Error(AiErrorCode.QUOTA_EXCEEDED, "limit reached")
        result.onError { code, msg ->
            capturedCode = code
            capturedMsg = msg
        }
        assertEquals(AiErrorCode.QUOTA_EXCEEDED, capturedCode)
        assertEquals("limit reached", capturedMsg)
    }

    @Test
    fun `onError should not invoke action for Success`() {
        var invoked = false
        val result = AiResult.Success("test")
        result.onError { _, _ -> invoked = true }
        assertEquals(false, invoked)
    }

    // === isSuccess / isError Properties Tests ===

    @Test
    fun `isSuccess should be true for Success`() {
        val result = AiResult.Success("test")
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun `isSuccess should be false for Error`() {
        val result: AiResult<String> = AiResult.Error(AiErrorCode.UNKNOWN, "error")
        assertEquals(false, result.isSuccess)
    }

    @Test
    fun `isError should be true for Error`() {
        val result: AiResult<String> = AiResult.Error(AiErrorCode.UNKNOWN, "error")
        assertEquals(true, result.isError)
    }

    @Test
    fun `isError should be false for Success`() {
        val result = AiResult.Success("test")
        assertEquals(false, result.isError)
    }
}
