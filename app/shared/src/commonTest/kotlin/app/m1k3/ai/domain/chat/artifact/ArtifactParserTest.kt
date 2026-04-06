package app.m1k3.ai.domain.chat.artifact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD Tests for ArtifactParser.
 *
 * Verifies extraction of <artifact> blocks from model responses,
 * leaving surrounding markdown text intact.
 */
class ArtifactParserTest {

    // ===== No artifact =====

    @Test
    fun `plain text returns single text part and no artifacts`() {
        val result = ArtifactParser.parse("Hello, how can I help?")
        assertEquals(listOf("Hello, how can I help?"), result.textParts)
        assertTrue(result.artifacts.isEmpty())
    }

    @Test
    fun `markdown-only response returns text unchanged`() {
        val md = "## Title\n\n- item one\n- item two"
        val result = ArtifactParser.parse(md)
        assertEquals(listOf(md), result.textParts)
        assertTrue(result.artifacts.isEmpty())
    }

    // ===== Single artifact =====

    @Test
    fun `single artifact extracted correctly`() {
        val html = "<html><body><p>Hello</p></body></html>"
        val input = """<artifact id="hello" type="html">$html</artifact>"""
        val result = ArtifactParser.parse(input)

        assertEquals(1, result.artifacts.size)
        assertEquals("hello", result.artifacts[0].id)
        assertEquals("html", result.artifacts[0].type)
        assertEquals(html, result.artifacts[0].html)
    }

    @Test
    fun `text before artifact preserved as first text part`() {
        val input = "Here's a timer:\n\n<artifact id=\"t\" type=\"html\"><p>timer</p></artifact>"
        val result = ArtifactParser.parse(input)

        assertEquals("Here's a timer:\n\n", result.textParts[0])
    }

    @Test
    fun `text after artifact preserved as last text part`() {
        val input = "<artifact id=\"t\" type=\"html\"><p>x</p></artifact>\n\nLet me know!"
        val result = ArtifactParser.parse(input)

        assertEquals("\n\nLet me know!", result.textParts.last())
    }

    @Test
    fun `artifact surrounded by text splits into three parts`() {
        val input = "Before\n<artifact id=\"a\" type=\"html\"><p>x</p></artifact>\nAfter"
        val result = ArtifactParser.parse(input)

        assertEquals("Before\n", result.textParts[0])
        assertEquals("\nAfter", result.textParts[1])
        assertEquals(1, result.artifacts.size)
    }

    // ===== Multiline artifact content =====

    @Test
    fun `multiline html artifact extracted correctly`() {
        val html = """
            <!DOCTYPE html>
            <html>
              <body><p>Hello</p></body>
            </html>
        """.trimIndent()
        val input = "<artifact id=\"page\" type=\"html\">$html</artifact>"
        val result = ArtifactParser.parse(input)

        assertEquals(html, result.artifacts[0].html)
    }

    // ===== Multiple artifacts =====

    @Test
    fun `two artifacts extracted in order`() {
        val input = """
            First:
            <artifact id="a1" type="html"><p>one</p></artifact>
            Second:
            <artifact id="a2" type="html"><p>two</p></artifact>
        """.trimIndent()
        val result = ArtifactParser.parse(input)

        assertEquals(2, result.artifacts.size)
        assertEquals("a1", result.artifacts[0].id)
        assertEquals("a2", result.artifacts[1].id)
    }

    // ===== ID and type defaults =====

    @Test
    fun `missing id gets generated id`() {
        val input = """<artifact type="html"><p>x</p></artifact>"""
        val result = ArtifactParser.parse(input)

        assertTrue(result.artifacts[0].id.isNotEmpty())
    }

    @Test
    fun `missing type defaults to html`() {
        val input = """<artifact id="a"><p>x</p></artifact>"""
        val result = ArtifactParser.parse(input)

        assertEquals("html", result.artifacts[0].type)
    }

    // ===== hasArtifacts helper =====

    @Test
    fun `hasArtifacts returns true when artifact present`() {
        assertTrue(ArtifactParser.hasArtifacts("<artifact id=\"x\" type=\"html\">h</artifact>"))
    }

    @Test
    fun `hasArtifacts returns false for plain text`() {
        assertTrue(!ArtifactParser.hasArtifacts("just text"))
    }

    // ===== Whitespace trimming =====

    @Test
    fun `artifact html content is trimmed`() {
        val input = "<artifact id=\"a\" type=\"html\">\n  <p>hi</p>\n</artifact>"
        val result = ArtifactParser.parse(input)
        assertEquals("<p>hi</p>", result.artifacts[0].html)
    }
}
