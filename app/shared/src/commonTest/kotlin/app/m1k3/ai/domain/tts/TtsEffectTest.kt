package app.m1k3.ai.domain.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TDD Tests for TtsEffect sealed class
 *
 * TtsEffect represents audio effects applied to TTS output.
 */
class TtsEffectTest {

    // ===== None Effect Tests =====

    @Test
    fun `TtsEffect None has no parameters`() {
        val effect = TtsEffect.None
        assertIs<TtsEffect.None>(effect)
    }

    @Test
    fun `TtsEffect None is a TtsEffect`() {
        val effect: TtsEffect = TtsEffect.None
        assertIs<TtsEffect>(effect)
    }

    // ===== Intercom Effect Tests =====

    @Test
    fun `TtsEffect Intercom has default distortion`() {
        val effect = TtsEffect.Intercom()
        assertEquals(0.1f, effect.distortion)
    }

    @Test
    fun `TtsEffect Intercom accepts custom distortion`() {
        val effect = TtsEffect.Intercom(distortion = 0.3f)
        assertEquals(0.3f, effect.distortion)
    }

    // ===== RadioChat Effect Tests =====

    @Test
    fun `TtsEffect RadioChat has correct default values`() {
        val effect = TtsEffect.RadioChat()

        assertEquals(0.3f, effect.intercomMix)
        assertEquals(0.6f, effect.compression)
        assertEquals(300f, effect.highPass)
        assertEquals(3400f, effect.lowPass)
    }

    @Test
    fun `TtsEffect RadioChat accepts custom values`() {
        val effect = TtsEffect.RadioChat(
            intercomMix = 0.5f,
            compression = 0.8f,
            highPass = 400f,
            lowPass = 3000f
        )

        assertEquals(0.5f, effect.intercomMix)
        assertEquals(0.8f, effect.compression)
        assertEquals(400f, effect.highPass)
        assertEquals(3000f, effect.lowPass)
    }

    @Test
    fun `TtsEffect RadioChat is a TtsEffect`() {
        val effect: TtsEffect = TtsEffect.RadioChat()
        assertIs<TtsEffect>(effect)
        assertIs<TtsEffect.RadioChat>(effect)
    }

    // ===== Compression Effect Tests =====

    @Test
    fun `TtsEffect Compression has default values`() {
        val effect = TtsEffect.Compression()

        assertEquals(0.6f, effect.threshold)
        assertEquals(0.3f, effect.ratio)
    }

    @Test
    fun `TtsEffect Compression accepts custom values`() {
        val effect = TtsEffect.Compression(threshold = 0.5f, ratio = 0.4f)

        assertEquals(0.5f, effect.threshold)
        assertEquals(0.4f, effect.ratio)
    }

    // ===== Normalization Effect Tests =====

    @Test
    fun `TtsEffect Normalization has default level`() {
        val effect = TtsEffect.Normalization()
        assertEquals(0.8f, effect.level)
    }

    @Test
    fun `TtsEffect Normalization accepts custom level`() {
        val effect = TtsEffect.Normalization(level = 0.9f)
        assertEquals(0.9f, effect.level)
    }

    // ===== Chain Effect Tests =====

    @Test
    fun `TtsEffect Chain can combine effects`() {
        val effect = TtsEffect.Chain(
            listOf(
                TtsEffect.Intercom(distortion = 0.2f),
                TtsEffect.Compression(),
                TtsEffect.Normalization()
            )
        )

        assertEquals(3, effect.effects.size)
        assertIs<TtsEffect.Intercom>(effect.effects[0])
        assertIs<TtsEffect.Compression>(effect.effects[1])
        assertIs<TtsEffect.Normalization>(effect.effects[2])
    }

    @Test
    fun `TtsEffect Chain can be empty`() {
        val effect = TtsEffect.Chain(emptyList())
        assertTrue(effect.effects.isEmpty())
    }

    // ===== Type Hierarchy Tests =====

    @Test
    fun `All effects are TtsEffect instances`() {
        val effects: List<TtsEffect> = listOf(
            TtsEffect.None,
            TtsEffect.Intercom(),
            TtsEffect.RadioChat(),
            TtsEffect.Compression(),
            TtsEffect.Normalization(),
            TtsEffect.Chain(emptyList())
        )

        effects.forEach { effect ->
            assertIs<TtsEffect>(effect)
        }
    }
}
