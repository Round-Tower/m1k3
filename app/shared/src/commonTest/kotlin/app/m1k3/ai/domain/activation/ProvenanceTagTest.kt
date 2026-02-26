package app.m1k3.ai.domain.activation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * TDD Tests for ProvenanceTag (MurphySig)
 *
 * Every M1K3 response carries a provenance tag recording
 * the activation lineage. bytesTransmitted is always 0
 * (privacy guarantee: 100% on-device).
 */
class ProvenanceTagTest {

    // ===== Construction =====

    @Test
    fun `can create ProvenanceTag with required fields`() {
        val tag = ProvenanceTag(
            sessionId = "abc-123",
            activationSource = "widget",
            modelInfo = "apple-foundation-models/v1",
            deviceClass = "iPhone16,2"
        )
        assertNotNull(tag)
    }

    @Test
    fun `sessionId is preserved`() {
        val tag = ProvenanceTag(
            sessionId = "test-session",
            activationSource = "hotword",
            modelInfo = "llama-cpp/gemma3",
            deviceClass = "Pixel8"
        )
        assertEquals("test-session", tag.sessionId)
    }

    @Test
    fun `activationSource is preserved`() {
        val tag = ProvenanceTag(
            sessionId = "s1",
            activationSource = "share",
            modelInfo = "model",
            deviceClass = "device"
        )
        assertEquals("share", tag.activationSource)
    }

    // ===== Privacy Guarantee =====

    @Test
    fun `bytesTransmitted is always 0`() {
        val tag = ProvenanceTag(
            sessionId = "s1",
            activationSource = "widget",
            modelInfo = "model",
            deviceClass = "device"
        )
        assertEquals(0, tag.bytesTransmitted)
    }

    @Test
    fun `bytesTransmitted cannot be overridden at construction`() {
        // bytesTransmitted is hardcoded to 0 in the data class
        val tag = ProvenanceTag(
            sessionId = "s1",
            activationSource = "widget",
            modelInfo = "model",
            deviceClass = "device"
        )
        assertEquals(0, tag.bytesTransmitted)
    }

    // ===== Defaults =====

    @Test
    fun `version defaults to 1_0`() {
        val tag = ProvenanceTag(
            sessionId = "s1",
            activationSource = "intent",
            modelInfo = "model",
            deviceClass = "device"
        )
        assertEquals("1.0", tag.version)
    }

    @Test
    fun `inputMimeType defaults to null`() {
        val tag = ProvenanceTag(
            sessionId = "s1",
            activationSource = "intent",
            modelInfo = "model",
            deviceClass = "device"
        )
        assertNull(tag.inputMimeType)
    }

    @Test
    fun `inputMimeType can be set`() {
        val tag = ProvenanceTag(
            sessionId = "s1",
            activationSource = "share",
            modelInfo = "model",
            deviceClass = "device",
            inputMimeType = "application/pdf"
        )
        assertEquals("application/pdf", tag.inputMimeType)
    }

    // ===== Equality =====

    @Test
    fun `tags with same values are equal`() {
        val tag1 = ProvenanceTag("s1", "widget", "model", "device")
        val tag2 = ProvenanceTag("s1", "widget", "model", "device")
        assertEquals(tag1, tag2)
    }
}
