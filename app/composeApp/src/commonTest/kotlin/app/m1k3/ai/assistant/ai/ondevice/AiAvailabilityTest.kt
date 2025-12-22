package app.m1k3.ai.assistant.ai.ondevice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * TDD Tests for AiAvailability sealed class.
 *
 * 🔴 RED Phase: These tests are written FIRST before implementation.
 * They should fail initially because AiAvailability doesn't exist yet.
 */
class AiAvailabilityTest {

    // === Available State Tests ===

    @Test
    fun `Available should be a singleton object`() {
        val available1 = AiAvailability.Available
        val available2 = AiAvailability.Available
        assertEquals(available1, available2)
    }

    @Test
    fun `Available should be an AiAvailability subtype`() {
        val available = AiAvailability.Available
        assertIs<AiAvailability>(available)
    }

    // === Downloading State Tests ===

    @Test
    fun `Downloading should be a singleton object`() {
        val downloading1 = AiAvailability.Downloading
        val downloading2 = AiAvailability.Downloading
        assertEquals(downloading1, downloading2)
    }

    @Test
    fun `Downloading should be an AiAvailability subtype`() {
        val downloading = AiAvailability.Downloading
        assertIs<AiAvailability>(downloading)
    }

    // === Unavailable State Tests ===

    @Test
    fun `Unavailable should contain reason`() {
        val unavailable = AiAvailability.Unavailable(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED)
        assertEquals(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED, unavailable.reason)
    }

    @Test
    fun `Unavailable should be an AiAvailability subtype`() {
        val unavailable = AiAvailability.Unavailable(AiAvailability.UnavailableReason.MODEL_NOT_READY)
        assertIs<AiAvailability>(unavailable)
    }

    @Test
    fun `Unavailable with different reasons should not be equal`() {
        val unavailable1 = AiAvailability.Unavailable(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED)
        val unavailable2 = AiAvailability.Unavailable(AiAvailability.UnavailableReason.AI_DISABLED)
        assertNotEquals(unavailable1, unavailable2)
    }

    @Test
    fun `Unavailable with same reason should be equal`() {
        val unavailable1 = AiAvailability.Unavailable(AiAvailability.UnavailableReason.QUOTA_EXCEEDED)
        val unavailable2 = AiAvailability.Unavailable(AiAvailability.UnavailableReason.QUOTA_EXCEEDED)
        assertEquals(unavailable1, unavailable2)
    }

    // === Fallback State Tests ===

    @Test
    fun `Fallback should contain engine name`() {
        val fallback = AiAvailability.Fallback("SmolLM2-135M")
        assertEquals("SmolLM2-135M", fallback.engineName)
    }

    @Test
    fun `Fallback should be an AiAvailability subtype`() {
        val fallback = AiAvailability.Fallback("LlamaCpp")
        assertIs<AiAvailability>(fallback)
    }

    @Test
    fun `Fallback with different engine names should not be equal`() {
        val fallback1 = AiAvailability.Fallback("SmolLM2-135M")
        val fallback2 = AiAvailability.Fallback("TinyLlama")
        assertNotEquals(fallback1, fallback2)
    }

    @Test
    fun `Fallback with same engine name should be equal`() {
        val fallback1 = AiAvailability.Fallback("SmolLM2-135M")
        val fallback2 = AiAvailability.Fallback("SmolLM2-135M")
        assertEquals(fallback1, fallback2)
    }

    // === UnavailableReason Enum Tests ===

    @Test
    fun `UnavailableReason should have DEVICE_NOT_SUPPORTED`() {
        val reason = AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED
        assertEquals("DEVICE_NOT_SUPPORTED", reason.name)
    }

    @Test
    fun `UnavailableReason should have MODEL_NOT_READY`() {
        val reason = AiAvailability.UnavailableReason.MODEL_NOT_READY
        assertEquals("MODEL_NOT_READY", reason.name)
    }

    @Test
    fun `UnavailableReason should have AI_DISABLED`() {
        val reason = AiAvailability.UnavailableReason.AI_DISABLED
        assertEquals("AI_DISABLED", reason.name)
    }

    @Test
    fun `UnavailableReason should have QUOTA_EXCEEDED`() {
        val reason = AiAvailability.UnavailableReason.QUOTA_EXCEEDED
        assertEquals("QUOTA_EXCEEDED", reason.name)
    }

    @Test
    fun `UnavailableReason should have BACKGROUND_BLOCKED`() {
        val reason = AiAvailability.UnavailableReason.BACKGROUND_BLOCKED
        assertEquals("BACKGROUND_BLOCKED", reason.name)
    }

    @Test
    fun `UnavailableReason should have UNKNOWN`() {
        val reason = AiAvailability.UnavailableReason.UNKNOWN
        assertEquals("UNKNOWN", reason.name)
    }

    // === State Differentiation Tests ===

    @Test
    fun `Different states should not be equal`() {
        val available = AiAvailability.Available
        val downloading = AiAvailability.Downloading
        val unavailable = AiAvailability.Unavailable(AiAvailability.UnavailableReason.UNKNOWN)
        val fallback = AiAvailability.Fallback("TestEngine")

        assertNotEquals<AiAvailability>(available, downloading)
        assertNotEquals<AiAvailability>(available, unavailable)
        assertNotEquals<AiAvailability>(available, fallback)
        assertNotEquals<AiAvailability>(downloading, unavailable)
        assertNotEquals<AiAvailability>(downloading, fallback)
        assertNotEquals<AiAvailability>(unavailable, fallback)
    }
}
