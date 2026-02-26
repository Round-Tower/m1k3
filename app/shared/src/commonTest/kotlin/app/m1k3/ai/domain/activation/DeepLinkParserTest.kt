package app.m1k3.ai.domain.activation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD Tests for DeepLinkParser
 *
 * Parses m1k3://activate?source=X&input=Y&mode=Z URIs into
 * ActivationContext. Pure Kotlin, no platform dependencies.
 */
class DeepLinkParserTest {

    private val parser = DeepLinkParser()

    // ===== Source Parsing =====

    @Test
    fun `parses widget source`() {
        val context = parser.parse("m1k3://activate?source=widget&mode=default")
        assertNotNull(context)
        assertEquals(ActivationSource.Widget, context.source)
    }

    @Test
    fun `parses google source`() {
        val context = parser.parse("m1k3://activate?source=google&input=Hello")
        assertNotNull(context)
        assertEquals(ActivationSource.GoogleAssistant, context.source)
    }

    @Test
    fun `parses gemini source`() {
        val context = parser.parse("m1k3://activate?source=gemini&input=Summarise+this")
        assertNotNull(context)
        assertEquals(ActivationSource.GeminiExtension, context.source)
    }

    @Test
    fun `parses hotword source`() {
        val context = parser.parse("m1k3://activate?source=hotword")
        assertNotNull(context)
        assertEquals(ActivationSource.Hotword, context.source)
    }

    @Test
    fun `parses share source`() {
        val context = parser.parse("m1k3://activate?source=share&mime=text%2Fplain")
        assertNotNull(context)
        assertEquals(ActivationSource.ShareExtension, context.source)
    }

    // ===== Mode Parsing =====

    @Test
    fun `parses reading mode`() {
        val context = parser.parse("m1k3://activate?source=widget&mode=reading")
        assertNotNull(context)
        assertEquals(M1K3Mode.Reading, context.mode)
    }

    @Test
    fun `missing mode defaults to Default`() {
        val context = parser.parse("m1k3://activate?source=widget")
        assertNotNull(context)
        assertEquals(M1K3Mode.Default, context.mode)
    }

    // ===== Input Parsing =====

    @Test
    fun `parses input parameter`() {
        val context = parser.parse("m1k3://activate?source=google&input=Hello")
        assertNotNull(context)
        assertEquals("Hello", context.input)
    }

    @Test
    fun `decodes URL-encoded input`() {
        val context = parser.parse("m1k3://activate?source=google&input=Hello%20M1K3")
        assertNotNull(context)
        assertEquals("Hello M1K3", context.input)
    }

    @Test
    fun `decodes plus as space in input`() {
        val context = parser.parse("m1k3://activate?source=google&input=Hello+World")
        assertNotNull(context)
        assertEquals("Hello World", context.input)
    }

    @Test
    fun `missing input defaults to null`() {
        val context = parser.parse("m1k3://activate?source=widget")
        assertNotNull(context)
        assertNull(context.input)
    }

    // ===== MIME Type Parsing =====

    @Test
    fun `parses mime parameter`() {
        val context = parser.parse("m1k3://activate?source=share&mime=text%2Fplain")
        assertNotNull(context)
        assertEquals("text/plain", context.mimeType)
    }

    @Test
    fun `parses pdf mime type`() {
        val context = parser.parse("m1k3://activate?source=share&mime=application%2Fpdf")
        assertNotNull(context)
        assertEquals("application/pdf", context.mimeType)
    }

    // ===== Session Parsing =====

    @Test
    fun `parses session parameter`() {
        val context = parser.parse("m1k3://activate?source=shortcut&session=abc-123")
        assertNotNull(context)
        assertEquals("abc-123", context.sessionId)
    }

    @Test
    fun `missing session auto-generates ID`() {
        val context = parser.parse("m1k3://activate?source=widget")
        assertNotNull(context)
        assertTrue(context.sessionId.isNotEmpty())
    }

    // ===== Missing Source =====

    @Test
    fun `missing source defaults to DeepLink`() {
        val context = parser.parse("m1k3://activate?input=hello")
        assertNotNull(context)
        assertEquals(ActivationSource.DeepLink, context.source)
    }

    // ===== Malformed URIs =====

    @Test
    fun `wrong scheme returns null`() {
        val context = parser.parse("https://example.com")
        assertNull(context)
    }

    @Test
    fun `wrong host returns null`() {
        val context = parser.parse("m1k3://settings?key=value")
        assertNull(context)
    }

    @Test
    fun `empty string returns null`() {
        val context = parser.parse("")
        assertNull(context)
    }

    @Test
    fun `no query params still parses`() {
        val context = parser.parse("m1k3://activate")
        assertNotNull(context)
        assertEquals(ActivationSource.DeepLink, context.source)
        assertEquals(M1K3Mode.Default, context.mode)
    }

    // ===== Complex URIs =====

    @Test
    fun `parses full share extension URI`() {
        val context = parser.parse(
            "m1k3://activate?source=share&mime=text%2Fplain&payload=SGVsbG8gV29ybGQ%3D"
        )
        assertNotNull(context)
        assertEquals(ActivationSource.ShareExtension, context.source)
        assertEquals("text/plain", context.mimeType)
    }

    @Test
    fun `parses URI with all parameters`() {
        val context = parser.parse(
            "m1k3://activate?source=intent&mode=summarise&input=Test&session=s1&mime=text%2Fplain"
        )
        assertNotNull(context)
        assertEquals(ActivationSource.AppIntent, context.source)
        assertEquals(M1K3Mode.Summarise, context.mode)
        assertEquals("Test", context.input)
        assertEquals("s1", context.sessionId)
        assertEquals("text/plain", context.mimeType)
    }
}
