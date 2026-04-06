package app.m1k3.ai.domain.stt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for STT domain state model.
 *
 * TDD: Verify state machine transitions and helper extensions.
 */
class SttStateTest {

    @Test
    fun `Idle is not listening`() {
        assertFalse(SttState.Idle.isListening)
    }

    @Test
    fun `Idle is not active`() {
        assertFalse(SttState.Idle.isActive)
    }

    @Test
    fun `Listening is listening`() {
        assertTrue(SttState.Listening().isListening)
    }

    @Test
    fun `Listening is active`() {
        assertTrue(SttState.Listening().isActive)
    }

    @Test
    fun `Listening captures partial text`() {
        val state = SttState.Listening(partialText = "hello wor")
        assertEquals("hello wor", state.partialText)
    }

    @Test
    fun `Processing is not listening`() {
        assertFalse(SttState.Processing.isListening)
    }

    @Test
    fun `Processing is active`() {
        assertTrue(SttState.Processing.isActive)
    }

    @Test
    fun `Result carries recognized text`() {
        val state = SttState.Result(text = "hello world")
        assertEquals("hello world", state.text)
    }

    @Test
    fun `Result is not active`() {
        assertFalse(SttState.Result("test").isActive)
    }

    @Test
    fun `Error carries message`() {
        val state = SttState.Error(message = "No speech detected")
        assertEquals("No speech detected", state.message)
    }

    @Test
    fun `Error is not active`() {
        assertFalse(SttState.Error("fail").isActive)
    }

    @Test
    fun `Listening defaults to empty partial text`() {
        assertEquals("", SttState.Listening().partialText)
    }
}
