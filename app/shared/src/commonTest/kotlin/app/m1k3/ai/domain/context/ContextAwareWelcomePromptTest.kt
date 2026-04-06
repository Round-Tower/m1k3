package app.m1k3.ai.domain.context

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD: ContextAwareWelcomePromptBuilder
 *
 * Verifies the system prompt injected into the LLM for the welcome message
 * correctly incorporates available UserContext so M1K3 can give a
 * genuinely personal response — not just a generic greeting.
 */
class ContextAwareWelcomePromptTest {

    private val builder = ContextAwareWelcomePromptBuilder()

    // ── Full context ──────────────────────────────────────────

    @Test fun `prompt includes name when available`() {
        val prompt = builder.build(fullContext())
        assertTrue(prompt.contains("Kev"))
    }

    @Test fun `prompt includes city when available`() {
        val prompt = builder.build(fullContext())
        assertTrue(prompt.contains("Dublin"))
    }

    @Test fun `prompt includes sleep when available`() {
        val prompt = builder.build(fullContext())
        assertTrue(prompt.contains("7h") || prompt.contains("420"))
    }

    @Test fun `prompt includes steps when available`() {
        val prompt = builder.build(fullContext())
        assertTrue(prompt.contains("5000") || prompt.contains("5,000"))
    }

    @Test fun `prompt includes screen time when available`() {
        val prompt = builder.build(fullContext())
        assertTrue(prompt.contains("90") || prompt.contains("screen"))
    }

    @Test fun `prompt includes notification count when non-zero`() {
        val prompt = builder.build(fullContext())
        assertTrue(prompt.contains("3") && prompt.contains("notif", ignoreCase = true))
    }

    // ── Graceful degradation ───────────────────────────────────

    @Test fun `prompt works with empty context`() {
        val prompt = builder.build(UserContext())
        assertTrue(prompt.isNotBlank())
        assertFalse(prompt.contains("null"))
    }

    @Test fun `prompt does not mention location when unavailable`() {
        val prompt = builder.build(UserContext(hourOfDay = 9))
        assertFalse(prompt.contains("Dublin"))
        assertFalse(prompt.contains("null"))
    }

    @Test fun `prompt does not mention health when unavailable`() {
        val prompt = builder.build(UserContext(hourOfDay = 9))
        assertFalse(prompt.contains("steps"))
        assertFalse(prompt.contains("sleep"))
    }

    // ── Tone instructions ─────────────────────────────────────

    @Test fun `prompt instructs brevity`() {
        val prompt = builder.build(fullContext())
        assertTrue(
            prompt.contains("brief", ignoreCase = true) ||
            prompt.contains("short", ignoreCase = true) ||
            prompt.contains("concise", ignoreCase = true)
        )
    }

    @Test fun `prompt instructs warmth`() {
        val prompt = builder.build(fullContext())
        assertTrue(
            prompt.contains("warm", ignoreCase = true) ||
            prompt.contains("personal", ignoreCase = true) ||
            prompt.contains("friendly", ignoreCase = true)
        )
    }

    @Test fun `prompt instructs not to repeat greeting header`() {
        val prompt = builder.build(fullContext())
        // The card already says "Good morning, Kev" — LLM shouldn't repeat it
        assertTrue(
            prompt.contains("already", ignoreCase = true) ||
            prompt.contains("don't repeat", ignoreCase = true) ||
            prompt.contains("not repeat", ignoreCase = true) ||
            prompt.contains("follow-up", ignoreCase = true)
        )
    }

    // ── Time awareness ────────────────────────────────────────

    @Test fun `prompt includes time of day context`() {
        val prompt = builder.build(UserContext(hourOfDay = 7))
        assertTrue(
            prompt.contains("morning", ignoreCase = true) ||
            prompt.contains("7", ignoreCase = true)
        )
    }

    // ── Helper ────────────────────────────────────────────────

    private fun fullContext() = UserContext(
        hourOfDay = 8,
        userName = "Kev",
        location = LocationContext(city = "Dublin", country = "Ireland"),
        health = HealthContext(stepsToday = 5000, sleepLastNightMinutes = 420),
        screenTime = ScreenTimeContext(todayMinutes = 90),
        notifications = NotificationContext(unreadCount = 3)
    )
}
