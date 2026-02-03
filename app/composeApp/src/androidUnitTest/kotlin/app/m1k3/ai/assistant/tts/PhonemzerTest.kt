package app.m1k3.ai.assistant.tts

import org.junit.Test
import org.junit.Assert.*

/**
 * TDD Tests for Phonemizer
 *
 * Tests the text-to-Kokoro-token-ID conversion via IPA phonemes.
 * Validates dictionary lookup, fallback G2P, and Kokoro vocab mapping.
 */
class PhonemzerTest {

    private val phonemizer = Phonemizer()

    // ===== Basic Phonemization Tests =====

    @Test
    fun `phonemize returns non-empty result for simple text`() {
        val result = phonemizer.phonemize("Hello")
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `phonemize handles empty string`() {
        val result = phonemizer.phonemize("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `phonemize handles whitespace only`() {
        val result = phonemizer.phonemize("   ")
        assertTrue(result.isEmpty())
    }

    // ===== Kokoro Token ID Validation =====

    @Test
    fun `phonemize returns valid Kokoro token IDs`() {
        val result = phonemizer.phonemize("Hello world")
        result.forEach { tokenId ->
            assertTrue("Token ID $tokenId should be positive", tokenId > 0)
            assertTrue("Token ID $tokenId should be within Kokoro vocab range", tokenId < 178)
        }
    }

    @Test
    fun `phonemize produces different output for different inputs`() {
        val hello = phonemizer.phonemize("Hello")
        val world = phonemizer.phonemize("World")
        assertFalse(
            "Different inputs should produce different tokens",
            hello.contentEquals(world)
        )
    }

    // ===== Dictionary Lookup Tests =====

    @Test
    fun `dictionary word hello produces IPA tokens`() {
        val result = phonemizer.phonemize("hello")
        assertTrue("hello should produce tokens", result.isNotEmpty())
        // hello -> hɛˈloʊ -> should contain ɛ (86), ˈ (156)
        assertTrue("Should contain IPA vowel token", result.any { it > 68 })
    }

    @Test
    fun `dictionary word the produces correct tokens`() {
        val result = phonemizer.phonemize("the")
        assertTrue(result.isNotEmpty())
        // the -> ðə -> ð (81), ə (83)
        assertTrue("Should contain ð token (81)", result.contains(81))
        assertTrue("Should contain ə token (83)", result.contains(83))
    }

    @Test
    fun `space between words produces space token`() {
        val result = phonemizer.phonemize("hello world")
        // Space token = 16 in Kokoro vocab
        assertTrue("Should contain space token (16)", result.contains(16))
    }

    // ===== Punctuation Tests =====

    @Test
    fun `phonemize handles punctuation`() {
        val result = phonemizer.phonemize("hello, world!")
        assertTrue(result.isNotEmpty())
        // , = 3, ! = 5 in Kokoro vocab
        assertTrue("Should contain comma token (3)", result.contains(3))
        assertTrue("Should contain exclamation token (5)", result.contains(5))
    }

    @Test
    fun `phonemize handles question mark`() {
        val result = phonemizer.phonemize("how are you?")
        assertTrue("Should contain question token (6)", result.contains(6))
    }

    // ===== Fallback G2P Tests =====

    @Test
    fun `unknown word still produces tokens`() {
        val result = phonemizer.phonemize("xylophone")
        assertTrue("Unknown word should still produce tokens", result.isNotEmpty())
    }

    @Test
    fun `phonemize handles multiple sentences`() {
        val result = phonemizer.phonemize("Hello world. How are you?")
        assertTrue(result.isNotEmpty())
        assertTrue("Should contain period token (4)", result.contains(4))
    }

    // ===== Normalization Tests =====

    @Test
    fun `phonemize normalizes case`() {
        val lower = phonemizer.phonemize("hello")
        val upper = phonemizer.phonemize("HELLO")
        assertArrayEquals(
            "Same word in different cases should produce same tokens",
            lower, upper
        )
    }

    @Test
    fun `phonemize trims whitespace`() {
        val trimmed = phonemizer.phonemize("hello")
        val padded = phonemizer.phonemize("  hello  ")
        assertArrayEquals(trimmed, padded)
    }

    // ===== Stress Marker Tests =====

    @Test
    fun `multi-syllable dictionary words include stress markers`() {
        // "important" -> ɪmˈpɔːɹtənt -> contains ˈ (156)
        val result = phonemizer.phonemize("important")
        assertTrue(
            "Multi-syllable word should include stress marker (156)",
            result.contains(156)
        )
    }
}
