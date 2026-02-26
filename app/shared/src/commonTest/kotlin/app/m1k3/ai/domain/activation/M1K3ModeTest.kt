package app.m1k3.ai.domain.activation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * TDD Tests for M1K3Mode enum
 *
 * M1K3Mode represents the operational mode requested by the activation path.
 * Each mode maps to a distinct user intent (general chat, reading assist, etc.).
 */
class M1K3ModeTest {

    // ===== Enum Values =====

    @Test
    fun `Default mode exists`() {
        assertEquals("default", M1K3Mode.Default.id)
    }

    @Test
    fun `Reading mode exists`() {
        assertEquals("reading", M1K3Mode.Reading.id)
    }

    @Test
    fun `Dictation mode exists`() {
        assertEquals("dictation", M1K3Mode.Dictation.id)
    }

    @Test
    fun `Summarise mode exists`() {
        assertEquals("summarise", M1K3Mode.Summarise.id)
    }

    @Test
    fun `Explain mode exists`() {
        assertEquals("explain", M1K3Mode.Explain.id)
    }

    // ===== from() Factory =====

    @Test
    fun `from default returns Default`() {
        assertEquals(M1K3Mode.Default, M1K3Mode.from("default"))
    }

    @Test
    fun `from reading returns Reading`() {
        assertEquals(M1K3Mode.Reading, M1K3Mode.from("reading"))
    }

    @Test
    fun `from dictation returns Dictation`() {
        assertEquals(M1K3Mode.Dictation, M1K3Mode.from("dictation"))
    }

    @Test
    fun `from summarise returns Summarise`() {
        assertEquals(M1K3Mode.Summarise, M1K3Mode.from("summarise"))
    }

    @Test
    fun `from explain returns Explain`() {
        assertEquals(M1K3Mode.Explain, M1K3Mode.from("explain"))
    }

    // ===== Case Insensitivity =====

    @Test
    fun `from is case insensitive`() {
        assertEquals(M1K3Mode.Reading, M1K3Mode.from("READING"))
        assertEquals(M1K3Mode.Summarise, M1K3Mode.from("Summarise"))
    }

    // ===== Unknown Defaults =====

    @Test
    fun `from unknown string defaults to Default`() {
        assertEquals(M1K3Mode.Default, M1K3Mode.from("unknown"))
    }

    @Test
    fun `from empty string defaults to Default`() {
        assertEquals(M1K3Mode.Default, M1K3Mode.from(""))
    }

    // ===== All Values =====

    @Test
    fun `entries contains all 5 modes`() {
        assertEquals(5, M1K3Mode.entries.size)
    }

    // ===== Identity =====

    @Test
    fun `different modes are not equal`() {
        assertNotEquals(M1K3Mode.Default, M1K3Mode.Reading)
    }
}
