package app.m1k3.ai.domain.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

/**
 * TDD Tests for Voice sealed class
 *
 * Voice represents TTS voice options with Kokoro Daniel as the default.
 */
class VoiceTest {

    // ===== Voice Identity Tests =====

    @Test
    fun `Voice Daniel has correct id`() {
        assertEquals("bm_daniel", Voice.Kokoro.Daniel.id)
    }

    @Test
    fun `Voice Daniel has correct display name`() {
        assertEquals("Daniel (British Male)", Voice.Kokoro.Daniel.displayName)
    }

    @Test
    fun `Voice Daniel has correct language`() {
        assertEquals("en", Voice.Kokoro.Daniel.language)
    }

    // ===== Voice Variants Tests =====

    @Test
    fun `Voice AmericanFemale has correct id`() {
        assertEquals("af", Voice.Kokoro.AmericanFemale.id)
    }

    @Test
    fun `Voice AmericanMale has correct id`() {
        assertEquals("am", Voice.Kokoro.AmericanMale.id)
    }

    @Test
    fun `Voice BritishFemale has correct id`() {
        assertEquals("bf", Voice.Kokoro.BritishFemale.id)
    }

    @Test
    fun `Voice BritishMale has correct id`() {
        assertEquals("bm", Voice.Kokoro.BritishMale.id)
    }

    // ===== Default Voice Tests =====

    @Test
    fun `Voice default is Daniel`() {
        assertEquals(Voice.Kokoro.Daniel, Voice.default)
    }

    @Test
    fun `Voice default is a Kokoro voice`() {
        assertIs<Voice.Kokoro>(Voice.default)
    }

    // ===== Voice Collection Tests =====

    @Test
    fun `Voice all returns at least 5 voices`() {
        val voices = Voice.all()
        assertTrue(voices.size >= 5, "Expected at least 5 voices, got ${voices.size}")
    }

    @Test
    fun `Voice all includes Daniel`() {
        val voices = Voice.all()
        assertTrue(voices.contains(Voice.Kokoro.Daniel))
    }

    @Test
    fun `Voice all includes all Kokoro voices`() {
        val voices = Voice.all()
        assertTrue(voices.contains(Voice.Kokoro.Daniel))
        assertTrue(voices.contains(Voice.Kokoro.AmericanFemale))
        assertTrue(voices.contains(Voice.Kokoro.AmericanMale))
        assertTrue(voices.contains(Voice.Kokoro.BritishFemale))
        assertTrue(voices.contains(Voice.Kokoro.BritishMale))
    }

    // ===== Voice Inheritance Tests =====

    @Test
    fun `Kokoro voices are Voice instances`() {
        val daniel: Voice = Voice.Kokoro.Daniel
        assertIs<Voice>(daniel)
        assertIs<Voice.Kokoro>(daniel)
    }

    @Test
    fun `Kokoro voices share same engine type`() {
        // All Kokoro voices should work with Kokoro TTS engine
        val kokoroVoices = Voice.all().filterIsInstance<Voice.Kokoro>()
        assertTrue(kokoroVoices.isNotEmpty())
        kokoroVoices.forEach { voice ->
            assertTrue(voice.id.isNotEmpty(), "Voice ${voice.displayName} should have an id")
        }
    }
}
