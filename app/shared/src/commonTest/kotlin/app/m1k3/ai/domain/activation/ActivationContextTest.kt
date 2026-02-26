package app.m1k3.ai.domain.activation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD Tests for ActivationContext data class
 *
 * ActivationContext is the unified payload that flows from any
 * activation path (widget, hotword, share, intent, deep link)
 * into the core M1K3 engine.
 */
class ActivationContextTest {

    // ===== Construction =====

    @Test
    fun `can create with source only`() {
        val context = ActivationContext(source = ActivationSource.Widget)
        assertNotNull(context)
        assertIs<ActivationSource.Widget>(context.source)
    }

    @Test
    fun `source is preserved`() {
        val context = ActivationContext(source = ActivationSource.Hotword)
        assertEquals(ActivationSource.Hotword, context.source)
    }

    // ===== Defaults =====

    @Test
    fun `mode defaults to Default`() {
        val context = ActivationContext(source = ActivationSource.Widget)
        assertEquals(M1K3Mode.Default, context.mode)
    }

    @Test
    fun `input defaults to null`() {
        val context = ActivationContext(source = ActivationSource.Widget)
        assertNull(context.input)
    }

    @Test
    fun `payload defaults to null`() {
        val context = ActivationContext(source = ActivationSource.Widget)
        assertNull(context.payload)
    }

    @Test
    fun `mimeType defaults to null`() {
        val context = ActivationContext(source = ActivationSource.Widget)
        assertNull(context.mimeType)
    }

    @Test
    fun `sessionId is auto-generated and non-empty`() {
        val context = ActivationContext(source = ActivationSource.Widget)
        assertTrue(context.sessionId.isNotEmpty())
    }

    @Test
    fun `timestamp defaults to non-zero`() {
        val context = ActivationContext(source = ActivationSource.Widget)
        assertTrue(context.timestamp > 0)
    }

    @Test
    fun `provenanceTag defaults to null`() {
        val context = ActivationContext(source = ActivationSource.Widget)
        assertNull(context.provenanceTag)
    }

    // ===== Full Construction =====

    @Test
    fun `can create with all fields`() {
        val tag = ProvenanceTag("s1", "widget", "model", "device")
        val payload = byteArrayOf(1, 2, 3)

        val context = ActivationContext(
            source = ActivationSource.ShareExtension,
            mode = M1K3Mode.Summarise,
            input = "Hello M1K3",
            payload = payload,
            mimeType = "text/plain",
            sessionId = "custom-session",
            timestamp = 1234567890L,
            provenanceTag = tag
        )

        assertEquals(ActivationSource.ShareExtension, context.source)
        assertEquals(M1K3Mode.Summarise, context.mode)
        assertEquals("Hello M1K3", context.input)
        assertTrue(context.payload.contentEquals(payload))
        assertEquals("text/plain", context.mimeType)
        assertEquals("custom-session", context.sessionId)
        assertEquals(1234567890L, context.timestamp)
        assertEquals(tag, context.provenanceTag)
    }

    // ===== Session Uniqueness =====

    @Test
    fun `auto-generated sessionIds are unique`() {
        val ctx1 = ActivationContext(source = ActivationSource.Widget)
        val ctx2 = ActivationContext(source = ActivationSource.Widget)
        assertNotEquals(ctx1.sessionId, ctx2.sessionId)
    }

    // ===== Copy =====

    @Test
    fun `copy preserves source and changes mode`() {
        val original = ActivationContext(
            source = ActivationSource.Hotword,
            mode = M1K3Mode.Default
        )
        val copied = original.copy(mode = M1K3Mode.Reading)

        assertEquals(ActivationSource.Hotword, copied.source)
        assertEquals(M1K3Mode.Reading, copied.mode)
    }
}
