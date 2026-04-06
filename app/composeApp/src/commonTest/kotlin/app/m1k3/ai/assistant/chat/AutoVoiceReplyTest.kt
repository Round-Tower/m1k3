package app.m1k3.ai.assistant.chat

import app.m1k3.ai.domain.chat.GenerationStats
import app.m1k3.ai.domain.platform.PreferenceKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Auto Voice Reply feature.
 *
 * TDD: RED phase - these tests define the expected behavior:
 * 1. Auto voice reply defaults to OFF
 * 2. ChatUiState tracks autoVoiceReply state
 * 3. Preference key exists and is correct
 * 4. Toggle flips the state
 */
class AutoVoiceReplyTest {

    // ===== Preference Key Tests =====

    @Test
    fun `VOICE_AUTO_REPLY preference key exists`() {
        assertEquals("voice_auto_reply", PreferenceKeys.VOICE_AUTO_REPLY)
    }

    // ===== ChatUiState Tests =====

    @Test
    fun `ChatUiState autoVoiceReply defaults to false`() {
        val state = ChatUiState()
        assertFalse(state.autoVoiceReply)
    }

    @Test
    fun `ChatUiState can enable autoVoiceReply`() {
        val state = ChatUiState(autoVoiceReply = true)
        assertTrue(state.autoVoiceReply)
    }

    @Test
    fun `ChatUiState autoVoiceReply can be toggled via copy`() {
        val original = ChatUiState(autoVoiceReply = false)
        val toggled = original.copy(autoVoiceReply = true)
        assertFalse(original.autoVoiceReply)
        assertTrue(toggled.autoVoiceReply)
    }

    // ===== Auto-speak trigger logic =====

    @Test
    fun `shouldAutoSpeak returns true when enabled and generation complete`() {
        val state = ChatUiState(
            autoVoiceReply = true,
            generationState = GenerationState.Complete(
                finalText = "Hello there!",
                stats = GenerationStats(tokenCount = 10, durationMs = 500, tokensPerSecond = 20f)
            ),
            isSpeaking = false
        )
        assertTrue(state.shouldAutoSpeak)
    }

    @Test
    fun `shouldAutoSpeak returns false when disabled`() {
        val state = ChatUiState(
            autoVoiceReply = false,
            generationState = GenerationState.Complete(
                finalText = "Hello there!",
                stats = GenerationStats(tokenCount = 10, durationMs = 500, tokensPerSecond = 20f)
            ),
            isSpeaking = false
        )
        assertFalse(state.shouldAutoSpeak)
    }

    @Test
    fun `shouldAutoSpeak returns false when already speaking`() {
        val state = ChatUiState(
            autoVoiceReply = true,
            generationState = GenerationState.Complete(
                finalText = "Hello there!",
                stats = GenerationStats(tokenCount = 10, durationMs = 500, tokensPerSecond = 20f)
            ),
            isSpeaking = true
        )
        assertFalse(state.shouldAutoSpeak)
    }

    @Test
    fun `shouldAutoSpeak returns false when still generating`() {
        val state = ChatUiState(
            autoVoiceReply = true,
            generationState = GenerationState.Streaming(
                partialText = "Hello",
                tokenCount = 5
            ),
            isSpeaking = false
        )
        assertFalse(state.shouldAutoSpeak)
    }

    @Test
    fun `shouldAutoSpeak returns false when idle`() {
        val state = ChatUiState(
            autoVoiceReply = true,
            generationState = GenerationState.Idle,
            isSpeaking = false
        )
        assertFalse(state.shouldAutoSpeak)
    }

    @Test
    fun `shouldAutoSpeak returns false when complete text is empty`() {
        val state = ChatUiState(
            autoVoiceReply = true,
            generationState = GenerationState.Complete(
                finalText = "",
                stats = GenerationStats(tokenCount = 0, durationMs = 100, tokensPerSecond = 0f)
            ),
            isSpeaking = false
        )
        assertFalse(state.shouldAutoSpeak)
    }
}
