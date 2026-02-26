package app.m1k3.ai.domain.activation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * TDD Tests for ActivationSource sealed class
 *
 * ActivationSource represents how M1K3 was activated — each sealed subtype
 * maps to a distinct user state (ambient, intentional, contextual, etc.).
 */
class ActivationSourceTest {

    // ===== Sealed Subtypes =====

    @Test
    fun `Hotword is an ActivationSource`() {
        assertIs<ActivationSource>(ActivationSource.Hotword)
    }

    @Test
    fun `Widget is an ActivationSource`() {
        assertIs<ActivationSource>(ActivationSource.Widget)
    }

    @Test
    fun `ShareExtension is an ActivationSource`() {
        assertIs<ActivationSource>(ActivationSource.ShareExtension)
    }

    @Test
    fun `AppIntent is an ActivationSource`() {
        assertIs<ActivationSource>(ActivationSource.AppIntent)
    }

    @Test
    fun `DeepLink is an ActivationSource`() {
        assertIs<ActivationSource>(ActivationSource.DeepLink)
    }

    @Test
    fun `GoogleAssistant is an ActivationSource`() {
        assertIs<ActivationSource>(ActivationSource.GoogleAssistant)
    }

    @Test
    fun `GeminiExtension is an ActivationSource`() {
        assertIs<ActivationSource>(ActivationSource.GeminiExtension)
    }

    @Test
    fun `Shortcut is an ActivationSource`() {
        assertIs<ActivationSource>(ActivationSource.Shortcut)
    }

    // ===== from() Factory =====

    @Test
    fun `from hotword returns Hotword`() {
        assertEquals(ActivationSource.Hotword, ActivationSource.from("hotword"))
    }

    @Test
    fun `from widget returns Widget`() {
        assertEquals(ActivationSource.Widget, ActivationSource.from("widget"))
    }

    @Test
    fun `from share returns ShareExtension`() {
        assertEquals(ActivationSource.ShareExtension, ActivationSource.from("share"))
    }

    @Test
    fun `from intent returns AppIntent`() {
        assertEquals(ActivationSource.AppIntent, ActivationSource.from("intent"))
    }

    @Test
    fun `from google returns GoogleAssistant`() {
        assertEquals(ActivationSource.GoogleAssistant, ActivationSource.from("google"))
    }

    @Test
    fun `from gemini returns GeminiExtension`() {
        assertEquals(ActivationSource.GeminiExtension, ActivationSource.from("gemini"))
    }

    @Test
    fun `from shortcut returns Shortcut`() {
        assertEquals(ActivationSource.Shortcut, ActivationSource.from("shortcut"))
    }

    // ===== Case Insensitivity =====

    @Test
    fun `from is case insensitive`() {
        assertEquals(ActivationSource.Hotword, ActivationSource.from("HOTWORD"))
        assertEquals(ActivationSource.Widget, ActivationSource.from("Widget"))
        assertEquals(ActivationSource.GoogleAssistant, ActivationSource.from("GOOGLE"))
    }

    // ===== Unknown Defaults =====

    @Test
    fun `from unknown string defaults to DeepLink`() {
        assertEquals(ActivationSource.DeepLink, ActivationSource.from("unknown"))
    }

    @Test
    fun `from empty string defaults to DeepLink`() {
        assertEquals(ActivationSource.DeepLink, ActivationSource.from(""))
    }

    // ===== Identity =====

    @Test
    fun `different sources are not equal`() {
        val hotword: ActivationSource = ActivationSource.Hotword
        val widget: ActivationSource = ActivationSource.Widget
        assertNotEquals(hotword, widget)
    }
}
